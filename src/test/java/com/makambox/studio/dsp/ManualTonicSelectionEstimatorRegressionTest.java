package com.makambox.studio.dsp;

import com.makambox.studio.audio.AudioLoader;
import com.makambox.studio.model.AudioData;

import java.nio.file.Path;

/** Regression coverage for the deliberately selection-local manual-tonic fallback. */
public final class ManualTonicSelectionEstimatorRegressionTest {
    private static final float SAMPLE_RATE = 44_100f;

    public static void main(String[] args) throws Exception {
        octaveAmbiguousHarmonicSoundUsesTheAutomaticRegisterContext();
        scatteredTonesAreRejected();
        shortSelectionIsRejected();
        if (args.length > 0) verifyEditedTail(Path.of(args[0]));
        System.out.println("MANUAL_TONIC_SELECTION_ESTIMATOR_OK");
    }

    private static void octaveAmbiguousHarmonicSoundUsesTheAutomaticRegisterContext() {
        float[] samples = new float[Math.round(SAMPLE_RATE * .60f)];
        for (int index = 0; index < samples.length; index++) {
            double second = index / SAMPLE_RATE;
            // 220 and 330 Hz share a convincing 110 Hz mathematical period;
            // a very weak 110 Hz component makes the register intentionally
            // ambiguous without changing the audible 220 Hz centre.
            samples[index] = (float) (0.015 * Math.sin(2.0 * Math.PI * 110.0 * second)
                    + 0.32 * Math.sin(2.0 * Math.PI * 220.0 * second)
                    + 0.13 * Math.sin(2.0 * Math.PI * 330.0 * second)
                    + 0.20 * Math.sin(2.0 * Math.PI * 440.0 * second));
        }
        AudioData audio = new AudioData("octave-ambiguous.wav", Path.of("octave-ambiguous.wav"),
                SAMPLE_RATE, samples);
        ManualTonicSelectionEstimator.Estimate estimate =
                new ManualTonicSelectionEstimator().estimate(audio, .08, .52, 198.0);
        check(estimate != null, "Octave-ambiguous manual selection was rejected");
        check(cents(estimate.hertz(), 220.0) < 25.0,
                "Manual selection remained in the wrong register: " + estimate.hertz());
        check(estimate.confidence() >= .35 && estimate.confidence() < .65,
                "Fallback must remain explicitly low-confidence: " + estimate.confidence());
        check(estimate.frameCount() >= 3, "Fallback returned insufficient frame evidence");
    }

    private static void scatteredTonesAreRejected() {
        double[] tones = {118.0, 177.0, 271.0, 403.0};
        int segment = Math.round(SAMPLE_RATE * .12f);
        float[] samples = new float[segment * tones.length];
        for (int block = 0; block < tones.length; block++) {
            for (int offset = 0; offset < segment; offset++) {
                int index = block * segment + offset;
                double second = index / SAMPLE_RATE;
                samples[index] = (float) (0.30 * Math.sin(
                        2.0 * Math.PI * tones[block] * second));
            }
        }
        AudioData audio = new AudioData("scattered.wav", Path.of("scattered.wav"),
                SAMPLE_RATE, samples);
        ManualTonicSelectionEstimator.Estimate estimate =
                new ManualTonicSelectionEstimator().estimate(audio, 0.0,
                        audio.durationSeconds(), Double.NaN);
        check(estimate == null, "Scattered tones must not become a tonic: " + estimate);
    }

    private static void shortSelectionIsRejected() {
        float[] samples = new float[Math.round(SAMPLE_RATE * .20f)];
        for (int index = 0; index < samples.length; index++) {
            samples[index] = (float) (0.30 * Math.sin(
                    2.0 * Math.PI * 220.0 * index / SAMPLE_RATE));
        }
        AudioData audio = new AudioData("short.wav", Path.of("short.wav"), SAMPLE_RATE, samples);
        check(new ManualTonicSelectionEstimator().estimate(audio, .02, .07, 220.0) == null,
                "A sub-80 ms selection must not create a tonic");
    }

    private static void verifyEditedTail(Path path) throws Exception {
        AudioData audio = new AudioLoader().load(path);
        ManualTonicSelectionEstimator.Estimate estimate =
                new ManualTonicSelectionEstimator().estimate(audio, 80.50,
                        Math.min(80.78, audio.durationSeconds()), 195.806);
        check(estimate != null, "Verified edited tail produced no manual candidate");
        check(cents(estimate.hertz(), 220.5) < 35.0,
                "Verified edited tail did not resolve near 220 Hz: " + estimate.hertz());
        check(estimate.confidence() < .65,
                "Verified manual fallback was presented as report-quality evidence");
    }

    private static double cents(double actual, double expected) {
        return Math.abs(1200.0 * Math.log(actual / expected) / Math.log(2.0));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
