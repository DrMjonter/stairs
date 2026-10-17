package com.notmjonter.stairs;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class GarageTravel {
    private static final Map<UUID, Pending> pending = new HashMap<>();
    private GarageTravel() {}

    public static BlockPos arrival(BlockPos stairOrigin) {
        return new BlockPos(stairOrigin.getX(), FutureDimension.SURFACE_Y + 1, stairOrigin.getZ());
    }

    public static boolean begin(ServerPlayer player, StaircaseData.Staircase stair) {
        if (pending.containsKey(player.getUUID())) return true;
        if (stair.destination == MirrorDimension.DESTINATION && MirrorSnapshots.busy(player.server)) {
            player.sendSystemMessage(Component.translatable("message.mjontersstairs.travel.mirror_busy"));
            return false;
        }
        boolean returning = (player.serverLevel().dimension() == MirrorDimension.KEY
                && stair.destination == MirrorDimension.DESTINATION)
                || (player.serverLevel().dimension() == EchoBasinDimension.KEY
                && stair.destination == EchoBasinDimension.DESTINATION && stair.returnOrigin != null)
                || (player.serverLevel().dimension() == TickstormDimension.KEY
                && stair.destination == TickstormDimension.DESTINATION && stair.returnOrigin != null)
                || (player.serverLevel().dimension() == GarbageCollectorDimension.KEY
                && stair.destination == GarbageCollectorDimension.DESTINATION && stair.returnOrigin != null);
        if (returning && stair.destination == EchoBasinDimension.DESTINATION
                && !StaircaseData.get(player.server.overworld()).echoComplete(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.mjontersstairs.wells.return_locked",
                    StaircaseData.get(player.server.overworld()).echoReferenceCount(player.getUUID())), true);
            return false;
        }
        if (!returning && (player.serverLevel().dimension() != Level.OVERWORLD
                || (stair.destination != 1 && stair.destination != UnfinishedSunDimension.DESTINATION
                    && stair.destination != EchoBasinDimension.DESTINATION
                    && stair.destination != TickstormDimension.DESTINATION
                    && stair.destination != GarbageCollectorDimension.DESTINATION
                    && stair.destination != MirrorDimension.DESTINATION))) return false;
        ServerLevel garage = player.server.getLevel(returning ? Level.OVERWORLD
                : destinationKey(stair.destination));
        if (garage == null) {
            player.sendSystemMessage(Component.translatable("message.mjontersstairs.travel.dimension_unavailable"));
            return false;
        }
        BlockPos destination = stair.destination == MirrorDimension.DESTINATION ? player.blockPosition() : arrival(stair.origin);
        pending.put(player.getUUID(), new Pending(player.position(), destination, stair.position(2, 1, 0),
                player.getYRot(), player.getXRot(), stair, returning, false));
        PacketDistributor.sendToPlayer(player, new GarageTransitionPayload(true));
        return true;
    }

    static boolean beginEchoExit(ServerPlayer player) {
        if (pending.containsKey(player.getUUID())) return true;
        if (player.serverLevel().dimension() != EchoBasinDimension.KEY
                || !StaircaseData.get(player.server.overworld()).echoComplete(player.getUUID())) return false;
        StaircaseData overworld = StaircaseData.get(player.server.overworld());
        CompoundTag back = overworld.garageReturns.get(player.getUUID());
        if (back == null) return false;
        BlockPos recorded = BlockPos.of(back.getLong("Position"));
        StaircaseData.Staircase entrance = overworld.stairs.values().stream()
                .filter(stair -> stair.destination == EchoBasinDimension.DESTINATION
                        && stair.position(2, 1, 0).equals(recorded))
                .findFirst().orElse(null);
        if (entrance == null) return false;
        BlockPos target = entrance.position(2, 8, 9).above();
        float yaw = entrance.rotation.rotate(net.minecraft.core.Direction.NORTH).toYRot();
        pending.put(player.getUUID(), new Pending(player.position(), target, recorded, yaw, 20,
                entrance, true, true));
        PacketDistributor.sendToPlayer(player, new GarageTransitionPayload(true));
        return true;
    }

    public static void tick(ServerTickEvent.Post event) {
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            Pending trip = entry.getValue();
            ServerLevel garage = event.getServer().getLevel(trip.returning ? Level.OVERWORLD
                    : destinationKey(trip.stair.destination));
            if (player == null || garage == null || !player.isAlive()
                    || player.serverLevel().dimension() != (trip.returning ? destinationKey(trip.stair.destination) : Level.OVERWORLD)) {
                if (player != null) PacketDistributor.sendToPlayer(player, new GarageTransitionPayload(false));
                iterator.remove();
                continue;
            }
            if (player.position().distanceToSqr(trip.held) > 0.0025) player.teleportTo(trip.held.x, trip.held.y, trip.held.z);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            // Spread a 5x5 arrival pre-generation over 25 ticks. Temporary vanilla
            // post-teleport tickets expire automatically, including on cancellation.
            if (trip.ticks < 25) {
                int dx = trip.ticks % 5 - 2, dz = trip.ticks / 5 - 2;
                ChunkPos chunk = new ChunkPos((trip.arrival.getX() >> 4) + dx, (trip.arrival.getZ() >> 4) + dz);
                garage.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunk, 2, player.getId());
                garage.getChunk(chunk.x, chunk.z);
            }
            if (++trip.ticks < 35) continue;
            StaircaseData.Staircase arrivalStair = trip.echoExit ? trip.stair : trip.returning
                    ? StaircaseData.get(garage).stairs.get(trip.stair.returnOrigin == null ? trip.stair.origin : trip.stair.returnOrigin)
                    : StaircaseArrival.prepare(player.serverLevel(), garage, trip.stair);
            Vec3 target = trip.echoExit ? Vec3.atBottomCenterOf(trip.arrival)
                    : arrivalStair == null ? null : StaircaseArrival.translate(trip.held, trip.stair.origin, arrivalStair.origin);
            if (target == null || !garage.getWorldBorder().isWithinBounds(BlockPos.containing(target))
                    || !garage.noCollision(player, player.getBoundingBox().move(target.subtract(player.position())))) {
                PacketDistributor.sendToPlayer(player, new GarageTransitionPayload(false));
                player.sendSystemMessage(Component.translatable("message.mjontersstairs.travel.arrival_obstructed"));
                iterator.remove();
                continue;
            }
            StaircaseData data = StaircaseData.get(event.getServer().overworld());
            CompoundTag back = new CompoundTag();
            back.putLong("Position", trip.returnPos.asLong());
            back.putFloat("Yaw", trip.yaw);
            if (garage.dimension().equals(FutureDimension.KEY))
                back.putLong("FutureEntrance", arrivalStair.origin.asLong());
            if (player.teleportTo(garage, target.x, target.y, target.z, Set.of(),
                    trip.yaw, trip.pitch)) {
                if (!trip.returning) {
                    data.garageReturns.put(player.getUUID(), back);
                    data.setDirty();
                }
                if (trip.stair.destination == MirrorDimension.DESTINATION) {
                    // Refresh rather than remove: both ends use the same ascent envelope.
                    StaircaseEffects.apply(player, StaircaseLifecycle.ascentIntensity((float) (target.y - arrivalStair.origin.getY())));
                } else player.removeEffect(StaircaseEffects.WEIGHT);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
            } else {
                PacketDistributor.sendToPlayer(player, new GarageTransitionPayload(false));
                player.sendSystemMessage(Component.translatable("message.mjontersstairs.travel.failed"));
            }
            iterator.remove();
        }
    }

    public static int returnPlayer(ServerPlayer player) {
        if (player.serverLevel().dimension() != GarageDimension.KEY
                && player.serverLevel().dimension() != UnfinishedSunDimension.KEY
                && player.serverLevel().dimension() != EchoBasinDimension.KEY
                && player.serverLevel().dimension() != TickstormDimension.KEY
                && player.serverLevel().dimension() != GarbageCollectorDimension.KEY
                && player.serverLevel().dimension() != MirrorDimension.KEY) {
            player.sendSystemMessage(Component.translatable("message.mjontersstairs.travel.return_dimension",
                    Component.translatable("itemGroup.mjontersstairs.dimension_1"),
                    Component.translatable("itemGroup.mjontersstairs.dimension_2"),
                    Component.translatable("itemGroup.mjontersstairs.dimension_3"),
                    Component.translatable("itemGroup.mjontersstairs.dimension_4"),
                    Component.translatable("itemGroup.mjontersstairs.dimension_5"),
                    Component.translatable("itemGroup.mjontersstairs.dimension_7")));
            return 0;
        }
        ServerLevel overworld = player.server.overworld();
        StaircaseData data = StaircaseData.get(overworld);
        CompoundTag back = data.garageReturns.get(player.getUUID());
        BlockPos destination = back == null ? overworld.getSharedSpawnPos() : BlockPos.of(back.getLong("Position"));
        overworld.getChunkAt(destination);
        // Find headroom and footing near the recorded entrance rather than assuming
        // that terrain has remained unchanged since entering.
        BlockPos safe = null;
        if (overworld.getWorldBorder().isWithinBounds(destination) && StairsCommands.isSafeLanding(overworld, destination))
            safe = destination;
        for (int radius = 0; radius <= 8 && safe == null; radius++) {
            for (int x = -radius; x <= radius && safe == null; x++) for (int z = -radius; z <= radius; z++) {
                int wx = destination.getX() + x, wz = destination.getZ() + z;
                overworld.getChunk(wx >> 4, wz >> 4);
                BlockPos pos = new BlockPos(wx, overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz), wz);
                if (overworld.getWorldBorder().isWithinBounds(pos) && StairsCommands.isSafeLanding(overworld, pos)) { safe = pos; break; }
            }
        }
        if (safe == null) {
            player.sendSystemMessage(Component.translatable("message.mjontersstairs.travel.no_safe_return"));
            return 0;
        }
        if (!player.teleportTo(overworld, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, Set.of(), back == null ? 0 : back.getFloat("Yaw"), 0)) return 0;
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        return 1;
    }

    /** A failed simulation attempt returns locally; this never calls change-dimension travel. */
    public static boolean resetFuturePlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(FutureDimension.KEY)) return false;
        StaircaseData future = StaircaseData.get(level);
        StaircaseData source = StaircaseData.get(player.server.overworld());
        CompoundTag back = source.garageReturns.get(player.getUUID());
        StaircaseData.Staircase entrance = futureEntrance(future, source, back);
        if (entrance == null) {
            player.sendSystemMessage(Component.translatable("message.mjontersstairs.travel.no_simulation_entrance"));
            return false;
        }
        BlockPos feet = entrance.position(2, 2, 2);
        level.getChunkAt(feet);
        Vec3 target = Vec3.atBottomCenterOf(feet);
        // Stair tops need not have a full square upper face, unlike the generic surface landing check.
        boolean clear = level.getWorldBorder().isWithinBounds(feet)
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty()
                && level.noCollision(player, player.getBoundingBox().move(target.subtract(player.position())));
        if (!clear) {
            BlockPos safe = findFutureArrival(level, entrance.origin);
            if (safe == null) return false;
            target = Vec3.atBottomCenterOf(safe);
        }
        player.stopFallFlying();
        player.teleportTo(target.x, target.y, target.z);
        player.setYRot(back == null ? player.getYRot() : back.getFloat("Yaw"));
        player.setXRot(0);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        return true;
    }

    static StaircaseData.Staircase futureEntrance(StaircaseData future, StaircaseData source, CompoundTag back) {
        if (back == null) return null;
        if (back.contains("FutureEntrance")) {
            var entrance = future.stairs.get(BlockPos.of(back.getLong("FutureEntrance")));
            if (entrance != null) return entrance;
        }
        // Resolve existing entrance records through the originating staircase's saved return position.
        BlockPos returnPosition = BlockPos.of(back.getLong("Position"));
        for (var stair : source.stairs.values()) {
            if (!stair.position(2, 1, 0).equals(returnPosition)) continue;
            for (var candidate : future.stairs.values())
                if (stair.origin.equals(candidate.returnOrigin)) return candidate;
        }
        return null;
    }

    static BlockPos findFutureArrival(ServerLevel level, BlockPos column) {
        for (int radius = 0; radius <= 8; radius++) {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                int wx = column.getX() + x, wz = column.getZ() + z;
                level.getChunk(wx >> 4, wz >> 4);
                BlockPos feet = new BlockPos(wx, level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz), wz);
                if (level.getWorldBorder().isWithinBounds(feet) && StairsCommands.isSafeLanding(level, feet)) return feet;
            }
        }
        return null;
    }

    static boolean hasMirrorTrips() {
        return pending.values().stream().anyMatch(trip -> trip.stair.destination == MirrorDimension.DESTINATION);
    }

    private static net.minecraft.resources.ResourceKey<Level> destinationKey(int destination) {
        return switch (destination) {
            case EchoBasinDimension.DESTINATION -> EchoBasinDimension.KEY;
            case UnfinishedSunDimension.DESTINATION -> UnfinishedSunDimension.KEY;
            case TickstormDimension.DESTINATION -> TickstormDimension.KEY;
            case GarbageCollectorDimension.DESTINATION -> GarbageCollectorDimension.KEY;
            case MirrorDimension.DESTINATION -> MirrorDimension.KEY;
            default -> GarageDimension.KEY;
        };
    }

    public static void stopped(ServerStoppedEvent event) { pending.clear(); }

    private static final class Pending {
        final Vec3 held;
        final BlockPos arrival;
        final BlockPos returnPos;
        final float yaw;
        final float pitch;
        final StaircaseData.Staircase stair;
        final boolean returning;
        final boolean echoExit;
        int ticks;
        Pending(Vec3 held, BlockPos arrival, BlockPos returnPos, float yaw, float pitch,
                StaircaseData.Staircase stair, boolean returning, boolean echoExit) {
            this.held = held; this.arrival = arrival; this.returnPos = returnPos; this.yaw = yaw;
            this.pitch = pitch; this.stair = stair; this.returning = returning; this.echoExit = echoExit;
        }
    }
}
