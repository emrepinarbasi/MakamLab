package com.makambox.studio.study.glissando;

public enum GlissandoEventStatus {
    PRIMARY("Ana istatistik"),
    AUDIT("Denetim adayı");

    private final String label;

    GlissandoEventStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
