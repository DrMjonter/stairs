package com.notmjonter.stairs;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Destination five: a flattened Overworld reconstruction under permanent rain. */
public final class TickstormDimension {
    public static final int DESTINATION = 5;
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "dimension_5"));

    private TickstormDimension() {}
}
