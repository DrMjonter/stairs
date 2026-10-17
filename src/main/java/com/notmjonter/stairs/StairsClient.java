package com.notmjonter.stairs;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Stairs.MODID, value = Dist.CLIENT)
public final class StairsClient {
    private static boolean containmentMessageShown;
    private StairsClient() {}

    @SubscribeEvent
    public static void celestialOrb(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        FutureOrbRenderer.render(event);
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent event) {
        if (GarageTransitionState.holding()) {
            event.getInput().forwardImpulse = 0;
            event.getInput().leftImpulse = 0;
            event.getInput().jumping = false;
            return;
        }
        if (event.getEntity().hasEffect(StaircaseEffects.WEIGHT)) {
            event.getInput().jumping = false;
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) FutureContainmentState.reset();
        else if (!mc.isPaused()) FutureContainmentState.tick();
        FutureOrbAudio.tickContainment(mc);
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || mc.player.isSpectator()) {
            AscentVisualState.reset();
            ClientDimensionEffects.reset(mc);
            GarageTransitionState.reset();
            FutureOrbReactionState.reset();
        } else {
            ClientDimensionEffects.tick(mc);
            if (!mc.isPaused()) {
                AscentVisualState.tick();
                GarageTransitionState.tick();
            }
        }
        boolean showMessage = mc.level != null && mc.player != null
                && mc.level.dimension().equals(FutureDimension.KEY) && FutureContainmentState.showText();
        if (showMessage) {
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable(
                    "message.mjontersstairs.containment_error").withStyle(net.minecraft.ChatFormatting.RED), false);
        } else if (containmentMessageShown) {
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.empty(), false);
        }
        containmentMessageShown = showMessage;
    }

    private static float intensity(double partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.getCameraEntity() == mc.player && mc.player.isAlive() && !mc.player.isSpectator()
                ? AscentVisualState.value((float) partialTick) : 0;
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        float amount = intensity(event.getPartialTick());
        float midnight = FutureMidnightDistortion.value((float) event.getPartialTick());
        midnight = Math.max(midnight, FutureContainmentState.distortion((float) event.getPartialTick()));
        float reaction = FutureOrbReactionState.value((float) event.getPartialTick());
        Minecraft mc = Minecraft.getInstance();
        if (amount == 0 && midnight == 0 && reaction == 0 || mc.player == null) return;
        double time = (mc.player.tickCount + event.getPartialTick()) / 20.0;
        double strength = amount * amount * mc.options.screenEffectScale().get();
        event.setRoll(event.getRoll() + (float) (Math.sin(time * 1.7) * 3.5 * strength));
        event.setPitch(event.getPitch() + (float) (Math.sin(time * 1.1) * 1.2 * strength));
        event.setYaw(event.getYaw() + (float) (Math.cos(time * 0.9) * 0.8 * strength));
        float pulse = (float) (midnight * mc.options.screenEffectScale().get());
        event.setRoll(event.getRoll() + (float) Math.sin(time * 31) * 7 * pulse);
        event.setPitch(event.getPitch() + (float) Math.sin(time * 23) * 2.2f * pulse);
        event.setYaw(event.getYaw() + (float) Math.cos(time * 27) * 2.8f * pulse);
        float tremor = (float) (reaction * mc.options.screenEffectScale().get());
        event.setRoll(event.getRoll() + (float) Math.sin(time * 35) * 1.2f * tremor);
        event.setPitch(event.getPitch() + (float) Math.sin(time * 29) * 0.45f * tremor);
        event.setYaw(event.getYaw() + (float) Math.cos(time * 33) * 0.55f * tremor);
    }

    @SubscribeEvent
    public static void movementFov(ComputeFovModifierEvent event) {
        var player = event.getPlayer();
        if (player.isScoping()) return;
        var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) return;
        var slowdown = movement.getModifier(StaircaseEffects.WALK_SPEED_MODIFIER);
        // Effect status and attribute updates are separate packets. Compensate only
        // for the actual synchronized modifier, including while it is being removed.
        if (slowdown == null) return;
        double multiplier = 1 + slowdown.amount();
        if (multiplier <= 0) return;
        double walkingSpeed = player.getAbilities().getWalkingSpeed();
        if (walkingSpeed <= 0) return;
        double speed = movement.getValue();
        double withStairs = (speed / walkingSpeed + 1) / 2;
        double withoutStairs = (speed / multiplier / walkingSpeed + 1) / 2;
        // Undo only our speed penalty's contribution, preserving bow/sprint/potion
        // modifiers and the user's FOV Effects setting. The zoom below is independent.
        double correction = event.getFovModifier() * (withoutStairs / withStairs - 1);
        event.setNewFovModifier(event.getNewFovModifier()
                + (float) (correction * Minecraft.getInstance().options.fovEffectScale().get()));
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getCameraEntity() != mc.player || !mc.player.isAlive()
                || mc.player.isSpectator() || !event.usedConfiguredFov()) return;
        float amount = AscentVisualState.fovValue((float) event.getPartialTick());
        float midnight = FutureMidnightDistortion.value((float) event.getPartialTick());
        midnight = Math.max(midnight, FutureContainmentState.distortion((float) event.getPartialTick()));
        float reaction = FutureOrbReactionState.value((float) event.getPartialTick());
        if (amount == 0 && midnight == 0 && reaction == 0) return;
        double time = (mc.player.tickCount + event.getPartialTick()) / 20.0;
        double strength = amount * amount * mc.options.screenEffectScale().get();
        double zoom = 1 - 0.12 * amount * mc.options.fovEffectScale().get();
        double pulse = 1 + Math.sin(time * 25) * 0.075 * midnight * mc.options.screenEffectScale().get();
        double warningPulse = 1 + Math.sin(time * 18) * 0.012 * reaction * mc.options.screenEffectScale().get();
        event.setFOV(event.getFOV() * zoom * (1 + Math.sin(time * 1.4) * 0.035 * strength) * pulse * warningPulse);
    }

    @SubscribeEvent
    public static void darkness(RenderGuiEvent.Post event) {
        float amount = intensity(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        float transfer = GarageTransitionState.value(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        var graphics = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        float reaction = Math.max(FutureOrbReactionState.value(event.getPartialTick().getGameTimeDeltaPartialTick(false)),
                FutureContainmentState.distortion(event.getPartialTick().getGameTimeDeltaPartialTick(false)));
        if (reaction > 0 && mc.player != null) {
            double time = (mc.player.tickCount + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 20.0;
            float pulse = (float) (reaction * (0.65 + 0.35 * Math.cos(time * 18)) * mc.options.screenEffectScale().get());
            int w = graphics.guiWidth(), h = graphics.guiHeight();
            for (int band = 0; band < 16; band++) {
                int alpha = Math.round(55 * pulse * (1 - band / 16f) * (1 - band / 16f));
                int x0 = Math.round(w * band * 0.01f), x1 = Math.round(w * (band + 1) * 0.01f);
                int y0 = Math.round(h * band * 0.01f), y1 = Math.round(h * (band + 1) * 0.01f);
                graphics.fill(x0, 0, x1, h, alpha << 24);
                graphics.fill(w - x1, 0, w - x0, h, alpha << 24);
                graphics.fill(x1, y0, w - x1, y1, alpha << 24);
                graphics.fill(x1, h - y1, w - x1, h - y0, alpha << 24);
            }
        }
        if (amount > 0 || transfer > 0) {
            // The patched pack handles early sky darkening without hiding the stairs.
            // Keep the final blackout, and retain the original fade on unpatched packs.
            float fade = StaircaseShaderBridge.active() ? Math.clamp((amount - 0.8f) / 0.2f, 0, 1) : amount;
            int alpha = Math.round(Math.max(235 * fade, 255 * transfer));
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
        }
        // Dimension HUDs stay hidden beneath the transfer cover, then fade in
        // according to their own registered effect lifecycle.
        if (transfer < 0.01f) ClientDimensionEffects.renderHud(Minecraft.getInstance(), graphics, event.getPartialTick());
        float blackout = FutureContainmentState.blackout(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (blackout > 0) graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Math.round(blackout * 255) << 24);
    }
}
