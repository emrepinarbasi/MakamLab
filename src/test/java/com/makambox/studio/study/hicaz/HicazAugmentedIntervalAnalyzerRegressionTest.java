package com.makambox.studio.study.hicaz;

import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.EventType;
import com.makambox.studio.model.MelodicDirection;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchOccurrence;

import java.util.ArrayList;
import java.util.List;

/** Regression coverage for Hicaz degree paths and the direct 2↔3 interval. */
public final class HicazAugmentedIntervalAnalyzerRegressionTest {
    private static final HicazAugmentedIntervalAnalyzer ANALYZER =
            new HicazAugmentedIntervalAnalyzer();

    public static void main(String[] args) {
        verifyCanonicalReferences();
        verifyExactTheoryAndNoNestedDoubleCounting();
        verifyEverySupportedCombination();
        verifySkippedDegreesDoNotCreateDirectInterval();
        verifyIndependentDegreeAndIntervalDeviation();
        verifyDescendingMeasurement();
        verifyDirectionChangeCreatesTwoMaximalPhrases();
        verifyLowConfidenceCandidatesRemainAuditable();
        verifySummaryStatisticsAndUniqueEvents();
        verifyTonicTranspositionInvariance();
        System.out.println("HICAZ_AUGMENTED_INTERVAL_ANALYZER_REGRESSION_OK");
    }

    private static void verifyCanonicalReferences() {
        close(HicazTetrachordDegree.DEGREE_1.theoreticalHc(), 0.0, 0.0,
                "1. derece Hc");
        close(HicazTetrachordDegree.DEGREE_2.theoreticalHc(), 5.0, 0.0,
                "2. derece Hc");
        close(HicazTetrachordDegree.DEGREE_3.theoreticalHc(), 17.0, 0.0,
                "3. derece Hc");
        close(HicazTetrachordDegree.DEGREE_4.theoreticalHc(), 22.0, 0.0,
                "4. derece Hc");
        close(HicazAugmentedIntervalMeasurement.THEORETICAL_INTERVAL_HC,
                12.0, 0.0, "2–3 kuramsal artık aralığı");
    }

    private static void verifyExactTheoryAndNoNestedDoubleCounting() {
        Scenario scenario = new Scenario(220.0);
        scenario.sequence(new int[]{1, 2, 3, 4}, new double[]{0, 0, 0, 0}, .94);
        HicazAugmentedIntervalStudyResult result = ANALYZER.analyze(
                scenario.events, scenario.tonicHertz);
        check(result.phrases().size() == 1,
                "1→2→3→4 yalnız bir maksimal cümle üretmeli");
        check(result.phrases().get(0).pathLabel().equals("1→2→3→4"),
                "tam çıkıcı yol etiketi");
        check(result.directIntervals().size() == 1,
                "tam cümlede 2→3 yalnız bir kez ölçülmeli");
        HicazAugmentedIntervalMeasurement interval = result.directIntervals().get(0);
        close(interval.observedIntervalHc(), 12.0, 1.0e-9, "kuramsal artık aralık");
        close(interval.deviationHc(), 0.0, 1.0e-9, "kuramsal artık aralık sapması");
        check(result.intervalStatistics(HicazPhraseDirection.ASCENDING).count() == 1,
                "çıkıcı aralık özeti");
        check(result.intervalStatistics(HicazPhraseDirection.DESCENDING).count() == 0,
                "inici özet boş olmalı");
    }

    private static void verifyEverySupportedCombination() {
        check(HicazAugmentedIntervalAnalyzer.ascendingCombinations().size() == 9,
                "Kullanıcının belirlediği 9 çıkıcı kombinasyon bulunmalı");
        check(HicazAugmentedIntervalAnalyzer.descendingCombinations().size() == 9,
                "Kullanıcının belirlediği 9 inici kombinasyon bulunmalı");
        for (List<Integer> combination :
                HicazAugmentedIntervalAnalyzer.ascendingCombinations()) {
            Scenario scenario = new Scenario(220.0);
            scenario.sequence(combination.stream().mapToInt(Integer::intValue).toArray(),
                    new double[combination.size()], .94);
            HicazAugmentedIntervalStudyResult result = ANALYZER.analyze(
                    scenario.events, scenario.tonicHertz);
            check(result.includedPhrases().size() == 1,
                    "çıkıcı kombinasyon tanınmadı: " + combination);
            check(result.includedPhrases().get(0).direction()
                            == HicazPhraseDirection.ASCENDING,
                    "çıkıcı yön: " + combination);
        }
        for (List<Integer> combination :
                HicazAugmentedIntervalAnalyzer.descendingCombinations()) {
            Scenario scenario = new Scenario(220.0);
            scenario.sequence(combination.stream().mapToInt(Integer::intValue).toArray(),
                    new double[combination.size()], .94);
            HicazAugmentedIntervalStudyResult result = ANALYZER.analyze(
                    scenario.events, scenario.tonicHertz);
            check(result.includedPhrases().size() == 1,
                    "inici kombinasyon tanınmadı: " + combination);
            check(result.includedPhrases().get(0).direction()
                            == HicazPhraseDirection.DESCENDING,
                    "inici yön: " + combination);
        }
    }

    private static void verifySkippedDegreesDoNotCreateDirectInterval() {
        Scenario skipped = new Scenario(220.0);
        skipped.sequence(new int[]{1, 3, 4}, new double[]{0, 0, 0}, .94);
        HicazAugmentedIntervalStudyResult result = ANALYZER.analyze(
                skipped.events, skipped.tonicHertz);
        check(result.includedPhrases().size() == 1,
                "1→3→4 çıkıcı cümle olarak korunmalı");
        check(result.directIntervals().isEmpty(),
                "atlanan 2. derece doğrudan 2→3 ölçümü üretmemeli");

        Scenario direct = new Scenario(220.0);
        direct.sequence(new int[]{1, 2, 3}, new double[]{0, 0, 0}, .94);
        check(ANALYZER.analyze(direct.events, direct.tonicHertz)
                        .directIntervals().size() == 1,
                "1→2→3 içindeki gerçek 2→3 ölçülmeli");
    }

    private static void verifyIndependentDegreeAndIntervalDeviation() {
        Scenario scenario = new Scenario(220.0);
        scenario.sequence(new int[]{1, 2, 3, 4},
                new double[]{0.0, -10.0, 20.0, 7.0}, .94);
        HicazAugmentedIntervalStudyResult result = ANALYZER.analyze(
                scenario.events, scenario.tonicHertz);
        close(result.degreeStatistics(HicazPhraseDirection.ASCENDING,
                        HicazTetrachordDegree.DEGREE_2).meanDeviationCent(),
                -10.0, 1.0e-6, "2. derece sapması");
        close(result.degreeStatistics(HicazPhraseDirection.ASCENDING,
                        HicazTetrachordDegree.DEGREE_3).meanDeviationCent(),
                20.0, 1.0e-6, "3. derece sapması");
        close(result.degreeStatistics(HicazPhraseDirection.ASCENDING,
                        HicazTetrachordDegree.DEGREE_4).meanDeviationCent(),
                7.0, 1.0e-6, "4. derece sapması");
        HicazAugmentedIntervalMeasurement interval = result.directIntervals().get(0);
        close(interval.observedIntervalCent(),
                12.0 * AnalysisConfig.COMMA_CENT + 30.0, 1.0e-6,
                "iki derecenin bağımsız sapması aralığa yansımalı");
        close(interval.deviationCent(), 30.0, 1.0e-6,
                "artık aralık sapması üçüncü eksi ikinci sapmadır");
    }

    private static void verifyDescendingMeasurement() {
        Scenario scenario = new Scenario(330.0);
        scenario.sequence(new int[]{4, 3, 2, 1},
                new double[]{-4.0, -12.0, 8.0, 3.0}, .94);
        HicazAugmentedIntervalStudyResult result = ANALYZER.analyze(
                scenario.events, scenario.tonicHertz);
        check(result.includedPhrases().size() == 1
                        && result.includedPhrases().get(0).direction()
                        == HicazPhraseDirection.DESCENDING,
                "4→3→2→1 inici olmalı");
        check(result.directIntervals(HicazPhraseDirection.DESCENDING).size() == 1,
                "3→2 doğrudan inici ölçüm olmalı");
        // |degree 3 - degree 2| = theory + (-12 - 8) cents.
        close(result.directIntervals().get(0).deviationCent(), -20.0, 1.0e-6,
                "inici artık aralık büyüklüğü yön işaretinden bağımsız olmalı");
    }

    private static void verifyDirectionChangeCreatesTwoMaximalPhrases() {
        Scenario scenario = new Scenario(220.0);
        scenario.sequence(new int[]{1, 2, 3, 2, 1},
                new double[]{0, 0, 0, 0, 0}, .94);
        HicazAugmentedIntervalStudyResult result = ANALYZER.analyze(
                scenario.events, scenario.tonicHertz);
        check(result.includedPhrases().size() == 2,
                "yön dönüşü iki maksimal cümle üretmeli");
        check(result.includedPhrases().get(0).pathLabel().equals("1→2→3")
                        && result.includedPhrases().get(1).pathLabel().equals("3→2→1"),
                "dönüşte iç içe alt cümleler üretilmemeli");
        check(result.directIntervals().size() == 2,
                "gerçek 2→3 ve 3→2 geçişleri birer kez ölçülmeli");
        check(result.overallDegreeStatistics(HicazTetrachordDegree.DEGREE_3).count() == 1,
                "ortak dönüş derecesi genel istatistikte çift sayılmamalı");
    }

    private static void verifyLowConfidenceCandidatesRemainAuditable() {
        Scenario scenario = new Scenario(220.0);
        scenario.add(1, 0.0, .94).add(2, 0.0, .50).add(3, 0.0, .94);
        HicazAugmentedIntervalStudyResult result = ANALYZER.analyze(
                scenario.events, scenario.tonicHertz);
        check(result.events().size() == 3 && result.auditEvents().size() == 1,
                "%35–65 güvenli derece silinmeden denetimde kalmalı");
        check(result.phrases().size() == 1 && result.auditPhrases().size() == 1,
                "düşük güvenli derece cümleyi denetim cümlesi yapmalı");
        check(result.directIntervals().size() == 1
                        && !result.directIntervals().get(0).included(),
                "doğrudan 2→3 denetimde görünmeli fakat ana ölçüm olmamalı");
        check(result.intervalStatistics(HicazPhraseDirection.ASCENDING).count() == 0,
                "düşük güven aralık ortalamasına sızmamalı");
        check(result.overallDegreeStatistics(HicazTetrachordDegree.DEGREE_2).count() == 0,
                "düşük güven derece ortalamasına sızmamalı");
    }

    private static void verifySummaryStatisticsAndUniqueEvents() {
        Scenario first = new Scenario(220.0);
        first.sequence(new int[]{1, 2, 3, 4},
                new double[]{0, -10, 10, 4}, .94);
        HicazAugmentedIntervalStudyResult result = ANALYZER.analyze(
                first.events, first.tonicHertz);
        close(result.intervalStatistics(HicazPhraseDirection.ASCENDING)
                        .meanDeviationCent(), 20.0, 1.0e-6,
                "doğrudan aralık özet sapması");
        check(result.overallDegreeStatistics(HicazTetrachordDegree.DEGREE_2).count() == 1,
                "derece özeti tek olayı bir kez saymalı");
        check(result.overallDegreeStatistics(HicazTetrachordDegree.DEGREE_3)
                        .withinOneHcCount() == 1,
                "+10 C bir Hc içinde olmalı");
    }

    private static void verifyTonicTranspositionInvariance() {
        Scenario low = new Scenario(220.0);
        low.sequence(new int[]{2, 3}, new double[]{-6, 9}, .94);
        Scenario high = new Scenario(330.0);
        high.sequence(new int[]{2, 3}, new double[]{-6, 9}, .94);
        HicazAugmentedIntervalStudyResult a = ANALYZER.analyze(low.events, low.tonicHertz);
        HicazAugmentedIntervalStudyResult b = ANALYZER.analyze(high.events, high.tonicHertz);
        close(a.directIntervals().get(0).deviationCent(),
                b.directIntervals().get(0).deviationCent(), 1.0e-6,
                "transpozisyon C aralık sapmasını değiştirmemeli");
        close(a.degreeStatistics(HicazPhraseDirection.ASCENDING,
                        HicazTetrachordDegree.DEGREE_2).meanDeviationHc(),
                b.degreeStatistics(HicazPhraseDirection.ASCENDING,
                        HicazTetrachordDegree.DEGREE_2).meanDeviationHc(), 1.0e-9,
                "transpozisyon Hc derece sapmasını değiştirmemeli");
    }

    private static void close(double actual, double expected, double tolerance,
                              String message) {
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
        private final List<PitchEvent> events = new ArrayList<>();
        private double cursor;

        private Scenario(double tonicHertz) {
            this.tonicHertz = tonicHertz;
            this.tonicCent = PitchTracker.hertzToCent(tonicHertz);
        }

        private Scenario sequence(int[] degrees, double[] deviations, double confidence) {
            if (degrees.length != deviations.length) throw new IllegalArgumentException();
            for (int index = 0; index < degrees.length; index++) {
                add(degrees[index], deviations[index], confidence);
            }
            return this;
        }

        private Scenario add(int degreeNumber, double deviationCent, double confidence) {
            HicazTetrachordDegree degree = HicazTetrachordDegree.fromNumber(degreeNumber);
            double relative = degree.theoreticalCent() + deviationCent;
            double observed = tonicCent + relative;
            double start = cursor;
            double end = start + .12;
            cursor = end + .02;
            PitchOccurrence occurrence = new PitchOccurrence(events.size() + 1,
                    start, end, observed, tonicCent + degree.theoreticalCent(), 2.0,
                    deviationCent, deviationCent, deviationCent, 4.0,
                    false, confidence, MelodicDirection.LEVEL, MelodicDirection.LEVEL,
                    MelodicDirection.LEVEL, EventType.SUSTAINED);
            events.add(new PitchEvent("hicaz-" + degreeNumber,
                    degree.label(), degree.theoreticalCent(), occurrence));
            return this;
        }
    }
}
