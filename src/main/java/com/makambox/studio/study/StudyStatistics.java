package com.makambox.studio.study;

import com.makambox.studio.model.AnalysisConfig;

import java.util.Arrays;
import java.util.List;

/** Descriptive statistics shared by the UI, DOCX and machine-readable exports. */
public record StudyStatistics(
        int count,
        double totalDurationSeconds,
        double meanDeviationCent,
        double medianDeviationCent,
        double durationWeightedDeviationCent,
        double standardDeviationCent,
        double medianAbsoluteDeviationCent,
        double meanEntryDeviationCent,
        double meanSettledDeviationCent,
        double meanExitDeviationCent,
        int pestCount,
        int dikCount,
        int theoreticalCenterCount,
        int withinOneHcCount,
        double withinOneHcPercent
) {
    public static StudyStatistics from(List<StudyPitchEvent> events) {
        if (events == null || events.isEmpty()) {
            return new StudyStatistics(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0, 0, 0, 0, 0.0);
        }
        int count = events.size();
        double totalDuration = events.stream().mapToDouble(StudyPitchEvent::durationSeconds).sum();
        double mean = events.stream().mapToDouble(StudyPitchEvent::deviationCent).average().orElse(0.0);
        double[] deviations = events.stream().mapToDouble(StudyPitchEvent::deviationCent).sorted().toArray();
        double median = median(deviations);
        double weighted = totalDuration > 0.0
                ? events.stream().mapToDouble(value -> value.deviationCent() * value.durationSeconds()).sum()
                / totalDuration : mean;
        double variance = events.stream().mapToDouble(value -> Math.pow(value.deviationCent() - mean, 2.0))
                .average().orElse(0.0);
        double[] absoluteFromMedian = Arrays.stream(deviations)
                .map(value -> Math.abs(value - median)).sorted().toArray();
        int pest = (int) events.stream().filter(StudyPitchEvent::pest).count();
        int dik = (int) events.stream().filter(StudyPitchEvent::dik).count();
        int center = count - pest - dik;
        int within = (int) events.stream()
                .filter(value -> Math.abs(value.deviationCent()) <= AnalysisConfig.COMMA_CENT + 1.0e-9)
                .count();
        return new StudyStatistics(count, totalDuration, mean, median, weighted,
                Math.sqrt(variance), median(absoluteFromMedian),
                events.stream().mapToDouble(StudyPitchEvent::entryDeviationCent).average().orElse(0.0),
                events.stream().mapToDouble(StudyPitchEvent::settledDeviationCent).average().orElse(0.0),
                events.stream().mapToDouble(StudyPitchEvent::exitDeviationCent).average().orElse(0.0),
                pest, dik, center, within, within * 100.0 / count);
    }

    public double meanDeviationHc() { return meanDeviationCent / AnalysisConfig.COMMA_CENT; }
    public double medianDeviationHc() { return medianDeviationCent / AnalysisConfig.COMMA_CENT; }
    public double durationWeightedDeviationHc() { return durationWeightedDeviationCent / AnalysisConfig.COMMA_CENT; }

    private static double median(double[] sorted) {
        if (sorted.length == 0) return 0.0;
        return sorted.length % 2 == 0
                ? (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0
                : sorted[sorted.length / 2];
    }
}
