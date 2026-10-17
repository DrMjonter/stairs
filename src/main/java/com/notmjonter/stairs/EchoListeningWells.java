package com.notmjonter.stairs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One well design; each physical well can grant one sequential reference, once globally. */
public final class EchoListeningWells {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Stairs.MODID);
    public static final net.neoforged.neoforge.registries.DeferredHolder<Feature<?>, ListeningWellFeature> FEATURE =
            FEATURES.register("listening_well", ListeningWellFeature::new);
    public static final DeferredBlock<WellBarrierBlock> BARRIER = FutureBlocks.BLOCKS.register("well_barrier",
            () -> new WellBarrierBlock(BlockBehaviour.Properties.of().strength(-1, 3600000).noOcclusion().dynamicShape()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false)
                    .noLootTable().pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> state.getValue(WellBarrierBlock.ACTIVE) ? 8 : 0)));
    private static final String[] NAMES = {"presence", "response", "recognition"};

    interface VoiceRecorder {
        boolean available(ServerPlayer player);
        boolean begin(Pending pending);
        void tick(net.minecraft.server.MinecraftServer server);
        void cancel(UUID player);
        void close();
    }
    static volatile VoiceRecorder recorder;
    private static final Map<UUID, Pending> pending = new HashMap<>();
    private static final Map<BlockPos, LightingState> lighting = new HashMap<>();
    private static final Map<UUID, BlockPos> playersOverWells = new HashMap<>();
    private static final Map<UUID, QueuedQuestion> queuedQuestions = new HashMap<>();
    private static final Map<UUID, Integer> nextGuidanceWhisper = new HashMap<>();
    private static final int MIN_GUIDANCE_DELAY = 45 * 20;
    private static final int MAX_GUIDANCE_DELAY = 120 * 20;
    private static final int APPROACH_POWER = 5;
    private static final int HELD_SHARD_POWER = 8;
    private static final int OFFERED_SHARD_POWER = 11;
    record Pending(ServerPlayer player, ServerLevel level, BlockPos center, int reference, int started) {}
    private record QueuedQuestion(ServerPlayer player, BlockPos center, int reference, int playTick) {}

    private static final class LightingState {
        boolean occupied;
        boolean holdingShard;
        boolean recording;
        boolean resetting;
        int successAge = -1;

        void clearSensors() {
            occupied = false;
            holdingShard = false;
            recording = false;
        }
    }

    private EchoListeningWells() {}

    static Pending pending(UUID player) { return pending.get(player); }

    static boolean near(Pending request) {
        var player = request.player();
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.serverLevel() == request.level()
                && player.distanceToSqr(request.center().getX() + 0.5, request.center().getY() + 1,
                        request.center().getZ() + 0.5) <= 64;
    }

    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        for (Pending request : List.copyOf(pending.values()))
            if (!near(request) || event.getServer().getTickCount() - request.started() > 900)
                cancel(request.player().getUUID(), "cancelled");
        if (recorder != null) recorder.tick(event.getServer());
        updateLighting(event.getServer());
        tickGuidanceWhispers(event.getServer());
    }

    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (UUID player : List.copyOf(pending.keySet())) cancel(player, "cancelled");
        if (recorder != null) recorder.close();
        recorder = null;
        lighting.clear();
        playersOverWells.clear();
        queuedQuestions.clear();
        nextGuidanceWhisper.clear();
    }

    static void cancel(UUID player, String reason) {
        Pending request = pending.remove(player);
        if (request == null) return;
        if (recorder != null) recorder.cancel(player);
        beginFailure(request.center());
        playForNearby(request.level(), EchoSounds.WELL_DENY, request.center(), -3, 1.2F);
        var refund = new net.minecraft.world.item.ItemStack(DimensionBlocks.RESONANCE_SHARD.get());
        if (request.player().isAlive() && !request.player().isRemoved())
            request.player().getInventory().add(refund);
        if (!refund.isEmpty()) request.level().addFreshEntity(new ItemEntity(request.level(),
                request.center().getX() + 3.5, request.center().getY() + 1, request.center().getZ() + 0.5, refund));
        request.player().displayClientMessage(
                Component.translatable("message.mjontersstairs.wells." + reason), true);
    }

    static Component name(int reference) {
        return Component.translatable("well.mjontersstairs." + NAMES[reference]);
    }

    static BlockPos center(BlockPos grate) {
        // The feature always centers on local chunk coordinates 8,8.
        return new BlockPos((grate.getX() & ~15) + 8, grate.getY(), (grate.getZ() & ~15) + 8);
    }

    static boolean isWell(ServerLevel level, BlockPos center) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (!level.getBlockState(center.offset(x, 0, z)).is(BARRIER)) return false;
        return level.getBlockState(center.below(24)).is(DimensionBlocks.ECHO_LANTERN);
    }

    /** Lantern positions ordered from the bottom of the shaft to its four surface lamps. */
    static BlockPos[] lanternLayer(BlockPos center, int layer) {
        return switch (layer) {
            case 0 -> new BlockPos[]{center.below(24)};
            case 1 -> shaftLanterns(center, -18);
            case 2 -> shaftLanterns(center, -12);
            case 3 -> shaftLanterns(center, -6);
            case 4 -> new BlockPos[]{center.offset(-3, 3, -3), center.offset(-3, 3, 3),
                    center.offset(3, 3, -3), center.offset(3, 3, 3)};
            default -> throw new IllegalArgumentException("Unknown well lantern layer " + layer);
        };
    }

    private static BlockPos[] shaftLanterns(BlockPos center, int y) {
        return new BlockPos[]{
                center.offset(2, y, -1), center.offset(2, y, 1),
                center.offset(-2, y, -1), center.offset(-2, y, 1),
                center.offset(-1, y, 2), center.offset(1, y, 2),
                center.offset(-1, y, -2), center.offset(1, y, -2)
        };
    }

    static void updateLighting(net.minecraft.server.MinecraftServer server, ServerPlayer... extraPlayers) {
        ServerLevel level = server.getLevel(EchoBasinDimension.KEY);
        if (level == null) return;
        lighting.values().forEach(LightingState::clearSensors);

        Set<UUID> sensedPlayers = new HashSet<>();
        for (ServerPlayer player : level.players()) sensePlayer(level, player, sensedPlayers);
        for (ServerPlayer player : extraPlayers)
            if (!level.players().contains(player)) sensePlayer(level, player, sensedPlayers);
        playersOverWells.keySet().removeIf(player -> !sensedPlayers.contains(player));
        queuedQuestions.entrySet().removeIf(entry ->
                !entry.getValue().center().equals(playersOverWells.get(entry.getKey())));
        playQueuedQuestions(server);
        for (Pending request : pending.values()) {
            if (request.level() != level) continue;
            LightingState state = lighting.computeIfAbsent(request.center(), ignored -> new LightingState());
            state.recording = true;
        }

        StaircaseData wellData = StaircaseData.get(level);
        for (var entry : List.copyOf(lighting.entrySet())) {
            BlockPos center = entry.getKey();
            LightingState state = entry.getValue();
            if (!level.hasChunkAt(center) || !isWell(level, center)) {
                lighting.remove(center);
                continue;
            }
            boolean active = wellData.activatedEchoWells.contains(center)
                    || level.getBlockState(center).getValue(WellBarrierBlock.ACTIVE);
            int[] targets = new int[5];
            if (active) {
                if (state.successAge < 0) {
                    java.util.Arrays.fill(targets, 15);
                } else {
                    java.util.Arrays.fill(targets, -1);
                    for (int layer = 0; layer < targets.length; layer++)
                        if (state.successAge >= layer * 6) targets[layer] = 15;
                    state.successAge++;
                }
            } else if (state.resetting) {
                // A failed capture visibly returns all the way to darkness before
                // the normal proximity sequence is allowed to begin again.
            } else if (state.recording) {
                targets[0] = APPROACH_POWER;
                targets[1] = HELD_SHARD_POWER;
                targets[2] = OFFERED_SHARD_POWER;
            } else if (state.occupied) {
                targets[0] = APPROACH_POWER;
                if (state.holdingShard) targets[1] = HELD_SHARD_POWER;
            }

            for (int layer = 0; layer < targets.length; layer++) {
                for (BlockPos lantern : lanternLayer(center, layer)) {
                    int target = state.recording && !active && layer == 4
                            ? flickerPower(server.getTickCount(), lantern) : targets[layer];
                    if (target < 0) continue;
                    moveLantern(level, lantern, target, state.recording && !active && layer == 4 ? 4 : 1);
                }
            }

            boolean settled = active ? allLanternsAt(level, center, 15) : allLanternsAt(level, center, 0);
            if (!active && state.resetting && settled) state.resetting = false;
            if (active && settled && state.successAge >= 0) {
                playForNearby(level, EchoSounds.WELL_SUCCESS, center, -2, 1.35F);
                state.successAge = -1;
            }
            if ((active && settled) || (!active && settled && !state.occupied && !state.recording))
                lighting.remove(center);
        }
    }

    private static void sensePlayer(ServerLevel level, ServerPlayer player, Set<UUID> sensedPlayers) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator() || player.serverLevel() != level) return;
        BlockPos standingOn = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
        if (!level.getBlockState(standingOn).is(BARRIER)) return;
        BlockPos center = center(standingOn);
        if (!isWell(level, center)) return;
        UUID playerId = player.getUUID();
        sensedPlayers.add(playerId);
        BlockPos previous = playersOverWells.put(playerId, center.immutable());
        if (!center.equals(previous)) queueQuestion(player, level, center);
        LightingState state = lighting.computeIfAbsent(center.immutable(), ignored -> new LightingState());
        state.occupied = true;
        state.holdingShard |= player.getMainHandItem().is(DimensionBlocks.RESONANCE_SHARD)
                || player.getOffhandItem().is(DimensionBlocks.RESONANCE_SHARD);
    }

    private static void queueQuestion(ServerPlayer player, ServerLevel level, BlockPos center) {
        StaircaseData wells = StaircaseData.get(level);
        if (wells.activatedEchoWells.contains(center)
                || level.getBlockState(center).getValue(WellBarrierBlock.ACTIVE)) return;
        int reference = StaircaseData.get(player.server.overworld()).nextEchoReference(player.getUUID());
        if (reference >= EchoSounds.WELL_QUESTIONS.length) return;
        playAt(player, EchoSounds.WELL_CUE, center, -2, 1.1F);
        queuedQuestions.put(player.getUUID(), new QueuedQuestion(player, center.immutable(), reference,
                player.server.getTickCount() + 10));
    }

    private static void playQueuedQuestions(net.minecraft.server.MinecraftServer server) {
        int tick = server.getTickCount();
        for (var entry : List.copyOf(queuedQuestions.entrySet())) {
            QueuedQuestion question = entry.getValue();
            if (tick < question.playTick()) continue;
            queuedQuestions.remove(entry.getKey());
            ServerPlayer player = question.player();
            if (!player.isAlive() || player.isRemoved() || player.serverLevel().dimension() != EchoBasinDimension.KEY
                    || !question.center().equals(playersOverWells.get(player.getUUID()))
                    || StaircaseData.get(player.server.overworld()).nextEchoReference(player.getUUID())
                            != question.reference()) continue;
            player.displayClientMessage(Component.translatable(
                    "well.mjontersstairs.phrase_" + NAMES[question.reference()]), true);
            playAt(player, EchoSounds.WELL_QUESTIONS[question.reference()], question.center(), -8, 1.35F);
        }
    }

    private static void playAt(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, BlockPos center,
            int yOffset, float volume) {
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                SoundSource.BLOCKS, center.getX() + 0.5, center.getY() + yOffset + 0.5, center.getZ() + 0.5,
                volume, 1, player.getRandom().nextLong()));
    }

    private static void playForNearby(ServerLevel level, net.minecraft.sounds.SoundEvent sound, BlockPos center,
            int yOffset, float volume) {
        level.playSound(null, center.getX() + 0.5, center.getY() + yOffset + 0.5, center.getZ() + 0.5,
                sound, SoundSource.BLOCKS, volume, 1);
    }

    private static void tickGuidanceWhispers(net.minecraft.server.MinecraftServer server) {
        ServerLevel level = server.getLevel(EchoBasinDimension.KEY);
        if (level == null) {
            nextGuidanceWhisper.clear();
            return;
        }
        int now = server.getTickCount();
        Set<UUID> present = new HashSet<>();
        StaircaseData wells = StaircaseData.get(level);
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isRemoved() || player.isSpectator()) continue;
            UUID id = player.getUUID();
            present.add(id);
            int due = nextGuidanceWhisper.computeIfAbsent(id, ignored -> nextGuidanceTick(player, now));
            if (now < due) continue;
            guidanceTarget(wells, player).ifPresent(center -> playGuidanceWhisperAt(player, center));
            nextGuidanceWhisper.put(id, nextGuidanceTick(player, now));
        }
        nextGuidanceWhisper.keySet().removeIf(id -> !present.contains(id));
    }

    private static int nextGuidanceTick(ServerPlayer player, int now) {
        return now + MIN_GUIDANCE_DELAY
                + player.getRandom().nextInt(MAX_GUIDANCE_DELAY - MIN_GUIDANCE_DELAY + 1);
    }

    private static java.util.Optional<BlockPos> nearestUnactivatedWell(StaircaseData wells, ServerPlayer player) {
        return wells.knownEchoWells.stream()
                .filter(center -> !wells.activatedEchoWells.contains(center))
                .min(java.util.Comparator.comparingDouble(center -> center.distToCenterSqr(player.position())));
    }

    private static java.util.Optional<BlockPos> guidanceTarget(StaircaseData basin, ServerPlayer player) {
        StaircaseData progress = StaircaseData.get(player.server.overworld());
        if (!progress.echoComplete(player.getUUID())) return nearestUnactivatedWell(basin, player);

        if (basin.echoExitStairs.isEmpty()) {
            basin.activatedEchoWells.stream()
                    .min(java.util.Comparator.comparingDouble(center -> center.distToCenterSqr(player.position())))
                    .ifPresent(center -> EchoExitStaircases.spawn(player.serverLevel(), center));
        }
        return basin.echoExitStairs.keySet().stream()
                .min(java.util.Comparator.comparingDouble(origin -> origin.distToCenterSqr(player.position())));
    }

    static boolean playGuidanceWhisper(ServerPlayer player) {
        if (player.serverLevel().dimension() != EchoBasinDimension.KEY) return false;
        var target = guidanceTarget(StaircaseData.get(player.serverLevel()), player);
        if (target.isEmpty()) return false;
        playGuidanceWhisperAt(player, target.get());
        return true;
    }

    private static void playGuidanceWhisperAt(ServerPlayer player, BlockPos well) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new EchoGuidancePayload(well));
    }

    static boolean contains(ServerLevel level, BlockPos pos, int radius) {
        if (level.dimension() != EchoBasinDimension.KEY) return false;
        for (BlockPos center : StaircaseData.get(level).knownEchoWells) {
            int dx = Math.abs(pos.getX() - center.getX());
            int dy = pos.getY() - center.getY();
            int dz = Math.abs(pos.getZ() - center.getZ());
            boolean platform = dx <= 5 + radius && dz <= 5 + radius
                    && dy >= -2 - radius && dy <= radius;
            boolean shaft = dx <= 2 + radius && dz <= 2 + radius
                    && dy >= -24 - radius && dy <= radius;
            boolean surfaceLights = dx <= 3 + radius && dz <= 3 + radius
                    && dy >= 1 - radius && dy <= 3 + radius;
            if (platform || shaft || surfaceLights) return true;
        }
        return false;
    }

    private static void moveLantern(ServerLevel level, BlockPos pos, int target, int step) {
        var state = level.getBlockState(pos);
        if (!state.is(DimensionBlocks.ECHO_LANTERN)) return;
        int power = state.getValue(EchoLanternBlock.POWER);
        int next = power < target ? Math.min(target, power + step) : Math.max(target, power - step);
        if (next != power) level.setBlock(pos, state.setValue(EchoLanternBlock.POWER, next), 2);
    }

    private static int flickerPower(int tick, BlockPos pos) {
        long value = (tick / 2L) * 1103515245L
                + pos.getX() * 73428767L + pos.getY() * 19349663L + pos.getZ() * 912931L;
        return switch ((int) Math.floorMod(value, 8)) {
            case 0 -> 0;
            case 1, 2 -> 15;
            default -> 3 + (int) Math.floorMod(value >>> 4, 9);
        };
    }

    private static boolean allLanternsAt(ServerLevel level, BlockPos center, int power) {
        for (int layer = 0; layer < 5; layer++) for (BlockPos pos : lanternLayer(center, layer)) {
            var state = level.getBlockState(pos);
            if (!state.is(DimensionBlocks.ECHO_LANTERN) || state.getValue(EchoLanternBlock.POWER) != power) return false;
        }
        return true;
    }

    private static void beginSuccess(BlockPos center) {
        LightingState state = lighting.computeIfAbsent(center.immutable(), ignored -> new LightingState());
        state.successAge = 0;
        state.recording = false;
        state.resetting = false;
    }

    private static void beginFailure(BlockPos center) {
        LightingState state = lighting.computeIfAbsent(center.immutable(), ignored -> new LightingState());
        state.recording = false;
        state.resetting = true;
    }

    static boolean activate(ServerLevel level, BlockPos center, ItemEntity item) {
        if (level.dimension() != EchoBasinDimension.KEY || item.level() != level || item.isRemoved()
                || !item.getItem().is(DimensionBlocks.RESONANCE_SHARD.get())
                || !(item.getOwner() instanceof ServerPlayer player)
                || player.serverLevel() != level || !player.isAlive() || player.isSpectator()) return false;
        // Only a shard physically inside the three-by-three opening may activate a real well.
        if (item.getX() < center.getX() - 1 || item.getX() >= center.getX() + 2
                || item.getZ() < center.getZ() - 1 || item.getZ() >= center.getZ() + 2
                || item.getY() < center.getY() || item.getY() >= center.getY() + 1
                || !isWell(level, center)) return false;
        StaircaseData wells = StaircaseData.get(level);
        StaircaseData progress = StaircaseData.get(player.server.overworld());
        int reference = progress.nextEchoReference(player.getUUID());
        if (reference == 3 || wells.activatedEchoWells.contains(center) || pending.containsKey(player.getUUID())
                || pending.values().stream().anyMatch(request -> request.center().equals(center))) return false;
        Pending request = new Pending(player, level, center.immutable(), reference, player.server.getTickCount());
        if (!near(request) || recorder == null || !recorder.available(player) || !recorder.begin(request)) {
            playForNearby(level, EchoSounds.WELL_DENY, center, -3, 1.2F);
            player.displayClientMessage(
                    Component.translatable("message.mjontersstairs.wells.voice_unavailable"), true);
            // An unavailable microphone must not strand the offered item down an inaccessible shaft.
            var returned = item.getItem().copy();
            player.getInventory().add(returned);
            if (returned.isEmpty()) item.discard();
            else {
                item.setItem(returned);
                item.setPos(center.getX() + 3.5, center.getY() + 1, center.getZ() + 0.5);
                item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            }
            return false;
        }
        pending.put(player.getUUID(), request);
        var remaining = item.getItem().copy();
        remaining.shrink(1);
        if (remaining.isEmpty()) item.discard();
        else item.setItem(remaining);
        player.displayClientMessage(Component.translatable("message.mjontersstairs.wells.prompt",
                Component.translatable("well.mjontersstairs.phrase_" + NAMES[reference])), true);
        return true;
    }

    /** Called only after the server has captured speech and finished echoing it from the well. */
    static boolean complete(ServerPlayer player, BlockPos center, int reference, short[] pcm) {
        Pending request = pending.get(player.getUUID());
        if (request == null || request.reference() != reference || !request.center().equals(center) || !near(request)
                || !isWell(request.level(), center)) return false;
        ServerLevel level = request.level();
        StaircaseData wells = StaircaseData.get(level);
        StaircaseData progress = StaircaseData.get(player.server.overworld());
        if (wells.activatedEchoWells.contains(center) || progress.nextEchoReference(player.getUUID()) != reference) return false;
        String file;
        try {
            file = EchoVoiceArchive.save(player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT),
                    player.getUUID(), reference, pcm);
        } catch (java.io.IOException | IllegalArgumentException error) {
            org.slf4j.LoggerFactory.getLogger(EchoListeningWells.class).error("Could not save well voice reference", error);
            cancel(player.getUUID(), "recording_failed");
            return false;
        }
        pending.remove(player.getUUID());
        wells.activateEchoWell(center);
        progress.recordEchoReference(player.getUUID(), reference);
        progress.echoVoiceLines.computeIfAbsent(player.getUUID(), ignored -> new net.minecraft.nbt.CompoundTag())
                .putString(NAMES[reference], file);
        progress.setDirty();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            level.setBlock(center.offset(x, 0, z), BARRIER.get().defaultBlockState()
                    .setValue(WellBarrierBlock.ACTIVE, true), 3);
        beginSuccess(center);
        playForNearby(level, EchoSounds.WELL_ACTIVATION, center, -8, 1.5F);
        level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS,
                1.5f, 0.65f + reference * 0.2f);
        level.sendParticles(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5,
                40, 0.8, 1.2, 0.8, 0.02);
        if (progress.echoComplete(player.getUUID())) {
            boolean exit = EchoExitStaircases.spawn(level, center);
            player.displayClientMessage(Component.translatable("message.mjontersstairs.wells.complete"), true);
            if (!exit) player.sendSystemMessage(Component.translatable("message.mjontersstairs.wells.exit_failed"));
            level.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1, 0.6f);
        } else {
            player.displayClientMessage(Component.translatable("message.mjontersstairs.wells.recorded", name(reference),
                    progress.echoReferenceCount(player.getUUID())), true);
        }
        return true;
    }
}
