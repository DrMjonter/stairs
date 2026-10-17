package com.notmjonter.stairs;

import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.audiochannel.ClientLocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Ordinary voice echoes remain local/in-memory; offered well recordings use the separate server bridge. */
public final class EchoVoiceClient {
    private static final int MAX_FRAMES = 16_384, MAX_INCOMING = 1024;
    private static final AtomicInteger incoming = new AtomicInteger();
    private static final Map<UUID, Phrase> phrases = new HashMap<>();
    private static final Set<Phrase> activePhrases = new HashSet<>();
    private static final short[] SILENCE = new short[960];
    private static final PriorityQueue<Frame> frames = new PriorityQueue<>(Comparator.comparingLong(Frame::due));
    private static volatile ClientLevel level;
    private static volatile long generation;
    private static EchoReplayConfig.Settings settings;
    private static boolean registered;

    private EchoVoiceClient() {}

    private static final class Phrase {
        final ClientLocationalAudioChannel[] channels = new ClientLocationalAudioChannel[EchoVoiceAudio.REPEATS];
        final VoicechatClientApi api;
        final UUID speaker;
        final EchoVoiceAudio.Processor[] processors = new EchoVoiceAudio.Processor[EchoVoiceAudio.REPEATS];
        final long[] nextTail = new long[EchoVoiceAudio.REPEATS], tailEnd = new long[EchoVoiceAudio.REPEATS];
        long last;

        Phrase(VoicechatClientApi api, UUID speaker, Vec3 position, Vec3 facing, float distance, long now) {
            this.api = api;
            this.speaker = speaker;
            last = now;
            for (int i = 0; i < channels.length; i++) {
                Vec3 echoPosition = EchoVoiceAudio.position(position, facing, i + 1);
                channels[i] = api.createLocationalAudioChannel(UUID.randomUUID(),
                        api.createPosition(echoPosition.x, echoPosition.y, echoPosition.z));
                if (channels[i] != null) channels[i].setDistance(distance + (float) (EchoVoiceAudio.TRAVEL_STEP * EchoVoiceAudio.REPEATS));
                processors[i] = new EchoVoiceAudio.Processor();
            }
        }
    }
    private record Frame(long due, Phrase phrase, int repeat, short[] pcm) {}

    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(EchoVoiceClient::tick);
    }

    public static void microphone(ClientSoundEvent event) {
        var api = event.getVoicechat();
        if (event.isCancelled() || api.isMuted() || api.isDisabled() || api.isDisconnected() || api.getGroup() != null) return;
        enqueue(api, null, event.getRawAudio(), event.isWhispering()
                ? (float) (api.getVoiceChatDistance() * 0.5) : (float) api.getVoiceChatDistance());
    }

    public static void received(ClientReceiveSoundEvent.EntitySound event) {
        var api = event.getVoicechat();
        if (event.isCancelled() || api.isDisabled() || api.isDisconnected() || api.getGroup() != null
                || api.isDisabled(event.getEntityId())) return;
        enqueue(api, event.getEntityId(), event.getRawAudio(), event.getDistance());
    }

    private static void enqueue(VoicechatClientApi api, UUID speaker, short[] raw, float distance) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel captured = mc.level;
        if (captured == null || !captured.dimension().equals(EchoBasinDimension.KEY)
                || EchoBasinProgress.intensity() <= 0 || raw == null || raw.length == 0 || raw.length > 9600) return;
        if (incoming.incrementAndGet() > MAX_INCOMING) { incoming.decrementAndGet(); return; }
        short[] pcm = raw.clone(); // Voice Chat reuses its buffers; never retain or modify them.
        long capturedAt = System.nanoTime();
        long capturedGeneration = generation;
        mc.execute(() -> {
            try {
                if (capturedGeneration != generation) return;
                synchronize(mc);
                if (mc.level != captured || !allowed(api)
                        || frames.size() + EchoVoiceAudio.REPEATS > MAX_FRAMES
                        || EchoReplayConfig.blacklisted(settings.blacklist(), "voicechat:voice",
                                speaker == null ? "voicechat:microphone" : "voicechat:proximity")) return;
                var player = speaker == null ? mc.player : mc.level.getPlayerByUUID(speaker);
                if (player == null || !player.isAlive()) return;
                UUID id = player.getUUID();
                Phrase phrase = phrases.get(id);
                if (phrase == null || capturedAt - phrase.last >= EchoVoiceAudio.PHRASE_GAP_NANOS) {
                    if (activePhrases.size() >= 128) return;
                    phrase = new Phrase(api, id, player.getEyePosition(), player.getLookAngle(), distance, capturedAt);
                    phrases.put(id, phrase);
                    activePhrases.add(phrase);
                }
                phrase.last = capturedAt;
                long spacing = settings.delayTicks() * 50_000_000L;
                for (int repeat = 1; repeat <= EchoVoiceAudio.REPEATS; repeat++)
                    frames.add(new Frame(capturedAt + spacing * repeat, phrase, repeat, pcm));
            } finally { incoming.decrementAndGet(); }
        });
    }

    private static boolean allowed(VoicechatClientApi api) {
        return level != null && level.dimension().equals(EchoBasinDimension.KEY) && settings.enabled()
                && !api.isDisabled() && !api.isDisconnected() && api.getGroup() == null;
    }

    private static void synchronize(Minecraft mc) {
        var next = EchoReplayConfig.settings();
        if (level != mc.level || !next.equals(settings)) {
            generation++;
            frames.clear();
            phrases.clear();
            activePhrases.clear();
            level = mc.level;
            settings = next;
        }
    }

    public static void connectionChanged() {
        Minecraft.getInstance().execute(() -> { generation++; frames.clear(); phrases.clear(); activePhrases.clear(); });
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        synchronize(mc);
        if (level == null || !level.dimension().equals(EchoBasinDimension.KEY) || !settings.enabled() || mc.isPaused()) {
            frames.clear();
            phrases.clear();
            activePhrases.clear();
            return;
        }
        long now = System.nanoTime();
        while (!frames.isEmpty() && frames.peek().due <= now) {
            Frame frame = frames.remove();
            Phrase phrase = frame.phrase;
            if (!allowed(phrase.api) || phrase.api.isDisabled(phrase.speaker)) continue;
            play(phrase, frame.repeat - 1, frame.pcm);
            int index = frame.repeat - 1;
            phrase.nextTail[index] = frame.due + frame.pcm.length * 1_000_000_000L / EchoVoiceAudio.SAMPLE_RATE;
            phrase.tailEnd[index] = phrase.nextTail[index] + EchoVoiceAudio.TAIL_NANOS;
        }
        // Feed silence after speech so the feedback buffers can emit their reverb tail.
        for (Phrase phrase : activePhrases) {
            if (!allowed(phrase.api) || phrase.api.isDisabled(phrase.speaker)) continue;
            for (int i = 0; i < phrase.channels.length; i++) {
                // Bound catch-up after a slow frame; do not burst seconds of stale audio.
                if (phrase.nextTail[i] > 0 && now - phrase.nextTail[i] > 250_000_000L)
                    phrase.nextTail[i] = now;
                while (phrase.nextTail[i] > 0 && phrase.nextTail[i] <= now
                        && phrase.nextTail[i] < phrase.tailEnd[i]) {
                    play(phrase, i, SILENCE);
                    phrase.nextTail[i] += 20_000_000L;
                }
            }
        }
        long retention = settings.delayTicks() * 50_000_000L * EchoVoiceAudio.REPEATS
                + EchoVoiceAudio.TAIL_NANOS + EchoVoiceAudio.PHRASE_GAP_NANOS;
        activePhrases.removeIf(phrase -> now - phrase.last > retention);
        phrases.entrySet().removeIf(entry -> !activePhrases.contains(entry.getValue()));
    }

    private static void play(Phrase phrase, int index, short[] pcm) {
        var channel = phrase.channels[index];
        if (channel == null) return;
        float personalVolume = (float) phrase.api.getVolumeConfig().getVolume(phrase.speaker);
        channel.play(phrase.processors[index].process(pcm, EchoVoiceAudio.gain(index + 1)
                * EchoBasinProgress.intensity() * personalVolume));
    }
}
