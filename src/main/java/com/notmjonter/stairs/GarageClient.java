package com.notmjonter.stairs;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionTransitionScreenEvent;

@EventBusSubscriber(modid = Stairs.MODID, value = Dist.CLIENT)
public final class GarageClient {
    private GarageClient() {}
    @SubscribeEvent
    public static void screens(RegisterDimensionTransitionScreenEvent event) {
        event.registerIncomingEffect(GarageDimension.KEY, BlackArrivalScreen::new);
        event.registerIncomingEffect(EchoBasinDimension.KEY, BlackArrivalScreen::new);
        event.registerIncomingEffect(UnfinishedSunDimension.KEY, BlackArrivalScreen::new);
        event.registerIncomingEffect(MirrorDimension.KEY, BlackArrivalScreen::new);
        event.registerOutgoingEffect(MirrorDimension.KEY, BlackArrivalScreen::new);
        event.registerOutgoingEffect(GarageDimension.KEY, BlackArrivalScreen::new);
        event.registerOutgoingEffect(EchoBasinDimension.KEY, BlackArrivalScreen::new);
        event.registerOutgoingEffect(UnfinishedSunDimension.KEY, BlackArrivalScreen::new);
    }

    private static final class BlackArrivalScreen extends ReceivingLevelScreen {
        BlackArrivalScreen(BooleanSupplier ready, Reason reason) {
            super(ready, reason);
            GarageTransitionState.cover();
        }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            graphics.fill(0, 0, width, height, 0xff000000);
        }
        @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            graphics.fill(0, 0, width, height, 0xff000000);
        }
        @Override public void onClose() {
            GarageTransitionState.cover();
            GarageTransitionState.release();
            super.onClose();
        }
    }
}
