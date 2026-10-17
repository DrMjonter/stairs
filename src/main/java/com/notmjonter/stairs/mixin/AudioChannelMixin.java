package com.notmjonter.stairs.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.notmjonter.stairs.StaircaseAudio;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public abstract class AudioChannelMixin {
    @Shadow @Final private int source;

    // Runs on the audio executor for both static and streamed sounds, including
    // already-playing ambience. Replays cached filters, never world raycasts.
    @Inject(method = "updateStream", at = @At("TAIL"))
    private void stairs$update(CallbackInfo ci) {
        StaircaseAudio.update(source);
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void stairs$destroy(CallbackInfo ci) {
        StaircaseAudio.forget(source);
    }
}
