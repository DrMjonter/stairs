package com.notmjonter.stairs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** The fixed-noon Overworld reconstruction used for destination two. */
public final class UnfinishedSunDimension {
    public static final int DESTINATION = 2;
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "dimension_2"));

    private UnfinishedSunDimension() {}

    /** Keep arrivals out of deterministic void chunks while preserving their local block coordinates. */
    static BlockPos safeArrivalColumn(BlockPos requested) {
        ChunkPos source = new ChunkPos(requested);
        if (!UnfinishedSunChunks.isMissing(source)) return requested;
        int localX = Math.floorMod(requested.getX(), 16);
        int localZ = Math.floorMod(requested.getZ(), 16);
        for (int radius = 1; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                ChunkPos candidate = new ChunkPos(source.x + dx, source.z + dz);
                if (!UnfinishedSunChunks.isMissing(candidate)) {
                    return new BlockPos(candidate.getMinBlockX() + localX, requested.getY(),
                            candidate.getMinBlockZ() + localZ);
                }
            }
        }
        throw new IllegalStateException("No solid Unfinished Sun arrival chunk found");
    }
}
