package com.notmjonter.stairs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Registry and lifecycle for composable client-side dimension effects. */
public final class ClientDimensionEffects {
    private static final Map<ResourceKey<Level>, State> EFFECTS = new LinkedHashMap<>();
    private static ResourceKey<Level> currentDimension;

    static {
        register(new FutureEffect());
        register(new UnfinishedSunEffect());
        register(new MirrorEffect());
        register(new EchoBasinEffect());
        register(new TickstormEffect());
    }

    private ClientDimensionEffects() {}

    public static void register(ClientDimensionEffect effect) {
        if (EFFECTS.putIfAbsent(effect.dimension(), new State(effect)) != null) {
            throw new IllegalArgumentException("A client dimension effect is already registered for "
                    + effect.dimension().location());
        }
    }

    public static void tick(Minecraft minecraft) {
        ResourceKey<Level> next = minecraft.level == null ? null : minecraft.level.dimension();
        if (next != currentDimension) changeDimension(minecraft, next);
        if (minecraft.isPaused()) return;

        for (State state : EFFECTS.values()) {
            state.previous = state.current;
            int duration = state.target > state.current ? state.effect.fadeInTicks() : state.effect.fadeOutTicks();
            float step = duration <= 0 ? 1 : 1f / duration;
            state.current = Math.clamp(state.current + Math.copySign(step, state.target - state.current),
                    Math.min(state.current, state.target), Math.max(state.current, state.target));
            if (state.previous > 0 || state.current > 0 || state.target > 0) {
                state.effect.tick(minecraft, state.current);
            }
        }
    }

    public static void reset(Minecraft minecraft) {
        if (currentDimension != null) changeDimension(minecraft, null);
        for (State state : EFFECTS.values()) {
            state.target = state.current = state.previous = 0;
            state.effect.tick(minecraft, 0);
        }
    }

    private static void changeDimension(Minecraft minecraft, ResourceKey<Level> next) {
        ResourceKey<Level> previous = currentDimension;
        State old = EFFECTS.get(previous);
        if (old != null) {
            old.target = 0;
            old.effect.onExit(minecraft, next);
        }
        currentDimension = next;
        State entered = EFFECTS.get(next);
        if (entered != null) {
            entered.target = 1;
            entered.effect.onEnter(minecraft, previous);
        }
    }

    public static boolean isCurrent(ResourceKey<Level> dimension) {
        return dimension.equals(currentDimension);
    }

    public static float intensity(ResourceKey<Level> dimension, float partialTick) {
        State state = EFFECTS.get(dimension);
        if (state == null) return 0;
        return state.previous + (state.current - state.previous) * Math.clamp(partialTick, 0, 1);
    }

    public static void renderHud(Minecraft minecraft, GuiGraphics graphics, DeltaTracker partialTick) {
        float partial = partialTick.getGameTimeDeltaPartialTick(false);
        for (State state : EFFECTS.values()) {
            float intensity = state.previous + (state.current - state.previous) * Math.clamp(partial, 0, 1);
            if (intensity > 0) state.effect.renderHud(minecraft, graphics, partialTick, intensity);
        }
    }

    public static Map<String, DoubleSupplier> shaderUniforms() {
        Map<String, DoubleSupplier> uniforms = new LinkedHashMap<>();
        for (State state : EFFECTS.values()) {
            String name = state.effect.shaderUniform();
            if (name == null || name.isBlank()) continue;
            if (uniforms.putIfAbsent(name, () -> intensity(state.effect.dimension(), framePartialTick())) != null) {
                throw new IllegalStateException("Duplicate dimension shader uniform " + name);
            }
        }
        return Map.copyOf(uniforms);
    }

    private static float framePartialTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0 : minecraft.getTimer().getGameTimeDeltaPartialTick(false);
    }

    private static final class State {
        final ClientDimensionEffect effect;
        float target;
        float previous;
        float current;

        State(ClientDimensionEffect effect) { this.effect = effect; }
    }

    private record ShaderEffect(ResourceKey<Level> dimension, String shaderUniform) implements ClientDimensionEffect {}

    private static final class FutureEffect implements ClientDimensionEffect {
        @Override public ResourceKey<Level> dimension() { return FutureDimension.KEY; }
        @Override public String shaderUniform() { return "stairsFuture"; }

        @Override
        public void tick(Minecraft minecraft, float intensity) {
            boolean here = minecraft.level != null
                    && minecraft.level.dimension().equals(FutureDimension.KEY);
            FutureFinaleState.tick(here);
            FutureOrbReactionState.tick(here);
            float reverb = here ? StaircaseAudio.futureReverbStrength(minecraft.level.getDayTime()) : 0;
            StaircaseAudio.futureIntensity(intensity * reverb);
            FutureMidnightDistortion.tick(minecraft, intensity);
            FutureOrbAudio.tick(minecraft, intensity);
        }

        @Override
        public void renderHud(Minecraft minecraft, GuiGraphics graphics, DeltaTracker partialTick, float intensity) {
            int color = Math.round(255 * intensity) << 24 | 0xffffff;
            graphics.drawString(minecraft.font, net.minecraft.network.chat.Component.translatable("hud.mjontersstairs.future_version"), 4,
                    graphics.guiHeight() - minecraft.font.lineHeight - 4, color, true);
        }
    }

    private static final class EchoBasinEffect implements ClientDimensionEffect {
        @Override public ResourceKey<Level> dimension() { return EchoBasinDimension.KEY; }
        @Override public String shaderUniform() { return "stairsEchoBasin"; }
        @Override public int fadeInTicks() { return 30; }
        @Override public int fadeOutTicks() { return 30; }
        @Override public void tick(Minecraft minecraft, float intensity) {
            StaircaseAudio.echoIntensity(intensity * EchoBasinProgress.intensity());
            EchoBasinAmbience.tick(minecraft, intensity);
        }
        @Override public void onExit(Minecraft minecraft, ResourceKey<Level> next) {
            EchoBasinProgress.receive(0);
        }
    }

    private record UnfinishedSunEffect() implements ClientDimensionEffect {
        @Override public ResourceKey<Level> dimension() { return UnfinishedSunDimension.KEY; }
        @Override public String shaderUniform() { return "stairsUnfinishedSun"; }
        @Override public int fadeInTicks() { return 20; }
        @Override public int fadeOutTicks() { return 20; }
    }

    private static final class TickstormEffect implements ClientDimensionEffect {
        @Override public ResourceKey<Level> dimension() { return TickstormDimension.KEY; }
        @Override public String shaderUniform() { return "stairsTickstorm"; }
        @Override public int fadeInTicks() { return 40; }
        @Override public int fadeOutTicks() { return 40; }
        @Override public void tick(Minecraft minecraft, float intensity) {
            TickstormClientState.tick(minecraft, intensity);
        }
        @Override public void onExit(Minecraft minecraft, ResourceKey<Level> next) {
            TickstormClientState.reset();
        }
    }

    private static final class MirrorEffect implements ClientDimensionEffect {
        @Override public ResourceKey<Level> dimension() { return MirrorDimension.KEY; }
        @Override public String shaderUniform() { return "stairsMirror"; }
        @Override public int fadeInTicks() { return 25; }
        @Override public int fadeOutTicks() { return 25; }
        @Override public void tick(Minecraft minecraft, float intensity) { StaircaseAudio.mirrorIntensity(intensity); }
        @Override public void onExit(Minecraft minecraft, ResourceKey<Level> next) {
            if (fadeOutTicks() == 0) StaircaseAudio.resetMirror();
        }
    }
}
