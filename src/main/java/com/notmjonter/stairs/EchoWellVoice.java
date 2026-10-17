package com.notmjonter.stairs;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/** Optional server-side Voice Chat bridge. Only explicitly offered well sessions are recorded. */
public final class EchoWellVoice implements EchoListeningWells.VoiceRecorder {
    private static final int MIN_HAUNT_DELAY = 20 * 60 * 2;
    private static final int MAX_HAUNT_DELAY = 20 * 60 * 5;
    private static final int ECHO_SPACING = 14;
    private static final int REVERB_TAIL_SAMPLES = EchoVoiceArchive.RATE * 2;
    private static volatile EchoWellVoice current;
    private final VoicechatServerApi api;
    private volatile boolean accepting = true;
    private final Map<UUID, Capture> captures = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> nextHaunt = new HashMap<>();
    private final List<Haunt> haunts = new ArrayList<>();
    private final Random random = new Random();

    private static final class Capture {
        final EchoListeningWells.Pending request;
        final OpusDecoder decoder;
        final ConcurrentLinkedQueue<byte[]> packets = new ConcurrentLinkedQueue<>();
        final AtomicInteger queued = new AtomicInteger();
        EchoVoiceArchive.Recording recording = new EchoVoiceArchive.Recording();
        int lastSpeech, echoAt;
        volatile boolean cancelled, finished;
        boolean playing;
        volatile short[] raw;
        AudioPlayer playback;
        OpusEncoder encoder;
        Capture(EchoListeningWells.Pending request, OpusDecoder decoder) {
            this.request = request;
            this.decoder = decoder;
            lastSpeech = request.started();
        }
        void close() {
            cancelled = true;
            packets.clear();
            if (!decoder.isClosed()) decoder.close();
            // Voice Chat owns/closes the encoder once playback starts. Interrupt its
            // thread instead of closing native state concurrently with encode().
            if (playback != null && playback.isStarted()) {
                if (!playback.isStopped()) playback.stopPlaying();
            } else if (encoder != null && !encoder.isClosed()) encoder.close();
        }
    }

    private static final class Trail {
        final AudioPlayer playback;
        final OpusEncoder encoder;
        final int startTick;
        boolean started;
        volatile boolean finished;

        Trail(AudioPlayer playback, OpusEncoder encoder, int startTick) {
            this.playback = playback;
            this.encoder = encoder;
            this.startTick = startTick;
        }

        void close() {
            finished = true;
            if (started && playback.isStarted()) {
                if (!playback.isStopped()) playback.stopPlaying();
            } else if (!encoder.isClosed()) encoder.close();
        }
    }

    private static final class Haunt {
        final UUID player;
        final List<Trail> trails;

        Haunt(UUID player, List<Trail> trails) {
            this.player = player;
            this.trails = trails;
        }

        void close() { trails.forEach(Trail::close); }
    }

    private EchoWellVoice(VoicechatServerApi api) { this.api = api; }

    public static void started(VoicechatServerApi api) {
        current = new EchoWellVoice(api);
        EchoListeningWells.recorder = current;
    }

    public static void stopping() {
        var instance = current;
        if (instance != null) instance.accepting = false;
        // Native decoder/playback cleanup is marshalled through the normal server tick/stop event.
    }

    public static void microphone(MicrophonePacketEvent event) {
        var instance = current;
        var connection = event.getSenderConnection();
        if (instance == null || !instance.accepting || event.isCancelled() || connection == null
                || !connection.isConnected() || connection.isDisabled() || connection.isInGroup()) return;
        Capture capture = instance.captures.get(connection.getPlayer().getUuid());
        if (capture == null || capture.cancelled || capture.raw != null) return;
        byte[] packet = event.getPacket().getOpusEncodedData();
        if (packet == null || packet.length == 0 || packet.length > 4096) return;
        if (capture.queued.incrementAndGet() > 64) { capture.queued.decrementAndGet(); return; }
        capture.packets.add(packet.clone());
    }

    @Override public boolean available(ServerPlayer player) {
        if (!accepting) return false;
        var connection = api.getConnectionOf(player.getUUID());
        return accepting && connection != null && connection.isInstalled() && connection.isConnected()
                && !connection.isDisabled() && !connection.isInGroup();
    }

    @Override public boolean begin(EchoListeningWells.Pending request) {
        if (!available(request.player()) || captures.size() >= 8 || captures.containsKey(request.player().getUUID())) return false;
        try {
            var decoder = api.createDecoder();
            if (decoder == null) return false;
            captures.put(request.player().getUUID(), new Capture(request, decoder));
            return true;
        } catch (RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger(EchoWellVoice.class).warn("Could not start well voice decoder", error);
            return false;
        }
    }

    @Override public void tick(MinecraftServer server) {
        int now = server.getTickCount();
        tickHaunts(server, now);
        for (Capture capture : new ArrayList<>(captures.values())) {
            UUID player = capture.request.player().getUUID();
            if (EchoListeningWells.pending(player) != capture.request || !available(capture.request.player())) {
                EchoListeningWells.cancel(player, "cancelled");
                cancel(player);
                continue;
            }
            try {
                if (capture.playback != null) {
                    if (!capture.playing && now >= capture.echoAt) {
                        capture.playing = true;
                        capture.playback.startPlaying();
                    }
                    if (capture.finished) {
                        if (!EchoListeningWells.complete(capture.request.player(), capture.request.center(),
                                capture.request.reference(), capture.raw)) EchoListeningWells.cancel(player, "recording_failed");
                        cancel(player);
                    }
                    continue;
                }
                byte[] packet;
                while ((packet = capture.packets.poll()) != null) {
                    capture.queued.decrementAndGet();
                    if (capture.recording.append(capture.decoder.decode(packet))) capture.lastSpeech = now;
                }
                if (capture.recording.started() && (now - capture.lastSpeech >= 16 || capture.recording.full())) {
                    if (capture.recording.ready()) prepareEcho(capture, now);
                    else capture.recording = new EchoVoiceArchive.Recording();
                } else if (!capture.recording.started() && now - capture.request.started() >= 600) {
                    EchoListeningWells.cancel(player, "cancelled");
                }
            } catch (RuntimeException error) {
                org.slf4j.LoggerFactory.getLogger(EchoWellVoice.class).warn("Well voice recording/playback failed", error);
                EchoListeningWells.cancel(player, "recording_failed");
            }
        }
    }

    private void tickHaunts(MinecraftServer server, int now) {
        for (var iterator = haunts.iterator(); iterator.hasNext();) {
            Haunt haunt = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(haunt.player);
            if (player == null || !player.isAlive() || player.serverLevel().dimension() != EchoBasinDimension.KEY
                    || !available(player)) {
                haunt.close();
                iterator.remove();
                continue;
            }
            for (Trail trail : haunt.trails) {
                if (!trail.started && !trail.finished && now >= trail.startTick) {
                    try {
                        trail.started = true;
                        trail.playback.startPlaying();
                    } catch (RuntimeException error) {
                        trail.close();
                        org.slf4j.LoggerFactory.getLogger(EchoWellVoice.class)
                                .warn("Could not start an archived well echo", error);
                    }
                }
            }
            if (haunt.trails.stream().allMatch(trail -> trail.finished)) iterator.remove();
        }

        var level = server.getLevel(EchoBasinDimension.KEY);
        if (level == null) {
            nextHaunt.clear();
            return;
        }
        Set<UUID> eligible = new HashSet<>();
        StaircaseData progress = StaircaseData.get(server.overworld());
        for (ServerPlayer player : level.players()) {
            UUID playerId = player.getUUID();
            List<String> voices = progress.echoVoiceFiles(playerId);
            if (voices.isEmpty() || !available(player)) continue;
            eligible.add(playerId);
            int due = nextHaunt.computeIfAbsent(playerId, ignored -> now + nextHauntDelay());
            if (now < due || EchoListeningWells.pending(playerId) != null
                    || haunts.stream().anyMatch(haunt -> haunt.player.equals(playerId))) continue;
            nextHaunt.put(playerId, now + nextHauntDelay());
            startHaunt(server, player, voices.get(random.nextInt(voices.size())), now);
        }
        nextHaunt.keySet().retainAll(eligible);
    }

    private int nextHauntDelay() {
        return random.nextInt(MIN_HAUNT_DELAY, MAX_HAUNT_DELAY + 1);
    }

    private void startHaunt(MinecraftServer server, ServerPlayer player, String file, int now) {
        short[] raw;
        try {
            raw = EchoVoiceArchive.load(server.getWorldPath(LevelResource.ROOT), file);
        } catch (java.io.IOException error) {
            org.slf4j.LoggerFactory.getLogger(EchoWellVoice.class)
                    .warn("Could not load archived well voice for {}", player.getGameProfile().getName(), error);
            return;
        }

        int paddedLength = ((raw.length + REVERB_TAIL_SAMPLES + 959) / 960) * 960;
        short[] input = Arrays.copyOf(raw, paddedLength);
        short[] reverberant = new EchoVoiceAudio.Processor().process(input, 0.62F);
        int count = 5 + random.nextInt(2);
        double startAngle = random.nextDouble() * Math.PI * 2;
        Vec3 origin = player.position().add(0, player.getEyeHeight() * 0.65, 0);
        List<Trail> trails = new ArrayList<>(count);
        try {
            for (int index = 0; index < count; index++) {
                double angle = startAngle + index * (Math.PI * 2 / count) + (random.nextDouble() - 0.5) * 0.45;
                double radius = 20 + random.nextDouble() * 12;
                Vec3 position = origin.add(Math.cos(angle) * radius, random.nextDouble() * 8 - 3,
                        Math.sin(angle) * radius);
                var channel = api.createLocationalAudioChannel(UUID.randomUUID(), api.fromServerLevel(player.serverLevel()),
                        api.createPosition(position.x, position.y, position.z));
                if (channel == null) {
                    trails.forEach(Trail::close);
                    return;
                }
                channel.setDistance(64);
                OpusEncoder encoder = api.createEncoder();
                float gain = (float) (0.72 * Math.pow(0.72, index));
                AudioPlayer playback;
                try {
                    playback = api.createAudioPlayer(channel, encoder, EchoVoiceAudio.scaled(reverberant, gain));
                } catch (RuntimeException error) {
                    if (encoder != null && !encoder.isClosed()) encoder.close();
                    throw error;
                }
                if (playback == null) {
                    if (encoder != null && !encoder.isClosed()) encoder.close();
                    trails.forEach(Trail::close);
                    return;
                }
                Trail trail = new Trail(playback, encoder, now + index * ECHO_SPACING);
                playback.setOnStopped(() -> trail.finished = true);
                trails.add(trail);
            }
            haunts.add(new Haunt(player.getUUID(), trails));
        } catch (RuntimeException error) {
            trails.forEach(Trail::close);
            org.slf4j.LoggerFactory.getLogger(EchoWellVoice.class)
                    .warn("Could not create archived well echoes", error);
        }
    }

    private void prepareEcho(Capture capture, int now) {
        capture.raw = capture.recording.pcm();
        capture.packets.clear();
        if (!capture.decoder.isClosed()) capture.decoder.close();
        int length = ((capture.raw.length + 120000 + 959) / 960) * 960;
        short[] input = Arrays.copyOf(capture.raw, length); // Silence lets the existing reverb decay.
        short[] echo = new EchoVoiceAudio.Processor().process(input, 0.65f);
        var center = capture.request.center();
        var channel = api.createLocationalAudioChannel(UUID.randomUUID(), api.fromServerLevel(capture.request.level()),
                api.createPosition(center.getX() + 0.5, center.getY() - 3, center.getZ() + 0.5));
        if (channel == null) throw new IllegalStateException("Voice Chat did not create a well channel");
        channel.setDistance(48);
        capture.encoder = api.createEncoder();
        capture.playback = api.createAudioPlayer(channel, capture.encoder, echo);
        if (capture.playback == null) throw new IllegalStateException("Voice Chat did not create a well echo player");
        capture.playback.setOnStopped(() -> { if (!capture.cancelled) capture.finished = true; });
        capture.echoAt = now + 15;
    }

    @Override public void cancel(UUID player) {
        Capture capture = captures.remove(player);
        if (capture != null) capture.close();
    }

    @Override public void close() {
        accepting = false;
        for (UUID player : new ArrayList<>(captures.keySet())) cancel(player);
        haunts.forEach(Haunt::close);
        haunts.clear();
        nextHaunt.clear();
        if (current == this) current = null;
    }
}
