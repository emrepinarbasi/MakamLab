package com.makambox.studio.model;

import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public record AnalysisReport(
        AudioData audio,
        AnalysisConfig config,
        List<PitchFrame> frames,
        List<PitchOccurrence> occurrences,
        List<GlissandoEvent> glissandi,
        ZonedDateTime generatedAt
) {
    public List<PitchOccurrence> group(MelodicDirection direction, EventType type) {
        return occurrences.stream().filter(o -> o.direction() == direction && o.eventType() == type).toList();
    }

    public List<PitchOccurrence> measuredOccurrences() {
        return occurrences.stream().filter(value -> value.eventType().measurable()).toList();
    }

    public List<PitchOccurrence> shortContacts() {
        return occurrences.stream().filter(value -> !value.eventType().measurable()).toList();
    }

    public int rawTargetContactRegions() {
        int count = 0;
        boolean inside = false;
        double lower = config.targetWindowLowerAbsoluteCent();
        double upper = config.targetWindowUpperAbsoluteCent();
        for (PitchFrame frame : frames) {
            boolean near = frame.voiced() && frame.absoluteCent() >= lower && frame.absoluteCent() <= upper;
            if (near && !inside) count++;
            inside = near;
        }
        return count;
    }

    public GroupStatistics statistics(MelodicDirection direction, EventType type) {
        return statistics(value -> value.direction() == direction && value.eventType() == type);
    }

    public GroupStatistics statistics(Predicate<PitchOccurrence> selector) {
        return GroupStatistics.from(occurrences.stream().filter(value -> value.eventType().measurable())
                        .filter(selector).toList(),
                config.similarityToleranceComma() * AnalysisConfig.COMMA_CENT);
    }

    public Map<MelodicDirection, Map<EventType, GroupStatistics>> statisticsMatrix() {
        Map<MelodicDirection, Map<EventType, GroupStatistics>> result = new EnumMap<>(MelodicDirection.class);
        for (MelodicDirection direction : MelodicDirection.values()) {
            Map<EventType, GroupStatistics> row = new EnumMap<>(EventType.class);
            for (EventType type : EventType.values()) row.put(type, statistics(direction, type));
            result.put(direction, row);
        }
        return result;
    }

    public void reclassify(int row, EventType type) {
        occurrences.get(row).setEventType(type);
    }
}
