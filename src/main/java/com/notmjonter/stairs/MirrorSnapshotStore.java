package com.notmjonter.stairs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Properties;
import java.util.UUID;

/** Staged disk snapshots. Installation is allowed only before dimension storage opens. */
final class MirrorSnapshotStore {
    private final Path world;
    private final Path storage;
    private final Path target;
    private static final String MARKER = "mirror-snapshot.properties";

    MirrorSnapshotStore(Path world) throws IOException {
        this.world = world.toRealPath();
        storage = this.world.resolve("mjontersstairs_mirror_snapshots");
        target = this.world.resolve("dimensions/mjontersstairs/dimension_7");
        checked(storage);
        checked(target);
        Files.createDirectories(storage);
    }

    @FunctionalInterface
    interface ExtraData { void write(Path destination) throws IOException; }

    String capture(long now, ExtraData extra) throws IOException {
        return capture(now, staging -> {
            for (String folder : new String[]{"region", "poi"}) {
                Path source = checked(world.resolve(folder));
                if (Files.exists(source)) copyTree(source, staging.resolve(folder));
            }
        }, extra);
    }

    String captureIncremental(long now, Runnable sourceReleased, ExtraData extra) throws IOException {
        return capture(now, staging -> {
            Path baseline = checked(storage.resolve("baseline"));
            // A failed/interrupted cache update is rebuilt; never trust partial hashes.
            if (Files.exists(checked(baseline.resolve("updating")))) removeTree(baseline);
            if (Files.exists(baseline)) try (var paths = Files.walk(baseline)) {
                for (Path path : paths.toList()) checked(path);
            }
            for (String folder : new String[]{"region", "poi"}) {
                Path source = checked(world.resolve(folder));
                if (Files.exists(source)) try (var paths = Files.walk(source)) {
                    for (Path path : paths.toList()) checked(path);
                }
            }
            MirrorSnapshotBlocks.updateBaseline(world, baseline);
            sourceReleased.run();
            // A real independent copy is essential: never hard-link writable mirror
            // region files to the clean baseline or the original Overworld.
            for (String folder : new String[]{"region", "poi"}) {
                Path source = checked(baseline.resolve(folder));
                if (Files.exists(source)) copyTree(source, staging.resolve(folder));
            }
        }, extra);
    }

    private String capture(long now, ExtraData populate, ExtraData extra) throws IOException {
        String id = "snapshot-" + UUID.randomUUID();
        Path staging = checked(storage.resolve(id));
        Files.createDirectory(staging);
        boolean published = false;
        try {
            populate.write(staging);
            extra.write(staging);
            Properties marker = new Properties();
            marker.setProperty("id", id);
            marker.setProperty("capturedAt", Long.toString(now));
            writeProperties(staging.resolve(MARKER), marker);
            Properties pointer = new Properties();
            pointer.setProperty("id", id);
            writeProperties(storage.resolve("pending.properties"), pointer);
            published = true;
            // Only discard superseded staging copies after publishing the complete new copy.
            pruneStaging(id);
            return id;
        } finally {
            if (!published) removeTree(staging);
        }
    }

    /** Recoverable directory swap: retain the previous mirror until the next installation. */
    boolean install() throws IOException {
        Properties pointer = readProperties(storage.resolve("pending.properties"));
        String id = pointer.getProperty("id");
        if (id == null) return false;
        if (!id.matches("snapshot-[0-9a-f-]{36}")) throw new IOException("Invalid mirror snapshot pointer");
        Path staging = checked(storage.resolve(id));
        if (id.equals(readProperties(target.resolve(MARKER)).getProperty("id"))) {
            // A previous run completed the rename but stopped before clearing its pointer.
            Files.deleteIfExists(checked(storage.resolve("pending.properties")));
            return true;
        }
        if (!id.equals(readProperties(staging.resolve(MARKER)).getProperty("id")))
            throw new IOException("Mirror snapshot is missing its completion marker; preserving existing mirror");
        Path previous = checked(storage.resolve("previous-mirror"));
        Files.createDirectories(checked(target.getParent()));
        if (Files.exists(target)) {
            removeTree(previous);
            Files.move(checked(target), previous, StandardCopyOption.ATOMIC_MOVE);
        }
        // If interrupted after moving the old mirror, this resumes with target absent.
        try { Files.move(staging, checked(target), StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException failure) {
            if (!Files.exists(target) && Files.exists(previous)) {
                try { Files.move(previous, checked(target), StandardCopyOption.ATOMIC_MOVE); }
                catch (IOException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
        Files.deleteIfExists(checked(storage.resolve("pending.properties")));
        return true;
    }

    Properties state() throws IOException { return readProperties(storage.resolve("schedule.properties")); }
    void state(Properties state) throws IOException { writeProperties(storage.resolve("schedule.properties"), state); }
    Properties active() throws IOException { return readProperties(target.resolve(MARKER)); }
    boolean pending() throws IOException { return Files.exists(checked(storage.resolve("pending.properties"))); }

    private void pruneStaging(String keep) throws IOException {
        try (var entries = Files.list(storage)) {
            for (Path entry : entries.toList()) {
                String name = entry.getFileName().toString();
                if (name.matches("snapshot-[0-9a-f-]{36}") && !name.equals(keep)) removeTree(entry);
            }
        }
    }

    private void copyTree(Path source, Path destination) throws IOException {
        try (var entries = Files.walk(source)) {
            for (Path entry : entries.toList()) {
                checked(entry);
                Path copy = checked(destination.resolve(source.relativize(entry)));
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(copy);
                else if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) Files.copy(entry, copy);
                else throw new IOException("Unsupported snapshot file: " + entry);
            }
        }
    }

    private void removeTree(Path path) throws IOException {
        path = checked(path);
        if (!path.startsWith(storage) || path.equals(storage)) throw new IOException("Unsafe snapshot cleanup target");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        try (var entries = Files.walk(path)) {
            // Validate the complete tree before deleting anything; never follow links outside the save.
            var paths = entries.sorted(Comparator.reverseOrder()).toList();
            for (Path entry : paths) checked(entry);
            for (Path entry : paths) Files.delete(entry);
        }
    }

    private Properties readProperties(Path path) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(checked(path))) try (var input = Files.newInputStream(path)) { properties.load(input); }
        return properties;
    }

    private void writeProperties(Path path, Properties properties) throws IOException {
        checked(path);
        Files.createDirectories(checked(path.getParent()));
        Path temporary = checked(path.resolveSibling(path.getFileName() + ".tmp"));
        try (var output = Files.newOutputStream(temporary)) { properties.store(output, "Stairs mirror snapshot"); }
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private Path checked(Path path) throws IOException {
        path = path.toAbsolutePath().normalize();
        if (!path.startsWith(world) || path.equals(world)) throw new IOException("Snapshot path escaped world directory");
        Path cursor = world;
        for (Path part : world.relativize(path)) {
            cursor = cursor.resolve(part);
            if (Files.isSymbolicLink(cursor)) throw new IOException("Snapshot paths cannot contain symbolic links: " + cursor);
            if (Files.exists(cursor) && !cursor.toRealPath().startsWith(world))
                throw new IOException("Snapshot path resolves outside world directory: " + cursor);
        }
        return path;
    }
}
