package com.notmjonter.stairs;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Reusable dimension effect for a fictional version label in the lower-left HUD. */
public final class VersionHudDimensionEffect implements ClientDimensionEffect {
    private final ResourceKey<Level> dimension;
    private final String versionTranslationKey;
    private final String shaderUniform;

    public VersionHudDimensionEffect(ResourceKey<Level> dimension, String versionTranslationKey, String shaderUniform) {
        this.dimension = dimension;
        this.versionTranslationKey = versionTranslationKey;
        this.shaderUniform = shaderUniform;
    }

    @Override public ResourceKey<Level> dimension() { return dimension; }
    @Override public String shaderUniform() { return shaderUniform; }

    @Override
    public void renderHud(Minecraft minecraft, GuiGraphics graphics, DeltaTracker partialTick, float intensity) {
        int alpha = Math.round(255 * intensity);
        int color = alpha << 24 | 0xffffff;
        graphics.drawString(minecraft.font, Component.translatable(versionTranslationKey), 4, graphics.guiHeight() - minecraft.font.lineHeight - 4,
                color, true);
    }
}
