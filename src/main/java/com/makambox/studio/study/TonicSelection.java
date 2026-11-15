package com.makambox.studio.study;

import java.util.Objects;

/** Reproducible evidence for the tonic accepted by an academic study. */
public record TonicSelection(
        double hertz,
        Source source,
        double startSeconds,
        double endSeconds,
        double confidence,
        double robustSpreadCent,
        int voicedFrameCount,
        String channelDescription,
        boolean userConfirmed
) {
    public enum Source {
        AUTOMATIC_FINAL_NOTE("Son kararlı sesten otomatik"),
        GRAPH_REGION("Grafikten seçilen bölge"),
        MANUAL_HERTZ("Elle girilen frekans");

        private final String label;
        Source(String label) { this.label = label; }
        public String label() { return label; }
        @Override public String toString() { return label; }
    }

    public TonicSelection {
        if (!Double.isFinite(hertz) || hertz <= 0.0) {
            throw new IllegalArgumentException("Karar frekansı sonlu ve pozitif olmalıdır");
        }
        source = Objects.requireNonNull(source, "source");
        if (!Double.isFinite(startSeconds) || startSeconds < 0.0) startSeconds = 0.0;
        if (!Double.isFinite(endSeconds) || endSeconds < startSeconds) endSeconds = startSeconds;
        confidence = clamp01(confidence);
        if (!Double.isFinite(robustSpreadCent) || robustSpreadCent < 0.0) {
            robustSpreadCent = Double.NaN;
        }
        voicedFrameCount = Math.max(0, voicedFrameCount);
        channelDescription = channelDescription == null || channelDescription.isBlank()
                ? "Belirtilmedi" : channelDescription.strip();
    }

    public double durationSeconds() { return Math.max(0.0, endSeconds - startSeconds); }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
