package com.notmjonter.stairs;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/** Shared materials for destinations other than Minecraft 33.X. */
public final class DimensionBlocks {
    public static final DeferredBlock<Block> INCOMPLETE_SOLAR_MATTER = block(
            "incomplete_solar_matter", Blocks.YELLOW_CONCRETE);
    public static final DeferredBlock<Block> ECHO_STONE = block("echo_stone", Blocks.STONE, EchoSounds.STONE);
    public static final DeferredBlock<Block> DARK_ECHO_STONE = block("dark_echo_stone", Blocks.DEEPSLATE, EchoSounds.STONE);
    public static final DeferredBlock<StairBlock> ECHO_STONE_STAIRS = stairs("echo_stone_stairs", ECHO_STONE);
    public static final DeferredBlock<SlabBlock> ECHO_STONE_SLAB = slab("echo_stone_slab");
    public static final DeferredBlock<Block> ECHO_STONE_BRICKS = block("echo_stone_bricks", Blocks.STONE_BRICKS, EchoSounds.STONE);
    public static final DeferredBlock<StairBlock> ECHO_STONE_BRICK_STAIRS = stairs("echo_stone_brick_stairs", ECHO_STONE_BRICKS);
    public static final DeferredBlock<SlabBlock> ECHO_STONE_BRICK_SLAB = slab("echo_stone_brick_slab");
    public static final DeferredBlock<Block> POLISHED_ECHO_STONE = block("polished_echo_stone", Blocks.STONE, EchoSounds.STONE);
    public static final DeferredBlock<StairBlock> POLISHED_ECHO_STONE_STAIRS = stairs("polished_echo_stone_stairs", POLISHED_ECHO_STONE);
    public static final DeferredBlock<SlabBlock> POLISHED_ECHO_STONE_SLAB = slab("polished_echo_stone_slab");
    public static final DeferredBlock<Block> CHISELED_ECHO_STONE = block("chiseled_echo_stone", Blocks.CHISELED_STONE_BRICKS, EchoSounds.STONE);
    public static final DeferredBlock<Block> ECHO_GRASS = block("echo_grass", Blocks.CRIMSON_NYLIUM, EchoSounds.GRASS);
    public static final DeferredBlock<EchoLanternBlock> ECHO_LANTERN = FutureBlocks.BLOCKS.register("echo_lantern",
            () -> new EchoLanternBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLOWSTONE)
                    .sound(EchoSounds.LANTERN).lightLevel(state -> state.getValue(EchoLanternBlock.POWER))));
    public static final DeferredBlock<Block> RESONANT_ORE = block("resonant_ore", Blocks.IRON_ORE, EchoSounds.STONE);
    public static final DeferredBlock<Block> COMPACTED_DATA = block("compacted_data", Blocks.GRAY_CONCRETE);
    public static final DeferredBlock<Block> TICKSTONE = block("tickstone", Blocks.PURPLE_CONCRETE);
    public static final DeferredBlock<Block> COUNTERFEIT_CLOUD = block(
            "counterfeit_cloud", Blocks.LIGHT_BLUE_CONCRETE);
    public static final DeferredBlock<Block> MIRROR_SUBSTRATE = block("mirror_substrate", Blocks.BLACK_CONCRETE);

    public static final DeferredItem<BlockItem> INCOMPLETE_SOLAR_MATTER_ITEM = item(INCOMPLETE_SOLAR_MATTER);
    public static final DeferredItem<BlockItem> ECHO_STONE_ITEM = item(ECHO_STONE);
    public static final DeferredItem<BlockItem> DARK_ECHO_STONE_ITEM = item(DARK_ECHO_STONE);
    public static final DeferredItem<BlockItem> ECHO_STONE_STAIRS_ITEM = item(ECHO_STONE_STAIRS);
    public static final DeferredItem<BlockItem> ECHO_STONE_SLAB_ITEM = item(ECHO_STONE_SLAB);
    public static final DeferredItem<BlockItem> ECHO_STONE_BRICKS_ITEM = item(ECHO_STONE_BRICKS);
    public static final DeferredItem<BlockItem> ECHO_STONE_BRICK_STAIRS_ITEM = item(ECHO_STONE_BRICK_STAIRS);
    public static final DeferredItem<BlockItem> ECHO_STONE_BRICK_SLAB_ITEM = item(ECHO_STONE_BRICK_SLAB);
    public static final DeferredItem<BlockItem> POLISHED_ECHO_STONE_ITEM = item(POLISHED_ECHO_STONE);
    public static final DeferredItem<BlockItem> POLISHED_ECHO_STONE_STAIRS_ITEM = item(POLISHED_ECHO_STONE_STAIRS);
    public static final DeferredItem<BlockItem> POLISHED_ECHO_STONE_SLAB_ITEM = item(POLISHED_ECHO_STONE_SLAB);
    public static final DeferredItem<BlockItem> CHISELED_ECHO_STONE_ITEM = item(CHISELED_ECHO_STONE);
    public static final DeferredItem<BlockItem> ECHO_GRASS_ITEM = item(ECHO_GRASS);
    public static final DeferredItem<BlockItem> ECHO_LANTERN_ITEM = item(ECHO_LANTERN);
    public static final DeferredItem<BlockItem> RESONANT_ORE_ITEM = item(RESONANT_ORE);
    public static final DeferredItem<net.minecraft.world.item.Item> RESONANCE_SHARD =
            FutureBlocks.ITEMS.registerSimpleItem("resonance_shard");
    public static final DeferredItem<BlockItem> COMPACTED_DATA_ITEM = item(COMPACTED_DATA);
    public static final DeferredItem<BlockItem> TICKSTONE_ITEM = item(TICKSTONE);
    public static final DeferredItem<BlockItem> COUNTERFEIT_CLOUD_ITEM = item(COUNTERFEIT_CLOUD);
    public static final DeferredItem<BlockItem> MIRROR_SUBSTRATE_ITEM = item(MIRROR_SUBSTRATE);

    private DimensionBlocks() {}

    public static void initialize() {} // Load all holders before the shared registers fire.

    public static BlockItem itemForDimension(int dimension) {
        return switch (dimension) {
            case 2 -> INCOMPLETE_SOLAR_MATTER_ITEM.get();
            case 3 -> ECHO_STONE_ITEM.get();
            case 4 -> COMPACTED_DATA_ITEM.get();
            case 5 -> TICKSTONE_ITEM.get();
            case 6 -> COUNTERFEIT_CLOUD_ITEM.get();
            case 7 -> MIRROR_SUBSTRATE_ITEM.get();
            default -> throw new IllegalArgumentException("No representative block for dimension " + dimension);
        };
    }

    private static DeferredBlock<Block> block(String name, Block source) {
        return FutureBlocks.BLOCKS.register(name,
                () -> new Block(BlockBehaviour.Properties.ofFullCopy(source)));
    }

    private static DeferredItem<BlockItem> item(DeferredBlock<? extends Block> block) {
        return FutureBlocks.ITEMS.registerSimpleBlockItem(block);
    }

    private static DeferredBlock<StairBlock> stairs(String name, DeferredBlock<Block> base) {
        return FutureBlocks.BLOCKS.register(name, () -> new StairBlock(base.get().defaultBlockState(),
                BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_STAIRS).sound(EchoSounds.STONE)));
    }

    private static DeferredBlock<SlabBlock> slab(String name) {
        return FutureBlocks.BLOCKS.register(name, () -> new SlabBlock(
                BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).sound(EchoSounds.STONE)));
    }

    private static DeferredBlock<Block> block(String name, Block source, net.minecraft.world.level.block.SoundType sounds) {
        return FutureBlocks.BLOCKS.register(name,
                () -> new Block(BlockBehaviour.Properties.ofFullCopy(source).sound(sounds)));
    }
}
