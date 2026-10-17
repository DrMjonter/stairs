package com.notmjonter.stairs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

public final class GarbageCollectorDimension {
    public static final int DESTINATION = 4;
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "dimension_4"));

    private GarbageCollectorDimension() {}

    static BlockPos safeArrivalColumn(ServerLevel level, BlockPos requested) {
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                // Center the exit in a cell, away from the void seams and ocean rims.
                BlockPos column = new BlockPos(((requested.getX() >> 4) + dx) * 16 + 8,
                        requested.getY(), ((requested.getZ() >> 4) + dz) * 16 + 8);
                level.getChunkAt(column);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        column.getX(), column.getZ()) - 1;
                if (y > level.getMinBuildHeight() + 12
                        && level.getFluidState(new BlockPos(column.getX(), y, column.getZ())).isEmpty()) return column;
            }
        }
        throw new IllegalStateException("No solid Garbage Collector arrival cell found");
    }
}
