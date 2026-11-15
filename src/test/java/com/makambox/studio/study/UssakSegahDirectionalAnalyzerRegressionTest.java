package com.makambox.studio.study;

import com.makambox.studio.analysis.AllPitchEventAnalyzer;
import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.theory.TheoryCatalog;

import java.util.ArrayList;
import java.util.List;

/** Regression coverage for functional Segâh direction, holds and group statistics. */
public final class UssakSegahDirectionalAnalyzerRegressionTest {
    private static final double STEP = .020;
    private static final TheoryCatalog CATALOG = TheoryCatalog.loadDefault();
    private static final TheoryCatalog.Makam USSAK = makam("Ussak");
    private static final TheoryCatalog.Makam HUSEYNI = makam("Huseyni");
    private static final UssakSegahDirectionalAnalyzer ANALYZER =
            new UssakSegahDirectionalAnalyzer();

    public static void main(String[] args) {
        verifyFullAndShortDirectionSemantics();
        verifyExtendedNevaPaths();
        verifyContextRecoversHighFunctionalSegah();
        verifyDirectHoldAndGlissandoExclusion();
        verifyOpenDugahBuselikBoundaries();
        verifySelectableOpenBoundaries();
        verifyAuditCandidatesAndConfidenceTiers();
        verifyGroupAndOverallStatistics();
        verifyTonicAndMakamInvariance();
        System.out.println("USSAK_SEGAH_DIRECTIONAL_ANALYZER_REGRESSION_OK");
    }

    private static void verifyFullAndShortDirectionSemantics() {
        Scenario ascending = new Scenario(220.0, USSAK);
        ascending.note("dugah", 15).segah(-15.0, 12).note("cargah", 15);
        UssakSegahEvent up = only(analyze(ascending));
        check(up.usageClass() == UssakSegahUsageClass.ASCENDING_PHRASE,
                "Dügâh→Segâh→Çargâh çıkıcı olmalı");
        close(up.globalDeviationCent(), -15.0, .01, "çıkıcı pest sapma");
        close(up.globalDeviationHc(), -15.0 / AnalysisConfig.COMMA_CENT, .001,
                "çıkıcı Hc sapması");

        Scenario descending = new Scenario(220.0, USSAK);
        descending.note("cargah", 15).segah(12.0, 12).note("dugah", 15);
        UssakSegahEvent down = only(analyze(descending));
        check(down.usageClass() == UssakSegahUsageClass.DESCENDING_PHRASE,
                "Çargâh→Segâh→Dügâh inici olmalı");
        close(down.globalDeviationCent(), 12.0, .01, "inici tiz sapma");

        Scenario shortUp = new Scenario(220.0, USSAK);
        shortUp.note("dugah", 15).segah(-8.0, 12).silence(20);
        check(only(analyze(shortUp)).usageClass() == UssakSegahUsageClass.ASCENDING_PHRASE,
                "Dügâh→Segâh kısa kanıtı tek başına çıkıcı sayılmalı");

        Scenario shortDown = new Scenario(220.0, USSAK);
        shortDown.silence(20).segah(6.0, 12).note("dugah", 15);
        check(only(analyze(shortDown)).usageClass() == UssakSegahUsageClass.DESCENDING_PHRASE,
                "Segâh→Dügâh kısa kanıtı tek başına inici sayılmalı");
    }

    private static void verifyExtendedNevaPaths() {
        Scenario up = new Scenario(220.0, USSAK);
        up.note("dugah", 12).segah(-5.0, 10).note("cargah", 12).note("neva", 12);
        UssakSegahEvent ascending = only(analyze(up));
        check(ascending.pathLabel().equals("Dügâh→Segâh→Çargâh→Nevâ"),
                "tam çıkıcı Nevâ yolu raporlanmalı");

        Scenario down = new Scenario(220.0, USSAK);
        down.note("neva", 12).note("cargah", 12).segah(-5.0, 10).note("dugah", 12);
        UssakSegahEvent descending = only(analyze(down));
        check(descending.pathLabel().equals("Nevâ→Çargâh→Segâh→Dügâh"),
                "tam inici Nevâ yolu raporlanmalı");
        check(ascending.classificationConfidence() > .80
                        && descending.classificationConfidence() > .80,
                "tam dört sesli yollar yüksek güven taşımalı");

        Scenario shortUp = new Scenario(220.0, USSAK);
        shortUp.note("dugah", 12).segah(-5.0, 10).silence(20);
        UssakSegahEvent shortAscending = only(analyze(shortUp));
        check(ascending.classificationConfidence() > shortAscending.classificationConfidence(),
                "Çargâh/Nevâ çıpaları kısa Dügâh→Segâh kanıtından daha yüksek güven vermeli");
    }

    private static void verifyContextRecoversHighFunctionalSegah() {
        Scenario scenario = new Scenario(220.0, USSAK);
        scenario.note("dugah", 15).segah(20.0, 12).note("cargah", 15);
        List<PitchEvent> canonical = scenario.canonical();
        check(canonical.stream().anyMatch(value -> value.noteKey().equals("buselik")),
                "+20 C aday genel en-yakın merkezde Bûselik olmalı");
        UssakSegahEvent event = only(ANALYZER.analyze(scenario.frames,
                canonical, scenario.config(), CATALOG, USSAK, scenario.tonicHertz));
        check(event.usageClass() == UssakSegahUsageClass.ASCENDING_PHRASE,
                "Bûselik adı alan yüksek ikinci derece bağlamla Segâh olarak korunmalı");
        close(event.globalDeviationCent(), 20.0, .01, "işlevsel Segâh sapması");
    }

    private static void verifyDirectHoldAndGlissandoExclusion() {
        Scenario hold = new Scenario(220.0, USSAK);
        hold.silence(6).segah(-6.0, 45).silence(16);
        UssakSegahEvent event = only(analyze(hold));
        check(event.usageClass() == UssakSegahUsageClass.DIRECT_HOLD,
                "uzun kararlı Segâh ve ardından sessizlik doğrudan kalış olmalı");
        check(event.included(), "kararlı doğrudan kalış ana istatistiğe katılmalı");

        Scenario glide = new Scenario(220.0, USSAK);
        double segahHc = CATALOG.relativeCent(USSAK, CATALOG.note("segah"))
                / AnalysisConfig.COMMA_CENT;
        glide.note("dugah", 12).ramp(.10, segahHc + .9, 18).note("cargah", 12);
        List<UssakSegahEvent> candidates = analyze(glide).events();
        check(candidates.stream().noneMatch(UssakSegahEvent::included),
                "Dügâh–Bûselik aralığından kesintisiz glissando geçişi basış sayılmamalı");
        check(candidates.stream().anyMatch(value -> value.exclusionReason().contains("glissando")),
                "glissando denetim kanıtında gerekçesiyle kalmalı");
    }

    private static void verifyOpenDugahBuselikBoundaries() {
        Scenario boundaries = new Scenario(220.0, USSAK);
        boundaries.note("dugah", 20).silence(5).note("buselik", 20);
        check(analyze(boundaries).events().isEmpty(),
                "Dügâh ve Bûselik açık aralık sınırlarının kendileri aday olmamalı");
    }

    private static void verifySelectableOpenBoundaries() {
        Scenario scenario = new Scenario(220.0, USSAK);
        scenario.note("dugah", 15).segah(-15.0, 12).note("cargah", 15);
        List<PitchEvent> canonical = scenario.canonical();
        UssakSegahDirectionalStudyResult defaultRange = ANALYZER.analyze(
                scenario.frames, canonical, scenario.config(), CATALOG, USSAK,
                scenario.tonicHertz, CATALOG.note("buselik"), CATALOG.note("dugah"));
        check(defaultRange.events().size() == 1,
                "ters sırayla seçilen Dügâh/Bûselik sınırları otomatik sıralanmalı");

        UssakSegahDirectionalStudyResult narrowRange = ANALYZER.analyze(
                scenario.frames, canonical, scenario.config(), CATALOG, USSAK,
                scenario.tonicHertz, CATALOG.note("segah"), CATALOG.note("buselik"));
        check(narrowRange.events().isEmpty(),
                "kullanıcının daralttığı açık aralığın altındaki aday analize alınmamalı");
    }

    private static void verifyAuditCandidatesAndConfidenceTiers() {
        Scenario lowConfidence = new Scenario(220.0, USSAK);
        lowConfidence.note("dugah", 15)
                .segahWithConfidence(-7.0, 12, .50)
                .note("cargah", 15);
        UssakSegahDirectionalStudyResult lowResult = analyze(lowConfidence);
        UssakSegahEvent low = only(lowResult);
        check(!low.included(), "%35–65 güvenli aday ana istatistiğe katılmamalı");
        check(low.exclusionReason().contains("%65"),
                "düşük güvenli aday açık gerekçeyle denetim katmanında kalmalı");
        check(lowResult.events().size() == 1 && lowResult.includedEvents().isEmpty(),
                "düşük güvenli aday silinmeden görünür kalmalı");
        check(lowResult.overallStatistics().count() == 0,
                "düşük güvenli aday genel ortalamaya sızmamalı");

        Scenario ambiguous = new Scenario(220.0, USSAK);
        ambiguous.silence(8).segah(-4.0, 18).silence(8);
        UssakSegahDirectionalStudyResult ambiguousResult = analyze(ambiguous);
        UssakSegahEvent other = only(ambiguousResult);
        check(other.usageClass() == UssakSegahUsageClass.OTHER,
                "kısa ve çıpasız Segâh adayı yön sınıfına zorlanmamalı");
        check(!other.included() && ambiguousResult.overallStatistics().count() == 0,
                "belirsiz olay genel istatistiğe katılmamalı");
    }

    private static void verifyGroupAndOverallStatistics() {
        Scenario scenario = new Scenario(220.0, USSAK);
        scenario.note("dugah", 15).segah(-20.0, 12).note("cargah", 15).silence(16)
                .note("cargah", 15).segah(10.0, 12).note("dugah", 15).silence(16)
                .segah(0.0, 45).silence(16);
        UssakSegahDirectionalStudyResult result = analyze(scenario);
        check(result.includedEvents().size() == 3, "üç araştırma grubu birer kez sayılmalı");
        close(result.statistics(UssakSegahUsageClass.ASCENDING_PHRASE)
                .meanDeviationCent(), -20.0, .01, "çıkıcı ortalama");
        close(result.statistics(UssakSegahUsageClass.DESCENDING_PHRASE)
                .meanDeviationCent(), 10.0, .01, "inici ortalama");
        close(result.statistics(UssakSegahUsageClass.DIRECT_HOLD)
                .meanDeviationCent(), 0.0, .01, "kalış ortalaması");
        close(result.overallStatistics().meanDeviationCent(), -10.0 / 3.0, .01,
                "tüm olaylar tek tek kullanılarak ortalanmalı");
    }

    private static void verifyTonicAndMakamInvariance() {
        Scenario ussak = new Scenario(330.0, USSAK);
        ussak.note("dugah", 15).segah(-11.0, 12).note("cargah", 15);
        close(only(analyze(ussak)).globalDeviationCent(), -11.0, .01,
                "karar registeri C sapmasını değiştirmemeli");

        Scenario huseyni = new Scenario(220.0, HUSEYNI);
        huseyni.note("dugah", 15).segah(-11.0, 12).note("cargah", 15);
        UssakSegahEvent event = only(analyze(huseyni));
        check(event.usageClass() == UssakSegahUsageClass.ASCENDING_PHRASE,
                "Hüseynî'nin karar Uşşak dörtlüsü aynı yön semantiğini kullanmalı");
        close(event.globalDeviationCent(), -11.0, .01, "Hüseynî ikinci derece sapması");
    }

    private static UssakSegahDirectionalStudyResult analyze(Scenario scenario) {
        return ANALYZER.analyze(scenario.frames, scenario.canonical(), scenario.config(),
                CATALOG, scenario.makam, scenario.tonicHertz);
    }

    private static UssakSegahEvent only(UssakSegahDirectionalStudyResult result) {
        check(result.events().size() == 1,
                "tek Segâh adayı bekleniyordu, bulunan=" + result.events().size());
        return result.events().get(0);
    }

    private static TheoryCatalog.Makam makam(String name) {
        return CATALOG.makams().stream().filter(value -> value.name().equals(name))
                .findFirst().orElseThrow();
    }

    private static void close(double actual, double expected, double tolerance, String message) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + ": " + actual + " != " + expected);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Scenario {
        private final double tonicHertz;
        private final double tonicCent;
        private final TheoryCatalog.Makam makam;
        private final List<PitchFrame> frames = new ArrayList<>();

        private Scenario(double tonicHertz, TheoryCatalog.Makam makam) {
            this.tonicHertz = tonicHertz;
            this.tonicCent = PitchTracker.hertzToCent(tonicHertz);
            this.makam = makam;
        }

        private Scenario note(String key, int count) {
            return relative(CATALOG.relativeCent(makam, CATALOG.note(key)), count, .94);
        }

        private Scenario segah(double deviationCent, int count) {
            return segahWithConfidence(deviationCent, count, .94);
        }

        private Scenario segahWithConfidence(double deviationCent, int count,
                                              double confidence) {
            return relative(CATALOG.relativeCent(makam, CATALOG.note("segah"))
                    + deviationCent, count, confidence);
        }

        private Scenario ramp(double startHc, double endHc, int count) {
            for (int index = 0; index < count; index++) {
                double position = count == 1 ? 0.0 : index / (double) (count - 1);
                frame((startHc + (endHc - startHc) * position)
                        * AnalysisConfig.COMMA_CENT, .94);
            }
            return this;
        }

        private Scenario relative(double relativeCent, int count, double confidence) {
            for (int index = 0; index < count; index++) frame(relativeCent, confidence);
            return this;
        }

        private void frame(double relativeCent, double confidence) {
            double absolute = tonicCent + relativeCent;
            frames.add(new PitchFrame(frames.size() * STEP,
                    PitchTracker.centToHertz(absolute), absolute, confidence, .10));
        }

        private Scenario silence(int count) {
            for (int index = 0; index < count; index++) {
                frames.add(new PitchFrame(frames.size() * STEP, 0.0,
                        Double.NaN, 0.0, 0.0));
            }
            return this;
        }

        private AnalysisConfig config() {
            return new AnalysisConfig(makam.toString(), "Dügâh", "Segâh", tonicHertz,
                    CATALOG.relativeCent(makam, CATALOG.note("segah")), 2.5, 2.5, 1.0,
                    .10, .35, .70, .28, .75, .55,
                    .12, 1.10, .72, 100.0, 700.0, 1.5, .02, 32.0, false);
        }

        private List<PitchEvent> canonical() {
            return new AllPitchEventAnalyzer().analyze(frames, config(), CATALOG, makam);
        }
    }
}
