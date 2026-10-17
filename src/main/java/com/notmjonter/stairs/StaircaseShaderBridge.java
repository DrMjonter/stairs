package com.notmjonter.stairs;

import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import com.mojang.logging.LogUtils;

/** Optional Iris 1.8 uniform bridge; loaded only by the client mixin. */
public final class StaircaseShaderBridge {
    private static long lastUniformRead;
    private static long lastOrbUniformRead;
    private static boolean warned;
    private static final java.util.Set<Object> orbNotifiers = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static java.lang.reflect.Method pipelineManager, currentPipeline, frameNotifier;
    private StaircaseShaderBridge() {}

    public static void register(Object holder, Object notifier) {
        try {
            ClassLoader loader = holder.getClass().getClassLoader();
            Class<?> uniformHolder = Class.forName("net.irisshaders.iris.gl.uniform.UniformHolder", true, loader);
            Class<?> frequency = Class.forName("net.irisshaders.iris.gl.uniform.UniformUpdateFrequency", true, loader);
            Object perFrame = frequency.getField("PER_FRAME").get(null);
            var register = uniformHolder.getMethod("uniform1f", frequency, String.class, DoubleSupplier.class);
            register.invoke(holder, perFrame, "stairsAscent", (DoubleSupplier) StaircaseShaderBridge::intensity);
            register.invoke(holder, perFrame, "stairsGarage", (DoubleSupplier) () -> 0.0);
            for (var uniform : ClientDimensionEffects.shaderUniforms().entrySet()) {
                register.invoke(holder, perFrame, uniform.getKey(), uniform.getValue());
            }
            register.invoke(holder, perFrame, "stairsDistortionScale",
                    (DoubleSupplier) () -> Minecraft.getInstance().options.screenEffectScale().get());
            register.invoke(holder, perFrame, "stairsMidnight",
                    (DoubleSupplier) FutureMidnightDistortion::shaderValue);
            register.invoke(holder, perFrame, "stairsOrbReaction", (DoubleSupplier) () -> {
                Minecraft mc = Minecraft.getInstance();
                return mc.level == null ? 0 : FutureOrbReactionState.value(mc.getTimer().getGameTimeDeltaPartialTick(false));
            });
            register.invoke(holder, perFrame, "stairsOrbOpacity", (DoubleSupplier) StaircaseShaderBridge::orbOpacity);
            register.invoke(holder, perFrame, "stairsOrbRadius", (DoubleSupplier) FutureOrbRenderer::shaderRadius);
            register.invoke(holder, perFrame, "stairsOrbCoverage", (DoubleSupplier) FutureOrbRenderer::shaderCoverage);
            register.invoke(holder, perFrame, "stairsEchoSubmerged", (DoubleSupplier) EchoBasinClient::submerged);
            register.invoke(holder, perFrame, "stairsTickstormSurge",
                    (DoubleSupplier) TickstormClientState::shaderValue);

            // Identify actual compiled support, not a pack filename or a timed
            // heartbeat. This avoids duplicate orbs on the first frame and on reload.
            Class<?> locations = Class.forName("net.irisshaders.iris.gl.uniform.LocationalUniformHolder", true, loader);
            Class<?> type = Class.forName("net.irisshaders.iris.gl.uniform.UniformType", true, loader);
            if (locations.isInstance(holder) && notifier != null) {
                var location = (java.util.OptionalInt) locations.getMethod("location", String.class, type)
                        .invoke(holder, "stairsOrbOpacity", type.getField("FLOAT").get(null));
                if (location.isPresent()) {
                    pipelineManager = Class.forName("net.irisshaders.iris.Iris", true, loader).getMethod("getPipelineManager");
                    currentPipeline = Class.forName("net.irisshaders.iris.pipeline.PipelineManager", true, loader).getMethod("getPipelineNullable");
                    frameNotifier = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPipeline", true, loader).getMethod("getFrameUpdateNotifier");
                    orbNotifiers.add(notifier);
                }
            }
        } catch (ReflectiveOperationException exception) {
            if (!warned) {
                LogUtils.getLogger().warn("Stairs could not register its optional Iris uniform; using the normal screen fade", exception);
                warned = true;
            }
        }
    }

    public static boolean orbActive() {
        // Direct proof that an active shader program consumed the orb uniform.
        // Keep this longer than a slow frame, but short enough to restore the
        // geometry fallback promptly after disabling or switching shader packs.
        if (lastOrbUniformRead != 0 && System.nanoTime() - lastOrbUniformRead < 2_000_000_000L) return true;
        if (pipelineManager == null || orbNotifiers.isEmpty()) return false;
        try {
            Object pipeline = currentPipeline.invoke(pipelineManager.invoke(null));
            return pipeline != null && orbNotifiers.contains(frameNotifier.invoke(pipeline));
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static double orbOpacity() {
        lastOrbUniformRead = System.nanoTime();
        return FutureOrbRenderer.shaderOpacity();
    }

    private static double intensity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || mc.player.isSpectator()
                || mc.getCameraEntity() != mc.player) return 0;
        lastUniformRead = System.nanoTime();
        return AscentVisualState.value(mc.getTimer().getGameTimeDeltaPartialTick(false));
    }

    public static boolean active() {
        return lastUniformRead != 0 && System.nanoTime() - lastUniformRead < 250_000_000L;
    }
}
