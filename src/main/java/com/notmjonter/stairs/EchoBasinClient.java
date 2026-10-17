package com.notmjonter.stairs;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.joml.Vector3f;

/** Vanilla visuals and custom-fluid extensions; never loaded on a dedicated server. */
@EventBusSubscriber(modid = Stairs.MODID, value = Dist.CLIENT)
public final class EchoBasinClient {
    private EchoBasinClient() {}

    /** Camera submersion, not the player's feet: supports swimming and third-person views. */
    public static double submerged() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        var pos = net.minecraft.core.BlockPos.containing(eye);
        var state = mc.level.getFluidState(pos);
        return state.getFluidType() == EchoLava.TYPE.get()
                && eye.y < pos.getY() + state.getHeight(mc.level, pos) ? 1 : 0;
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(EchoLava.SOURCE.get(), RenderType.solid());
            ItemBlockRenderTypes.setRenderLayer(EchoLava.FLOWING.get(), RenderType.solid());
        });
    }

    @SubscribeEvent
    public static void dimensions(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "echo_basin"),
                new DimensionSpecialEffects(Float.NaN, true, DimensionSpecialEffects.SkyType.NONE, false, true) {
                    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) { return color; }
                    @Override public boolean isFoggyAt(int x, int y) { return true; }
                    @Override public float[] getSunriseColor(float time, float partialTick) { return null; }
                    @Override public void adjustLightmapColors(ClientLevel level, float partialTicks,
                            float skyDarken, float flicker, float skyLight, int x, int y, Vector3f colors) {
                        float light = Math.max(colors.x(), Math.max(colors.y(), colors.z()));
                        colors.set(light * 0.65f, light * 0.72f, light);
                    }
                });
    }

    @SubscribeEvent
    public static void fluids(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            private ResourceLocation sprite(String name, String fallback) {
                ResourceLocation custom = ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "block/" + name);
                ResourceLocation image = custom.withPath("textures/" + custom.getPath() + ".png");
                return Minecraft.getInstance().getResourceManager().getResource(image).isPresent()
                        ? custom : ResourceLocation.withDefaultNamespace("block/" + fallback);
            }
            @Override public ResourceLocation getStillTexture() { return sprite("echo_lava_still", "water_still"); }
            @Override public ResourceLocation getFlowingTexture() { return sprite("echo_lava_flow", "water_flow"); }
            @Override public int getTintColor() { return 0xff669dff; }
            @Override public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level,
                    int renderDistance, float darkenWorldAmount, Vector3f color) {
                return new Vector3f(0.10f, 0.16f, 0.42f);
            }
            @Override public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance,
                    float partialTick, float nearDistance, float farDistance, FogShape shape) {
                RenderSystem.setShaderFogStart(-0.1f);
                RenderSystem.setShaderFogEnd(1.5f);
            }
        }, EchoLava.TYPE.get());
    }
}
