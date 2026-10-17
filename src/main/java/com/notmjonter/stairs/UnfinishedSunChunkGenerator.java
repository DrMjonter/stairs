package com.notmjonter.stairs;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Vanilla Overworld generation with stable holes where whole chunks were never reconstructed. */
public final class UnfinishedSunChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<UnfinishedSunChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(UnfinishedSunChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(UnfinishedSunChunkGenerator::generatorSettings)
    ).apply(instance, UnfinishedSunChunkGenerator::new));

    public UnfinishedSunChunkGenerator(BiomeSource biomes, Holder<NoiseGeneratorSettings> settings) {
        super(biomes, settings);
    }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    static boolean isMissing(ChunkPos pos) {
        return UnfinishedSunChunks.isMissing(pos);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
            StructureManager structures, ChunkAccess chunk) {
        return isMissing(chunk.getPos()) ? CompletableFuture.completedFuture(chunk)
                : super.fillFromNoise(blender, random, structures, chunk);
    }

    @Override public void buildSurface(WorldGenRegion level, StructureManager structures,
            RandomState random, ChunkAccess chunk) {
        if (!isMissing(chunk.getPos())) super.buildSurface(level, structures, random, chunk);
    }

    @Override public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomes,
            StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
        if (!isMissing(chunk.getPos())) super.applyCarvers(level, seed, random, biomes, structures, chunk, step);
    }

    @Override public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        if (!isMissing(chunk.getPos())) super.applyBiomeDecoration(level, chunk, structures);
    }

    @Override public void createStructures(RegistryAccess registries, ChunkGeneratorStructureState state,
            StructureManager structures, ChunkAccess chunk, StructureTemplateManager templates) {
        if (!isMissing(chunk.getPos())) super.createStructures(registries, state, structures, chunk, templates);
    }

    @Override public void createReferences(WorldGenLevel level, StructureManager structures, ChunkAccess chunk) {
        if (!isMissing(chunk.getPos())) super.createReferences(level, structures, chunk);
    }

    @Override public int getBaseHeight(int x, int z, Heightmap.Types type,
            LevelHeightAccessor level, RandomState random) {
        return isMissing(new ChunkPos(x >> 4, z >> 4)) ? level.getMinBuildHeight()
                : super.getBaseHeight(x, z, type, level, random);
    }

    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        if (!isMissing(new ChunkPos(x >> 4, z >> 4))) return super.getBaseColumn(x, z, level, random);
        return new NoiseColumn(level.getMinBuildHeight(),
                java.util.Collections.nCopies(level.getHeight(), Blocks.AIR.defaultBlockState())
                        .toArray(net.minecraft.world.level.block.state.BlockState[]::new));
    }
}
