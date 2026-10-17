package com.notmjonter.stairs;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Checks persistence of separate visits and exact recorded motion/pose. */
final class FutureReplayTest {
    static void run() {
        FutureReplays.Archive archive = new FutureReplays.Archive();
        UUID player = UUID.randomUUID();
        FutureReplays.Visit first = new FutureReplays.Visit(UUID.randomUUID(), player, "TestVisitor");
        first.frames.add(new FutureReplays.Frame(0, -32.125, 64.5, 1024.75, 179, -45, true, false));
        first.frames.add(new FutureReplays.Frame(2, -31.125, 65.5, 1024.75, -179, 15, false, true));
        first.ticks = 7;
        FutureReplays.Visit second = new FutureReplays.Visit(UUID.randomUUID(), player, "TestVisitor");
        second.frames.add(new FutureReplays.Frame(0, 0, 70, 0, 0, 0, false, true));
        second.ticks = 1;
        archive.visits.add(first);
        archive.visits.add(second);
        CompoundTag saved = archive.save(new CompoundTag(), null);
        FutureReplays.Archive restored = FutureReplays.Archive.load(saved, null);
        require(restored.visits.size() == 2, "Repeated visits must remain separate recordings");
        require(restored.visits.getFirst().id.equals(first.id)
                        && restored.visits.getFirst().player.equals(player),
                "Visit identity and ownership must survive restart");
        require(restored.visits.getFirst().playerName.equals("TestVisitor")
                        && restored.visits.getFirst().ticks == 7,
                "Owner name and actual duration must survive restart with irregular landing keyframes");
        require(restored.visits.getFirst().frames.equals(first.frames),
                "Fractional positions, wrapped gaze, jumping and crouching must survive serialization");
        first.frames.add(new FutureReplays.Frame(7, 1, 2, 3, 0, 0, false, true));
        require(restored.visits.getFirst().frames.size() == 2,
                "Saved snapshots must not change when the ongoing visit receives more samples");
        for (int secondIndex = 0; secondIndex < 60; secondIndex++) {
            int samples = 0;
            for (int tick = secondIndex * 20; tick < (secondIndex + 1) * 20; tick++)
                if (FutureReplays.Visit.sampleTick(tick)) samples++;
            require(samples == 6, "Sampling must produce exactly six regular frames per second without drift");
        }
        StaircaseData sourceStairs = new StaircaseData();
        StaircaseData localStairs = new StaircaseData();
        var sourceEntrance = new StaircaseData.Staircase(new net.minecraft.core.BlockPos(100, 70, 200),
                net.minecraft.world.level.block.Rotation.CLOCKWISE_90, 1);
        var localEntrance = new StaircaseData.Staircase(new net.minecraft.core.BlockPos(100, 56, 200),
                sourceEntrance.rotation, 1);
        localEntrance.returnOrigin = sourceEntrance.origin;
        sourceStairs.stairs.put(sourceEntrance.origin, sourceEntrance);
        localStairs.stairs.put(localEntrance.origin, localEntrance);
        CompoundTag entranceRecord = new CompoundTag();
        entranceRecord.putLong("Position", sourceEntrance.position(2, 1, 0).asLong());
        require(GarageTravel.futureEntrance(localStairs, sourceStairs, entranceRecord) == localEntrance,
                "Existing return records must resolve the local arrival staircase, not the Overworld entrance");
        entranceRecord.putLong("FutureEntrance", localEntrance.origin.asLong());
        require(GarageTravel.futureEntrance(localStairs, new StaircaseData(), entranceRecord) == localEntrance,
                "New visit records must retain their exact local entrance independently of source stairs");
        System.out.println("Future replay archive persistence checks passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
