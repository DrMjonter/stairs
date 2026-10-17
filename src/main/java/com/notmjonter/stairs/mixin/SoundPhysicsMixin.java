package com.notmjonter.stairs.mixin;

import com.notmjonter.stairs.StaircaseAudio;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional compatibility hook for SPR NeoForge 1.21.1-1.5.1. */
@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
public abstract class SoundPhysicsMixin {
    @Shadow(remap = false)
    public static void setEnvironment(int source, float a, float b, float c, float d,
            float e, float f, float g, float h, float i, float j) {
        throw new AssertionError();
    }

    @Inject(method = "init", at = @At("HEAD"), remap = false)
    private static void stairs$reset(CallbackInfo ci) {
        StaircaseAudio.clear();
    }

    @Inject(method = "processSound(IDDDLnet/minecraft/sounds/SoundSource;Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"), remap = false)
    private static void stairs$category(int source, double x, double y, double z,
            SoundSource category, ResourceLocation sound, boolean auxOnly, CallbackInfoReturnable<Vec3> cir) {
        StaircaseAudio.category(source, category, sound, auxOnly);
    }

    @Inject(method = "setEnvironment", at = @At("HEAD"), cancellable = true, remap = false)
    private static void stairs$environment(int source, float a, float b, float c, float d,
            float e, float f, float g, float h, float i, float j, CallbackInfo ci) {
        if (StaircaseAudio.environment(source, new float[]{a, b, c, d, e, f, g, h, i, j},
                (id, v) -> setEnvironment(id, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9]))) {
            ci.cancel();
        }
    }
}
