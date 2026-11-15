package com.makambox.studio.model;

import java.util.List;

public record GroupStatistics(
        int count,
        double totalDurationSeconds,
        double meanDurationSeconds,
        double meanDeviationCent,
        double medianDeviationCent,
        double meanAbsoluteDeviationCent,
        double standardDeviationCent,
        double durationWeightedDeviationCent,
        double meanEntryDeviationCent,
        double meanSettledDeviationCent,
        double meanExitDeviationCent,
        double similarityPercent
) {
    public static GroupStatistics from(List<PitchOccurrence> occurrences, double toleranceCent) {
        if (occurrences.isEmpty()) return new GroupStatistics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        double totalDuration = occurrences.stream().mapToDouble(PitchOccurrence::durationSeconds).sum();
        double mean = occurrences.stream().mapToDouble(PitchOccurrence::deviationCent).average().orElse(0);
        double meanAbs = occurrences.stream().mapToDouble(o -> Math.abs(o.deviationCent())).average().orElse(0);
        double[] values = occurrences.stream().mapToDouble(PitchOccurrence::deviationCent).sorted().toArray();
        double median = values.length % 2 == 0
                ? (values[values.length / 2 - 1] + values[values.length / 2]) / 2.0
                : values[values.length / 2];
        double variance = occurrences.stream().mapToDouble(o -> Math.pow(o.deviationCent() - mean, 2)).average().orElse(0);
        double weighted = totalDuration > 0
                ? occurrences.stream().mapToDouble(o -> o.deviationCent() * o.durationSeconds()).sum() / totalDuration
                : mean;
        double entry = occurrences.stream().mapToDouble(PitchOccurrence::entryDeviationCent).average().orElse(0);
        double settled = occurrences.stream().mapToDouble(PitchOccurrence::settledDeviationCent).average().orElse(0);
        double exit = occurrences.stream().mapToDouble(PitchOccurrence::exitDeviationCent).average().orElse(0);
        long within = occurrences.stream().filter(o -> Math.abs(o.deviationCent()) <= toleranceCent).count();
        return new GroupStatistics(occurrences.size(), totalDuration, totalDuration / occurrences.size(), mean,
                median, meanAbs, Math.sqrt(variance), weighted, entry, settled, exit,
                within * 100.0 / occurrences.size());
    }
}
