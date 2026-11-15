package com.makambox.studio.study;

import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.EventType;
import com.makambox.studio.model.MelodicDirection;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchOccurrence;

import java.util.Objects;

/** Immutable, report-ready representation of one canonically named pitch event. */
public record StudyPitchEvent(
        int index,
        String noteKey,
        String noteName,
        double startSeconds,
        double endSeconds,
        double durationSeconds,
        double observedHertz,
        double theoreticalRelativeCent,
        double observedRelativeCent,
        double observedRelativeHc,
        double deviationCent,
        double deviationHc,
        double entryDeviationCent,
        double settledDeviationCent,
        double exitDeviationCent,
        double stabilityCent,
        double vibratoSpanCent,
        double confidence,
        MelodicDirection entryDirection,
        MelodicDirection exitDirection,
        MelodicDirection direction,
        EventType eventType,
        boolean phraseFinal
) {
    public StudyPitchEvent {
        noteKey = Objects.requireNonNull(noteKey, "noteKey");
        noteName = Objects.requireNonNull(noteName, "noteName");
        entryDirection = Objects.requireNonNull(entryDirection, "entryDirection");
        exitDirection = Objects.requireNonNull(exitDirection, "exitDirection");
        direction = Objects.requireNonNull(direction, "direction");
        eventType = Objects.requireNonNull(eventType, "eventType");
    }

    public static StudyPitchEvent from(PitchEvent event, double tonicAbsoluteCent) {
        Objects.requireNonNull(event, "event");
        PitchOccurrence occurrence = event.occurrence();
        double observedRelativeCent = occurrence.observedCent() - tonicAbsoluteCent;
        return new StudyPitchEvent(
                occurrence.index(), event.noteKey(), event.noteName(),
                occurrence.startSeconds(), occurrence.endSeconds(), occurrence.durationSeconds(),
                PitchTracker.centToHertz(occurrence.observedCent()),
                event.theoreticalRelativeCent(), observedRelativeCent,
                observedRelativeCent / AnalysisConfig.COMMA_CENT,
                occurrence.deviationCent(), occurrence.deviationComma(),
                occurrence.entryDeviationCent(), occurrence.settledDeviationCent(),
                occurrence.exitDeviationCent(), occurrence.stabilityCent(),
                occurrence.vibratoSpanCent(), occurrence.confidence(),
                occurrence.entryDirection(), occurrence.exitDirection(), occurrence.direction(),
                occurrence.eventType(), occurrence.phraseFinal());
    }

    public boolean measurable() { return eventType.measurable(); }
    public boolean pest() { return deviationCent < -1.0e-9; }
    public boolean dik() { return deviationCent > 1.0e-9; }
    public String intonationLabel() { return pest() ? "Pest" : dik() ? "Dik" : "Kuramsal merkez"; }
}
