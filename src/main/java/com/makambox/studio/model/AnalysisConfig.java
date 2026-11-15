package com.makambox.studio.model;

public record AnalysisConfig(
        String makamName,
        String tonicNote,
        String targetNote,
        double tonicHz,
        double targetRelativeCent,
        double targetLowerToleranceComma,
        double targetUpperToleranceComma,
        double similarityToleranceComma,
        double minimumOccurrenceSeconds,
        double passingMaximumSeconds,
        double holdMinimumSeconds,
        double phraseSilenceSeconds,
        double directionThresholdComma,
        double maximumStableSpreadComma,
        double minimumGlissandoSeconds,
        double minimumGlissandoSpanComma,
        double minimumGlissandoMonotonicity,
        double maximumGlissandoFrameStepCent,
        double maximumAutomaticGlissandoSpanCent,
        double glissandoTargetToleranceComma,
        double minimumGlissandoTargetContactSeconds,
        double maximumGlissandoTrajectoryResidualCent,
        boolean tonicEstimated
) {
    public static final double COMMA_CENT = 1200.0 / 53.0;
    public static final double DEFAULT_MINIMUM_GLISSANDO_SECONDS = 0.08;
    public static final double DEFAULT_MINIMUM_GLISSANDO_SPAN_COMMA = 0.80;
    public static final double DEFAULT_MINIMUM_GLISSANDO_MONOTONICITY = 0.68;
    public static final double DEFAULT_MAXIMUM_GLISSANDO_FRAME_STEP_CENT = 90.0;
    public double expectedAbsoluteCent() {
        return 1200.0 * Math.log(tonicHz / 16.351597831287414) / Math.log(2.0)
                + targetRelativeCent;
    }
    public double targetWindowLowerAbsoluteCent() {
        return expectedAbsoluteCent() - targetLowerToleranceComma * COMMA_CENT;
    }
    public double targetWindowUpperAbsoluteCent() {
        return expectedAbsoluteCent() + targetUpperToleranceComma * COMMA_CENT;
    }
}
