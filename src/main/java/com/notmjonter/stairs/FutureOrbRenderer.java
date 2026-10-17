package com.notmjonter.stairs;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Camera-relative celestial geometry, not an entity or a through-walls HUD overlay. */
public final class FutureOrbRenderer {
    private static final int SEGMENTS = 128;
    private static final Vector3f CENTER = new Vector3f(0.6f, 0.22f, -0.77f).normalize().mul(100);
    private static final Vector3f RIGHT = new Vector3f(CENTER).cross(0, 1, 0).normalize();
    private static final Vector3f UP = new Vector3f(RIGHT).cross(CENTER).normalize();

    private FutureOrbRenderer() {}

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (StaircaseShaderBridge.orbActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc)) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float visibility = opacity(mc, partial);
        float radius = radius(mc, partial);
        float coverage = FutureContainmentState.skyCoverage(partial);

        // Sky pass has no terrain yet. Do not write depth: even very distant terrain
        // must cover the orb. Clouds/weather are rendered over it afterwards.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            BufferBuilder buffer = Tesselator.getInstance().begin(coverage > 0 ? VertexFormat.Mode.TRIANGLES : VertexFormat.Mode.TRIANGLE_FAN,
                    DefaultVertexFormat.POSITION_COLOR);
            Matrix4f matrix = event.getModelViewMatrix();
            // Plain event horizon only. Rings, glow and lensing belong in the
            // optional shader-pack integration, not the baseline silhouette.
            if (coverage > 0) {
                // Grow the angular boundary around the entire camera, including the opposite sky.
                double normalBoundary = 1 / Math.sqrt(1 + radius * radius / 10000.0);
                double angle = Math.acos(normalBoundary + (-1 - normalBoundary) * coverage);
                for (int ring = 0; ring < 16; ring++) for (int segment = 0; segment < SEGMENTS; segment++) {
                    double a = angle * ring / 16, b = angle * (ring + 1) / 16;
                    double u = Math.PI * 2 * segment / SEGMENTS, v = Math.PI * 2 * (segment + 1) / SEGMENTS;
                    sphereVertex(buffer, matrix, a, u, visibility);
                    sphereVertex(buffer, matrix, b, u, visibility);
                    sphereVertex(buffer, matrix, b, v, visibility);
                    sphereVertex(buffer, matrix, a, u, visibility);
                    sphereVertex(buffer, matrix, b, v, visibility);
                    sphereVertex(buffer, matrix, a, v, visibility);
                }
            } else {
                vertex(buffer, matrix, 0, 0, visibility);
                for (int i = 0; i <= SEGMENTS; i++) {
                    double angle = Math.PI * 2 * i / SEGMENTS;
                    vertex(buffer, matrix, radius * (float) Math.cos(angle),
                            radius * (float) Math.sin(angle), visibility);
                }
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static boolean visible(Minecraft mc) {
        if (mc.level == null || !mc.level.dimension().equals(FutureDimension.KEY)) return false;
        var camera = mc.gameRenderer.getMainCamera();
        if (camera.getFluidInCamera() != FogType.NONE) return false;
        return !(camera.getEntity() instanceof LivingEntity living)
                || !(living.hasEffect(MobEffects.BLINDNESS)
                    || living.hasEffect(MobEffects.DARKNESS) && !FutureFinaleState.active());
    }

    private static float dusk(Minecraft mc, float partial) {
        return FutureOrbAppearance.dusk(Math.cos(mc.level.getTimeOfDay(partial) * Math.PI * 2));
    }

    static double shaderOpacity() {
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc)) return 0;
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        return opacity(mc, partial);
    }

    private static float opacity(Minecraft mc, float partial) {
        float normal = FutureOrbAppearance.opacity(dusk(mc, partial), mc.level.getRainLevel(partial));
        if (FutureContainmentState.active()) return 1;
        return normal + (Math.max(normal, 0.85f) - normal) * FutureOrbReactionState.value(partial);
    }

    static double shaderRadius() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 15;
        return radius(mc, mc.getTimer().getGameTimeDeltaPartialTick(false));
    }

    static double shaderCoverage() {
        Minecraft mc = Minecraft.getInstance();
        return visible(mc) ? FutureContainmentState.skyCoverage(mc.getTimer().getGameTimeDeltaPartialTick(false)) : 0;
    }

    private static void sphereVertex(BufferBuilder buffer, Matrix4f matrix, double angle, double azimuth, float alpha) {
        float axial = (float) Math.cos(angle);
        float x = (float) (100 * Math.sin(angle) * Math.cos(azimuth));
        float y = (float) (100 * Math.sin(angle) * Math.sin(azimuth));
        buffer.addVertex(matrix, CENTER.x * axial + RIGHT.x * x + UP.x * y,
                        CENTER.y * axial + RIGHT.y * x + UP.y * y, CENTER.z * axial + RIGHT.z * x + UP.z * y)
                .setColor(0f, 0f, 0f, alpha);
    }

    private static float radius(Minecraft mc, float partial) {
        if (FutureContainmentState.active()) return FutureContainmentState.radius(partial);
        double time = Math.floorMod(mc.level.getDayTime(), 24000);
        if (mc.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)
                && mc.level.tickRateManager().runsNormally()) {
            float rate = mc.level.getDayTimePerTick();
            // Interpolate between solar ticks, especially during the 40x cycle.
            time += partial * Math.min(rate < 0 ? 1 : rate, 24000 - time);
        }
        return FutureOrbAppearance.radius(time) * FutureFinaleState.scale(partial)
                * (FutureFinaleState.active() ? 1 : FutureOrbReactionState.scale(partial));
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float alpha) {
        buffer.addVertex(matrix, CENTER.x + RIGHT.x * x + UP.x * y,
                        CENTER.y + RIGHT.y * x + UP.y * y, CENTER.z + RIGHT.z * x + UP.z * y)
                .setColor(0f, 0f, 0f, alpha);
    }
}
