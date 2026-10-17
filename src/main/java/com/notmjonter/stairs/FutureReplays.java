package com.notmjonter.stairs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Server-owned visit archive and replay timeline. No client movement packets are trusted. */
public final class FutureReplays {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Stairs.MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<FutureReplayEntity>> CLONE = ENTITIES.register(
            "future_replay", () -> EntityType.Builder.<FutureReplayEntity>of(FutureReplayEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).clientTrackingRange(64).updateInterval(1).noSave()
                    .build(Stairs.MODID + ":future_replay"));
    static final int SAMPLES_PER_SECOND = 6;
    private static final Map<UUID, Visit> RECORDING = new HashMap<>();
    private static final Map<UUID, Playback> PLAYING = new HashMap<>();
    private static int restartDelay;
    private FutureReplays() {}

    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(CLONE.get(), net.minecraft.world.entity.Mob.createMobAttributes().build());
    }

    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(FutureDimension.KEY)) return;
        if (!level.tickRateManager().runsNormally()) return;
        Archive archive = archive(level);
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer player : level.players()) if (player.isAlive() && !player.isSpectator()) present.add(player.getUUID());
        boolean arrival = present.stream().anyMatch(id -> !RECORDING.containsKey(id));
        var iterator = RECORDING.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!present.contains(entry.getKey())) {
                archive.setDirty();
                iterator.remove();
            }
        }
        for (ServerPlayer player : level.players()) {
            if (!present.contains(player.getUUID())) continue;
            Visit visit = RECORDING.get(player.getUUID());
            if (visit == null) {
                visit = new Visit(UUID.randomUUID(), player.getUUID(), player.getGameProfile().getName());
                archive.visits.add(visit);
                RECORDING.put(player.getUUID(), visit);
            }
            int tick = visit.ticks++;
            Frame previous = visit.frames.isEmpty() ? null : visit.frames.getLast();
            if (Visit.sampleTick(tick) || previous == null || previous.ground != player.onGround()) {
                visit.frames.add(Frame.capture(player, tick));
                archive.setDirty();
            }
        }
        if (arrival || present.isEmpty()) {
            boolean hadClones = !PLAYING.isEmpty();
            clearClones(level, arrival);
            restartDelay = arrival && hadClones ? 20 : 0;
        }
        if (present.isEmpty()) return;
        if (restartDelay > 0 && --restartDelay > 0) return;
        for (Visit visit : archive.visits) {
            if (visit.frames.isEmpty() || RECORDING.containsValue(visit)) continue;
            Playback playback = PLAYING.computeIfAbsent(visit.id, ignored -> new Playback());
            int duration = Math.max(visit.ticks, 1);
            int tick = playback.tick;
            playback.tick = (tick + 1) % duration;
            if (tick == 0) playback.index = 0;
            while (playback.index + 1 < visit.frames.size()
                    && visit.frames.get(playback.index + 1).tick <= tick) playback.index++;
            int index = playback.index;
            // Hold the last sample, then restart without interpolating through the map.
            Frame a = visit.frames.get(index);
            Frame b = visit.frames.get(Math.min(index + 1, visit.frames.size() - 1));
            float amount = a == b ? 0 : Math.clamp((tick - a.tick) / (float) (b.tick - a.tick), 0, 1);
            double x = Mth.lerp(amount, a.x, b.x), z = Mth.lerp(amount, a.z, b.z);
            if (level.getChunkSource().getChunkNow(Mth.floor(x) >> 4, Mth.floor(z) >> 4) == null) {
                if (playback.clone != null) playback.clone.discard();
                playback.clone = null;
                continue;
            }
            if (playback.clone == null || playback.clone.isRemoved()) {
                FutureReplayEntity clone = CLONE.get().create(level);
                if (clone == null) continue;
                clone.alex((visit.id.getLeastSignificantBits() & 1) != 0);
                clone.skinVariant(Math.floorMod(visit.id.hashCode(), FutureReplayEntity.SKIN_VARIANTS));
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(visit.player);
                clone.owner(visit.player, owner == null ? visit.playerName : owner.getGameProfile().getName());
                pose(clone, a, b, amount);
                if (!level.addFreshEntity(clone)) continue;
                playback.clone = clone;
            }
            pose(playback.clone, a, b, amount);
        }
    }

    private static void pose(FutureReplayEntity clone, Frame a, Frame b, float amount) {
        clone.setPos(Mth.lerp(amount, a.x, b.x), Mth.lerp(amount, a.y, b.y), Mth.lerp(amount, a.z, b.z));
        float yaw = Mth.rotLerp(amount, a.yaw, b.yaw);
        clone.setYRot(yaw);
        clone.setYHeadRot(yaw);
        clone.setYBodyRot(yaw);
        clone.setXRot(Mth.lerp(amount, a.pitch, b.pitch));
        clone.setShiftKeyDown(a.crouching);
        clone.setOnGround(a.ground);
    }

    private static void clearClones(ServerLevel level, boolean fizzle) {
        // Also discard tracked instances whose chunks were unloaded this tick.
        for (Playback playback : PLAYING.values()) if (playback.clone != null) {
            FutureReplayEntity clone = playback.clone;
            if (fizzle && !clone.isRemoved()) level.sendParticles(ParticleTypes.ENCHANT,
                    clone.getX(), clone.getY() + 1, clone.getZ(), 35, 0.35, 0.8, 0.35, 0.2);
            clone.discard();
        }
        for (var entity : level.getAllEntities()) if (entity instanceof FutureReplayEntity clone) {
            clone.discard();
        }
        PLAYING.clear();
    }

    public static void stopped(ServerStoppedEvent event) {
        RECORDING.clear();
        PLAYING.clear();
        restartDelay = 0;
    }

    public static void restart(ServerLevel level) {
        clearClones(level, false);
        RECORDING.clear();
        archive(level).setDirty();
        restartDelay = 0;
    }

    static Archive archive(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Archive::new, Archive::load),
                Stairs.MODID + "_future_visits");
    }

    private static final class Playback {
        FutureReplayEntity clone;
        int tick;
        int index;
    }

    static final class Visit {
        final UUID id;
        final UUID player;
        final String playerName;
        final List<Frame> frames = new ArrayList<>();
        int ticks;
        Visit(UUID id, UUID player, String playerName) {
            this.id = id; this.player = player; this.playerName = playerName;
        }
        static boolean sampleTick(int tick) {
            return tick == 0 || tick * (long) SAMPLES_PER_SECOND / 20
                    != (tick - 1L) * SAMPLES_PER_SECOND / 20;
        }
    }

    record Frame(int tick, double x, double y, double z, float yaw, float pitch, boolean crouching, boolean ground) {
        static Frame capture(ServerPlayer player, int tick) {
            return new Frame(tick, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                    player.isShiftKeyDown(), player.onGround());
        }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Tick", tick);
            tag.putDouble("X", x); tag.putDouble("Y", y); tag.putDouble("Z", z);
            tag.putFloat("Yaw", yaw); tag.putFloat("Pitch", pitch);
            tag.putBoolean("Crouching", crouching); tag.putBoolean("Ground", ground);
            return tag;
        }
        static Frame load(CompoundTag tag, int legacyTick) {
            return new Frame(tag.contains("Tick") ? tag.getInt("Tick") : legacyTick,
                    tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
                    tag.getFloat("Yaw"), tag.getFloat("Pitch"), tag.getBoolean("Crouching"), tag.getBoolean("Ground"));
        }
    }

    static final class Archive extends SavedData {
        final List<Visit> visits = new ArrayList<>();
        static Archive load(CompoundTag tag, HolderLookup.Provider provider) {
            Archive archive = new Archive();
            for (Tag value : tag.getList("Visits", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) value;
                Visit visit = new Visit(entry.getUUID("Id"), entry.getUUID("Player"), entry.getString("PlayerName"));
                for (Tag frame : entry.getList("Frames", Tag.TAG_COMPOUND))
                    visit.frames.add(Frame.load((CompoundTag) frame, visit.frames.size() * 5));
                visit.ticks = entry.contains("Duration") ? entry.getInt("Duration") : visit.frames.size() * 5;
                archive.visits.add(visit);
            }
            return archive;
        }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag entries = new ListTag();
            for (Visit visit : visits) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Id", visit.id); entry.putUUID("Player", visit.player);
                entry.putString("PlayerName", visit.playerName);
                entry.putInt("Duration", visit.ticks);
                ListTag frames = new ListTag();
                visit.frames.forEach(frame -> frames.add(frame.save()));
                entry.put("Frames", frames); entries.add(entry);
            }
            tag.put("Visits", entries);
            return tag;
        }
    }
}
