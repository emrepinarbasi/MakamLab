package com.makambox.studio.study;

import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.EventType;
import com.makambox.studio.model.MelodicDirection;

/** Exact boundary coverage for the interval-study evidence tiers. */
public final class IntervalEventTierRegressionTest {
    public static void main(String[] args) {
        checkTier(0.0599, 0.99, 0.0, IntervalEventTier.Tier.AUDIT_ONLY,
                "60 ms altındaki olay yalnız denetim katmanında kalmalı");
        checkTier(0.0600, 0.75, 0.55, IntervalEventTier.Tier.CONDITIONAL_MEASURABLE,
                "60 ms, %75 güven ve 0,55 Hc sınırı dahil olmalı");
        checkTier(0.0999, 0.95, 0.10, IntervalEventTier.Tier.CONDITIONAL_MEASURABLE,
                "100 ms altındaki kaliteli olay koşullu ölçülebilir olmalı");
        checkTier(0.1000, 0.20, 9.0, IntervalEventTier.Tier.STRONG_MEASURABLE,
                "100 ms ve üzerindeki olay güçlü ölçülebilir olmalı");
        checkTier(0.0600, 0.7499, 0.10, IntervalEventTier.Tier.AUDIT_ONLY,
                "koşullu olay güven sınırını karşılamıyorsa ana istatistiğe girmemeli");
        checkTier(0.0600, 0.95, 0.5501, IntervalEventTier.Tier.AUDIT_ONLY,
                "koşullu olay yayılım sınırını aşıyorsa ana istatistiğe girmemeli");

        StudyPitchEvent conditional = event(0.0600, 0.90, 0.20);
        check(conditional.eventType() == EventType.SHORT_CONTACT,
                "koşullu olayın kullanıcıya görünen tür etiketi Kısa temas kalmalı");
        check(!conditional.measurable(),
                "genel olay ölçülebilirliği diğer motorlar için değişmemeli");
        check(IntervalEventTier.statisticallyMeasurable(conditional),
                "perdeler-arası katman Kısa temas etiketinden bağımsız ölçebilmelidir");
        System.out.println("INTERVAL_EVENT_TIER_REGRESSION_OK");
    }

    private static void checkTier(double duration, double confidence, double spreadHc,
                                  IntervalEventTier.Tier expected, String message) {
        IntervalEventTier.Tier actual = IntervalEventTier.classify(
                event(duration, confidence, spreadHc));
        check(actual == expected, message + " (beklenen=" + expected + ", gerçek=" + actual + ")");
    }

    private static StudyPitchEvent event(double duration, double confidence,
                                         double spreadHc) {
        double spreadCent = spreadHc * AnalysisConfig.COMMA_CENT;
        return new StudyPitchEvent(1, "nim zirgule", "Nim Zirgüle",
                1.0, 1.0 + duration, duration, 230.0,
                4.0 * AnalysisConfig.COMMA_CENT,
                4.0 * AnalysisConfig.COMMA_CENT,
                4.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                spreadCent, 0.0, confidence,
                MelodicDirection.LEVEL, MelodicDirection.LEVEL,
                MelodicDirection.LEVEL, EventType.SHORT_CONTACT, false);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
