package com.makambox.studio.ui;

import com.makambox.studio.i18n.I18n;

/** User-selectable academic analysis scope in the standalone MakamLab app. */
public enum MakamAnalysisMode {
    SINGLE_PITCH("analysis.single", "Tek perde analizi"),
    BETWEEN_PITCHES("analysis.between", "Perdeler arası analiz"),
    USSAK_SEGAH_DIRECTIONAL("analysis.directional", "Uşşak dörtlüsü – Segâh yönsel analizi"),
    HICAZ_AUGMENTED_INTERVAL("analysis.augmented", "Artık Aralık Analizi"),
    GLISSANDO_ANALYSIS("analysis.glissando", "İki perde arası Glisendo/Portamento analizi");

    private final String messageKey;
    private final String fallback;

    MakamAnalysisMode(String messageKey, String fallback) {
        this.messageKey = messageKey;
        this.fallback = fallback;
    }

    public String messageKey() { return messageKey; }

    @Override public String toString() {
        return I18n.get().textOrDefault(messageKey, fallback);
    }
}
