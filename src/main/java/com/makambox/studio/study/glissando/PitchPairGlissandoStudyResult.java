package com.makambox.studio.study.glissando;

import java.util.List;

public record PitchPairGlissandoStudyResult(
        PitchPairGlissandoRequest request,
        List<PitchPairGlissandoEvent> events,
        PitchPairGlissandoStatistics ascendingStatistics,
        PitchPairGlissandoStatistics descendingStatistics
) {
    public PitchPairGlissandoStudyResult {
        events = List.copyOf(events);
    }

    public List<PitchPairGlissandoEvent> primaryEvents() {
        return events.stream().filter(PitchPairGlissandoEvent::primary).toList();
    }

    public List<PitchPairGlissandoEvent> auditEvents() {
        return events.stream().filter(value -> !value.primary()).toList();
    }

    public List<PitchPairGlissandoEvent> ascendingEvents() {
        return primaryEvents().stream()
                .filter(value -> value.direction() == GlissandoDirection.ASCENDING).toList();
    }

    public List<PitchPairGlissandoEvent> descendingEvents() {
        return primaryEvents().stream()
                .filter(value -> value.direction() == GlissandoDirection.DESCENDING).toList();
    }
}
