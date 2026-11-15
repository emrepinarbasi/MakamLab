package com.makambox.studio.ui;

import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.PitchFrame;

import java.nio.file.Path;
import java.util.List;

/** Headless-safe regression for the graph-region tonic selector used by Makam. */
public final class MakamFrameRegressionTest {
    public static void main(String[] args) {
        double center = PitchTracker.hertzToCent(123.45);
        List<PitchFrame> frames = List.of(
                frame(.00, center - 1.5, .92), frame(.02, center + .5, .94),
                frame(.04, center + 1.0, .91), frame(.06, center - .4, .96),
                frame(.08, center + 180.0, .70), frame(.10, center, .95));
        double result = MakamFrame.diagnosticRobustCandidateHertz(frames, 0.0, .12);
        if (!Double.isFinite(result) || Math.abs(1200.0 * Math.log(result / 123.45) / Math.log(2.0)) > 2.0) {
            throw new AssertionError("Robust graph tonic selection failed: " + result);
        }
        if (!Double.isNaN(MakamFrame.diagnosticRobustCandidateHertz(frames.subList(0, 2), 0.0, .03))) {
            throw new AssertionError("Insufficient evidence must not create a tonic candidate");
        }

        // PitchTracker deliberately keeps 35–65% confidence observations out of the
        // academic F0 layer while retaining them in the visible candidate layer.  A
        // compact group may be offered for the user's explicit tonic confirmation,
        // preserving quiet MP3/decay selections without re-analysing raw PCM.
        List<PitchFrame> candidateOnlyFrames = List.of(
                candidateFrame(.00, center - 1.1, .54),
                candidateFrame(.02, center + .7, .57),
                candidateFrame(.04, center - .3, .51),
                candidateFrame(.06, center + .9, .59),
                candidateFrame(.08, center + 165.0, .38));
        double candidateLayerResult = MakamFrame.diagnosticRobustCandidateHertz(
                candidateOnlyFrames, 0.0, .08);
        if (!Double.isFinite(candidateLayerResult)
                || Math.abs(1200.0 * Math.log(candidateLayerResult / 123.45) / Math.log(2.0)) > 2.0) {
            throw new AssertionError("Compact visual candidates should remain manually confirmable: "
                    + candidateLayerResult);
        }

        List<PitchFrame> dispersedCandidateFrames = List.of(
                candidateFrame(.00, center - 360.0, .52),
                candidateFrame(.02, center - 210.0, .55),
                candidateFrame(.04, center - 65.0, .58),
                candidateFrame(.06, center + 95.0, .56),
                candidateFrame(.08, center + 245.0, .53),
                candidateFrame(.10, center + 410.0, .57));
        if (!Double.isNaN(MakamFrame.diagnosticRobustCandidateHertz(
                dispersedCandidateFrames, 0.0, .10))) {
            throw new AssertionError("A scattered visual-candidate range must not become a tonic");
        }

        // A fading/harmonically ambiguous selection can contain no retained
        // PitchFrame at all.  The deliberately selected PCM may still provide a
        // compact low-confidence candidate for explicit user confirmation.
        int sampleRate = 44_100;
        float[] harmonicTail = new float[(int) Math.round(sampleRate * .42)];
        for (int index = 0; index < harmonicTail.length; index++) {
            double second = index / (double) sampleRate;
            harmonicTail[index] = (float) (0.02 * Math.sin(2.0 * Math.PI * 110.0 * second)
                    + 0.31 * Math.sin(2.0 * Math.PI * 220.0 * second)
                    + 0.16 * Math.sin(2.0 * Math.PI * 440.0 * second));
        }
        AudioData selectedAudio = new AudioData("edited-tail.wav", Path.of("edited-tail.wav"),
                sampleRate, harmonicTail);
        double manualResult = MakamFrame.diagnosticSelectedTonicCandidateHertz(
                selectedAudio, List.of(), .04, .38, 195.8);
        if (!Double.isFinite(manualResult)
                || Math.abs(1200.0 * Math.log(manualResult / 220.0) / Math.log(2.0)) > 25.0) {
            throw new AssertionError("PCM-only manual selection was not recovered near 220 Hz: "
                    + manualResult);
        }
        System.out.println("MakamFrameRegressionTest passed");
    }

    private static PitchFrame frame(double time, double cent, double confidence) {
        return new PitchFrame(time, PitchTracker.centToHertz(cent), cent, confidence, .1);
    }

    private static PitchFrame candidateFrame(double time, double cent, double confidence) {
        return new PitchFrame(time, 0.0, Double.NaN, confidence, .1,
                PitchTracker.centToHertz(cent), cent);
    }
}
