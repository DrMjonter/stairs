package com.notmjonter.stairs;

/** Shared real-tick timing for the global hostility reset. Survives the return dimension transition. */
public final class FutureContainmentState {
    public static final int TEXT_TICK = 60;
    public static final int DISTORTION_TICK = 120;
    public static final int TRANSFER_TICK = 200;
    public static final int SOUND_END_TICK = 260;
    private static int age = -1;
    private static int previous;
    private static float baseRadius = 15;
    private FutureContainmentState() {}
    public static void begin(int ticks) { age = previous = Math.max(0, ticks); }
    public static void baseRadius(float radius) { baseRadius = radius; }
    public static float radius(float partial) { return baseRadius * scale(partial); }
    public static void cover() { age = previous = Math.max(TRANSFER_TICK, age); }
    public static void tick() {
        if (age < 0) return;
        previous = age;
        if (++age >= SOUND_END_TICK + 40) reset();
    }
    public static boolean active() { return age >= 0; }
    public static boolean showText() { return age >= TEXT_TICK && age < TRANSFER_TICK; }
    public static boolean soundActive() { return age >= 0 && age < SOUND_END_TICK; }
    public static float time(float partial) { return previous + (age - previous) * Math.clamp(partial, 0, 1); }
    public static float distortionAt(float ticks) {
        float progress = Math.clamp((ticks - DISTORTION_TICK) / (TRANSFER_TICK - DISTORTION_TICK), 0, 1);
        return progress * progress * (3 - 2 * progress);
    }
    public static float skyCoverage(float partial) {
        return active() ? distortionAt(time(partial)) : 0;
    }
    public static float distortion(float partial) {
        return !active() || age >= TRANSFER_TICK ? 0 : distortionAt(time(partial));
    }
    public static float scale(float partial) {
        if (!active()) return 1;
        float progress = Math.clamp(time(partial) / 8f, 0, 1);
        return 1 + 7 * progress * progress * (3 - 2 * progress);
    }
    public static float blackout(float partial) {
        if (!active() || age < TRANSFER_TICK) return 0;
        return 1 - Math.clamp((time(partial) - SOUND_END_TICK) / 40f, 0, 1);
    }
    public static void reset() { age = -1; previous = 0; }
}
