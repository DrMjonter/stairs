package com.notmjonter.stairs.mixin;
import com.notmjonter.stairs.MirrorDimension;
import com.notmjonter.stairs.MirrorSnapshots;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerPlayer.class)
public abstract class MirrorEntryMixin {
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), cancellable = true)
    private void stairs$waitForSnapshot(ServerLevel level, double x, double y, double z, Set<RelativeMovement> movement,
            float yaw, float pitch, CallbackInfoReturnable<Boolean> cir) {
        if (level.dimension() == MirrorDimension.KEY && MirrorSnapshots.busy(level.getServer())) cir.setReturnValue(false);
    }
}
