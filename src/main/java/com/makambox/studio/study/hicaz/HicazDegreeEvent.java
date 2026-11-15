package com.makambox.studio.study.hicaz;

import com.makambox.studio.model.AnalysisConfig;

import java.util.Objects;

/** One auditable F0 event assigned to a functional Hicaz degree. */
public record HicazDegreeEvent(
        int index,
        int sourceEventIndex,
        HicazTetrachordDegree degree,
        double startSeconds,
        double endSeconds,
        double durationSeconds,
        double observedHertz,
        double observedRelativeCent,
        double deviationCent,
        double confidence,
        boolean included,
        String auditReason
) {
    public HicazDegreeEvent {
        if (index < 1) throw new IllegalArgumentException("index must be positive");
        degree = Objects.requireNonNull(degree, "degree");
        if (!Double.isFinite(startSeconds) || !Double.isFinite(endSeconds)
                || endSeconds + 1.0e-9 < startSeconds) {
            throw new IllegalArgumentException("invalid event time range");
        }
        if (!Double.isFinite(observedHertz) || observedHertz <= 0.0) {
            throw new IllegalArgumentException("observedHertz must be finite and positive");
        }
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        auditReason = Objects.requireNonNullElse(auditReason, "");
    }

    public double theoreticalRelativeCent() { return degree.theoreticalCent(); }
    public double theoreticalRelativeHc() { return degree.theoreticalHc(); }
    public double observedRelativeHc() { return observedRelativeCent / AnalysisConfig.COMMA_CENT; }
    public double deviationHc() { return deviationCent / AnalysisConfig.COMMA_CENT; }
    public boolean pest() { return deviationCent < -1.0e-9; }
    public boolean dik() { return deviationCent > 1.0e-9; }
    public String intonationLabel() { return pest() ? "Pest" : dik() ? "Dik" : "Kuramsal merkez"; }
}
