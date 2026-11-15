package com.makambox.studio.study.glissando;

import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.PitchFrame;

import java.util.ArrayList;
import java.util.List;

/** Regression coverage for the two-selected-pitch academic glissando engine. */
public final class PitchPairGlissandoAnalyzerRegressionTest {
    private static final double STEP = 0.020;
    private static final double TONIC_HZ = 220.0;
    private static final double TONIC_CENT = PitchTracker.hertzToCent(TONIC_HZ);
    private static final double UPPER_CENT = 9.0 * AnalysisConfig.COMMA_CENT;

    public static void main(String[] args) {
        detectsAscendingAndDescendingSeparately();
        distinguishesDirectReversedAndMixedContours();
        enforcesSettledEndpointsOnlyInContactMode();
        keepsWeakEvidenceInAuditLayer();
        rejectsAbruptStepFromPrimaryStatistics();
        preservesCentAndHcMeasurementsUnderTransposition();
        System.out.println("PITCH_PAIR_GLISSANDO_ANALYZER_REGRESSION_OK");
    }

    private static void detectsAscendingAndDescendingSeparately() {
        Builder builder = new Builder();
        builder.hold(0.0, 0.16, 0.96)
                .linear(0.0, UPPER_CENT, 0.48, 0.96)
                .hold(UPPER_CENT, 0.16, 0.96)
                .silence(0.16)
                .hold(UPPER_CENT, 0.16, 0.96)
                .linear(UPPER_CENT, 0.0, 0.52, 0.96)
                .hold(0.0, 0.16, 0.96);
        PitchPairGlissandoStudyResult result = analyze(builder.frames(), openRequest(TONIC_HZ));
        check(result.ascendingEvents().size() == 1, "Bir çıkıcı olay bulunmalı");
        check(result.descendingEvents().size() == 1, "Bir inici olay bulunmalı");
        check(result.ascendingStatistics().count() == 1, "Çıkıcı istatistiği ayrı tutulmalı");
        check(result.descendingStatistics().count() == 1, "İnici istatistiği ayrı tutulmalı");
        PitchPairGlissandoEvent ascending = result.ascendingEvents().get(0);
        check(ascending.signedSpeedCentPerSecond() > 0.0, "Çıkıcı hız pozitif olmalı");
        check(Math.abs(ascending.observedSpanHc() - 7.0) < 1.2,
                "Açık aralık kapıları yaklaşık yedi Hc güvenilir iz vermeli");
        PitchPairGlissandoEvent descending = result.descendingEvents().get(0);
        check(descending.signedSpeedCentPerSecond() < 0.0, "İnici hız negatif olmalı");
    }

    private static void distinguishesDirectReversedAndMixedContours() {
        Builder direct = new Builder();
        direct.hold(0.0, 0.14, 0.96).linear(0.0, UPPER_CENT, 0.44, 0.96)
                .hold(UPPER_CENT, 0.14, 0.96);
        PitchPairGlissandoEvent directEvent = singlePrimary(direct.frames(), openRequest(TONIC_HZ));
        check(directEvent.shape() == GlissandoShape.DIRECT, "Doğrusal iz düz olmalı");

        Builder reversed = new Builder();
        reversed.hold(0.0, 0.14, 0.96)
                .linear(0.0, 4.8 * AnalysisConfig.COMMA_CENT, 0.24, 0.96)
                .linear(4.8 * AnalysisConfig.COMMA_CENT, 4.1 * AnalysisConfig.COMMA_CENT, 0.10, 0.96)
                .linear(4.1 * AnalysisConfig.COMMA_CENT, UPPER_CENT, 0.26, 0.96)
                .hold(UPPER_CENT, 0.14, 0.96);
        PitchPairGlissandoEvent reversedEvent = singlePrimary(reversed.frames(), openRequest(TONIC_HZ));
        check(reversedEvent.shape() == GlissandoShape.WITH_REVERSAL,
                "Küçük geri kıvrım silinmeden geri dönüşlü işaretlenmeli");
        check(reversedEvent.reverseMovementHc() > 0.25, "Geri hareket Hc olarak ölçülmeli");

        Builder mixed = new Builder();
        mixed.hold(0.0, 0.14, 0.96)
                .linear(0.0, 5.8 * AnalysisConfig.COMMA_CENT, 0.20, 0.96)
                .linear(5.8 * AnalysisConfig.COMMA_CENT, 1.8 * AnalysisConfig.COMMA_CENT, 0.20, 0.96)
                .linear(1.8 * AnalysisConfig.COMMA_CENT, UPPER_CENT, 0.24, 0.96)
                .hold(UPPER_CENT, 0.14, 0.96);
        PitchPairGlissandoStudyResult mixedResult = analyze(mixed.frames(), openRequest(TONIC_HZ));
        check(mixedResult.primaryEvents().isEmpty(), "Karma hareket ana istatistiğe girmemeli");
        check(mixedResult.auditEvents().stream().anyMatch(value -> value.shape() == GlissandoShape.MIXED),
                "Karma hareket denetim katmanında korunmalı");
    }

    private static void enforcesSettledEndpointsOnlyInContactMode() {
        Builder settled = new Builder();
        settled.hold(0.0, 0.16, 0.96).linear(0.0, UPPER_CENT, 0.42, 0.96)
                .hold(UPPER_CENT, 0.16, 0.96);
        PitchPairGlissandoStudyResult settledResult = analyze(settled.frames(), contactRequest(TONIC_HZ));
        check(settledResult.primaryEvents().size() == 1, "İki yerleşik uç ana olay olmalı");
        check(settledResult.primaryEvents().get(0).bothEndpointsSettled(), "İki uç da yerleşik görünmeli");

        Builder passing = new Builder();
        passing.hold(-2.0 * AnalysisConfig.COMMA_CENT, 0.10, 0.96)
                .linear(-2.0 * AnalysisConfig.COMMA_CENT, UPPER_CENT + 2.0 * AnalysisConfig.COMMA_CENT,
                        0.52, 0.96)
                .hold(UPPER_CENT + 2.0 * AnalysisConfig.COMMA_CENT, 0.10, 0.96);
        PitchPairGlissandoStudyResult contactResult = analyze(passing.frames(), contactRequest(TONIC_HZ));
        check(contactResult.primaryEvents().isEmpty(), "Yerleşmeyen geçiş temas modunda ana olay olmamalı");
        PitchPairGlissandoStudyResult openResult = analyze(passing.frames(), openRequest(TONIC_HZ));
        check(openResult.primaryEvents().size() == 1, "Aynı geçiş açık aralık modunda ölçülebilmeli");
    }

    private static void keepsWeakEvidenceInAuditLayer() {
        Builder weak = new Builder();
        weak.hold(0.0, 0.14, 0.48).linear(0.0, UPPER_CENT, 0.46, 0.48)
                .hold(UPPER_CENT, 0.14, 0.48);
        PitchPairGlissandoStudyResult result = analyze(weak.frames(), openRequest(TONIC_HZ));
        check(result.primaryEvents().isEmpty(), "Zayıf F0 ana istatistiğe girmemeli");
        check(result.auditEvents().size() == 1, "Zayıf F0 silinmeden denetim adayı olmalı");
        check(result.auditEvents().get(0).auditReason().contains("Güvenilir F0"),
                "Dışlama gerekçesi açık yazılmalı");
    }

    private static void rejectsAbruptStepFromPrimaryStatistics() {
        Builder step = new Builder();
        step.hold(0.0, 0.20, 0.96).hold(UPPER_CENT, 0.20, 0.96);
        PitchPairGlissandoStudyResult result = analyze(step.frames(), openRequest(TONIC_HZ));
        check(result.primaryEvents().isEmpty(), "Ani perde değişimi glissando ana olayı olmamalı");
    }

    private static void preservesCentAndHcMeasurementsUnderTransposition() {
        Builder first = new Builder();
        first.hold(0.0, 0.14, 0.96).linear(0.0, UPPER_CENT, 0.48, 0.96)
                .hold(UPPER_CENT, 0.14, 0.96);
        PitchPairGlissandoEvent at220 = singlePrimary(first.frames(), openRequest(TONIC_HZ));

        double otherTonic = 329.36;
        Builder second = new Builder(PitchTracker.hertzToCent(otherTonic));
        second.hold(0.0, 0.14, 0.96).linear(0.0, UPPER_CENT, 0.48, 0.96)
                .hold(UPPER_CENT, 0.14, 0.96);
        PitchPairGlissandoEvent transposed = singlePrimary(second.frames(), openRequest(otherTonic));
        check(Math.abs(at220.observedSpanCent() - transposed.observedSpanCent()) < 0.01,
                "Transpozisyon C genişliğini değiştirmemeli");
        check(Math.abs(at220.observedSpanHc() - transposed.observedSpanHc()) < 0.001,
                "Transpozisyon Hc genişliğini değiştirmemeli");
    }

    private static PitchPairGlissandoEvent singlePrimary(List<PitchFrame> frames,
                                                          PitchPairGlissandoRequest request) {
        PitchPairGlissandoStudyResult result = analyze(frames, request);
        check(result.primaryEvents().size() == 1,
                "Tam bir ana olay bekleniyor; bulunan=" + result.primaryEvents().size()
                        + " denetim=" + result.auditEvents().size());
        return result.primaryEvents().get(0);
    }

    private static PitchPairGlissandoStudyResult analyze(List<PitchFrame> frames,
                                                          PitchPairGlissandoRequest request) {
        return new PitchPairGlissandoAnalyzer().analyze(frames, request);
    }

    private static PitchPairGlissandoRequest openRequest(double tonic) {
        return PitchPairGlissandoRequest.academicDefaults("Uşşak", tonic,
                "Dügâh", 0.0, "Bûselik", UPPER_CENT, GlissandoScopeMode.OPEN_INTERVAL);
    }

    private static PitchPairGlissandoRequest contactRequest(double tonic) {
        return PitchPairGlissandoRequest.academicDefaults("Uşşak", tonic,
                "Dügâh", 0.0, "Bûselik", UPPER_CENT,
                GlissandoScopeMode.ENDPOINT_CONTACT_REQUIRED);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Builder {
        private final List<PitchFrame> frames = new ArrayList<>();
        private final double tonicCent;

        private Builder() {
            this(TONIC_CENT);
        }

        private Builder(double tonicCent) {
            this.tonicCent = tonicCent;
        }

        private Builder hold(double relativeCent, double seconds, double confidence) {
            int count = Math.max(1, (int) Math.round(seconds / STEP));
            for (int i = 0; i < count; i++) add(relativeCent, confidence);
            return this;
        }

        private Builder linear(double first, double last, double seconds, double confidence) {
            int count = Math.max(3, (int) Math.round(seconds / STEP));
            for (int i = 0; i < count; i++) {
                add(first + (last - first) * i / (count - 1.0), confidence);
            }
            return this;
        }

        private Builder silence(double seconds) {
            int count = Math.max(1, (int) Math.round(seconds / STEP));
            for (int i = 0; i < count; i++) {
                frames.add(new PitchFrame(frames.size() * STEP, 0.0, Double.NaN,
                        0.0, 0.0, 0.0, Double.NaN));
            }
            return this;
        }

        private void add(double relativeCent, double confidence) {
            double absolute = tonicCent + relativeCent;
            double reliableHertz = confidence >= PitchTracker.RELIABLE_CONFIDENCE
                    ? PitchTracker.centToHertz(absolute) : 0.0;
            frames.add(new PitchFrame(frames.size() * STEP, reliableHertz,
                    reliableHertz > 0.0 ? absolute : Double.NaN,
                    confidence, 0.10, PitchTracker.centToHertz(absolute), absolute));
        }

        private List<PitchFrame> frames() {
            return List.copyOf(frames);
        }
    }
}
