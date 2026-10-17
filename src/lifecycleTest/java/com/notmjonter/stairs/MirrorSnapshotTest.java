package com.notmjonter.stairs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

final class MirrorSnapshotTest {
    static void run() {
        try {
            incremental();
            var chunkTag = new net.minecraft.nbt.CompoundTag();
            var containers = new net.minecraft.nbt.ListTag();
            var chestTag = new net.minecraft.nbt.CompoundTag();
            chestTag.putString("id", "minecraft:chest");
            chestTag.putInt("x", -17);
            chestTag.putInt("y", 72);
            chestTag.putInt("z", 33);
            chestTag.putString("LootTable", "minecraft:chests/simple_dungeon");
            chestTag.put("Items", new net.minecraft.nbt.ListTag());
            chestTag.put("neoforge:attachments", new net.minecraft.nbt.CompoundTag());
            containers.add(chestTag);
            chunkTag.put("block_entities", containers);
            chunkTag.putString("Status", "minecraft:full");
            MirrorSnapshotBlocks.sanitize(chunkTag);
            var cleanChest = chunkTag.getList("block_entities", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
            check(cleanChest.getString("id").equals("minecraft:chest") && cleanChest.getInt("x") == -17
                    && cleanChest.getInt("y") == 72 && cleanChest.getInt("z") == 33,
                    "Block entity identity and position must survive for client synchronization");
            check(cleanChest.getAllKeys().equals(java.util.Set.of("id", "x", "y", "z"))
                    && chunkTag.getString("Status").equals("minecraft:full"),
                    "Inventories, loot tables and attachments must be stripped while preserving terrain");
            long now = 20_000L * MirrorSnapshotSchedule.DAY_MILLIS + 1234;
            for (int seed = 0; seed < 100; seed++) {
                long first = MirrorSnapshotSchedule.first(now, new Random(seed));
                check(first >= now && MirrorSnapshotSchedule.day(first) == MirrorSnapshotSchedule.day(now), "First capture must be today");
                long next = MirrorSnapshotSchedule.afterSuccess(now, new Random(seed));
                check(MirrorSnapshotSchedule.day(next) == MirrorSnapshotSchedule.day(now) + 1, "Next automatic capture must be tomorrow");
            }
            check(!MirrorSnapshotSchedule.due(now, now - 1, now - 100), "Never capture twice automatically on the same day");
            check(MirrorSnapshotSchedule.due(now + 5 * MirrorSnapshotSchedule.DAY_MILLIS, now, now), "Offline days must allow one catch-up");
            check(!MirrorSnapshotSchedule.due(now - MirrorSnapshotSchedule.DAY_MILLIS, now, now), "Clock rollback cannot duplicate capture");
            Path world = Files.createTempDirectory(Path.of("."), "mirror-snapshot-test-");
            Files.createDirectories(world.resolve("region"));
            Files.writeString(world.resolve("region/r.0.0.mca"), "build before snapshot");
            Files.createDirectories(world.resolve("entities"));
            Files.writeString(world.resolve("entities/r.0.0.mca"), "items and mobs");
            Files.createDirectories(world.resolve("playerdata"));
            Files.writeString(world.resolve("playerdata/keep"), "player inventory");
            Path target = world.resolve("dimensions/mjontersstairs/dimension_7");
            Files.createDirectories(target.resolve("region"));
            Files.writeString(target.resolve("region/old.mca"), "old mirror build");
            MirrorSnapshotStore store = new MirrorSnapshotStore(world);
            store.capture(now, path -> {});
            Files.writeString(world.resolve("region/r.0.0.mca"), "new overworld build");
            check(Files.exists(target.resolve("region/old.mca")), "Capture must leave active mirror untouched");
            check(store.install(), "Completed snapshot must install");
            check(Files.readString(target.resolve("region/r.0.0.mca")).equals("build before snapshot"), "Mirror must retain stale builds");
            check(!Files.exists(target.resolve("region/old.mca")), "Refresh must replace old mirror chunks");
            check(!Files.exists(target.resolve("entities")), "Blocks-only snapshots must not duplicate item entities or mobs");
            check(!Files.exists(target.resolve("playerdata")), "Never copy player inventories");
            check(Files.readString(world.resolve("region/r.0.0.mca")).equals("new overworld build"), "Never overwrite the Overworld");
            check(!store.install(), "Installing twice must be harmless");
            try { store.capture(now + 1, path -> { throw new java.io.IOException("injected failure"); });
                throw new AssertionError("Failed capture must propagate");
            } catch (java.io.IOException expected) {}
            check(!store.pending(), "Partial capture cannot become pending");
            check(Files.readString(target.resolve("region/r.0.0.mca")).equals("build before snapshot"), "Failed capture must preserve active mirror");
            store.capture(now + 2, path -> {});
            // Simulate interruption after moving the current dimension out of the way.
            Path interrupted = world.resolve("interrupted-old-mirror");
            Files.move(target, interrupted);
            check(new MirrorSnapshotStore(world).install(), "A restart must finish an interrupted swap");
            check(Files.readString(target.resolve("region/r.0.0.mca")).equals("new overworld build"), "Recovered swap must install complete copy");
            System.out.println("Mirror snapshot schedule, isolation, failure and recovery checks passed.");
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }
    private static void incremental() throws java.io.IOException {
        Path world = Files.createTempDirectory(Path.of("."), "mirror-incremental-test-");
        Path baseline = world.resolve("cache");
        writeChunk(world, 0, 1);
        writeChunk(world, 1, 10);
        var first = MirrorSnapshotBlocks.updateBaseline(world, baseline);
        check(first.updatedChunks() == 2, "Initial cache must populate every source chunk");
        var unchanged = MirrorSnapshotBlocks.updateBaseline(world, baseline);
        check(unchanged.updatedChunks() == 0 && unchanged.scannedRegions() == 0 && unchanged.reusedRegions() == 1,
                "Unchanged regions must skip chunk reading and sanitization");
        writeChunk(world, 0, 2);
        Files.setLastModifiedTime(world.resolve("region/r.0.0.mca"), java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 2000));
        var changed = MirrorSnapshotBlocks.updateBaseline(world, baseline);
        check(changed.updatedChunks() == 1 && changed.scannedRegions() == 1,
                "Only the modified chunk inside a changed region should be rewritten");
        check(readChunk(baseline, 0) == 2 && readChunk(baseline, 1) == 10, "Unchanged builds must survive incremental updates");
        MirrorSnapshotStore store = new MirrorSnapshotStore(world);
        store.captureIncremental(100, () -> {}, path -> {});
        store.install();
        Path playable = world.resolve("dimensions/mjontersstairs/dimension_7");
        writeChunk(playable, 1, 999);
        store.captureIncremental(200, () -> {}, path -> {});
        store.install();
        check(readChunk(playable, 1) == 10, "Mirror edits must never contaminate the clean baseline");
        Files.delete(world.resolve("region/r.0.0.mca"));
        MirrorSnapshotBlocks.updateBaseline(world, baseline);
        check(!Files.exists(baseline.resolve("region/r.0.0.mca")), "Pruned source regions must disappear from baseline");
    }

    private static void writeChunk(Path root, int x, int version) throws java.io.IOException {
        Path folder = root.resolve("region");
        Files.createDirectories(folder);
        try (var region = new net.minecraft.world.level.chunk.storage.RegionFile(
                new net.minecraft.world.level.chunk.storage.RegionStorageInfo("test", MirrorDimension.KEY, "chunk"),
                folder.resolve("r.0.0.mca"), folder, true)) {
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.putInt("version", version);
            try (var output = region.getChunkDataOutputStream(new net.minecraft.world.level.ChunkPos(x, 0))) {
                net.minecraft.nbt.NbtIo.write(tag, output);
            }
        }
    }

    private static int readChunk(Path root, int x) throws java.io.IOException {
        Path folder = root.resolve("region");
        try (var region = new net.minecraft.world.level.chunk.storage.RegionFile(
                new net.minecraft.world.level.chunk.storage.RegionStorageInfo("test", MirrorDimension.KEY, "chunk"),
                folder.resolve("r.0.0.mca"), folder, true);
             var input = region.getChunkDataInputStream(new net.minecraft.world.level.ChunkPos(x, 0))) {
            return net.minecraft.nbt.NbtIo.read(input).getInt("version");
        }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
