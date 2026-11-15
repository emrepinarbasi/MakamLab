package com.makambox.studio.study;

import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.MelodicDirection;

import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Canonical data contract consumed by the Makam UI and every report exporter. */
public record SinglePitchStudyResult(
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
        String targetNoteKey,
        String targetNoteName,
        TonicSelection tonicSelection,
        AnalysisConfig config,
        double theoreticalTargetCent,
        double theoreticalTargetHc,
        double theoreticalTargetHertz,
        List<StudyPitchEvent> allCanonicalEvents,
        List<StudyPitchEvent> allTargetContacts,
        List<StudyPitchEvent> measurableTargetEvents,
        List<StudyPitchEvent> stableTargetEvents,
        StudyStatistics measuredStatistics,
        StudyStatistics stableStatistics,
        Map<MelodicDirection, StudyStatistics> directionStatistics,
        Map<MelodicDirection, StudyStatistics> stableDirectionStatistics,
        MakamProfileComparison profileComparison
) {
    public SinglePitchStudyResult {
        allCanonicalEvents = List.copyOf(allCanonicalEvents);
        allTargetContacts = List.copyOf(allTargetContacts);
        measurableTargetEvents = List.copyOf(measurableTargetEvents);
        stableTargetEvents = List.copyOf(stableTargetEvents);
        directionStatistics = immutableDirections(directionStatistics);
        stableDirectionStatistics = immutableDirections(stableDirectionStatistics);
    }

    private static Map<MelodicDirection, StudyStatistics> immutableDirections(
            Map<MelodicDirection, StudyStatistics> source) {
        EnumMap<MelodicDirection, StudyStatistics> copy = new EnumMap<>(MelodicDirection.class);
        copy.putAll(source);
        return Map.copyOf(copy);
    }
}
