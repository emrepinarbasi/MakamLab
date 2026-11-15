package com.makambox.studio.study.hicaz;

import com.makambox.studio.model.AnalysisConfig;

import java.util.Arrays;
import java.util.List;

/** Statistics of direct 2↔3 augmented-second measurements. */
public record HicazIntervalStatistics(
        int count,
        double meanObservedIntervalCent,
        double medianObservedIntervalCent,
        double meanDeviationCent,
        double medianDeviationCent,
        double standardDeviationCent,
        double medianAbsoluteDeviationCent,
        double minimumObservedIntervalCent,
        double maximumObservedIntervalCent,
        int withinOneHcCount,
        double withinOneHcPercent
) {
    public static HicazIntervalStatistics empty() { return from(List.of()); }

    public static HicazIntervalStatistics from(
            List<HicazAugmentedIntervalMeasurement> measurements) {
        if (measurements == null || measurements.isEmpty()) {
            return new HicazIntervalStatistics(0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0, 0.0);
        }
        int count = measurements.size();
        double[] observed = measurements.stream()
                .mapToDouble(HicazAugmentedIntervalMeasurement::observedIntervalCent)
                .sorted().toArray();
        double[] deviations = measurements.stream()
                .mapToDouble(HicazAugmentedIntervalMeasurement::deviationCent)
                .sorted().toArray();
        double meanObserved = Arrays.stream(observed).average().orElse(0.0);
        double meanDeviation = Arrays.stream(deviations).average().orElse(0.0);
        double medianDeviation = median(deviations);
        double variance = Arrays.stream(deviations)
                .map(value -> Math.pow(value - meanDeviation, 2.0)).average().orElse(0.0);
        double[] absoluteFromMedian = Arrays.stream(deviations)
                .map(value -> Math.abs(value - medianDeviation)).sorted().toArray();
        int within = (int) Arrays.stream(deviations)
                .filter(value -> Math.abs(value) <= AnalysisConfig.COMMA_CENT + 1.0e-9).count();
        return new HicazIntervalStatistics(count, meanObserved, median(observed),
                meanDeviation, medianDeviation, Math.sqrt(variance),
                median(absoluteFromMedian), observed[0], observed[observed.length - 1],
                within, within * 100.0 / count);
    }

    public double meanObservedIntervalHc() {
        return meanObservedIntervalCent / AnalysisConfig.COMMA_CENT;
    }
    public double medianObservedIntervalHc() {
        return medianObservedIntervalCent / AnalysisConfig.COMMA_CENT;
    }
    public double meanDeviationHc() { return meanDeviationCent / AnalysisConfig.COMMA_CENT; }
    public double medianDeviationHc() { return medianDeviationCent / AnalysisConfig.COMMA_CENT; }

    private static double median(double[] sorted) {
        if (sorted.length == 0) return 0.0;
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2.0
                : sorted[middle];
    }
}
