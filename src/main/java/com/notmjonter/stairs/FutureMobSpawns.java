package com.notmjonter.stairs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Final spawn boundary for Minecraft 33.X while its mob roster is unfinished. */
public final class FutureMobSpawns {
    private static final Map<ServerLevel, BedSpawns> BED_SPAWNS = new WeakHashMap<>();
    private static final int BED_COOLDOWN = 20 * 60 * 5;
    private FutureMobSpawns() {}

    public static void joining(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !event.getLevel().dimension().equals(FutureDimension.KEY)) return;
        if (event.getEntity() instanceof Mob && !(event.getEntity() instanceof Villager)
                && !(event.getEntity() instanceof FutureReplayEntity)) {
            event.setCanceled(true);
        }
    }

    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(FutureDimension.KEY)
                || !level.tickRateManager().runsNormally() || FutureContainment.active(level)
                || level.players().stream().noneMatch(player -> player.isAlive() && !player.isSpectator())) return;
        BedSpawns state = BED_SPAWNS.computeIfAbsent(level, ignored -> new BedSpawns());
        if (state.remaining < 0) state.remaining = level.random.nextInt(1200, 2401);
        if (--state.remaining > 0) return;
        state.remaining = level.random.nextInt(1200, 2401);

        long now = level.getGameTime();
        state.usedBeds.entrySet().removeIf(entry -> now - entry.getValue() >= BED_COOLDOWN);
        var beds = new ArrayList<BlockPos>();
        var checked = new HashSet<Long>();
        for (var player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            ChunkPos center = player.chunkPosition();
            for (int x = center.x - 4; x <= center.x + 4; x++) for (int z = center.z - 4; z <= center.z + 4; z++) {
                if (!checked.add(ChunkPos.asLong(x, z))) continue;
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    var block = chunk.getBlockState(pos);
                    if (!(block.getBlock() instanceof BedBlock) || block.getValue(BedBlock.PART) != BedPart.HEAD
                            || block.getValue(BedBlock.OCCUPIED) || state.usedBeds.containsKey(pos)) continue;
                    Vec3 spawn = bedSpawn(pos);
                    if (level.players().stream().anyMatch(observer -> !farEnough(spawn.distanceToSqr(observer.position())))) continue;
                    BlockPos foot = pos.relative(block.getValue(BedBlock.FACING).getOpposite());
                    var footChunk = level.getChunkSource().getChunkNow(foot.getX() >> 4, foot.getZ() >> 4);
                    if (footChunk == null || !footChunk.getBlockState(foot).is(block.getBlock())) continue;
                    beds.add(pos.immutable());
                }
            }
        }
        while (!beds.isEmpty()) {
            BlockPos bed = beds.remove(level.random.nextInt(beds.size()));
            Vec3 spawn = bedSpawn(bed);
            Villager villager = EntityType.VILLAGER.create(level);
            if (villager == null) return;
            villager.moveTo(spawn.x, spawn.y, spawn.z, level.random.nextFloat() * 360, 0);
            if (!level.getWorldBorder().isWithinBounds(villager.getBoundingBox())
                    || !level.noCollision(villager, villager.getBoundingBox())
                    || !level.getFluidState(bed.above()).isEmpty()
                    || !level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(4)).isEmpty()) continue;
            villager.finalizeSpawn(level, level.getCurrentDifficultyAt(bed), MobSpawnType.EVENT, null);
            villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
            if (level.addFreshEntity(villager)) {
                state.usedBeds.put(bed, now);
                return;
            }
        }
    }

    static boolean farEnough(double distanceSquared) { return distanceSquared >= 12 * 12; }
    private static Vec3 bedSpawn(BlockPos pos) { return new Vec3(pos.getX() + 0.5, pos.getY() + 9.0 / 16, pos.getZ() + 0.5); }
    public static void stopped(ServerStoppedEvent event) { BED_SPAWNS.clear(); }
    private static final class BedSpawns {
        int remaining = -1;
        final Map<BlockPos, Long> usedBeds = new HashMap<>();
    }
}
