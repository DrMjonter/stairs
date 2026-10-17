package com.notmjonter.stairs;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import com.notmjonter.stairs.StaircaseData.Staircase;
import com.notmjonter.stairs.StaircaseData.State;

public final class StaircaseProtection {
    static final int PLACEMENT_RADIUS = 3;
    private StaircaseProtection() {}

    static boolean contains(Staircase stair, BlockPos pos, int radius) {
        if (stair.state == State.EXPIRED) return false;
        if (within(stair.position(2, 0, 2), pos, radius)) return true;
        for (int step = 0; step < 8; step++) {
            if (within(stair.position(2, step + 1, step + 2), pos, radius)) return true;
            if (step > 0 && within(stair.position(2, step, step + 2), pos, radius)) return true;
        }
        return false;
    }

    private static boolean within(BlockPos block, BlockPos pos, int radius) {
        return Math.abs(block.getX() - pos.getX()) <= radius
                && Math.abs(block.getY() - pos.getY()) <= radius
                && Math.abs(block.getZ() - pos.getZ()) <= radius;
    }

    private static boolean protectedAt(ServerLevel level, BlockPos pos, int radius) {
        if (level.dimension() == EchoBasinDimension.KEY
                && (EchoExitStaircases.contains(level, pos, radius)
                    || EchoListeningWells.contains(level, pos, radius))) return true;
        if (level.dimension() != Level.OVERWORLD)
            return StaircaseData.get(level).stairs.values().stream().anyMatch(stair -> contains(stair, pos, radius));
        StaircaseData data = StaircaseData.get(level);
        // Protection must work before the periodic player discovery scan, including
        // explosions in loaded terrain that nobody has approached yet.
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "forest_stairs"));
        if (structure != null) {
            for (int x = (pos.getX() >> 4) - 1; x <= (pos.getX() >> 4) + 1; x++) {
                for (int z = (pos.getZ() >> 4) - 1; z <= (pos.getZ() >> 4) + 1; z++) {
                    var chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk == null) continue;
                    var start = chunk.getStartForStructure(structure);
                    if (start == null || !start.isValid()) continue;
                    for (var piece : start.getPieces()) {
                        if (piece instanceof PoolElementStructurePiece pool) {
                            data.register(pool.getPosition(), pool.getRotation(), level.getSeed());
                        }
                    }
                }
            }
        }
        return data.stairs.values().stream().anyMatch(stair -> contains(stair, pos, radius));
    }

    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && protectedAt(level, event.getPos(), 0)) {
            event.setCanceled(true);
        }
    }

    public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Predicate<BlockPos> blocked = pos -> protectedAt(level, pos, PLACEMENT_RADIUS);
        boolean cancel = event instanceof BlockEvent.EntityMultiPlaceEvent multi
                ? multi.getReplacedBlockSnapshots().stream().anyMatch(snapshot -> blocked.test(snapshot.getPos()))
                : blocked.test(event.getPos());
        if (cancel) event.setCanceled(true);
    }

    public static void explosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().removeIf(pos -> protectedAt(level, pos, 0));
        }
    }

    public static void piston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) return;
        var movement = event.getPistonMoveType().isExtend ? event.getDirection() : event.getDirection().getOpposite();
        if (protectedAt(level, event.getFaceOffsetPos(), PLACEMENT_RADIUS)
                || resolver.getToPush().stream().anyMatch(pos -> protectedAt(level, pos, 0)
                    || protectedAt(level, pos.relative(movement), PLACEMENT_RADIUS))
                || resolver.getToDestroy().stream().anyMatch(pos -> protectedAt(level, pos, 0))) {
            event.setCanceled(true);
        }
    }
}
