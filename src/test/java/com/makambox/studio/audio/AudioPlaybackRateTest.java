package com.makambox.studio.audio;

/** Regression checks for pitch-preserving slow playback. */
public final class AudioPlaybackRateTest {
    public static void main(String[] args) throws Exception {
        float sampleRate = 44_100f;
        float[] sine = new float[(int) (sampleRate * 2.0)];
        for (int i = 0; i < sine.length; i++) sine[i] = (float) (0.65 * Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate));
        for (double rate : new double[]{0.50, 0.75, 1.25}) {
            float[] stretched = AudioPlayback.timeStretchPitchPreserving(sine, 0, sine.length, rate, sampleRate);
            int expected = (int) Math.round(sine.length / rate);
            if (Math.abs(stretched.length - expected) > 2) throw new AssertionError("Yanlış süre: " + rate);
            double frequency = estimateFrequency(stretched, sampleRate);
            if (Math.abs(frequency - 440.0) > 12.0) {
                throw new AssertionError("Perde korunmadı: " + rate + " -> " + frequency + " Hz");
            }
        }
        System.out.println("AUDIO_PLAYBACK_RATE_OK");
    }

    private static double estimateFrequency(float[] samples, float sampleRate) {
        int center = samples.length / 2;
        int length = Math.min(16_384, samples.length / 3);
        int start = Math.max(0, center - length / 2);
        int bestLag = 1;
        double best = -Double.MAX_VALUE;
        int minimumLag = (int) (sampleRate / 470.0);
        int maximumLag = (int) (sampleRate / 410.0);
        for (int lag = minimumLag; lag <= maximumLag; lag++) {
            double correlation = 0.0;
            for (int i = start; i < start + length - lag; i++) correlation += samples[i] * samples[i + lag];
            if (correlation > best) { best = correlation; bestLag = lag; }
        }
        return sampleRate / bestLag;
    }
}
