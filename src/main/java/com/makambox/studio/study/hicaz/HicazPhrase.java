package com.makambox.studio.study.hicaz;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One maximal monotonic degree path; nested subpaths are deliberately not emitted. */
public record HicazPhrase(
        int index,
        HicazPhraseDirection direction,
        List<HicazDegreeEvent> events,
        double confidence,
        boolean included,
        String auditReason
) {
    public HicazPhrase {
        if (index < 1) throw new IllegalArgumentException("index must be positive");
        direction = Objects.requireNonNull(direction, "direction");
        events = List.copyOf(events == null ? List.of() : events);
        if (events.size() < 2) throw new IllegalArgumentException("phrase needs at least two degrees");
        int previous = events.get(0).degree().number();
        for (int position = 1; position < events.size(); position++) {
            int current = events.get(position).degree().number();
            if (direction == HicazPhraseDirection.ASCENDING ? current <= previous : current >= previous) {
                throw new IllegalArgumentException("phrase degrees must be strictly monotonic");
            }
            previous = current;
        }
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        auditReason = Objects.requireNonNullElse(auditReason, "");
    }

    public double startSeconds() { return events.get(0).startSeconds(); }
    public double endSeconds() { return events.get(events.size() - 1).endSeconds(); }
    public double durationSeconds() { return Math.max(0.0, endSeconds() - startSeconds()); }

    public List<Integer> degreeNumbers() {
        return events.stream().map(value -> value.degree().number()).toList();
    }

    public String pathLabel() {
        List<String> numbers = new ArrayList<>(events.size());
        for (HicazDegreeEvent event : events) numbers.add(Integer.toString(event.degree().number()));
        return String.join("→", numbers);
    }

    public boolean containsDirectAugmentedInterval() {
        for (int index = 1; index < events.size(); index++) {
            int before = events.get(index - 1).degree().number();
            int after = events.get(index).degree().number();
            if ((before == 2 && after == 3) || (before == 3 && after == 2)) return true;
        }
        return false;
    }
}
