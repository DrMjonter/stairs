package com.notmjonter.stairs.mixin;

import com.notmjonter.stairs.GarageMusic;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class MusicVolumeMixin {
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("RETURN"), cancellable = true)
    private void stairs$musicFade(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(GarageMusic.volume(sound, cir.getReturnValue()));
    }
}
