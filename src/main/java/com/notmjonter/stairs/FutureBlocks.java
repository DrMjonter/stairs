package com.notmjonter.stairs;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Building materials for the future Minecraft dimension. */
public final class FutureBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Stairs.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Stairs.MODID);
    public static final DeferredBlock<Block> TURF = BLOCKS.registerSimpleBlock("turf",
            BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.6F).sound(SoundType.GRASS));
    public static final DeferredBlock<Block> BEDROCK_PLUS = BLOCKS.registerSimpleBlock("bedrock_plus",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK));
    public static final DeferredItem<BlockItem> TURF_ITEM = ITEMS.registerSimpleBlockItem(TURF);
    public static final DeferredItem<BlockItem> BEDROCK_PLUS_ITEM = ITEMS.registerSimpleBlockItem(BEDROCK_PLUS);

    private FutureBlocks() {}

}
