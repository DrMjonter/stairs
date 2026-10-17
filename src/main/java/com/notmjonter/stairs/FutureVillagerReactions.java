package com.notmjonter.stairs;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** The orb notices attacks on villagers and successful player bed evictions. */
public final class FutureVillagerReactions {
    public static final ResourceLocation[] SOUNDS = {
        sound("orb_warning_01"), sound("orb_warning_02"), sound("orb_warning_03")
    };
    private static final Map<ServerLevel, Long> LAST_REACTION = new WeakHashMap<>();
    private FutureVillagerReactions() {}

    private static ResourceLocation sound(String name) {
        return ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "future." + name);
    }

    public static boolean isReactionSound(ResourceLocation sound) {
        for (ResourceLocation candidate : SOUNDS) if (candidate.equals(sound)) return true;
        return false;
    }

    public static void damaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Villager villager && event.getNewDamage() > 0
                && event.getSource().getEntity() instanceof ServerPlayer player
                && villager.level() instanceof ServerLevel level) {
            react(level, player);
        }
    }

    public static void react(ServerLevel level, ServerPlayer responsible) {
        if (!level.dimension().equals(FutureDimension.KEY) || level.players().isEmpty() || FutureContainment.active(level)) return;
        long now = level.getGameTime();
        Long previous = LAST_REACTION.get(level);
        if (previous != null && now >= previous && now - previous < 40) return;
        LAST_REACTION.put(level, now);
        if (FutureContainment.warning(level)) return;
        int variant = level.random.nextInt(SOUNDS.length);
        // A celestial warning is heard by everyone in this dimension, with the same variant.
        for (var player : level.players()) PacketDistributor.sendToPlayer(player,
                new Payload(variant));
    }

    public static void reset(ServerPlayer player) {
        LAST_REACTION.remove(player.serverLevel());
        PacketDistributor.sendToPlayer(player, new Payload(-1));
    }
    public static void clearCooldown(ServerLevel level) { LAST_REACTION.remove(level); }

    public static void stopped(ServerStoppedEvent event) { LAST_REACTION.clear(); }

    public record Payload(int variant) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "future_villager_reaction"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Payload::variant, Payload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
