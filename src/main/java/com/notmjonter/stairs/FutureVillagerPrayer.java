package com.notmjonter.stairs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Villagers prepare at 11:50 PM, then enter prayer state from midnight to 1:00 AM. */
public final class FutureVillagerPrayer {
    private static final int PREPARATION_TIME = 17833;
    private static final ResourceLocation FREEZE = ResourceLocation.fromNamespaceAndPath(
            Stairs.MODID, "future_villager_prayer");
    private static final AttributeModifier FREEZE_MODIFIER = new AttributeModifier(
            FREEZE, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();

    private FutureVillagerPrayer() {}

    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(FutureDimension.KEY)) return;
        int time = Math.floorMod(level.getDayTime(), 24000);
        boolean preparing = preparationTime(time);
        State state = STATES.computeIfAbsent(level, ignored -> new State());
        if (!preparing) {
            if (state.active) release(level, state);
            return;
        }

        state.active = true;
        boolean prayer = prayerTime(time);
        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof Villager villager) || !villager.isAlive()) continue;
            if (villager.isSleeping()) villager.stopSleeping();
            Entry entry = state.villagers.computeIfAbsent(villager.getUUID(), ignored -> new Entry());
            boolean outdoors = level.canSeeSky(villager.blockPosition().above());
            if (!outdoors) {
                unfreeze(villager);
                if (entry.target == null || villager.tickCount % 40 == 0) {
                    entry.target = findOutside(level, villager);
                }
                if (entry.target != null) villager.getNavigation().moveTo(entry.target.getX() + 0.5,
                        entry.target.getY(), entry.target.getZ() + 0.5, 0.65);
                continue;
            }

            villager.getNavigation().stop();
            if (prayer) {
                freeze(villager);
                var movement = villager.getDeltaMovement();
                villager.setDeltaMovement(0, movement.y, 0);
                // Horizontal and vertical distances match for a true 45-degree gaze.
                villager.lookAt(EntityAnchorArgument.Anchor.EYES,
                        villager.getEyePosition().add(60, Math.hypot(60, 77), -77));
                emitPrayerGlyph(level, villager);
            } else {
                unfreeze(villager);
            }
        }
    }

    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Villager && prayerTime(event.getLevel())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    public static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget() instanceof Villager && prayerTime(event.getLevel())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    static boolean prayerTime(Level level) {
        return level.dimension().equals(FutureDimension.KEY) && prayerTime(level.getDayTime());
    }

    static boolean prayerTime(long dayTime) {
        int time = Math.floorMod(dayTime, 24000);
        return time >= 18000 && time < 19000;
    }

    static boolean preparationTime(long dayTime) {
        int time = Math.floorMod(dayTime, 24000);
        return time >= PREPARATION_TIME && time < 19000;
    }

    private static BlockPos findOutside(ServerLevel level, Villager villager) {
        BlockPos origin = villager.blockPosition();
        for (int radius = 3; radius <= 16; radius++) {
            for (int step = -radius; step <= radius; step++) {
                BlockPos candidate = candidate(level, origin.getX() + step, origin.getZ() - radius);
                if (reachable(villager, candidate)) return candidate;
                candidate = candidate(level, origin.getX() + step, origin.getZ() + radius);
                if (reachable(villager, candidate)) return candidate;
                candidate = candidate(level, origin.getX() - radius, origin.getZ() + step);
                if (reachable(villager, candidate)) return candidate;
                candidate = candidate(level, origin.getX() + radius, origin.getZ() + step);
                if (reachable(villager, candidate)) return candidate;
            }
        }
        return null;
    }

    private static BlockPos candidate(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos position = new BlockPos(x, y, z);
        return level.canSeeSky(position) && level.getFluidState(position).isEmpty() ? position : null;
    }

    private static boolean reachable(Villager villager, BlockPos candidate) {
        if (candidate == null) return false;
        var path = villager.getNavigation().createPath(candidate, 0);
        return path != null && path.canReach();
    }

    private static void freeze(Villager villager) {
        var speed = villager.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(FREEZE)) speed.addTransientModifier(FREEZE_MODIFIER);
    }

    private static void unfreeze(Villager villager) {
        var speed = villager.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(FREEZE);
    }

    private static void emitPrayerGlyph(ServerLevel level, Villager villager) {
        // Stagger villagers so a gathered crowd produces a steady haze instead of synchronized bursts.
        if ((villager.tickCount + villager.getId()) % 4 != 0) return;
        level.sendParticles(ParticleTypes.ENCHANT,
                villager.getX(), villager.getY() + villager.getBbHeight() * 0.7, villager.getZ(),
                1, 0.35, 0.45, 0.35, 0.03);
    }

    private static void release(ServerLevel level, State state) {
        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof Villager villager)) continue;
            unfreeze(villager);
            villager.getBrain().setActiveActivityIfPossible(Activity.REST);
        }
        state.villagers.clear();
        state.active = false;
    }

    private static final class State {
        final Map<UUID, Entry> villagers = new HashMap<>();
        boolean active;
    }

    private static final class Entry { BlockPos target; }
}
