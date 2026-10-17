package com.notmjonter.stairs;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Common-side tab registration; ModernTabs presentation is configured only on clients. */
public final class StairsCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Stairs.MODID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("stairs",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mjontersstairs.stairs"))
                    .icon(() -> FutureBlocks.TURF_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        for (int dimension = 1; dimension <= StaircaseData.TOTAL_DESTINATIONS; dimension++) {
                            items(dimension).forEach(output::accept);
                        }
                    }).build());

    private StairsCreativeTab() {}

    public static ResourceLocation section(int dimension) {
        if (dimension < 1 || dimension > StaircaseData.TOTAL_DESTINATIONS) {
            throw new IllegalArgumentException("Invalid destination " + dimension);
        }
        return ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "dimension_" + dimension);
    }

    /** Keep membership shared by the vanilla tab contents and ModernTabs section mapping. */
    public static List<ItemLike> items(int dimension) {
        List<ItemLike> items = new ArrayList<>();
        if (dimension == 1) {
            items.add(FutureBlocks.TURF_ITEM.get());
            items.add(FutureBlocks.BEDROCK_PLUS_ITEM.get());
            PlasticBlocks.ITEMS.forEach(item -> items.add(item.get()));
        } else {
            items.add(DimensionBlocks.itemForDimension(dimension));
            if (dimension == EchoBasinDimension.DESTINATION) {
                items.add(DimensionBlocks.DARK_ECHO_STONE_ITEM.get());
                items.add(DimensionBlocks.ECHO_STONE_STAIRS_ITEM.get());
                items.add(DimensionBlocks.ECHO_STONE_SLAB_ITEM.get());
                items.add(DimensionBlocks.ECHO_STONE_BRICKS_ITEM.get());
                items.add(DimensionBlocks.ECHO_STONE_BRICK_STAIRS_ITEM.get());
                items.add(DimensionBlocks.ECHO_STONE_BRICK_SLAB_ITEM.get());
                items.add(DimensionBlocks.POLISHED_ECHO_STONE_ITEM.get());
                items.add(DimensionBlocks.POLISHED_ECHO_STONE_STAIRS_ITEM.get());
                items.add(DimensionBlocks.POLISHED_ECHO_STONE_SLAB_ITEM.get());
                items.add(DimensionBlocks.CHISELED_ECHO_STONE_ITEM.get());
                items.add(DimensionBlocks.ECHO_GRASS_ITEM.get());
                items.add(DimensionBlocks.ECHO_LANTERN_ITEM.get());
                items.add(EchoLava.BUCKET.get());
                items.add(DimensionBlocks.RESONANT_ORE_ITEM.get());
                items.add(DimensionBlocks.RESONANCE_SHARD.get());
            }
        }
        return items;
    }
}
