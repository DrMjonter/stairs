package com.notmjonter.stairs;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Echo Basin uses vanilla noise generation with dimension-specific data. */
public final class EchoBasinDimension {
    public static final int DESTINATION = 3;
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "dimension_3"));

    private EchoBasinDimension() {}
}
