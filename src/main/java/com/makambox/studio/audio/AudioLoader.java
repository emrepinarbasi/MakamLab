package com.makambox.studio.audio;

import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.AudioChannelMode;
import javazoom.jl.converter.Converter;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class AudioLoader {
    private static final double MAXIMUM_SECONDS = 60.0 * 45.0;
    /** Keeps source PCM plus analysis/display working sets below the packaged 2 GiB heap. */
    private static final long MAXIMUM_MONO_SAMPLES = 140_000_000L;
    private static final long TRANSCODE_TIMEOUT_MINUTES = 15;
    private static final String[] SUPPORTED_FILE_EXTENSIONS = {
            "wav", "wave", "aif", "aiff", "au", "mp3", "m4a"
    };

    @FunctionalInterface
    interface M4aDecoder {
        void decode(Path source, Path wavTarget) throws Exception;
    }

    @FunctionalInterface
    interface Mp3Decoder {
        void decode(Path source, Path wavTarget) throws Exception;
    }

    private final M4aDecoder m4aDecoder;
    private final Mp3Decoder mp3Decoder;

    public AudioLoader() {
        this(AudioLoader::decodeM4aWithPlatformTool, AudioLoader::decodeMp3WithPlatformTool);
    }

    AudioLoader(M4aDecoder m4aDecoder) {
        this(m4aDecoder, AudioLoader::decodeMp3WithPlatformTool);
    }

    AudioLoader(M4aDecoder m4aDecoder, Mp3Decoder mp3Decoder) {
        this.m4aDecoder = m4aDecoder;
        this.mp3Decoder = mp3Decoder;
    }

    public static String[] supportedFileExtensions() {
        return SUPPORTED_FILE_EXTENSIONS.clone();
    }

    public AudioData load(Path requested) throws Exception {
        return load(requested, AudioChannelMode.MIX);
    }

    /**
     * Loads a source as the mono signal used by the pitch-analysis pipeline.
     * Stereo sources can be mixed, inspected automatically, or read from one
     * channel without retaining two full decoded sample arrays in memory.
     */
    public AudioData load(Path requested, AudioChannelMode requestedMode) throws Exception {
        String lower = requested.getFileName().toString().toLowerCase(Locale.ROOT);
        Path sourceForDecoding = requested;
        Path temporaryDirectory = null;
        Path temporary = null;
        AudioChannelMode mode = requestedMode == null ? AudioChannelMode.MIX : requestedMode;
        try {
            if (lower.endsWith(".mp3") || lower.endsWith(".m4a")) {
                temporaryDirectory = Files.createTempDirectory("makambox-audio-");
                temporary = temporaryDirectory.resolve("decoded.wav");
                if (lower.endsWith(".mp3")) {
                    mp3Decoder.decode(requested.toAbsolutePath(), temporary.toAbsolutePath());
                } else {
                    m4aDecoder.decode(requested.toAbsolutePath(), temporary.toAbsolutePath());
                }
                if (!Files.isRegularFile(temporary) || Files.size(temporary) == 0) {
                    throw new IOException("Sıkıştırılmış ses verisi çözülemedi: " + requested.getFileName());
                }
                sourceForDecoding = temporary;
            }

            PcmDescription description = inspectPcm(sourceForDecoding);
            AudioChannelMode resolved = description.channels() < 2 ? AudioChannelMode.MIX
                    : mode == AudioChannelMode.AUTO ? selectAutomaticChannel(sourceForDecoding) : mode;
            float[] samples = readSelectedChannel(sourceForDecoding, resolved, description);
            return new AudioData(requested.getFileName().toString(), requested,
                    description.sampleRate(), samples, resolved, description.channels());
        } catch (UnsupportedAudioFileException exception) {
            throw new IOException("Desteklenmeyen ses biçimi: " + requested.getFileName(), exception);
        } finally {
            deleteTemporary(temporary);
            deleteTemporary(temporaryDirectory);
        }
    }

    private static void decodeM4aWithPlatformTool(Path source, Path wavTarget) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path afconvert = Path.of("/usr/bin/afconvert");
        if (os.contains("mac") && Files.isExecutable(afconvert)) {
            runTranscoder(List.of(afconvert.toString(), source.toString(), wavTarget.toString(),
                    "-f", "WAVE", "-d", "LEI16"), wavTarget, "macOS Core Audio");
            return;
        }

        String ffmpeg = findFfmpeg();
        if (ffmpeg == null) {
            throw new IOException("M4A çözücüsü bulunamadı. macOS'ta Core Audio, diğer sistemlerde "
                    + "FFmpeg gerekir.");
        }
        runTranscoder(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                "-i", source.toString(), "-vn", "-c:a", "pcm_s16le", wavTarget.toString()),
                wavTarget, "FFmpeg");
    }

    /**
     * Decodes MP3 through the same native PCM path used for M4A whenever the
     * platform provides one.  JLayer remains a compatibility fallback for
     * development systems without Core Audio or the packaged FFmpeg binary.
     *
     * <p>Keeping MP3 and M4A on one PCM conversion path is important for
     * graph-region tonic selection: the waveform time axis and the tracked F0
     * frames must be based on exactly the same decoded sample sequence.</p>
     */
    private static void decodeMp3WithPlatformTool(Path source, Path wavTarget) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path afconvert = Path.of("/usr/bin/afconvert");
        if (os.contains("mac") && Files.isExecutable(afconvert)) {
            runTranscoder(List.of(afconvert.toString(), source.toString(), wavTarget.toString(),
                    "-f", "WAVE", "-d", "LEI16"), wavTarget, "macOS Core Audio");
            return;
        }

        String ffmpeg = findFfmpeg();
        if (ffmpeg != null) {
            runTranscoder(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                    "-i", source.toString(), "-vn", "-c:a", "pcm_s16le", wavTarget.toString()),
                    wavTarget, "FFmpeg");
            return;
        }

        new Converter().convert(source.toString(), wavTarget.toString());
    }

    private static String findFfmpeg() {
        String configured = System.getProperty("makambox.ffmpeg", "").trim();
        if (configured.isEmpty()) configured = System.getenv().getOrDefault("MAKAMBOX_FFMPEG", "").trim();
        if (!configured.isEmpty()) return configured;

        String resources = System.getProperty("makambox.resources", "").trim();
        if (!resources.isEmpty()) {
            List<Path> candidates = new ArrayList<>();
            candidates.add(Path.of(resources, "ffmpeg"));
            candidates.add(Path.of(resources, "ffmpeg.exe"));
            candidates.add(Path.of(resources, "tools", "ffmpeg"));
            candidates.add(Path.of(resources, "tools", "ffmpeg.exe"));
            for (Path candidate : candidates) {
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate.toAbsolutePath().toString();
                }
            }
        }

        String path = System.getenv().getOrDefault("PATH", "");
        for (String directory : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (directory.isBlank()) continue;
            for (String name : List.of("ffmpeg", "ffmpeg.exe")) {
                Path candidate = Path.of(directory, name);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate.toAbsolutePath().toString();
                }
            }
        }
        return null;
    }

    private static void runTranscoder(List<String> command, Path wavTarget, String decoderName)
            throws IOException, InterruptedException {
        Path log = wavTarget.resolveSibling("decoder.log");
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            builder.redirectOutput(log.toFile());
            process = builder.start();
            boolean completed = process.waitFor(TRANSCODE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException(decoderName + " ses dönüşümü zaman aşımına uğradı.");
            }
            String diagnostic = Files.isRegularFile(log)
                    ? new String(Files.readAllBytes(log), StandardCharsets.UTF_8).trim() : "";
            if (process.exitValue() != 0) {
                if (diagnostic.length() > 2_000) diagnostic = diagnostic.substring(diagnostic.length() - 2_000);
                throw new IOException(decoderName + " ses dönüşümü başarısız oldu"
                        + (diagnostic.isEmpty() ? "." : ": " + diagnostic));
            }
        } catch (InterruptedException exception) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw exception;
        } finally {
            deleteTemporary(log);
        }
    }

    private static void deleteTemporary(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            path.toFile().deleteOnExit();
        }
    }

    private static PcmDescription inspectPcm(Path source) throws Exception {
        try (AudioInputStream decoded = openPcm(source)) {
            AudioFormat format = decoded.getFormat();
            long frameLength = decoded.getFrameLength();
            if (frameLength > 0 && frameLength / format.getFrameRate() > MAXIMUM_SECONDS) {
                throw new IOException("Ses dosyası 45 dakikalık güvenli analiz sınırını aşıyor.");
            }
            if (frameLength > MAXIMUM_MONO_SAMPLES) {
                throw new IOException("Ses dosyası örnekleme hızı ve süresi birlikte güvenli bellek "
                        + "sınırını aşıyor. Kaydı 48 kHz veya daha düşük örnekleme hızına dönüştürün.");
            }
            return new PcmDescription(format.getSampleRate(), format.getChannels(), frameLength);
        }
    }

    private static AudioInputStream openPcm(Path sourcePath) throws Exception {
        AudioInputStream original = AudioSystem.getAudioInputStream(sourcePath.toFile());
        try {
            AudioFormat source = original.getFormat();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, source.getSampleRate(),
                    16, source.getChannels(), source.getChannels() * 2, source.getSampleRate(), false);
            return AudioSystem.getAudioInputStream(pcm, original);
        } catch (Exception exception) {
            original.close();
            throw exception;
        }
    }

    private static AudioChannelMode selectAutomaticChannel(Path source) throws Exception {
        SignalStatistics mix = new SignalStatistics();
        SignalStatistics left = new SignalStatistics();
        SignalStatistics right = new SignalStatistics();
        visitPcmFrames(source, (bytes, base, channels) -> {
            double sum = 0.0;
            for (int channel = 0; channel < channels; channel++) {
                sum += sample(bytes, base + channel * 2);
            }
            double leftValue = sample(bytes, base);
            double rightValue = sample(bytes, base + Math.min(1, channels - 1) * 2);
            mix.accept(sum / channels);
            left.accept(leftValue);
            right.accept(rightValue);
        });

        AudioChannelMode best = AudioChannelMode.MIX;
        double bestScore = mix.score();
        if (left.score() > bestScore + 0.25) {
            best = AudioChannelMode.LEFT;
            bestScore = left.score();
        }
        if (right.score() > bestScore + 0.25) best = AudioChannelMode.RIGHT;
        return best;
    }

    private static float[] readSelectedChannel(Path source, AudioChannelMode mode,
                                               PcmDescription description) throws Exception {
        int expected = description.frameLength() > 0 && description.frameLength() <= Integer.MAX_VALUE
                ? (int) description.frameLength() : 16_384;
        FloatSamples samples = new FloatSamples(expected);
        long maximumFrames = Math.min(MAXIMUM_MONO_SAMPLES,
                Math.max(1L, (long) Math.ceil(description.sampleRate() * MAXIMUM_SECONDS)));
        visitPcmFrames(source, (bytes, base, channels) -> {
            if (samples.size() >= maximumFrames) {
                throw new AudioTooLongException();
            }
            double value;
            if (mode == AudioChannelMode.LEFT) {
                value = sample(bytes, base);
            } else if (mode == AudioChannelMode.RIGHT) {
                value = sample(bytes, base + Math.min(1, channels - 1) * 2);
            } else {
                double sum = 0.0;
                for (int channel = 0; channel < channels; channel++) {
                    sum += sample(bytes, base + channel * 2);
                }
                value = sum / channels;
            }
            samples.add((float) value);
        });
        return samples.toArray();
    }

    private static void visitPcmFrames(Path source, FrameVisitor visitor) throws Exception {
        try (AudioInputStream decoded = openPcm(source)) {
            AudioFormat format = decoded.getFormat();
            int channels = Math.max(1, format.getChannels());
            int frameSize = channels * 2;
            int bufferSize = Math.max(frameSize, 64 * 1024 - (64 * 1024) % frameSize);
            byte[] buffer = new byte[bufferSize + frameSize];
            int carry = 0;
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Ses yükleme iptal edildi");
                }
                int count = decoded.read(buffer, carry, bufferSize - carry);
                if (count < 0) break;
                int available = carry + count;
                int frameBytes = available - available % frameSize;
                for (int base = 0; base < frameBytes; base += frameSize) {
                    visitor.accept(buffer, base, channels);
                }
                carry = available - frameBytes;
                if (carry > 0) System.arraycopy(buffer, frameBytes, buffer, 0, carry);
            }
        } catch (AudioTooLongException exception) {
            throw new IOException("Ses dosyası süre/örnekleme hızı bakımından güvenli analiz "
                    + "sınırını aşıyor. Kaydı 48 kHz veya daha düşük örnekleme hızına dönüştürün.");
        }
    }

    private static double sample(byte[] bytes, int offset) {
        int value = (bytes[offset] & 0xff) | (bytes[offset + 1] << 8);
        return (short) value / 32768.0;
    }

    @FunctionalInterface
    private interface FrameVisitor {
        void accept(byte[] bytes, int base, int channels);
    }

    private record PcmDescription(float sampleRate, int channels, long frameLength) { }

    private static final class AudioTooLongException extends RuntimeException { }

    private static final class SignalStatistics {
        private long count;
        private long active;
        private long clipped;
        private double sum;
        private double sumSquares;
        private double absoluteDifferenceSum;
        private double previous;
        private boolean hasPrevious;

        void accept(double value) {
            count++;
            sum += value;
            sumSquares += value * value;
            if (Math.abs(value) >= 0.005) active++;
            if (Math.abs(value) >= 0.995) clipped++;
            if (hasPrevious) absoluteDifferenceSum += Math.abs(value - previous);
            previous = value;
            hasPrevious = true;
        }

        double score() {
            if (count == 0) return Double.NEGATIVE_INFINITY;
            double mean = sum / count;
            double rms = Math.sqrt(Math.max(0.0, sumSquares / count));
            double activity = active / (double) count;
            double clipping = clipped / (double) count;
            // Broadband noise and percussion can be louder than a clean
            // melodic channel.  Penalise sample-to-sample roughness relative
            // to RMS so AUTO favours a continuous pitched source instead of
            // simply choosing the loudest channel.
            double roughness = absoluteDifferenceSum
                    / Math.max(1.0, count - 1.0) / Math.max(1.0e-9, rms);
            return 20.0 * Math.log10(rms + 1.0e-9)
                    + 6.0 * activity - 24.0 * clipping
                    - 4.0 * Math.min(1.0, Math.abs(mean) / Math.max(1.0e-9, rms))
                    - 8.0 * Math.min(1.5, roughness);
        }
    }

    private static final class FloatSamples {
        private float[] values;
        private int size;

        FloatSamples(int expected) {
            values = new float[Math.max(16, expected)];
        }

        int size() { return size; }

        void add(float value) {
            if (size == values.length) {
                int next = values.length >= Integer.MAX_VALUE / 2
                        ? Integer.MAX_VALUE - 8 : values.length * 2;
                if (next <= values.length) throw new IllegalStateException("Ses belleğe sığmıyor.");
                values = java.util.Arrays.copyOf(values, next);
            }
            values[size++] = value;
        }

        float[] toArray() {
            return size == values.length ? values : java.util.Arrays.copyOf(values, size);
        }
    }
}
