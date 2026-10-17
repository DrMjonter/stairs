package com.notmjonter.stairs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.sound.sampled.AudioSystem;

final class EchoVoiceArchiveTest {
    static void run() throws Exception {
        var recording = new EchoVoiceArchive.Recording();
        if (recording.append(new short[960]) || recording.started()) throw new AssertionError("Silence must not start recording");
        short[] speech = new short[960];
        for (int i = 0; i < speech.length; i++) speech[i] = (short) (5000 * Math.sin(i * 0.1));
        for (int i = 0; i < 50; i++) recording.append(speech);
        recording.append(new short[960]);
        if (!recording.ready() || recording.pcm().length != 48000) throw new AssertionError("Recording must retain speech and trim trailing silence");
        var full = new EchoVoiceArchive.Recording();
        for (int i = 0; i < 1000; i++) full.append(speech);
        if (!full.full() || full.pcm().length != EchoVoiceArchive.MAX_SAMPLES) throw new AssertionError("Recording must be bounded to eight seconds");
        Path world = Files.createTempDirectory(Path.of(".").toAbsolutePath(), "echo-voice-test-");
        String file = EchoVoiceArchive.save(world, UUID.randomUUID(), 0, recording.pcm());
        if (!world.resolve(file).normalize().startsWith(world.normalize())) throw new AssertionError("Archive must stay inside the world");
        if (!java.util.Arrays.equals(recording.pcm(), EchoVoiceArchive.load(world, file)))
            throw new AssertionError("Archived voice PCM must load without changing samples");
        try (var audio = AudioSystem.getAudioInputStream(world.resolve(file).toFile())) {
            if (audio.getFormat().getChannels() != 1 || audio.getFormat().getSampleRate() != 48000
                    || audio.getFormat().getSampleSizeInBits() != 16 || audio.getFrameLength() != 48000)
                throw new AssertionError("Archived voice must be 48 kHz, 16-bit mono WAV");
            byte[] bytes = audio.readAllBytes();
            short[] original = recording.pcm();
            for (int i = 0; i < original.length; i++) {
                short sample = (short) ((bytes[i * 2] & 255) | (bytes[i * 2 + 1] << 8));
                if (sample != original[i]) throw new AssertionError("Raw voice must round-trip without DSP changes");
            }
        }
        try {
            EchoVoiceArchive.save(world, UUID.randomUUID(), 0, new short[1]);
            throw new AssertionError("Too-short recordings must be rejected");
        } catch (IllegalArgumentException expected) {}
        System.out.println("Well voice recording limits and raw WAV round-trip checks passed.");
    }
}
