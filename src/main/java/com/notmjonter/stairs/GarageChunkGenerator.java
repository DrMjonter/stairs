package com.notmjonter.stairs;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/** One continuous underground parking level, generated seamlessly across chunks. */
public final class GarageChunkGenerator extends ChunkGenerator {
    public static final MapCodec<GarageChunkGenerator> CODEC = BiomeSource.CODEC.fieldOf("biome_source")
            .xmap(GarageChunkGenerator::new, GarageChunkGenerator::getBiomeSource);
    public static final int FLOOR = 48;
    public GarageChunkGenerator(BiomeSource biomes) { super(biomes); }
    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    public static BlockState blockAt(int x, int y, int z) {
        int rx = Math.floorMod(x, 32), rz = Math.floorMod(z, 32);
        int section = Math.floorDiv(x, 32) * 31 + Math.floorDiv(z, 32) * 17;
        if (y == 46 || y == 56) return Blocks.BEDROCK.defaultBlockState();
        if (y == 47 || y == 55) return Blocks.GRAY_CONCRETE.defaultBlockState();
        if (y == FLOOR) {
            if ((rx == 12 || rx == 20) && rz % 8 < 5) return Blocks.YELLOW_CONCRETE.defaultBlockState();
            if ((rx >= 2 && rx <= 10 || rx >= 22 && rx <= 29) && rz % 8 == 2)
                return Blocks.WHITE_CONCRETE.defaultBlockState();
            if (Math.floorMod(x * 13 + z * 7, 97) < 4) return Blocks.GRAY_CONCRETE.defaultBlockState();
            return Blocks.SMOOTH_STONE.defaultBlockState();
        }
        if (y < 49 || y > 54) return Blocks.AIR.defaultBlockState();
        boolean pillar = (rx == 10 || rx == 11 || rx == 21 || rx == 22) && (rz % 16 <= 1);
        if (pillar) {
            if (y == 50 || y == 51) return ((rx + rz + y) % 2 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE).defaultBlockState();
            return Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        }
        if (rx == 0 && (rz >= 3 && rz <= 11 || rz >= 21 && rz <= 29))
            return (y == 50 ? Blocks.GRAY_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState();
        if (y == 54) return Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        if (y == 53) {
            if (rz % 16 == 0) return Blocks.GRAY_CONCRETE.defaultBlockState();
            if (rx >= 15 && rx <= 17 && (rz == 8 || rz == 24)) {
                return Math.floorMod(section + rz, 5) == 0 ? Blocks.GRAY_CONCRETE.defaultBlockState()
                        : GarageDimension.FLUORESCENT.get().defaultBlockState();
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        var ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        var surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 46; y <= 56; y++) {
            int worldX = chunk.getPos().getMinBlockX() + x, worldZ = chunk.getPos().getMinBlockZ() + z;
            BlockState state = blockAt(worldX, y, worldZ);
            if (state.isAir()) continue;
            chunk.setBlockState(pos.set(worldX, y, worldZ), state, false);
            ocean.update(x, y, z, state);
            surface.update(x, y, z, state);
        }
        return CompletableFuture.completedFuture(chunk);
    }
    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) { return 57; }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) states[i] = blockAt(x, level.getMinBuildHeight() + i, z);
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }
    @Override public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {}
    @Override public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomes,
            StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {}
    @Override public void spawnOriginalMobs(WorldGenRegion level) {}
    @Override public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) { info.add("Parking level B1"); }
    @Override public int getMinY() { return 0; }
    @Override public int getGenDepth() { return 128; }
    @Override public int getSeaLevel() { return 0; }
}
