package com.makambox.studio.dsp;

import com.makambox.studio.model.AudioData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Low-confidence, selection-local tonic fallback used only after the normal
 * reliable/candidate pitch-frame evidence has failed.
 *
 * <p>The academic pitch track is deliberately left untouched.  This estimator
 * re-examines only the PCM explicitly selected by the user, uses a relaxed YIN
 * gate, and requires a compact majority cluster before it offers a manually
 * confirmable value.  Octave-related candidates are folded toward the existing
 * automatic-tonic context when that context is available.</p>
 */
public final class ManualTonicSelectionEstimator {
    private static final double FRAME_SECONDS = 0.040;
    private static final double HOP_SECONDS = 0.020;
    private static final double EVIDENCE_SECONDS = 0.120;
    private static final double MINIMUM_SELECTION_SECONDS = 0.080;
    private static final double MINIMUM_RMS = 0.0035;
    private static final double RELAXED_YIN_THRESHOLD = 0.65;
    private static final double MINIMUM_CANDIDATE_CONFIDENCE = 0.35;
    private static final double MINIMUM_INLIER_SHARE = 0.55;
    private static final double MINIMUM_FRAME_COVERAGE = 0.30;
    private static final double MAXIMUM_ROBUST_SPREAD_CENT = 35.0;

    public record Estimate(double hertz, double confidence, int frameCount,
                           double robustSpreadCent, boolean octaveFolded) { }

    public Estimate estimate(AudioData audio, double requestedStartSeconds,
                             double requestedEndSeconds, double referenceHertz) {
        if (audio == null || audio.samples().length == 0
                || !Double.isFinite(requestedStartSeconds)
                || !Double.isFinite(requestedEndSeconds)) return null;

        double startSeconds = Math.max(0.0,
                Math.min(requestedStartSeconds, requestedEndSeconds));
        double endSeconds = Math.min(audio.durationSeconds(),
                Math.max(requestedStartSeconds, requestedEndSeconds));
        if (endSeconds - startSeconds + 1.0e-9 < MINIMUM_SELECTION_SECONDS) return null;

        int sampleRate = Math.round(audio.sampleRate());
        int frameSize = Math.max(512, (int) Math.round(sampleRate * FRAME_SECONDS));
        int hopSize = Math.max(256, (int) Math.round(sampleRate * HOP_SECONDS));
        int evidenceSize = Math.max(frameSize,
                (int) Math.round(sampleRate * EVIDENCE_SECONDS));
        int selectionStart = Math.max(0,
                Math.min(audio.samples().length, (int) Math.floor(startSeconds * sampleRate)));
        int selectionEnd = Math.max(selectionStart,
                Math.min(audio.samples().length, (int) Math.ceil(endSeconds * sampleRate)));
        int possibleFrames = 1 + Math.max(0, (selectionEnd - selectionStart - frameSize) / hopSize);
        if (possibleFrames < 3) return null;

        YinPitchDetector detector = new YinPitchDetector(audio.sampleRate(), frameSize,
                RELAXED_YIN_THRESHOLD, 45.0, 1600.0);
        float[] frame = new float[frameSize];
        float[] evidence = new float[evidenceSize];
        float[] aggregateEvidence = aggregateEvidence(audio.samples(), selectionStart,
                selectionEnd, sampleRate);
        ArrayList<Observation> candidates = new ArrayList<>();
        boolean foldedAny = false;
        for (int index = 0, frameStart = selectionStart;
             frameStart + frameSize <= selectionEnd;
             index++, frameStart += hopSize) {
            System.arraycopy(audio.samples(), frameStart, frame, 0, frameSize);
            removeDc(frame);
            if (rms(frame) < MINIMUM_RMS) continue;
            fillSelectionEvidence(audio.samples(), selectionStart, selectionEnd,
                    frameStart, frameSize, evidence);
            removeDc(evidence);
            YinPitchDetector.Result result = detector.detect(frame, evidence);
            if (!(result.hertz() > 0.0)
                    || result.confidence() + 1.0e-9 < MINIMUM_CANDIDATE_CONFIDENCE) continue;
            double spectral = promoteSubharmonic(result.hertz(), aggregateEvidence,
                    audio.sampleRate());
            double folded = foldToReferenceOctave(spectral, referenceHertz);
            foldedAny |= octaveDistanceCent(result.hertz(), folded) >= 600.0;
            candidates.add(new Observation(frameStart / (double) sampleRate,
                    PitchTracker.hertzToCent(folded), result.confidence()));
        }
        if (candidates.size() < 3) return null;

        double medianCent = median(candidates.stream()
                .mapToDouble(Observation::cent).sorted().toArray());
        double mad = median(candidates.stream()
                .mapToDouble(value -> Math.abs(value.cent() - medianCent)).sorted().toArray());
        double limit = Math.max(22.0, Math.min(70.0, 3.5 * mad));
        List<Observation> inliers = candidates.stream()
                .filter(value -> Math.abs(value.cent() - medianCent) <= limit).toList();
        if (inliers.size() < 3
                || inliers.size() + 1.0e-9 < candidates.size() * MINIMUM_INLIER_SHARE
                || inliers.size() + 1.0e-9 < possibleFrames * MINIMUM_FRAME_COVERAGE) return null;

        double finalCent = median(inliers.stream()
                .mapToDouble(Observation::cent).sorted().toArray());
        double spread = median(inliers.stream()
                .mapToDouble(value -> Math.abs(value.cent() - finalCent)).sorted().toArray());
        if (!Double.isFinite(spread) || spread > MAXIMUM_ROBUST_SPREAD_CENT) return null;
        double first = inliers.get(0).timeSeconds();
        double last = inliers.get(inliers.size() - 1).timeSeconds();
        if (last - first + FRAME_SECONDS + 1.0e-9 < MINIMUM_SELECTION_SECONDS) return null;

        double meanConfidence = inliers.stream().mapToDouble(Observation::confidence)
                .average().orElse(0.0);
        double purity = inliers.size() / (double) candidates.size();
        double coverage = Math.min(1.0, inliers.size() / (double) possibleFrames);
        double stability = Math.max(0.0,
                1.0 - spread / MAXIMUM_ROBUST_SPREAD_CENT);
        double confidence = 0.45 * meanConfidence + 0.25 * coverage
                + 0.20 * purity + 0.10 * stability;
        // This is intentionally a manual/audit fallback and must never be
        // presented as a report-quality >=65% pitch-frame decision.
        confidence = Math.max(MINIMUM_CANDIDATE_CONFIDENCE, Math.min(0.64, confidence));
        return new Estimate(PitchTracker.centToHertz(finalCent), confidence,
                inliers.size(), spread, foldedAny);
    }

    private static void fillSelectionEvidence(float[] samples, int selectionStart,
                                              int selectionEnd, int frameStart,
                                              int frameSize, float[] evidence) {
        Arrays.fill(evidence, 0f);
        int requestedStart = frameStart + frameSize / 2 - evidence.length / 2;
        int sourceStart = Math.max(selectionStart, requestedStart);
        int sourceEnd = Math.min(selectionEnd, requestedStart + evidence.length);
        if (sourceEnd <= sourceStart) return;
        int destinationStart = sourceStart - requestedStart;
        System.arraycopy(samples, sourceStart, evidence, destinationStart,
                sourceEnd - sourceStart);
    }

    private static double foldToReferenceOctave(double hertz, double referenceHertz) {
        if (!(hertz > 0.0) || !(referenceHertz > 0.0)
                || !Double.isFinite(referenceHertz)) return hertz;
        int octave = (int) Math.round(Math.log(referenceHertz / hertz) / Math.log(2.0));
        octave = Math.max(-4, Math.min(4, octave));
        double folded = hertz * Math.pow(2.0, octave);
        return folded >= 45.0 && folded <= 1600.0 ? folded : hertz;
    }

    /**
     * Uses the complete selected sound (bounded to a short centre window) to
     * disambiguate a weak common-period f/2 or f/3 candidate.  Frame-local
     * decisions can alternate between 55, 110 and 220 Hz in a complex decay;
     * the aggregate spectrum is much less sensitive to that alternation.
     */
    private static double promoteSubharmonic(double hertz, float[] evidence,
                                             float sampleRate) {
        if (!(hertz > 0.0) || hertz >= 120.0 || evidence.length < 32) return hertz;
        double first = sinusoidalPower(evidence, sampleRate, hertz);
        double second = hertz * 2.0 <= 1600.0
                ? sinusoidalPower(evidence, sampleRate, hertz * 2.0) : 0.0;
        double third = hertz * 3.0 <= 1600.0
                ? sinusoidalPower(evidence, sampleRate, hertz * 3.0) : 0.0;
        double strongestUpper = Math.max(second, third);
        if (strongestUpper <= 1.0e-14 || first / strongestUpper >= 0.018) return hertz;
        if (second > third * 1.35) return hertz * 2.0;
        if (third > second * 1.35) return hertz * 3.0;
        return hertz;
    }

    private static float[] aggregateEvidence(float[] samples, int selectionStart,
                                             int selectionEnd, int sampleRate) {
        int maximum = Math.max(512, (int) Math.round(sampleRate * 1.20));
        int length = Math.min(maximum, Math.max(0, selectionEnd - selectionStart));
        if (length <= 0) return new float[0];
        int start = selectionStart + Math.max(0,
                (selectionEnd - selectionStart - length) / 2);
        float[] result = Arrays.copyOfRange(samples, start, start + length);
        removeDc(result);
        return result;
    }

    private static double sinusoidalPower(float[] samples, float sampleRate,
                                          double frequency) {
        double angle = -2.0 * Math.PI * frequency / sampleRate;
        double stepReal = Math.cos(angle);
        double stepImaginary = Math.sin(angle);
        double oscillatorReal = 1.0;
        double oscillatorImaginary = 0.0;
        double sumReal = 0.0;
        double sumImaginary = 0.0;
        for (int index = 0; index < samples.length; index++) {
            double window = samples.length <= 1 ? 1.0
                    : 0.5 - 0.5 * Math.cos(2.0 * Math.PI * index
                    / (samples.length - 1.0));
            double sample = samples[index] * window;
            sumReal += sample * oscillatorReal;
            sumImaginary += sample * oscillatorImaginary;
            double nextReal = oscillatorReal * stepReal
                    - oscillatorImaginary * stepImaginary;
            oscillatorImaginary = oscillatorReal * stepImaginary
                    + oscillatorImaginary * stepReal;
            oscillatorReal = nextReal;
        }
        return sumReal * sumReal + sumImaginary * sumImaginary;
    }

    private static double octaveDistanceCent(double first, double second) {
        return Math.abs(1200.0 * Math.log(second / first) / Math.log(2.0));
    }

    private static void removeDc(float[] samples) {
        double mean = 0.0;
        for (float sample : samples) mean += sample;
        mean /= Math.max(1, samples.length);
        for (int index = 0; index < samples.length; index++) samples[index] -= (float) mean;
    }

    private static double rms(float[] samples) {
        double sum = 0.0;
        for (float sample : samples) sum += sample * sample;
        return Math.sqrt(sum / Math.max(1, samples.length));
    }

    private static double median(double[] values) {
        if (values.length == 0) return Double.NaN;
        int middle = values.length / 2;
        return values.length % 2 == 0
                ? (values[middle - 1] + values[middle]) / 2.0 : values[middle];
    }

    private record Observation(double timeSeconds, double cent, double confidence) { }
}
