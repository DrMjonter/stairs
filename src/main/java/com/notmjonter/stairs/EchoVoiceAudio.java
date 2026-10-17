package com.notmjonter.stairs;

/** Pure PCM helpers: original plus four progressively quieter echoes. */
public final class EchoVoiceAudio {
    public static final int REPEATS = 4;
    public static final long PHRASE_GAP_NANOS = 1_000_000_000L;
    public static final int SAMPLE_RATE = 48_000;
    public static final float LOW_CUT_HZ = 300;
    public static final double TRAVEL_STEP = 6;
    public static final long TAIL_NANOS = 2_500_000_000L;

    private EchoVoiceAudio() {}

    public static float gain(int repeat) {
        if (repeat < 1 || repeat > REPEATS) throw new IllegalArgumentException("Invalid voice echo index");
        return EchoReplayConfig.REPLAY_VOLUME * (float) Math.pow(0.5, repeat - 1);
    }

    public static short[] scaled(short[] original, float gain) {
        short[] result = new short[original.length];
        gain = Float.isFinite(gain) ? Math.clamp(gain, 0, 1) : 0;
        for (int i = 0; i < original.length; i++) result[i] = (short) Math.round(original[i] * gain);
        return result;
    }

    public static net.minecraft.world.phys.Vec3 position(net.minecraft.world.phys.Vec3 origin,
            net.minecraft.world.phys.Vec3 facing, int repeat) {
        gain(repeat); // Validate the repeat index.
        return origin.add(facing.normalize().scale(TRAVEL_STEP * repeat));
    }

    /** Stateful high-pass plus diffuse feedback reverb; one instance per phrase/repeat. */
    public static final class Processor {
        private final float[][] combs = {new float[1493], new float[1601], new float[1747], new float[1999]};
        private final int[] combIndex = new int[4];
        private final float[] damped = new float[4];
        private final float[][] diffusers = {new float[225], new float[556]};
        private final int[] diffuserIndex = new int[2];
        private final float highPassAlpha = (float) (1.0 / (1.0 + 2 * Math.PI * LOW_CUT_HZ / SAMPLE_RATE));
        private float previousInput, previousHighPass;

        public short[] process(short[] pcm, float gain) {
            gain = Float.isFinite(gain) ? Math.clamp(gain, 0, 1) : 0;
            short[] output = new short[pcm.length];
            for (int sample = 0; sample < pcm.length; sample++) {
                float input = pcm[sample] / 32768f;
                float filtered = highPassAlpha * (previousHighPass + input - previousInput);
                previousInput = input;
                previousHighPass = filtered;
                float wet = 0;
                for (int i = 0; i < combs.length; i++) {
                    int index = combIndex[i];
                    float reflected = combs[i][index];
                    damped[i] += 0.75f * (reflected - damped[i]);
                    combs[i][index] = filtered + damped[i] * 0.88f;
                    wet += reflected * 0.25f;
                    combIndex[i] = (index + 1) % combs[i].length;
                }
                for (int i = 0; i < diffusers.length; i++) {
                    int index = diffuserIndex[i];
                    float delayed = diffusers[i][index];
                    float diffused = delayed - wet;
                    diffusers[i][index] = wet + delayed * 0.5f;
                    wet = diffused;
                    diffuserIndex[i] = (index + 1) % diffusers[i].length;
                }
                float mixed = (filtered * 0.20f + wet * 0.80f) * gain;
                // Smoothly tame summed reflections rather than hard-clipping loud voices.
                mixed /= 1 + Math.abs(mixed) * 0.35f;
                output[sample] = (short) Math.round(Math.clamp(mixed * 32767, -32768, 32767));
            }
            return output;
        }
    }
}
