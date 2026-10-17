package com.notmjonter.stairs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Player-anchored positional whisper which continually points toward a Listening Well. */
public final class EchoGuidanceWhisper {
    private static final double DISTANCE = 10;

    private EchoGuidanceWhisper() {}

    public static void play(BlockPos well) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.level.dimension() != EchoBasinDimension.KEY) return;
        minecraft.getSoundManager().play(new GuidanceSound(well.immutable()));
    }

    static Vec3 position(BlockPos well) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return Vec3.ZERO;
        double dx = well.getX() + 0.5 - minecraft.player.getX();
        double dz = well.getZ() + 0.5 - minecraft.player.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) {
            dx = 0;
            dz = 1;
            length = 1;
        }
        return new Vec3(minecraft.player.getX() + dx / length * DISTANCE,
                minecraft.player.getEyeY(), minecraft.player.getZ() + dz / length * DISTANCE);
    }

    static final class GuidanceSound extends AbstractTickableSoundInstance {
        final BlockPos well;

        GuidanceSound(BlockPos well) {
            super(EchoSounds.WELL_GUIDANCE_WHISPER, SoundSource.AMBIENT, RandomSource.create());
            this.well = well;
            attenuation = SoundInstance.Attenuation.LINEAR;
            looping = false;
            delay = 0;
            volume = 0.65F;
            pitch = 0.92F + random.nextFloat() * 0.16F;
            updatePosition();
        }

        @Override public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null
                    || minecraft.level.dimension() != EchoBasinDimension.KEY) {
                stop();
                return;
            }
            updatePosition();
        }

        private void updatePosition() {
            Vec3 position = position(well);
            x = position.x;
            y = position.y;
            z = position.z;
        }
    }
}
