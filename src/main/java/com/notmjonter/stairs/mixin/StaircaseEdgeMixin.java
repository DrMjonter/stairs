package com.notmjonter.stairs.mixin;

import com.notmjonter.stairs.StaircaseEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuse vanilla edge protection without changing the player's crouch state. */
@Mixin(Player.class)
public abstract class StaircaseEdgeMixin {
    @Inject(method = "isStayingOnGroundSurface", at = @At("RETURN"), cancellable = true)
    private void stairs$protectEdges(CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        if (player.hasEffect(StaircaseEffects.WEIGHT) && player.isAlive()
                && !player.isSpectator() && !player.getAbilities().flying && !player.isPassenger()) {
            cir.setReturnValue(true);
        }
    }
}
