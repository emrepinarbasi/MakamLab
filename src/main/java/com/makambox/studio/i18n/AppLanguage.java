package com.makambox.studio.i18n;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A language/region combination supported by the standalone MakamLab user
 * interface.
 *
 * <p>The language model is deliberately independent of makam and pitch names.
 * Names such as {@code Uşşak}, {@code Segâh} and {@code Dügâh} are scientific
 * proper names and must be passed to translated messages as literal values.
 * They are never transliterated or stripped of their diacritics here.</p>
 */
public record AppLanguage(String tag, String flag, boolean rightToLeft) {
    /**
     * MakamLab intentionally offers one Turkish and one English interface.
     * Keeping this list small also guarantees that a report can never mix a
     * partially translated third language into its prose.
     */
    private static final List<AppLanguage> SUPPORTED = List.of(
            language("en-US", "🇺🇸"),
            language("tr-TR", "🇹🇷")
    );

    public AppLanguage {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(flag, "flag");
        String canonical = Locale.forLanguageTag(tag).toLanguageTag();
        if (canonical.isBlank() || "und".equals(canonical)) {
            throw new IllegalArgumentException("Invalid BCP 47 language tag: " + tag);
        }
        tag = canonical;
    }

    private static AppLanguage language(String tag, String flag) {
        return new AppLanguage(tag, flag, false);
    }

    public Locale locale() {
        return Locale.forLanguageTag(tag);
    }

    /** A stable translation key for language-picker labels. */
    public String translationKey() {
        return "language." + tag.toLowerCase(Locale.ROOT).replace('-', '.');
    }

    /**
     * Localized display name, using the JDK CLDR data when no explicit message
     * resource is available.
     */
    public String displayName(Locale uiLocale) {
        Locale effective = uiLocale == null ? Locale.ENGLISH : uiLocale;
        if ("en-US".equals(tag)) return "tr".equals(effective.getLanguage())
                ? "İngilizce" : "English";
        if ("tr-TR".equals(tag)) return "tr".equals(effective.getLanguage())
                ? "Türkçe" : "Turkish";
        String value = locale().getDisplayName(effective);
        return value == null || value.isBlank() ? tag : value;
    }

    public String nativeDisplayName() {
        return displayName(locale());
    }

    public static List<AppLanguage> supported() {
        return SUPPORTED;
    }

    public static Optional<AppLanguage> findByTag(String tag) {
        if (tag == null || tag.isBlank()) return Optional.empty();
        String canonical = Locale.forLanguageTag(tag).toLanguageTag();
        return SUPPORTED.stream().filter(value -> value.tag.equalsIgnoreCase(canonical)).findFirst();
    }

    /**
     * Resolves an operating-system locale to one of the supported variants.
     * Unsupported locales fall back to US English.
     */
    public static AppLanguage matchSystemLocale(Locale systemLocale) {
        Locale locale = systemLocale == null ? Locale.getDefault() : systemLocale;
        Optional<AppLanguage> exact = findByTag(locale.toLanguageTag());
        if (exact.isPresent()) return exact.get();

        String language = locale.getLanguage();
        return "tr".equals(language) ? byTag("tr-TR") : byTag("en-US");
    }

    static List<AppLanguage> alphabeticallyFor(Locale uiLocale) {
        Locale effective = uiLocale == null ? Locale.ENGLISH : uiLocale;
        Collator collator = Collator.getInstance(effective);
        collator.setStrength(Collator.PRIMARY);
        List<AppLanguage> result = new ArrayList<>(SUPPORTED);
        result.sort((left, right) -> {
            int compared = collator.compare(left.displayName(effective), right.displayName(effective));
            return compared != 0 ? compared : left.tag.compareTo(right.tag);
        });
        return List.copyOf(result);
    }

    private static AppLanguage byTag(String tag) {
        return findByTag(tag).orElseThrow(() -> new IllegalStateException(
                "Canonical application language is missing: " + tag));
    }
}
