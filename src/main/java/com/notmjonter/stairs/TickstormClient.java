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

/** Purple, close Overworld fog used when no shader pack supplies the haze. */
@EventBusSubscriber(modid = Stairs.MODID, value = Dist.CLIENT)
public final class TickstormClient {
    private TickstormClient() {}

    @SubscribeEvent
    public static void dimensions(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "tickstorm"),
                new DimensionSpecialEffects(128, true, DimensionSpecialEffects.SkyType.NORMAL, false, false) {
                    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
                        Vec3 purple = new Vec3(0.19, 0.07, 0.28).scale(0.55 + brightness * 0.45);
                        return color.scale(0.2).add(purple.scale(0.8));
                    }
                    @Override public boolean isFoggyAt(int x, int y) { return true; }
                    @Override public float[] getSunriseColor(float time, float partialTick) { return null; }
                    @Override public void adjustLightmapColors(ClientLevel level, float partialTicks,
                            float skyDarken, float flicker, float skyLight, int blockX, int skyY, Vector3f colors) {
                        colors.mul(0.88f, 0.78f, 0.96f);
                    }
                });
    }
}
