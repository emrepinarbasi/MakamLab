package com.makambox.studio.ui;

import com.makambox.studio.audio.AudioLoader;
import com.makambox.studio.dsp.AnalysisAudioResampler;
import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.PitchFrame;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * End-to-end regression for the decoded-MP3 -> wave editor -> edited-WAV hand-off.
 *
 * <p>The editor always receives decoded PCM, regardless of whether the original
 * container was MP3, M4A or WAV.  This check therefore gives the source its MP3
 * identity, performs a time-axis edit, writes and reloads the edited WAV, then
 * verifies that the post-edit selection still addresses real F0 frames and can
 * supply a tonic candidate.</p>
 */
public final class MakamWaveEditorTransferRegressionTest {
    private MakamWaveEditorTransferRegressionTest() { }

    public static void main(String[] arguments) throws Exception {
        int sampleRate = 44_100;
        float[] decodedMp3Samples = sine(sampleRate, 2.5, 220.0);
        AudioData decodedMp3 = new AudioData("karar-kaydi.mp3", Path.of("karar-kaydi.mp3"),
                sampleRate, decodedMp3Samples);
        WaveformEditModel model = new WaveformEditModel(decodedMp3);

        // Removing the first half-second proves that the transferred interval is
        // expressed on the edited timeline, not the original MP3 timeline.
        model.delete(0, model.secondsToSample(.5));
        double selectedStart = .35;
        double selectedEnd = .95;

        Path directory = Files.createTempDirectory("makamlab-editor-transfer-");
        Path target = directory.resolve("karar-kaydi-duzenlenmis.wav");
        try {
            MakamWaveEditorDialog.writeWave(target, model);
            MakamWaveEditorDialog.EditResult transfer = new MakamWaveEditorDialog.EditResult(
                    target, selectedStart, selectedEnd);
            AudioData reloaded = new AudioLoader().load(transfer.path());

            if (transfer.selectionStartSeconds() < 0.0
                    || transfer.selectionEndSeconds() > reloaded.durationSeconds()
                    || transfer.selectionEndSeconds() - transfer.selectionStartSeconds() < .02) {
                throw new AssertionError("Editor returned an invalid post-edit selection: " + transfer);
            }

            List<PitchFrame> frames = new PitchTracker().track(
                    AnalysisAudioResampler.forPitchTracking(reloaded), ignored -> { });
            long available = frames.stream()
                    .filter(frame -> frame.timeSeconds() >= transfer.selectionStartSeconds())
                    .filter(frame -> frame.timeSeconds() <= transfer.selectionEndSeconds())
                    .filter(frame -> frame.voiced() || frame.candidateVoiced())
                    .count();
            if (available < 3) {
                throw new AssertionError("Transferred selection has no usable F0 frames: " + available);
            }

            TonicSelectionEstimator.Estimate estimate = TonicSelectionEstimator.estimate(frames,
                    time -> time >= transfer.selectionStartSeconds()
                            && time <= transfer.selectionEndSeconds());
            if (estimate == null || !Double.isFinite(estimate.hertz())) {
                throw new AssertionError("Transferred selection did not produce a tonic candidate");
            }
            double errorCent = 1200.0 * Math.log(estimate.hertz() / 220.0) / Math.log(2.0);
            if (Math.abs(errorCent) > 6.0) {
                throw new AssertionError("Transferred tonic moved by " + errorCent + " C");
            }
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
        System.out.println("MakamWaveEditorTransferRegressionTest passed");
    }

    private static float[] sine(int sampleRate, double seconds, double hertz) {
        float[] samples = new float[(int) Math.round(sampleRate * seconds)];
        for (int index = 0; index < samples.length; index++) {
            samples[index] = (float) (.62 * Math.sin(2.0 * Math.PI * hertz * index / sampleRate));
        }
        return samples;
    }
}
