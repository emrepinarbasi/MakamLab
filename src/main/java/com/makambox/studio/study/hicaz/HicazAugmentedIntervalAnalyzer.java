package com.makambox.studio.study.hicaz;

import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchOccurrence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Detects maximal ascending/descending Hicaz-tetrachord paths and measures
 * degrees 2, 3 and 4 without quantising away their performed F0.
 *
 * <p>The canonical references are 0, 5, 17 and 22 Holderian commas.  A direct
 * augmented-second measurement is created only for an actually consecutive
 * 2→3 or 3→2 transition.  A complete 1→2→3→4 phrase is emitted once; nested
 * pairs and triplets are not duplicated.</p>
 */
public final class HicazAugmentedIntervalAnalyzer {
    public static final double MINIMUM_CANDIDATE_CONFIDENCE = .35;
    public static final double PRIMARY_STATISTICS_CONFIDENCE = .65;
    public static final double MINIMUM_EVENT_SECONDS = .040;
    public static final double MAXIMUM_DEGREE_DISTANCE_HC = 2.75;
    public static final double MAXIMUM_INTER_EVENT_GAP_SECONDS = 1.50;
    public static final double REPEATED_DEGREE_COLLAPSE_GAP_SECONDS = .25;

    private static final List<List<Integer>> ASCENDING_COMBINATIONS = List.of(
            List.of(1, 2), List.of(1, 3), List.of(1, 4),
            List.of(2, 3), List.of(3, 4),
            List.of(1, 2, 3), List.of(1, 3, 4),
            List.of(2, 3, 4), List.of(1, 2, 3, 4));
    private static final List<List<Integer>> DESCENDING_COMBINATIONS =
            ASCENDING_COMBINATIONS.stream().map(HicazAugmentedIntervalAnalyzer::reversed)
                    .toList();

    public static List<List<Integer>> ascendingCombinations() {
        return ASCENDING_COMBINATIONS;
    }

    public static List<List<Integer>> descendingCombinations() {
        return DESCENDING_COMBINATIONS;
    }

    public HicazAugmentedIntervalStudyResult analyze(List<PitchEvent> sourceEvents,
                                                      double tonicHertz) {
        Objects.requireNonNull(sourceEvents, "sourceEvents");
        if (!Double.isFinite(tonicHertz) || tonicHertz <= 0.0) {
            throw new IllegalArgumentException("tonicHertz must be finite and positive");
        }
        double tonicCent = PitchTracker.hertzToCent(tonicHertz);
        List<PitchEvent> ordered = sourceEvents.stream()
                .sorted(Comparator.comparingDouble(value -> value.occurrence().startSeconds()))
                .toList();
        List<HicazDegreeEvent> candidates = new ArrayList<>();
        for (PitchEvent source : ordered) {
            HicazDegreeEvent candidate = candidate(candidates.size() + 1, source, tonicCent);
            if (candidate != null) candidates.add(candidate);
        }

        List<HicazDegreeEvent> phraseNodes = collapseImmediateRepetitions(candidates);
        List<HicazPhrase> phrases = maximalPhrases(phraseNodes);
        List<HicazAugmentedIntervalMeasurement> direct = directIntervals(phrases);

        EnumMap<HicazPhraseDirection, Map<HicazTetrachordDegree, HicazDegreeStatistics>>
                directionalDegreeStatistics = new EnumMap<>(HicazPhraseDirection.class);
        for (HicazPhraseDirection direction : HicazPhraseDirection.values()) {
            List<HicazDegreeEvent> unique = uniqueIncludedPhraseEvents(phrases, direction);
            EnumMap<HicazTetrachordDegree, HicazDegreeStatistics> byDegree =
                    new EnumMap<>(HicazTetrachordDegree.class);
            for (HicazTetrachordDegree degree : HicazTetrachordDegree.values()) {
                byDegree.put(degree, HicazDegreeStatistics.from(unique.stream()
                        .filter(value -> value.degree() == degree).toList()));
            }
            directionalDegreeStatistics.put(direction, Map.copyOf(byDegree));
        }

        List<HicazDegreeEvent> uniqueOverall = uniqueIncludedPhraseEvents(phrases, null);
        EnumMap<HicazTetrachordDegree, HicazDegreeStatistics> overall =
                new EnumMap<>(HicazTetrachordDegree.class);
        for (HicazTetrachordDegree degree : HicazTetrachordDegree.values()) {
            overall.put(degree, HicazDegreeStatistics.from(uniqueOverall.stream()
                    .filter(value -> value.degree() == degree).toList()));
        }

        EnumMap<HicazPhraseDirection, HicazIntervalStatistics> intervalStatistics =
                new EnumMap<>(HicazPhraseDirection.class);
        for (HicazPhraseDirection direction : HicazPhraseDirection.values()) {
            intervalStatistics.put(direction, HicazIntervalStatistics.from(direct.stream()
                    .filter(HicazAugmentedIntervalMeasurement::included)
                    .filter(value -> value.direction() == direction).toList()));
        }
        return new HicazAugmentedIntervalStudyResult(tonicHertz, candidates, phrases, direct,
                directionalDegreeStatistics, overall, intervalStatistics);
    }

    private static HicazDegreeEvent candidate(int index, PitchEvent source,
                                               double tonicAbsoluteCent) {
        PitchOccurrence occurrence = source.occurrence();
        double confidence = occurrence.confidence();
        if (confidence + 1.0e-9 < MINIMUM_CANDIDATE_CONFIDENCE
                || !Double.isFinite(occurrence.observedCent())) return null;
        double observedRelative = occurrence.observedCent() - tonicAbsoluteCent;
        HicazTetrachordDegree nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (HicazTetrachordDegree degree : HicazTetrachordDegree.values()) {
            double distance = Math.abs(observedRelative - degree.theoreticalCent());
            if (distance < nearestDistance) {
                nearest = degree;
                nearestDistance = distance;
            }
        }
        if (nearest == null || nearestDistance > MAXIMUM_DEGREE_DISTANCE_HC
                * AnalysisConfig.COMMA_CENT + 1.0e-9) return null;
        double duration = Math.max(0.0, occurrence.durationSeconds());
        boolean included = confidence + 1.0e-9 >= PRIMARY_STATISTICS_CONFIDENCE
                && duration + 1.0e-9 >= MINIMUM_EVENT_SECONDS;
        String reason = included ? "" : confidence < PRIMARY_STATISTICS_CONFIDENCE
                ? "F0 güveni %35–65 aralığında; denetim adayı"
                : "Temas süresi ana istatistik için çok kısa";
        return new HicazDegreeEvent(index, occurrence.index(), nearest,
                occurrence.startSeconds(), occurrence.endSeconds(), duration,
                PitchTracker.centToHertz(occurrence.observedCent()), observedRelative,
                observedRelative - nearest.theoreticalCent(), confidence, included, reason);
    }

    private static List<HicazDegreeEvent> collapseImmediateRepetitions(
            List<HicazDegreeEvent> candidates) {
        if (candidates.isEmpty()) return List.of();
        List<HicazDegreeEvent> result = new ArrayList<>();
        int position = 0;
        while (position < candidates.size()) {
            HicazDegreeEvent best = candidates.get(position);
            int cursor = position + 1;
            while (cursor < candidates.size()) {
                HicazDegreeEvent next = candidates.get(cursor);
                if (next.degree() != best.degree()
                        || next.startSeconds() - candidates.get(cursor - 1).endSeconds()
                        > REPEATED_DEGREE_COLLAPSE_GAP_SECONDS) break;
                if (betterRepresentative(next, best)) best = next;
                cursor++;
            }
            result.add(best);
            position = cursor;
        }
        return List.copyOf(result);
    }

    private static boolean betterRepresentative(HicazDegreeEvent candidate,
                                                HicazDegreeEvent current) {
        if (candidate.included() != current.included()) return candidate.included();
        int confidence = Double.compare(candidate.confidence(), current.confidence());
        if (confidence != 0) return confidence > 0;
        return candidate.durationSeconds() > current.durationSeconds();
    }

    private static List<HicazPhrase> maximalPhrases(List<HicazDegreeEvent> events) {
        List<HicazPhrase> result = new ArrayList<>();
        int start = 0;
        while (start + 1 < events.size()) {
            HicazDegreeEvent first = events.get(start);
            HicazDegreeEvent second = events.get(start + 1);
            if (!connected(first, second)) { start++; continue; }
            int sign = Integer.compare(second.degree().number(), first.degree().number());
            if (sign == 0) { start++; continue; }
            int end = start + 1;
            while (end + 1 < events.size()) {
                HicazDegreeEvent before = events.get(end);
                HicazDegreeEvent after = events.get(end + 1);
                if (!connected(before, after)) break;
                int nextSign = Integer.compare(after.degree().number(), before.degree().number());
                if (nextSign != sign) break;
                end++;
            }
            List<HicazDegreeEvent> path = List.copyOf(events.subList(start, end + 1));
            HicazPhraseDirection direction = sign > 0
                    ? HicazPhraseDirection.ASCENDING : HicazPhraseDirection.DESCENDING;
            if (supported(path, direction)) {
                boolean included = path.stream().allMatch(HicazDegreeEvent::included);
                double confidence = path.stream().mapToDouble(HicazDegreeEvent::confidence)
                        .min().orElse(0.0);
                String reason = included ? "" : path.stream()
                        .filter(value -> !value.included()).map(HicazDegreeEvent::auditReason)
                        .filter(value -> !value.isBlank()).findFirst()
                        .orElse("Düşük güvenli derece; denetim cümlesi");
                result.add(new HicazPhrase(result.size() + 1, direction, path,
                        confidence, included, reason));
            }
            // The turning degree may anchor the following phrase.  Nested
            // subpaths within this maximal run are never emitted.
            start = end;
        }
        return List.copyOf(result);
    }

    private static boolean connected(HicazDegreeEvent before, HicazDegreeEvent after) {
        return after.startSeconds() - before.endSeconds()
                <= MAXIMUM_INTER_EVENT_GAP_SECONDS + 1.0e-9;
    }

    private static boolean supported(List<HicazDegreeEvent> path,
                                     HicazPhraseDirection direction) {
        List<Integer> numbers = path.stream().map(value -> value.degree().number()).toList();
        return (direction == HicazPhraseDirection.ASCENDING
                ? ASCENDING_COMBINATIONS : DESCENDING_COMBINATIONS).contains(numbers);
    }

    private static List<HicazAugmentedIntervalMeasurement> directIntervals(
            List<HicazPhrase> phrases) {
        List<HicazAugmentedIntervalMeasurement> result = new ArrayList<>();
        for (HicazPhrase phrase : phrases) {
            for (int position = 1; position < phrase.events().size(); position++) {
                HicazDegreeEvent before = phrase.events().get(position - 1);
                HicazDegreeEvent after = phrase.events().get(position);
                boolean ascending = before.degree() == HicazTetrachordDegree.DEGREE_2
                        && after.degree() == HicazTetrachordDegree.DEGREE_3;
                boolean descending = before.degree() == HicazTetrachordDegree.DEGREE_3
                        && after.degree() == HicazTetrachordDegree.DEGREE_2;
                if (!ascending && !descending) continue;
                HicazDegreeEvent second = ascending ? before : after;
                HicazDegreeEvent third = ascending ? after : before;
                double observed = third.observedRelativeCent() - second.observedRelativeCent();
                boolean included = phrase.included() && second.included() && third.included();
                String reason = included ? "" : phrase.auditReason().isBlank()
                        ? "Düşük güvenli doğrudan artık aralık; denetim adayı"
                        : phrase.auditReason();
                result.add(new HicazAugmentedIntervalMeasurement(result.size() + 1,
                        phrase.index(), phrase.direction(), second, third, observed,
                        observed - HicazAugmentedIntervalMeasurement.THEORETICAL_INTERVAL_CENT,
                        Math.min(second.confidence(), third.confidence()), included, reason));
            }
        }
        return List.copyOf(result);
    }

    private static List<HicazDegreeEvent> uniqueIncludedPhraseEvents(
            List<HicazPhrase> phrases, HicazPhraseDirection direction) {
        Map<Integer, HicazDegreeEvent> unique = new LinkedHashMap<>();
        for (HicazPhrase phrase : phrases) {
            if (!phrase.included() || direction != null && phrase.direction() != direction) continue;
            for (HicazDegreeEvent event : phrase.events()) unique.putIfAbsent(event.index(), event);
        }
        return List.copyOf(unique.values());
    }

    private static List<Integer> reversed(List<Integer> source) {
        List<Integer> copy = new ArrayList<>(source);
        java.util.Collections.reverse(copy);
        return List.copyOf(copy);
    }
}
