package com.notmjonter.stairs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Directional ambience synchronized to the future world's distant orb. */
public final class FutureOrbAudio {
    static final ResourceLocation AMBIENCE = id("orb_ambience");
    static final ResourceLocation EXPANSION = id("orb_expansion");
    static final ResourceLocation STINGER = id("orb_stinger");
    static final ResourceLocation BOOM = id("orb_boom");
    static final ResourceLocation CONTAINMENT = id("orb_containment");
    private static final String AMBIENCE_FILE = "sounds/future/orb_ambience.ogg";
    private static final String EXPANSION_FILE = "sounds/future/orb_expansion.ogg";
    private static final String STINGER_FILE = "sounds/future/orb_stinger_01.ogg";
    private static final String BOOM_FILE = "sounds/future/orb_boom.ogg";
    private static final RandomSource RANDOM = RandomSource.create();
    private static OrbSound ambience;
    private static OrbSound expansion;
    private static OrbSound stinger;
    private static OrbSound boom;
    private static OrbSound warning;
    private static OrbSound containment;
    private static int retryDelay;
    private static int stingerDelay;

    private FutureOrbAudio() {}

    public static void tick(Minecraft minecraft, float dimensionIntensity) {
        if (minecraft.level == null || minecraft.player == null
                || !minecraft.level.dimension().equals(FutureDimension.KEY) || dimensionIntensity <= 0) {
            stop(minecraft);
            StaircaseAudio.orbIntensity(0);
            return;
        }

        double dayTicks = Math.floorMod(minecraft.level.getDayTime(), 24000);
        float night = FutureOrbAppearance.nightIntensity(dayTicks);
        // Keep the dedicated source reverberant even when its daylight volume is tiny.
        StaircaseAudio.orbIntensity(dimensionIntensity * (0.55f + 0.45f * night));

        var sounds = minecraft.getSoundManager();
        if (ambience != null && !sounds.isActive(ambience)) ambience = null;
        if (expansion != null && !sounds.isActive(expansion)) expansion = null;
        if (stinger != null && !sounds.isActive(stinger)) stinger = null;
        if (boom != null && !sounds.isActive(boom)) boom = null;
        if (warning != null && !sounds.isActive(warning)) warning = null;

        if (ambience == null && retryDelay-- <= 0) {
            retryDelay = 40;
            if (hasFile(minecraft, AMBIENCE_FILE)) {
                ambience = new OrbSound(AMBIENCE, true);
                ambience.update(minecraft, dimensionIntensity * (0.08f + 0.92f * night));
                sounds.play(ambience);
            }
        }
        if (ambience != null) {
            ambience.update(minecraft, dimensionIntensity * (0.08f + 0.92f * night));
        }

        // Minecraft 11:30 PM through midnight. This lasts 25 seconds on a normal
        // cycle and 0.625 seconds on a 40x cycle, so stop it from solar time rather
        // than relying on the source file's duration.
        boolean rapidlyExpanding = dayTicks >= 17500 && dayTicks < 18000;
        float expansionProgress = rapidlyExpanding ? (float) ((dayTicks - 17500) / 500.0) : 0;
        float expansionEnvelope = Math.min(1, expansionProgress * 10)
                * Math.min(1, (1 - expansionProgress) * 10);
        if (rapidlyExpanding && expansion == null && hasFile(minecraft, EXPANSION_FILE)) {
            expansion = new OrbSound(EXPANSION, true);
            expansion.update(minecraft, dimensionIntensity * expansionEnvelope);
            sounds.play(expansion);
        }
        if (expansion != null) {
            if (!rapidlyExpanding) {
                sounds.stop(expansion);
                expansion = null;
            } else {
                // Immediate onset for the accelerated day, with a short solar-time fade.
                expansion.update(minecraft, dimensionIntensity * expansionEnvelope);
            }
        }

        if (stingerDelay <= 0) stingerDelay = RANDOM.nextInt(1200, 3601);
        if (--stingerDelay <= 0 && stinger == null) {
            if (hasFile(minecraft, STINGER_FILE)) {
                stinger = new OrbSound(STINGER, false);
                stinger.update(minecraft, dimensionIntensity * (0.18f + 0.52f * night));
                sounds.play(stinger);
                // After the first 1-3 minute delay, leave 2-5 minutes between stingers.
                stingerDelay = RANDOM.nextInt(2400, 6001);
            } else {
                stingerDelay = 200;
            }
        }
        if (stinger != null) {
            stinger.update(minecraft, dimensionIntensity * (0.18f + 0.52f * night));
        }
        if (boom != null) boom.update(minecraft, dimensionIntensity);
        if (warning != null) warning.update(minecraft, 1);
    }

    public static void villagerWarning(Minecraft minecraft, int variant) {
        if (minecraft.level == null || minecraft.player == null
                || !minecraft.level.dimension().equals(FutureDimension.KEY)) return;
        if (variant < 0 || variant >= FutureVillagerReactions.SOUNDS.length) return;
        FutureOrbReactionState.begin();
        String file = "sounds/future/orb_warning_0" + (variant + 1) + ".ogg";
        if (!hasFile(minecraft, file)) return;
        var sounds = minecraft.getSoundManager();
        if (warning != null) sounds.stop(warning);
        warning = new OrbSound(FutureVillagerReactions.SOUNDS[variant], false);
        warning.update(minecraft, 1);
        sounds.play(warning);
    }

    public static void midnightBoom(Minecraft minecraft, float dimensionIntensity) {
        if (minecraft.level == null || !minecraft.level.dimension().equals(FutureDimension.KEY)
                || !hasFile(minecraft, BOOM_FILE)) return;
        var sounds = minecraft.getSoundManager();
        if (boom != null) sounds.stop(boom);
        boom = new OrbSound(BOOM, false);
        boom.update(minecraft, dimensionIntensity);
        sounds.play(boom);
    }

    public static void containment(Minecraft minecraft) {
        if (minecraft.level == null || !hasFile(minecraft, "sounds/future/orb_containment.ogg")) return;
        if (warning != null) minecraft.getSoundManager().stop(warning);
        if (containment != null) minecraft.getSoundManager().stop(containment);
        containment = new OrbSound(CONTAINMENT, false);
        containment.update(minecraft, 1);
        minecraft.getSoundManager().play(containment);
    }

    public static void tickContainment(Minecraft minecraft) {
        if (containment == null) return;
        if (minecraft.level == null || !FutureContainmentState.soundActive()) {
            minecraft.getSoundManager().stop(containment);
            containment = null;
        } else containment.update(minecraft, 1);
    }

    public static boolean isOrbSound(ResourceLocation sound) {
        return AMBIENCE.equals(sound) || EXPANSION.equals(sound) || STINGER.equals(sound)
                || BOOM.equals(sound) || CONTAINMENT.equals(sound) || FutureVillagerReactions.isReactionSound(sound);
    }

    private static boolean hasFile(Minecraft minecraft, String path) {
        return minecraft.getResourceManager().getResource(
                ResourceLocation.fromNamespaceAndPath(Stairs.MODID, path)).isPresent();
    }

    private static void stop(Minecraft minecraft) {
        if (minecraft == null) return;
        var sounds = minecraft.getSoundManager();
        if (ambience != null) sounds.stop(ambience);
        if (expansion != null) sounds.stop(expansion);
        if (stinger != null) sounds.stop(stinger);
        if (boom != null) sounds.stop(boom);
        if (warning != null) sounds.stop(warning);
        ambience = null;
        expansion = null;
        stinger = null;
        boom = null;
        warning = null;
        retryDelay = 0;
        stingerDelay = 0;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "future." + path);
    }

    private static final class OrbSound extends AbstractTickableSoundInstance {
        OrbSound(ResourceLocation id, boolean loop) {
            super(SoundEvent.createFixedRangeEvent(id, 96), SoundSource.AMBIENT, RandomSource.create());
            attenuation = SoundInstance.Attenuation.LINEAR;
            looping = loop;
            delay = 0;
            volume = 0;
        }

        @Override public boolean canStartSilent() { return true; }

        @Override public void tick() {}

        void update(Minecraft minecraft, float gain) {
            var listener = minecraft.gameRenderer.getMainCamera().getPosition();
            // The render direction is (0.6, 0.22, -0.77), normalized. Keeping the
            // source 48 blocks from the listener preserves a stable celestial bearing.
            x = listener.x + 0.60156 * 48;
            y = listener.y + 0.22057 * 48;
            z = listener.z - 0.77199 * 48;
            volume = Math.clamp(gain, 0, 1);
        }
    }
}
