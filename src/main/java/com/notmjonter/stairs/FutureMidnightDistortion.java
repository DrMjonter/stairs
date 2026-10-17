package com.notmjonter.stairs;

import net.minecraft.client.Minecraft;

/** Short client pulse when the future world's clock crosses midnight. */
public final class FutureMidnightDistortion {
    private static final int DURATION = 40;
    private static long previousTime = Long.MIN_VALUE;
    private static float previous;
    private static float current;
    private static int remaining;

    private FutureMidnightDistortion() {}

    public static void tick(Minecraft minecraft, float dimensionIntensity) {
        previous = current;
        if (minecraft.level == null || !minecraft.level.dimension().equals(FutureDimension.KEY)
                || dimensionIntensity <= 0) {
            reset();
            return;
        }
        long now = minecraft.level.getDayTime();
        if (previousTime != Long.MIN_VALUE && FutureClock.crossedMidnight(previousTime, now)) {
            remaining = DURATION;
            FutureOrbAudio.midnightBoom(minecraft, dimensionIntensity);
        }
        previousTime = now;
        if (remaining > 0) remaining--;
        float age = 1 - remaining / (float) DURATION;
        current = remaining <= 0 ? 0 : dimensionIntensity
                * (float) (Math.sin(Math.PI * age) * (0.72 + 0.28 * Math.sin(age * Math.PI * 13)));
        current = Math.clamp(current, 0, 1);
    }

    public static float value(float partialTick) {
        return previous + (current - previous) * Math.clamp(partialTick, 0, 1);
    }

    public static double shaderValue() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0
                : Math.max(value(minecraft.getTimer().getGameTimeDeltaPartialTick(false)),
                    FutureContainmentState.distortion(minecraft.getTimer().getGameTimeDeltaPartialTick(false)));
    }

    private static void reset() {
        previousTime = Long.MIN_VALUE;
        previous = current = 0;
        remaining = 0;
    }
}
