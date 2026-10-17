package com.notmjonter.stairs;

/** Pure fragment layout shared by generation and terrain regression checks. */
final class GarbageCollectorTerrain {
    private GarbageCollectorTerrain() {}

    static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    static int kind(long seed) {
        int type = (int) Math.floorMod(seed, 6);
        // Replace former ocean cells with plains without reshuffling other fragments.
        return type == 5 ? 0 : type;
    }
    static boolean missing(long seed) { return Math.floorMod(mix(seed), 19) == 0; }
    static boolean empty(long seed, int x, int z) { return missing(seed) || x == 0 || z == 0; }

    static int surface(long seed, int x, int z) {
        int base = 48 + (int) Math.floorMod(seed >>> 8, 49);
        double phase = (seed & 65535) * 0.001;
        double hills = Math.sin(x * 0.19 + phase) * Math.cos(z * 0.17 - phase);
        int relief = kind(seed) == 3 ? 27 : 9;
        return base + (int) Math.round(hills * relief);
    }
}
