package com.makambox.studio.audio;

import com.makambox.studio.model.AudioChannelMode;
import com.makambox.studio.model.AudioData;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AudioLoaderChannelSelectionRegressionTest {
    private AudioLoaderChannelSelectionRegressionTest() { }

    public static void main(String[] arguments) throws Exception {
        Path directory = Files.createTempDirectory("makambox-channel-regression-");
        Path stereo = directory.resolve("stereo.wav");
        Path melodyAndNoise = directory.resolve("melody-and-noise.wav");
        try {
            writeStereoWave(stereo, 44_100, 4_410);
            writeMelodyAndLoudNoiseWave(melodyAndNoise, 44_100, 44_100);
            AudioLoader loader = new AudioLoader();
            AudioData left = loader.load(stereo, AudioChannelMode.LEFT);
            AudioData right = loader.load(stereo, AudioChannelMode.RIGHT);
            AudioData mix = loader.load(stereo, AudioChannelMode.MIX);
            AudioData automatic = loader.load(stereo, AudioChannelMode.AUTO);

            if (left.sourceChannelCount() != 2 || right.sourceChannelCount() != 2) {
                throw new AssertionError("Stereo source channel count was not retained");
            }
            if (left.channelMode() != AudioChannelMode.LEFT
                    || right.channelMode() != AudioChannelMode.RIGHT
                    || mix.channelMode() != AudioChannelMode.MIX) {
                throw new AssertionError("Requested channel selection was not retained");
            }
            double leftRms = rms(left.samples());
            double rightRms = rms(right.samples());
            double mixRms = rms(mix.samples());
            if (!(rightRms > leftRms * 4.5 && mixRms > leftRms && mixRms < rightRms)) {
                throw new AssertionError("Stereo channel extraction changed: "
                        + leftRms + " / " + mixRms + " / " + rightRms);
            }
            if (automatic.channelMode() != AudioChannelMode.RIGHT) {
                throw new AssertionError("Automatic selection did not choose the clearer right channel: "
                        + automatic.channelMode());
            }
            if (Math.abs(rms(automatic.samples()) - rightRms) > 1.0e-6) {
                throw new AssertionError("Automatic samples differ from selected right channel");
            }

            AudioData cleanMelody = loader.load(melodyAndNoise, AudioChannelMode.LEFT);
            AudioData loudNoise = loader.load(melodyAndNoise, AudioChannelMode.RIGHT);
            AudioData melodicAutomatic = loader.load(melodyAndNoise, AudioChannelMode.AUTO);
            if (!(rms(loudNoise.samples()) > rms(cleanMelody.samples()) * 1.8)) {
                throw new AssertionError("Synthetic noise channel is not sufficiently louder");
            }
            if (melodicAutomatic.channelMode() != AudioChannelMode.LEFT) {
                throw new AssertionError("AUTO followed the louder broadband channel instead of "
                        + "the clean melody: " + melodicAutomatic.channelMode());
            }
            if (maximumDifference(melodicAutomatic.samples(), cleanMelody.samples()) > 1.0e-6) {
                throw new AssertionError("AUTO melody samples differ from the selected clean channel");
            }
            System.out.println("Audio channel selection regression checks passed.");
        } finally {
            Files.deleteIfExists(melodyAndNoise);
            Files.deleteIfExists(stereo);
            Files.deleteIfExists(directory);
        }
    }

    private static void writeStereoWave(Path output, int sampleRate, int frames) throws Exception {
        byte[] pcm = new byte[frames * 4];
        for (int frame = 0; frame < frames; frame++) {
            double phase = 2.0 * Math.PI * 220.0 * frame / sampleRate;
            short left = (short) Math.round(Math.sin(phase) * 2_000.0);
            short right = (short) Math.round(Math.sin(phase) * 14_000.0);
            int offset = frame * 4;
            pcm[offset] = (byte) left;
            pcm[offset + 1] = (byte) (left >>> 8);
            pcm[offset + 2] = (byte) right;
            pcm[offset + 3] = (byte) (right >>> 8);
        }
        AudioFormat format = new AudioFormat(sampleRate, 16, 2, true, false);
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(pcm), format, frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, output.toFile());
        }
    }

    private static void writeMelodyAndLoudNoiseWave(Path output, int sampleRate, int frames)
            throws Exception {
        byte[] pcm = new byte[frames * 4];
        int randomState = 0x13579bdf;
        for (int frame = 0; frame < frames; frame++) {
            double phase = 2.0 * Math.PI * 220.0 * frame / sampleRate;
            short melody = (short) Math.round(Math.sin(phase) * 5_000.0);
            randomState = randomState * 1_664_525 + 1_013_904_223;
            double uniformNoise = ((randomState >>> 1) / (double) 0x7fffffff) * 2.0 - 1.0;
            short loudNoise = (short) Math.round(uniformNoise * 14_000.0);
            int offset = frame * 4;
            pcm[offset] = (byte) melody;
            pcm[offset + 1] = (byte) (melody >>> 8);
            pcm[offset + 2] = (byte) loudNoise;
            pcm[offset + 3] = (byte) (loudNoise >>> 8);
        }
        AudioFormat format = new AudioFormat(sampleRate, 16, 2, true, false);
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(pcm), format, frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, output.toFile());
        }
    }

    private static double rms(float[] samples) {
        double sum = 0.0;
        for (float sample : samples) sum += sample * sample;
        return Math.sqrt(sum / Math.max(1, samples.length));
    }


    private static double maximumDifference(float[] first, float[] second) {
        if (first.length != second.length) return Double.POSITIVE_INFINITY;
        double maximum = 0.0;
        for (int index = 0; index < first.length; index++) {
            maximum = Math.max(maximum, Math.abs(first[index] - second[index]));
        }
        return maximum;
    }
}
