package com.makambox.studio.study;

import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

/** Regression gate for persistent, individually selectable profile settings. */
public final class FirstFourProfilePreferencesRegressionTest {
    private static final List<String> CORE = List.of(
            "Çargâh", "Kürdî", "Rast", "Uşşak", "Hicaz");
    private static final List<String> OPTIONAL = List.of(
            "Sabâ", "Segâh", "Müstear", "Hüzzam", "Nikriz", "Pençgâh",
            "Ferahnâk", "Nişabur");

    private FirstFourProfilePreferencesRegressionTest() { }

    public static void main(String[] args) throws Exception {
        Preferences node = Preferences.userRoot().node(
                "/com/makamlab/tests/first-four-" + UUID.randomUUID());
        try {
            FirstFourProfilePreferences preferences = new FirstFourProfilePreferences(node);
            check(preferences.coreDisplayNames().equals(CORE),
                    "the five required proper names must be exact and ordered");
            check(preferences.optionalDisplayNames().equals(OPTIONAL),
                    "the eight optional proper names must be exact and ordered");
            check(preferences.enabledOptionalDisplayNames().isEmpty(),
                    "new preferences must default to the five core profiles only");
            check(preferences.selectionSnapshot().includedDisplayNames().equals(CORE),
                    "the default immutable snapshot must contain only core profiles");

            preferences.setEnabledOptionalDisplayNames(List.of("Nişabur", "Segâh", "Nikriz"));
            check(preferences.enabledOptionalDisplayNames().equals(
                            List.of("Segâh", "Nikriz", "Nişabur")),
                    "enabled options must be persisted in catalogue order");

            FirstFourProfilePreferences reloaded = new FirstFourProfilePreferences(node);
            check(reloaded.enabledOptionalDisplayNames().equals(
                            List.of("Segâh", "Nikriz", "Nişabur")),
                    "optional selection must survive a new preferences facade");
            FirstFourProfileSelection frozen = reloaded.selectionSnapshot();
            check(frozen.includedDisplayNames().equals(List.of(
                            "Çargâh", "Kürdî", "Rast", "Uşşak", "Hicaz",
                            "Segâh", "Nikriz", "Nişabur")),
                    "snapshot must append selected options without translating names");

            preferences.restoreDefaults();
            check(preferences.enabledOptionalDisplayNames().isEmpty(),
                    "restore defaults must switch off every optional profile");
            check(frozen.includedDisplayNames().equals(List.of(
                            "Çargâh", "Kürdî", "Rast", "Uşşak", "Hicaz",
                            "Segâh", "Nikriz", "Nişabur")),
                    "an analysis snapshot must not change when preferences change later");

            boolean rejected = false;
            try {
                preferences.setEnabledOptionalDisplayNames(List.of("Uşşak", "Unknown"));
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, "core and unknown names must not be stored as optional choices");

            FirstFourProfileSelection migrated = FirstFourProfileSelection
                    .ofOptionalDisplayNames(List.of("Bûselik", "Segâh"));
            check(migrated.optionalDisplayNames().equals(List.of("Segâh")),
                    "a stale Bûselik comparison preference must be ignored safely");
            System.out.println("FIRST_FOUR_PROFILE_PREFERENCES_OK");
        } finally {
            node.removeNode();
            node.flush();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
