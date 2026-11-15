package com.makambox.studio.study;

import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.MelodicDirection;

import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Canonical report contract for a two-pitch open interval (both boundaries excluded). */
public record PitchIntervalStudyResult(
        String sourceName,
        String sourcePath,
        String sourceSha256,
        double sourceDurationSeconds,
        float sourceSampleRate,
        int sourceChannelCount,
        String analyzedChannel,
        String applicationVersion,
        ZonedDateTime generatedAt,
        String makamName,
        String tonicNote,
        String lowerBoundaryKey,
        String lowerBoundaryName,
        String upperBoundaryKey,
        String upperBoundaryName,
        TonicSelection tonicSelection,
        AnalysisConfig config,
        double lowerBoundaryCent,
        double upperBoundaryCent,
        double lowerBoundaryHc,
        double upperBoundaryHc,
        double lowerBoundaryHertz,
        double upperBoundaryHertz,
        List<StudyPitchEvent> allCanonicalEvents,
        List<StudyPitchEvent> intervalContacts,
        List<StudyPitchEvent> measurableIntervalEvents,
        List<StudyPitchEvent> stableIntervalEvents,
        PitchIntervalDistribution distribution,
        StudyStatistics measuredStatistics,
        StudyStatistics stableStatistics,
        Map<MelodicDirection, StudyStatistics> directionStatistics,
        Map<MelodicDirection, StudyStatistics> stableDirectionStatistics,
        List<PitchIntervalNoteSummary> noteSummaries,
        MakamProfileComparison profileComparison
) {
    public PitchIntervalStudyResult {
        allCanonicalEvents = List.copyOf(allCanonicalEvents);
        intervalContacts = List.copyOf(intervalContacts);
        measurableIntervalEvents = List.copyOf(measurableIntervalEvents);
        stableIntervalEvents = List.copyOf(stableIntervalEvents);
        directionStatistics = immutableDirections(directionStatistics);
        stableDirectionStatistics = immutableDirections(stableDirectionStatistics);
        noteSummaries = List.copyOf(noteSummaries);
    }

    /** Events with a defensible canonical name at the configured C/Hc tolerance. */
    public List<StudyPitchEvent> namedIntervalEvents() {
        return intervalContacts.stream()
                .filter(value -> IntervalPitchNaming.isCanonicallyNamed(value, config)).toList();
    }

    /**
     * Events retained by actual F0 position but too far from the nearest theory
     * centre to receive that centre's pitch name.
     */
    public List<StudyPitchEvent> unnamedIntervalEvents() {
        return intervalContacts.stream()
                .filter(value -> !IntervalPitchNaming.isCanonicallyNamed(value, config)).toList();
    }

    private static Map<MelodicDirection, StudyStatistics> immutableDirections(
            Map<MelodicDirection, StudyStatistics> source) {
        EnumMap<MelodicDirection, StudyStatistics> copy = new EnumMap<>(MelodicDirection.class);
        copy.putAll(source);
        return Map.copyOf(copy);
    }
}
