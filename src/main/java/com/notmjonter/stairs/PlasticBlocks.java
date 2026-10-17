package com.notmjonter.stairs;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/** Molded imitation building materials, sharing the future block registries. */
public final class PlasticBlocks {
    public static final List<DeferredItem<BlockItem>> ITEMS = new ArrayList<>();
    public static final DeferredBlock<Block> PLANKS = simple("plastic_planks", Blocks.OAK_PLANKS);
    public static final DeferredBlock<RotatedPillarBlock> LOG = pillar("plastic_log", Blocks.OAK_LOG);
    public static final DeferredBlock<RotatedPillarBlock> WOOD = pillar("plastic_wood", Blocks.OAK_WOOD);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_LOG = pillar("stripped_plastic_log", Blocks.STRIPPED_OAK_LOG);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_WOOD = pillar("stripped_plastic_wood", Blocks.STRIPPED_OAK_WOOD);
    public static final DeferredBlock<StairBlock> STAIRS = add("plastic_stairs", () -> new StairBlock(PLANKS.get().defaultBlockState(), properties(Blocks.OAK_STAIRS)));
    public static final DeferredBlock<SlabBlock> SLAB = add("plastic_slab", () -> new SlabBlock(properties(Blocks.OAK_SLAB)));
    public static final DeferredBlock<FenceBlock> FENCE = add("plastic_fence", () -> new FenceBlock(properties(Blocks.OAK_FENCE)));
    public static final DeferredBlock<FenceGateBlock> FENCE_GATE = add("plastic_fence_gate", () -> new FenceGateBlock(WoodType.CHERRY, properties(Blocks.OAK_FENCE_GATE)));
    public static final DeferredBlock<ButtonBlock> BUTTON = add("plastic_button", () -> new ButtonBlock(BlockSetType.CHERRY, 30, properties(Blocks.OAK_BUTTON)));
    public static final DeferredBlock<PressurePlateBlock> PRESSURE_PLATE = add("plastic_pressure_plate", () -> new PressurePlateBlock(BlockSetType.CHERRY, properties(Blocks.OAK_PRESSURE_PLATE)));
    public static final DeferredBlock<DoorBlock> DOOR = add("plastic_door", () -> new DoorBlock(BlockSetType.CHERRY, properties(Blocks.OAK_DOOR)));
    public static final DeferredBlock<TrapDoorBlock> TRAPDOOR = add("plastic_trapdoor", () -> new TrapDoorBlock(BlockSetType.CHERRY, properties(Blocks.OAK_TRAPDOOR)));
    // Decorative molded foliage: biome-tinted like oak, with no decay or spreading.
    public static final DeferredBlock<Block> LEAVES = add("plastic_leaves", () -> new Block(
            BlockBehaviour.Properties.of().strength(0.2F).sound(SoundType.CHERRY_WOOD).noOcclusion()));
    public static final DeferredBlock<Block> COBBLESTONE = simple("plastic_cobblestone", Blocks.COBBLESTONE);
    public static final DeferredBlock<StairBlock> COBBLESTONE_STAIRS = add("plastic_cobblestone_stairs", () -> new StairBlock(COBBLESTONE.get().defaultBlockState(), properties(Blocks.COBBLESTONE_STAIRS)));
    public static final DeferredBlock<SlabBlock> COBBLESTONE_SLAB = add("plastic_cobblestone_slab", () -> new SlabBlock(properties(Blocks.COBBLESTONE_SLAB)));
    public static final DeferredBlock<WallBlock> COBBLESTONE_WALL = add("plastic_cobblestone_wall", () -> new WallBlock(properties(Blocks.COBBLESTONE_WALL)));
    public static final DeferredBlock<Block> MOSSY_COBBLESTONE = simple("mossy_plastic_cobblestone", Blocks.MOSSY_COBBLESTONE);
    public static final DeferredBlock<StairBlock> MOSSY_STAIRS = add("mossy_plastic_cobblestone_stairs", () -> new StairBlock(MOSSY_COBBLESTONE.get().defaultBlockState(), properties(Blocks.MOSSY_COBBLESTONE_STAIRS)));
    public static final DeferredBlock<SlabBlock> MOSSY_SLAB = add("mossy_plastic_cobblestone_slab", () -> new SlabBlock(properties(Blocks.MOSSY_COBBLESTONE_SLAB)));
    public static final DeferredBlock<WallBlock> MOSSY_WALL = add("mossy_plastic_cobblestone_wall", () -> new WallBlock(properties(Blocks.MOSSY_COBBLESTONE_WALL)));
    public static final DeferredBlock<FakeWheatBlock> FAKE_WHEAT = add("fake_wheat",
            () -> new FakeWheatBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT)));
    public static final DeferredBlock<RotatedPillarBlock> HAY_BALE = pillar("plastic_hay_bale", Blocks.HAY_BLOCK);

    private PlasticBlocks() {}
    public static void initialize() {} // Populate the shared registers before registration events.

    private static BlockBehaviour.Properties properties(Block source) {
        return BlockBehaviour.Properties.ofFullCopy(source).sound(SoundType.CHERRY_WOOD);
    }

    private static DeferredBlock<Block> simple(String name, Block source) {
        return add(name, () -> new Block(properties(source)));
    }

    private static DeferredBlock<RotatedPillarBlock> pillar(String name, Block source) {
        return add(name, () -> new RotatedPillarBlock(properties(source)));
    }

    private static <B extends Block> DeferredBlock<B> add(String name, Supplier<B> factory) {
        DeferredBlock<B> block = FutureBlocks.BLOCKS.register(name, factory);
        ITEMS.add(FutureBlocks.ITEMS.registerSimpleBlockItem(block));
        return block;
    }

    public static void strip(BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() != ItemAbilities.AXE_STRIP) return;
        var state = event.getState();
        if (state.is(LOG.get())) event.setFinalState(STRIPPED_LOG.get().withPropertiesOf(state));
        else if (state.is(WOOD.get())) event.setFinalState(STRIPPED_WOOD.get().withPropertiesOf(state));
    }
}
