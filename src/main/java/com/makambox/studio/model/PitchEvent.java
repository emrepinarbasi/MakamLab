package com.makambox.studio.model;

import java.util.Objects;

/**
 * A pitch occurrence identified against a stable makam-theory note.
 *
 * <p>The key is the machine-readable identity from {@code TheoryCatalog}; the
 * name is only its localized display label. Keeping both prevents UI labels
 * from becoming the identity of an event.</p>
 */
public record PitchEvent(
        String noteKey,
        String noteName,
        double theoreticalRelativeCent,
        PitchOccurrence occurrence
) {
    public PitchEvent {
        noteKey = Objects.requireNonNull(noteKey, "noteKey");
        noteName = Objects.requireNonNull(noteName, "noteName");
        occurrence = Objects.requireNonNull(occurrence, "occurrence");
        if (noteKey.isBlank()) throw new IllegalArgumentException("noteKey must not be blank");
        if (noteName.isBlank()) throw new IllegalArgumentException("noteName must not be blank");
        if (!Double.isFinite(theoreticalRelativeCent)) {
            throw new IllegalArgumentException("theoreticalRelativeCent must be finite");
        }
    }
}
