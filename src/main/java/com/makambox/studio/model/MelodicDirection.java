package com.makambox.studio.model;

public enum MelodicDirection {
    ASCENDING("Çıkıcı"), DESCENDING("İnici"), LEVEL("Yatay/Karma");

    private final String label;
    MelodicDirection(String label) { this.label = label; }
    public String label() { return label; }
    @Override public String toString() { return label; }
}
