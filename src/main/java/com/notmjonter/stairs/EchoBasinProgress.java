package com.notmjonter.stairs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Synchronizes each player's well progression for client-side Echo Basin audio. */
public final class EchoBasinProgress {
    private static final Map<UUID, Integer> SENT_WELLS = new HashMap<>();
    private static volatile int clientWells;

    private EchoBasinProgress() {}

    public static void tick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            boolean inBasin = player.serverLevel().dimension().equals(EchoBasinDimension.KEY);
            Integer previous = SENT_WELLS.get(player.getUUID());
            if (!inBasin) {
                if (previous != null) PacketDistributor.sendToPlayer(player, new Payload(0));
                SENT_WELLS.remove(player.getUUID());
                continue;
            }
            int wells = StaircaseData.get(event.getServer().overworld()).echoReferenceCount(player.getUUID());
            if (previous == null || previous != wells) {
                PacketDistributor.sendToPlayer(player, new Payload(wells));
                SENT_WELLS.put(player.getUUID(), wells);
            }
        }
        SENT_WELLS.keySet().removeIf(id -> event.getServer().getPlayerList().getPlayer(id) == null);
    }

    public static void stopped(ServerStoppingEvent event) {
        SENT_WELLS.clear();
    }

    static void receive(int wells) {
        clientWells = Math.clamp(wells, 0, 3);
    }

    static float intensity() {
        return clientWells == 0 ? 0.25F : clientWells * 0.5F;
    }

    public record Payload(int wells) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "echo_basin_progress"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Payload::wells, Payload::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
