package com.notmjonter.stairs.mixin;

import java.util.UUID;
import com.notmjonter.stairs.FutureDimension;
import com.notmjonter.stairs.FutureVillagerFollow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Suspend scheduled work/wandering while preserving vanilla movement and look controls. */
@Mixin(Villager.class)
public abstract class FutureVillagerFollowMixin {
    @Unique private boolean stairs$following;
    @Unique private UUID stairs$followTarget;
    @Unique private BehaviorControl<LivingEntity> stairs$doors;

    @Redirect(method = "customServerAiStep", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void stairs$daytimeSurveillance(Brain<Villager> brain, ServerLevel level, LivingEntity entity) {
        Villager villager = (Villager) entity;
        if (level.dimension().equals(FutureDimension.KEY) && FutureVillagerFollow.followTime(level.getDayTime())) {
            var player = FutureVillagerFollow.nearest(villager, level);
            UUID target = player == null ? null : player.getUUID();
            boolean changed = !stairs$following || !java.util.Objects.equals(target, stairs$followTarget);
            if (!stairs$following) {
                brain.stopAll(level, villager);
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
                villager.getNavigation().stop();
            }
            stairs$following = true;
            stairs$followTarget = target;
            FutureVillagerFollow.follow(villager, player, changed);
            // Door opening is a brain behavior, not part of navigation itself.
            // Run its vanilla one-shot against the surveillance path while other tasks are suspended.
            var path = villager.getNavigation().getPath();
            if (path != null && !path.isDone()) {
                brain.setMemory(MemoryModuleType.PATH, path);
                if (stairs$doors == null) stairs$doors = InteractWithDoor.create();
                stairs$doors.tryStart(level, villager, level.getGameTime());
                stairs$doors.tickOrStop(level, villager, level.getGameTime());
            }
            return;
        }
        if (stairs$following) {
            villager.getNavigation().stop();
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            brain.eraseMemory(MemoryModuleType.PATH);
            stairs$following = false;
            stairs$followTarget = null;
        }
        brain.tick(level, villager);
    }
}
