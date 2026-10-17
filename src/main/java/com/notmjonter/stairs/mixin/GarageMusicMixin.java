package com.notmjonter.stairs.mixin;

import com.notmjonter.stairs.GarageMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class GarageMusicMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private SoundInstance currentMusic;
    @Shadow private int nextSongDelay;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void stairs$garageMusic(CallbackInfo ci) {
        if (GarageMusic.tick(minecraft, currentMusic)) {
            currentMusic = null;
            nextSongDelay = 0;
            ci.cancel();
        }
    }
}
