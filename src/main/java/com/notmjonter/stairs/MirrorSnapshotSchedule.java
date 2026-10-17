package com.notmjonter.stairs;

import java.util.random.RandomGenerator;

/** Wall-clock UTC days, independent of Minecraft time and tick rate. */
final class MirrorSnapshotSchedule {
    static final long DAY_MILLIS = 86_400_000L;
    private MirrorSnapshotSchedule() {}

    static long day(long now) { return Math.floorDiv(now, DAY_MILLIS); }

    static long first(long now, RandomGenerator random) {
        long end = Math.multiplyExact(day(now) + 1, DAY_MILLIS);
        return now + random.nextLong(Math.max(1, end - now));
    }

    static long afterSuccess(long now, RandomGenerator random) {
        return Math.multiplyExact(day(now) + 1, DAY_MILLIS) + random.nextLong(DAY_MILLIS);
    }

    static boolean due(long now, long next, long lastSuccess) {
        return now >= next && (lastSuccess == 0 || day(now) > day(lastSuccess));
    }
}
