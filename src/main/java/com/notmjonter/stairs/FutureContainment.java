package com.notmjonter.stairs;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server authority for the shared fifth-warning containment sequence. */
public final class FutureContainment {
    private static final Map<ServerLevel, Integer> WARNINGS = new WeakHashMap<>();
    private static final Map<ServerLevel, Integer> ACTIVE = new WeakHashMap<>();
    private FutureContainment() {}

    public static boolean active(ServerLevel level) { return ACTIVE.containsKey(level); }
    public static void resetWarnings(ServerLevel level) { WARNINGS.remove(level); }

    public static boolean warning(ServerLevel level) {
        if (active(level)) return true;
        if (WARNINGS.merge(level, 1, Integer::sum) < 5) return false;
        ACTIVE.put(level, 0);
        // Prevent the ordinary fifth-midnight sequence from competing with containment.
        if (!level.players().isEmpty()) FutureFinale.restart(level, level.players().getFirst());
        for (ServerPlayer player : level.players()) PacketDistributor.sendToPlayer(player, new Payload(0));
        return true;
    }

    public static void synchronize(ServerPlayer player) {
        Integer age = ACTIVE.get(player.serverLevel());
        if (age != null) PacketDistributor.sendToPlayer(player, new Payload(age));
    }

    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !active(level)
                || !level.tickRateManager().runsNormally()) return;
        int ticks = ACTIVE.get(level) + 1;
        ACTIVE.put(level, ticks);
        if (ticks < FutureContainmentState.TRANSFER_TICK) return;
        var players = new ArrayList<>(level.players());
        for (ServerPlayer player : players) {
            PacketDistributor.sendToPlayer(player, new Payload(-2));
            if (player.isSleeping()) player.stopSleepInBed(true, true);
            FutureFinale.restart(level, player);
            if (!GarageTravel.resetFuturePlayer(player)) {
                // Keep failed transfers visible and safe rather than trapping someone under black.
                PacketDistributor.sendToPlayer(player, new Payload(-1));
            }
            // Same-dimension teleports have no arrival screen to release the standard cover.
            PacketDistributor.sendToPlayer(player, new GarageTransitionPayload(false));
        }
        FutureReplays.restart(level);
        level.setDayTime(0);
        if (level.getLevelData() instanceof FutureClock.Data data) data.setDayTimeFraction(0);
        level.setDayTimePerTick(1);
        level.getServer().forceTimeSynchronization();
        FutureVillagerReactions.clearCooldown(level);
        resetWarnings(level);
        ACTIVE.remove(level);
    }

    public static void stopped(ServerStoppedEvent event) { WARNINGS.clear(); ACTIVE.clear(); }

    /** Nonnegative age starts/synchronizes; -2 covers the transfer; -1 cancels. */
    public record Payload(int age) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "future_containment"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Payload::age, Payload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
