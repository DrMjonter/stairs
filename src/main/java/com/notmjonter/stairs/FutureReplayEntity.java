package com.notmjonter.stairs;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.damagesource.DamageSource;
import java.util.UUID;
import net.minecraft.world.level.Level;

/** Anonymous, inert player silhouette; the recording manager controls its pose. */
public final class FutureReplayEntity extends PathfinderMob {
    public static final int SKIN_VARIANTS = 6;
    private UUID recordedPlayer;
    private static final EntityDataAccessor<Boolean> ALEX = SynchedEntityData.defineId(
            FutureReplayEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SKIN_VARIANT = SynchedEntityData.defineId(
            FutureReplayEntity.class, EntityDataSerializers.INT);

    public FutureReplayEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setSilent(true);
        noPhysics = true;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ALEX, false);
        builder.define(SKIN_VARIANT, 0);
    }

    public boolean alex() { return entityData.get(ALEX); }
    public void alex(boolean value) { entityData.set(ALEX, value); }
    public int skinVariant() { return Math.floorMod(entityData.get(SKIN_VARIANT), SKIN_VARIANTS); }
    public void skinVariant(int value) { entityData.set(SKIN_VARIANT, Math.floorMod(value, SKIN_VARIANTS)); }
    public void owner(UUID player, String name) {
        recordedPlayer = player;
        getPersistentData().putUUID("RecordedPlayer", player);
        getPersistentData().putString("RecordedPlayerName", name);
        addTag("recorded_player:" + name);
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || recordedPlayer == null || !Float.isFinite(amount) || amount <= 0) return false;
        var player = getServer().getPlayerList().getPlayer(recordedPlayer);
        return player != null && player.isAlive() && player.hurt(source, amount);
    }

    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        // The server already interpolates the recording every tick. A second three-tick
        // mob interpolation erases short contacts with the ground during repeated jumps.
        super.lerpTo(x, y, z, yaw, pitch, 1);
    }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPickable() { return true; }
    @Override public boolean shouldShowName() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
}
