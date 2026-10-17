package com.notmjonter.stairs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Client-only streamed playlist. Owns the previous vanilla track only during its fade. */
public final class GarageMusic {
    private static final int FADE_TICKS = 80;
    private static final int TRACK_COUNT = 3;
    private static SoundInstance outgoing;
    private static float outgoingGain = 1;
    private static Track playing;
    private static int nextSlot;
    private static int retryDelay;

    private GarageMusic() {}

    /** Called before vanilla music selection, so it cannot abruptly stop the old track. */
    public static boolean tick(Minecraft mc, SoundInstance vanillaMusic) {
        var sounds = mc.getSoundManager();
        if (mc.level == null) {
            if (outgoing != null) sounds.stop(outgoing);
            if (playing != null) sounds.stop(playing);
            outgoing = null;
            playing = null;
            retryDelay = 0;
            return false;
        }
        if (mc.isPaused()) return playing != null || outgoing != null;
        // Destination 1 now uses the future world's vanilla music selection.
        boolean garage = false;
        if (playing != null && !sounds.isActive(playing)) playing = null;
        if (garage && playing == null && retryDelay-- <= 0) {
            retryDelay = 100; // Avoid retrying muted/failed streams every tick.
            if (mc.options.getSoundSourceVolume(SoundSource.MUSIC) > 0
                    && mc.options.getSoundSourceVolume(SoundSource.MASTER) > 0) {
                for (int offset = 0; offset < TRACK_COUNT; offset++) {
                    int slot = (nextSlot + offset) % TRACK_COUNT + 1;
                    ResourceLocation file = ResourceLocation.fromNamespaceAndPath(Stairs.MODID,
                            "sounds/music/garage/garage_0" + slot + ".ogg");
                    if (mc.getResourceManager().getResource(file).isEmpty()) continue;
                    playing = new Track(ResourceLocation.fromNamespaceAndPath(Stairs.MODID,
                            "music.garage_0" + slot));
                    sounds.play(playing);
                    if (!sounds.isActive(playing)) {
                        playing = null;
                        continue;
                    }
                    nextSlot = slot % TRACK_COUNT;
                    if (vanillaMusic != null && sounds.isActive(vanillaMusic)) {
                        outgoing = vanillaMusic;
                        outgoingGain = 1;
                    }
                    break;
                }
            }
        }
        if (outgoing != null) {
            outgoingGain = Math.max(0, outgoingGain - 1f / FADE_TICKS);
            if (outgoingGain <= 0 || !sounds.isActive(outgoing)) {
                sounds.stop(outgoing);
                outgoing = null;
            } else {
                // Recalculate existing non-tickable music without changing the user's slider.
                sounds.updateSourceVolume(SoundSource.MUSIC,
                        mc.options.getSoundSourceVolume(SoundSource.MUSIC));
            }
        }
        if (playing != null) {
            playing.fade(garage);
            if (playing.isStopped()) {
                sounds.stop(playing);
                playing = null;
            }
        }
        if (!garage) retryDelay = 0;
        // Missing files leave normal music selection intact.
        return playing != null || outgoing != null;
    }

    public static float volume(SoundInstance sound, float vanillaVolume) {
        return sound == outgoing ? vanillaVolume * outgoingGain : vanillaVolume;
    }

    private static final class Track extends AbstractTickableSoundInstance {
        Track(ResourceLocation id) {
            super(SoundEvent.createVariableRangeEvent(id), SoundSource.MUSIC, RandomSource.create());
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
            volume = 0;
        }

        @Override
        public boolean canStartSilent() { return true; }

        @Override
        public void tick() {} // Envelope follows MusicManager, which already respects pause.

        void fade(boolean inGarage) {
            volume = Math.clamp(volume + (inGarage ? 1f : -1f) / FADE_TICKS, 0, 1);
            if (!inGarage && volume <= 0) stop();
        }
    }
}
