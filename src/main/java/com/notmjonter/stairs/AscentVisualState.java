package com.notmjonter.stairs;

/** Client animation state without references to client-only Minecraft classes. */
public final class AscentVisualState {
    private static float target;
    private static float previous;
    private static float current;
    private static int age;
    private static float previousFov;
    private static float currentFov;

    private AscentVisualState() {}

    public static void receive(float intensity) {
        target = Float.isFinite(intensity) ? Math.clamp(intensity, 0, 1) : 0;
        age = 0;
    }

    public static void tick() {
        // Preserve the envelope while the dimension transfer is holding the screen.
        if (!GarageTransitionState.holding() && ++age > 15) target = 0;
        previousFov = currentFov;
        currentFov += (target - currentFov) * 0.08f;
        if (target == 0 && currentFov < 0.001f) currentFov = 0;
        previous = current;
        current += (target - current) * (target > current ? 0.3f : 0.25f);
        if (target == 0 && current < 0.001f) current = 0;
        StaircaseAudio.intensity(current);
    }

    public static float value(float partialTick) {
        return previous + (current - previous) * Math.clamp(partialTick, 0, 1);
    }

    public static float fovValue(float partialTick) {
        return previousFov + (currentFov - previousFov) * Math.clamp(partialTick, 0, 1);
    }

    public static void reset() {
        target = previous = current = previousFov = currentFov = 0;
        age = 0;
        StaircaseAudio.intensity(0);
    }
}
