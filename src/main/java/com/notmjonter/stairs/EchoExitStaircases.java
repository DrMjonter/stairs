package com.notmjonter.stairs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Monumental quartz exits unlocked by completing all three Listening Wells. */
public final class EchoExitStaircases {
    static final int TOP_Y = 120;
    private static final int HALF_WIDTH = 2;
    private static final int RAIL_OFFSET = 3;
    private static final int LANDING_LENGTH = 5;
    private static final Map<UUID, Descent> DESCENTS = new HashMap<>();

    private record Descent(BlockPos origin, int highestStep, int currentStep) {}

    private EchoExitStaircases() {}

    static boolean spawn(ServerLevel level, BlockPos well) {
        StaircaseData data = StaircaseData.get(level);
        Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        int start = Math.floorMod(Long.hashCode(well.asLong()), directions.length);
        // A cavern column can be obstructed by another generated feature. Search progressively
        // farther from the completed well instead of silently giving up after four attempts.
        for (int distance = 10; distance <= 74; distance += 8) {
            for (int offset = 0; offset < directions.length; offset++) {
                Direction direction = directions[(start + offset) % directions.length];
                BlockPos column = well.relative(direction, distance);
                int floor = cavernFloor(level, column.getX(), column.getZ(), well.getY());
                if (floor < 0) continue;
                BlockPos origin = new BlockPos(column.getX(), floor + 1, column.getZ());
                if (!canBuild(level, data, origin, direction)) continue;
                build(level, origin, direction);
                data.registerEchoExit(origin, direction);
                return true;
            }
        }
        return false;
    }

    private static int cavernFloor(ServerLevel level, int x, int z, int preferred) {
        int best = -1;
        for (int y = 12; y < TOP_Y - 8; y++) {
            BlockPos ground = new BlockPos(x, y, z);
            if (!natural(level.getBlockState(ground)) || !level.getBlockState(ground.above()).isAir()
                    || !level.getBlockState(ground.above(2)).isAir()) continue;
            if (best < 0 || Math.abs(y - preferred) < Math.abs(best - preferred)) best = y;
        }
        return best;
    }

    private static boolean canBuild(ServerLevel level, StaircaseData data, BlockPos origin, Direction direction) {
        int steps = steps(origin);
        if (steps < 8) return false;
        BlockPos end = origin.relative(direction, steps + LANDING_LENGTH);
        Direction side = direction.getClockWise();
        for (int lateral : new int[]{-RAIL_OFFSET, RAIL_OFFSET}) {
            if (!level.getWorldBorder().isWithinBounds(origin.relative(side, lateral))
                    || !level.getWorldBorder().isWithinBounds(end.relative(side, lateral))) return false;
        }
        for (BlockPos well : data.knownEchoWells) {
            if (well.distSqr(origin) < 64) continue;
            if (insidePlan(origin, direction, well, 5)) return false;
        }
        for (int step = 0; step <= steps + LANDING_LENGTH; step++) {
            int y = Math.min(TOP_Y, origin.getY() + step);
            for (int lateral = -RAIL_OFFSET; lateral <= RAIL_OFFSET; lateral++) {
                BlockPos column = origin.relative(direction, step).relative(side, lateral);
                int low = lateral == -RAIL_OFFSET || lateral == RAIL_OFFSET ? y : origin.getY() - 1;
                int high = lateral == -RAIL_OFFSET || lateral == RAIL_OFFSET ? y : y + 2;
                for (int blockY = low; blockY <= high; blockY++) {
                    BlockPos pos = column.atY(blockY);
                    BlockState state = level.getBlockState(pos);
                    if (state.hasBlockEntity() || state.is(EchoListeningWells.BARRIER)
                            || (!replaceable(state) && !state.is(Blocks.BEDROCK))) return false;
                    if (state.is(Blocks.BEDROCK) && blockY <= TOP_Y + 2) return false;
                }
            }
        }
        return true;
    }

    private static void build(ServerLevel level, BlockPos origin, Direction direction) {
        int steps = steps(origin);
        Direction side = direction.getClockWise();
        BlockState stair = Blocks.QUARTZ_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, direction).setValue(StairBlock.HALF, Half.BOTTOM);
        BlockState quartz = Blocks.QUARTZ_BLOCK.defaultBlockState();
        for (int step = 0; step <= steps; step++) {
            int y = origin.getY() + step;
            for (int lateral = -HALF_WIDTH; lateral <= HALF_WIDTH; lateral++) {
                BlockPos column = origin.relative(direction, step).relative(side, lateral);
                for (int blockY = origin.getY() - 1; blockY < y; blockY++)
                    level.setBlock(column.atY(blockY), quartz, 2);
                level.setBlock(column.atY(y), stair, 2);
                level.setBlock(column.atY(y + 1), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(column.atY(y + 2), Blocks.AIR.defaultBlockState(), 2);
            }
            for (int lateral : new int[]{-RAIL_OFFSET, RAIL_OFFSET})
                level.setBlock(origin.relative(direction, step).relative(side, lateral).atY(y), quartz, 2);
        }
        for (int forward = 1; forward <= LANDING_LENGTH; forward++) {
            for (int lateral = -RAIL_OFFSET; lateral <= RAIL_OFFSET; lateral++) {
                BlockPos pos = origin.relative(direction, steps + forward).relative(side, lateral).atY(TOP_Y);
                for (int blockY = origin.getY() - 1; blockY < TOP_Y; blockY++)
                    level.setBlock(pos.atY(blockY), quartz, 2);
                level.setBlock(pos, quartz, 2);
                if (Math.abs(lateral) <= HALF_WIDTH) {
                    level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
                    level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != EchoBasinDimension.KEY) return;
        StaircaseData basin = StaircaseData.get(level);
        StaircaseData progress = StaircaseData.get(level.getServer().overworld());
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.getAbilities().flying || player.isPassenger()
                    || !progress.echoComplete(player.getUUID())) {
                DESCENTS.remove(player.getUUID());
                continue;
            }
            boolean onExit = false;
            for (var entry : basin.echoExitStairs.entrySet()) {
                int step = occupiedStep(player, entry.getKey(), entry.getValue());
                if (step < 0) continue;
                onExit = true;
                Descent descent = DESCENTS.get(player.getUUID());
                if (descent == null || !descent.origin().equals(entry.getKey())) {
                    DESCENTS.put(player.getUUID(), new Descent(entry.getKey(), step, step));
                } else {
                    Descent updated = new Descent(entry.getKey(), Math.max(descent.highestStep(), step), step);
                    DESCENTS.put(player.getUUID(), updated);
                    if (updated.highestStep() - step >= 8) GarageTravel.beginEchoExit(player);
                }
                break;
            }
            if (!onExit) DESCENTS.remove(player.getUUID());
        }
        DESCENTS.keySet().removeIf(id -> level.getServer().getPlayerList().getPlayer(id) == null
                || level.getServer().getPlayerList().getPlayer(id).serverLevel() != level);
    }

    private static int occupiedStep(ServerPlayer player, BlockPos origin, Direction direction) {
        Direction side = direction.getClockWise();
        BlockPos feet = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
        int dx = feet.getX() - origin.getX(), dz = feet.getZ() - origin.getZ();
        int along = dx * direction.getStepX() + dz * direction.getStepZ();
        int lateral = dx * side.getStepX() + dz * side.getStepZ();
        int top = steps(origin);
        if (along < 0 || along > top + LANDING_LENGTH || Math.abs(lateral) > HALF_WIDTH) return -1;
        int expectedY = origin.getY() + Math.min(along, top);
        return Math.abs(feet.getY() - expectedY) <= 1 ? Math.min(along, top) : -1;
    }

    static float effectIntensity(ServerPlayer player) {
        Descent descent = DESCENTS.get(player.getUUID());
        return descent == null ? 0 : descentIntensity(descent.highestStep() - descent.currentStep());
    }

    static float descentIntensity(int descendedSteps) {
        // Reuse the exact Overworld envelope: the first downward tread starts at 30%,
        // reaching full strength before the eighth tread starts the transfer.
        return descendedSteps <= 0 ? 0 : StaircaseLifecycle.ascentIntensity(descendedSteps + 0.5f);
    }

    static boolean contains(ServerLevel level, BlockPos pos, int radius) {
        for (var entry : StaircaseData.get(level).echoExitStairs.entrySet())
            if (insidePlan(entry.getKey(), entry.getValue(), pos, radius)) return true;
        return false;
    }

    private static boolean insidePlan(BlockPos origin, Direction direction, BlockPos pos, int radius) {
        Direction side = direction.getClockWise();
        int dx = pos.getX() - origin.getX(), dz = pos.getZ() - origin.getZ();
        int along = dx * direction.getStepX() + dz * direction.getStepZ();
        int lateral = dx * side.getStepX() + dz * side.getStepZ();
        int top = steps(origin);
        if (along < -radius || along > top + LANDING_LENGTH + radius
                || Math.abs(lateral) > RAIL_OFFSET + radius) return false;
        int height = origin.getY() + Math.clamp(along, 0, top);
        return pos.getY() >= origin.getY() - 1 - radius && pos.getY() <= height + 2 + radius;
    }

    private static int steps(BlockPos origin) { return TOP_Y - origin.getY(); }

    private static boolean natural(BlockState state) {
        return state.is(DimensionBlocks.ECHO_STONE) || state.is(DimensionBlocks.ECHO_GRASS)
                || state.is(DimensionBlocks.DARK_ECHO_STONE) || state.is(DimensionBlocks.RESONANT_ORE);
    }

    private static boolean replaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty() || natural(state);
    }

    public static void stopped(ServerStoppingEvent event) { DESCENTS.clear(); }
}
