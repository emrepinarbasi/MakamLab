package com.makambox.studio.study.glissando;

public enum GlissandoDirection {
    ASCENDING("Çıkıcı", 1),
    DESCENDING("İnici", -1);

    private final String label;
    private final int sign;

    GlissandoDirection(String label, int sign) {
        this.label = label;
        this.sign = sign;
    }

    public String label() {
        return label;
    }

    public int sign() {
        return sign;
    }
}
