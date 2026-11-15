package com.makambox.studio.study.glissando;

/** One measured movement between the two selected pitch boundaries. */
public record PitchPairGlissandoEvent(
        int index,
        GlissandoDirection direction,
        GlissandoShape shape,
        GlissandoEventStatus status,
        String auditReason,
        double startSeconds,
        double endSeconds,
        double startHertz,
        double endHertz,
        double startRelativeCent,
        double endRelativeCent,
        double startRelativeHc,
        double endRelativeHc,
        double startEndpointDeviationCent,
        double endEndpointDeviationCent,
        double startEndpointDeviationHc,
        double endEndpointDeviationHc,
        double observedSpanCent,
        double observedSpanHc,
        double theoreticalSpanCent,
        double theoreticalSpanHc,
        double intervalErrorCent,
        double intervalErrorHc,
        double signedSpeedCentPerSecond,
        double signedSpeedHcPerSecond,
        double reverseMovementCent,
        double reverseMovementHc,
        double monotonicity,
        double voicedCoverage,
        double confidence,
        boolean originEndpointSettled,
        boolean destinationEndpointSettled
) {
    public double durationSeconds() {
        return endSeconds - startSeconds;
    }

    public boolean primary() {
        return status == GlissandoEventStatus.PRIMARY;
    }

    public boolean bothEndpointsSettled() {
        return originEndpointSettled && destinationEndpointSettled;
    }

    public String evidenceKindLabel() {
        return bothEndpointsSettled() ? "Yerleşik uçtan uca" : "Seçili aralığı geçen";
    }
}
