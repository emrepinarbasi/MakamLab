package com.makambox.studio.study;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable selection of the first-four-pitch profiles used for comparison.
 *
 * <p>The five core profiles are always present.  Callers may enable any of the
 * eight supplementary profiles without changing or reducing the complete
 * thirteen-profile theory catalogue.</p>
 */
public record FirstFourProfileSelection(List<String> optionalDisplayNames) {
    public FirstFourProfileSelection {
        Objects.requireNonNull(optionalDisplayNames, "optionalDisplayNames");
        Set<String> requested = new LinkedHashSet<>();
        for (String name : optionalDisplayNames) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Ek profil adı boş olamaz");
            }
            String normalized = name.strip();
            // Bûselik was a mandatory comparison row in releases before the
            // November 2026 revision.  Ignore a stale serialized selection
            // instead of preventing the application from opening; the raw
            // Bûselik pitch and every interval-boundary use remain unchanged.
            if (!"Bûselik".equals(normalized) && !"Buselik".equals(normalized)) {
                requested.add(normalized);
            }
        }

        List<String> unknown = requested.stream()
                .filter(name -> !FirstFourProfileReferenceCatalog.optionalDisplayNames().contains(name))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Bilinmeyen ek ilk-dört-ses profili: "
                    + String.join(", ", unknown));
        }

        // Store one canonical, catalogue-ordered copy.  This keeps equality,
        // serialization and report ordering deterministic even when a settings
        // screen supplies names in a different order.
        optionalDisplayNames = FirstFourProfileReferenceCatalog.optionalDisplayNames().stream()
                .filter(requested::contains)
                .toList();
    }

    /** The required product default: only the five fundamental profiles. */
    public static FirstFourProfileSelection defaults() {
        return new FirstFourProfileSelection(List.of());
    }

    /** Enables a caller-selected subset of the supplementary profile names. */
    public static FirstFourProfileSelection ofOptionalDisplayNames(Collection<String> names) {
        Objects.requireNonNull(names, "names");
        return new FirstFourProfileSelection(List.copyOf(names));
    }

    /** Convenience selection for audits that explicitly request all profiles. */
    public static FirstFourProfileSelection allProfiles() {
        return new FirstFourProfileSelection(
                FirstFourProfileReferenceCatalog.optionalDisplayNames());
    }

    public List<String> includedDisplayNames() {
        List<String> result = new ArrayList<>(
                FirstFourProfileReferenceCatalog.coreDisplayNames().size()
                        + optionalDisplayNames.size());
        result.addAll(FirstFourProfileReferenceCatalog.coreDisplayNames());
        result.addAll(optionalDisplayNames);
        return List.copyOf(result);
    }

    public boolean includes(String displayName) {
        return FirstFourProfileReferenceCatalog.coreDisplayNames().contains(displayName)
                || optionalDisplayNames.contains(displayName);
    }
}
