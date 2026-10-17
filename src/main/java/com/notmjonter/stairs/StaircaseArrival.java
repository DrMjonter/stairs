package com.notmjonter.stairs;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Shared arrival placement for destination dimensions; keeps the same tread and fractional footing. */
final class StaircaseArrival {
    private StaircaseArrival() {}

    static Vec3 translate(Vec3 feet, BlockPos from, BlockPos to) {
        return feet.add(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }

    static StaircaseData.Staircase prepare(ServerLevel source, ServerLevel destination, StaircaseData.Staircase stair) {
        var data = StaircaseData.get(destination);
        var existing = data.stairs.values().stream().filter(candidate -> stair.origin.equals(candidate.returnOrigin)
                && candidate.rotation == stair.rotation).findFirst().orElse(null);
        BlockPos top = stair.position(2, 8, 9);
        BlockPos destinationOrigin = stair.origin;
        if (destination.dimension() == GarbageCollectorDimension.KEY) {
            BlockPos safeTop = existing == null ? GarbageCollectorDimension.safeArrivalColumn(destination, top)
                    : existing.position(2, 8, 9);
            destinationOrigin = stair.origin.offset(safeTop.getX() - top.getX(), 0, safeTop.getZ() - top.getZ());
            top = safeTop;
        }
        if (destination.dimension() == UnfinishedSunDimension.KEY && existing == null) {
            BlockPos safeTop = UnfinishedSunDimension.safeArrivalColumn(top);
            destinationOrigin = stair.origin.offset(safeTop.getX() - top.getX(), 0, safeTop.getZ() - top.getZ());
            top = safeTop;
        } else if (destination.dimension() == UnfinishedSunDimension.KEY) {
            top = existing.position(2, 8, 9);
            destinationOrigin = existing.origin;
        }
        // Height queries do not generate a previously unloaded column.
        destination.getChunkAt(top);
        int surface = destination.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top.getX(), top.getZ()) - 1;
        if (destination.dimension() == EchoBasinDimension.KEY) {
            // Heightmaps see the roof. Find dry cavern footing instead, without breaking bedrock.
            surface = cavernSurface(destination, top);
            if (surface < 0 && existing == null) return null;
        }
        // A mirror snapshot can already contain the source entrance. Measure the
        // ground beneath it, not the old elevated treads.
        if (destination.dimension() == MirrorDimension.KEY) {
            while (surface > destination.getMinBuildHeight()) {
                BlockPos pos = new BlockPos(top.getX(), surface, top.getZ());
                if (!destination.getBlockState(pos).isAir() && !sourceStairBlock(source, destination, stair, pos)) break;
                surface--;
            }
        }
        BlockPos origin = existing == null ? new BlockPos(destinationOrigin.getX(), surface - 8, destinationOrigin.getZ()) : existing.origin;
        var copy = new StaircaseData.Staircase(origin, stair.rotation, stair.destination);
        copy.returnOrigin = stair.origin;
        copy.state = StaircaseData.State.ACTIVATED;
        Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
        if (destination.dimension() == MirrorDimension.KEY) {
            // Remove only the snapshot's matching old entrance blocks.
            addSourceRemoval(source, destination, changes, stair, stair.position(2, 0, 2));
            for (int step = 0; step < 8; step++) {
                addSourceRemoval(source, destination, changes, stair, stair.position(2, step + 1, step + 2));
                if (step > 0) addSourceRemoval(source, destination, changes, stair, stair.position(2, step, step + 2));
            }
        }
        // Cut an open trench, including side clearance for a player standing at a tread edge.
        for (int step = 0; step < 8; step++) {
            for (int x = 1; x <= 3; x++) {
                for (int y = step + 2; y <= 11; y++) changes.put(copy.position(x, y, step + 2), Blocks.AIR.defaultBlockState());
            }
        }
        changes.put(copy.position(2, 0, 2), source.getBlockState(stair.position(2, 0, 2)));
        for (int step = 0; step < 8; step++) {
            changes.put(copy.position(2, step + 1, step + 2), source.getBlockState(stair.position(2, step + 1, step + 2)));
            if (step > 0) changes.put(copy.position(2, step, step + 2), source.getBlockState(stair.position(2, step, step + 2)));
        }
        // Check the complete footprint before touching any blocks. No container destruction.
        for (var entry : changes.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState before = destination.getBlockState(pos);
            if (!destination.isInWorldBounds(pos) || !destination.getWorldBorder().isWithinBounds(pos)
                    || before.hasBlockEntity() || !before.getFluidState().isEmpty()) return null;
            boolean ownTread = existing != null && StaircaseProtection.contains(existing, pos, 0)
                    && (before.is(Blocks.SMOOTH_QUARTZ_STAIRS) || before.is(Blocks.SMOOTH_QUARTZ));
            if (!before.equals(entry.getValue()) && !natural(before) && !ownTread
                    && !sourceStairBlock(source, destination, stair, pos)) return null;
        }
        // Suppress neighbor updates until the entire cutout and supporting treads exist.
        changes.forEach((pos, state) -> destination.setBlock(pos, state, 2));
        changes.forEach((pos, state) -> destination.updateNeighborsAt(pos, state.getBlock()));
        if (destination.dimension() == MirrorDimension.KEY && !copy.origin.equals(stair.origin)) {
            var snapshotEntrance = data.stairs.get(stair.origin);
            if (snapshotEntrance != null && snapshotEntrance.returnOrigin == null) data.stairs.remove(stair.origin);
        }
        data.stairs.put(copy.origin, copy);
        data.setDirty();
        return copy;
    }

    private static boolean natural(BlockState state) {
        return state.isAir() || state.canBeReplaced() || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
                || state.is(FutureBlocks.TURF) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.SANDSTONE)
                || state.is(DimensionBlocks.ECHO_STONE) || state.is(DimensionBlocks.ECHO_GRASS)
                || state.is(DimensionBlocks.DARK_ECHO_STONE)
                || state.is(DimensionBlocks.RESONANT_ORE);
    }

    private static int cavernSurface(ServerLevel level, BlockPos column) {
        int best = -1;
        for (int y = 12; y <= 112; y++) {
            BlockPos ground = new BlockPos(column.getX(), y, column.getZ());
            BlockState state = level.getBlockState(ground);
            if (!(state.is(DimensionBlocks.ECHO_STONE) || state.is(DimensionBlocks.ECHO_GRASS)
                    || state.is(DimensionBlocks.DARK_ECHO_STONE)
                    || state.is(DimensionBlocks.RESONANT_ORE))) continue;
            if (!level.getBlockState(ground.above()).isAir()
                    || !level.getBlockState(ground.above(2)).isAir()
                    || !level.getBlockState(ground.above(3)).isAir()) continue;
            if (best < 0 || Math.abs(y - 64) < Math.abs(best - 64)) best = y;
        }
        return best;
    }

    private static boolean sourceStairBlock(ServerLevel source, ServerLevel destination, StaircaseData.Staircase stair, BlockPos pos) {
        if (destination.dimension() != MirrorDimension.KEY || !StaircaseProtection.contains(stair, pos, 0)) return false;
        BlockState state = destination.getBlockState(pos);
        return (state.is(Blocks.SMOOTH_QUARTZ_STAIRS) || state.is(Blocks.SMOOTH_QUARTZ)) && state.equals(source.getBlockState(pos));
    }

    private static void addSourceRemoval(ServerLevel source, ServerLevel destination, Map<BlockPos, BlockState> changes,
            StaircaseData.Staircase stair, BlockPos pos) {
        if (sourceStairBlock(source, destination, stair, pos)) changes.put(pos, Blocks.AIR.defaultBlockState());
    }
}
