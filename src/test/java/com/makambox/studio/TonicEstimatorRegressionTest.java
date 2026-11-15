package com.makambox.studio;

import com.makambox.studio.analysis.TonicEstimator;
import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.PitchFrame;

import java.util.ArrayList;
import java.util.List;

/** Ensures the final stable note, rather than the global modal pitch, defines the tonic. */
public final class TonicEstimatorRegressionTest {
    public static void main(String[] args) {
        List<PitchFrame> frames = new ArrayList<>();
        add(frames, 220.0, 240);
        silence(frames, 20);
        add(frames, 165.0, 70);
        silence(frames, 8);
        add(frames, 82.5, 30); // simulated YIN sub-octave error on the final note
        TonicEstimator.Estimate estimate = new TonicEstimator().estimate(frames);
        check(Math.abs(estimate.hertz() - 165.0) < 1.5,
                "son kararlı sesin desteklenen oktavı karar olmalı: " + estimate.hertz());
        System.out.printf("TONIC_REGRESSION_OK tonic=%.2f confidence=%.2f%n",
                estimate.hertz(), estimate.confidence());
    }

    private static void add(List<PitchFrame> frames, double hertz, int count) {
        double cent = PitchTracker.hertzToCent(hertz);
        for (int i = 0; i < count; i++) {
            frames.add(new PitchFrame(frames.size() * 0.02, hertz, cent, 0.96, 0.10));
        }
    }

    private static void silence(List<PitchFrame> frames, int count) {
        for (int i = 0; i < count; i++) {
            frames.add(new PitchFrame(frames.size() * 0.02, 0.0, Double.NaN, 0.0, 0.0));
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
