package com.notmjonter.stairs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** Noon-to-nightfall surveillance, with normal navigation and no world/player teleporting. */
public final class FutureVillagerFollow {
    public static final double STOP_DISTANCE_SQUARED = 16;
    private FutureVillagerFollow() {}

    public static boolean followTime(long dayTime) {
        int time = Math.floorMod(dayTime, 24000);
        return time >= 6000 && time < 13000;
    }

    public static ServerPlayer nearest(Villager villager, ServerLevel level) {
        ServerPlayer nearest = null;
        double distance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            double candidate = villager.distanceToSqr(player);
            if (candidate < distance) { distance = candidate; nearest = player; }
        }
        return nearest;
    }

    public static void follow(Villager villager, ServerPlayer player, boolean repath) {
        if (player == null) { villager.getNavigation().stop(); return; }
        if (villager.isSleeping()) villager.stopSleeping();
        villager.getLookControl().setLookAt(player.getX(), player.getEyeY(), player.getZ(), 45, 45);
        if (villager.distanceToSqr(player) <= STOP_DISTANCE_SQUARED) {
            villager.getNavigation().stop();
        } else if (repath || villager.tickCount % 10 == 0) {
            villager.getNavigation().moveTo(player, 0.65);
        }
    }
}
