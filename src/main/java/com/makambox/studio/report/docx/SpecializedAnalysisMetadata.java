package com.makambox.studio.report.docx;

import java.nio.file.Path;
import java.util.Objects;

/** Source and tonic information shared by the specialised Hicaz and glissando reports. */
public record SpecializedAnalysisMetadata(
        String sourceName,
        Path sourcePath,
        double durationSeconds,
        float sampleRate,
        String channelDescription,
        String makamName,
        String tonicNoteName,
        double tonicHertz,
        String tonicMethod,
        String applicationVersion
) {
    public SpecializedAnalysisMetadata {
        sourceName = Objects.requireNonNullElse(sourceName, "—");
        channelDescription = Objects.requireNonNullElse(channelDescription, "—");
        makamName = Objects.requireNonNullElse(makamName, "—");
        tonicNoteName = Objects.requireNonNullElse(tonicNoteName, "—");
        tonicMethod = Objects.requireNonNullElse(tonicMethod, "—");
        applicationVersion = Objects.requireNonNullElse(applicationVersion, "MakamLab V1");
        if (!Double.isFinite(durationSeconds) || durationSeconds < 0.0) durationSeconds = 0.0;
        if (!Float.isFinite(sampleRate) || sampleRate < 0.0f) sampleRate = 0.0f;
        if (!Double.isFinite(tonicHertz) || tonicHertz <= 0.0) {
            throw new IllegalArgumentException(ReportLocalization.text("report.error.positiveTonic",
                    "The tonic frequency must be positive", "Karar frekansı pozitif olmalı"));
        }
    }
}
