package com.makambox.studio.dsp;

/**
 * Zero-phase harmonic notch filter used to obtain a second melody candidate
 * when a continuously ringing tonic string dominates a monophonic tracker.
 */
final class ResonantToneFilter {
    private ResonantToneFilter() { }

    static float[] suppress(float[] input, float sampleRate, double fundamentalHertz) {
        float[] result = input.clone();
        for (int harmonic = 1; harmonic <= 8; harmonic++) {
            double frequency = fundamentalHertz * harmonic;
            if (frequency >= sampleRate * 0.45) break;
            applyForwardBackward(result, sampleRate, frequency, 30.0);
        }
        return result;
    }

    private static void applyForwardBackward(float[] samples, float sampleRate,
                                             double frequency, double qualityFactor) {
        double omega = 2.0 * Math.PI * frequency / sampleRate;
        double cosine = Math.cos(omega);
        double alpha = Math.sin(omega) / (2.0 * qualityFactor);
        double a0 = 1.0 + alpha;
        double b0 = 1.0 / a0;
        double b1 = -2.0 * cosine / a0;
        double b2 = 1.0 / a0;
        double a1 = -2.0 * cosine / a0;
        double a2 = (1.0 - alpha) / a0;
        filter(samples, b0, b1, b2, a1, a2, false);
        filter(samples, b0, b1, b2, a1, a2, true);
    }

    private static void filter(float[] samples, double b0, double b1, double b2,
                               double a1, double a2, boolean reverse) {
        double x1 = 0.0, x2 = 0.0, y1 = 0.0, y2 = 0.0;
        int index = reverse ? samples.length - 1 : 0;
        int end = reverse ? -1 : samples.length;
        int step = reverse ? -1 : 1;
        while (index != end) {
            double x0 = samples[index];
            double y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            samples[index] = (float) Math.max(-1.0, Math.min(1.0, y0));
            x2 = x1; x1 = x0; y2 = y1; y1 = y0;
            index += step;
        }
    }
}
