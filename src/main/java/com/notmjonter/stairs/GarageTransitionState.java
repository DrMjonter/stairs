package com.notmjonter.stairs;

/** Client fade state; independent of client classloading and the ascent distortion. */
public final class GarageTransitionState {
    private static boolean holding;
    private static float opacity;
    private static float previous;
    private static int ticks;
    private GarageTransitionState() {}
    public static void begin() { holding = true; ticks = 0; }
    public static void cover() { begin(); opacity = previous = 1; }
    public static boolean holding() { return holding; }
    public static void release() { holding = false; }
    public static void tick() {
        if (++ticks > 800) holding = false;
        previous = opacity;
        opacity = Math.clamp(opacity + (holding ? 0.07f : -0.04f), 0, 1);
    }
    public static float value(float partial) { return previous + (opacity - previous) * Math.clamp(partial, 0, 1); }
    public static void reset() { holding = false; opacity = previous = 0; ticks = 0; }
}
