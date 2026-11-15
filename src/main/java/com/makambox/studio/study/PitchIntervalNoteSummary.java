package com.makambox.studio.study;

import java.util.List;
import java.util.Objects;

/** Per-note breakdown retained inside an interval study. */
public record PitchIntervalNoteSummary(
        String noteKey,
        String noteName,
        double theoreticalRelativeCent,
        double theoreticalRelativeHc,
        double theoreticalHertz,
        List<StudyPitchEvent> contacts,
        List<StudyPitchEvent> measurableEvents,
        List<StudyPitchEvent> stableEvents,
        StudyStatistics measuredStatistics,
        StudyStatistics stableStatistics
) {
    public PitchIntervalNoteSummary {
        noteKey = Objects.requireNonNull(noteKey, "noteKey");
        noteName = Objects.requireNonNull(noteName, "noteName");
        contacts = List.copyOf(contacts);
        measurableEvents = List.copyOf(measurableEvents);
        stableEvents = List.copyOf(stableEvents);
        measuredStatistics = Objects.requireNonNull(measuredStatistics, "measuredStatistics");
        stableStatistics = Objects.requireNonNull(stableStatistics, "stableStatistics");
    }
}
