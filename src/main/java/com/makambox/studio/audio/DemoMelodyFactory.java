package com.makambox.studio.audio;

import com.makambox.studio.model.AudioData;

import java.util.ArrayList;
import java.util.List;

public final class DemoMelodyFactory {
    private static final float SAMPLE_RATE = 44_100f;

    private DemoMelodyFactory() {}

    public static AudioData create() {
        Builder builder = new Builder();
        double tonic = 220.0;
        double target = tonic * Math.pow(2.0, 498.113 / 1200.0);
        builder.tone(tonic, 0.55, 0.24)
                .glide(tonic, target, 0.42, 0.26)
                .tone(target * Math.pow(2, 8.0 / 1200.0), 0.22, 0.25)
                .glide(target, target * 1.22, 0.35, 0.24)
                .silence(0.32)
                .tone(target * 1.34, 0.38, 0.22)
                .glide(target * 1.34, target, 0.46, 0.24)
                .tone(target * Math.pow(2, -5.0 / 1200.0), 1.38, 0.24)
                .silence(0.42)
                .tone(tonic, 0.45, 0.23)
                .glide(tonic, target, 0.30, 0.24)
                .glide(target, target * 1.18, 0.28, 0.24)
                .silence(0.25)
                .glide(target * 1.25, target, 0.33, 0.24)
                .tone(target * Math.pow(2, 3.0 / 1200.0), 0.82, 0.24)
                .glide(target, tonic, 0.44, 0.23)
                .tone(tonic, 0.65, 0.22);
        return new AudioData("MakamBox demo melodisi", null, SAMPLE_RATE, builder.toArray());
    }

    private static final class Builder {
        private final List<Float> samples = new ArrayList<>();
        private double phase;

        Builder silence(double seconds) {
            int count = (int) Math.round(seconds * SAMPLE_RATE);
            for (int i = 0; i < count; i++) samples.add(0f);
            return this;
        }

        Builder tone(double frequency, double seconds, double amplitude) {
            return glide(frequency, frequency, seconds, amplitude);
        }

        Builder glide(double startHz, double endHz, double seconds, double amplitude) {
            int count = (int) Math.round(seconds * SAMPLE_RATE);
            for (int i = 0; i < count; i++) {
                double position = count <= 1 ? 1.0 : i / (double) (count - 1);
                double smooth = position * position * (3.0 - 2.0 * position);
                double frequency = startHz * Math.pow(endHz / startHz, smooth);
                double envelope = Math.min(1.0, Math.min(i / 300.0, (count - i) / 300.0));
                phase += 2.0 * Math.PI * frequency / SAMPLE_RATE;
                double harmonic = Math.sin(phase) + 0.22 * Math.sin(phase * 2.0) + 0.08 * Math.sin(phase * 3.0);
                samples.add((float) (amplitude * envelope * harmonic / 1.3));
            }
            return this;
        }

        float[] toArray() {
            float[] result = new float[samples.size()];
            for (int i = 0; i < result.length; i++) result[i] = samples.get(i);
            return result;
        }
    }
}
