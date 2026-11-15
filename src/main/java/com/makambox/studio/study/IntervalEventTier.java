package com.makambox.studio.study;

import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchOccurrence;

import java.util.Objects;

/**
 * Shared evidence tiers for an open, between-pitches study.
 *
 * <p>The generic event label intentionally remains unchanged: a 60--99 ms
 * event may still be labelled {@code Kısa temas}.  In an interval study that
 * event can nevertheless contribute to the primary statistics when its F0 is
 * sufficiently confident and stable.  Events shorter than 60 ms remain only
 * in the visual/audit layer.</p>
 */
public final class IntervalEventTier {
    public static final double STRONG_MINIMUM_SECONDS = 0.100;
    public static final double CONDITIONAL_MINIMUM_SECONDS = 0.060;
    public static final double CONDITIONAL_MINIMUM_CONFIDENCE = 0.75;
    public static final double CONDITIONAL_MAXIMUM_SPREAD_HC = 0.55;
    private static final double EPSILON = 1.0e-9;

    private IntervalEventTier() { }

    public enum Tier {
        STRONG_MEASURABLE,
        CONDITIONAL_MEASURABLE,
        AUDIT_ONLY;

        public boolean statisticallyMeasurable() {
            return this != AUDIT_ONLY;
        }
    }

    public static Tier classify(PitchOccurrence occurrence) {
        Objects.requireNonNull(occurrence, "occurrence");
        return classify(occurrence.durationSeconds(), occurrence.confidence(),
                occurrence.stabilityCent());
    }

    public static Tier classify(PitchEvent event) {
        Objects.requireNonNull(event, "event");
        return classify(event.occurrence());
    }

    public static Tier classify(StudyPitchEvent event) {
        Objects.requireNonNull(event, "event");
        return classify(event.durationSeconds(), event.confidence(), event.stabilityCent());
    }

    public static boolean statisticallyMeasurable(PitchOccurrence occurrence) {
        return classify(occurrence).statisticallyMeasurable();
    }

    public static boolean statisticallyMeasurable(PitchEvent event) {
        return classify(event).statisticallyMeasurable();
    }

    public static boolean statisticallyMeasurable(StudyPitchEvent event) {
        return classify(event).statisticallyMeasurable();
    }

    private static Tier classify(double durationSeconds, double confidence,
                                 double stabilityCent) {
        if (Double.isFinite(durationSeconds)
                && durationSeconds + EPSILON >= STRONG_MINIMUM_SECONDS) {
            return Tier.STRONG_MEASURABLE;
        }
        boolean conditional = Double.isFinite(durationSeconds)
                && durationSeconds + EPSILON >= CONDITIONAL_MINIMUM_SECONDS
                && Double.isFinite(confidence)
                && confidence + EPSILON >= CONDITIONAL_MINIMUM_CONFIDENCE
                && Double.isFinite(stabilityCent)
                && stabilityCent <= CONDITIONAL_MAXIMUM_SPREAD_HC
                * AnalysisConfig.COMMA_CENT + EPSILON;
        return conditional ? Tier.CONDITIONAL_MEASURABLE : Tier.AUDIT_ONLY;
    }
}
