package com.makambox.studio.i18n;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Window;
import java.text.Collator;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;

/**
 * Application-wide localization state used by both Swing views and report
 * exporters.
 *
 * <p>The service owns only interface/report language.  Makam and pitch names
 * remain untouched scientific values and should be supplied as arguments to
 * {@link #text(String, Object...)}.</p>
 */
public final class LocalizationService {
    public static final String BUNDLE_BASE_NAME = "i18n.messages";
    public static final String PREFERENCE_KEY = "uiLanguageTag";
    public static final String PREFERENCE_SCHEMA_KEY = "uiLanguagePreferenceSchema";
    public static final String DISABLE_PREFERENCE_WRITES_PROPERTY =
            "makamlab.i18n.disablePreferenceWrites";
    private static final String PREFERENCE_SCHEMA_VERSION = "2";

    private static final LocalizationService INSTANCE = new LocalizationService();

    // MakamLab and MakamBox Studio share part of a source tree, but they are
    // separate products.  Keep the chosen MakamLab language in its own node so
    // installing or running Studio can never change the language of a MakamLab
    // window (or vice versa).
    private final Preferences preferences = Preferences.userRoot().node("/com/makamlab/academic");
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile AppLanguage currentLanguage;
    private volatile ResourceBundle bundle;

    private LocalizationService() {
        // Schema 2 deliberately retires the former multilingual catalogue.
        // Any saved third-language choice is discarded once, after which the
        // application follows the operating-system language (Turkish or
        // English; every other system language falls back to English).
        String preferenceSchema = preferences.get(PREFERENCE_SCHEMA_KEY, "");
        boolean migrateUnversionedPreference = !PREFERENCE_SCHEMA_VERSION.equals(
                preferenceSchema);
        if (migrateUnversionedPreference && preferenceWritesEnabled()) {
            preferences.remove(PREFERENCE_KEY);
            preferences.put(PREFERENCE_SCHEMA_KEY, PREFERENCE_SCHEMA_VERSION);
        }
        String persisted = migrateUnversionedPreference
                ? "" : preferences.get(PREFERENCE_KEY, "");
        currentLanguage = AppLanguage.findByTag(persisted)
                .orElseGet(() -> AppLanguage.matchSystemLocale(Locale.getDefault()));
        reloadBundles();
    }

    public static LocalizationService getInstance() {
        return INSTANCE;
    }

    /** Current supported language selection. */
    public AppLanguage currentLanguage() {
        return currentLanguage;
    }

    /** Locale to use for UI, report, number and date formatting. */
    public Locale locale() {
        return currentLanguage.locale();
    }

    public String languageTag() {
        return currentLanguage.tag();
    }

    public boolean isRightToLeft() {
        return currentLanguage.rightToLeft();
    }

    public ComponentOrientation componentOrientation() {
        return isRightToLeft() ? ComponentOrientation.RIGHT_TO_LEFT
                : ComponentOrientation.LEFT_TO_RIGHT;
    }

    /**
     * Returns a newly sorted immutable list.  Sorting follows the active UI
     * language, not the declaration order, so the language picker is always
     * alphabetic to its reader.
     */
    public List<AppLanguage> availableLanguages() {
        Collator collator = Collator.getInstance(locale());
        collator.setStrength(Collator.PRIMARY);
        return AppLanguage.supported().stream()
                .sorted((left, right) -> {
                    int compared = collator.compare(languageDisplayName(left),
                            languageDisplayName(right));
                    return compared != 0 ? compared : left.tag().compareTo(right.tag());
                })
                .toList();
    }

    /** Display label for the language picker. */
    public String languageDisplayName(AppLanguage language) {
        Objects.requireNonNull(language, "language");
        // Makam and pitch names are not translated; only the two interface
        // language labels are localized here.
        return language.displayName(locale());
    }

    /** Flag plus language name, ready for a non-editable Swing menu item. */
    public String languageMenuLabel(AppLanguage language) {
        return language.flag() + "  " + languageDisplayName(language);
    }

    /**
     * Changes and persists the application language.  Swing listeners are
     * notified on the event-dispatch thread, and all open windows immediately
     * receive the corresponding left-to-right/right-to-left orientation.
     */
    public synchronized void setLanguage(AppLanguage language) {
        Objects.requireNonNull(language, "language");
        if (!AppLanguage.supported().contains(language)) {
            throw new IllegalArgumentException("Unsupported application language: " + language.tag());
        }
        if (language.equals(currentLanguage)) return;
        currentLanguage = language;
        if (preferenceWritesEnabled()) {
            preferences.put(PREFERENCE_SCHEMA_KEY, PREFERENCE_SCHEMA_VERSION);
            preferences.put(PREFERENCE_KEY, language.tag());
        }
        reloadBundles();
        notifyLanguageChanged();
    }

    public void setLanguageTag(String languageTag) {
        AppLanguage language = AppLanguage.findByTag(languageTag)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported application language tag: " + languageTag));
        setLanguage(language);
    }

    /** Removes the override and immediately follows the current OS locale. */
    public synchronized void useSystemLanguage() {
        if (preferenceWritesEnabled()) {
            preferences.put(PREFERENCE_SCHEMA_KEY, PREFERENCE_SCHEMA_VERSION);
            preferences.remove(PREFERENCE_KEY);
        }
        AppLanguage language = AppLanguage.matchSystemLocale(Locale.getDefault());
        boolean changed = !language.equals(currentLanguage);
        currentLanguage = language;
        reloadBundles();
        if (changed) notifyLanguageChanged();
    }

    public void addChangeListener(Runnable listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    public void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /** Alias preferred by report code. */
    public String tr(String key, Object... arguments) {
        return text(key, arguments);
    }

    /**
     * Looks up and formats a localized message. A missing key is rendered
     * visibly; content is never borrowed from another locale.
     */
    public String text(String key, Object... arguments) {
        Objects.requireNonNull(key, "key");
        String pattern = raw(key);
        if (pattern == null) pattern = "[" + key + "]";
        if (arguments == null || arguments.length == 0) return pattern;
        return new MessageFormat(pattern, locale()).format(arguments);
    }

    /**
     * Compatibility variant for callers that still carry a development
     * fallback beside the key. Release bundles are deliberately isolated: a
     * missing translation displays its key instead of mixing languages.
     */
    public String textOrDefault(String key, String fallback, Object... arguments) {
        Objects.requireNonNull(key, "key");
        String pattern = raw(key);
        if (pattern == null) pattern = "[" + key + "]";
        if (arguments == null || arguments.length == 0) return pattern;
        return new MessageFormat(pattern, locale()).format(arguments);
    }

    public NumberFormat numberFormat(int minimumFractionDigits, int maximumFractionDigits) {
        if (minimumFractionDigits < 0 || maximumFractionDigits < minimumFractionDigits) {
            throw new IllegalArgumentException("Invalid fraction digit range");
        }
        NumberFormat format = NumberFormat.getNumberInstance(locale());
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(minimumFractionDigits);
        format.setMaximumFractionDigits(maximumFractionDigits);
        return format;
    }

    public String formatNumber(double value, int minimumFractionDigits,
                               int maximumFractionDigits) {
        return numberFormat(minimumFractionDigits, maximumFractionDigits).format(value);
    }

    /** Locale-aware numeric output that also shows an explicit plus sign. */
    public String formatSignedNumber(double value, int minimumFractionDigits,
                                     int maximumFractionDigits) {
        NumberFormat format = numberFormat(minimumFractionDigits, maximumFractionDigits);
        if (format instanceof DecimalFormat decimalFormat) decimalFormat.setPositivePrefix("+");
        return format.format(value);
    }

    public String formatInteger(long value) {
        return NumberFormat.getIntegerInstance(locale()).format(value);
    }

    /** @param fraction 0.0 means 0%, 1.0 means 100%. */
    public String formatPercent(double fraction, int maximumFractionDigits) {
        NumberFormat format = NumberFormat.getPercentInstance(locale());
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(Math.max(0, maximumFractionDigits));
        return format.format(fraction);
    }

    public DateTimeFormatter dateFormatter() {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale());
    }

    public DateTimeFormatter dateTimeFormatter() {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale());
    }

    public String formatDate(TemporalAccessor value) {
        Objects.requireNonNull(value, "value");
        return dateFormatter().format(value);
    }

    public String formatDateTime(TemporalAccessor value) {
        Objects.requireNonNull(value, "value");
        return dateTimeFormatter().format(value);
    }

    /**
     * Applies direction recursively to an existing Swing hierarchy.  Text and
     * values are not mirrored manually; Swing's component orientation keeps
     * selection coordinates and scientific numeric data intact.
     */
    public void applyOrientation(Component component) {
        if (component == null) return;
        component.applyComponentOrientation(componentOrientation());
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) applyOrientation(child);
        }
        component.invalidate();
        component.repaint();
    }

    /** Snapshot useful for diagnostics and report metadata. */
    public Map<String, String> metadata() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("languageTag", languageTag());
        values.put("displayName", languageDisplayName(currentLanguage));
        values.put("direction", isRightToLeft() ? "rtl" : "ltr");
        return Collections.unmodifiableMap(values);
    }

    private String raw(String key) {
        ResourceBundle active = bundle;
        if (active != null && active.containsKey(key)) return active.getString(key);
        return null;
    }

    private void reloadBundles() {
        bundle = loadExactBundle(currentLanguage.tag());
    }

    /** Loads only the selected locale file, without ResourceBundle parent fallback. */
    private static ResourceBundle loadExactBundle(String languageTag) {
        String resource = BUNDLE_BASE_NAME.replace('.', '/') + "_"
                + languageTag.replace('-', '_') + ".properties";
        try (InputStream stream = LocalizationService.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (stream == null) return null;
            return new PropertyResourceBundle(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return null;
        }
    }

    private void notifyLanguageChanged() {
        Runnable task = () -> {
            for (Runnable listener : listeners) {
                try {
                    listener.run();
                } catch (RuntimeException error) {
                    System.err.println("MakamLab localization listener failed: " + error.getMessage());
                }
            }
            for (Window window : Window.getWindows()) {
                if (!window.isDisplayable()) continue;
                applyOrientation(window);
                window.validate();
                window.repaint();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }

    private static boolean preferenceWritesEnabled() {
        return !Boolean.getBoolean(DISABLE_PREFERENCE_WRITES_PROPERTY);
    }
}
