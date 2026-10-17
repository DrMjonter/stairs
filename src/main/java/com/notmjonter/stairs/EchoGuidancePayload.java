package com.notmjonter.stairs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Starts a client-side whisper that remains player-relative while pointing at this well. */
public record EchoGuidancePayload(BlockPos well) implements CustomPacketPayload {
    public static final Type<EchoGuidancePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "echo_guidance_whisper"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoGuidancePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBlockPos(payload.well),
            buffer -> new EchoGuidancePayload(buffer.readBlockPos()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
