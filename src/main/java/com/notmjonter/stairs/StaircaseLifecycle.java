package com.notmjonter.stairs;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import com.notmjonter.stairs.StaircaseData.Staircase;
import com.notmjonter.stairs.StaircaseData.State;

public final class StaircaseLifecycle {
    static final int DISCOVERY_RADIUS = 32;
    static final int DEPARTURE_RADIUS = 96;
    private static final ResourceLocation STRUCTURE = ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "forest_stairs");

    private StaircaseLifecycle() {}

    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || (level.dimension() != Level.OVERWORLD
                && level.dimension() != MirrorDimension.KEY && level.dimension() != EchoBasinDimension.KEY
                && level.dimension() != GarbageCollectorDimension.KEY)) return;
        // An empty server is paused for lifecycle purposes, including during world startup.
        if (level.getServer().getPlayerList().getPlayerCount() == 0) return;
        StaircaseData data = StaircaseData.get(level);
        List<ServerPlayer> players = level.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
        long time = level.getGameTime();
        if (level.dimension() == Level.OVERWORLD && time % 20 == 0) discoverStructures(level, data, players);

        Map<UUID, Float> intensities = new HashMap<>();
        for (Staircase stair : data.stairs.values()) {
            if (stair.state == State.EXPIRED) {
                if (time % 20 == 0 && stair.removedBlocks != 0xffff) removeBlocks(level, data, stair);
                continue;
            }
            BlockPos foot = stair.position(2, 1, 2);
            for (ServerPlayer player : players) {
                if (!player.getAbilities().flying && !player.isPassenger()) {
                    for (int step = 0; step < 8; step++) {
                        if (insideEffectArea(level, player, stair.position(2, step + 1, step + 2))) {
                            float height = (float) (player.getY() - stair.origin.getY());
                            intensities.merge(player.getUUID(), ascentIntensity(height), Math::max);
                        }
                    }
                }
                if (near(player, foot, DISCOVERY_RADIUS) && stair.encounter()) data.setDirty();
                if (stair.state == State.ENCOUNTERED || stair.state == State.ACTIVATED) checkAscent(level, data, stair, player);
            }
            // Leaving is about physical presence, not eligibility to discover/activate.
            // In particular, switching to spectator must not expire stairs under the player.
            if (stair.expire(hasNearbyPlayer(foot, level.players().stream().map(ServerPlayer::position)))) {
                data.setDirty();
                removeBlocks(level, data, stair);
            }
            stair.ascents.removeIf(id -> players.stream().noneMatch(p -> p.getUUID().equals(id) && near(p, foot, 16)));
        }
        if (level.dimension() == EchoBasinDimension.KEY) {
            for (ServerPlayer player : players) {
                float intensity = EchoExitStaircases.effectIntensity(player);
                if (intensity > 0) intensities.merge(player.getUUID(), intensity, Math::max);
            }
        }
        for (ServerPlayer player : level.players()) {
            StaircaseEffects.apply(player, intensities.getOrDefault(player.getUUID(), 0f));
        }
    }

    static boolean hasNearbyPlayer(BlockPos foot, Stream<Vec3> playerPositions) {
        Vec3 center = Vec3.atCenterOf(foot);
        return playerPositions.anyMatch(pos -> pos.distanceToSqr(center) <= DEPARTURE_RADIUS * DEPARTURE_RADIUS);
    }

    private static boolean near(ServerPlayer player, BlockPos pos, int radius) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= radius * radius;
    }

    private static void discoverStructures(ServerLevel level, StaircaseData data, List<ServerPlayer> players) {
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(STRUCTURE);
        if (structure == null) return;
        Set<Long> checked = new HashSet<>();
        for (ServerPlayer player : players) {
            ChunkPos center = player.chunkPosition();
            // Includes the start chunk even when a rotated template extends across its boundary.
            for (int x = center.x - 3; x <= center.x + 3; x++) {
                for (int z = center.z - 3; z <= center.z + 3; z++) {
                    if (!checked.add(ChunkPos.asLong(x, z))) continue;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
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
    }

    private static void checkAscent(ServerLevel level, StaircaseData data, Staircase stair, ServerPlayer player) {
        if (player.getAbilities().flying || player.isPassenger()) {
            stair.ascents.remove(player.getUUID());
            return;
        }
        if (!player.onGround()) return;
        for (int step = 0; step < 3; step++) {
            if (standingOn(level, player, stair.position(2, step + 1, step + 2))) {
                stair.ascents.add(player.getUUID());
                return;
            }
        }
        if (stair.ascents.contains(player.getUUID()) && (standingOn(level, player, stair.position(2, 7, 8))
                || standingOn(level, player, stair.position(2, 8, 9)))) {
            boolean activated = stair.activate();
            stair.ascents.remove(player.getUUID());
            if (activated) data.setDirty();
            if (stair.destination == 1 || stair.destination == EchoBasinDimension.DESTINATION
                    || stair.destination == TickstormDimension.DESTINATION
                    || stair.destination == GarbageCollectorDimension.DESTINATION
                    || stair.destination == MirrorDimension.DESTINATION) GarageTravel.begin(player, stair);
            else if (activated) player.sendSystemMessage(Component.translatable("message.mjontersstairs.staircase.destination_unavailable",
                    Stairs.MODID + ":dimension_" + stair.destination));
        }
    }

    static float ascentIntensity(float height) {
        // Visible from the first tread, fully developed at the penultimate tread.
        return Math.clamp(0.3f + (height - 1.5f) * (0.7f / 6f), 0.3f, 1f);
    }

    static boolean withinEffectColumn(Vec3 feet, BlockPos tread) {
        // Allow feet above stair risers and during a normal jump. Keep the footprint
        // narrow so stepping sideways off the staircase promptly releases the effect.
        return feet.x >= tread.getX() - 0.4 && feet.x <= tread.getX() + 1.4
                && feet.z >= tread.getZ() - 0.4 && feet.z <= tread.getZ() + 1.4
                && feet.y >= tread.getY() - 0.1 && feet.y <= tread.getY() + 2.5;
    }

    private static boolean insideEffectArea(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!withinEffectColumn(player.position(), pos)) return false;
        BlockState block = level.getBlockState(pos);
        return block.is(Blocks.SMOOTH_QUARTZ_STAIRS) && block.getValue(StairBlock.HALF) == Half.BOTTOM;
    }

    private static boolean standingOn(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (player.getX() < pos.getX() || player.getX() >= pos.getX() + 1
                || player.getZ() < pos.getZ() || player.getZ() >= pos.getZ() + 1
                || player.getY() < pos.getY() + 0.49 || player.getY() > pos.getY() + 1.01) return false;
        BlockState block = level.getBlockState(pos);
        return block.is(Blocks.SMOOTH_QUARTZ_STAIRS) && block.getValue(StairBlock.HALF) == Half.BOTTOM;
    }

    private static void removeBlocks(ServerLevel level, StaircaseData data, Staircase stair) {
        // The template contains exactly 16 solid blocks. Persist progress so unloaded chunks
        // can be cleaned later without loading terrain or repeatedly deleting player rebuilds.
        removeBlock(level, data, stair, 0, stair.position(2, 0, 2), Blocks.SMOOTH_QUARTZ.defaultBlockState());
        for (int step = 0; step < 8; step++) {
            removeBlock(level, data, stair, step + 1, stair.position(2, step + 1, step + 2),
                    Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.BOTTOM));
            if (step > 0) removeBlock(level, data, stair, step + 8, stair.position(2, step, step + 2),
                    Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP));
        }
    }

    private static void removeBlock(ServerLevel level, StaircaseData data, Staircase stair, int index, BlockPos pos, BlockState expected) {
        int bit = 1 << index;
        if ((stair.removedBlocks & bit) != 0 || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return;
        BlockState current = level.getBlockState(pos);
        if (current.is(expected.getBlock()) && (!current.hasProperty(StairBlock.HALF)
                || current.getValue(StairBlock.HALF) == expected.getValue(StairBlock.HALF))) {
            level.setBlock(pos, current.getFluidState().createLegacyBlock(), 3);
        }
        stair.removedBlocks |= bit;
        data.setDirty();
    }
}
