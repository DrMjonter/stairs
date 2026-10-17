package com.notmjonter.stairs;

import static com.notmjonter.stairs.GarbageCollectorTerrain.*;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Independently seeded terrain fragments. All writes stay inside the owning chunk. */
public final class GarbageCollectorChunkGenerator extends ChunkGenerator {
    public static final MapCodec<GarbageCollectorChunkGenerator> CODEC = BiomeSource.CODEC.fieldOf("biome_source")
            .xmap(GarbageCollectorChunkGenerator::new, GarbageCollectorChunkGenerator::getBiomeSource);
    private static final ResourceLocation RANDOM = ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "garbage_cells");

    public GarbageCollectorChunkGenerator(BiomeSource biomes) { super(biomes); }
    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    static long cellSeed(RandomState random, int chunkX, int chunkZ) {
        return random.getOrCreateRandomFactory(RANDOM).at(chunkX, 0, chunkZ).nextLong();
    }

    static BlockState blockAt(long seed, int x, int y, int z) {
        // One-block-wide open seams expose the discarded cross-sections.
        if (empty(seed, x, z)) return Blocks.AIR.defaultBlockState();
        int ground = surface(seed, x, z);
        int type = kind(seed);
        if (y > ground) {
            if (type == 1) {
                // Tree canopies are clipped to the fragment, never written into neighbors.
                for (int tx : new int[] {5, 11}) for (int tz : new int[] {5, 11}) {
                    int root = surface(seed, tx, tz);
                    if (x == tx && z == tz && y <= root + 5) return Blocks.OAK_LOG.defaultBlockState();
                    if (Math.abs(x - tx) <= 2 && Math.abs(z - tz) <= 2
                            && y >= root + 4 && y <= root + 6)
                        return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                }
            }
            if (y == ground + 1 && (type == 0 || type == 1)
                    && Math.floorMod(mix(seed + x * 31L + z), 7) == 0)
                return Blocks.SHORT_GRASS.defaultBlockState();
            return Blocks.AIR.defaultBlockState();
        }
        if (y == -64) return Blocks.BEDROCK.defaultBlockState();
        if (y == ground) return switch (type) {
            case 2 -> Blocks.SAND.defaultBlockState();
            case 3 -> Blocks.STONE.defaultBlockState();
            case 4 -> Blocks.SNOW_BLOCK.defaultBlockState();
            default -> Blocks.GRASS_BLOCK.defaultBlockState();
        };
        if (y >= ground - 3) return (type == 2 ? Blocks.SANDSTONE
                : type == 3 ? Blocks.STONE : Blocks.DIRT).defaultBlockState();
        // Small sealed cave pockets give exposed chunk faces some internal texture.
        if (y > -55 && y < ground - 7 && Math.sin(x * .35 + seed % 97) * Math.cos(z * .31)
                + Math.sin(y * .24) > 1.55) return Blocks.AIR.defaultBlockState();
        return (y < 0 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
    }

    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
            StructureManager structures, ChunkAccess chunk) {
        long seed = cellSeed(random, chunk.getPos().x, chunk.getPos().z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            for (int y = Math.max(-64, chunk.getMinBuildHeight()); y < Math.min(136, chunk.getMaxBuildHeight()); y++) {
                BlockState state = blockAt(seed, x, y, z);
                if (!state.isAir()) chunk.setBlockState(pos.set(chunk.getPos().getMinBlockX() + x, y,
                        chunk.getPos().getMinBlockZ() + z), state, false);
            }
        }
        Heightmap.primeHeightmaps(chunk, Set.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        long seed = cellSeed(random, x >> 4, z >> 4);
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) states[i] = blockAt(seed, x & 15, level.getMinBuildHeight() + i, z & 15);
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        NoiseColumn column = getBaseColumn(x, z, level, random);
        for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--)
            if (type.isOpaque().test(column.getBlock(y))) return y + 1;
        return level.getMinBuildHeight();
    }

    @Override public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {}
    @Override public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomes,
            StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {}
    @Override public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {}
    @Override public void spawnOriginalMobs(WorldGenRegion level) {}
    @Override public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {}
    @Override public int getMinY() { return -64; }
    @Override public int getGenDepth() { return 384; }
    @Override public int getSeaLevel() { return 63; }
}
