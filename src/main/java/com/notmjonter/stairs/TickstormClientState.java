package com.notmjonter.stairs;

import net.minecraft.client.Minecraft;

/** Smooth client envelope for the synchronized storm shader treatment. */
public final class TickstormClientState {
    private static float previous;
    private static float current;
    private static boolean active;

    private TickstormClientState() {}

    public static void receive(boolean value) { active = value; }

    public static void tick(Minecraft minecraft, float dimensionIntensity) {
        previous = current;
        boolean here = minecraft.level != null && minecraft.level.dimension().equals(TickstormDimension.KEY);
        float target = here && active ? dimensionIntensity : 0;
        current += Math.clamp(target - current, -0.025f, 0.025f);
        if (here) {
            // Tickstorm is a temporal event, not a weather event. Keep the vanilla
            // weather state clear even when a weather renderer is installed.
            minecraft.level.setRainLevel(0);
            minecraft.level.setThunderLevel(0);
        }
        if (!here && current <= 0) reset();
    }

    public static double shaderValue() {
        Minecraft minecraft = Minecraft.getInstance();
        float partial = minecraft.level == null ? 0 : minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        return previous + (current - previous) * Math.clamp(partial, 0, 1);
    }

    public static void reset() { active = false; previous = current = 0; }
}
