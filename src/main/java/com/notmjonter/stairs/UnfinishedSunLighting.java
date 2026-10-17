package com.notmjonter.stairs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Leaves a stable trail of full-strength invisible lights behind exploring players. */
public final class UnfinishedSunLighting {
    private static final int SPREAD = 3;
    private static final int[][] OFFSETS = {
            {-SPREAD, -SPREAD}, {-SPREAD, SPREAD}, {SPREAD, -SPREAD}, {SPREAD, SPREAD}
    };
    private UnfinishedSunLighting() {}

    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != UnfinishedSunDimension.KEY
                || level.getGameTime() % 5 != 0) return;
        var light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        for (var player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            BlockPos center = player.blockPosition();
            for (int[] offset : OFFSETS) {
                BlockPos pos = center.offset(offset[0], 0, offset[1]);
                if (level.isInWorldBounds(pos) && level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, light, 3);
                }
            }
        }
    }
}
