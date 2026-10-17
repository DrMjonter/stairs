package com.notmjonter.stairs;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Vanilla Overworld noise with its surface relief compressed around sea level. */
public final class TickstormChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<TickstormChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(TickstormChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(TickstormChunkGenerator::generatorSettings)
    ).apply(instance, TickstormChunkGenerator::new));
    public TickstormChunkGenerator(BiomeSource biomes, Holder<NoiseGeneratorSettings> settings) {
        super(biomes, settings);
    }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
            StructureManager structures, ChunkAccess chunk) {
        return super.fillFromNoise(blender, random, structures, chunk).thenApply(generated -> {
            flatten(generated);
            return generated;
        });
    }

    private static void flatten(ChunkAccess chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int min = chunk.getMinBuildHeight();
        int max = chunk.getMaxBuildHeight();
        for (int localX = 0; localX < 16; localX++) for (int localZ = 0; localZ < 16; localZ++) {
            int worldX = chunk.getPos().getMinBlockX() + localX;
            int worldZ = chunk.getPos().getMinBlockZ() + localZ;
            int surface = solidSurface(chunk, pos, worldX, worldZ, min, max);
            reshapeColumn(chunk, pos, worldX, worldZ, surface, TickstormTerrain.flattenedSurface(surface), max);
        }
        Heightmap.primeHeightmaps(chunk, Set.of(Heightmap.Types.WORLD_SURFACE_WG,
                Heightmap.Types.OCEAN_FLOOR_WG));
    }

    private static int solidSurface(ChunkAccess chunk, BlockPos.MutableBlockPos pos,
            int x, int z, int min, int max) {
        for (int y = max - 1; y >= min; y--) {
            BlockState state = chunk.getBlockState(pos.set(x, y, z));
            if (!state.isAir() && state.getFluidState().isEmpty()) return y;
        }
        return min;
    }

    private static void reshapeColumn(ChunkAccess chunk, BlockPos.MutableBlockPos pos,
            int x, int z, int source, int target, int max) {
        if (target > source) {
            for (int y = source + 1; y <= target; y++)
                chunk.setBlockState(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), false);
        } else if (target < source) {
            for (int y = target + 1; y < max; y++) {
                BlockState replacement = y <= TickstormTerrain.SEA_LEVEL
                        ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                chunk.setBlockState(pos.set(x, y, z), replacement, false);
            }
        }
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type,
            LevelHeightAccessor level, RandomState random) {
        int vanilla = super.getBaseHeight(x, z, type, level, random);
        return vanilla <= level.getMinBuildHeight() ? vanilla : TickstormTerrain.flattenedSurface(vanilla - 1) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        NoiseColumn original = super.getBaseColumn(x, z, level, random);
        int min = level.getMinBuildHeight();
        BlockState[] states = new BlockState[level.getHeight()];
        int surface = min;
        for (int index = 0; index < states.length; index++) {
            int y = min + index;
            states[index] = original.getBlock(y);
            if (!states[index].isAir() && states[index].getFluidState().isEmpty()) surface = y;
        }
        int target = TickstormTerrain.flattenedSurface(surface);
        for (int y = Math.min(surface, target) + 1; y <= Math.max(surface, target); y++) {
            states[y - min] = target > surface ? Blocks.STONE.defaultBlockState()
                    : y <= TickstormTerrain.SEA_LEVEL ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        if (target < surface) for (int y = surface + 1; y < min + states.length; y++)
            states[y - min] = y <= TickstormTerrain.SEA_LEVEL
                    ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        return new NoiseColumn(min, states);
    }

    @Override public void buildSurface(WorldGenRegion level, StructureManager structures,
            RandomState random, ChunkAccess chunk) {
        super.buildSurface(level, structures, random, chunk);
    }
}
