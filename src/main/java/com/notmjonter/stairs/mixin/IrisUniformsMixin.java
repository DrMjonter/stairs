package com.notmjonter.stairs.mixin;

import com.notmjonter.stairs.StaircaseShaderBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class IrisUniformsMixin {
    @Inject(method = "generalCommonUniforms", at = @At("TAIL"), remap = false)
    private static void stairs$uniform(@Coerce Object holder, @Coerce Object notifier,
            @Coerce Object directives, CallbackInfo ci) {
        StaircaseShaderBridge.register(holder, notifier);
    }
}
