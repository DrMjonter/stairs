package com.notmjonter.stairs;

import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class FutureDimension {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "dimension_1"));
    public static final int SURFACE_Y = 64;
    private FutureDimension() {}

    private static final class Materials {
        static final Map<Block, Block> REPLACEMENTS = Map.ofEntries(
            Map.entry(Blocks.GRASS_BLOCK, FutureBlocks.TURF.get()),
            Map.entry(Blocks.BEDROCK, FutureBlocks.BEDROCK_PLUS.get()),
            Map.entry(Blocks.OAK_PLANKS, PlasticBlocks.PLANKS.get()),
            Map.entry(Blocks.OAK_LOG, PlasticBlocks.LOG.get()),
            Map.entry(Blocks.OAK_WOOD, PlasticBlocks.WOOD.get()),
            Map.entry(Blocks.STRIPPED_OAK_LOG, PlasticBlocks.STRIPPED_LOG.get()),
            Map.entry(Blocks.STRIPPED_OAK_WOOD, PlasticBlocks.STRIPPED_WOOD.get()),
            Map.entry(Blocks.OAK_STAIRS, PlasticBlocks.STAIRS.get()),
            Map.entry(Blocks.OAK_SLAB, PlasticBlocks.SLAB.get()),
            Map.entry(Blocks.OAK_FENCE, PlasticBlocks.FENCE.get()),
            Map.entry(Blocks.OAK_FENCE_GATE, PlasticBlocks.FENCE_GATE.get()),
            Map.entry(Blocks.OAK_BUTTON, PlasticBlocks.BUTTON.get()),
            Map.entry(Blocks.OAK_PRESSURE_PLATE, PlasticBlocks.PRESSURE_PLATE.get()),
            Map.entry(Blocks.OAK_DOOR, PlasticBlocks.DOOR.get()),
            Map.entry(Blocks.OAK_TRAPDOOR, PlasticBlocks.TRAPDOOR.get()),
            Map.entry(Blocks.OAK_LEAVES, PlasticBlocks.LEAVES.get()),
            Map.entry(Blocks.COBBLESTONE, PlasticBlocks.COBBLESTONE.get()),
            Map.entry(Blocks.COBBLESTONE_STAIRS, PlasticBlocks.COBBLESTONE_STAIRS.get()),
            Map.entry(Blocks.COBBLESTONE_SLAB, PlasticBlocks.COBBLESTONE_SLAB.get()),
            Map.entry(Blocks.COBBLESTONE_WALL, PlasticBlocks.COBBLESTONE_WALL.get()),
            Map.entry(Blocks.MOSSY_COBBLESTONE, PlasticBlocks.MOSSY_COBBLESTONE.get()),
            Map.entry(Blocks.MOSSY_COBBLESTONE_STAIRS, PlasticBlocks.MOSSY_STAIRS.get()),
            Map.entry(Blocks.MOSSY_COBBLESTONE_SLAB, PlasticBlocks.MOSSY_SLAB.get()),
            Map.entry(Blocks.MOSSY_COBBLESTONE_WALL, PlasticBlocks.MOSSY_WALL.get()),
            Map.entry(Blocks.FARMLAND, Blocks.BROWN_WOOL),
            Map.entry(Blocks.WHEAT, PlasticBlocks.FAKE_WHEAT.get()),
            Map.entry(Blocks.CARROTS, PlasticBlocks.FAKE_WHEAT.get()),
            Map.entry(Blocks.POTATOES, PlasticBlocks.FAKE_WHEAT.get()),
            Map.entry(Blocks.BEETROOTS, PlasticBlocks.FAKE_WHEAT.get()),
            Map.entry(Blocks.HAY_BLOCK, PlasticBlocks.HAY_BALE.get()));
    }

    public static BlockState material(BlockState original) {
        Block replacement = Materials.REPLACEMENTS.get(original.getBlock());
        return replacement == null ? original : replacement.withPropertiesOf(original);
    }
}
