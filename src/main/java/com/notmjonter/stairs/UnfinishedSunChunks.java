package com.notmjonter.stairs;

import net.minecraft.world.level.ChunkPos;

/** Pure deterministic chunk-layout rules, kept independent of worldgen bootstrap. */
final class UnfinishedSunChunks {
    private UnfinishedSunChunks() {}

    static boolean isMissing(ChunkPos pos) {
        long value = (long) pos.x * 0x9E3779B97F4A7C15L ^ (long) pos.z * 0xC2B2AE3D27D4EB4FL;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return Math.floorMod(value, 11) == 0;
    }
}
