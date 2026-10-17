package com.notmjonter.stairs;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Dimension-local cycle that records fifteen seconds and replays it over three seconds. */
public final class TickstormWeather {
    static final int CAPTURE_TICKS = 20 * 15;
    static final int BURST_TICKS = 20 * 3;
    static final int PLAYBACK_RATE = CAPTURE_TICKS / BURST_TICKS;
    private static final int MAX_QUEUED_ACTIONS = 8192;
    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();

    private TickstormWeather() {}

    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(TickstormDimension.KEY)
                || !level.tickRateManager().runsNormally()) return;
        State state = STATES.computeIfAbsent(level, TickstormWeather::newState);
        enforceClearWeather(level);

        if (state.phase == Phase.CAPTURE) {
            freezeMobs(level, state);
        } else if (state.phase == Phase.BURST) {
            replayActions(level, state);
            accelerateMobs(level);
        }

        if (--state.remaining > 0) return;
        if (state.phase == Phase.CAPTURE) beginBurst(state);
        else beginCapture(state);
    }

    private static State newState(ServerLevel level) {
        State state = new State();
        state.phase = Phase.CAPTURE;
        state.remaining = CAPTURE_TICKS;
        enforceClearWeather(level);
        return state;
    }

    /** Called by packet hooks. Returning true means the live packet must be cancelled. */
    public static boolean queueInteraction(ServerPlayer player, Runnable replay) {
        if (!player.serverLevel().dimension().equals(TickstormDimension.KEY)) return false;
        State state = STATES.computeIfAbsent(player.serverLevel(), TickstormWeather::newState);
        if (state.phase != Phase.CAPTURE) return false;
        enqueue(state, new QueuedAction(CAPTURE_TICKS - state.remaining, player.getUUID(), replay));
        return true;
    }

    /** Called by the LivingEntity damage hook before damage mutates the entity. */
    public static boolean queueDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity.level() instanceof ServerLevel level)
                || !level.dimension().equals(TickstormDimension.KEY)) return false;
        State state = STATES.computeIfAbsent(level, TickstormWeather::newState);
        if (state.phase != Phase.CAPTURE) return false;
        enqueue(state, new QueuedAction(CAPTURE_TICKS - state.remaining, null, () -> {
            if (entity.isAlive() && entity.level() == level) entity.hurt(source, amount);
        }));
        return true;
    }

    private static void enqueue(State state, QueuedAction action) {
        if (state.actions.size() < MAX_QUEUED_ACTIONS) state.actions.addLast(action);
    }

    private static void beginCapture(State state) {
        restoreMobs(state);
        state.actions.clear();
        state.phase = Phase.CAPTURE;
        state.remaining = CAPTURE_TICKS;
    }

    private static void beginBurst(State state) {
        restoreMobs(state);
        state.phase = Phase.BURST;
        state.remaining = BURST_TICKS;
    }

    private static void freezeMobs(ServerLevel level, State state) {
        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            FrozenMob frozen = state.mobs.computeIfAbsent(mob.getUUID(), ignored -> new FrozenMob(
                    mob, mob.position(), mob.getXRot(), mob.getYRot(), mob.isNoAi(), mob.isNoGravity()));
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setDeltaMovement(Vec3.ZERO);
            mob.teleportTo(frozen.position.x, frozen.position.y, frozen.position.z);
            mob.setXRot(frozen.xRot);
            mob.setYRot(frozen.yRot);
        }
    }

    private static void restoreMobs(State state) {
        for (FrozenMob frozen : state.mobs.values()) {
            if (frozen.mob.isRemoved()) continue;
            frozen.mob.setNoAi(frozen.noAi);
            frozen.mob.setNoGravity(frozen.noGravity);
        }
        state.mobs.clear();
    }

    private static void replayActions(ServerLevel level, State state) {
        int playbackTick = BURST_TICKS - state.remaining;
        int captureCutoff = (playbackTick + 1) * PLAYBACK_RATE;
        while (!state.actions.isEmpty() && state.actions.peekFirst().captureTick < captureCutoff) {
            QueuedAction action = state.actions.removeFirst();
            if (action.player == null) action.replay.run();
            else {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(action.player);
                if (player != null && player.isAlive() && player.serverLevel() == level) action.replay.run();
            }
        }
    }

    private static void accelerateMobs(ServerLevel level) {
        // The ordinary level tick already supplied one tick. Four additional ticks
        // make the recorded fifteen seconds unfold during the three-second burst.
        var mobs = new java.util.ArrayList<Mob>();
        for (var entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && mob.isAlive()) mobs.add(mob);
        }
        for (Mob mob : mobs) {
            for (int extra = 1; extra < PLAYBACK_RATE && mob.isAlive() && !mob.isRemoved(); extra++) mob.tick();
        }
    }

    private static void enforceClearWeather(ServerLevel level) {
        level.setRainLevel(0);
        level.setThunderLevel(0);
        if (level.getGameTime() % 100 == 0) level.setWeatherParameters(6000, 0, false, false);
    }

    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getTo().equals(TickstormDimension.KEY)) synchronize(player);
        else if (event.getFrom().equals(TickstormDimension.KEY))
            PacketDistributor.sendToPlayer(player, new Payload(false));
    }

    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.serverLevel().dimension().equals(TickstormDimension.KEY)) synchronize(player);
    }

    private static void synchronize(ServerPlayer player) {
        STATES.computeIfAbsent(player.serverLevel(), TickstormWeather::newState);
        PacketDistributor.sendToPlayer(player, new Payload(true));
    }

    public static void stopped(ServerStoppedEvent event) {
        STATES.values().forEach(TickstormWeather::restoreMobs);
        STATES.clear();
    }

    private enum Phase { CAPTURE, BURST }

    private static final class State {
        Phase phase;
        int remaining;
        final ArrayDeque<QueuedAction> actions = new ArrayDeque<>();
        final Map<UUID, FrozenMob> mobs = new HashMap<>();
    }

    private record QueuedAction(int captureTick, UUID player, Runnable replay) {}
    private record FrozenMob(Mob mob, Vec3 position, float xRot, float yRot, boolean noAi, boolean noGravity) {}

    public record Payload(boolean active) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                Stairs.MODID, "tickstorm_weather"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, Payload::active, Payload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
