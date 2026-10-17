package com.notmjonter.stairs;

/** Short warning envelope, timed in client ticks rather than accelerated solar time. */
public final class FutureOrbReactionState {
    static final int GROW_TICKS = 4;
    static final int HOLD_TICKS = 20;
    static final int RETURN_TICKS = 40;
    private static int age = GROW_TICKS + HOLD_TICKS + RETURN_TICKS;
    private static float previous;
    private static float current;
    private FutureOrbReactionState() {}

    public static void begin() { age = 0; previous = current = 0; }
    public static void tick(boolean here) {
        if (!here) { reset(); return; }
        previous = current;
        age = Math.min(age + 1, GROW_TICKS + HOLD_TICKS + RETURN_TICKS);
        current = envelope(age);
    }
    static float envelope(int ticks) {
        if (ticks <= GROW_TICKS) return smooth(ticks / (float) GROW_TICKS);
        if (ticks <= GROW_TICKS + HOLD_TICKS) return 1;
        return 1 - smooth((ticks - GROW_TICKS - HOLD_TICKS) / (float) RETURN_TICKS);
    }
    private static float smooth(float value) {
        value = Math.clamp(value, 0, 1);
        return value * value * (3 - 2 * value);
    }
    public static float value(float partial) {
        return previous + (current - previous) * Math.clamp(partial, 0, 1);
    }
    public static float scale(float partial) { return 1 + 2 * value(partial); }
    public static void reset() { age = GROW_TICKS + HOLD_TICKS + RETURN_TICKS; previous = current = 0; }
}
