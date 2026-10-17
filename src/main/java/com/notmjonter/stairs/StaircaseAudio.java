package com.notmjonter.stairs;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/** Per-source SPR environments. Maps and OpenAL callbacks are sound-thread-only. */
public final class StaircaseAudio {
    private static volatile float intensity;
    private static volatile float mirror;
    private static volatile float future;
    private static volatile float orb;
    private static volatile float echo;
    private static final Map<Integer, Boolean> eligible = new HashMap<>();
    private static final Map<Integer, Boolean> orbSources = new HashMap<>();
    private static final Map<Integer, Environment> environments = new HashMap<>();
    private static boolean replaying;

    private StaircaseAudio() {}

    public static void intensity(float value) {
        intensity = Float.isFinite(value) ? Math.clamp(value, 0, 1) : 0;
    }

    public static void mirrorIntensity(float value) {
        mirror = Float.isFinite(value) ? Math.clamp(value, 0, 1) : 0;
    }

    public static void resetMirror() { mirror = 0; }

    public static void echoIntensity(float value) {
        echo = Float.isFinite(value) ? Math.clamp(value, 0, 1.5f) : 0;
    }

    public static void futureIntensity(float value) {
        future = Float.isFinite(value) ? Math.clamp(value, 0, 1) : 0;
    }

    static float futureReverbStrength(long dayTime) {
        int time = Math.floorMod(dayTime, 24000);
        float daytime = 0.65f;
        if (time >= 13000 && time < 23000) return 1;
        if (time >= 11000 && time < 13000) {
            return daytime + (1 - daytime) * smooth((time - 11000) / 2000f);
        }
        if (time >= 23000 || time < 1000) {
            float progress = (Math.floorMod(time - 23000, 24000)) / 2000f;
            return 1 - (1 - daytime) * smooth(progress);
        }
        return daytime;
    }

    private static float smooth(float amount) {
        amount = Math.clamp(amount, 0, 1);
        return amount * amount * (3 - 2 * amount);
    }

    public static void orbIntensity(float value) {
        orb = Float.isFinite(value) ? Math.clamp(value, 0, 1) : 0;
    }

    public static boolean affects(SoundSource category, boolean auxOnly) {
        return !auxOnly && category != null && switch (category) {
            case AMBIENT, BLOCKS, WEATHER, HOSTILE, NEUTRAL, PLAYERS, RECORDS -> true;
            default -> false;
        };
    }

    public static void category(int source, SoundSource category, ResourceLocation sound, boolean auxOnly) {
        boolean orbSource = !auxOnly && FutureOrbAudio.isOrbSound(sound);
        eligible.put(source, orbSource || affects(category, auxOnly));
        orbSources.put(source, orbSource);
    }

    // Returns true when the caller's environment has already been applied.
    public static boolean environment(int source, float[] original, Writer writer) {
        if (replaying) return false;
        if (!eligible.getOrDefault(source, false)) {
            environments.remove(source);
            return false;
        }
        Environment environment = new Environment(original, writer, orbSources.getOrDefault(source, false));
        environments.put(source, environment);
        if (intensity <= 0 && mirror <= 0 && future <= 0 && echo <= 0
                && (!environment.orbSource || orb <= 0)) return false;
        apply(source, environment, intensity, mirror, future, orb);
        return true;
    }

    public static void update(int source) {
        Environment environment = environments.get(source);
        if (environment != null && (environment.applied != intensity || environment.mirrorApplied != mirror
                || environment.futureApplied != future || environment.echoApplied != echo
                || environment.orbSource && environment.orbApplied != orb)) {
            apply(source, environment, intensity, mirror, future, orb);
        }
    }

    private static void apply(int source, Environment environment, float amount, float mirrorAmount,
            float futureAmount, float orbAmount) {
        replaying = true;
        try {
            environment.writer.write(source, blend(environment.original, amount, mirrorAmount, futureAmount,
                    environment.orbSource ? orbAmount : 0, echo));
            environment.applied = amount;
            environment.mirrorApplied = mirrorAmount;
            environment.futureApplied = futureAmount;
            environment.orbApplied = orbAmount;
            environment.echoApplied = echo;
        } finally {
            replaying = false;
        }
    }

    public static float[] blend(float[] original, float amount) {
        return blend(original, amount, 0);
    }

    public static float[] blend(float[] original, float amount, float mirrorAmount) {
        return blend(original, amount, mirrorAmount, 0);
    }

    public static float[] blend(float[] original, float amount, float mirrorAmount, float orbAmount) {
        return blend(original, amount, mirrorAmount, 0, orbAmount);
    }

    public static float[] blend(float[] original, float amount, float mirrorAmount,
            float futureAmount, float orbAmount) {
        return blend(original, amount, mirrorAmount, futureAmount, orbAmount, 0);
    }

    public static float[] blend(float[] original, float amount, float mirrorAmount,
            float futureAmount, float orbAmount, float echoAmount) {
        float dream = Float.isFinite(mirrorAmount) ? Math.clamp(mirrorAmount, 0, 1) : 0;
        float strength = Float.isFinite(amount) ? Math.clamp(amount, 0, 1) : 0;
        float[] result = original.clone();
        // Preserve the real environment, adding a diffuse tail even in open air.
        float[] reverb = {0.12f, 0.22f, 0.35f, 0.5f};
        for (int i = 0; i < 4; i++) {
            result[i] += (Math.max(result[i], reverb[i]) - result[i]) * strength;
            result[i + 4] *= 1 - 0.7f * strength;
        }
        result[8] *= 1 - 0.97f * strength; // Direct high frequencies: muffling.
        result[9] *= 1 - 0.45f * strength; // Keep the dry signal audible.
        // A more reflective, less muffled environment than the staircase itself.
        float[] dreamReverb = {0.18f, 0.30f, 0.50f, 0.70f};
        for (int i = 0; i < 4; i++) {
            result[i] += (Math.max(result[i], dreamReverb[i]) - result[i]) * dream;
            result[i + 4] *= 1 - 0.35f * dream;
        }
        result[8] *= 1 - 0.60f * dream;
        result[9] *= 1 - 0.18f * dream;
        // A broad but restrained outdoor echo for all in-world sounds in 33.X.
        float emptyWorld = Float.isFinite(futureAmount) ? Math.clamp(futureAmount, 0, 1) : 0;
        float[] futureReverb = {0.10f, 0.20f, 0.38f, 0.58f};
        for (int i = 0; i < 4; i++) {
            result[i] += (Math.max(result[i], futureReverb[i]) - result[i]) * emptyWorld;
            result[i + 4] *= 1 - 0.15f * emptyWorld;
        }
        result[8] *= 1 - 0.28f * emptyWorld;
        result[9] *= 1 - 0.08f * emptyWorld;
        // The orb is an impossibly distant source: broad late reflections, very
        // little high-frequency direct signal, and enough dry sound for direction.
        float distant = Float.isFinite(orbAmount) ? Math.clamp(orbAmount, 0, 1) : 0;
        float[] orbReverb = {0.35f, 0.55f, 0.75f, 0.90f};
        for (int i = 0; i < 4; i++) {
            result[i] += (Math.max(result[i], orbReverb[i]) - result[i]) * distant;
            result[i + 4] *= 1 - 0.55f * distant;
        }
        result[8] *= 1 - 0.88f * distant;
        result[9] *= 1 - 0.30f * distant;
        // Echo Basin: strong late reflections, with a clear dry signal for orientation.
        float basin = Float.isFinite(echoAmount) ? Math.clamp(echoAmount, 0, 1.5f) : 0;
        float[] basinReverb = {0.55f, 0.75f, 0.90f, 1.0f};
        for (int i = 0; i < 4; i++) {
            result[i] += (Math.max(result[i], basinReverb[i]) - result[i]) * basin;
            result[i + 4] *= 1 - 0.25f * basin;
        }
        result[8] *= 1 - 0.20f * basin;
        result[9] *= 1 - 0.10f * basin;
        return result;
    }

    public static void forget(int source) {
        eligible.remove(source);
        orbSources.remove(source);
        environments.remove(source);
    }

    public static void clear() {
        eligible.clear();
        orbSources.clear();
        environments.clear();
    }

    @FunctionalInterface
    public interface Writer { void write(int source, float[] values); }

    private static final class Environment {
        final float[] original;
        final Writer writer;
        float applied;
        float mirrorApplied;
        float futureApplied;
        float orbApplied;
        float echoApplied;
        final boolean orbSource;
        Environment(float[] original, Writer writer, boolean orbSource) {
            this.original = original;
            this.writer = writer;
            this.orbSource = orbSource;
        }
    }
}
