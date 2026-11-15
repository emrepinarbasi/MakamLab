package com.makambox.studio.study;

import com.makambox.studio.model.EventType;
import com.makambox.studio.model.MelodicDirection;

import java.util.ArrayList;
import java.util.List;

/** Regression contract for ordered first-four paths in ordinary pitch reports. */
public final class FirstFourDirectionalAnalyzerRegressionTest {
    private static int nextIndex = 1;

    public static void main(String[] args) {
        FirstFourDirectionalAnalyzer analyzer = new FirstFourDirectionalAnalyzer();
        MakamProfileComparison comparison = comparison();
        List<StudyPitchEvent> events = new ArrayList<>();

        // Repeated degrees are legitimate evidence.  The first degree-1 pair
        // is a short split; the degree-2 re-attack is intentionally longer
        // than 120 ms and must remain a distinct event without breaking 1-2-3-4.
        addPhrase(events, 0.00, new int[]{1, 1, 2, 2, 3, 4},
                new double[]{0.00, .17, .34, .75, .92, 1.09});
        addPhrase(events, 31.00, new int[]{1, 3, 4}, null);
        addPhrase(events, 61.00, new int[]{2, 3, 4}, null);

        addPhrase(events, 10.00, new int[]{4, 3, 3, 2, 2, 1}, null);
        addPhrase(events, 40.00, new int[]{4, 2, 1}, null);
        addPhrase(events, 70.00, new int[]{3, 2, 1}, null);

        // A true reversal must split the phrase.  It may start a new 2-3-4
        // phrase, but it must never be counted as one uninterrupted 1-2-3-4.
        addPhrase(events, 91.00, new int[]{1, 2, 3, 2, 3, 4}, null);

        // Unreliable evidence is a hard boundary and cannot bridge 1-2 to 3-4.
        events.add(event(101.00, 1, .90, false));
        events.add(event(101.18, 2, .90, false));
        events.add(event(101.36, 2, .40, false));
        events.add(event(101.54, 3, .90, false));
        events.add(event(101.72, 4, .90, true));

        // Long silence and an octave/register jump are also hard boundaries.
        events.add(event(110.00, 1, .90, false));
        events.add(event(110.18, 2, .90, false));
        events.add(event(111.30, 3, .90, false));
        events.add(event(111.48, 4, .90, true));
        events.add(event(120.00, 1, .90, false));
        events.add(event(120.18, 2, .90, false));
        events.add(event(120.36, 3, .90, false));
        events.add(eventAtCent(120.54, degreeCent(4) + 1200.0, .90, true));

        FirstFourDirectionalAnalyzer.Result result = analyzer.compare(events, comparison);
        check(result.ascending().size() == 2 && result.descending().size() == 2,
                "Only active profile candidates may appear");
        assertCommonEvidence(result.ascending(), "ascending");
        assertCommonEvidence(result.descending(), "descending");
        FirstFourDirectionalAnalyzer.Candidate ascending = candidate(result.ascending(), "Kürdî");
        FirstFourDirectionalAnalyzer.Candidate descending = candidate(result.descending(), "Kürdî");

        check(ascending.patternCounts().get("1→2→3→4") == 1,
                "Repetition/reversal handling changed the full ascending path count: "
                        + ascending.patternCounts());
        check(ascending.patternCounts().get("1→3→4") == 1,
                "Skipped ascending path was not counted");
        check(ascending.patternCounts().get("2→3→4") == 2,
                "2-3-4 path or post-reversal phrase boundary was lost");
        check(ascending.patternCounts().get("1→2→3") >= 1,
                "The pre-reversal 1-2-3 combination must remain valid evidence: "
                        + ascending.patternCounts());
        check(ascending.evidenceEventCount() == 29,
                "Repeated degree events must remain unique evidence: "
                        + ascending.evidenceEventCount());
        check(ascending.phraseCount() == 10 && ascending.timeRegionCount() >= 4,
                "Ascending phrase/time-region evidence is inconsistent: "
                        + ascending.phraseCount() + "/" + ascending.timeRegionCount());
        check(ascending.evidenceStatus() == FirstFourDirectionalAnalyzer.EvidenceStatus.COMPARABLE,
                "Ascending evidence should be fully comparable");
        check(ascending.coveragePercent() > 99.9 && ascending.scorePercent() > 99.9,
                "Exact ascending evidence should have full degree coverage and similarity");

        check(descending.patternCounts().get("4→3→2→1") == 1,
                "Repeated descending degrees must collapse only for path matching");
        check(descending.patternCounts().get("4→2→1") == 1
                        && descending.patternCounts().get("3→2→1") == 1,
                "Descending allowed-pattern counts are incomplete");
        check(descending.evidenceEventCount() == 14,
                "Descending repeat count must contribute to evidence: "
                        + descending.evidenceEventCount() + " " + descending.patternCounts());
        check(descending.evidenceStatus() == FirstFourDirectionalAnalyzer.EvidenceStatus.COMPARABLE,
                "Descending evidence should be fully comparable");

        FirstFourDirectionalAnalyzer.Candidate wrong = candidate(result.ascending(), "Uşşak");
        check(Double.isFinite(wrong.scorePercent())
                        && wrong.scorePercent() < ascending.scorePercent(),
                "A competing profile may retain descriptive partial-path evidence, but it must "
                        + "rank below the exact profile");

        FirstFourDirectionalAnalyzer.Result onePhrase = analyzer.compare(
                phrase(200.0, new int[]{1, 2, 3, 4}), comparison);
        FirstFourDirectionalAnalyzer.Candidate insufficient =
                candidate(onePhrase.ascending(), "Kürdî");
        check(insufficient.phraseCount() == 1
                        && insufficient.evidenceStatus()
                        == FirstFourDirectionalAnalyzer.EvidenceStatus.INSUFFICIENT
                        && Double.isFinite(insufficient.scorePercent())
                        && insufficient.scorePercent() > 99.9,
                "One phrase must retain its descriptive percentage while being marked insufficient");

        // Fast but high-confidence contacts are legitimate path evidence. They
        // must not be deleted merely because each contact is shorter than the
        // sustained-pitch report's 100 ms summary threshold.
        List<StudyPitchEvent> fastContacts = new ArrayList<>();
        addFastPhrase(fastContacts, 240.0, new int[]{1, 2, 3, 4});
        addFastPhrase(fastContacts, 271.0, new int[]{1, 3, 4});
        FirstFourDirectionalAnalyzer.Candidate fast = candidate(
                analyzer.compare(fastContacts, comparison).ascending(), "Kürdî");
        check(fast.phraseCount() == 2 && Double.isFinite(fast.scorePercent())
                        && fast.scorePercent() > 99.9,
                "Reliable short contacts must produce directional similarity evidence");

        // Uşşak and Bûselik share the same third and fourth degree in this
        // fixture; their diagnostic difference is the second degree (8 Hc vs
        // 9 Hc).  Both candidates must receive the exact same observed events.
        // The comparison is continuous: 8.2 Hc is nearer Uşşak, whereas
        // 8.8 Hc is nearer Bûselik.  No profile may select its own evidence.
        MakamProfileComparison microtonal = ussakBuselikComparison();
        FirstFourDirectionalAnalyzer.Result lowSecond = analyzer.compare(
                microtonalFixture(8.2), microtonal);
        FirstFourDirectionalAnalyzer.Result highSecond = analyzer.compare(
                microtonalFixture(8.8), microtonal);
        assertCommonEvidence(lowSecond.ascending(), "8.2 Hc ascending");
        assertCommonEvidence(highSecond.ascending(), "8.8 Hc ascending");
        FirstFourDirectionalAnalyzer.Candidate lowUssak =
                candidate(lowSecond.ascending(), "Uşşak");
        FirstFourDirectionalAnalyzer.Candidate lowBuselik =
                candidate(lowSecond.ascending(), "Bûselik");
        FirstFourDirectionalAnalyzer.Candidate highUssak =
                candidate(highSecond.ascending(), "Uşşak");
        FirstFourDirectionalAnalyzer.Candidate highBuselik =
                candidate(highSecond.ascending(), "Bûselik");
        check(lowUssak.scorePercent() > lowBuselik.scorePercent(),
                "An 8.2 Hc second degree must be nearer Uşşak than Bûselik: "
                        + lowUssak.scorePercent() + "/" + lowBuselik.scorePercent());
        check(highBuselik.scorePercent() > highUssak.scorePercent(),
                "An 8.8 Hc second degree must be nearer Bûselik than Uşşak: "
                        + highBuselik.scorePercent() + "/" + highUssak.scorePercent());
        check(lowUssak.evidenceEventCount() == lowBuselik.evidenceEventCount()
                        && highUssak.evidenceEventCount() == highBuselik.evidenceEventCount(),
                "Competing profiles must not filter the common F0 evidence independently");

        // The explicit 80% reporting boundary controls the ranking. Finite
        // strong matches come first, lower percentages follow, and a profile
        // without a computable path is last.
        check(result.ascending().get(0).profileName().equals("Kürdî")
                        && Double.isFinite(result.ascending().get(0).scorePercent()),
                "Directional ranking must prioritize the strongest finite score");

        // A longer, more confident repeated attack must remain a separate
        // event and carry more statistical weight than the same residual in
        // a brief/lower-confidence re-attack.  The degree repetition still
        // resolves to one 1-2-3-4 path in both fixtures.
        FirstFourDirectionalAnalyzer.Candidate lightRepeat = candidate(
                analyzer.compare(weightedRepeatFixture(.12, .66), comparison).ascending(),
                "Kürdî");
        FirstFourDirectionalAnalyzer.Candidate heavyRepeat = candidate(
                analyzer.compare(weightedRepeatFixture(.40, .96), comparison).ascending(),
                "Kürdî");
        check(lightRepeat.patternCounts().get("1→2→3→4") == 2
                        && heavyRepeat.patternCounts().get("1→2→3→4") == 2,
                "Repeated degree must not alter the logical path count");
        check(lightRepeat.evidenceEventCount() == 9 && heavyRepeat.evidenceEventCount() == 9,
                "Repeated attack must remain an individual evidence event");
        check(heavyRepeat.meanSignedErrorCent() > lightRepeat.meanSignedErrorCent(),
                "Duration/confidence weight of a repeated attack was not preserved");

        System.out.println("FIRST_FOUR_DIRECTIONAL_OK");
    }

    private static void assertCommonEvidence(
            List<FirstFourDirectionalAnalyzer.Candidate> candidates, String label) {
        check(!candidates.isEmpty(), label + " candidates are missing");
        FirstFourDirectionalAnalyzer.Candidate first = candidates.get(0);
        for (FirstFourDirectionalAnalyzer.Candidate value : candidates) {
            check(value.phraseCount() == first.phraseCount()
                            && value.evidenceEventCount() == first.evidenceEventCount()
                            && value.timeRegionCount() == first.timeRegionCount(),
                    label + " candidates were not scored on one common evidence set: "
                            + first.profileName() + "=" + first.phraseCount() + "/"
                            + first.evidenceEventCount() + "/" + first.timeRegionCount()
                            + ", " + value.profileName() + "=" + value.phraseCount() + "/"
                            + value.evidenceEventCount() + "/" + value.timeRegionCount());
        }
    }

    private static MakamProfileComparison ussakBuselikComparison() {
        double hc = 1200.0 / 53.0;
        MakamProfileComparison.Candidate ussak = candidateProfile("Uşşak",
                new double[]{0.0, 8.0 * hc, 13.0 * hc, 22.0 * hc});
        MakamProfileComparison.Candidate buselik = candidateProfile("Bûselik",
                new double[]{0.0, 9.0 * hc, 13.0 * hc, 22.0 * hc});
        return new MakamProfileComparison("AEU", "fixture", true, "fixture",
                40, 4, 12.0, 4, List.of(), List.of(ussak, buselik));
    }

    private static List<StudyPitchEvent> microtonalFixture(double secondHc) {
        double hc = 1200.0 / 53.0;
        List<StudyPitchEvent> result = new ArrayList<>();
        addCentPhrase(result, 360.0,
                new double[]{0.0, secondHc * hc, 13.0 * hc, 22.0 * hc});
        addCentPhrase(result, 391.0,
                new double[]{0.0, secondHc * hc, 13.0 * hc, 22.0 * hc});
        return result;
    }

    private static void addCentPhrase(List<StudyPitchEvent> events, double start,
                                      double[] cents) {
        for (int index = 0; index < cents.length; index++) {
            events.add(eventAtCent(start + index * .18, cents[index], .94,
                    index == cents.length - 1));
        }
    }

    private static List<StudyPitchEvent> weightedRepeatFixture(double repeatDuration,
                                                                double repeatConfidence) {
        List<StudyPitchEvent> result = new ArrayList<>();
        result.add(event(300.00, 1, .90, false));
        result.add(event(300.18, 2, .90, false));
        result.add(eventAtCent(300.50, degreeCent(2) + 10.0,
                repeatConfidence, false, repeatDuration));
        result.add(event(301.00, 3, .90, false));
        result.add(event(301.18, 4, .90, true));
        addPhrase(result, 331.00, new int[]{1, 2, 3, 4}, null);
        return result;
    }

    private static MakamProfileComparison comparison() {
        MakamProfileComparison.Candidate kurdi = candidateProfile("Kürdî",
                new double[]{0.0, 90.0, 203.0, 294.0});
        MakamProfileComparison.Candidate ussak = candidateProfile("Uşşak",
                new double[]{0.0, 181.0, 294.0, 385.0});
        return new MakamProfileComparison("AEU", "Kürdilihicazkâr", true, "fixture",
                40, 4, 12.0, 4, List.of(), List.of(kurdi, ussak));
    }

    private static MakamProfileComparison.Candidate candidateProfile(String name,
                                                                      double[] cents) {
        List<MakamProfileComparison.DegreeMatch> degrees = new ArrayList<>();
        for (int i = 0; i < cents.length; i++) {
            degrees.add(new MakamProfileComparison.DegreeMatch(i + 1, cents[i], cents[i] / 22.641509,
                    true, cents[i], cents[i] / 22.641509, 0.0, 0.0, 10, 2.0));
        }
        return new MakamProfileComparison.Candidate(1, name, List.of(name), false,
                90.0, 2.0, 2.0, 4.0, 100.0, 100.0, 4, 4, degrees);
    }

    private static void addPhrase(List<StudyPitchEvent> events, double start, int[] degrees,
                                  double[] offsets) {
        for (int i = 0; i < degrees.length; i++) {
            double offset = offsets == null ? i * .18 : offsets[i];
            events.add(event(start + offset, degrees[i], .90, i == degrees.length - 1));
        }
    }

    private static void addFastPhrase(List<StudyPitchEvent> events, double start, int[] degrees) {
        for (int i = 0; i < degrees.length; i++) {
            double at = start + i * .05;
            events.add(eventAtCent(at, degreeCent(degrees[i]), .92,
                    i == degrees.length - 1, .02));
        }
    }

    private static List<StudyPitchEvent> phrase(double start, int[] degrees) {
        List<StudyPitchEvent> result = new ArrayList<>();
        addPhrase(result, start, degrees, null);
        return result;
    }

    private static StudyPitchEvent event(double start, int degree, double confidence,
                                         boolean phraseFinal) {
        return eventAtCent(start, degreeCent(degree), confidence, phraseFinal);
    }

    private static StudyPitchEvent eventAtCent(double start, double relativeCent,
                                               double confidence, boolean phraseFinal) {
        return eventAtCent(start, relativeCent, confidence, phraseFinal, .12);
    }

    private static StudyPitchEvent eventAtCent(double start, double relativeCent,
                                               double confidence, boolean phraseFinal,
                                               double duration) {
        return new StudyPitchEvent(nextIndex++, "d" + nextIndex, "D" + nextIndex,
                start, start + duration, duration,
                220.0 * Math.pow(2.0, relativeCent / 1200.0), relativeCent,
                relativeCent, relativeCent / 22.641509, 0.0, 0.0,
                0.0, 0.0, 0.0, 2.0, 0.0, confidence,
                MelodicDirection.LEVEL, MelodicDirection.LEVEL, MelodicDirection.LEVEL,
                EventType.SUSTAINED, phraseFinal);
    }

    private static double degreeCent(int degree) {
        return switch (degree) {
            case 1 -> 0.0;
            case 2 -> 90.0;
            case 3 -> 203.0;
            case 4 -> 294.0;
            default -> throw new IllegalArgumentException("degree " + degree);
        };
    }

    private static FirstFourDirectionalAnalyzer.Candidate candidate(
            List<FirstFourDirectionalAnalyzer.Candidate> candidates, String name) {
        return candidates.stream().filter(value -> value.profileName().equals(name))
                .findFirst().orElseThrow();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
