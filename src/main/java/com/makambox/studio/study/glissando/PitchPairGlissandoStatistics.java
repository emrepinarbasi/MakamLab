package com.makambox.studio.study.glissando;

import com.makambox.studio.model.AnalysisConfig;

public record PitchPairGlissandoStatistics(
        GlissandoDirection direction,
        int count,
        double meanDurationSeconds,
        double medianDurationSeconds,
        double meanStartRelativeCent,
        double medianStartRelativeCent,
        double meanEndRelativeCent,
        double medianEndRelativeCent,
        double meanSpanCent,
        double medianSpanCent,
        double meanIntervalErrorCent,
        double medianIntervalErrorCent,
        double meanSignedSpeedCentPerSecond,
        double medianSignedSpeedCentPerSecond,
        double meanReverseMovementCent,
        double meanMonotonicity,
        double meanConfidence
) {
    public static PitchPairGlissandoStatistics empty(GlissandoDirection direction) {
        return new PitchPairGlissandoStatistics(direction, 0,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN);
    }

    public double meanStartRelativeHc() { return meanStartRelativeCent / AnalysisConfig.COMMA_CENT; }
    public double medianStartRelativeHc() { return medianStartRelativeCent / AnalysisConfig.COMMA_CENT; }
    public double meanEndRelativeHc() { return meanEndRelativeCent / AnalysisConfig.COMMA_CENT; }
    public double medianEndRelativeHc() { return medianEndRelativeCent / AnalysisConfig.COMMA_CENT; }
    public double meanSpanHc() { return meanSpanCent / AnalysisConfig.COMMA_CENT; }
    public double medianSpanHc() { return medianSpanCent / AnalysisConfig.COMMA_CENT; }
    public double meanIntervalErrorHc() { return meanIntervalErrorCent / AnalysisConfig.COMMA_CENT; }
    public double medianIntervalErrorHc() { return medianIntervalErrorCent / AnalysisConfig.COMMA_CENT; }
    public double meanSignedSpeedHcPerSecond() {
        return meanSignedSpeedCentPerSecond / AnalysisConfig.COMMA_CENT;
    }
    public double medianSignedSpeedHcPerSecond() {
        return medianSignedSpeedCentPerSecond / AnalysisConfig.COMMA_CENT;
    }
    public double meanReverseMovementHc() { return meanReverseMovementCent / AnalysisConfig.COMMA_CENT; }
}
