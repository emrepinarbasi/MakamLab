package com.makambox.studio.ui;

import com.makambox.studio.model.AudioData;

import java.nio.file.Path;
import java.util.Arrays;

/** Memory-safe edit-list regression for Makam's Audacity-like waveform editor. */
public final class WaveformEditModelRegressionTest {
    public static void main(String[] args) {
        float[] samples = new float[20];
        for (int index = 0; index < samples.length; index++) samples[index] = index / 20f;
        WaveformEditModel model = new WaveformEditModel(
                new AudioData("test.wav", Path.of("test.wav"), 10f, samples));

        model.splitAt(5);
        model.splitAt(15);
        if (!model.splitPoints().equals(java.util.List.of(5, 15))) {
            throw new AssertionError("Split markers were not retained");
        }

        model.cut(5, 10);
        assertSamples(model.materialize(), 0, 1, 2, 3, 4, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19);
        if (!model.hasClipboard() || model.sampleCount() != 15) throw new AssertionError("Cut failed");
        if (Math.abs(model.sourceSecondsAt(5) - 1.0) > 1e-9) {
            throw new AssertionError("Edited time no longer maps to source time");
        }

        model.pasteAt(5);
        assertSamples(model.materialize(), 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
                10, 11, 12, 13, 14, 15, 16, 17, 18, 19);
        model.undo();
        if (model.sampleCount() != 15) throw new AssertionError("Undo paste failed");
        model.undo();
        if (model.sampleCount() != 20) throw new AssertionError("Undo cut failed");

        model.trimTo(4, 12);
        assertSamples(model.materialize(), 4, 5, 6, 7, 8, 9, 10, 11);
        WaveformCache.Range range = model.range(0, model.sampleCount());
        if (Math.abs(range.minimum() - .20f) > 1e-6 || Math.abs(range.maximum() - .55f) > 1e-6) {
            throw new AssertionError("Waveform range failed: " + range);
        }

        model.delete(2, 5);
        assertSamples(model.materialize(), 4, 5, 9, 10, 11);
        System.out.println("WaveformEditModelRegressionTest passed");
    }

    private static void assertSamples(float[] actual, int... sourceIndexes) {
        float[] expected = new float[sourceIndexes.length];
        for (int i = 0; i < sourceIndexes.length; i++) expected[i] = sourceIndexes[i] / 20f;
        if (!Arrays.equals(actual, expected)) {
            throw new AssertionError("Expected " + Arrays.toString(expected)
                    + " but got " + Arrays.toString(actual));
        }
    }
}
