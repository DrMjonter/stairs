package com.notmjonter.stairs;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Stairs.MODID)
@PrefixGameTestTemplate(false)
public final class GarageGameTests {
    private GarageGameTests() {}
    @GameTest(template = "garage_test_empty", timeoutTicks = 200)
    public static void listeningWellVoiceCancellation(GameTestHelper helper) {
        var originalRecorder = EchoListeningWells.recorder;
        var level = helper.getLevel().getServer().getLevel(EchoBasinDimension.KEY);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "VoiceCancelTester"));
        BlockPos floor = new BlockPos(280008, 64, 280008);
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) for (int y = -24; y <= 5; y++)
            level.setBlock(floor.offset(x, y, z), y <= 0 ? DimensionBlocks.ECHO_STONE.get().defaultBlockState()
                    : Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(ListeningWellFeature.build(level, floor), "Cancellation test well must build");
        player.setPos(floor.getX() + 3.5, floor.getY() + 1, floor.getZ() + 0.5);
        try {
            EchoListeningWells.recorder = null;
            int initial = player.getInventory().countItem(DimensionBlocks.RESONANCE_SHARD.get());
            var offered = new net.minecraft.world.entity.item.ItemEntity(level, floor.getX() + 0.5,
                    floor.getY() + 0.5, floor.getZ() + 0.5,
                    new net.minecraft.world.item.ItemStack(DimensionBlocks.RESONANCE_SHARD.get()));
            offered.setThrower(player);
            helper.assertTrue(!EchoListeningWells.activate(level, floor, offered) && offered.isRemoved()
                            && player.getInventory().countItem(DimensionBlocks.RESONANCE_SHARD.get()) == initial + 1,
                    "Unavailable voice must return the offering without spending a well");
            EchoListeningWells.recorder = new EchoListeningWells.VoiceRecorder() {
                public boolean available(net.minecraft.server.level.ServerPlayer player) { return true; }
                public boolean begin(EchoListeningWells.Pending request) { return true; }
                public void tick(net.minecraft.server.MinecraftServer server) {}
                public void cancel(java.util.UUID player) {}
                public void close() {}
            };
            offered = new net.minecraft.world.entity.item.ItemEntity(level, floor.getX() + 0.5,
                    floor.getY() + 0.5, floor.getZ() + 0.5,
                    new net.minecraft.world.item.ItemStack(DimensionBlocks.RESONANCE_SHARD.get()));
            offered.setThrower(player);
            helper.assertTrue(EchoListeningWells.activate(level, floor, offered)
                            && EchoListeningWells.pending(player.getUUID()) != null,
                    "An available recorder must reserve the well");
            for (int tick = 0; tick < 12; tick++) EchoListeningWells.updateLighting(level.getServer(), player);
            EchoListeningWells.cancel(player.getUUID(), "cancelled");
            for (int tick = 0; tick < 15; tick++) EchoListeningWells.updateLighting(level.getServer(), player);
            helper.assertTrue(EchoListeningWells.pending(player.getUUID()) == null
                            && !StaircaseData.get(level).activatedEchoWells.contains(floor)
                            && StaircaseData.get(level.getServer().overworld()).echoReferenceCount(player.getUUID()) == 0
                            && player.getInventory().countItem(DimensionBlocks.RESONANCE_SHARD.get()) == initial + 2
                            && wellLayerAt(level, floor, 0, 0) && wellLayerAt(level, floor, 1, 0)
                            && wellLayerAt(level, floor, 2, 0) && wellLayerAt(level, floor, 4, 0),
                    "Cancellation must refund the shard, darken its temporary lights, and keep progress untouched");
            helper.assertTrue(!EchoListeningWells.complete(player, floor, 0, new short[48000]),
                    "Late playback callbacks must not activate a cancelled well");
            helper.succeed();
        } finally {
            EchoListeningWells.cancel(player.getUUID(), "cancelled");
            EchoListeningWells.recorder = originalRecorder;
        }
    }

    @GameTest(template = "garage_test_empty", timeoutTicks = 600)
    public static void listeningWells(GameTestHelper helper) {
        var originalRecorder = EchoListeningWells.recorder;
        EchoListeningWells.recorder = new EchoListeningWells.VoiceRecorder() {
            public boolean available(net.minecraft.server.level.ServerPlayer player) { return true; }
            public boolean begin(EchoListeningWells.Pending request) { return true; }
            public void tick(net.minecraft.server.MinecraftServer server) {}
            public void cancel(java.util.UUID player) {}
            public void close() {}
        };
        try {
            var level = helper.getLevel().getServer().getLevel(EchoBasinDimension.KEY);
            helper.assertTrue(level != null, "Echo Basin must load");
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "WellTester"));
            var other = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "OtherTester"));
            var data = StaircaseData.get(level.getServer().overworld());
            var wells = StaircaseData.get(level);
            var stair = new StaircaseData.Staircase(new BlockPos(270000, 56, 270000),
                    net.minecraft.world.level.block.Rotation.NONE, EchoBasinDimension.DESTINATION);
            stair.returnOrigin = new BlockPos(270000, 70, 270000);
            helper.assertTrue(!GarageTravel.begin(player, stair), "Return stairs must reject missing references");
            for (int reference = 0; reference < 3; reference++) {
                BlockPos floor = new BlockPos(270008 + reference * 32, 64, 270008);
                for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) for (int y = -24; y <= 5; y++) {
                    level.setBlock(floor.offset(x, y, z), y <= 0 ? DimensionBlocks.ECHO_STONE.get().defaultBlockState()
                            : Blocks.AIR.defaultBlockState(), 2);
                }
                BlockPos obstruction = floor.offset(4, 1, 0);
                level.setBlock(obstruction, Blocks.CHEST.defaultBlockState(), 2);
                helper.assertTrue(!ListeningWellFeature.build(level, floor)
                                && level.getBlockState(obstruction).is(Blocks.CHEST)
                                && level.getBlockState(floor).is(DimensionBlocks.ECHO_STONE),
                        "Wells must reject containers without partially building");
                level.setBlock(obstruction, Blocks.AIR.defaultBlockState(), 2);
                var feature = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                        .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "listening_well"));
                helper.assertTrue(feature != null && feature.place(level, level.getChunkSource().getGenerator(),
                                net.minecraft.util.RandomSource.create(1), floor),
                        "The single configured well feature must load and place on dry shelves");
                if (reference == 0) {
                    helper.assertTrue(wellLayerAt(level, floor, 0, 0) && wellLayerAt(level, floor, 4, 0),
                            "A newly generated well must begin unlit");
                    player.setPos(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
                    for (int tick = 0; tick < 5; tick++) EchoListeningWells.updateLighting(level.getServer(), player);
                    helper.assertTrue(wellLayerAt(level, floor, 0, 5) && wellLayerAt(level, floor, 1, 0),
                            "Standing over the grate must smoothly light only the bottom lantern");
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                            new net.minecraft.world.item.ItemStack(DimensionBlocks.RESONANCE_SHARD.get()));
                    for (int tick = 0; tick < 8; tick++) EchoListeningWells.updateLighting(level.getServer(), player);
                    helper.assertTrue(wellLayerAt(level, floor, 1, 8),
                            "Holding a resonance shard over the grate must light the next shaft layer");
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                            net.minecraft.world.item.ItemStack.EMPTY);
                    player.setPos(floor.getX() + 3.5, floor.getY() + 1, floor.getZ() + 0.5);
                    for (int tick = 0; tick < 8; tick++) EchoListeningWells.updateLighting(level.getServer(), player);
                    helper.assertTrue(wellLayerAt(level, floor, 0, 0) && wellLayerAt(level, floor, 1, 0),
                            "Leaving an unused well must smoothly darken its sensing lights");
                }
                var grate = level.getBlockState(floor);
                helper.assertTrue(grate.is(EchoListeningWells.BARRIER) && level.getBlockState(floor.below(12)).isAir()
                                && level.getBlockState(floor.offset(-1, -12, 0)).isAir()
                                && level.getBlockState(floor.offset(3, 1, 0)).isAir()
                                && level.getBlockState(floor.below(24)).is(DimensionBlocks.ECHO_LANTERN),
                        "Wells must have a barrier, a deep lit shaft, and no ladders or resonators");
                helper.assertTrue(EchoListeningWells.lanternLayer(floor, 1).length == 8
                                && EchoListeningWells.lanternLayer(floor, 2).length == 8
                                && EchoListeningWells.lanternLayer(floor, 3).length == 8
                                && wellLayerAt(level, floor, 1, 0)
                                && wellLayerAt(level, floor, 2, 0)
                                && wellLayerAt(level, floor, 3, 0),
                        "Every underground lantern tier must be symmetrical across all four well walls");
                helper.assertTrue(!ListeningWellFeature.build(level, floor), "Existing wells must not be overwritten");
                var dropped = new net.minecraft.world.entity.item.ItemEntity(level, floor.getX() + 0.5,
                        floor.getY() + 2, floor.getZ() + 0.5,
                        new net.minecraft.world.item.ItemStack(DimensionBlocks.RESONANCE_SHARD.get(), 2));
                dropped.setThrower(player);
                dropped.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                player.setPos(floor.getX() + 3.5, floor.getY() + 1, floor.getZ() + 0.5);
                helper.assertTrue(grate.getCollisionShape(level, floor,
                                net.minecraft.world.phys.shapes.CollisionContext.of(dropped)).isEmpty()
                                && !grate.getCollisionShape(level, floor,
                                net.minecraft.world.phys.shapes.CollisionContext.of(player)).isEmpty(),
                        "The invisible barrier must pass dropped items and block players");
                var invalid = new net.minecraft.world.entity.item.ItemEntity(level, floor.getX() + 0.5,
                        floor.getY() + 0.5, floor.getZ() + 0.5,
                        new net.minecraft.world.item.ItemStack(DimensionBlocks.RESONANCE_SHARD.get()));
                helper.assertTrue(!EchoListeningWells.activate(level, floor, invalid),
                        "Unowned shards must not grant progress to the nearest player");
                invalid.setThrower(player);
                invalid.setItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE));
                helper.assertTrue(!EchoListeningWells.activate(level, floor, invalid), "Other items must not activate wells");
                // Real item tick/movement must cross the grate and call entityInside automatically.
                for (int tick = 0; tick < 30; tick++) dropped.tick();
                helper.assertTrue(!wells.activatedEchoWells.contains(floor)
                                && EchoListeningWells.pending(player.getUUID()) != null
                                && data.echoReferenceCount(player.getUUID()) == reference,
                        "Offering a shard must prompt recording without prematurely spending the well or granting progress");
                for (int tick = 0; tick < 12; tick++) EchoListeningWells.updateLighting(level.getServer(), player);
                helper.assertTrue(wellLayerAt(level, floor, 0, 5) && wellLayerAt(level, floor, 1, 8)
                                && wellLayerAt(level, floor, 2, 11) && wellLayerHasLight(level, floor, 4),
                        "Recording must retain its lower sequence and flicker the four surface lanterns");
                // Simulate a finished microphone recording and echo; real microphone testing needs a client.
                short[] voice = new short[48000];
                for (int sample = 0; sample < voice.length; sample++) voice[sample] = (short) (4000 * Math.sin(sample * 0.06));
                helper.assertTrue(EchoListeningWells.complete(player, floor, reference, voice),
                        "A completed voice echo must archive the voice and activate its reserved well");
                for (int tick = 0; tick < 50; tick++) EchoListeningWells.updateLighting(level.getServer(), player);
                helper.assertTrue(wells.activatedEchoWells.contains(floor) && data.hasEchoReference(player.getUUID(), reference)
                                && data.echoReferenceCount(player.getUUID()) == reference + 1
                                && wellLayerAt(level, floor, 0, 15) && wellLayerAt(level, floor, 1, 15)
                                && wellLayerAt(level, floor, 2, 15) && wellLayerAt(level, floor, 3, 15)
                                && wellLayerAt(level, floor, 4, 15),
                        "Success must grant progress and finish a permanent bottom-to-top full-bright wave");
                helper.assertTrue(dropped.getY() < floor.getY() && dropped.getItem().getCount() == 1
                                && level.getBlockState(floor).getValue(WellBarrierBlock.ACTIVE),
                        "Items must fall through, consume only one shard, and permanently light the spent well");
                dropped.setPos(floor.getX() + 0.5, floor.getY() + 0.5, floor.getZ() + 0.5);
                helper.assertTrue(!EchoListeningWells.activate(level, floor, dropped) && dropped.getItem().getCount() == 1,
                        "Repeated offerings must not reactivate a well or consume more shards");
                invalid.setItem(new net.minecraft.world.item.ItemStack(DimensionBlocks.RESONANCE_SHARD.get()));
                invalid.setThrower(other);
                helper.assertTrue(!EchoListeningWells.activate(level, floor, invalid)
                                && data.echoReferenceCount(other.getUUID()) == 0 && invalid.getItem().getCount() == 1,
                        "A well must remain spent for every player");
                helper.assertTrue(data.echoComplete(player.getUUID()) == (reference == 2),
                        "The third distinct well must unlock the completing player's return");
            }
            var restored = StaircaseData.load(data.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()), level.registryAccess());
            var restoredWells = StaircaseData.load(wells.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()), level.registryAccess());
            helper.assertTrue(restored.echoComplete(player.getUUID())
                            && restoredWells.activatedEchoWells.containsAll(wells.activatedEchoWells)
                            && !wells.echoExitStairs.isEmpty()
                            && restoredWells.echoExitStairs.equals(wells.echoExitStairs),
                    "Progress, spent wells, and the completed return staircase must survive restart");
            helper.assertTrue(restored.echoVoiceLines.get(player.getUUID()).getAllKeys().size() == 3,
                    "Each completed reference must persist its own archived voice-line path");
            helper.succeed();
        } finally {
            EchoListeningWells.recorder = originalRecorder;
        }
    }

    private static boolean wellLayerAt(net.minecraft.server.level.ServerLevel level, BlockPos center,
            int layer, int power) {
        for (BlockPos pos : EchoListeningWells.lanternLayer(center, layer))
            if (!level.getBlockState(pos).is(DimensionBlocks.ECHO_LANTERN)
                    || level.getBlockState(pos).getValue(EchoLanternBlock.POWER) != power) return false;
        return true;
    }

    private static boolean wellLayerHasLight(net.minecraft.server.level.ServerLevel level, BlockPos center, int layer) {
        for (BlockPos pos : EchoListeningWells.lanternLayer(center, layer))
            if (level.getBlockState(pos).is(DimensionBlocks.ECHO_LANTERN)
                    && level.getBlockState(pos).getValue(EchoLanternBlock.POWER) > 0) return true;
        return false;
    }

    @GameTest(template = "garage_test_empty", timeoutTicks = 200)
    public static void echoBasinTerrainProfile(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(EchoBasinDimension.KEY);
        helper.assertTrue(level != null, "Echo Basin must load");
        // Sample seeded density across a wide area without generating thousands of chunks.
        var density = level.getChunkSource().randomState().router().finalDensity();
        int flooded = 0, tall = 0, columns = 0;
        int lowestFloor = 128, highestFloor = 0;
        for (int x = -1024; x <= 1024; x += 32) for (int z = -1024; z <= 1024; z += 32) {
            int firstAir = -1, openRun = 0, longestRun = 0;
            boolean solidColumn = true;
            for (int y = 0; y < 128; y += 4) {
                double value = density.compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, y, z));
                helper.assertTrue(Double.isFinite(value), "Terrain density must remain finite");
                if (value < 0) {
                    if (firstAir < 0) firstAir = y;
                    openRun += 4;
                    longestRun = Math.max(longestRun, openRun);
                    if (y >= 40 && y <= 96) solidColumn = false;
                    if (y == 28) flooded++;
                } else openRun = 0;
                if (y == 0 || y == 124) helper.assertTrue(value > 0, "Terrain must seal floor and roof");
            }
            if (firstAir >= 0) {
                lowestFloor = Math.min(lowestFloor, firstAir);
                highestFloor = Math.max(highestFloor, firstAir);
            }
            if (longestRun >= 40) tall++;
            if (solidColumn) columns++;
        }
        helper.assertTrue(flooded > 0, "Terrain must include basins below the fluid level");
        helper.assertTrue(tall > 0, "Terrain must include spacious chambers");
        helper.assertTrue(columns > 0 && columns < 4225, "Terrain must include columns and open connections");
        helper.assertTrue(highestFloor - lowestFloor >= 24, "Basin floors must occupy different terrace elevations");
        org.slf4j.LoggerFactory.getLogger(GarageGameTests.class).info(
                "Echo Basin density profile: {} flooded samples, {} tall chambers, {} solid columns, first-air Y={}..{}",
                flooded, tall, columns, lowestFloor, highestFloor);
        helper.succeed();
    }

    @GameTest(template = "garage_test_empty", timeoutTicks = 20)
    public static void echoLanternProperties(GameTestHelper helper) {
        var state = DimensionBlocks.ECHO_LANTERN.get().defaultBlockState();
        helper.assertTrue(state.getValue(EchoLanternBlock.POWER) == 0
                        && state.getLightEmission(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)) == 0,
                "An unpowered Echo Lantern must be dark");
        for (int power = 1; power <= 15; power++) {
            var powered = state.setValue(EchoLanternBlock.POWER, power);
            helper.assertTrue(powered.getLightEmission(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)) == power,
                    "Echo Lantern light must match redstone strength " + power);
        }
        helper.assertTrue(!state.requiresCorrectToolForDrops(), "Echo Lantern must drop when mined by hand");
        var drops = net.minecraft.world.level.block.Block.getDrops(state, helper.getLevel(),
                helper.absolutePos(BlockPos.ZERO), null, null, net.minecraft.world.item.ItemStack.EMPTY);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(DimensionBlocks.ECHO_LANTERN_ITEM.get())
                        && drops.getFirst().getCount() == 1,
                "Echo Lantern must drop itself without Silk Touch or a tool");
        var sounds = state.getSoundType(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), null);
        helper.assertTrue(sounds.getBreakSound() == EchoSounds.LANTERN_BREAK
                        && sounds.getStepSound() == EchoSounds.LANTERN_STEP
                        && sounds.getPlaceSound() == EchoSounds.LANTERN_PLACE
                        && sounds.getHitSound() == EchoSounds.LANTERN_HIT
                        && sounds.getFallSound() == EchoSounds.LANTERN_FALL,
                "Every Echo Lantern block sound must use its custom event");
        helper.assertTrue(StairsCreativeTab.items(EchoBasinDimension.DESTINATION)
                        .contains(DimensionBlocks.ECHO_LANTERN_ITEM.get()),
                "Echo Lantern must appear in the Echo Basin creative section");
        helper.succeed();
    }

    @GameTest(template = "garage_test_empty", timeoutTicks = 200)
    public static void echoLavaHarmless(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(EchoBasinDimension.KEY);
        BlockPos center = new BlockPos(247000, 70, 247000);
        var chunk = new net.minecraft.world.level.ChunkPos(center);
        level.setChunkForced(chunk.x, chunk.z, true);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            level.setBlock(center.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 2);
            for (int y = -1; y <= 2; y++) level.setBlock(center.offset(x, y, z),
                    Math.abs(x) == 2 || Math.abs(z) == 2 ? Blocks.STONE.defaultBlockState()
                            : EchoLava.BLOCK.get().defaultBlockState(), 2);
        }
        var pig = net.minecraft.world.entity.EntityType.PIG.create(level);
        helper.assertTrue(pig != null, "Test entity must exist");
        pig.setNoAi(true);
        pig.setNoGravity(true);
        pig.setPersistenceRequired();
        pig.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        float health = pig.getHealth();
        BlockPos wood = center.offset(2, 3, 0);
        level.setBlock(wood, Blocks.OAK_PLANKS.defaultBlockState(), 2);
        level.setBlock(wood.above(), Blocks.AIR.defaultBlockState(), 2);
        for (int i = 0; i < 100; i++) EchoLava.SOURCE.get().randomTick(level, center,
                EchoLava.SOURCE.get().defaultFluidState(), level.random);
        helper.assertTrue(level.getBlockState(wood.above()).isAir(), "Echo Lava must not start nearby fires");
        // Tick the isolated entity directly: far-away GameTest dimension chunks
        // do not always reach entity-ticking readiness before the test's callback.
        try {
            for (int i = 0; i < 40; i++) pig.tick();
            helper.assertTrue(pig.isAlive() && pig.getHealth() == health && !pig.isOnFire(),
                    "Immersion must not damage or ignite entities: health=" + pig.getHealth()
                            + ", fire=" + pig.isOnFire() + ", position=" + pig.position()
                            + ", removal=" + pig.getRemovalReason()
                            + ", damage=" + (pig.getLastDamageSource() == null ? "none" : pig.getLastDamageSource().getMsgId()));
            helper.assertTrue(pig.isInFluidType(EchoLava.TYPE.get()), "Test entity must remain immersed");
            helper.assertTrue(!EchoLava.TYPE.get().canDrownIn(pig) && EchoLava.TYPE.get().canExtinguish(pig),
                    "Echo Lava must not drown entities and must extinguish fire");
            helper.succeed();
        } finally {
            pig.discard();
            level.setChunkForced(chunk.x, chunk.z, false);
        }
    }
    @GameTest(template = "garage_test_empty", timeoutTicks = 2000)
    public static void echoBasinGeneration(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(EchoBasinDimension.KEY);
        helper.assertTrue(level != null, "Echo Basin must load");
        var grassSound = DimensionBlocks.ECHO_GRASS.get().defaultBlockState().getSoundType(level, BlockPos.ZERO, null);
        var stoneSound = DimensionBlocks.ECHO_STONE.get().defaultBlockState().getSoundType(level, BlockPos.ZERO, null);
        helper.assertTrue(grassSound.getStepSound() == net.minecraft.sounds.SoundEvents.NYLIUM_STEP,
                "Echo Grass must retain its original footstep sound");
        helper.assertTrue(stoneSound.getBreakSound() == EchoSounds.STONE_BREAK
                        && grassSound.getBreakSound() == EchoSounds.GRASS_BREAK,
                "Echo materials must use their registered custom sounds");
        helper.assertTrue(EchoLava.SOURCE.get().getPickupSound().orElseThrow() == EchoSounds.LAVA_FILL
                        && EchoLava.TYPE.get().getSound(net.neoforged.neoforge.common.SoundActions.BUCKET_EMPTY) == EchoSounds.LAVA_EMPTY,
                "Echo Lava bucket sounds must use custom events");
        helper.assertTrue(level.dimensionType().hasCeiling() && !level.dimensionType().hasSkyLight(),
                "Echo Basin must be a roofed cavern without skylight");
        helper.assertTrue(level.dimensionType().effectsLocation().equals(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "echo_basin")),
                "Echo Basin must use its own blue/violet visual effects");
        int stone = 0, ore = 0, air = 0, grass = 0, fluid = 0;
        for (int cx = 15000; cx < 15003; cx++) for (int cz = 15000; cz < 15003; cz++) {
            var chunk = level.getChunk(cx, cz);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                BlockPos column = new BlockPos(cx * 16 + x, 0, cz * 16 + z);
                helper.assertTrue(chunk.getBlockState(column).is(Blocks.BEDROCK), "Bedrock must seal the floor");
                helper.assertTrue(chunk.getBlockState(column.atY(127)).is(Blocks.BEDROCK), "Bedrock must seal the roof");
                for (int y = 1; y < 127; y++) {
                    var state = chunk.getBlockState(column.atY(y));
                    if (state.is(DimensionBlocks.ECHO_STONE)) stone++;
                    if (state.is(DimensionBlocks.ECHO_GRASS)) {
                        grass++;
                        var cover = chunk.getBlockState(column.atY(y + 1));
                        helper.assertTrue(y >= 32 && (cover.isAir() || cover.is(DimensionBlocks.ECHO_STONE_BRICKS)),
                                "Echo Grass must cover dry floors or lie below a well foundation at "
                                        + column.atY(y) + "; cover=" + cover);
                    }
                    if (state.is(DimensionBlocks.RESONANT_ORE)) ore++;
                    if (state.isAir()) air++;
                    if (state.getFluidState().getType().isSame(EchoLava.SOURCE.get())) fluid++;
                    helper.assertTrue(!state.is(Blocks.LAVA), "Echo Basin must generate Echo Lava rather than vanilla lava");
                    helper.assertTrue(!state.is(Blocks.NETHERRACK), "Nether terrain must be replaced by Echo Stone");
                }
            }
        }
        helper.assertTrue(stone > 0 && ore > 0 && air > 0, "Caverns must contain Echo Stone, Resonant Ore, and open air");
        helper.assertTrue(grass > 0, "Cavern floors must generate Echo Grass");
        // A particular 3x3 patch can be solid below sea level. Sample separated areas
        // rather than assuming every random world seed puts a pool at those coordinates.
        for (int attempt = 0; attempt < 64 && fluid == 0; attempt++) {
            var sample = level.getChunk(15100 + attempt * 8, 15100 - attempt * 8);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 5; y < 32; y++) {
                var state = sample.getBlockState(new BlockPos(sample.getPos().getMinBlockX() + x, y,
                        sample.getPos().getMinBlockZ() + z));
                if (state.getFluidState().getType().isSame(EchoLava.SOURCE.get())) fluid++;
                helper.assertTrue(!state.is(Blocks.LAVA), "Molten pools must never generate vanilla lava");
            }
        }
        helper.assertTrue(fluid > 0, "Caverns must contain Echo Lava pools");
        helper.assertTrue(EchoLava.SOURCE.get().getBucket() == EchoLava.BUCKET.get()
                        && EchoLava.FLOWING.get().isSame(EchoLava.SOURCE.get())
                        && EchoLava.SOURCE.get().getFluidType() == EchoLava.TYPE.get(),
                "Custom source, flowing fluid, type, and bucket must be linked");
        helper.assertTrue(EchoLava.SOURCE.get().defaultFluidState().createLegacyBlock().is(EchoLava.BLOCK.get()),
                "Echo Lava must retain its own block when flowing");
        helper.assertTrue(EchoLava.SOURCE.get().defaultFluidState().is(net.minecraft.tags.FluidTags.LAVA),
                "Echo Lava must retain lava contact and pathfinding behavior");
        var drops = net.minecraft.world.level.block.Block.getDrops(
                DimensionBlocks.RESONANT_ORE.get().defaultBlockState(), level,
                new BlockPos(240000, 64, 240000), null, null,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(DimensionBlocks.RESONANCE_SHARD.get()),
                "Resonant Ore must drop the custom Resonance Shard");
        helper.assertTrue(DimensionBlocks.RESONANT_ORE.get().defaultBlockState().requiresCorrectToolForDrops(),
                "Ore must require the correct mining tool");
        var source = level.getServer().overworld();
        var stair = new StaircaseData.Staircase(new BlockPos(240016, 80, 240016),
                net.minecraft.world.level.block.Rotation.NONE, EchoBasinDimension.DESTINATION);
        var tread = Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState();
        source.setBlock(stair.position(2, 0, 2), Blocks.SMOOTH_QUARTZ.defaultBlockState(), 2);
        for (int step = 0; step < 8; step++) {
            source.setBlock(stair.position(2, step + 1, step + 2), tread, 2);
            if (step > 0) source.setBlock(stair.position(2, step, step + 2), tread.setValue(
                    net.minecraft.world.level.block.StairBlock.HALF,
                    net.minecraft.world.level.block.state.properties.Half.TOP), 2);
        }
        // A controlled cavern below the real generated roof exercises roof-aware arrival.
        for (int x = 240016; x <= 240020; x++) for (int z = 240016; z <= 240027; z++) {
            for (int y = 53; y <= 112; y++) level.setBlock(new BlockPos(x, y, z),
                    y == 64 ? DimensionBlocks.ECHO_GRASS.get().defaultBlockState()
                            : y < 64 ? DimensionBlocks.ECHO_STONE.get().defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        var entrance = StaircaseArrival.prepare(source, level, stair);
        helper.assertTrue(entrance != null && entrance.origin.getY() == 56,
                "Echo Basin arrival must use the cavern floor, not the bedrock roof");
        helper.assertTrue(level.getBlockState(new BlockPos(240018, 127, 240025)).is(Blocks.BEDROCK),
                "Arrival must leave the roof intact");
        helper.assertTrue(StaircaseArrival.prepare(source, level, stair) != null,
                "Repeat arrivals must reuse the protected entrance");
        helper.succeed();
    }
    @GameTest(template = "garage_test_empty", timeoutTicks = 200)
    public static void futureClock(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var level = server.getLevel(FutureDimension.KEY);
        var overworld = server.overworld();
        var mirror = server.getLevel(MirrorDimension.KEY);
        helper.assertTrue(level.getLevelData() instanceof FutureClock.Data, "Future world must install independent solar data");
        long overworldTime = overworld.getDayTime(), mirrorTime = mirror.getDayTime(), gameTime = level.getGameTime();
        float overworldRate = overworld.getDayTimePerTick(), fraction = overworld.getDayTimeFraction();
        long original = level.getDayTime();
        boolean daylight = level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT);
        try {
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(true, server);
            level.setDayTime(24000);
            var tick = new net.neoforged.neoforge.event.tick.LevelTickEvent.Pre(() -> true, level);
            for (int i = 0; i < 599; i++) FutureClock.tick(tick);
            helper.assertTrue(level.getDayTime() == 47960 && level.getDayTimePerTick() == 40, "Fast day must advance at 40x");
            FutureClock.tick(tick);
            helper.assertTrue(level.getDayTime() == 48000 && level.getDayTimePerTick() == 1, "600 ticks must complete fast day and restore 1x");
            level.setDayTimeFraction(0.25f);
            helper.assertTrue(overworld.getDayTime() == overworldTime && mirror.getDayTime() == mirrorTime
                    && overworld.getDayTimePerTick() == overworldRate && overworld.getDayTimeFraction() == fraction,
                    "Solar time, speed and fraction must not leak into other dimensions");
            helper.assertTrue(level.getGameTime() == gameTime, "Solar acceleration must not advance simulation time");
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
            FutureClock.tick(tick);
            helper.assertTrue(level.getDayTime() == 48000, "Daylight gamerule must freeze the local clock");
            var saved = level.getDataStorage().get(new net.minecraft.world.level.saveddata.SavedData.Factory<>(FutureClock::new, FutureClock::load),
                    Stairs.MODID + "_future_clock");
            helper.assertTrue(saved != null, "Clock must be attached to dimension save storage");
            var restored = FutureClock.load(saved.save(new net.minecraft.nbt.CompoundTag(), server.registryAccess()), server.registryAccess());
            helper.assertTrue(restored.save(new net.minecraft.nbt.CompoundTag(), server.registryAccess()).getLong("DayTime") == 48000,
                    "Clock phase must survive save/reload");
        } finally {
            level.setDayTime(original);
            level.setDayTimeFraction(0);
            level.setDayTimePerTick(FutureClock.speed(original));
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(daylight, server);
        }
        helper.succeed();
    }
    @GameTest(template = "garage_test_empty", timeoutTicks = 200)
    public static void futureGeneration(GameTestHelper helper) {
        var level = helper.getLevel().getServer().getLevel(FutureDimension.KEY);
        helper.assertTrue(level != null, "Future dimension must load from its datapack");
        helper.assertTrue(level.dimensionType().hasSkyLight(), "Future world must have daylight");
        int cavities = 0;
        for (BlockPos origin : new BlockPos[]{BlockPos.ZERO, new BlockPos(-2256, 100, 912), new BlockPos(700, 64, -900)}) {
            var column = level.getChunkSource().getGenerator().getBaseColumn(origin.getX(), origin.getZ(), level,
                    level.getChunkSource().randomState());
            helper.assertTrue(column.getBlock(-64).is(FutureBlocks.BEDROCK_PLUS), "Bottom layer must be Bedrock+");
            for (int y = 54; y <= 63; y++) helper.assertTrue(column.getBlock(y).is(Blocks.GRAVEL), "Ten layers must be gravel");
            helper.assertTrue(column.getBlock(64).is(FutureBlocks.TURF), "Top layer must be Turf at normal height");
            helper.assertTrue(column.getBlock(65).isAir(), "Above terrain must be air");
            var chunk = level.getChunkAt(origin);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int wx = chunk.getPos().getMinBlockX() + x, wz = chunk.getPos().getMinBlockZ() + z;
                helper.assertTrue(chunk.getBlockState(new BlockPos(wx, -64, wz)).is(FutureBlocks.BEDROCK_PLUS), "Carvers must preserve bedrock");
                helper.assertTrue(chunk.getBlockState(new BlockPos(wx, 64, wz)).is(FutureBlocks.TURF), "Generated surface must stay flat");
                for (int y = -50; y < 40; y++) {
                    var state = chunk.getBlockState(new BlockPos(wx, y, wz));
                    if (state.isAir() || !state.getFluidState().isEmpty()) cavities++;
                }
            }
            BlockPos feet = GarageTravel.findFutureArrival(level, GarageTravel.arrival(origin));
            helper.assertTrue(feet != null && StairsCommands.isSafeLanding(level, feet), "Arrival must have safe footing and headroom");
        }
        helper.assertTrue(cavities > 100, "Underground must contain actual caves/aquifers, not solid flat layers");
        helper.succeed();
    }
    @GameTest(template = "garage_test_empty", timeoutTicks = 2000)
    public static void futureArrivals(GameTestHelper helper) {
        var source = helper.getLevel().getServer().overworld();
        var target = helper.getLevel().getServer().getLevel(FutureDimension.KEY);
        int index = 0;
        for (var rotation : net.minecraft.world.level.block.Rotation.values()) {
            var stair = new StaircaseData.Staircase(new BlockPos(-4000 - 64 * index++, 80, -4000), rotation, 1);
            var bottom = Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING, rotation.rotate(net.minecraft.core.Direction.SOUTH));
            source.setBlock(stair.position(2, 0, 2), Blocks.SMOOTH_QUARTZ.defaultBlockState(), 2);
            for (int step = 0; step < 8; step++) {
                source.setBlock(stair.position(2, step + 1, step + 2), bottom, 2);
                if (step > 0) source.setBlock(stair.position(2, step, step + 2), bottom
                        .setValue(net.minecraft.world.level.block.StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP), 2);
            }
            var arrived = StaircaseArrival.prepare(source, target, stair);
            helper.assertTrue(arrived != null, "Arrival must succeed in all four rotations");
            helper.assertTrue(arrived.position(2, 8, 9).getY() == 64, "Top tread must be flush with Turf");
            for (int step = 0; step < 8; step++) {
                helper.assertTrue(target.getBlockState(arrived.position(2, step + 1, step + 2)).equals(bottom), "Every tread must retain its facing and shape");
            }
            var sideways = switch (rotation) {
                case NONE -> new net.minecraft.world.phys.Vec3(-0.37, 1, 0);
                case CLOCKWISE_90 -> new net.minecraft.world.phys.Vec3(0, 1, -0.37);
                case CLOCKWISE_180 -> new net.minecraft.world.phys.Vec3(0.37, 1, 0);
                case COUNTERCLOCKWISE_90 -> new net.minecraft.world.phys.Vec3(0, 1, 0.37);
            };
            var feet = net.minecraft.world.phys.Vec3.atBottomCenterOf(stair.position(2, 7, 8)).add(sideways);
            helper.assertTrue(source.noCollision(new net.minecraft.world.phys.AABB(feet.x - 0.3, feet.y, feet.z - 0.3,
                    feet.x + 0.3, feet.y + 1.8, feet.z + 0.3)), "Test footing must be valid at the source");
            var moved = StaircaseArrival.translate(feet, stair.origin, arrived.origin);
            helper.assertTrue(target.noCollision(new net.minecraft.world.phys.AABB(moved.x - 0.3, moved.y, moved.z - 0.3,
                    moved.x + 0.3, moved.y + 1.8, moved.z + 0.3)), "Edge-of-tread arrivals must have headroom");
            var repeated = StaircaseArrival.prepare(source, target, stair);
            helper.assertTrue(repeated != null && repeated.origin.equals(arrived.origin), "Repeated arrival must not sink the staircase further");
            var reloaded = StaircaseData.load(StaircaseData.get(target).save(new net.minecraft.nbt.CompoundTag(), target.registryAccess()), target.registryAccess());
            helper.assertTrue(stair.origin.equals(reloaded.stairs.get(arrived.origin).returnOrigin), "Entrance link must survive a save/reload");

            BlockPos chest = arrived.position(1, 10, 2);
            target.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
            var container = (net.minecraft.world.level.block.entity.ChestBlockEntity) target.getBlockEntity(chest);
            container.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 7));
            helper.assertTrue(StaircaseArrival.prepare(source, target, stair) == null, "Obstructed arrivals must abort");
            helper.assertTrue(container.getItem(0).getCount() == 7 && target.getBlockState(arrived.position(2, 8, 9)).equals(bottom),
                    "Failed placement must leave the inventory and entrance untouched");
        }
        helper.succeed();
    }

    @GameTest(template = "garage_test_empty", timeoutTicks = 2000)
    public static void futureVillage(GameTestHelper helper) throws ReflectiveOperationException {
        var level = helper.getLevel().getServer().getLevel(FutureDimension.KEY);
        // Vanilla's isolated GameTestServer hardcodes structure generation off.
        // Enable it only in that test harness, never in a player's regular server.
        if (level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer) {
            var flag = net.minecraft.world.level.levelgen.WorldOptions.class.getDeclaredField("generateStructures");
            flag.setAccessible(true);
            flag.setBoolean(level.getServer().getWorldData().worldGenOptions(), true);
        }
        var registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        var holder = registry.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.STRUCTURE, net.minecraft.resources.ResourceLocation.withDefaultNamespace("village_plains")));
        var found = level.getChunkSource().getGenerator().findNearestMapStructure(level,
                net.minecraft.core.HolderSet.direct(holder), new BlockPos(10000, 0, 10000), 32, false);
        helper.assertTrue(found != null, "Plains villages must generate naturally");
        BlockPos center = found.getFirst();
        int plastic = 0;
        for (int cx = (center.getX() >> 4) - 2; cx <= (center.getX() >> 4) + 2; cx++) {
            for (int cz = (center.getZ() >> 4) - 2; cz <= (center.getZ() >> 4) + 2; cz++) {
                var chunk = level.getChunk(cx, cz);
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 64; y <= 97; y++) {
                    var state = chunk.getBlockState(new BlockPos(cx * 16 + x, y, cz * 16 + z));
                    helper.assertTrue(FutureDimension.material(state).equals(state), "Village materials must be replaced during worldgen");
                    if (state.is(PlasticBlocks.PLANKS.get()) || state.is(PlasticBlocks.COBBLESTONE.get())
                            || state.is(PlasticBlocks.LOG.get()) || state.is(PlasticBlocks.MOSSY_COBBLESTONE.get())) plastic++;
                }
            }
        }
        helper.assertTrue(plastic > 0, "Generated village must contain plastic building materials");
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.WEST);
        helper.assertTrue(FutureDimension.material(door).getValue(net.minecraft.world.level.block.DoorBlock.FACING)
                == net.minecraft.core.Direction.WEST, "Replacement must preserve facing");
        helper.assertTrue(FutureDimension.material(Blocks.FARMLAND.defaultBlockState()).is(Blocks.BROWN_WOOL),
                "Future village farmland must become brown wool");
        helper.assertTrue(FutureDimension.material(Blocks.WHEAT.defaultBlockState()).is(PlasticBlocks.FAKE_WHEAT.get())
                        && FutureDimension.material(Blocks.CARROTS.defaultBlockState()).is(PlasticBlocks.FAKE_WHEAT.get())
                        && FutureDimension.material(Blocks.POTATOES.defaultBlockState()).is(PlasticBlocks.FAKE_WHEAT.get())
                        && FutureDimension.material(Blocks.BEETROOTS.defaultBlockState()).is(PlasticBlocks.FAKE_WHEAT.get()),
                "Every vanilla village crop must become fake wheat");
        var hay = Blocks.HAY_BLOCK.defaultBlockState().setValue(
                net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X);
        helper.assertTrue(FutureDimension.material(hay).is(PlasticBlocks.HAY_BALE.get())
                        && FutureDimension.material(hay).getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)
                        == net.minecraft.core.Direction.Axis.X,
                "Village hay bales must become plastic while preserving their axis");
        helper.succeed();
    }
    @GameTest(template = "garage_test_empty", timeoutTicks = 200)
    public static void mirrorGeneration(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var mirror = server.getLevel(MirrorDimension.KEY);
        var overworld = server.overworld();
        helper.assertTrue(mirror != null, "Mirror dimension must load");
        helper.assertTrue(mirror.getSeed() == overworld.getSeed(), "Mirror must use the Overworld seed");
        helper.assertTrue(mirror.dimensionType().hasFixedTime(), "Mirror must have permanent nighttime");
        helper.assertTrue(mirror.dimensionType().timeOfDay(0) == mirror.dimensionType().timeOfDay(12000), "Overworld time must not move the mirror sky");
        helper.assertTrue(mirror.dimensionType().hasSkyLight() && !mirror.dimensionType().hasCeiling(), "Mirror must retain an open Overworld sky");
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess());
        var codec = net.minecraft.world.level.chunk.ChunkGenerator.CODEC;
        helper.assertTrue(codec.encodeStart(ops, mirror.getChunkSource().getGenerator()).getOrThrow()
                .equals(codec.encodeStart(ops, overworld.getChunkSource().getGenerator()).getOrThrow()),
                "Mirror must clone actual generator settings, including flat presets");
        helper.assertTrue(mirror.getChunkSource().getGenerator() != overworld.getChunkSource().getGenerator(),
                "Generator instances must be independent");
        for (int x : new int[]{1600, -1600}) {
            overworld.getChunk(x >> 4, 100);
            mirror.getChunk(x >> 4, 100);
            for (int y = overworld.getMinBuildHeight(); y < overworld.getMaxBuildHeight(); y++) {
                BlockPos pos = new BlockPos(x, y, 1600);
                helper.assertTrue(overworld.getBlockState(pos).equals(mirror.getBlockState(pos)), "Generated columns must match");
            }
        }
        var origin = new BlockPos(1600, 80, 1600);
        var stair = new StaircaseData.Staircase(origin, net.minecraft.world.level.block.Rotation.CLOCKWISE_90,
                MirrorDimension.DESTINATION);
        for (int step = 0; step < 8; step++) {
            BlockPos tread = stair.position(2, step + 1, step + 2);
            overworld.setBlock(tread, Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState(), 3);
        }
        // This harness's Overworld is only four blocks deep; provide ground for a buried entrance.
        for (int x = 1588; x <= 1612; x++) for (int z = 1588; z <= 1612; z++) for (int y = 53; y <= 64; y++) {
            mirror.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 2);
        }
        var arrivalStair = StaircaseArrival.prepare(overworld, mirror, stair);
        helper.assertTrue(arrivalStair != null && arrivalStair.origin.getY() == 56, "Mirror entrance must be sunk into the ground");
        for (int step = 0; step < 8; step++) {
            BlockPos tread = stair.position(2, step + 1, step + 2);
            helper.assertTrue(overworld.getBlockState(tread).equals(mirror.getBlockState(arrivalStair.position(2, step + 1, step + 2))), "Rotated arrival stair must match");
        }
        helper.assertTrue(StaircaseData.get(mirror).stairs.get(arrivalStair.origin).state == StaircaseData.State.ACTIVATED,
                "Mirror entrance must be permanent and protected");
        helper.assertTrue(origin.equals(arrivalStair.returnOrigin), "Mirror entrance must retain its original return link");
        helper.succeed();
    }
    @GameTest(template = "garage_test_empty", batch = "snapshot", timeoutTicks = 100000)
    public static void mirrorSnapshot(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var overworld = server.overworld();
        var oldMirror = server.getLevel(MirrorDimension.KEY);
        BlockPos build = new BlockPos(1610, 72, 1610);
        BlockPos chest = build.east();
        overworld.setBlock(build, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        overworld.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        ((net.minecraft.world.level.block.entity.ChestBlockEntity) overworld.getBlockEntity(chest))
                .setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 7));
        oldMirror.setBlock(build, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        helper.assertTrue(MirrorSnapshots.request(server.createCommandSourceStack()) == 1, "Snapshot command must queue");
        helper.succeedWhen(() -> {
            var mirror = server.getLevel(MirrorDimension.KEY);
            helper.assertTrue(mirror != null && mirror != oldMirror && !MirrorSnapshots.busy(server), "Live replacement must complete");
            mirror.getChunkAt(build);
            helper.assertTrue(mirror.getBlockState(build).is(Blocks.DIAMOND_BLOCK), "Player build must be copied");
            var chunk = mirror.getChunkAt(chest);
            helper.assertTrue(chunk.getBlockEntities().containsKey(chest), "Chest must exist before any lazy server lookup");
            var packet = new net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData(chunk);
            boolean[] sentChest = {false};
            packet.getBlockEntitiesTagsConsumer(chunk.getPos().x, chunk.getPos().z).accept((pos, type, tag) -> {
                if (pos.equals(chest) && type == net.minecraft.world.level.block.entity.BlockEntityType.CHEST) sentChest[0] = true;
            });
            helper.assertTrue(sentChest[0], "Chunk packet must include the chest renderer's block entity");
            var copiedChest = (net.minecraft.world.level.block.entity.ChestBlockEntity) mirror.getBlockEntity(chest);
            helper.assertTrue(copiedChest != null && copiedChest.isEmpty(), "Container blocks must be copied with empty inventories");
            overworld.setBlock(build, Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
            helper.assertTrue(mirror.getBlockState(build).is(Blocks.DIAMOND_BLOCK), "Later Overworld changes must not leak into mirror");
        });
    }
}
