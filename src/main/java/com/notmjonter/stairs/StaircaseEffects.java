package com.notmjonter.stairs;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class StaircaseEffects {
    public static final double WALK_SPEED_MULTIPLIER = 0.2;
    public static final ResourceLocation WALK_SPEED_MODIFIER = ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "staircase_weight");
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Stairs.MODID);
    public static final DeferredHolder<MobEffect, MobEffect> WEIGHT = EFFECTS.register("staircase_weight", Weight::new);

    private StaircaseEffects() {}

    private static final class Weight extends MobEffect {
        Weight() {
            super(MobEffectCategory.HARMFUL, 0x302d38);
            addAttributeModifier(Attributes.MOVEMENT_SPEED,
                    WALK_SPEED_MODIFIER,
                    WALK_SPEED_MULTIPLIER - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addAttributeModifier(Attributes.JUMP_STRENGTH,
                    ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "staircase_jump_lock"),
                    -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
    }

    static void apply(ServerPlayer player, float intensity) {
        boolean hadEffect = player.hasEffect(WEIGHT);
        if (intensity > 0) {
            var effect = player.getEffect(WEIGHT);
            if (effect == null || effect.getDuration() < 10) {
                player.addEffect(new MobEffectInstance(WEIGHT, 20, 0, false, false, false));
            }
        } else if (hadEffect) {
            player.removeEffect(WEIGHT);
        }
        if ((intensity > 0 && (!hadEffect || player.tickCount % 2 == 0)) || (hadEffect && intensity == 0)) {
            PacketDistributor.sendToPlayer(player, new AscentPayload(intensity));
        }
    }

    public static void jump(LivingEvent.LivingJumpEvent event) {
        // Jump Boost is added after the jump-strength attribute, so suppress its
        // remaining vertical impulse too. This runs on both logical sides.
        if (event.getEntity() instanceof Player player && player.hasEffect(WEIGHT)) {
            var velocity = player.getDeltaMovement();
            player.setDeltaMovement(velocity.x, Math.min(0, velocity.y), velocity.z);
        }
    }

    public static void playerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ((player.level().dimension() != Level.OVERWORLD && player.level().dimension() != MirrorDimension.KEY
                    && player.level().dimension() != EchoBasinDimension.KEY
                    && player.level().dimension() != TickstormDimension.KEY
                    && player.level().dimension() != GarbageCollectorDimension.KEY)
                    || !player.isAlive() || player.isSpectator())) {
            apply(player, 0);
        }
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(FutureContainment.Payload.TYPE, FutureContainment.Payload.CODEC,
                (payload, context) -> {
                    if (payload.age() == -2) {
                        FutureContainmentState.cover();
                        GarageTransitionState.cover();
                    } else if (payload.age() < 0) {
                        FutureContainmentState.reset();
                        GarageTransitionState.release();
                    } else {
                        FutureContainmentState.begin(payload.age());
                        var mc = net.minecraft.client.Minecraft.getInstance();
                        if (mc.level != null) FutureContainmentState.baseRadius(
                                FutureOrbAppearance.radius(mc.level.getDayTime()));
                        FutureOrbReactionState.reset();
                        FutureOrbAudio.containment(net.minecraft.client.Minecraft.getInstance());
                    }
                });
        event.registrar("1").playToClient(FutureVillagerReactions.Payload.TYPE, FutureVillagerReactions.Payload.CODEC,
                (payload, context) -> FutureOrbAudio.villagerWarning(
                        net.minecraft.client.Minecraft.getInstance(), payload.variant()));
        event.registrar("1").playToClient(GarageTransitionPayload.TYPE, GarageTransitionPayload.CODEC,
                (payload, context) -> { if (payload.begin()) GarageTransitionState.begin(); else GarageTransitionState.release(); });
        event.registrar("1").playToClient(AscentPayload.TYPE, AscentPayload.CODEC,
                (payload, context) -> AscentVisualState.receive(payload.intensity()));
        event.registrar("1").playToClient(FutureFinale.Payload.TYPE, FutureFinale.Payload.CODEC,
                (payload, context) -> {
                    if (payload.begin()) FutureFinaleState.begin();
                    else FutureFinaleState.reset();
                });
        event.registrar("1").playToClient(EchoBasinProgress.Payload.TYPE, EchoBasinProgress.Payload.CODEC,
                (payload, context) -> EchoBasinProgress.receive(payload.wells()));
        event.registrar("1").playToClient(EchoGuidancePayload.TYPE, EchoGuidancePayload.CODEC,
                (payload, context) -> EchoGuidanceWhisper.play(payload.well()));
        event.registrar("1").playToClient(TickstormWeather.Payload.TYPE, TickstormWeather.Payload.CODEC,
                (payload, context) -> TickstormClientState.receive(payload.active()));
    }

    public record AscentPayload(float intensity) implements CustomPacketPayload {
        public static final Type<AscentPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Stairs.MODID, "ascent"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AscentPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.FLOAT, AscentPayload::intensity, AscentPayload::new);
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
