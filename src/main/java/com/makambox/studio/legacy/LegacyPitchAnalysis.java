package com.makambox.studio.legacy;

import java.util.List;

/**
 * Result of the MakamBox v1-compatible pitch, histogram and makam analysis.
 *
 * <p>The compatibility path deliberately remains separate from the newer
 * directional-intonation and glissando pipeline so established MakamBox
 * results can still be reproduced.</p>
 */
public record LegacyPitchAnalysis(
        List<PitchPoint> pitchPoints,
        double[] histogram,
        double[] alignedHistogram,
        double[] makamTemplate,
        int minimumCent,
        int maximumCent,
        String makamName,
        String tonicNote,
        double tonicHertz,
        double tonicCent,
        double templateDistance,
        double voicedPercent) {

    public record PitchPoint(double timeSeconds, double hertz, double absoluteCent) { }
}
