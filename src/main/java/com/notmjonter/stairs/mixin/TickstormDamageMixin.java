package com.notmjonter.stairs.mixin;

import com.notmjonter.stairs.TickstormWeather;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Defers living-entity damage during Tickstorm capture. */
@Mixin(LivingEntity.class)
public abstract class TickstormDamageMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void stairs$queueDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if (TickstormWeather.queueDamage((LivingEntity) (Object) this, source, amount))
            callback.setReturnValue(false);
    }
}
