package com.notmjonter.stairs;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** One delayed, quieter copy of each actually started sound, with no replay recursion. */
@EventBusSubscriber(modid = Stairs.MODID, value = Dist.CLIENT)
public final class EchoBasinEchoes {
    private static final int MAX_PENDING = 4096;
    private static final ConcurrentHashMap<SoundInstance, Context> STARTING = new ConcurrentHashMap<>();
    private static final PriorityQueue<Pending> PENDING = new PriorityQueue<>(Comparator.comparingLong(Pending::due));
    private static final List<Playing> PLAYING = new ArrayList<>();
    private static ClientLevel level;
    private static EchoReplayConfig.Settings settings;
    private static long clock, generation;

    private EchoBasinEchoes() {}

    private record Context(ClientLevel level, EchoReplayConfig.Settings settings, long generation, long started) {}
    private record Pending(long due, Replay sound) {}
    private record Playing(long started, Replay sound) {}

    private static boolean here(Minecraft mc) {
        return mc.level != null && mc.level.dimension().equals(EchoBasinDimension.KEY);
    }

    /** Only called on the client thread, including commands and settings changes. */
    private static void synchronize(Minecraft mc) {
        var next = EchoReplayConfig.settings();
        if (level != mc.level || !next.equals(settings)) {
            clear(mc);
            level = mc.level;
            settings = next;
        }
    }

    private static void clear(Minecraft mc) {
        generation++;
        STARTING.clear();
        PENDING.clear();
        for (Playing playing : PLAYING) {
            playing.sound.stopped = true;
            mc.getSoundManager().stop(playing.sound);
        }
        PLAYING.clear();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void requested(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (here(mc) && isAmbientAnimal(sound)) {
            event.setSound(null);
            return;
        }
        if (sound instanceof Replay || EchoBasinAmbience.isAmbience(sound)) return;
        Runnable capture = () -> {
            synchronize(mc);
            if (!here(mc) || !settings.enabled() || EchoBasinProgress.intensity() <= 0
                    || STARTING.size() >= MAX_PENDING
                    || EchoReplayConfig.blacklisted(settings.blacklist(), sound.getLocation().toString(), null)) return;
            STARTING.put(sound, new Context(mc.level, settings, generation, clock));
        };
        if (mc.isSameThread()) capture.run();
        else mc.execute(capture);
    }

    private static boolean isAmbientAnimal(SoundInstance sound) {
        var id = sound.getLocation();
        return id.getNamespace().equals("ambientsounds") && id.getPath().startsWith("animals.");
    }

    @SubscribeEvent
    public static void source(PlaySoundSourceEvent event) { started(event.getSound()); }

    @SubscribeEvent
    public static void stream(PlayStreamingSourceEvent event) { started(event.getSound()); }

    private static void started(SoundInstance original) {
        // Source events run on the audio thread. Never mutate client queues/world state there.
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            Context context = STARTING.remove(original);
            if (context == null) return;
            synchronize(mc);
            if (context.generation != generation || context.level != mc.level || !here(mc)
                    || !settings.enabled() || PENDING.size() >= MAX_PENDING) return;
            Sound clip = original.getSound();
            if (clip == null || clip == SoundManager.EMPTY_SOUND || clip == SoundManager.INTENTIONALLY_EMPTY_SOUND
                    || EchoReplayConfig.blacklisted(settings.blacklist(), original.getLocation().toString(),
                            clip.getLocation().toString())) return;
            PENDING.add(new Pending(clock + context.settings.delayTicks(), new Replay(original, context.level)));
        });
    }

    @SubscribeEvent
    public static void reloaded(SoundEngineLoadEvent event) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> clear(mc));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        synchronize(mc);
        if (!here(mc) || !settings.enabled()) {
            if (!STARTING.isEmpty() || !PENDING.isEmpty() || !PLAYING.isEmpty()) clear(mc);
            return;
        }
        if (mc.isPaused()) return;
        clock++;
        // Cancel sounds that never actually started (muted, rejected, or failed to load).
        STARTING.entrySet().removeIf(entry -> clock - entry.getValue().started > 200);
        while (!PENDING.isEmpty() && PENDING.peek().due <= clock) {
            Replay replay = PENDING.remove().sound;
            if (!replay.canPlaySound()) continue;
            mc.getSoundManager().play(replay);
            PLAYING.add(new Playing(clock, replay));
        }
        PLAYING.removeIf(playing -> clock - playing.started > 200
                && !mc.getSoundManager().isActive(playing.sound));
    }

    /** Pins the resolved audio variant and start position; never resolves a second random clip. */
    private static final class Replay extends AbstractSoundInstance implements TickableSoundInstance {
        private final ClientLevel origin;
        private final Sound clip;
        private final net.minecraft.core.BlockPos guidanceWell;
        private final float gain, tone;
        private final long createdGeneration;
        private boolean stopped;

        Replay(SoundInstance original, ClientLevel origin) {
            super(original.getLocation(), original.getSource(), RandomSource.create());
            this.origin = origin;
            createdGeneration = generation;
            clip = original.getSound();
            sound = clip;
            guidanceWell = original instanceof EchoGuidanceWhisper.GuidanceSound guidance ? guidance.well : null;
            float categoryGain = original.getSource() == net.minecraft.sounds.SoundSource.MASTER ? 1
                    : Minecraft.getInstance().options.getSoundSourceVolume(original.getSource());
            // Account for the original channel's volume clamp, so loud sounds still echo at 30%.
            float audibleGain = Math.clamp(original.getVolume() * categoryGain, 0, 1);
            gain = categoryGain > 0 ? audibleGain / categoryGain * EchoReplayConfig.REPLAY_VOLUME
                    * EchoBasinProgress.intensity() : 0;
            tone = original.getPitch();
            x = original.getX(); y = original.getY(); z = original.getZ();
            attenuation = original.getAttenuation();
            relative = original.isRelative();
            // Even an original looping sound receives just one extra playback, never a second loop.
            looping = false;
        }

        @Override public WeighedSoundEvents resolve(SoundManager manager) {
            sound = clip;
            var event = manager.getSoundEvent(location);
            // Some mods resolve sounds dynamically without a sounds.json event entry.
            return event == null ? new WeighedSoundEvents(location, null) : event;
        }
        @Override public float getVolume() { return gain; }
        @Override public float getPitch() { return tone; }
        @Override public boolean isStopped() { return stopped || createdGeneration != generation; }
        @Override public boolean canPlaySound() {
            Minecraft mc = Minecraft.getInstance();
            return !isStopped() && mc.level == origin && here(mc) && EchoReplayConfig.settings().enabled()
                    && !EchoReplayConfig.blacklisted(EchoReplayConfig.settings().blacklist(),
                            location.toString(), clip.getLocation().toString());
        }
        @Override public void tick() {
            if (!canPlaySound()) stopped = true;
            else if (guidanceWell != null) {
                var position = EchoGuidanceWhisper.position(guidanceWell);
                x = position.x;
                y = position.y;
                z = position.z;
            }
        }
    }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("basinecho")
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("on").executes(context -> enabled(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> enabled(context.getSource(), false)))
                .then(Commands.literal("toggle").executes(context ->
                        enabled(context.getSource(), !EchoReplayConfig.settings().enabled())))
                .then(Commands.literal("delay").then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0, 60))
                        .executes(context -> {
                            EchoReplayConfig.DELAY_SECONDS.set(DoubleArgumentType.getDouble(context, "seconds"));
                            saved();
                            return status(context.getSource());
                        })))
                .then(Commands.literal("blacklist")
                        .executes(context -> status(context.getSource()))
                        .then(Commands.literal("add").then(Commands.argument("pattern", StringArgumentType.greedyString())
                                .executes(context -> blacklist(context.getSource(),
                                        StringArgumentType.getString(context, "pattern"), true))))
                        .then(Commands.literal("remove").then(Commands.argument("pattern", StringArgumentType.greedyString())
                                .executes(context -> blacklist(context.getSource(),
                                        StringArgumentType.getString(context, "pattern"), false))))
                        .then(Commands.literal("clear").executes(context -> {
                            EchoReplayConfig.BLACKLIST.set(List.of());
                            saved();
                            return status(context.getSource());
                        }))));
    }

    private static int enabled(net.minecraft.commands.CommandSourceStack source, boolean enabled) {
        EchoReplayConfig.ENABLED.set(enabled);
        saved();
        return status(source);
    }

    private static int blacklist(net.minecraft.commands.CommandSourceStack source, String pattern, boolean add) {
        if (!EchoReplayConfig.validPattern(pattern)) {
            source.sendFailure(Component.translatable("command.mjontersstairs.echo.invalid_pattern"));
            return 0;
        }
        var entries = new ArrayList<String>(EchoReplayConfig.settings().blacklist());
        if (add && !entries.contains(pattern)) entries.add(pattern);
        if (!add) entries.remove(pattern);
        EchoReplayConfig.BLACKLIST.set(entries);
        saved();
        return status(source);
    }

    private static void saved() {
        EchoReplayConfig.SPEC.save();
        synchronize(Minecraft.getInstance());
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        var current = EchoReplayConfig.settings();
        source.sendSuccess(() -> Component.translatable("command.mjontersstairs.echo.status",
                Component.translatable(current.enabled() ? "command.mjontersstairs.echo.enabled"
                        : "command.mjontersstairs.echo.disabled"),
                current.delayTicks() / 20.0, String.join(", ", current.blacklist())), false);
        return 1;
    }
}
