package com.makambox.studio.study;

import com.makambox.studio.model.AnalysisConfig;

import java.util.Arrays;
import java.util.List;

/** Observed F0 distribution inside a selected theoretical pitch interval. */
public record PitchIntervalDistribution(
        int count,
        double totalDurationSeconds,
        double minimumObservedRelativeCent,
        double maximumObservedRelativeCent,
        double meanObservedRelativeCent,
        double medianObservedRelativeCent,
        double durationWeightedObservedRelativeCent
) {
    public static PitchIntervalDistribution from(List<StudyPitchEvent> events) {
        if (events == null || events.isEmpty()) {
            return new PitchIntervalDistribution(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        double[] values = events.stream().mapToDouble(StudyPitchEvent::observedRelativeCent)
                .sorted().toArray();
        double duration = events.stream().mapToDouble(StudyPitchEvent::durationSeconds).sum();
        double weighted = duration > 0.0
                ? events.stream().mapToDouble(value -> value.observedRelativeCent()
                * value.durationSeconds()).sum() / duration
                : Arrays.stream(values).average().orElse(0.0);
        return new PitchIntervalDistribution(events.size(), duration, values[0],
                values[values.length - 1], Arrays.stream(values).average().orElse(0.0),
                median(values), weighted);
    }

    public double minimumObservedRelativeHc() {
        return minimumObservedRelativeCent / AnalysisConfig.COMMA_CENT;
    }

    public double maximumObservedRelativeHc() {
        return maximumObservedRelativeCent / AnalysisConfig.COMMA_CENT;
    }

    public double meanObservedRelativeHc() {
        return meanObservedRelativeCent / AnalysisConfig.COMMA_CENT;
    }

    public double medianObservedRelativeHc() {
        return medianObservedRelativeCent / AnalysisConfig.COMMA_CENT;
    }

    public double durationWeightedObservedRelativeHc() {
        return durationWeightedObservedRelativeCent / AnalysisConfig.COMMA_CENT;
    }

    private static double median(double[] sorted) {
        return sorted.length % 2 == 0
                ? (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0
                : sorted[sorted.length / 2];
    }
}
