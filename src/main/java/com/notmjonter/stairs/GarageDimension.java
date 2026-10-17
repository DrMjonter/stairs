package com.notmjonter.stairs;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GarageDimension {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "dimension_1"));
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Stairs.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Stairs.MODID);
    public static final DeferredBlock<Block> FLUORESCENT = BLOCKS.registerSimpleBlock("garage_fluorescent",
            BlockBehaviour.Properties.of().strength(3).lightLevel(state -> 10));
    static {
        GENERATORS.register("parking_garage", () -> GarageChunkGenerator.CODEC);
        GENERATORS.register("future_flat", () -> FutureChunkGenerator.CODEC);
        GENERATORS.register("unfinished_sun", () -> UnfinishedSunChunkGenerator.CODEC);
        GENERATORS.register("tickstorm", () -> TickstormChunkGenerator.CODEC);
        GENERATORS.register("garbage_collector", () -> GarbageCollectorChunkGenerator.CODEC);
    }
    private GarageDimension() {}
}
