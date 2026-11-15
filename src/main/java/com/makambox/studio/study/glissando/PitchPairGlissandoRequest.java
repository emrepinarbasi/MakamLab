package com.makambox.studio.study.glissando;

import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisConfig;

/** Immutable academic-analysis settings for two selected makam pitches. */
public record PitchPairGlissandoRequest(
        String makamName,
        double tonicHertz,
        String lowerPitchName,
        double lowerRelativeCent,
        String upperPitchName,
        double upperRelativeCent,
        GlissandoScopeMode scopeMode,
        double endpointToleranceHc,
        double minimumEndpointContactSeconds,
        double minimumDurationSeconds,
        double maximumDurationSeconds,
        double minimumSpanHc,
        double minimumMonotonicity,
        double maximumDirectReverseHc,
        double minimumReliableCoverage,
        double reliableConfidence,
        double auditConfidence,
        double maximumFrameStepCent,
        double maximumGapSeconds,
        double intermediateHoldSeconds
) {
    public PitchPairGlissandoRequest {
        if (makamName == null || makamName.isBlank()) throw new IllegalArgumentException("Makam adı gerekli");
        if (!(tonicHertz > 0.0)) throw new IllegalArgumentException("Karar frekansı pozitif olmalı");
        if (lowerPitchName == null || lowerPitchName.isBlank()
                || upperPitchName == null || upperPitchName.isBlank()) {
            throw new IllegalArgumentException("Alt ve üst perde adları gerekli");
        }
        if (!(upperRelativeCent > lowerRelativeCent)) {
            throw new IllegalArgumentException("Üst perde alt perdeden daha yüksek olmalı");
        }
        if (upperRelativeCent - lowerRelativeCent >= 1200.0) {
            throw new IllegalArgumentException("İki perde aynı oktav içindeki bir aralık oluşturmalı");
        }
        if (scopeMode == null) scopeMode = GlissandoScopeMode.OPEN_INTERVAL;
        if (!(endpointToleranceHc > 0.0) || !(minimumEndpointContactSeconds >= 0.0)
                || !(minimumDurationSeconds > 0.0) || !(maximumDurationSeconds >= minimumDurationSeconds)
                || !(minimumSpanHc > 0.0) || !(minimumMonotonicity > 0.0 && minimumMonotonicity <= 1.0)
                || !(maximumDirectReverseHc >= 0.0)
                || !(minimumReliableCoverage > 0.0 && minimumReliableCoverage <= 1.0)
                || !(reliableConfidence > 0.0 && reliableConfidence <= 1.0)
                || !(auditConfidence > 0.0 && auditConfidence <= reliableConfidence)
                || !(maximumFrameStepCent > 0.0) || !(maximumGapSeconds >= 0.0)
                || !(intermediateHoldSeconds >= 0.0)) {
            throw new IllegalArgumentException("Glissando eşikleri geçersiz");
        }
    }

    public static PitchPairGlissandoRequest academicDefaults(
            String makamName, double tonicHertz,
            String lowerPitchName, double lowerRelativeCent,
            String upperPitchName, double upperRelativeCent,
            GlissandoScopeMode scopeMode) {
        double theoreticalSpanHc = (upperRelativeCent - lowerRelativeCent) / AnalysisConfig.COMMA_CENT;
        return new PitchPairGlissandoRequest(makamName, tonicHertz,
                lowerPitchName, lowerRelativeCent, upperPitchName, upperRelativeCent, scopeMode,
                0.60, 0.08, 0.08, 1.80,
                Math.max(0.80, theoreticalSpanHc - 2.0),
                0.62, 0.25, 0.75,
                PitchTracker.RELIABLE_CONFIDENCE, PitchTracker.VISUAL_CANDIDATE_CONFIDENCE,
                170.0, 0.06, 0.28);
    }

    public double theoreticalSpanCent() {
        return upperRelativeCent - lowerRelativeCent;
    }

    public double theoreticalSpanHc() {
        return theoreticalSpanCent() / AnalysisConfig.COMMA_CENT;
    }

    public double lowerHertz() {
        return tonicHertz * Math.pow(2.0, lowerRelativeCent / 1200.0);
    }

    public double upperHertz() {
        return tonicHertz * Math.pow(2.0, upperRelativeCent / 1200.0);
    }
}
