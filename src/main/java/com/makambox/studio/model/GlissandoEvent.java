package com.makambox.studio.model;

public record GlissandoEvent(
        int index,
        GlissandoType type,
        MelodicDirection direction,
        double startSeconds,
        double endSeconds,
        double crossingSeconds,
        double startCent,
        double endCent,
        double targetCent,
        double monotonicity,
        double confidence
) {
    public double durationSeconds() { return endSeconds - startSeconds; }
    public double spanCent() { return Math.abs(endCent - startCent); }
    public double spanComma() { return spanCent() / AnalysisConfig.COMMA_CENT; }
    public double signedSpanCent() { return endCent - startCent; }
    public double averageSpeedCentPerSecond() {
        return durationSeconds() > 0 ? signedSpanCent() / durationSeconds() : 0;
    }
    /** Turkish performance terminology: upward is düz, downward is ters glissando. */
    public String directionTechniqueLabel() {
        return switch (direction) {
            case ASCENDING -> "Düz (çıkıcı)";
            case DESCENDING -> "Ters (inici)";
            case LEVEL -> "Yatay/Karma";
        };
    }
}
