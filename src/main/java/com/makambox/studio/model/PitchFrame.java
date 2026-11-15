package com.makambox.studio.model;

public record PitchFrame(double timeSeconds, double hertz, double absoluteCent,
                         double confidence, double rms,
                         double candidateHertz, double candidateAbsoluteCent) {
    /**
     * Compatibility constructor for reliable/manual frames.  Existing analysis
     * and test code intentionally sees the same value in the reliable and raw
     * evidence layers.
     */
    public PitchFrame(double timeSeconds, double hertz, double absoluteCent,
                      double confidence, double rms) {
        this(timeSeconds, hertz, absoluteCent, confidence, rms, hertz, absoluteCent);
    }

    public boolean voiced() { return hertz > 0.0 && Double.isFinite(absoluteCent); }

    /** A weaker F0 candidate retained only for visual audit, never for reports. */
    public boolean candidateVoiced() {
        return candidateHertz > 0.0 && Double.isFinite(candidateAbsoluteCent);
    }
}
