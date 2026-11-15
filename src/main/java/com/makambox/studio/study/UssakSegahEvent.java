package com.makambox.studio.study;

import java.util.Objects;

/** One auditable functional Segâh candidate and its full phrase context. */
public record UssakSegahEvent(
        int index,
        StudyPitchEvent measurement,
        UssakSegahUsageClass usageClass,
        String previousAnchorKey,
        String previousAnchorName,
        String nextAnchorKey,
        String nextAnchorName,
        String pathLabel,
        double classificationConfidence,
        double localTonicHertz,
        double localDeviationCent,
        boolean included,
        String exclusionReason
) {
    public UssakSegahEvent {
        if (index < 1) throw new IllegalArgumentException("index must be positive");
        measurement = Objects.requireNonNull(measurement, "measurement");
        usageClass = Objects.requireNonNull(usageClass, "usageClass");
        previousAnchorKey = Objects.requireNonNullElse(previousAnchorKey, "");
        previousAnchorName = Objects.requireNonNullElse(previousAnchorName, "");
        nextAnchorKey = Objects.requireNonNullElse(nextAnchorKey, "");
        nextAnchorName = Objects.requireNonNullElse(nextAnchorName, "");
        pathLabel = pathLabel == null || pathLabel.isBlank()
                ? usageClass.defaultPathLabel() : pathLabel;
        classificationConfidence = Math.max(0.0, Math.min(1.0, classificationConfidence));
        exclusionReason = Objects.requireNonNullElse(exclusionReason, "");
    }

    public double globalDeviationCent() { return measurement.deviationCent(); }
    public double globalDeviationHc() { return measurement.deviationHc(); }
    public double localDeviationHc() {
        return Double.isFinite(localDeviationCent)
                ? localDeviationCent / com.makambox.studio.model.AnalysisConfig.COMMA_CENT
                : Double.NaN;
    }
    public String intonationLabel() { return measurement.intonationLabel(); }
}
