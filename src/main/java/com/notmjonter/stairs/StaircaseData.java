package com.notmjonter.stairs;

import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent identity and lifecycle of stairs; approach progress is intentionally session-only. */
public final class StaircaseData extends SavedData {
    static final int TOTAL_DESTINATIONS = 7;
    final Map<BlockPos, Staircase> stairs = new LinkedHashMap<>();
    final Map<UUID, CompoundTag> garageReturns = new LinkedHashMap<>();
    final Map<UUID, Integer> futureMidnights = new LinkedHashMap<>();
    final Map<UUID, Integer> echoReferences = new LinkedHashMap<>();
    final Map<UUID, CompoundTag> echoVoiceLines = new LinkedHashMap<>();
    final Set<BlockPos> knownEchoWells = new HashSet<>();
    final Set<BlockPos> activatedEchoWells = new HashSet<>();
    final Map<BlockPos, Direction> echoExitStairs = new LinkedHashMap<>();

    public static StaircaseData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(StaircaseData::new, StaircaseData::load), Stairs.MODID + "_staircases");
    }

    Staircase register(BlockPos origin, Rotation rotation, long seed) {
        return stairs.computeIfAbsent(origin.immutable(), pos -> {
            setDirty();
            return new Staircase(pos, rotation, destination(seed, pos));
        });
    }

    static int destination(long seed, BlockPos origin) {
        // Mix world seed and location once; visiting players never influence the result.
        long value = seed ^ origin.asLong();
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return (int) Math.floorMod(value ^ (value >>> 31), TOTAL_DESTINATIONS) + 1;
    }

    static StaircaseData load(CompoundTag tag, HolderLookup.Provider registries) {
        StaircaseData data = new StaircaseData();
        for (Tag value : tag.getList("Staircases", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            BlockPos origin = BlockPos.of(entry.getLong("Origin"));
            Staircase stair = new Staircase(origin, Rotation.valueOf(entry.getString("Rotation")), entry.getInt("Destination"));
            stair.state = State.valueOf(entry.getString("State"));
            stair.removedBlocks = entry.getInt("RemovedBlocks");
            if (entry.contains("ReturnOrigin")) stair.returnOrigin = BlockPos.of(entry.getLong("ReturnOrigin"));
            data.stairs.put(origin, stair);
        }
        for (Tag value : tag.getList("GarageReturns", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            data.garageReturns.put(entry.getUUID("Player"), entry.getCompound("Return").copy());
        }
        for (Tag value : tag.getList("FutureMidnights", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            data.futureMidnights.put(entry.getUUID("Player"), entry.getInt("Count"));
        }
        for (Tag value : tag.getList("EchoReferences", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            data.echoReferences.put(entry.getUUID("Player"), entry.getInt("Mask") & 7);
        }
        for (long position : tag.getLongArray("ActivatedEchoWells"))
            data.activatedEchoWells.add(BlockPos.of(position));
        for (long position : tag.getLongArray("KnownEchoWells"))
            data.knownEchoWells.add(BlockPos.of(position));
        for (Tag value : tag.getList("EchoExitStairs", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            data.echoExitStairs.put(BlockPos.of(entry.getLong("Origin")),
                    Direction.valueOf(entry.getString("Direction")));
        }
        for (Tag value : tag.getList("EchoVoiceLines", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            data.echoVoiceLines.put(entry.getUUID("Player"), entry.getCompound("Files").copy());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Staircase stair : stairs.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Origin", stair.origin.asLong());
            entry.putString("Rotation", stair.rotation.name());
            entry.putInt("Destination", stair.destination);
            entry.putString("State", stair.state.name());
            entry.putInt("RemovedBlocks", stair.removedBlocks);
            if (stair.returnOrigin != null) entry.putLong("ReturnOrigin", stair.returnOrigin.asLong());
            entries.add(entry);
        }
        tag.put("Staircases", entries);
        ListTag returns = new ListTag();
        garageReturns.forEach((player, destination) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.put("Return", destination.copy());
            returns.add(entry);
        });
        tag.put("GarageReturns", returns);
        ListTag midnights = new ListTag();
        futureMidnights.forEach((player, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putInt("Count", count);
            midnights.add(entry);
        });
        tag.put("FutureMidnights", midnights);
        ListTag references = new ListTag();
        echoReferences.forEach((player, mask) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putInt("Mask", mask & 7);
            references.add(entry);
        });
        tag.put("EchoReferences", references);
        tag.putLongArray("KnownEchoWells", knownEchoWells.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("ActivatedEchoWells", activatedEchoWells.stream().mapToLong(BlockPos::asLong).toArray());
        ListTag exits = new ListTag();
        echoExitStairs.forEach((origin, direction) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Origin", origin.asLong());
            entry.putString("Direction", direction.name());
            exits.add(entry);
        });
        tag.put("EchoExitStairs", exits);
        ListTag voices = new ListTag();
        echoVoiceLines.forEach((player, files) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.put("Files", files.copy());
            voices.add(entry);
        });
        tag.put("EchoVoiceLines", voices);
        return tag;
    }

    boolean hasEchoReference(UUID player, int reference) {
        if (reference < 0 || reference > 2) throw new IllegalArgumentException("Invalid Echo reference");
        return (echoReferences.getOrDefault(player, 0) & (1 << reference)) != 0;
    }

    boolean recordEchoReference(UUID player, int reference) {
        if (hasEchoReference(player, reference)) return false;
        echoReferences.merge(player, 1 << reference, (before, bit) -> (before | bit) & 7);
        setDirty();
        return true;
    }

    int echoReferenceCount(UUID player) {
        return Integer.bitCount(echoReferences.getOrDefault(player, 0) & 7);
    }

    boolean echoComplete(UUID player) {
        return echoReferenceCount(player) == 3;
    }

    List<String> echoVoiceFiles(UUID player) {
        CompoundTag files = echoVoiceLines.get(player);
        if (files == null) return List.of();
        return files.getAllKeys().stream()
                .filter(key -> files.contains(key, Tag.TAG_STRING))
                .map(files::getString)
                .filter(path -> !path.isBlank())
                .limit(3)
                .toList();
    }

    int nextEchoReference(UUID player) {
        for (int reference = 0; reference < 3; reference++)
            if (!hasEchoReference(player, reference)) return reference;
        return 3;
    }

    boolean activateEchoWell(BlockPos center) {
        BlockPos immutable = center.immutable();
        boolean discovered = knownEchoWells.add(immutable);
        if (!activatedEchoWells.add(immutable)) {
            if (discovered) setDirty();
            return false;
        }
        setDirty();
        return true;
    }

    void registerEchoWell(BlockPos center) {
        if (knownEchoWells.add(center.immutable())) setDirty();
    }

    boolean registerEchoExit(BlockPos origin, Direction direction) {
        if (echoExitStairs.putIfAbsent(origin.immutable(), direction) != null) return false;
        setDirty();
        return true;
    }

    int incrementFutureMidnights(UUID player) {
        int count = futureMidnights.merge(player, 1, Integer::sum);
        setDirty();
        return count;
    }

    void futureMidnights(UUID player, int count) {
        futureMidnights.put(player, count);
        setDirty();
    }

    enum State { UNDISCOVERED, ENCOUNTERED, ACTIVATED, EXPIRED }

    static final class Staircase {
        final BlockPos origin;
        final Rotation rotation;
        final int destination;
        BlockPos returnOrigin;
        State state = State.UNDISCOVERED;
        int removedBlocks;
        final Set<UUID> ascents = new HashSet<>();

        Staircase(BlockPos origin, Rotation rotation, int destination) {
            this.origin = origin;
            this.rotation = rotation;
            this.destination = destination;
        }

        BlockPos position(int x, int y, int z) {
            return new BlockPos(x, y, z).rotate(rotation).offset(origin);
        }

        boolean encounter() {
            if (state != State.UNDISCOVERED) return false;
            state = State.ENCOUNTERED;
            return true;
        }

        boolean activate() {
            if (state != State.ENCOUNTERED) return false;
            state = State.ACTIVATED;
            ascents.clear();
            return true;
        }

        boolean expire(boolean anyoneNearby) {
            if (state != State.ENCOUNTERED || anyoneNearby) return false;
            state = State.EXPIRED;
            ascents.clear();
            return true;
        }
    }
}
