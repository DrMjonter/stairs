package com.notmjonter.stairs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** A non-positional ambience bed whose gain follows the Echo Basin transition. */
public final class EchoBasinAmbience {
    private static final String FILE = "sounds/echo_basin/ambience.ogg";
    private static AmbienceSound playing;
    private static int retryDelay;

    private EchoBasinAmbience() {}

    public static void tick(Minecraft minecraft, float intensity) {
        if (minecraft.level == null || minecraft.player == null) {
            stop(minecraft);
            return;
        }

        var sounds = minecraft.getSoundManager();
        if (playing != null && !sounds.isActive(playing)) playing = null;

        boolean inBasin = minecraft.level.dimension().equals(EchoBasinDimension.KEY);
        if (playing == null && inBasin && intensity > 0 && retryDelay-- <= 0) {
            retryDelay = 40;
            if (minecraft.getResourceManager().getResource(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Stairs.MODID, FILE)).isPresent()) {
                playing = new AmbienceSound();
                playing.setGain(intensity);
                sounds.play(playing);
            }
        }

        if (playing != null) {
            playing.setGain(intensity);
            if (intensity <= 0) {
                sounds.stop(playing);
                playing = null;
                retryDelay = 0;
            }
        }
    }

    public static boolean isAmbience(SoundInstance sound) {
        return sound != null && sound.getLocation().equals(EchoSounds.AMBIENCE.getLocation());
    }

    private static void stop(Minecraft minecraft) {
        if (minecraft != null && playing != null) minecraft.getSoundManager().stop(playing);
        playing = null;
        retryDelay = 0;
    }

    private static final class AmbienceSound extends AbstractTickableSoundInstance {
        AmbienceSound() {
            super(EchoSounds.AMBIENCE, SoundSource.AMBIENT, RandomSource.create());
            attenuation = SoundInstance.Attenuation.NONE;
            relative = true;
            looping = true;
            delay = 0;
            volume = 0;
        }

        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() {}

        void setGain(float gain) {
            volume = Math.clamp(gain, 0, 1);
        }
    }
}
