package com.makambox.studio.study.hicaz;

import com.makambox.studio.model.AnalysisConfig;

import java.util.Arrays;
import java.util.List;

/** Descriptive pitch-deviation statistics for one degree and direction. */
public record HicazDegreeStatistics(
        int count,
        double totalDurationSeconds,
        double meanDeviationCent,
        double medianDeviationCent,
        double durationWeightedDeviationCent,
        double standardDeviationCent,
        double medianAbsoluteDeviationCent,
        double minimumDeviationCent,
        double maximumDeviationCent,
        int pestCount,
        int dikCount,
        int theoreticalCenterCount,
        int withinOneHcCount,
        double withinOneHcPercent
) {
    public static HicazDegreeStatistics empty() {
        return from(List.of());
    }

    public static HicazDegreeStatistics from(List<HicazDegreeEvent> events) {
        if (events == null || events.isEmpty()) {
            return new HicazDegreeStatistics(0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0, 0.0);
        }
        int count = events.size();
        double totalDuration = events.stream().mapToDouble(HicazDegreeEvent::durationSeconds).sum();
        double mean = events.stream().mapToDouble(HicazDegreeEvent::deviationCent)
                .average().orElse(0.0);
        double[] deviations = events.stream().mapToDouble(HicazDegreeEvent::deviationCent)
                .sorted().toArray();
        double median = median(deviations);
        double weighted = totalDuration > 0.0
                ? events.stream().mapToDouble(value -> value.deviationCent()
                * value.durationSeconds()).sum() / totalDuration : mean;
        double variance = events.stream().mapToDouble(value -> Math.pow(
                value.deviationCent() - mean, 2.0)).average().orElse(0.0);
        double[] absoluteFromMedian = Arrays.stream(deviations)
                .map(value -> Math.abs(value - median)).sorted().toArray();
        int pest = (int) events.stream().filter(HicazDegreeEvent::pest).count();
        int dik = (int) events.stream().filter(HicazDegreeEvent::dik).count();
        int center = count - pest - dik;
        int within = (int) events.stream().filter(value -> Math.abs(value.deviationCent())
                <= AnalysisConfig.COMMA_CENT + 1.0e-9).count();
        return new HicazDegreeStatistics(count, totalDuration, mean, median, weighted,
                Math.sqrt(variance), median(absoluteFromMedian), deviations[0],
                deviations[deviations.length - 1], pest, dik, center, within,
                within * 100.0 / count);
    }

    public double meanDeviationHc() { return meanDeviationCent / AnalysisConfig.COMMA_CENT; }
    public double medianDeviationHc() { return medianDeviationCent / AnalysisConfig.COMMA_CENT; }
    public double durationWeightedDeviationHc() {
        return durationWeightedDeviationCent / AnalysisConfig.COMMA_CENT;
    }
    public double minimumDeviationHc() { return minimumDeviationCent / AnalysisConfig.COMMA_CENT; }
    public double maximumDeviationHc() { return maximumDeviationCent / AnalysisConfig.COMMA_CENT; }

    private static double median(double[] sorted) {
        if (sorted.length == 0) return 0.0;
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2.0
                : sorted[middle];
    }
}
