package com.notmjonter.stairs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Small, chunk-contained listening chambers fitted to dry cavern shelves, never the roof. */
public final class ListeningWellFeature extends Feature<NoneFeatureConfiguration> {
    public ListeningWellFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (context.level().getLevel().dimension() != EchoBasinDimension.KEY) return false;
        // Center within the owning chunk: no neighbor-chunk writes or cross-chunk feature races.
        int x = (context.origin().getX() & ~15) + 8;
        int z = (context.origin().getZ() & ~15) + 8;
        for (int offset = 0; offset <= 36; offset++) {
            for (int sign : new int[]{1, -1}) {
                if (offset == 0 && sign == -1) continue;
                int y = 64 + offset * sign;
                if (y < 34 || y > 100) continue;
                BlockPos floor = new BlockPos(x, y, z);
                if (build(context.level(), floor)) {
                    var level = context.level().getLevel();
                    // World generation may be running off-thread; SavedData belongs on the server thread.
                    level.getServer().execute(() -> StaircaseData.get(level).registerEchoWell(floor));
                    return true;
                }
            }
        }
        return false;
    }

    static boolean natural(BlockState state) {
        return state.is(DimensionBlocks.ECHO_STONE) || state.is(DimensionBlocks.DARK_ECHO_STONE)
                || state.is(DimensionBlocks.ECHO_GRASS) || state.is(DimensionBlocks.RESONANT_ORE);
    }

    static boolean canBuild(LevelAccessor level, BlockPos floor) {
        if (floor.getY() < 34 || floor.getY() > 100) return false;
        // Validate every edited position before making any changes: no fluids, containers,
        // bedrock, existing buildings, or other wells are excavated or replaced.
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            if (x * x + z * z > 25) continue;
            for (int y = -2; y <= 5; y++) {
                BlockPos pos = floor.offset(x, y, z);
                if (level.isOutsideBuildHeight(pos) || level.getBlockEntity(pos) != null) return false;
                BlockState state = level.getBlockState(pos);
                if (!state.getFluidState().isEmpty() || (!state.isAir() && !natural(state))) return false;
                if (y == -2 && !natural(state)) return false;
                if (y >= 1 && !state.isAir()) return false;
            }
        }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            for (int y = -24; y <= -1; y++) {
                BlockPos pos = floor.offset(x, y, z);
                if (level.getBlockEntity(pos) != null || !natural(level.getBlockState(pos))) return false;
            }
        }
        return true;
    }

    static boolean build(LevelAccessor level, BlockPos floor) {
        if (!canBuild(level, floor)) return false;
        BlockState bricks = DimensionBlocks.ECHO_STONE_BRICKS.get().defaultBlockState();
        BlockState polished = DimensionBlocks.POLISHED_ECHO_STONE.get().defaultBlockState();
        BlockState dark = DimensionBlocks.DARK_ECHO_STONE.get().defaultBlockState();
        BlockState chiseled = DimensionBlocks.CHISELED_ECHO_STONE.get().defaultBlockState();
        BlockState lantern = DimensionBlocks.ECHO_LANTERN.get().defaultBlockState();
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            if (x * x + z * z > 25) continue;
            level.setBlock(floor.offset(x, -1, z), bricks, 2);
            level.setBlock(floor.offset(x, 0, z), Math.max(Math.abs(x), Math.abs(z)) == 2 ? chiseled : polished, 2);
        }
        for (int y = -24; y <= 0; y++) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                boolean wall = Math.max(Math.abs(x), Math.abs(z)) == 2 || y == -24;
                level.setBlock(floor.offset(x, y, z), wall ? dark : Blocks.AIR.defaultBlockState(), 2);
            }
            if (y < 0 && y > -24 && Math.floorMod(y, 6) == 0) {
                for (int offset : new int[]{-1, 1}) {
                    level.setBlock(floor.offset(2, y, offset), lantern, 2);
                    level.setBlock(floor.offset(-2, y, offset), lantern, 2);
                    level.setBlock(floor.offset(offset, y, 2), lantern, 2);
                    level.setBlock(floor.offset(offset, y, -2), lantern, 2);
                }
            }
        }
        // An invisible, entity-selective grate keeps players out while admitting dropped items.
        level.setBlock(floor.below(24), lantern, 2);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            level.setBlock(floor.offset(x, 0, z), EchoListeningWells.BARRIER.get().defaultBlockState(), 2);
        for (int x : new int[]{-3, 3}) for (int z : new int[]{-3, 3}) {
            level.setBlock(floor.offset(x, 1, z), bricks, 2);
            level.setBlock(floor.offset(x, 2, z), chiseled, 2);
            level.setBlock(floor.offset(x, 3, z), lantern, 2);
        }
        return true;
    }
}
