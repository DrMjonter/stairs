package com.notmjonter.stairs.mixin;

import com.notmjonter.stairs.FutureDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bed attempts in the simulation must not overwrite a player's real respawn location. */
@Mixin(ServerPlayer.class)
public abstract class FuturePlayerSleepMixin {
    @Unique private boolean stairs$simulationBedAttempt;

    @Inject(method = "startSleepInBed", at = @At("HEAD"))
    private void stairs$beginAttempt(BlockPos pos, CallbackInfoReturnable<?> callback) {
        stairs$simulationBedAttempt = ((ServerPlayer) (Object) this).level().dimension().equals(FutureDimension.KEY);
    }

    @Inject(method = "startSleepInBed", at = @At("RETURN"))
    private void stairs$endAttempt(BlockPos pos, CallbackInfoReturnable<?> callback) {
        stairs$simulationBedAttempt = false;
    }

    @Inject(method = "setRespawnPosition", at = @At("HEAD"), cancellable = true)
    private void stairs$preserveSpawn(ResourceKey<Level> dimension, BlockPos pos, float yaw,
            boolean forced, boolean notify, CallbackInfo callback) {
        if (stairs$simulationBedAttempt) callback.cancel();
    }
}
