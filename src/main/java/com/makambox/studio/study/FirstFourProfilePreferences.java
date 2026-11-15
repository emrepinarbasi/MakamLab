package com.makambox.studio.study;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Persistent user choice for supplementary first-four-pitch comparisons.
 *
 * <p>The five core profiles are deliberately not preferences: they are always
 * included.  Only stable ASCII option identifiers are persisted.  Turkish
 * makam/çeşni names remain scientific proper names and are returned exactly
 * as declared by {@link FirstFourProfileReferenceCatalog}.</p>
 */
public final class FirstFourProfilePreferences {
    static final String PREFERENCE_NODE = "firstFourProfileComparison";
    static final String ENABLED_OPTION_IDS_KEY = "enabledOptionalProfileIds";

    private static final Map<String, String> OPTIONAL_NAMES_BY_ID = optionalNamesById();
    private final Preferences preferences;

    public FirstFourProfilePreferences() {
        this(Preferences.userRoot().node("/com/makamlab/academic").node(PREFERENCE_NODE));
    }

    FirstFourProfilePreferences(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    /** The five profiles that are always included and cannot be switched off. */
    public List<String> coreDisplayNames() {
        return FirstFourProfileReferenceCatalog.coreDisplayNames();
    }

    /** The eight individually selectable supplementary profiles. */
    public List<String> optionalDisplayNames() {
        return FirstFourProfileReferenceCatalog.optionalDisplayNames();
    }

    /** Returns a defensive, catalogue-ordered snapshot of enabled options. */
    public synchronized List<String> enabledOptionalDisplayNames() {
        Set<String> enabledIds = readEnabledIds();
        return OPTIONAL_NAMES_BY_ID.entrySet().stream()
                .filter(entry -> enabledIds.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * Replaces the enabled supplementary set. Unknown names are rejected so a
     * typo can never silently alter the academic comparison inventory.
     */
    public synchronized void setEnabledOptionalDisplayNames(Collection<String> displayNames) {
        Objects.requireNonNull(displayNames, "displayNames");
        Set<String> requested = new LinkedHashSet<>(displayNames);
        List<String> unknown = requested.stream()
                .filter(name -> !OPTIONAL_NAMES_BY_ID.containsValue(name))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Bilinmeyen ek ilk-dört-ses profili: "
                    + String.join(", ", unknown));
        }

        String stored = OPTIONAL_NAMES_BY_ID.entrySet().stream()
                .filter(entry -> requested.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        preferences.put(ENABLED_OPTION_IDS_KEY, stored);
        flush();
    }

    /** Restores the product default: the five core profiles only. */
    public synchronized void restoreDefaults() {
        preferences.remove(ENABLED_OPTION_IDS_KEY);
        flush();
    }

    /** Immutable selection to freeze at the start of one analysis run. */
    public synchronized FirstFourProfileSelection selectionSnapshot() {
        return FirstFourProfileSelection.ofOptionalDisplayNames(enabledOptionalDisplayNames());
    }

    private Set<String> readEnabledIds() {
        Set<String> result = new LinkedHashSet<>();
        String stored = preferences.get(ENABLED_OPTION_IDS_KEY, "");
        for (String id : stored.split(",")) {
            String normalized = id.strip();
            if (OPTIONAL_NAMES_BY_ID.containsKey(normalized)) result.add(normalized);
        }
        return result;
    }

    private void flush() {
        try {
            preferences.flush();
        } catch (BackingStoreException error) {
            throw new IllegalStateException("İlk-dört-ses profil ayarları kaydedilemedi", error);
        }
    }

    private static Map<String, String> optionalNamesById() {
        List<String> names = FirstFourProfileReferenceCatalog.optionalDisplayNames();
        List<String> ids = List.of("saba", "segah", "mustear", "huzzam",
                "nikriz", "pencgah", "ferahnak", "nisabur");
        if (names.size() != ids.size()) {
            throw new ExceptionInInitializerError("Ek ilk-dört-ses profil sayısı değişti");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < ids.size(); index++) {
            result.put(ids.get(index), names.get(index));
        }
        return Collections.unmodifiableMap(result);
    }
}
