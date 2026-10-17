package com.notmjonter.stairs;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Independent solar clock: 20 minutes, then 30 seconds, repeating at normal server TPS. */
public final class FutureClock extends SavedData {
    private long time;

    public FutureClock() {}

    static int speed(long time) {
        return Math.floorMod(Math.floorDiv(time, 24000), 2) == 0 ? 1 : 40;
    }

    static long next(long time) {
        // Commands can start partway through a fast day. Land exactly on sunrise
        // rather than carrying a 40-tick step into the following normal day.
        return time + Math.min(speed(time), 24000 - Math.floorMod(time, 24000));
    }

    static boolean crossedMidnight(long before, long after) {
        if (after <= before || after - before > 24000) return false;
        return Math.floorDiv(before - 18000, 24000) < Math.floorDiv(after - 18000, 24000);
    }

    static FutureClock load(CompoundTag tag, HolderLookup.Provider registries) {
        FutureClock clock = new FutureClock();
        clock.time = tag.getLong("DayTime");
        return clock;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("DayTime", time);
        return tag;
    }

    public static void loaded(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.getLevelData() instanceof Data data) {
            data.clock = level.getDataStorage().computeIfAbsent(new Factory<>(FutureClock::new, FutureClock::load),
                    Stairs.MODID + "_future_clock");
            data.rate = speed(data.clock.time);
        }
    }

    public static void tick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(level.getLevelData() instanceof Data data)) return;
        if (!level.tickRateManager().runsNormally()) return;
        if (level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            data.setDayTime(next(data.getDayTime()));
        }
        // NeoForge sends this rate with time synchronization, allowing clients to
        // advance locally at 40x instead of jumping forward once per second.
        // Rate changes force synchronization; game time and scheduled ticks are untouched.
        level.setDayTimePerTick(speed(data.getDayTime()));
    }

    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restartOnEntry(player);
    }

    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restartOnEntry(player);
    }

    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restartOnEntry(player);
    }

    private static void restartOnEntry(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(FutureDimension.KEY)) return;
        if (FutureContainment.active(level)) {
            FutureContainment.synchronize(player);
            return;
        }
        FutureContainment.resetWarnings(level);
        level.setDayTime(0);
        if (level.getLevelData() instanceof Data data) data.setDayTimeFraction(0);
        level.setDayTimePerTick(1);
        FutureFinale.restart(level, player);
        // Rate may already be 1; force a time update even when the rate did not change.
        level.getServer().forceTimeSynchronization();
    }

    public static void sleep(CanPlayerSleepEvent event) {
        if (!event.getLevel().dimension().equals(FutureDimension.KEY)) return;
        // Let visitors lie down during daytime too, but preserve range/obstruction checks.
        if (event.getProblem() == Player.BedSleepingProblem.NOT_POSSIBLE_NOW) event.setProblem(null);
    }

    public static void continueSleeping(CanContinueSleepingEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.serverLevel().dimension().equals(FutureDimension.KEY)
                && event.getProblem() == Player.BedSleepingProblem.NOT_POSSIBLE_NOW) {
            event.setContinueSleeping(true);
        }
    }

    public static void sleepingTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.serverLevel().dimension().equals(FutureDimension.KEY)
                && player.isSleeping() && player.getSleepTimer() >= 60) {
            player.stopSleepInBed(true, true);
            player.displayClientMessage(Component.translatable("message.mjontersstairs.future_no_sleep"), true);
        }
    }

    /** DerivedLevelData normally shares time AND speed with the Overworld. Isolate both. */
    public static final class Data extends DerivedLevelData {
        private FutureClock clock = new FutureClock();
        private float rate = 1;
        private float fraction;

        public Data(WorldData world, ServerLevelData wrapped) { super(world, wrapped); }

        @Override public long getDayTime() { return clock.time; }
        @Override public void setDayTime(long time) {
            if (clock.time != time) {
                clock.time = time;
                clock.setDirty();
            }
        }
        @Override public float getDayTimePerTick() { return rate; }
        @Override public void setDayTimePerTick(float rate) { this.rate = rate; }
        @Override public float getDayTimeFraction() { return fraction; }
        @Override public void setDayTimeFraction(float fraction) { this.fraction = fraction; }
    }
}
