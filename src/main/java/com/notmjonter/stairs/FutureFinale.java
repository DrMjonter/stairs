package com.notmjonter.stairs;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Counts experienced nights and ejects a player during Minecraft 33.X's fifth midnight. */
public final class FutureFinale {
    static final int TRIGGER_MIDNIGHT = 5;
    static final int DURATION = 160;
    private static final Map<ServerLevel, Long> PREVIOUS_TIME = new WeakHashMap<>();
    private static final Map<UUID, Sequence> ACTIVE = new HashMap<>();

    private FutureFinale() {}

    static void restart(ServerLevel level, ServerPlayer arriving) {
        PREVIOUS_TIME.put(level, level.getDayTime());
        StaircaseData data = StaircaseData.get(level.getServer().overworld());
        for (ServerPlayer player : level.players()) resetPlayer(player, data);
        // The entry event can occur before the arriving player appears in the level's list.
        resetPlayer(arriving, data);
    }

    private static void resetPlayer(ServerPlayer player, StaircaseData data) {
        FutureVillagerReactions.reset(player);
        data.futureMidnights(player.getUUID(), 0);
        if (ACTIVE.remove(player.getUUID()) != null) {
            player.removeEffect(MobEffects.DARKNESS);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            // An arrival can interrupt the ascent; let that player descend safely.
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false, false));
        }
        PacketDistributor.sendToPlayer(player, new Payload(false));
    }

    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(FutureDimension.KEY)) return;
        if (FutureContainment.active(level)) return;

        long now = level.getDayTime();
        Long before = PREVIOUS_TIME.put(level, now);
        StaircaseData data = StaircaseData.get(level.getServer().overworld());
        if (before != null && FutureClock.crossedMidnight(before, now)) {
            for (ServerPlayer player : level.players()) {
                if (ACTIVE.containsKey(player.getUUID())) continue;
                if (data.incrementFutureMidnights(player.getUUID()) == TRIGGER_MIDNIGHT) start(player);
            }
        }

        var iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || !player.serverLevel().dimension().equals(FutureDimension.KEY)) {
                abort(player, data, entry.getKey());
                iterator.remove();
                continue;
            }

            Sequence sequence = entry.getValue();
            if (!player.hasEffect(MobEffects.DARKNESS)) applyDarkness(player);
            player.setDeltaMovement(new Vec3(0, ascentSpeed(sequence.ticks), 0));
            player.hurtMarked = true;
            player.resetFallDistance();
            if (++sequence.ticks < DURATION) continue;

            player.removeEffect(MobEffects.DARKNESS);
            PacketDistributor.sendToPlayer(player, new GarageTransitionPayload(true));
            if (GarageTravel.returnPlayer(player) != 1) {
                PacketDistributor.sendToPlayer(player, new GarageTransitionPayload(false));
                PacketDistributor.sendToPlayer(player, new Payload(false));
                data.futureMidnights(player.getUUID(), TRIGGER_MIDNIGHT - 1);
            }
            iterator.remove();
        }
    }

    private static void start(ServerPlayer player) {
        ACTIVE.put(player.getUUID(), new Sequence());
        applyDarkness(player);
        PacketDistributor.sendToPlayer(player, new Payload(true));
    }

    private static void applyDarkness(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DURATION + 40, 0,
                false, false, false));
    }

    private static void abort(ServerPlayer player, StaircaseData data, UUID id) {
        data.futureMidnights(id, TRIGGER_MIDNIGHT - 1);
        if (player != null) {
            player.removeEffect(MobEffects.DARKNESS);
            PacketDistributor.sendToPlayer(player, new Payload(false));
        }
    }

    static double ascentSpeed(int ticks) {
        double amount = Math.clamp(ticks / (double) DURATION, 0, 1);
        return 0.055 + 0.065 * amount * amount * (3 - 2 * amount);
    }

    public static void stopped(ServerStoppedEvent event) {
        PREVIOUS_TIME.clear();
        ACTIVE.clear();
    }

    private static final class Sequence { int ticks; }

    public record Payload(boolean begin) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "future_finale"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, Payload::begin, Payload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
