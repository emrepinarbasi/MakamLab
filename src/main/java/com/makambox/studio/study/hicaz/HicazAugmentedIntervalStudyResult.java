package com.makambox.studio.study.hicaz;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Immutable result shared by future UI, DOCX and machine-readable exporters. */
public record HicazAugmentedIntervalStudyResult(
        double tonicHertz,
        List<HicazDegreeEvent> events,
        List<HicazPhrase> phrases,
        List<HicazAugmentedIntervalMeasurement> directIntervals,
        Map<HicazPhraseDirection, Map<HicazTetrachordDegree, HicazDegreeStatistics>>
                degreeStatisticsByDirection,
        Map<HicazTetrachordDegree, HicazDegreeStatistics> overallDegreeStatistics,
        Map<HicazPhraseDirection, HicazIntervalStatistics> intervalStatisticsByDirection
) {
    public HicazAugmentedIntervalStudyResult {
        if (!Double.isFinite(tonicHertz) || tonicHertz <= 0.0) {
            throw new IllegalArgumentException("tonicHertz must be finite and positive");
        }
        events = List.copyOf(events == null ? List.of() : events);
        phrases = List.copyOf(phrases == null ? List.of() : phrases);
        directIntervals = List.copyOf(directIntervals == null ? List.of() : directIntervals);
        degreeStatisticsByDirection = freezeDirectionalDegreeStatistics(
                degreeStatisticsByDirection);
        overallDegreeStatistics = freezeOverallDegreeStatistics(overallDegreeStatistics);
        intervalStatisticsByDirection = freezeIntervalStatistics(intervalStatisticsByDirection);
    }

    public List<HicazDegreeEvent> includedEvents() {
        return events.stream().filter(HicazDegreeEvent::included).toList();
    }

    public List<HicazDegreeEvent> auditEvents() {
        return events.stream().filter(value -> !value.included()).toList();
    }

    public List<HicazPhrase> includedPhrases() {
        return phrases.stream().filter(HicazPhrase::included).toList();
    }

    public List<HicazPhrase> auditPhrases() {
        return phrases.stream().filter(value -> !value.included()).toList();
    }

    public List<HicazAugmentedIntervalMeasurement> directIntervals(
            HicazPhraseDirection direction) {
        return directIntervals.stream().filter(value -> value.direction() == direction).toList();
    }

    public HicazDegreeStatistics degreeStatistics(HicazPhraseDirection direction,
                                                  HicazTetrachordDegree degree) {
        return degreeStatisticsByDirection.get(direction).get(degree);
    }

    public HicazDegreeStatistics overallDegreeStatistics(HicazTetrachordDegree degree) {
        return overallDegreeStatistics.get(degree);
    }

    public HicazIntervalStatistics intervalStatistics(HicazPhraseDirection direction) {
        return intervalStatisticsByDirection.get(direction);
    }

    private static Map<HicazPhraseDirection, Map<HicazTetrachordDegree, HicazDegreeStatistics>>
    freezeDirectionalDegreeStatistics(
            Map<HicazPhraseDirection, Map<HicazTetrachordDegree, HicazDegreeStatistics>> source) {
        EnumMap<HicazPhraseDirection, Map<HicazTetrachordDegree, HicazDegreeStatistics>> outer =
                new EnumMap<>(HicazPhraseDirection.class);
        for (HicazPhraseDirection direction : HicazPhraseDirection.values()) {
            EnumMap<HicazTetrachordDegree, HicazDegreeStatistics> inner =
                    new EnumMap<>(HicazTetrachordDegree.class);
            Map<HicazTetrachordDegree, HicazDegreeStatistics> supplied =
                    source == null ? null : source.get(direction);
            if (supplied != null) inner.putAll(supplied);
            for (HicazTetrachordDegree degree : HicazTetrachordDegree.values()) {
                inner.putIfAbsent(degree, HicazDegreeStatistics.empty());
            }
            outer.put(direction, Map.copyOf(inner));
        }
        return Map.copyOf(outer);
    }

    private static Map<HicazTetrachordDegree, HicazDegreeStatistics>
    freezeOverallDegreeStatistics(Map<HicazTetrachordDegree, HicazDegreeStatistics> source) {
        EnumMap<HicazTetrachordDegree, HicazDegreeStatistics> copy =
                new EnumMap<>(HicazTetrachordDegree.class);
        if (source != null) copy.putAll(source);
        for (HicazTetrachordDegree degree : HicazTetrachordDegree.values()) {
            copy.putIfAbsent(degree, HicazDegreeStatistics.empty());
        }
        return Map.copyOf(copy);
    }

    private static Map<HicazPhraseDirection, HicazIntervalStatistics>
    freezeIntervalStatistics(Map<HicazPhraseDirection, HicazIntervalStatistics> source) {
        EnumMap<HicazPhraseDirection, HicazIntervalStatistics> copy =
                new EnumMap<>(HicazPhraseDirection.class);
        if (source != null) copy.putAll(source);
        for (HicazPhraseDirection direction : HicazPhraseDirection.values()) {
            copy.putIfAbsent(direction, HicazIntervalStatistics.empty());
        }
        return Map.copyOf(copy);
    }
}
