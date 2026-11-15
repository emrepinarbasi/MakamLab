package com.makambox.studio.study;

/**
 * Mutually exclusive functional contexts used by the Uşşak-tetrachord Segâh study.
 *
 * <p>The classification is deliberately separate from the generic melodic
 * direction attached to a pitch event.  Dügâh→Segâh is sufficient evidence
 * for an ascending use and Segâh→Dügâh is sufficient evidence for a descending
 * use.  Observed Çargâh and Nevâ anchors strengthen the path label and its
 * confidence; they are not mandatory.</p>
 */
public enum UssakSegahUsageClass {
    ASCENDING_PHRASE("Çıkıcı cümle", "Dügâh→Segâh→Çargâh"),
    DESCENDING_PHRASE("İnici cümle", "Çargâh→Segâh→Dügâh"),
    DIRECT_HOLD("Doğrudan Segâh kalışı", "Segâh kalışı"),
    OTHER("Belirsiz/diğer", "Bağlam yetersiz");

    private final String label;
    private final String defaultPathLabel;

    UssakSegahUsageClass(String label, String defaultPathLabel) {
        this.label = label;
        this.defaultPathLabel = defaultPathLabel;
    }

    public String label() { return label; }
    public String defaultPathLabel() { return defaultPathLabel; }

    @Override public String toString() { return label; }
}
