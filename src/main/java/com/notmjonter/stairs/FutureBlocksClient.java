package com.notmjonter.stairs;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.FoliageColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = Stairs.MODID, value = Dist.CLIENT)
public final class FutureBlocksClient {
    private FutureBlocksClient() {}

    @SubscribeEvent
    public static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> tintIndex != 0 ? -1
                : level != null && pos != null ? BiomeColors.getAverageGrassColor(level, pos)
                : GrassColor.getDefaultColor(), FutureBlocks.TURF.get());
        event.register((state, level, pos, tintIndex) -> tintIndex != 0 ? -1
                : level != null && pos != null ? BiomeColors.getAverageFoliageColor(level, pos)
                : FoliageColor.getDefaultColor(), PlasticBlocks.LEAVES.get());
    }

    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0 ? GrassColor.getDefaultColor() : -1,
                FutureBlocks.TURF_ITEM.get());
        event.register((stack, tintIndex) -> tintIndex == 0 ? FoliageColor.getDefaultColor() : -1,
                PlasticBlocks.LEAVES.get());
    }
}
