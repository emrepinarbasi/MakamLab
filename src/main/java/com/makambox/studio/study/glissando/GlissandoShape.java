package com.makambox.studio.study.glissando;

/** Describes the contour independently of its overall ascending/descending direction. */
public enum GlissandoShape {
    DIRECT("Düz"),
    WITH_REVERSAL("Geri dönüşlü"),
    MIXED("Karma");

    private final String label;

    GlissandoShape(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
