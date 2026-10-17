package com.notmjonter.stairs;

/** Pure terrain-profile math shared by world generation and bootstrap-free tests. */
final class TickstormTerrain {
    static final int SEA_LEVEL = 63;

    private TickstormTerrain() {}

    static int flattenedSurface(int vanillaSurface) {
        return SEA_LEVEL + Math.clamp(Math.floorDiv(vanillaSurface - SEA_LEVEL, 4), -10, 24);
    }
}
