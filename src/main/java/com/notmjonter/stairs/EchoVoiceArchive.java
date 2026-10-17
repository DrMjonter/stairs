package com.notmjonter.stairs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/** Bounded, opt-in mono voice references, stored only after the well echo completes. */
public final class EchoVoiceArchive {
    public static final int RATE = 48000, MAX_SAMPLES = RATE * 8, MIN_SAMPLES = RATE / 2;
    private EchoVoiceArchive() {}

    static String save(Path world, UUID player, int reference, short[] pcm) throws IOException {
        if (reference < 0 || reference > 2 || pcm.length < MIN_SAMPLES || pcm.length > MAX_SAMPLES)
            throw new IllegalArgumentException("Invalid voice reference");
        String relative = "mjontersstairs/voice_references/" + player + "/" + reference + "-" + UUID.randomUUID() + ".wav";
        Path destination = world.resolve(relative).toAbsolutePath().normalize();
        if (!destination.startsWith(world.toAbsolutePath().normalize())) throw new IOException("Invalid archive path");
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "recording-", ".tmp");
        try {
            byte[] bytes = new byte[pcm.length * 2];
            for (int i = 0; i < pcm.length; i++) {
                bytes[i * 2] = (byte) pcm[i];
                bytes[i * 2 + 1] = (byte) (pcm[i] >>> 8);
            }
            try (var stream = new AudioInputStream(new ByteArrayInputStream(bytes),
                    new AudioFormat(RATE, 16, 1, true, false), pcm.length)) {
                AudioSystem.write(stream, AudioFileFormat.Type.WAVE, temporary.toFile());
            }
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, destination); }
        } finally { Files.deleteIfExists(temporary); }
        return relative;
    }

    static short[] load(Path world, String relative) throws IOException {
        Path root = world.toAbsolutePath().normalize();
        Path source = root.resolve(relative).toAbsolutePath().normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source)) throw new IOException("Invalid voice archive path");
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(source.toFile())) {
            AudioFormat format = audio.getFormat();
            if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED || format.getSampleRate() != RATE
                    || format.getSampleSizeInBits() != 16 || format.getChannels() != 1
                    || format.getFrameSize() != 2 || format.isBigEndian())
                throw new IOException("Unsupported voice archive format");
            byte[] bytes = audio.readNBytes(MAX_SAMPLES * 2 + 1);
            if (bytes.length < MIN_SAMPLES * 2 || bytes.length > MAX_SAMPLES * 2 || (bytes.length & 1) != 0)
                throw new IOException("Invalid voice archive length");
            short[] pcm = new short[bytes.length / 2];
            for (int i = 0; i < pcm.length; i++)
                pcm[i] = (short) ((bytes[i * 2] & 0xff) | bytes[i * 2 + 1] << 8);
            return pcm;
        } catch (javax.sound.sampled.UnsupportedAudioFileException error) {
            throw new IOException("Unreadable voice archive", error);
        }
    }

    /** Keeps internal pauses, trims trailing silence, and rejects silence-only offers. */
    static final class Recording {
        private final short[] samples = new short[MAX_SAMPLES];
        private int size, voiced, end;
        boolean append(short[] frame) {
            if (frame == null || frame.length == 0 || frame.length > 9600) return false;
            double energy = 0;
            for (short sample : frame) energy += (double) sample * sample;
            boolean audible = Math.sqrt(energy / frame.length) >= 200;
            if (size == 0 && !audible) return false;
            int count = Math.min(frame.length, MAX_SAMPLES - size);
            System.arraycopy(frame, 0, samples, size, count);
            size += count;
            if (audible) { voiced += count; end = size; }
            return audible && count > 0;
        }
        boolean ready() { return voiced >= MIN_SAMPLES; }
        boolean full() { return size == MAX_SAMPLES; }
        boolean started() { return end > 0; }
        short[] pcm() { return Arrays.copyOf(samples, end); }
    }
}
