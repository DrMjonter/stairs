package com.notmjonter.stairs;

import com.mojang.logging.LogUtils;
import com.notmjonter.stairs.mixin.MirrorServerAccess;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-thread lifecycle, with bulk file copying and replacement on a dedicated worker. */
public final class MirrorSnapshots {
    // Region writes execute on IO workers. A snapshot holds the exclusive gate while
    // copying, so no region/entity/POI file can change underneath Files.copy.
    public static final ReentrantReadWriteLock DISK_GATE = new ReentrantReadWriteLock(true);
    private static MirrorSnapshots active;
    private final MinecraftServer server;
    private final Supplier<ServerLevel> factory;
    private final MirrorSnapshotStore store;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Stairs mirror snapshot");
        thread.setDaemon(true);
        return thread;
    });
    private final Properties schedule;
    private CompletableFuture<Void> job;
    private boolean installing;
    private boolean preparing;
    private boolean requested;
    private long nextCheck;
    private long generation;
    private BorderChangeListener ownedBorderListener;
    private CommandSourceStack requester;

    private MirrorSnapshots(MinecraftServer server, Supplier<ServerLevel> factory) throws IOException {
        this.server = server;
        this.factory = factory;
        store = new MirrorSnapshotStore(server.getWorldPath(LevelResource.ROOT));
        // This factory hook runs before the first mirror ServerLevel opens its files.
        // Finish a crash-interrupted swap before Minecraft can create replacement chunks.
        store.install();
        schedule = store.state();
        generation = Long.parseLong(store.active().getProperty("capturedAt", "0"));
        if (generation > Long.parseLong(schedule.getProperty("last", "0"))) {
            schedule.setProperty("last", Long.toString(generation));
            schedule.setProperty("next", Long.toString(MirrorSnapshotSchedule.afterSuccess(System.currentTimeMillis(), RandomGenerator.getDefault())));
            store.state(schedule);
        }
        if (!schedule.containsKey("next")) {
            schedule.setProperty("next", Long.toString(MirrorSnapshotSchedule.first(System.currentTimeMillis(), RandomGenerator.getDefault())));
            store.state(schedule);
        }
    }

    public static void factory(MinecraftServer server, Supplier<ServerLevel> factory) {
        try { active = new MirrorSnapshots(server, factory); }
        catch (IOException exception) { throw new IllegalStateException("Cannot initialize mirror snapshots", exception); }
    }

    public static boolean busy(MinecraftServer server) {
        return active != null && active.server == server && (active.job != null || active.installing || active.preparing);
    }

    public static int request(CommandSourceStack source) {
        if (active == null || active.server != source.getServer()) {
            source.sendFailure(Component.translatable("command.mjontersstairs.mirror.unavailable"));
            return 0;
        }
        if (active.requested || busy(source.getServer())) {
            source.sendFailure(Component.translatable("command.mjontersstairs.mirror.already_queued"));
            return 0;
        }
        active.requested = true;
        active.requester = source;
        active.nextCheck = 0;
        source.sendSuccess(() -> Component.translatable("command.mjontersstairs.mirror.queued"), false);
        return 1;
    }

    public static int status(CommandSourceStack source) {
        if (active == null) return 0;
        try {
            String captured = active.store.active().getProperty("capturedAt");
            Component state = Component.translatable(active.installing ? "command.mjontersstairs.mirror.state.installing"
                    : active.job != null ? "command.mjontersstairs.mirror.state.copying"
                    : active.requested || active.store.pending() ? "command.mjontersstairs.mirror.state.waiting"
                    : "command.mjontersstairs.mirror.state.idle");
            Object activeCopy = captured == null ? Component.translatable("command.mjontersstairs.mirror.no_copy")
                    : java.time.Instant.ofEpochMilli(Long.parseLong(captured)).toString();
            String nextCapture = java.time.Instant.ofEpochMilli(active.number("next")).toString();
            source.sendSuccess(() -> Component.translatable("command.mjontersstairs.mirror.status", state, activeCopy, nextCapture), false);
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.translatable("command.mjontersstairs.mirror.status_failed"));
            return 0;
        }
    }

    public static void tick(ServerTickEvent.Post event) {
        if (active != null && active.server == event.getServer()) active.tick();
    }

    private void tick() {
        try {
            if (job != null) {
                if (!job.isDone()) return;
                finishJob();
                nextCheck = 0;
            }
            long now = System.currentTimeMillis();
            if (now < nextCheck) return;
            nextCheck = now + 1000;
            if (store.pending()) {
                if (empty()) install();
            } else if ((requested || MirrorSnapshotSchedule.due(now, number("next"), number("last"))) && empty()) {
                preparing = true;
                try { capture(now); } finally { preparing = false; }
            }
        } catch (Exception exception) {
            failure(exception);
        }
    }

    private boolean empty() {
        ServerLevel mirror = server.getLevel(MirrorDimension.KEY);
        return mirror != null && mirror.players().isEmpty() && !GarageTravel.hasMirrorTrips();
    }

    private void capture(long now) throws IOException {
        // Serialization and the initial save must stay on the server thread.
        LogUtils.getLogger().info("Saving Overworld for mirror snapshot");
        server.overworld().save(null, true, false);
        CompoundTag staircaseFile = new CompoundTag();
        StaircaseData mirrored = new StaircaseData();
        StaircaseData.get(server.overworld()).stairs.values().stream()
                .filter(stair -> stair.destination == MirrorDimension.DESTINATION
                        && stair.state == StaircaseData.State.ACTIVATED)
                .forEach(stair -> {
                    var copy = new StaircaseData.Staircase(stair.origin, stair.rotation, MirrorDimension.DESTINATION);
                    copy.state = StaircaseData.State.ACTIVATED;
                    mirrored.stairs.put(copy.origin, copy);
                });
        staircaseFile.put("data", mirrored.save(new CompoundTag(), server.registryAccess()));
        staircaseFile.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        job = CompletableFuture.runAsync(() -> {
            var lock = DISK_GATE.writeLock();
            lock.lock();
            try {
                store.captureIncremental(now, lock::unlock, directory -> {
                    Files.createDirectories(directory.resolve("data"));
                    NbtIo.writeCompressed(staircaseFile, directory.resolve("data/" + Stairs.MODID + "_staircases.dat"));
                });
            } catch (IOException exception) { throw new java.util.concurrent.CompletionException(exception); }
            finally { if (DISK_GATE.isWriteLockedByCurrentThread()) lock.unlock(); }
        }, worker);
        schedule.setProperty("capturing", Long.toString(now));
        LogUtils.getLogger().info("Copying mirror snapshot on background worker; mirror entry is temporarily disabled");
    }

    @SuppressWarnings("deprecation") // NeoForge requires invalidating its cached world array after replacement.
    private void install() throws IOException {
        ServerLevel old = server.getLevel(MirrorDimension.KEY);
        installing = true;
        old.save(null, true, false);
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(old));
        old.close();
        ((MirrorServerAccess) server).stairs$levels().remove(MirrorDimension.KEY);
        server.markWorldsDirty();
        installing = true;
        job = CompletableFuture.runAsync(() -> {
            try { store.install(); }
            catch (IOException exception) { throw new java.util.concurrent.CompletionException(exception); }
        }, worker);
    }

    @SuppressWarnings("deprecation") // See install(): a fresh ServerLevel must enter the next tick array.
    private void finishJob() throws IOException {
        var completed = job;
        job = null;
        try { completed.join(); }
        finally {
            if (installing) {
                // install() rolls back the previous directory if the rename fails.
                ServerLevel replacement = factory.get();
                replacement.getWorldBorder().applySettings(server.overworld().getWorldBorder().createSettings());
                if (ownedBorderListener != null) server.overworld().getWorldBorder().removeListener(ownedBorderListener);
                ownedBorderListener = new BorderChangeListener.DelegateBorderChangeListener(replacement.getWorldBorder());
                server.overworld().getWorldBorder().addListener(ownedBorderListener);
                ((MirrorServerAccess) server).stairs$levels().put(MirrorDimension.KEY, replacement);
                server.markWorldsDirty();
                NeoForge.EVENT_BUS.post(new LevelEvent.Load(replacement));
                generation = Long.parseLong(store.active().getProperty("capturedAt", "0"));
                installing = false;
            }
        }
        if (store.pending()) {
            long captured = number("capturing");
            if (captured != 0) {
                schedule.setProperty("last", Long.toString(captured));
                schedule.setProperty("next", Long.toString(MirrorSnapshotSchedule.afterSuccess(System.currentTimeMillis(), RandomGenerator.getDefault())));
                schedule.remove("capturing");
                store.state(schedule);
            }
        } else {
            // Also recover scheduling after a crash between publishing and recording capture.
            long captured = Long.parseLong(store.active().getProperty("capturedAt", "0"));
            if (captured > number("last")) {
                schedule.setProperty("last", Long.toString(captured));
                schedule.setProperty("next", Long.toString(MirrorSnapshotSchedule.afterSuccess(System.currentTimeMillis(), RandomGenerator.getDefault())));
                schedule.remove("capturing");
                store.state(schedule);
            }
            requested = false;
            if (requester != null) requester.sendSuccess(() -> Component.translatable("command.mjontersstairs.mirror.refreshed"), false);
            requester = null;
            LogUtils.getLogger().info("Mirror snapshot installed and dimension reloaded without restarting");
        }
    }

    private long number(String key) { return Long.parseLong(schedule.getProperty(key, "0")); }

    private void failure(Exception exception) {
        LogUtils.getLogger().error("Mirror snapshot failed; preserving staged/previous data", exception);
        nextCheck = System.currentTimeMillis() + 60_000;
        if (requester != null) requester.sendFailure(Component.translatable("command.mjontersstairs.mirror.failed"));
        requester = null;
    }

    public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || active == null || player.server != active.server || player.serverLevel().dimension() != MirrorDimension.KEY) return;
        if (active.generation > player.getPersistentData().getLong("mjontersstairs_mirror_generation")) {
            if (GarageTravel.returnPlayer(player) == 1)
                player.sendSystemMessage(Component.translatable("message.mjontersstairs.mirror.shifted_while_away"));
        }
        player.getPersistentData().putLong("mjontersstairs_mirror_generation", active.generation);
    }

    public static void changedDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player && active != null
                && player.server == active.server && player.serverLevel().dimension() == MirrorDimension.KEY)
            player.getPersistentData().putLong("mjontersstairs_mirror_generation", active.generation);
    }

    public static void stopping(ServerStoppingEvent event) {
        if (active == null || active.server != event.getServer()) return;
        try {
            // Worker does no server-thread work, so waiting here cannot form a task cycle.
            if (active.job != null) active.finishJob();
        } catch (Exception exception) { active.failure(exception); }
        finally { active.worker.shutdown(); active = null; }
    }
}
