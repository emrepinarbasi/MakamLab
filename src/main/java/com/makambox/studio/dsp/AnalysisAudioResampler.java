package com.makambox.studio.dsp;

import com.makambox.studio.model.AudioData;

import java.util.Objects;

/**
 * Builds the lightweight mono working copy used by pitch analysis.
 *
 * <p>Playback, waveform and spectrogram rendering continue to use the original
 * {@link AudioData}. The pitch tracker does not need frequencies above its
 * 1.6 kHz ceiling, so analysing 44.1/48/96 kHz input at 22.05 kHz only wastes
 * work. A sixth-order Butterworth low-pass runs as a streaming cascade before
 * area-weighted rate conversion.  This matters for academic cent/Hc work: a
 * mere pair average does not sufficiently suppress a strong component near
 * the source Nyquist frequency, which can fold into the melodic F0 range.
 * The filter never allocates a second source-sized array and the exact
 * recording duration is retained to within one target sample.</p>
 */
public final class AnalysisAudioResampler {
    public static final float MAXIMUM_ANALYSIS_SAMPLE_RATE = 22_050f;
    private static final double LOW_PASS_FRACTION_OF_TARGET_RATE = 0.36;
    private static final double[] BUTTERWORTH_SIXTH_ORDER_Q = {
            0.5176380902050415, 0.7071067811865476, 1.9318516525781366
    };

    private AnalysisAudioResampler() { }

    public static AudioData forPitchTracking(AudioData source) {
        return downsampleToMaximum(source, MAXIMUM_ANALYSIS_SAMPLE_RATE);
    }

    static AudioData downsampleToMaximum(AudioData source, float maximumSampleRate) {
        Objects.requireNonNull(source, "source");
        if (!Float.isFinite(maximumSampleRate) || maximumSampleRate <= 0f) {
            throw new IllegalArgumentException("maximumSampleRate must be finite and positive");
        }
        if (source.sampleRate() <= maximumSampleRate || source.samples().length == 0) return source;

        double ratio = source.sampleRate() / maximumSampleRate;
        int outputLength = Math.max(1, (int) Math.round(source.samples().length / ratio));
        float[] output = new float[outputLength];
        float[] input = source.samples();

        double cutoff = Math.min(maximumSampleRate * LOW_PASS_FRACTION_OF_TARGET_RATE,
                source.sampleRate() * 0.45);
        Biquad[] lowPass = new Biquad[BUTTERWORTH_SIXTH_ORDER_Q.length];
        for (int index = 0; index < lowPass.length; index++) {
            lowPass[index] = Biquad.lowPass(source.sampleRate(), cutoff,
                    BUTTERWORTH_SIXTH_ORDER_Q[index]);
        }

        // Source-centred accumulation evaluates every input sample exactly
        // once. A source sample can straddle at most two target intervals; its
        // filtered value is distributed by geometric overlap without a large
        // temporary filtered-source array.
        int outputIndex = 0;
        double targetEnd = Math.min(input.length, ratio);
        double weightedSum = 0.0;
        double accumulatedWeight = 0.0;
        final double epsilon = 1.0e-10;
        for (int sourceIndex = 0; sourceIndex < input.length && outputIndex < outputLength;
             sourceIndex++) {
            double filtered = input[sourceIndex];
            for (Biquad section : lowPass) filtered = section.process(filtered);

            double cursor = sourceIndex;
            double sourceEnd = sourceIndex + 1.0;
            while (cursor < sourceEnd - epsilon && outputIndex < outputLength) {
                double overlapEnd = Math.min(sourceEnd, targetEnd);
                double weight = Math.max(0.0, overlapEnd - cursor);
                weightedSum += filtered * weight;
                accumulatedWeight += weight;
                cursor = overlapEnd;
                if (cursor >= targetEnd - epsilon) {
                    output[outputIndex++] = accumulatedWeight > 0.0
                            ? (float) (weightedSum / accumulatedWeight) : (float) filtered;
                    weightedSum = 0.0;
                    accumulatedWeight = 0.0;
                    targetEnd = Math.min(input.length, (outputIndex + 1.0) * ratio);
                }
            }
        }
        if (outputIndex < outputLength && accumulatedWeight > 0.0) {
            output[outputIndex++] = (float) (weightedSum / accumulatedWeight);
        }
        while (outputIndex < outputLength) {
            output[outputIndex] = outputIndex == 0 ? input[input.length - 1]
                    : output[outputIndex - 1];
            outputIndex++;
        }
        return new AudioData(source.name(), source.source(), maximumSampleRate, output,
                source.channelMode(), source.sourceChannelCount());
    }

    /** Direct-form-I biquad with normalized coefficients and no sample buffer. */
    private static final class Biquad {
        private final double b0;
        private final double b1;
        private final double b2;
        private final double a1;
        private final double a2;
        private double x1;
        private double x2;
        private double y1;
        private double y2;

        private Biquad(double b0, double b1, double b2, double a1, double a2) {
            this.b0 = b0;
            this.b1 = b1;
            this.b2 = b2;
            this.a1 = a1;
            this.a2 = a2;
        }

        static Biquad lowPass(double sampleRate, double cutoff, double q) {
            double omega = 2.0 * Math.PI * cutoff / sampleRate;
            double cosine = Math.cos(omega);
            double alpha = Math.sin(omega) / (2.0 * q);
            double a0 = 1.0 + alpha;
            double b0 = (1.0 - cosine) * 0.5 / a0;
            double b1 = (1.0 - cosine) / a0;
            double b2 = b0;
            double a1 = -2.0 * cosine / a0;
            double a2 = (1.0 - alpha) / a0;
            return new Biquad(b0, b1, b2, a1, a2);
        }

        double process(double input) {
            double output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = input;
            y2 = y1;
            y1 = output;
            return output;
        }
    }
}
