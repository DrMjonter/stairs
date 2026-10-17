package com.notmjonter.stairs.mixin;

import com.notmjonter.stairs.TickstormWeather;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Holds world interaction packets until a Tickstorm enters its burst phase. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class TickstormInteractionMixin {
    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void stairs$queueBlockBreaking(ServerboundPlayerActionPacket packet, CallbackInfo callback) {
        if (switch (packet.getAction()) {
            case START_DESTROY_BLOCK, ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK -> true;
            default -> false;
        }) {
            ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
            if (TickstormWeather.queueInteraction(listener.player, () -> listener.handlePlayerAction(packet)))
                callback.cancel();
        }
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void stairs$queueBlockUse(ServerboundUseItemOnPacket packet, CallbackInfo callback) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        if (TickstormWeather.queueInteraction(listener.player, () -> listener.handleUseItemOn(packet))) callback.cancel();
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void stairs$queueItemUse(ServerboundUseItemPacket packet, CallbackInfo callback) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        if (TickstormWeather.queueInteraction(listener.player, () -> listener.handleUseItem(packet))) callback.cancel();
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void stairs$queueEntityUse(ServerboundInteractPacket packet, CallbackInfo callback) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        if (TickstormWeather.queueInteraction(listener.player, () -> listener.handleInteract(packet))) callback.cancel();
    }
}
