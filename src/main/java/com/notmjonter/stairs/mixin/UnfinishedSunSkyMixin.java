package com.notmjonter.stairs.mixin;

import com.notmjonter.stairs.Stairs;
import com.notmjonter.stairs.UnfinishedSunDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Uses the custom solar disk only in destination two, with vanilla as an asset fallback. */
@Mixin(LevelRenderer.class)
public abstract class UnfinishedSunSkyMixin {
    private static final ResourceLocation CUSTOM_SUN = ResourceLocation.fromNamespaceAndPath(
            Stairs.MODID, "textures/environment/unfinished_sun.png");

    @ModifyArg(method = "renderSky", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V",
            ordinal = 0), index = 1)
    private ResourceLocation stairs$unfinishedSun(ResourceLocation original) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.dimension() == UnfinishedSunDimension.KEY
                && minecraft.getResourceManager().getResource(CUSTOM_SUN).isPresent() ? CUSTOM_SUN : original;
    }
}
