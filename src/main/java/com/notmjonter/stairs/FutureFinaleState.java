package com.notmjonter.stairs;

/** Client animation state for the fifth-midnight orb expansion. */
public final class FutureFinaleState {
    static final int GROW_TICKS = 120;
    private static boolean active;
    private static int age;
    private static float previousScale = 1;
    private static float scale = 1;

    private FutureFinaleState() {}

    public static void begin() {
        active = true;
        age = 0;
        previousScale = scale = 1;
    }

    public static void tick(boolean inFutureDimension) {
        if (!inFutureDimension) {
            reset();
            return;
        }
        previousScale = scale;
        if (active) scale = scaleAt(++age);
    }

    static float scaleAt(int ticks) {
        float amount = Math.clamp(ticks / (float) GROW_TICKS, 0, 1);
        amount = amount * amount * (3 - 2 * amount);
        return 1 + 99 * amount;
    }

    public static float scale(float partialTick) {
        return previousScale + (scale - previousScale) * Math.clamp(partialTick, 0, 1);
    }

    public static boolean active() { return active; }

    public static void reset() {
        active = false;
        age = 0;
        previousScale = scale = 1;
    }
}
