package com.makambox.studio.study.hicaz;

import com.makambox.studio.model.AnalysisConfig;

import java.util.Objects;

/** Direct 2→3 or 3→2 measurement; skipped-degree motion is never substituted. */
public record HicazAugmentedIntervalMeasurement(
        int index,
        int phraseIndex,
        HicazPhraseDirection direction,
        HicazDegreeEvent secondDegree,
        HicazDegreeEvent thirdDegree,
        double observedIntervalCent,
        double deviationCent,
        double confidence,
        boolean included,
        String auditReason
) {
    public static final double THEORETICAL_INTERVAL_HC = 12.0;
    public static final double THEORETICAL_INTERVAL_CENT =
            THEORETICAL_INTERVAL_HC * AnalysisConfig.COMMA_CENT;

    public HicazAugmentedIntervalMeasurement {
        if (index < 1 || phraseIndex < 1) {
            throw new IllegalArgumentException("indices must be positive");
        }
        direction = Objects.requireNonNull(direction, "direction");
        secondDegree = Objects.requireNonNull(secondDegree, "secondDegree");
        thirdDegree = Objects.requireNonNull(thirdDegree, "thirdDegree");
        if (secondDegree.degree() != HicazTetrachordDegree.DEGREE_2
                || thirdDegree.degree() != HicazTetrachordDegree.DEGREE_3) {
            throw new IllegalArgumentException("direct interval requires degree 2 and degree 3");
        }
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        auditReason = Objects.requireNonNullElse(auditReason, "");
    }

    public double theoreticalIntervalCent() { return THEORETICAL_INTERVAL_CENT; }
    public double theoreticalIntervalHc() { return THEORETICAL_INTERVAL_HC; }
    public double observedIntervalHc() { return observedIntervalCent / AnalysisConfig.COMMA_CENT; }
    public double deviationHc() { return deviationCent / AnalysisConfig.COMMA_CENT; }
    public double startSeconds() {
        return direction == HicazPhraseDirection.ASCENDING
                ? secondDegree.startSeconds() : thirdDegree.startSeconds();
    }
    public double endSeconds() {
        return direction == HicazPhraseDirection.ASCENDING
                ? thirdDegree.endSeconds() : secondDegree.endSeconds();
    }
}
