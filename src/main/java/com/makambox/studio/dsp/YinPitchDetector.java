package com.makambox.studio.dsp;

public final class YinPitchDetector {
    private static final double SUBHARMONIC_GUARD_CEILING_HZ = 120.0;
    public record Result(double hertz, double confidence) {}

    private final float sampleRate;
    private final double threshold;
    private final double minimumFrequency;
    private final double maximumFrequency;
    private double[] yin;
    private double[] hann;

    public YinPitchDetector(float sampleRate, int frameSize, double threshold,
                            double minimumFrequency, double maximumFrequency) {
        this.sampleRate = sampleRate;
        this.threshold = threshold;
        this.minimumFrequency = minimumFrequency;
        this.maximumFrequency = maximumFrequency;
        this.yin = new double[Math.max(2, frameSize / 2)];
        this.hann = hann(frameSize);
    }

    public Result detect(float[] buffer) {
        return detect(buffer, buffer);
    }

    /**
     * Detects pitch from the short timing window while using a longer,
     * time-centred window only to reject subharmonic aliases.  Keeping these
     * windows separate preserves glissando timing and gives the spectral guard
     * enough frequency resolution to distinguish f from 2f and 3f.
     */
    Result detect(float[] buffer, float[] spectralEvidence) {
        int half = buffer.length / 2;
        if (yin.length != half) yin = new double[half];
        yin[0] = 1.0;
        for (int tau = 1; tau < half; tau++) {
            double sum = 0.0;
            for (int i = 0; i < half; i++) {
                double delta = buffer[i] - buffer[i + tau];
                sum += delta * delta;
            }
            yin[tau] = sum;
        }

        double running = 0.0;
        for (int tau = 1; tau < half; tau++) {
            running += yin[tau];
            yin[tau] = running == 0.0 ? 1.0 : yin[tau] * tau / running;
        }

        int minTau = Math.max(2, (int) Math.floor(sampleRate / maximumFrequency));
        int maxTau = Math.min(half - 2, (int) Math.ceil(sampleRate / minimumFrequency));
        int best = -1;
        double bestValue = Double.POSITIVE_INFINITY;
        for (int tau = minTau + 1; tau < maxTau; tau++) {
            if (yin[tau] <= yin[tau - 1] && yin[tau] < yin[tau + 1] && yin[tau] < bestValue) {
                best = tau;
                bestValue = yin[tau];
            }
        }
        if (best < 0 || bestValue >= threshold) return new Result(0.0, 0.0);

        // A strong second/third harmonic or a ringing open string can make YIN prefer
        // two times the true period.  Among minima that are almost as plausible as
        // the global best, use the shortest lag.  This is the standard octave guard:
        // it keeps the fundamental candidate instead of reporting a sub-octave.
        int selected = best;
        double protectedLimit = Math.min(0.32, bestValue + 0.12);
        for (int tau = minTau + 1; tau <= best; tau++) {
            if (yin[tau] <= yin[tau - 1] && yin[tau] < yin[tau + 1]
                    && yin[tau] <= protectedLimit) {
                selected = tau;
                break;
            }
        }

        double betterTau = parabolicInterpolation(selected);
        double frequency = sampleRate / betterTau;
        double confidence = Math.max(0.0, Math.min(1.0, 1.0 - yin[selected]));
        if (!Double.isFinite(frequency) || frequency < minimumFrequency || frequency > maximumFrequency) {
            return new Result(0.0, 0.0);
        }
        frequency = correctSpectralSubharmonic(spectralEvidence, frequency);
        return new Result(frequency, confidence);
    }

    /**
     * YIN can occasionally lock to twice or three times the period when a
     * transient makes neighbouring cycles look slightly different.  In that
     * case the reported frequency is f/2 or f/3 even though practically all
     * spectral energy lies on the even, respectively third, harmonic grid.
     *
     * <p>The test below is deliberately conservative.  A candidate is folded
     * upwards only when the harmonics which are <em>exclusive</em> to the lower
     * fundamental are almost absent.  A real low note normally has energy on
     * at least one of those interleaved harmonics and is therefore retained.
     * This makes the guard useful for long decays as well as isolated frames,
     * without imposing a preferred register on arbitrary recordings.</p>
     */
    private double correctSpectralSubharmonic(float[] buffer, double frequency) {
        // Above this point the ambiguity between a weak true fundamental and a
        // bright upper partial cannot be resolved safely from one monophonic
        // frame.  Higher-register octave decisions are left to the temporal
        // track, while the problematic 50--110 Hz aliases remain covered.
        if (frequency >= SUBHARMONIC_GUARD_CEILING_HZ) return frequency;
        int maximumHarmonic = Math.min(9, (int) Math.floor((sampleRate * 0.48) / frequency));
        if (maximumHarmonic < 4) return frequency;

        double[] power = new double[maximumHarmonic + 1];
        for (int harmonic = 1; harmonic <= maximumHarmonic; harmonic++) {
            power[harmonic] = sinusoidalPower(buffer, frequency * harmonic);
        }

        // A resonating open string together with the played note can form a
        // convincing mathematical common period although there is virtually
        // no acoustic energy at that period's fundamental.  This is exactly
        // the f/2 and f/3 failure seen in long instrumental decays.  When the
        // first bin is absent and either 2f or 3f clearly dominates, prefer
        // that audible spectral fundamental.  The dominance margin prevents
        // arbitrary choices in genuinely ambiguous/polyphonic frames.
        double second = power.length > 2 && frequency * 2.0 <= maximumFrequency ? power[2] : 0.0;
        double third = power.length > 3 && frequency * 3.0 <= maximumFrequency ? power[3] : 0.0;
        double strongestUpper = Math.max(second, third);
        if (strongestUpper > 1e-14 && power[1] / strongestUpper < 0.018) {
            if (second > third * 1.35) return frequency * 2.0;
            if (third > second * 1.35) return frequency * 3.0;
        }

        // Test f/3 before f/2.  A true third-subharmonic often also has a
        // sizeable second multiple, which must not make it look like f/2.
        if (frequency * 3.0 <= maximumFrequency && maximumHarmonic >= 6
                && missingInterleavedHarmonics(power, 3, 0.055)) {
            return frequency * 3.0;
        }
        if (frequency * 2.0 <= maximumFrequency
                && missingInterleavedHarmonics(power, 2, 0.075)) {
            return frequency * 2.0;
        }
        return frequency;
    }

    private boolean missingInterleavedHarmonics(double[] power, int multiple, double maximumRatio) {
        double shared = 0.0;
        double exclusive = 0.0;
        double strongest = 0.0;
        for (int harmonic = 1; harmonic < power.length; harmonic++) {
            strongest = Math.max(strongest, power[harmonic]);
            double weight = 1.0 / Math.sqrt(harmonic);
            if (harmonic % multiple == 0) shared += power[harmonic] * weight;
            else exclusive += power[harmonic] * weight;
        }
        if (strongest <= 1e-14 || shared <= strongest * 0.80) return false;
        return exclusive / shared < maximumRatio;
    }

    private double sinusoidalPower(float[] buffer, double frequency) {
        if (hann.length != buffer.length) hann = hann(buffer.length);
        double angle = -2.0 * Math.PI * frequency / sampleRate;
        double stepReal = Math.cos(angle);
        double stepImaginary = Math.sin(angle);
        double oscillatorReal = 1.0;
        double oscillatorImaginary = 0.0;
        double sumReal = 0.0;
        double sumImaginary = 0.0;
        for (int index = 0; index < buffer.length; index++) {
            double sample = buffer[index] * hann[index];
            sumReal += sample * oscillatorReal;
            sumImaginary += sample * oscillatorImaginary;
            double nextReal = oscillatorReal * stepReal - oscillatorImaginary * stepImaginary;
            oscillatorImaginary = oscillatorReal * stepImaginary + oscillatorImaginary * stepReal;
            oscillatorReal = nextReal;
        }
        return sumReal * sumReal + sumImaginary * sumImaginary;
    }

    private static double[] hann(int size) {
        double[] result = new double[size];
        if (size <= 1) return result;
        for (int index = 0; index < size; index++) {
            result[index] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * index / (size - 1.0));
        }
        return result;
    }

    private double parabolicInterpolation(int tau) {
        if (tau <= 0 || tau >= yin.length - 1) return tau;
        double left = yin[tau - 1];
        double center = yin[tau];
        double right = yin[tau + 1];
        double denominator = 2.0 * (2.0 * center - right - left);
        return Math.abs(denominator) < 1e-12 ? tau : tau + (right - left) / denominator;
    }
}
