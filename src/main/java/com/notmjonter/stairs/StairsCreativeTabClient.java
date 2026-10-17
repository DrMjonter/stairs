package com.notmjonter.stairs;

import de.Roboter007.moderntabs.ModernTabs;
import de.Roboter007.moderntabs.section.item.SectionedItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = Stairs.MODID, value = Dist.CLIENT)
public final class StairsCreativeTabClient {
    private StairsCreativeTabClient() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ModernTabs.configureTab(StairsCreativeTab.MAIN.get(), new ModernTabs.TabDesign().sectionsEnabled(true));
            for (int dimension = 1; dimension <= StaircaseData.TOTAL_DESTINATIONS; dimension++) {
                var section = StairsCreativeTab.section(dimension);
                StairsCreativeTab.items(dimension).forEach(item -> SectionedItems.addItem(section, item));
            }
        });
    }
}
