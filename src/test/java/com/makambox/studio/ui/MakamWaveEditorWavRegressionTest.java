package com.makambox.studio.ui;

import com.makambox.studio.audio.AudioLoader;
import com.makambox.studio.model.AudioData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Ensures edited audio is persisted as a distinct, reloadable WAV analysis source. */
public final class MakamWaveEditorWavRegressionTest {
    public static void main(String[] args) throws Exception {
        float[] source = new float[4_000];
        for (int i = 0; i < source.length; i++) source[i] = (float) (.6 * Math.sin(2 * Math.PI * 220 * i / 8_000.0));
        WaveformEditModel model = new WaveformEditModel(
                new AudioData("source.wav", Path.of("source.wav"), 8_000f, source));
        model.delete(1_000, 2_000);
        Path directory = Files.createTempDirectory("makam-wave-edit-test-");
        Path target = directory.resolve("edited.wav");
        try {
            ArrayList<Integer> progress = new ArrayList<>();
            MakamWaveEditorDialog.writeWave(target, model, progress::add);
            if (progress.isEmpty() || progress.get(progress.size() - 1) != 100) {
                throw new AssertionError("Edited WAV progress did not finish at 100: " + progress);
            }
            AudioData loaded = new AudioLoader().load(target);
            if (loaded.samples().length != 3_000 || Math.abs(loaded.sampleRate() - 8_000f) > .1f) {
                throw new AssertionError("Edited WAV did not round-trip: " + loaded.samples().length);
            }
            if (loaded.source().equals(model.source().source())) {
                throw new AssertionError("Edited WAV must have a distinct cache/source identity");
            }
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
        System.out.println("MakamWaveEditorWavRegressionTest passed");
    }
}
