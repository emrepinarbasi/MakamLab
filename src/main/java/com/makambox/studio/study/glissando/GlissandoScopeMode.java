package com.makambox.studio.study.glissando;

/** Defines whether both selected pitches must be settled or merely bound the search area. */
public enum GlissandoScopeMode {
    ENDPOINT_CONTACT_REQUIRED("Uç temasları zorunlu"),
    OPEN_INTERVAL("Seçili açık aralık");

    private final String label;

    GlissandoScopeMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
