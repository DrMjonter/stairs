package com.notmjonter.stairs;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Vanilla noise caves/aquifers underneath an intentionally artificial, level surface. */
public final class FutureChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<FutureChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(FutureChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(FutureChunkGenerator::generatorSettings)
    ).apply(instance, FutureChunkGenerator::new));

    public FutureChunkGenerator(BiomeSource biomes, Holder<NoiseGeneratorSettings> settings) {
        super(biomes, settings);
    }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> lookup, RandomState random, long seed) {
        return ChunkGeneratorStructureState.createForFlat(random, seed, getBiomeSource(),
                lookup.listElements().filter(holder -> holder.key().location().equals(
                        ResourceLocation.withDefaultNamespace("villages"))).map(holder -> holder));
    }

    static BlockState layer(int y, BlockState underground) {
        if (y > FutureDimension.SURFACE_Y) return Blocks.AIR.defaultBlockState();
        if (y == FutureDimension.SURFACE_Y) return FutureBlocks.TURF.get().defaultBlockState();
        if (y >= FutureDimension.SURFACE_Y - 10) return Blocks.GRAVEL.defaultBlockState();
        // A solid roof supports the gravel and keeps the artificial surface unbroken.
        if (y >= FutureDimension.SURFACE_Y - 23) return Blocks.STONE.defaultBlockState();
        if (y == -64) return FutureBlocks.BEDROCK_PLUS.get().defaultBlockState();
        if (y < 0 && underground.is(Blocks.STONE)) return Blocks.DEEPSLATE.defaultBlockState();
        return underground;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
            StructureManager structures, ChunkAccess chunk) {
        return super.fillFromNoise(blender, random, structures, chunk).thenApply(generated -> {
            applyLayers(generated);
            return generated;
        });
    }

    private static void applyLayers(ChunkAccess chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
                pos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                BlockState before = chunk.getBlockState(pos);
                BlockState after = layer(y, before);
                if (before != after) chunk.setBlockState(pos, after, false);
            }
        }
        Heightmap.primeHeightmaps(chunk, java.util.Set.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
    }

    // Do not let the Overworld surface rules turn the artificial cap into grass/dirt.
    @Override public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {}

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomes,
            StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
        super.applyCarvers(level, seed, random, biomes, structures, chunk, step);
        applyLayers(chunk);
    }

    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return FutureDimension.SURFACE_Y + 1;
    }

    @Override public int getSpawnHeight(LevelHeightAccessor level) { return FutureDimension.SURFACE_Y + 1; }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        NoiseColumn original = super.getBaseColumn(x, z, level, random);
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = level.getMinBuildHeight() + i;
            states[i] = layer(y, original.getBlock(y));
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override public void spawnOriginalMobs(WorldGenRegion level) {}
}
