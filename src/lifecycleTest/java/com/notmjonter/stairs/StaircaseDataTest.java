package com.notmjonter.stairs;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import com.notmjonter.stairs.StaircaseData.State;

/** Dependency-free regression checks run by Gradle check. */
public final class StaircaseDataTest {
    public static void main(String[] args) throws Exception {
        EchoVoiceArchiveTest.run();
        MirrorSnapshotTest.run();
        FutureReplayTest.run();
        require(EchoVoiceAudio.REPEATS == 4 && EchoVoiceAudio.gain(1) == 0.30f
                        && EchoVoiceAudio.gain(2) == 0.15f && EchoVoiceAudio.gain(4) == 0.0375f,
                "Voice must have four fading echoes after the original");
        short[] voiceFrame = {Short.MIN_VALUE, -1000, 0, 1000, Short.MAX_VALUE};
        short[] quietVoice = EchoVoiceAudio.scaled(voiceFrame, 0.3f);
        require(quietVoice[1] == -300 && quietVoice[3] == 300 && quietVoice[0] < 0
                        && voiceFrame[1] == -1000,
                "Voice echoes must attenuate PCM without modifying the original microphone buffer");
        require(EchoVoiceAudio.scaled(voiceFrame, Float.NaN)[4] == 0,
                "Invalid voice echo gain must not corrupt playback");
        var voiceOrigin = new Vec3(10, 20, -5);
        require(EchoVoiceAudio.position(voiceOrigin, new Vec3(0, 0, -2), 1).equals(new Vec3(10, 20, -11))
                        && EchoVoiceAudio.position(voiceOrigin, new Vec3(0, 0, -2), 4).equals(new Vec3(10, 20, -29)),
                "Voice repeats must travel 6/12/18/24 blocks along normalized initial facing");
        short[] voiceImpulse = new short[12000];
        voiceImpulse[0] = 16000;
        short[] reverberant = new EchoVoiceAudio.Processor().process(voiceImpulse, 0.3f);
        require(java.util.stream.IntStream.range(2000, reverberant.length).anyMatch(i -> reverberant[i] != 0)
                        && voiceImpulse[0] == 16000 && voiceImpulse[2000] == 0,
                "Echo reverb must produce a tail without modifying recorded PCM");
        var splitProcessor = new EchoVoiceAudio.Processor();
        short[] firstProcessed = splitProcessor.process(java.util.Arrays.copyOfRange(voiceImpulse, 0, 960), 0.3f);
        short[] secondProcessed = splitProcessor.process(java.util.Arrays.copyOfRange(voiceImpulse, 960, 1920), 0.3f);
        short[] wholeProcessed = new EchoVoiceAudio.Processor().process(java.util.Arrays.copyOf(voiceImpulse, 1920), 0.3f);
        require(java.util.Arrays.equals(firstProcessed, java.util.Arrays.copyOfRange(wholeProcessed, 0, 960))
                        && java.util.Arrays.equals(secondProcessed, java.util.Arrays.copyOfRange(wholeProcessed, 960, 1920)),
                "Voice DSP state must remain continuous across incoming audio frames");
        short[] constantVoice = new short[48000];
        java.util.Arrays.fill(constantVoice, (short) 10000);
        short[] filteredVoice = new EchoVoiceAudio.Processor().process(constantVoice, 1);
        require(java.util.stream.IntStream.range(46000, 48000).allMatch(i -> Math.abs(filteredVoice[i]) < 500),
                "Low cut must reject sustained DC/bass rather than amplifying it");
        require(EchoReplayConfig.delayTicks(1) == 20 && EchoReplayConfig.delayTicks(0) == 0
                        && EchoReplayConfig.delayTicks(0.5) == 10 && EchoReplayConfig.delayTicks(60) == 1200,
                "Echo delay must support one second, instant replay, and adjustable client-tick timing");
        require(EchoReplayConfig.delayTicks(Double.NaN) == 20 && EchoReplayConfig.delayTicks(-1) == 0,
                "Invalid echo delays must be safely bounded");
        require(EchoReplayConfig.REPLAY_VOLUME == 0.30f, "Delayed echo must play at 30 percent volume");
        require(EchoReplayConfig.validPattern("minecraft:ui.*")
                        && EchoReplayConfig.validPattern("*:echo_basin/*")
                        && !EchoReplayConfig.validPattern("missing_namespace")
                        && !EchoReplayConfig.validPattern("minecraft:has spaces"),
                "Echo blacklist patterns must be valid namespaced IDs or wildcards");
        require(EchoReplayConfig.blacklisted(java.util.List.of("minecraft:ui.*"), "minecraft:ui.button.click", null)
                        && !EchoReplayConfig.blacklisted(java.util.List.of("minecraft:ui.*"), "minecraft:block.stone.break", null),
                "Echo blacklist wildcard matching must not affect unrelated sounds");
        require(EchoReplayConfig.blacklisted(java.util.List.of("mjontersstairs:echo_basin/echo_stone/*"),
                        "mjontersstairs:echo_basin.stone.break", "mjontersstairs:echo_basin/echo_stone/break_01")
                        && !EchoReplayConfig.blacklisted(java.util.List.of(), "minecraft:music.menu", null),
                "Echo blacklist must support audio asset IDs and default to all sounds");
        require(!FutureMobSpawns.farEnough(143.999) && FutureMobSpawns.farEnough(144),
                "Bed-spawned villagers must remain at least twelve blocks from every player");
        require(TickstormTerrain.flattenedSurface(63) == 63
                        && TickstormTerrain.flattenedSurface(103) == 73
                        && TickstormTerrain.flattenedSurface(23) == 53,
                "Tickstorm terrain must preserve sea level while compressing relief to one quarter");
        Set<Integer> fragmentHeights = new HashSet<>();
        Set<Integer> fragmentTypes = new HashSet<>();
        boolean foundMissingFragment = false;
        for (long sample = -128; sample < 128; sample++) {
            long seed = GarbageCollectorTerrain.mix(sample);
            require(GarbageCollectorTerrain.empty(seed, 0, 8) && GarbageCollectorTerrain.empty(seed, 8, 0),
                    "Garbage Collector seams must stay empty for every fragment");
            foundMissingFragment |= GarbageCollectorTerrain.missing(seed);
            fragmentTypes.add(GarbageCollectorTerrain.kind(seed));
            for (int x = 1; x < 16; x++) for (int z = 1; z < 16; z++) {
                int height = GarbageCollectorTerrain.surface(seed, x, z);
                require(height >= 21 && height <= 123, "Fragment surfaces must stay within generation bounds");
                fragmentHeights.add(height);
            }
        }
        require(foundMissingFragment && fragmentTypes.size() == 5 && !fragmentTypes.contains(5)
                        && fragmentHeights.size() > 10,
                "Garbage Collector must include void cells and five dry terrain styles with varied heights");
        require(TickstormWeather.CAPTURE_TICKS == 300
                        && TickstormWeather.BURST_TICKS == 60
                        && TickstormWeather.PLAYBACK_RATE == 5,
                "Tickstorm must continuously capture fifteen seconds and replay at five times speed over three seconds");
        require(!FutureVillagerFollow.followTime(5999) && FutureVillagerFollow.followTime(6000)
                        && FutureVillagerFollow.followTime(12999) && !FutureVillagerFollow.followTime(13000)
                        && FutureVillagerFollow.followTime(30000),
                "Villager following must run from noon until nightfall and repeat each solar day");
        FutureContainmentState.begin(0);
        require(!FutureContainmentState.showText() && FutureContainmentState.scale(1) == 1,
                "Containment must begin with immediate growth but no early sky text");
        for (int tick = 0; tick < 59; tick++) FutureContainmentState.tick();
        require(!FutureContainmentState.showText(), "Containment text must not appear before three seconds");
        FutureContainmentState.tick();
        require(FutureContainmentState.showText(), "Containment text must appear at three seconds");
        require(FutureContainmentState.skyCoverage(1) == 0,
                "Containment must retain its initial orb shape before the six-second sky expansion");
        require(FutureContainmentState.distortionAt(120) == 0
                        && FutureContainmentState.distortionAt(160) > 0
                        && FutureContainmentState.distortionAt(200) == 1,
                "Containment distortion must grow from six to ten seconds");
        for (int tick = 60; tick < 200; tick++) FutureContainmentState.tick();
        require(FutureContainmentState.blackout(1) == 1 && !FutureContainmentState.showText()
                        && FutureContainmentState.soundActive(),
                "Containment must black out at ten seconds while its sound continues");
        require(FutureContainmentState.skyCoverage(1) == 1,
                "Containment sky cap must cover every direction at ten seconds");
        FutureContainmentState.cover();
        for (int tick = 200; tick < 260; tick++) FutureContainmentState.tick();
        require(!FutureContainmentState.soundActive() && FutureContainmentState.blackout(1) == 1,
                "Containment must retain black through the thirteen-second sound ending");
        for (int tick = 260; tick < 300; tick++) FutureContainmentState.tick();
        require(!FutureContainmentState.active() && FutureContainmentState.blackout(1) == 0,
                "Containment cover must release completely after arrival");
        FutureOrbReactionState.begin();
        require(FutureOrbReactionState.value(1) == 0, "Warning must start without a scale discontinuity");
        for (int tick = 0; tick < FutureOrbReactionState.GROW_TICKS; tick++) FutureOrbReactionState.tick(true);
        require(FutureOrbReactionState.scale(1) == 3, "Orb warning must reach 3x size quickly");
        for (int tick = 0; tick < FutureOrbReactionState.HOLD_TICKS; tick++) {
            FutureOrbReactionState.tick(true);
            require(FutureOrbReactionState.scale(1) == 3, "Warning must hold at full scale for one second");
        }
        for (int tick = 0; tick < FutureOrbReactionState.RETURN_TICKS; tick++) FutureOrbReactionState.tick(true);
        require(FutureOrbReactionState.scale(1) == 1 && FutureOrbReactionState.value(1) == 0,
                "Orb warning must return completely to normal after two seconds of decay");
        FutureOrbReactionState.begin();
        FutureOrbReactionState.tick(true);
        FutureOrbReactionState.tick(false);
        require(FutureOrbReactionState.value(1) == 0, "Warning effects must clear on dimension exit");
        long solarTime = 0;
        for (int tick = 0; tick < 24000; tick++) solarTime = FutureClock.next(solarTime);
        require(solarTime == 24000 && FutureClock.speed(solarTime) == 40, "First cycle must last 20 minutes");
        for (int tick = 0; tick < 600; tick++) solarTime = FutureClock.next(solarTime);
        require(solarTime == 48000 && FutureClock.speed(solarTime) == 1, "Second cycle must last exactly 30 seconds");
        require(FutureClock.next(solarTime) == 48001, "Fast cycle must return to normal speed");
        require(FutureClock.next(47999) == 48000, "Fast cycle must not overshoot sunrise after a time command");
        require(FutureOrbAppearance.radius(6000) == 15, "Noon orb must be at 1x");
        require(FutureOrbAppearance.radius(17500) == 60, "11:30 PM orb must be 50% of maximum size");
        require(FutureOrbAppearance.radius(18000) == 120, "Midnight orb must be at 8x");
        require(FutureOrbAppearance.radius(30000) == 15, "Next noon must restore 1x");
        require(FutureClock.crossedMidnight(17999, 18000),
                "Midnight distortion must trigger on the exact boundary");
        require(FutureClock.crossedMidnight(17960, 18000),
                "Accelerated solar steps must not skip the midnight distortion");
        require(!FutureClock.crossedMidnight(18000, 18040)
                        && !FutureClock.crossedMidnight(19000, 18000),
                "The midnight distortion must not repeat or trigger on backward time changes");
        require(FutureFinaleState.scaleAt(0) == 1
                        && FutureFinaleState.scaleAt(FutureFinaleState.GROW_TICKS) == 100,
                "The fifth-midnight orb must grow from 1x to exactly 100x");
        float previousFinaleScale = 1;
        for (int tick = 1; tick <= FutureFinaleState.GROW_TICKS; tick++) {
            float finaleScale = FutureFinaleState.scaleAt(tick);
            require(finaleScale >= previousFinaleScale && finaleScale <= 100,
                    "The fifth-midnight orb expansion must remain smooth and monotonic");
            previousFinaleScale = finaleScale;
        }
        require(FutureFinale.ascentSpeed(0) > 0
                        && FutureFinale.ascentSpeed(FutureFinale.DURATION) > FutureFinale.ascentSpeed(0),
                "The fifth-midnight ascent must lift and accelerate the player");
        require(FutureOrbAudio.isOrbSound(FutureOrbAudio.BOOM),
                "The midnight boom must receive the dedicated orb sound profile");
        require(!FutureVillagerPrayer.prayerTime(17999)
                        && FutureVillagerPrayer.prayerTime(18000)
                        && FutureVillagerPrayer.prayerTime(18999)
                        && !FutureVillagerPrayer.prayerTime(19000),
                "Villager prayer state must last exactly from midnight until 1:00 AM");
        require(!FutureVillagerPrayer.preparationTime(17832)
                        && FutureVillagerPrayer.preparationTime(17833),
                "Villagers must begin preparing at approximately 11:50 PM");
        float previousRadius = 15;
        for (int tick = 6001; tick <= 30000; tick++) {
            float radius = FutureOrbAppearance.radius(tick);
            require(radius >= 15 && radius <= 120 && Math.abs(radius - previousRadius) < 0.2f,
                    "Orb radius must remain bounded and continuous throughout the day");
            require(tick <= 18000 ? radius >= previousRadius : radius <= previousRadius,
                    "Orb must grow toward midnight and shrink toward noon");
            require(Math.abs(radius - FutureOrbAppearance.radius(tick + 24000)) < 0.00001f,
                    "Size cycle must repeat without a sunrise seam");
            previousRadius = radius;
        }
        require(Math.abs(FutureOrbAppearance.opacity(0, 0) - 0.08f) < 0.00001f, "Daytime orb must remain faint");
        require(FutureOrbAppearance.opacity(1, 0) == 1, "Nighttime orb must become opaque");
        require(StaircaseAudio.futureReverbStrength(6000) == 0.65f,
                "Future dimension daytime reverb must be restrained");
        require(StaircaseAudio.futureReverbStrength(13000) == 1
                        && StaircaseAudio.futureReverbStrength(18000) == 1
                        && StaircaseAudio.futureReverbStrength(22999) == 1,
                "Future dimension nighttime reverb must retain its full profile");
        require(StaircaseAudio.futureReverbStrength(12000) > 0.65f
                        && StaircaseAudio.futureReverbStrength(12000) < 1
                        && StaircaseAudio.futureReverbStrength(0) > 0.65f
                        && StaircaseAudio.futureReverbStrength(0) < 1,
                "Future dimension reverb must transition smoothly at dusk and dawn");
        float previousDusk = 0;
        for (int i = 0; i <= 2000; i++) {
            double elevation = 1 - i / 1000.0;
            float dusk = FutureOrbAppearance.dusk(elevation);
            require(dusk >= previousDusk && dusk - previousDusk < 0.002, "Dusk fade must be smooth and monotonic");
            require(Math.abs(dusk + FutureOrbAppearance.dusk(-elevation) - 1) < 0.00001, "Dawn must reverse the same curve");
            previousDusk = dusk;
        }
        StaircaseData data = new StaircaseData();
        BlockPos origin = new BlockPos(-123, 64, 456);
        var stair = data.register(origin, Rotation.CLOCKWISE_90, 42);
        require(stair.state == State.UNDISCOVERED, "New stairs must be undiscovered");
        require(!stair.expire(false), "Unseen stairs must survive departure");
        require(stair.encounter(), "Approach must encounter stairs");
        require(!stair.expire(true), "One remaining player must prevent expiration");
        stair.ascents.add(UUID.randomUUID());
        require(stair.activate(), "Ascent must anchor stairs");
        require(stair.ascents.isEmpty(), "Activation must clear ascent tracking");
        require(!stair.expire(false), "Activated stairs must survive departure");
        require(!stair.activate(), "Activation must happen only once");
        require(data.register(origin, Rotation.NONE, 999) == stair, "Rediscovery must preserve identity");
        require(stair.position(2, 8, 9).equals(origin.offset(-9, 8, 2)), "Clockwise rotation must match template placement");

        var expired = data.register(new BlockPos(22, 70, -48), Rotation.COUNTERCLOCKWISE_90, 42);
        expired.encounter();
        require(expired.expire(false), "Unused stairs must expire after everyone departs");
        require(!expired.activate() && !expired.encounter(), "Expired stairs cannot return");
        expired.removedBlocks = 0x35;
        var restored = StaircaseData.load(data.save(new CompoundTag(), null), null);
        var restoredActive = restored.stairs.get(origin);
        require(restoredActive.state == State.ACTIVATED, "Activation must survive save/load");
        require(restoredActive.destination == stair.destination, "Destination must survive save/load");
        require(restoredActive.rotation == stair.rotation, "Rotation must survive save/load");
        var restoredExpired = restored.stairs.get(expired.origin);
        require(restoredExpired.state == State.EXPIRED && restoredExpired.removedBlocks == 0x35,
                "Expiration and partial cleanup must survive save/load");
        ChunkPos missing = null;
        for (int x = -20; x <= 20 && missing == null; x++) for (int z = -20; z <= 20; z++) {
            ChunkPos candidate = new ChunkPos(x, z);
            if (UnfinishedSunChunks.isMissing(candidate)) { missing = candidate; break; }
        }
        require(missing != null, "Unfinished Sun must deterministically include void chunks");
        BlockPos unsafeArrival = new BlockPos(missing.getMinBlockX() + 5, 80, missing.getMinBlockZ() + 11);
        BlockPos safeArrival = UnfinishedSunDimension.safeArrivalColumn(unsafeArrival);
        require(!UnfinishedSunChunks.isMissing(new ChunkPos(safeArrival))
                        && Math.floorMod(safeArrival.getX(), 16) == 5
                        && Math.floorMod(safeArrival.getZ(), 16) == 11,
                "An entrance over a void chunk must move to nearby terrain without changing local alignment");
        // Regression: the reported second staircase was at Y=64, with the player
        // at Y=80 when switching to spectator. Physical presence must still protect it.
        var second = data.register(new BlockPos(0, 64, 8528), Rotation.NONE, 42);
        require(second.state == State.UNDISCOVERED, "Expiring one staircase must not affect another");
        second.encounter();
        BlockPos foot = second.position(2, 1, 2);
        Vec3 observer = new Vec3(0.5, 80, 8528.5);
        require(!second.expire(StaircaseLifecycle.hasNearbyPlayer(foot, Stream.of(observer))),
                "A nearby observer must prevent disappearance even without an eligible discoverer");
        Vec3 center = Vec3.atCenterOf(foot);
        require(StaircaseLifecycle.hasNearbyPlayer(foot, Stream.of(center.add(96, 0, 0))),
                "Exactly 96 blocks is still nearby");
        require(StaircaseLifecycle.hasNearbyPlayer(foot, Stream.of(center.add(200, 0, 0), observer)),
                "A second nearby observer must prevent disappearance");
        require(!StaircaseLifecycle.hasNearbyPlayer(foot, Stream.empty()), "No players means no nearby observer");
        require(second.expire(StaircaseLifecycle.hasNearbyPlayer(foot, Stream.of(center.add(96.01, 0, 0)))),
                "Actually leaving the radius must still expire unused stairs");

        Set<Integer> destinations = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            BlockPos pos = new BlockPos(i * 17, 64, -i * 31);
            int destination = StaircaseData.destination(42, pos);
            require(destination >= 1 && destination <= StaircaseData.TOTAL_DESTINATIONS, "Destination must be in range");
            require(destination == StaircaseData.destination(42, pos), "Destination must be deterministic");
            destinations.add(destination);
        }
        require(destinations.size() == StaircaseData.TOTAL_DESTINATIONS, "All seven destinations must be reachable");
        for (Rotation rotation : Rotation.values()) {
            var rotated = new StaircaseData.Staircase(origin, rotation, 1);
            Set<BlockPos> blocks = new HashSet<>();
            blocks.add(rotated.position(2, 0, 2));
            for (int step = 0; step < 8; step++) {
                blocks.add(rotated.position(2, step + 1, step + 2));
                if (step > 0) blocks.add(rotated.position(2, step, step + 2));
            }
            for (BlockPos block : blocks) {
                require(StaircaseProtection.contains(rotated, block, 0), "Every template block must be protected");
            }
            BlockPos top = rotated.position(2, 8, 9);
            require(StaircaseProtection.contains(rotated, top.above(3), 3), "Placement buffer must cover upper steps");
            require(!StaircaseProtection.contains(rotated, top.above(4), 3), "Blocks beyond the buffer must be allowed");
            rotated.encounter();
            rotated.activate();
            require(StaircaseProtection.contains(rotated, top, 0), "Activated stairs must stay protected");
            rotated.state = State.EXPIRED;
            require(!StaircaseProtection.contains(rotated, top, 3), "Expired stairs must release protection");
            require(blocks.size() == 16, "Every rotation must have 16 unique cleanup positions");
        }
        BlockPos tread = new BlockPos(0, 64, 0);
        require(StaircaseLifecycle.withinEffectColumn(new Vec3(0.5, 64.4, 0.5), tread),
                "Crossing the lower riser must retain slowness");
        require(StaircaseLifecycle.withinEffectColumn(new Vec3(0.5, 66.2, 0.5), tread),
                "A normal jump above a tread must retain slowness");
        require(StaircaseLifecycle.withinEffectColumn(new Vec3(1.1, 65, 0.5), tread),
                "The edge allowance must bridge stair boundaries");
        for (Vec3 edge : new Vec3[]{new Vec3(-0.35, 65, 0.5), new Vec3(1.35, 65, 0.5),
                new Vec3(0.5, 65, -0.35), new Vec3(0.5, 65, 1.35)}) {
            require(StaircaseLifecycle.withinEffectColumn(edge, tread),
                    "Players overlapping either edge must retain effects for every rotation");
        }
        require(!StaircaseLifecycle.withinEffectColumn(new Vec3(1.5, 65, 0.5), tread),
                "Stepping sideways off the stairs must release slowness");
        require(!StaircaseLifecycle.withinEffectColumn(new Vec3(0.5, 67, 0.5), tread),
                "The effect must not extend indefinitely above stairs");
        require(StaircaseLifecycle.ascentIntensity(1.5f) >= 0.3f,
                "Darkness must be noticeable on the first step");
        require(StaircaseLifecycle.ascentIntensity(7.5f) == 1f,
                "Darkness must peak at the penultimate step");
        require(EchoExitStaircases.descentIntensity(0) == 0f,
                "Echo exit distortion must remain off before descent begins");
        require(EchoExitStaircases.descentIntensity(1) == 0.3f,
                "The first downward Echo exit tread must begin the Overworld distortion envelope");
        require(EchoExitStaircases.descentIntensity(7) == 1f,
                "Echo exit distortion must reach full intensity before return transfer");
        AscentVisualState.reset();
        AscentVisualState.receive(1);
        AscentVisualState.tick();
        float firstFade = AscentVisualState.value(1);
        require(AscentVisualState.fovValue(0) == 0 && AscentVisualState.fovValue(1) > 0
                && AscentVisualState.fovValue(1) < firstFade, "FOV must ease in slower than darkness");
        require(firstFade > 0 && firstFade < 1, "Darkness must ease in instead of jumping");
        require(AscentVisualState.value(0) == 0, "Interpolation must start at the previous tick");
        for (int i = 0; i < 40; i++) {
            AscentVisualState.receive(1);
            AscentVisualState.tick();
        }
        require(AscentVisualState.value(1) > 0.99f, "Sustained ascent must reach full intensity");
        AscentVisualState.receive(0);
        AscentVisualState.tick();
        require(AscentVisualState.value(1) > 0 && AscentVisualState.value(1) < 0.8f,
                "Backing off must fade smoothly");
        for (int i = 0; i < 50; i++) AscentVisualState.tick();
        require(AscentVisualState.value(1) == 0, "Backing off must fully clear the screen");
        AscentVisualState.receive(1);
        for (int i = 0; i < 70; i++) AscentVisualState.tick();
        require(AscentVisualState.value(1) == 0, "Missing server updates must not leave stale effects");
        GarageTransitionState.begin();
        AscentVisualState.receive(1);
        for (int i = 0; i < 60; i++) {
            GarageTransitionState.tick();
            AscentVisualState.tick();
        }
        require(AscentVisualState.value(1) > 0.99f, "Transfer must preserve ascent while awaiting destination updates");
        GarageTransitionState.release();
        for (int i = 0; i < 70; i++) AscentVisualState.tick();
        require(AscentVisualState.value(1) == 0, "Transfer release must restore stale-packet timeout");
        GarageTransitionState.reset();
        AscentVisualState.receive(Float.NaN);
        AscentVisualState.tick();
        require(Float.isFinite(AscentVisualState.value(1)), "Invalid intensities must not poison rendering");
        AscentVisualState.reset();
        require(AscentVisualState.value(1) == 0, "Disconnect reset must clear interpolation");
        require(AscentVisualState.fovValue(1) == 0, "Disconnect reset must clear FOV interpolation");
        float[] normalAudio = {0, 0.1f, 0.2f, 0.3f, 1, 0.8f, 0.6f, 0.4f, 0.7f, 1};
        require(java.util.Arrays.equals(normalAudio, StaircaseAudio.blend(normalAudio, 0)),
                "Zero ascent must preserve SPR's exact environment");
        float[] basinAudio = StaircaseAudio.blend(normalAudio, 0, 0, 0, 0, 1);
        require(basinAudio[3] == 1 && basinAudio[9] >= 0.9f,
                "Echo Basin must add strong late reflections without silencing the direct sound");
        require(java.util.Arrays.equals(normalAudio, StaircaseAudio.blend(normalAudio, 0, 0, 0, 0, Float.NaN)),
                "Invalid Echo Basin strength must preserve the original audio");
        float[][] basinReceived = {null};
        StaircaseAudio.echoIntensity(1);
        StaircaseAudio.category(99, net.minecraft.sounds.SoundSource.BLOCKS,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("block.stone.break"), false);
        require(StaircaseAudio.environment(99, normalAudio, (id, values) -> basinReceived[0] = values),
                "Echo Basin must apply to existing SPR sources");
        require(java.util.Arrays.equals(basinReceived[0], basinAudio), "Echo Basin source must receive its own profile");
        StaircaseAudio.echoIntensity(0);
        StaircaseAudio.update(99);
        require(java.util.Arrays.equals(basinReceived[0], normalAudio),
                "Leaving Echo Basin must restore the source's original environment");
        StaircaseAudio.forget(99);
        float[] muffled = StaircaseAudio.blend(normalAudio, 1);
        require(muffled[8] < normalAudio[8] * 0.04f && muffled[9] > 0,
                "Full ascent must muffle highs without silencing the dry sound");
        require(muffled[3] > normalAudio[3], "Full ascent must add a diffuse reverb tail");
        require(normalAudio[8] == 0.7f, "Blending must not mutate the cached environment");
        require(!StaircaseAudio.affects(net.minecraft.sounds.SoundSource.MUSIC, false)
                && !StaircaseAudio.affects(net.minecraft.sounds.SoundSource.MASTER, false)
                && !StaircaseAudio.affects(net.minecraft.sounds.SoundSource.AMBIENT, true),
                "Music, UI and auxiliary voice-chat sources must stay excluded");
        float[][] receivedAudio = {null};
        StaircaseAudio.category(42, net.minecraft.sounds.SoundSource.AMBIENT,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("ambient.cave"), false);
        StaircaseAudio.intensity(1);
        require(StaircaseAudio.environment(42, normalAudio, (id, values) -> receivedAudio[0] = values),
                "Playing ambient sources must receive the ascent mix");
        require(receivedAudio[0][8] < 0.03f, "Initial source must be muffled");
        StaircaseAudio.intensity(0);
        StaircaseAudio.update(42);
        require(java.util.Arrays.equals(receivedAudio[0], normalAudio),
                "Existing sounds must restore their original environment on exit");
        StaircaseAudio.forget(42);
        receivedAudio[0] = null;
        StaircaseAudio.intensity(1);
        StaircaseAudio.update(42);
        require(receivedAudio[0] == null, "Destroyed sources must not retain filters");
        StaircaseAudio.clear();
        StaircaseAudio.intensity(0);
        float[] distantOrb = StaircaseAudio.blend(normalAudio, 0, 0, 1);
        require(distantOrb[3] >= 0.89f && distantOrb[8] < normalAudio[8] * 0.13f
                        && distantOrb[9] > 0,
                "Orb sources must gain a long dark echo without losing direction entirely");
        StaircaseAudio.orbIntensity(1);
        StaircaseAudio.category(43, net.minecraft.sounds.SoundSource.AMBIENT,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("ambient.cave"), false);
        require(!StaircaseAudio.environment(43, normalAudio, (id, values) -> {}),
                "Orb reverb must not leak into ordinary ambient sources");
        StaircaseAudio.forget(43);
        StaircaseAudio.orbIntensity(0);
        float[] futureEcho = StaircaseAudio.blend(normalAudio, 0, 0, 1, 0);
        require(futureEcho[3] >= 0.57f && futureEcho[8] < normalAudio[8]
                        && futureEcho[9] > normalAudio[9] * 0.9f,
                "Future world sounds must gain a clear echo while retaining their dry direction");
        StaircaseAudio.futureIntensity(1);
        StaircaseAudio.category(44, net.minecraft.sounds.SoundSource.BLOCKS,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("block.stone.break"), false);
        require(StaircaseAudio.environment(44, normalAudio, (id, values) -> receivedAudio[0] = values),
                "Future world block sounds must receive the dimension reverb");
        StaircaseAudio.futureIntensity(0);
        StaircaseAudio.update(44);
        require(java.util.Arrays.equals(receivedAudio[0], normalAudio),
                "Leaving the future world must restore the original sound environment");
        StaircaseAudio.forget(44);
        for (int coordinate : new int[]{-10000, -33, -32, -1, 0, 31, 32, 10000}) {
            BlockPos entrance = new BlockPos(coordinate, 70, -coordinate);
            BlockPos arrival = GarageTravel.arrival(entrance);
            require(arrival.getX() == entrance.getX() && arrival.getZ() == entrance.getZ()
                    && arrival.getY() == FutureDimension.SURFACE_Y + 1, "Future arrivals must retain the entrance's horizontal coordinates");
            require(arrival.equals(GarageTravel.arrival(entrance)), "Arrival must be stable across visits");
            Vec3 feet = new Vec3(coordinate + 0.13, 77.5, -coordinate + 0.87);
            BlockPos buriedOrigin = entrance.atY(FutureDimension.SURFACE_Y - 8);
            Vec3 translated = StaircaseArrival.translate(feet, entrance, buriedOrigin);
            require(translated.x == feet.x && translated.z == feet.z
                    && translated.y - buriedOrigin.getY() == feet.y - entrance.getY(), "Arrival must preserve fractional tread position");
            require(StaircaseArrival.translate(translated, buriedOrigin, entrance).equals(feet), "Return translation must be reversible");
        }
        float[] dryMirror = {0, 0, 0, 0, 1, 1, 1, 1, 1, 1};
        float[] dreamMirror = StaircaseAudio.blend(dryMirror, 0, 1);
        require(dreamMirror[3] >= 0.69f && dreamMirror[8] < 1 && dreamMirror[9] > 0.8f,
                "Mirror must add diffuse reverb while preserving an audible direct signal");
        require(java.util.Arrays.equals(dryMirror, StaircaseAudio.blend(dryMirror, 0, 0)),
                "Leaving mirror and stairs must restore the original audio environment");
        UUID visitor = UUID.randomUUID();
        CompoundTag returnPoint = new CompoundTag();
        returnPoint.putLong("Position", origin.asLong());
        data.garageReturns.put(visitor, returnPoint);
        data.futureMidnights.put(visitor, 4);
        StaircaseData restoredTravel = StaircaseData.load(data.save(new CompoundTag(), null), null);
        require(restoredTravel.garageReturns.get(visitor).getLong("Position") == origin.asLong(),
                "Return point must survive world restart");
        require(restoredTravel.futureMidnights.get(visitor) == 4,
                "Experienced future midnights must survive world restart");
        UUID otherVisitor = UUID.randomUUID();
        require(!data.echoComplete(visitor) && data.echoReferenceCount(visitor) == 0,
                "Echo Basin starts with all three references missing");
        require(data.recordEchoReference(visitor, 0) && !data.recordEchoReference(visitor, 0)
                        && data.echoReferenceCount(visitor) == 1,
                "Duplicate wells must not count as new references");
        require(data.recordEchoReference(visitor, 2) && !data.echoComplete(visitor),
                "Two different wells must not unlock the exit");
        require(data.recordEchoReference(visitor, 1) && data.echoComplete(visitor)
                        && !data.echoComplete(otherVisitor),
                "All three references unlock only the completing player's exit");
        StaircaseData restoredWells = StaircaseData.load(data.save(new CompoundTag(), null), null);
        require(restoredWells.echoComplete(visitor) && restoredWells.hasEchoReference(visitor, 0)
                        && restoredWells.hasEchoReference(visitor, 1) && restoredWells.hasEchoReference(visitor, 2),
                "Listening Well reference progress must survive restart");
        require(data.nextEchoReference(otherVisitor) == 0 && data.nextEchoReference(visitor) == 3,
                "Fresh players start at Presence and completed players cannot spend another well");
        BlockPos spentWell = new BlockPos(808, 50, -312);
        BlockPos waitingWell = new BlockPos(-408, 62, 712);
        BlockPos echoExit = new BlockPos(-398, 63, 712);
        data.registerEchoWell(waitingWell);
        require(data.registerEchoExit(echoExit, net.minecraft.core.Direction.EAST)
                        && !data.registerEchoExit(echoExit, net.minecraft.core.Direction.WEST),
                "Echo exits must retain their original orientation");
        require(data.activateEchoWell(spentWell) && !data.activateEchoWell(spentWell),
                "Each physical well can activate once globally");
        StaircaseData restoredSpentWells = StaircaseData.load(data.save(new CompoundTag(), null), null);
        require(restoredSpentWells.activatedEchoWells.contains(spentWell)
                        && restoredSpentWells.knownEchoWells.contains(spentWell)
                        && restoredSpentWells.knownEchoWells.contains(waitingWell)
                        && restoredSpentWells.echoExitStairs.get(echoExit) == net.minecraft.core.Direction.EAST
                        && !restoredSpentWells.activateEchoWell(spentWell),
                "Known wells, spent wells, and giant exits must survive restart");
        GarageTransitionState.reset();
        GarageTransitionState.begin();
        for (int i = 0; i < 20; i++) GarageTransitionState.tick();
        require(GarageTransitionState.value(1) == 1, "Transition must reach black before transfer");
        GarageTransitionState.cover();
        GarageTransitionState.release();
        GarageTransitionState.tick();
        require(GarageTransitionState.value(1) > 0 && GarageTransitionState.value(1) < 1, "Arrival must ease out of black");
        for (int i = 0; i < 30; i++) GarageTransitionState.tick();
        require(GarageTransitionState.value(1) == 0, "Arrival fade must fully clear");
        GarageTransitionState.reset();
        System.out.println("Staircase, audio, garage arrival and transition regression checks passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
