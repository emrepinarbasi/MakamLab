package com.makambox.studio.audio;

import com.makambox.studio.dsp.AnalysisAudioResampler;
import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.PitchFrame;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Regression coverage for the platform PCM path used by MP3 tonic selection. */
public final class AudioLoaderMp3RegressionTest {
    private AudioLoaderMp3RegressionTest() { }

    public static void main(String[] arguments) throws Exception {
        if (!Arrays.asList(AudioLoader.supportedFileExtensions()).contains("mp3")) {
            throw new AssertionError("MP3 is missing from the shared audio file filter");
        }
        if (arguments.length > 0) {
            verifyRealMp3(Path.of(arguments[0]));
            return;
        }
        verifyInjectedDecoderAndCleanup();
        System.out.println("AudioLoader MP3 regression checks passed.");
    }

    private static void verifyInjectedDecoderAndCleanup() throws Exception {
        Path fixtureDirectory = Files.createTempDirectory("makamlab-mp3-regression-");
        Path source = fixtureDirectory.resolve("örnek karar.mp3");
        Path decodedFixture = fixtureDirectory.resolve("fixture.wav");
        Files.write(source, new byte[]{0, 1, 2, 3});
        writeWave(decodedFixture, 44_100, 8_820, 220.0);

        AtomicReference<Path> generatedTarget = new AtomicReference<>();
        try {
            AudioLoader loader = new AudioLoader((ignored, target) -> {
                throw new AssertionError("M4A decoder must not be used for MP3");
            }, (ignored, target) -> {
                generatedTarget.set(target);
                Files.copy(decodedFixture, target);
            });
            AudioData audio = loader.load(source);
            if (!source.getFileName().toString().equals(audio.name())) {
                throw new AssertionError("Original MP3 name was not preserved");
            }
            if (Math.abs(audio.sampleRate() - 44_100f) > 0.01f
                    || audio.samples().length != 8_820) {
                throw new AssertionError("Decoded MP3 sample rate or frame count changed");
            }
            List<PitchFrame> frames = new PitchTracker().track(
                    AnalysisAudioResampler.forPitchTracking(audio), ignored -> { });
            long voiced = frames.stream().filter(PitchFrame::voiced).count();
            if (voiced < 3) {
                throw new AssertionError("Decoded MP3 did not provide enough F0 evidence");
            }

            Path temporaryTarget = generatedTarget.get();
            if (temporaryTarget == null || Files.exists(temporaryTarget)
                    || Files.exists(temporaryTarget.getParent())) {
                throw new AssertionError("Temporary MP3 decoding files were not removed");
            }
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(decodedFixture);
            Files.deleteIfExists(fixtureDirectory);
        }
    }

    private static void verifyRealMp3(Path source) throws Exception {
        AudioData audio = new AudioLoader().load(source);
        List<PitchFrame> frames = new PitchTracker().track(
                AnalysisAudioResampler.forPitchTracking(audio), ignored -> { });
        long reliable = frames.stream()
                .filter(PitchFrame::voiced)
                .filter(value -> value.confidence() >= .65)
                .count();
        if (audio.samples().length == 0 || audio.sampleRate() <= 0
                || audio.durationSeconds() <= 0 || reliable < 3) {
            throw new AssertionError("Real MP3 did not yield reliable selected-region F0 evidence");
        }
        System.out.printf("Real MP3 loaded: %s • %.3f s • %.1f Hz • %,d reliable frames%n",
                audio.name(), audio.durationSeconds(), audio.sampleRate(), reliable);
    }

    private static void writeWave(Path output, int sampleRate, int frames, double hertz)
            throws Exception {
        byte[] pcm = new byte[frames * 2];
        for (int frame = 0; frame < frames; frame++) {
            double phase = 2.0 * Math.PI * hertz * frame / sampleRate;
            short value = (short) Math.round(Math.sin(phase) * 12_000.0);
            pcm[frame * 2] = (byte) value;
            pcm[frame * 2 + 1] = (byte) (value >>> 8);
        }
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(pcm), format, frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, output.toFile());
        }
    }
}
