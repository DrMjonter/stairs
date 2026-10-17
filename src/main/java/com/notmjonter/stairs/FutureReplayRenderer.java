package com.notmjonter.stairs;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = Stairs.MODID, value = Dist.CLIENT)
public final class FutureReplayRenderer extends HumanoidMobRenderer<FutureReplayEntity, PlayerModel<FutureReplayEntity>> {
    private static final ResourceLocation[] SKINS = {
        skin("clone_1"), skin("clone_2"), skin("clone_3"),
        skin("clone_4"), skin("clone_5"), skin("clone_6")
    };
    private final PlayerModel<FutureReplayEntity> steve;
    private final PlayerModel<FutureReplayEntity> alex;

    public FutureReplayRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        steve = model;
        alex = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override public ResourceLocation getTextureLocation(FutureReplayEntity entity) {
        ResourceLocation custom = customSkin(entity);
        if (custom != null) return custom;
        return ResourceLocation.withDefaultNamespace(entity.alex()
                ? "textures/entity/player/slim/alex.png" : "textures/entity/player/wide/steve.png");
    }

    private static ResourceLocation skin(String name) {
        return ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "textures/entity/future_replay/" + name + ".png");
    }

    private static ResourceLocation customSkin(FutureReplayEntity entity) {
        ResourceLocation skin = SKINS[entity.skinVariant()];
        return Minecraft.getInstance().getResourceManager().getResource(skin).isPresent() ? skin : null;
    }

    @Override public void render(FutureReplayEntity entity, float yaw, float partialTick,
            com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        // Every custom color variant shares the classic, four-pixel-wide arm template.
        model = customSkin(entity) != null ? steve : entity.alex() ? alex : steve;
        model.crouching = entity.isShiftKeyDown();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override protected boolean shouldShowName(FutureReplayEntity entity) { return false; }

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FutureReplays.CLONE.get(), FutureReplayRenderer::new);
    }
}
