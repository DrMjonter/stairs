package com.notmjonter.stairs.mixin;

import com.notmjonter.stairs.FutureDimension;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep the independent solar clock and weather running normally while visitors lie in beds. */
@Mixin(ServerLevel.class)
public abstract class FutureSleepTimeMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/players/SleepStatus;areEnoughSleeping(I)Z"))
    private boolean stairs$noNightSkip(SleepStatus status, int percentage) {
        return !((ServerLevel) (Object) this).dimension().equals(FutureDimension.KEY)
                && status.areEnoughSleeping(percentage);
    }
}
