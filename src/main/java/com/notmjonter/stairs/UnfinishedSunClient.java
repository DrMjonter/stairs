package com.notmjonter.stairs;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Vector3f;

/** Fixed-noon sky with a nearly black unlit baseline; real block light reveals visited terrain. */
@EventBusSubscriber(modid = Stairs.MODID, value = Dist.CLIENT)
public final class UnfinishedSunClient {
    private UnfinishedSunClient() {}

    @SubscribeEvent
    public static void dimensions(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "unfinished_sun"),
                new DimensionSpecialEffects(192, true, DimensionSpecialEffects.SkyType.NORMAL, false, false) {
                    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
                        return color.scale(0.16 + brightness * 0.24);
                    }
                    @Override public boolean isFoggyAt(int x, int y) { return false; }
                    @Override public float[] getSunriseColor(float time, float partialTick) { return null; }
                    @Override public void adjustLightmapColors(ClientLevel level, float partialTicks,
                            float skyDarken, float flicker, float skyLight, int blockX, int skyY, Vector3f colors) {
                        float blockLight = blockX / 15f;
                        float shadow = 0.08f + 0.92f * blockLight * blockLight;
                        colors.mul(shadow);
                    }
                });
    }
}
