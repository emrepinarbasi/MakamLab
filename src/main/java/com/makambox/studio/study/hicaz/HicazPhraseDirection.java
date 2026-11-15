package com.makambox.studio.study.hicaz;

/** Net order of a canonically ordered Hicaz-tetrachord phrase. */
public enum HicazPhraseDirection {
    ASCENDING("Çıkıcı"),
    DESCENDING("İnici");

    private final String label;

    HicazPhraseDirection(String label) { this.label = label; }

    public String label() { return label; }

    @Override public String toString() { return label; }
}
