package com.makambox.studio.study;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Frozen result shared by the Segâh UI, Word report and future CSV exporter. */
public record UssakSegahDirectionalStudyResult(
        String makamName,
        double tonicHertz,
        double theoreticalSegahCent,
        double theoreticalSegahHc,
        double theoreticalSegahHertz,
        List<UssakSegahEvent> events,
        Map<UssakSegahUsageClass, StudyStatistics> statisticsByUsage,
        StudyStatistics overallStatistics
) {
    public UssakSegahDirectionalStudyResult {
        events = List.copyOf(events == null ? List.of() : events);
        EnumMap<UssakSegahUsageClass, StudyStatistics> copy =
                new EnumMap<>(UssakSegahUsageClass.class);
        if (statisticsByUsage != null) copy.putAll(statisticsByUsage);
        for (UssakSegahUsageClass value : UssakSegahUsageClass.values()) {
            copy.putIfAbsent(value, StudyStatistics.from(List.of()));
        }
        statisticsByUsage = Map.copyOf(copy);
        overallStatistics = overallStatistics == null
                ? StudyStatistics.from(List.of()) : overallStatistics;
    }

    public List<UssakSegahEvent> events(UssakSegahUsageClass usageClass) {
        return events.stream().filter(value -> value.usageClass() == usageClass).toList();
    }

    public List<UssakSegahEvent> includedEvents() {
        return events.stream().filter(UssakSegahEvent::included).toList();
    }

    public StudyStatistics statistics(UssakSegahUsageClass usageClass) {
        return statisticsByUsage.get(usageClass);
    }
}
