package com.makambox.studio.audio;

import com.makambox.studio.model.AudioData;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public final class AudioLoaderM4aRegressionTest {
    private AudioLoaderM4aRegressionTest() {}

    public static void main(String[] arguments) throws Exception {
        if (!Arrays.asList(AudioLoader.supportedFileExtensions()).contains("m4a")) {
            throw new AssertionError("M4A is missing from the shared audio file filter");
        }

        if (arguments.length > 0) {
            verifyRealM4a(Path.of(arguments[0]));
            return;
        }
        verifyInjectedDecoderAndCleanup();
        System.out.println("AudioLoader M4A regression checks passed.");
    }

    private static void verifyInjectedDecoderAndCleanup() throws Exception {
        Path fixtureDirectory = Files.createTempDirectory("makambox-m4a-regression-");
        Path source = fixtureDirectory.resolve("örnek kayıt.m4a");
        Path decodedFixture = fixtureDirectory.resolve("fixture.wav");
        Files.write(source, new byte[]{0, 1, 2, 3});
        writeWave(decodedFixture, 44_100, 4_410);

        AtomicReference<Path> generatedTarget = new AtomicReference<>();
        try {
            AudioLoader loader = new AudioLoader((ignored, target) -> {
                generatedTarget.set(target);
                Files.copy(decodedFixture, target);
            });
            AudioData audio = loader.load(source);
            if (!source.getFileName().toString().equals(audio.name())) {
                throw new AssertionError("Original M4A name was not preserved");
            }
            if (Math.abs(audio.sampleRate() - 44_100f) > 0.01f || audio.samples().length != 4_410) {
                throw new AssertionError("Decoded M4A sample rate or frame count changed");
            }
            if (Math.abs(audio.durationSeconds() - 0.1) > 0.0001) {
                throw new AssertionError("Decoded M4A duration changed: " + audio.durationSeconds());
            }

            Path temporaryTarget = generatedTarget.get();
            if (temporaryTarget == null || Files.exists(temporaryTarget)
                    || Files.exists(temporaryTarget.getParent())) {
                throw new AssertionError("Temporary M4A decoding files were not removed");
            }
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(decodedFixture);
            Files.deleteIfExists(fixtureDirectory);
        }
    }

    private static void verifyRealM4a(Path source) throws Exception {
        AudioData audio = new AudioLoader().load(source);
        if (audio.samples().length == 0 || audio.sampleRate() <= 0 || audio.durationSeconds() <= 0) {
            throw new AssertionError("Real M4A decoded to empty audio");
        }
        if (!source.getFileName().toString().equals(audio.name())) {
            throw new AssertionError("Real M4A did not retain its source name");
        }
        System.out.printf("Real M4A loaded: %s • %.3f s • %.1f Hz • %,d mono samples%n",
                audio.name(), audio.durationSeconds(), audio.sampleRate(), audio.samples().length);
    }

    private static void writeWave(Path output, int sampleRate, int frames) throws Exception {
        byte[] pcm = new byte[frames * 2];
        for (int frame = 0; frame < frames; frame++) {
            double phase = 2.0 * Math.PI * 440.0 * frame / sampleRate;
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
