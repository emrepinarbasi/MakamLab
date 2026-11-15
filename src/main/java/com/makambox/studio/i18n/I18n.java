package com.makambox.studio.i18n;

/** Compact facade for Swing controls and table models. */
public final class I18n {
    private I18n() { }

    public static LocalizationService get() {
        return LocalizationService.getInstance();
    }

    public static String tr(String key, Object... arguments) {
        return get().text(key, arguments);
    }
}
