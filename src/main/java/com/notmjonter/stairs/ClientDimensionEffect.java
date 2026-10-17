package com.notmjonter.stairs;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Client-only behavior attached to one dimension. */
public interface ClientDimensionEffect {
    ResourceKey<Level> dimension();

    /** Optional Iris uniform driven by this effect's transition intensity. */
    default String shaderUniform() { return null; }

    default int fadeInTicks() { return 20; }

    default int fadeOutTicks() { return 20; }

    /** Called once after the client enters this effect's dimension. Previous may be null. */
    default void onEnter(Minecraft minecraft, ResourceKey<Level> previous) {}

    /** Called once before this effect begins fading out. Next may be null. */
    default void onExit(Minecraft minecraft, ResourceKey<Level> next) {}

    /** Called each unpaused client tick while the effect is visible, including its fade-out. */
    default void tick(Minecraft minecraft, float intensity) {}

    /** Draw dimension-specific HUD elements after the normal HUD. */
    default void renderHud(Minecraft minecraft, GuiGraphics graphics, DeltaTracker partialTick, float intensity) {}
}
