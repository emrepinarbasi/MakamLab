package com.makambox.studio.ui;

import com.makambox.studio.i18n.I18n;
import com.makambox.studio.i18n.LocalizationService;

/** Small monotonic progress/ETA formatter shared by long-running UI analyses. */
final class ProgressEta {
    private long startedNanos;

    void start() { startedNanos = System.nanoTime(); }

    String describe(int percent) {
        return describe(percent, elapsedSeconds());
    }

    double elapsedSeconds() {
        if (startedNanos == 0L) return 0.0;
        return Math.max(0.0, (System.nanoTime() - startedNanos) / 1_000_000_000.0);
    }

    static String describe(int percent, double elapsedSeconds) {
        LocalizationService i18n = I18n.get();
        int value = Math.max(0, Math.min(100, percent));
        if (value <= 0) return i18n.textOrDefault("progress.preparing", "Hazırlanıyor…");
        String percentage = i18n.formatPercent(value / 100.0, 0);
        if (value >= 100) return i18n.textOrDefault("progress.complete",
                "{0} • {1}", percentage, formatDuration(elapsedSeconds));
        double remaining = elapsedSeconds * (100.0 - value) / value;
        return i18n.textOrDefault("progress.running",
                "{0} • geçen {1} • kalan ~{2}", percentage,
                formatDuration(elapsedSeconds), formatDuration(remaining));
    }

    static String formatDuration(double seconds) {
        LocalizationService i18n = I18n.get();
        int rounded = Math.max(0, (int) Math.round(seconds));
        int hours = rounded / 3600;
        int minutes = (rounded % 3600) / 60;
        int remainder = rounded % 60;
        String minuteText = hours > 0 ? twoDigits(minutes, i18n) : i18n.formatInteger(minutes);
        String secondText = twoDigits(remainder, i18n);
        return hours > 0 ? i18n.formatInteger(hours) + ":" + minuteText + ":" + secondText
                : minuteText + ":" + secondText;
    }

    private static String twoDigits(int value, LocalizationService i18n) {
        String localized = i18n.formatInteger(value);
        return value < 10 ? i18n.formatInteger(0) + localized : localized;
    }
}
