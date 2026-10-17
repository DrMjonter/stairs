package com.notmjonter.stairs;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GarageTransitionPayload(boolean begin) implements CustomPacketPayload {
    public static final Type<GarageTransitionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "garage_transition"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GarageTransitionPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, GarageTransitionPayload::begin, GarageTransitionPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
