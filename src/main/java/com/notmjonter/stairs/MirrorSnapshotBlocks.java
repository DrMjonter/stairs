package com.notmjonter.stairs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

/** Preserve loadable block entities while resetting their saved contents and state. */
final class MirrorSnapshotBlocks {
    private MirrorSnapshotBlocks() {}

    static void sanitize(CompoundTag chunk) {
        var records = chunk.getList("block_entities", net.minecraft.nbt.Tag.TAG_COMPOUND);
        var clean = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < records.size(); i++) {
            CompoundTag original = records.getCompound(i);
            CompoundTag identity = new CompoundTag();
            // Chunk loading needs these records to instantiate and synchronize renderers.
            // Whitelisting identity also excludes loot tables and modded inventory data.
            for (String key : new String[]{"id", "x", "y", "z", "keepPacked"}) {
                if (original.contains(key)) identity.put(key, original.get(key).copy());
            }
            clean.add(identity);
        }
        chunk.put("block_entities", clean);
    }

    record UpdateStats(int scannedRegions, int updatedChunks, int reusedRegions) {}

    static UpdateStats updateBaseline(Path world, Path baseline) throws IOException {
        Files.createDirectories(baseline);
        Path interrupted = baseline.resolve("updating");
        Files.writeString(interrupted, "Discard this cache if the update does not finish");
        int scanned = 0, updated = 0, reused = 0;
        for (String folder : new String[]{"region", "poi"}) {
            Path source = world.resolve(folder), destination = baseline.resolve(folder);
            Path indices = baseline.resolve("indices").resolve(folder);
            Files.createDirectories(destination);
            Files.createDirectories(indices);
            var present = new java.util.HashSet<String>();
            if (Files.isDirectory(source)) try (var paths = Files.list(source)) {
                for (Path file : paths.toList()) {
                    var match = java.util.regex.Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca").matcher(file.getFileName().toString());
                    if (!match.matches()) continue;
                    String name = file.getFileName().toString();
                    present.add(name);
                    Path indexPath = indices.resolve(name + ".properties");
                    var index = new java.util.Properties();
                    if (Files.exists(indexPath)) try (var input = Files.newInputStream(indexPath)) { index.load(input); }
                    // Full-resolution file time detects repeated saves within the same
                    // second, unlike the coarse timestamp in an Anvil region header.
                    String signature = Files.getLastModifiedTime(file) + ":" + Files.size(file);
                    if (signature.equals(index.getProperty("signature")) && Files.exists(destination.resolve(name))) {
                        reused++;
                        continue;
                    }
                    scanned++;
                    int rx = Integer.parseInt(match.group(1)), rz = Integer.parseInt(match.group(2));
                    var next = new java.util.Properties();
                    var info = new RegionStorageInfo("mirror-baseline", MirrorDimension.KEY, folder);
                    try (RegionFile input = new RegionFile(info, file, source, true);
                         RegionFile output = new RegionFile(info, destination.resolve(name), destination, true)) {
                        for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) {
                            ChunkPos pos = new ChunkPos(rx * 32 + x, rz * 32 + z);
                            String key = x + "," + z;
                            if (!input.hasChunk(pos)) {
                                if (output.hasChunk(pos)) { output.clear(pos); updated++; }
                                continue;
                            }
                            byte[] bytes;
                            try (var stream = input.getChunkDataInputStream(pos)) {
                                if (stream == null) throw new IOException("Unreadable snapshot chunk " + pos);
                                bytes = stream.readAllBytes();
                            }
                            String digest;
                            try { digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
                            catch (java.security.NoSuchAlgorithmException exception) { throw new AssertionError(exception); }
                            next.setProperty(key, digest);
                            if (digest.equals(index.getProperty(key)) && output.hasChunk(pos)) continue;
                            CompoundTag tag;
                            try (var stream = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes))) { tag = NbtIo.read(stream); }
                            if (folder.equals("region")) sanitize(tag);
                            try (var stream = output.getChunkDataOutputStream(pos)) { NbtIo.write(tag, stream); }
                            updated++;
                        }
                    }
                    // Opening an Anvil file can pad its tail on close; record metadata afterward.
                    next.setProperty("signature", Files.getLastModifiedTime(file) + ":" + Files.size(file));
                    try (var stream = Files.newOutputStream(indexPath)) { next.store(stream, "Overworld chunk fingerprints"); }
                }
            }
            // Region removal/pruning in the source must remove cached chunks too.
            try (var paths = Files.list(destination)) {
                for (Path file : paths.toList()) {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".mca") && !present.contains(name)) {
                        Files.delete(file);
                        Files.deleteIfExists(indices.resolve(name + ".properties"));
                    } else if (name.endsWith(".mcc")) {
                        var parts = name.split("\\.");
                        if (parts.length == 4 && parts[0].equals("c")) {
                            String region = "r." + Math.floorDiv(Integer.parseInt(parts[1]), 32) + "."
                                    + Math.floorDiv(Integer.parseInt(parts[2]), 32) + ".mca";
                            if (!present.contains(region)) Files.delete(file);
                        }
                    }
                }
            }
        }
        Files.delete(interrupted);
        com.mojang.logging.LogUtils.getLogger().info("Mirror incremental capture: {} changed regions scanned, {} chunks updated, {} regions reused",
                scanned, updated, reused);
        return new UpdateStats(scanned, updated, reused);
    }

}
