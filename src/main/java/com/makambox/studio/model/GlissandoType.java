package com.makambox.studio.model;

public enum GlissandoType {
    INTO_TARGET("Perdeye giriş"),
    OUT_OF_TARGET("Perdeden çıkış"),
    THROUGH_TARGET("Perdeden geçiş");

    private final String label;
    GlissandoType(String label) { this.label = label; }
    public String label() { return label; }
    @Override public String toString() { return label; }
}
