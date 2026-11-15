package com.makambox.studio.ui;

import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.PitchFrame;

import java.util.Arrays;
import java.util.List;
import java.util.function.DoublePredicate;

/**
 * Estimates a stable tonic from a user-selected time range.
 *
 * <p>The reliable academic pitch layer is always preferred.  When a quiet MP3
 * or a decaying final sound contains fewer than four reliable frames, the
 * already-computed visual candidate layer may still offer a manually
 * confirmable value, but only if at least three candidates form one compact
 * cluster.  This preserves the earlier selection workflow without re-analysing
 * raw PCM behind the user's back.</p>
 */
final class TonicSelectionEstimator {
    private static final int MINIMUM_EVIDENCE_FRAMES = 3;
    private static final int RELIABLE_TARGET_FRAMES = 4;
    private static final double MAXIMUM_AUDIT_MAD_CENT = 45.0;
    private static final double MAXIMUM_AUDIT_INLIER_LIMIT_CENT = 60.0;

    private TonicSelectionEstimator() { }

    static Estimate estimate(List<PitchFrame> frames, DoublePredicate includesTime) {
        List<Evidence> reliable = frames.stream()
                .filter(frame -> includesTime.test(frame.timeSeconds()))
                .filter(PitchFrame::voiced)
                .filter(frame -> frame.confidence() >= PitchTracker.RELIABLE_CONFIDENCE)
                .map(frame -> new Evidence(frame.absoluteCent(), frame.confidence()))
                .toList();
        if (reliable.size() >= RELIABLE_TARGET_FRAMES) {
            return robustEstimate(reliable, false);
        }

        List<Evidence> candidates = frames.stream()
                .filter(frame -> includesTime.test(frame.timeSeconds()))
                .filter(PitchFrame::candidateVoiced)
                .filter(frame -> frame.confidence() >= PitchTracker.VISUAL_CANDIDATE_CONFIDENCE)
                .map(frame -> new Evidence(frame.voiced()
                                ? frame.absoluteCent() : frame.candidateAbsoluteCent(),
                        frame.confidence()))
                .toList();
        return robustEstimate(candidates, true);
    }

    private static Estimate robustEstimate(List<Evidence> evidence, boolean auditFallback) {
        if (evidence.size() < MINIMUM_EVIDENCE_FRAMES) return null;
        double[] cents = evidence.stream().mapToDouble(Evidence::cent).sorted().toArray();
        double medianCent = median(cents);
        double[] deviations = Arrays.stream(cents)
                .map(value -> Math.abs(value - medianCent)).sorted().toArray();
        double mad = median(deviations);
        if (auditFallback && (!Double.isFinite(mad) || mad > MAXIMUM_AUDIT_MAD_CENT)) {
            return null;
        }
        double limit = Math.max(18.0, 3.5 * mad);
        if (auditFallback) limit = Math.min(limit, MAXIMUM_AUDIT_INLIER_LIMIT_CENT);
        final double inlierLimit = limit;
        List<Evidence> inliers = evidence.stream()
                .filter(value -> Math.abs(value.cent() - medianCent) <= inlierLimit).toList();
        if (inliers.size() < MINIMUM_EVIDENCE_FRAMES) return null;

        double finalCent = median(inliers.stream().mapToDouble(Evidence::cent).sorted().toArray());
        double confidence = inliers.stream().mapToDouble(Evidence::confidence).average().orElse(0.0);
        return new Estimate(PitchTracker.centToHertz(finalCent), finalCent,
                confidence, inliers.size(), auditFallback);
    }

    private static double median(double[] values) {
        if (values.length == 0) return Double.NaN;
        int middle = values.length / 2;
        return values.length % 2 == 0
                ? (values[middle - 1] + values[middle]) / 2.0 : values[middle];
    }

    record Estimate(double hertz, double absoluteCent, double confidence,
                    int count, boolean auditFallback) { }

    private record Evidence(double cent, double confidence) { }
}
