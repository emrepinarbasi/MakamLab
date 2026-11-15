package com.makambox.studio.study;

import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.EventType;
import com.makambox.studio.model.MelodicDirection;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchOccurrence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Regression coverage for the fixed, first-four-only similarity evidence domain. */
public final class MakamProfileFirstFourScoringRegressionTest {
    private static final double TONIC_HZ = 220.0;
    private static final double TONIC_CENT = PitchTracker.hertzToCent(TONIC_HZ);
    private static final double[] USSAK_NON_TONIC = {181.13208, 294.33963, 498.11322};

    public static void main(String[] args) {
        List<PitchEvent> firstFour = events(false);
        MakamProfileComparator comparator = new MakamProfileComparator();
        double maximumSpread = MakamProfileComparator.MAXIMUM_STABLE_SPREAD_HC
                * (1200.0 / 53.0);
        check(!MakamProfileComparator.measurableFirstFourEvidence(
                        .0599, .99, maximumSpread, false),
                "a contact below 60 ms must remain audit/directional evidence only");
        check(MakamProfileComparator.measurableFirstFourEvidence(
                        .060, .75, maximumSpread, false),
                "the inclusive 60 ms / 75% / 0.55-Hc short-measurable boundary changed");
        check(!MakamProfileComparator.measurableFirstFourEvidence(
                        .060, .7499, maximumSpread, false),
                "a short contact below 75% confidence must remain audit-only");
        check(!MakamProfileComparator.measurableFirstFourEvidence(
                        .060, .99, maximumSpread + .001, false),
                "a short contact above 0.55 Hc spread must remain audit-only");
        check(MakamProfileComparator.measurableFirstFourEvidence(
                        .10, .65, maximumSpread, true),
                "the inclusive 100 ms strong-measurable boundary changed");

        MakamProfileComparison defaults = comparator.compare(firstFour, TONIC_HZ, "Uşşak",
                MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        check(defaults.sufficientEvidence(),
                "an ordered performed 1-2-3-4 path must be sufficient");
        check(defaults.phraseEvidence().status()
                        == MakamProfileComparison.PhraseEvidenceStatus.COMPLETE,
                "an ordered performed 1-2-3-4 path must be labelled complete");
        check(defaults.observedClusterCount() == 4,
                "the global inventory must retain the actually performed tonic contact");

        MakamProfileComparison shortButComplete = comparator.compare(events(false, 0.20),
                TONIC_HZ, "Uşşak", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        check(shortButComplete.acceptedDurationSeconds() < 5.0,
                "the short fixture must remain below the former five-second gate");
        check(shortButComplete.sufficientEvidence(),
                "a complete first-four evidence set must not fail an undocumented duration gate");

        MakamProfileComparison tonicAnchoredThreeDegrees = comparator.compare(
                orderedAnchoredThreeDegreePath(true), TONIC_HZ, "Uşşak",
                MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        check(tonicAnchoredThreeDegrees.phraseEvidence().status()
                        == MakamProfileComparison.PhraseEvidenceStatus.COMPLETE,
                "an ordered 1-2-3 path must authorize a comparable tetrachord claim");
        check(tonicAnchoredThreeDegrees.phraseEvidence().qualifyingPhraseCount() == 1
                        && tonicAnchoredThreeDegrees.phraseEvidence().completePhraseCount() == 0
                        && tonicAnchoredThreeDegrees.candidates().stream()
                        .anyMatch(value -> value.scorePercent() > 0.0),
                "an ordered tonic-anchored three-degree path must remain comparable while literal four-degree paths stay separately counted");
        check(tonicAnchoredThreeDegrees.sufficientEvidence(),
                "an ordered 1-2-3 path must authorize a published percentage claim");

        MakamProfileComparison fourthAnchoredThreeDegrees = comparator.compare(
                orderedAnchoredThreeDegreePath(false), TONIC_HZ, "Uşşak",
                MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        check(fourthAnchoredThreeDegrees.phraseEvidence().status()
                        == MakamProfileComparison.PhraseEvidenceStatus.COMPLETE,
                "an ordered 2-3-4 path must authorize a comparable tetrachord claim");
        check(fourthAnchoredThreeDegrees.phraseEvidence().qualifyingPhraseCount() == 1
                        && fourthAnchoredThreeDegrees.phraseEvidence().completePhraseCount() == 0,
                "an ordered fourth-anchored three-degree path must qualify without pretending that all four degrees occurred");
        check(fourthAnchoredThreeDegrees.sufficientEvidence(),
                "an ordered 2-3-4 path must authorize a published percentage claim");

        MakamProfileComparison unanchoredTwoDegree = comparator.compare(
                unanchoredTwoDegreePath(), TONIC_HZ, "Uşşak",
                MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        check(unanchoredTwoDegree.phraseEvidence().status()
                        == MakamProfileComparison.PhraseEvidenceStatus.PARTIAL_MIXED,
                "a two-degree path must remain visible as partial/mixed evidence");
        check(unanchoredTwoDegree.phraseEvidence().qualifyingPhraseCount() == 0
                        && unanchoredTwoDegree.phraseEvidence().partialPhraseCount() == 1,
                "an unanchored two-degree path must not become a complete phrase claim");
        check(unanchoredTwoDegree.sufficientEvidence()
                        && unanchoredTwoDegree.candidates().stream()
                        .anyMatch(value -> value.scorePercent() > 0.0),
                "usable global inventory must still produce the required ranked similarity graph");

        MakamProfileComparison all = comparator.compare(firstFour, TONIC_HZ, "Uşşak",
                MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.allProfiles());
        assertSameCoreScores(defaults, all,
                "enabling optional rows must not change any core score");
        check(defaults.observedClusters().equals(all.observedClusters())
                        && defaults.phraseEvidence().equals(all.phraseEvidence()),
                "enabling optional rows must not change the common inventory or phrase evidence");

        MakamProfileComparison withUpperDegrees = comparator.compare(events(true), TONIC_HZ, "Uşşak",
                MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        assertSameCoreScores(defaults, withUpperDegrees,
                "events above the fixed first-four domain must not lower first-four scores");
        check(defaults.acceptedEventCount() == withUpperDegrees.acceptedEventCount(),
                "upper-degree events must not enter the reported first-four evidence count");

        // Every surviving reference must see the same measured modes.  The
        // removed Bûselik comparison row may not reappear, while a centre near
        // Uşşak's 8-Hc second degree must score closer to Uşşak than to Kürdî.
        MakamProfileComparison lowerSecond = comparator.compare(microtonalEvents(8.2),
                TONIC_HZ, "Uşşak", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        MakamProfileComparison higherSecond = comparator.compare(microtonalEvents(8.95),
                TONIC_HZ, "Uşşak", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        check(lowerSecond.candidates().stream()
                        .noneMatch(value -> value.makamName().equals("Bûselik")),
                "Bûselik must not reappear as a comparison candidate");
        check(profileScore(lowerSecond, "Uşşak") > profileScore(lowerSecond, "Kürdî"),
                "8.2 Hc must be nearer Uşşak than Kürdî");
        check(profileScore(higherSecond, "Uşşak") > profileScore(higherSecond, "Kürdî"),
                "8.95 Hc must remain nearer Uşşak than Kürdî");

        // Candidate scoring must never reselect evidence to favour a reference.
        // The exact same observed second-degree centre is compared continuously
        // with both theoretical centres: 8.2-8.0 = +0.2 Hc for Uşşak and
        // 8.2-4.0 = +4.2 Hc for Kürdî.
        MakamProfileComparison.DegreeMatch lowerUssakSecond = degree(lowerSecond,
                "Uşşak", 2);
        MakamProfileComparison.DegreeMatch lowerKurdiSecond = degree(lowerSecond,
                "Kürdî", 2);
        close(lowerUssakSecond.observedHc(), 8.2, 1.0e-5,
                "Uşşak must use the immutable observed second-degree centre");
        close(lowerKurdiSecond.observedHc(), 8.2, 1.0e-5,
                "Kürdî must use the same immutable observed second-degree centre");
        close(lowerUssakSecond.residualHc(), +0.2, 1.0e-5,
                "8.2 Hc evidence must be +0.2 Hc from the Uşşak reference");
        close(lowerKurdiSecond.residualHc(), +4.2, 1.0e-5,
                "8.2 Hc evidence must be +4.2 Hc from the Kürdî reference");
        check(lowerUssakSecond.eventCount() == lowerKurdiSecond.eventCount(),
                "reference rows must not select different event counts");
        close(lowerUssakSecond.totalDurationSeconds(),
                lowerKurdiSecond.totalDurationSeconds(), 1.0e-12,
                "reference rows must not select different evidence durations");
        MakamProfileComparison.DegreeMatch higherUssakSecond = degree(higherSecond,
                "Uşşak", 2);
        MakamProfileComparison.DegreeMatch higherKurdiSecond = degree(higherSecond,
                "Kürdî", 2);
        close(higherUssakSecond.observedHc(), 8.95, 1.0e-5,
                "Uşşak must use the immutable 8.95-Hc observed centre");
        close(higherKurdiSecond.observedHc(), 8.95, 1.0e-5,
                "Kürdî must use the same immutable 8.95-Hc observed centre");
        close(higherKurdiSecond.residualHc(), +4.95, 1.0e-5,
                "8.95 Hc evidence must be +4.95 Hc from the Kürdî reference");
        close(higherUssakSecond.residualHc(), +0.95, 1.0e-5,
                "8.95 Hc evidence must be +0.95 Hc from the Uşşak reference");
        check(higherUssakSecond.eventCount() == higherKurdiSecond.eventCount(),
                "8.95-Hc reference rows must not select different event counts");
        close(higherUssakSecond.totalDurationSeconds(),
                higherKurdiSecond.totalDurationSeconds(), 1.0e-12,
                "8.95-Hc reference rows must not select different evidence durations");
        assertSimilarityOrder(lowerSecond);
        assertSimilarityOrder(higherSecond);

        // Separate performed modes must remain separate common phrase
        // evidence. In particular, equally supported 4.93-Hc and 9.03-Hc
        // contacts must never be replaced by an unperformed synthetic centre
        // near 6.98 Hc.
        MakamProfileComparison mixedModes = comparator.compare(mixedSecondModes(),
                TONIC_HZ, "Kürdilihicazkâr", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        check(hasObservedPeak(mixedModes.phraseEvidence(), 2, 4.93, .15),
                "the 4.93-Hc second-degree mode disappeared from phrase evidence");
        check(hasObservedPeak(mixedModes.phraseEvidence(), 2, 9.03, .15),
                "the 9.03-Hc second-degree mode disappeared from phrase evidence");
        check(mixedModes.phraseEvidence().degreePeaks().stream()
                        .noneMatch(value -> value.degree() == 2
                                && value.centerHc() > 6.0 && value.centerHc() < 8.0),
                "separate 4.93/9.03-Hc modes were collapsed into an unperformed mean");

        MakamProfileComparison singleAttack = comparator.compare(repeatedDegreePath(false),
                TONIC_HZ, "Uşşak", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        MakamProfileComparison repeatedAttack = comparator.compare(repeatedDegreePath(true),
                TONIC_HZ, "Uşşak", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        check(degreeEventCount(repeatedAttack.phraseEvidence(), 2)
                        > degreeEventCount(singleAttack.phraseEvidence(), 2),
                "repeated performed notes must remain visible in phrase evidence");
        close(degreeEvidenceWeight(repeatedAttack.phraseEvidence(), 2),
                degreeEvidenceWeight(singleAttack.phraseEvidence(), 2), 1.0e-9,
                "re-attacks of one degree in one phrase must be capped so they cannot dominate");

        // A global 9-Hc contact that does not form an ordered phrase with the
        // tonic or common fourth remains in the inventory and contributes to
        // the non-directional ranking, while phrase status stays independent.
        MakamProfileComparison isolatedInventory = comparator.compare(isolatedGlobalContacts(),
                TONIC_HZ, "Kürdilihicazkâr", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        check(isolatedInventory.observedClusters().stream()
                        .anyMatch(value -> Math.abs(value.centerHc() - 9.0) < .15),
                "isolated 9-Hc contact must remain visible in the global inventory");
        check(isolatedInventory.phraseEvidence().degreePeaks().stream()
                        .noneMatch(value -> value.degree() == 2
                                && Math.abs(value.centerHc() - 9.0) < .15),
                "isolated global 9-Hc contact leaked into tetrachord phrase evidence");
        check(isolatedInventory.phraseEvidence().status()
                        != MakamProfileComparison.PhraseEvidenceStatus.COMPLETE,
                "an unanchored isolated contact must not produce a complete tetrachord claim");
        check(isolatedInventory.sufficientEvidence()
                        && isolatedInventory.candidates().stream()
                        .anyMatch(value -> value.scorePercent() > 0.0),
                "global inventory must retain a ranked comparison even without a complete phrase path");

        // The six primary tetrachords all end at 22 Hc.  Evidence between that
        // shared fourth degree and an optional profile's higher fourth degree
        // must not enter or alter the primary six-row comparison.
        List<PitchEvent> withTwentyFourHc = new ArrayList<>(firstFour);
        for (int repeat = 0; repeat < 4; repeat++) {
            withTwentyFourHc.add(event(1100 + repeat, repeat * 8.0 + 3.2,
                    24.0 * (1200.0 / 53.0), .50));
        }
        MakamProfileComparison nearUpperOptional = comparator.compare(withTwentyFourHc,
                TONIC_HZ, "Uşşak", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        check(nearUpperOptional.acceptedEventCount() == defaults.acceptedEventCount(),
                "24-Hc evidence must stay outside the core 0-22-Hc first-four domain");
        assertSameCoreScores(defaults, nearUpperOptional,
                "24-Hc optional-profile evidence must not change core scores");

        // A reliable 60 ms contact at Kürdî's diagnostic 4-Hc second degree
        // remains in the global inventory: it must no longer be deleted solely
        // because PitchOccurrenceSemantics calls it SHORT_CONTACT.  It changes
        // the inventory ranking but cannot change the separately reported
        // ordered-phrase evidence.
        List<PitchEvent> sustainedBuselik = microtonalEvents(9.0);
        MakamProfileComparison beforeShort = comparator.compare(sustainedBuselik,
                TONIC_HZ, "Bûselik", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        List<PitchEvent> withReliableShort = new ArrayList<>(sustainedBuselik);
        withReliableShort.add(shortContact(1000, 29.0, 4.0 * (1200.0 / 53.0),
                .060, .96, 1.0));
        MakamProfileComparison afterShort = comparator.compare(withReliableShort,
                TONIC_HZ, "Bûselik", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        check(afterShort.acceptedEventCount() == beforeShort.acceptedEventCount() + 1,
                "a reliable 60 ms SHORT_CONTACT must enter short-measurable profile evidence");
        check(afterShort.observedClusters().stream()
                        .anyMatch(value -> Math.abs(value.centerHc() - 4.0) < .15),
                "the reliable 4-Hc short contact must remain in the global inventory");
        check(!coreScores(beforeShort).equals(coreScores(afterShort)),
                "an admitted 4-Hc inventory mode must affect the non-directional ranking");
        check(beforeShort.phraseEvidence().equals(afterShort.phraseEvidence()),
                "an isolated inventory contact must not alter ordered-phrase evidence");

        // Weak transients remain audit-only: they stay in the caller's event
        // collection but must not alter accepted evidence or profile scores.
        List<PitchEvent> withWeakShort = new ArrayList<>(sustainedBuselik);
        withWeakShort.add(shortContact(1001, 30.0, 4.0 * (1200.0 / 53.0),
                .060, .70, 1.0));
        MakamProfileComparison weakShort = comparator.compare(withWeakShort,
                TONIC_HZ, "Bûselik", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        check(weakShort.acceptedEventCount() == beforeShort.acceptedEventCount(),
                "a low-confidence short contact must remain audit-only");
        assertSameCoreScores(beforeShort, weakShort,
                "audit-only weak contacts must not change profile scores");

        List<PitchEvent> withAuditContact = new ArrayList<>(sustainedBuselik);
        withAuditContact.add(shortContact(1002, 31.0, 4.0 * (1200.0 / 53.0),
                .040, .98, 1.0));
        MakamProfileComparison auditContact = comparator.compare(withAuditContact,
                TONIC_HZ, "Bûselik", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
        check(auditContact.acceptedEventCount() == beforeShort.acceptedEventCount(),
                "a 20-60 ms contact must remain audit/directional evidence, not enter the main mean");
        assertSameCoreScores(beforeShort, auditContact,
                "audit-only 20-60 ms contacts must not change non-directional profile scores");

        System.out.println("MAKAM_PROFILE_FIRST_FOUR_SCORING_OK");
    }

    private static List<PitchEvent> microtonalEvents(double secondHc) {
        double hc = 1200.0 / 53.0;
        List<PitchEvent> result = new ArrayList<>();
        int index = 1;
        for (int repeat = 0; repeat < 4; repeat++) {
            double start = repeat * 8.0;
            result.add(event(index++, start, 0.0, .50));
            result.add(event(index++, start + .4, secondHc * hc, .50));
            result.add(event(index++, start + .8, 13.0 * hc, .50));
            result.add(event(index++, start + 1.2, 22.0 * hc, .50));
        }
        return List.copyOf(result);
    }

    private static List<PitchEvent> orderedAnchoredThreeDegreePath(boolean tonicAnchored) {
        double hc = 1200.0 / 53.0;
        double[] degrees = tonicAnchored
                ? new double[]{0.0, 8.2, 13.0}
                : new double[]{8.2, 13.0, 22.0};
        List<PitchEvent> result = new ArrayList<>();
        for (int index = 0; index < degrees.length; index++) {
            result.add(event(7000 + index, index * .3, degrees[index] * hc, .12));
        }
        return List.copyOf(result);
    }

    private static List<PitchEvent> unanchoredTwoDegreePath() {
        double hc = 1200.0 / 53.0;
        return List.of(
                event(7100, 0.0, 8.2 * hc, .12),
                event(7101, .3, 13.0 * hc, .12));
    }

    private static List<PitchEvent> repeatedDegreePath(boolean repeated) {
        double hc = 1200.0 / 53.0;
        List<PitchEvent> result = new ArrayList<>();
        int index = 7200;
        result.add(event(index++, 0.0, 0.0, .08));
        result.add(event(index++, .25, 8.2 * hc, .08));
        if (repeated) {
            result.add(event(index++, .50, 8.2 * hc, .08));
            result.add(event(index++, .75, 8.2 * hc, .08));
            result.add(event(index++, 1.00, 8.2 * hc, .08));
        }
        double thirdStart = repeated ? 1.25 : .50;
        result.add(event(index++, thirdStart, 13.0 * hc, .08));
        result.add(event(index, thirdStart + .25, 22.0 * hc, .08));
        return List.copyOf(result);
    }

    private static List<PitchEvent> mixedSecondModes() {
        double hc = 1200.0 / 53.0;
        List<PitchEvent> result = new ArrayList<>();
        int index = 5000;
        double[][] phrases = {
                {0.0, 4.93, 13.0, 22.0},
                {0.0, 9.03, 13.0, 22.0},
                {0.0, 4.93, 13.0, 22.0},
                {0.0, 9.03, 13.0, 22.0}
        };
        for (int phrase = 0; phrase < phrases.length; phrase++) {
            double start = phrase * 8.0;
            for (int degree = 0; degree < phrases[phrase].length; degree++) {
                result.add(event(index++, start + degree * .4,
                        phrases[phrase][degree] * hc, .22));
            }
        }
        return List.copyOf(result);
    }

    private static List<PitchEvent> isolatedGlobalContacts() {
        double hc = 1200.0 / 53.0;
        return List.of(
                event(6000, 1.0, 9.0 * hc, .30),
                event(6001, 8.0, 13.0 * hc, .30),
                event(6002, 16.0, 22.0 * hc, .30));
    }

    private static boolean hasObservedPeak(MakamProfileComparison.PhraseEvidence evidence,
                                           int degree, double expectedHc,
                                           double toleranceHc) {
        return evidence.degreePeaks().stream()
                .anyMatch(value -> value.degree() == degree
                        && Math.abs(value.centerHc() - expectedHc) <= toleranceHc);
    }

    private static int degreeEventCount(MakamProfileComparison.PhraseEvidence evidence,
                                        int degree) {
        return evidence.degreePeaks().stream()
                .filter(value -> value.degree() == degree)
                .mapToInt(MakamProfileComparison.DegreePeak::eventCount)
                .sum();
    }

    private static double degreeEvidenceWeight(MakamProfileComparison.PhraseEvidence evidence,
                                               int degree) {
        return evidence.degreePeaks().stream()
                .filter(value -> value.degree() == degree)
                .mapToDouble(MakamProfileComparison.DegreePeak::evidenceWeight)
                .sum();
    }

    private static double profileScore(MakamProfileComparison comparison, String name) {
        return comparison.candidates().stream()
                .filter(value -> value.makamName().equals(name))
                .findFirst().orElseThrow().scorePercent();
    }

    private static MakamProfileComparison.DegreeMatch degree(
            MakamProfileComparison comparison, String name, int degree) {
        return comparison.candidates().stream()
                .filter(value -> value.makamName().equals(name))
                .findFirst().orElseThrow().degreeMatches().stream()
                .filter(value -> value.degree() == degree)
                .findFirst().orElseThrow();
    }

    private static void assertSimilarityOrder(MakamProfileComparison comparison) {
        double previous = Double.POSITIVE_INFINITY;
        for (MakamProfileComparison.Candidate candidate : comparison.candidates()) {
            check(candidate.scorePercent() <= previous + 1.0e-9,
                    "candidate rows must be ordered by descending similarity: "
                            + candidate.makamName());
            previous = candidate.scorePercent();
        }
    }

    private static List<PitchEvent> events(boolean includeUpperDegrees) {
        return events(includeUpperDegrees, 0.50);
    }

    private static List<PitchEvent> events(boolean includeUpperDegrees, double durationSeconds) {
        List<PitchEvent> result = new ArrayList<>();
        int index = 1;
        for (int repeat = 0; repeat < 4; repeat++) {
            double phraseStart = repeat * 8.0;
            result.add(event(index++, phraseStart, 0.0, durationSeconds));
            for (int degree = 0; degree < USSAK_NON_TONIC.length; degree++) {
                double relativeCent = USSAK_NON_TONIC[degree] + (repeat - 1.5) * 0.8;
                double start = phraseStart + .4 + degree * 0.8;
                result.add(event(index++, start, relativeCent, durationSeconds));
            }
            if (includeUpperDegrees) {
                result.add(event(index++, phraseStart + 3.2, 700.0 + repeat,
                        durationSeconds));
                result.add(event(index++, phraseStart + 4.0, 905.0 - repeat,
                        durationSeconds));
            }
        }
        return List.copyOf(result);
    }

    private static PitchEvent event(int index, double start, double relativeCent) {
        return event(index, start, relativeCent, 0.50);
    }

    private static PitchEvent event(int index, double start, double relativeCent,
                                    double durationSeconds) {
        return event(index, start, relativeCent, durationSeconds, .95, 2.0,
                EventType.SUSTAINED);
    }

    private static PitchEvent shortContact(int index, double start, double relativeCent,
                                           double durationSeconds, double confidence,
                                           double stabilityCent) {
        return event(index, start, relativeCent, durationSeconds, confidence, stabilityCent,
                EventType.SHORT_CONTACT);
    }

    private static PitchEvent event(int index, double start, double relativeCent,
                                    double durationSeconds, double confidence,
                                    double stabilityCent, EventType eventType) {
        PitchOccurrence occurrence = new PitchOccurrence(index, start, start + durationSeconds,
                TONIC_CENT + relativeCent, TONIC_CENT + relativeCent,
                stabilityCent, 0.0, 0.0, 0.0, 3.0, false, confidence,
                MelodicDirection.LEVEL, MelodicDirection.LEVEL, MelodicDirection.LEVEL,
                eventType);
        return new PitchEvent("test-" + index, "Test", relativeCent, occurrence);
    }

    private static void assertSameCoreScores(MakamProfileComparison expected,
                                             MakamProfileComparison actual,
                                             String message) {
        Map<String, Double> expectedScores = coreScores(expected);
        Map<String, Double> actualScores = coreScores(actual);
        check(expectedScores.keySet().equals(actualScores.keySet()), message + " (row set)");
        for (String name : expectedScores.keySet()) {
            check(Math.abs(expectedScores.get(name) - actualScores.get(name)) < 1.0e-9,
                    message + ": " + name + " " + expectedScores.get(name)
                            + " != " + actualScores.get(name));
        }
    }

    private static Map<String, Double> coreScores(MakamProfileComparison comparison) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (MakamProfileComparison.Candidate candidate : comparison.candidates()) {
            if (FirstFourProfileReferenceCatalog.coreDisplayNames().contains(candidate.makamName())) {
                result.put(candidate.makamName(), candidate.scorePercent());
            }
        }
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void close(double actual, double expected,
                              double tolerance, String message) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
