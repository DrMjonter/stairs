package com.notmjonter.stairs;

/** Shared, continuous appearance curve for the geometry and shader-pack renderers. */
final class FutureOrbAppearance {
    private FutureOrbAppearance() {}

    static float dusk(double sunHeight) {
        float amount = (float) Math.clamp((0.65 - sunHeight) / 1.30, 0, 1);
        return amount * amount * (3 - 2 * amount);
    }

    static float radius(double dayTicks) {
        // Minecraft noon = 6000, 11:30 PM = 17500, midnight = 18000.
        // Use clock time, not the eased sun angle, so the midnight surge lands on time.
        double fromNoon = ((dayTicks - 6000) % 24000 + 24000) % 24000;
        if (fromNoon <= 11500) return blend(15, 60, fromNoon / 11500);
        if (fromNoon <= 12000) return blend(60, 120, (fromNoon - 11500) / 500);
        // The morning decline spans the full twelve hours rather than undoing
        // the midnight surge immediately. All joins have zero slope.
        return blend(120, 15, (fromNoon - 12000) / 12000);
    }

    static float nightIntensity(double dayTicks) {
        return Math.clamp((radius(dayTicks) - 15) / 105, 0, 1);
    }

    private static float blend(float from, float to, double amount) {
        double eased = amount * amount * (3 - 2 * amount);
        return (float) (from + (to - from) * eased);
    }

    static float opacity(float dusk, float rain) {
        return (0.08f + 0.92f * dusk) * (1 - Math.clamp(rain, 0, 1) * 0.85f);
    }
}
