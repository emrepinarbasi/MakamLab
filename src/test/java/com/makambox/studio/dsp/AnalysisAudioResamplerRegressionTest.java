package com.makambox.studio.dsp;

import com.makambox.studio.model.AudioData;

import java.nio.file.Path;
import java.util.List;

public final class AnalysisAudioResamplerRegressionTest {
    private AnalysisAudioResamplerRegressionTest() { }

    public static void main(String[] args) {
        keepsLowRateInputByIdentity();
        preservesDurationAndPitchAtFortyFourK();
        handlesNonIntegerRateRatio();
        rejectsNyquistAliasWithoutMovingMelodicF0();
        System.out.println("AnalysisAudioResamplerRegressionTest passed");
    }

    private static void keepsLowRateInputByIdentity() {
        AudioData source = tone(22_050f, 1.0, 440.0);
        assert AnalysisAudioResampler.forPitchTracking(source) == source;
    }

    private static void preservesDurationAndPitchAtFortyFourK() {
        AudioData source = tone(44_100f, 2.0, 440.0);
        AudioData result = AnalysisAudioResampler.forPitchTracking(source);
        assert result != source;
        assert result.sampleRate() == 22_050f;
        assert result.samples().length == 44_100;
        assert Math.abs(result.durationSeconds() - source.durationSeconds()) <= 1.0 / 22_050.0;

        int crossings = 0;
        for (int index = 2; index < result.samples().length - 2; index++) {
            if (result.samples()[index - 1] <= 0f && result.samples()[index] > 0f) crossings++;
        }
        assert Math.abs(crossings / result.durationSeconds() - 440.0) < 1.0;
        assert result.source().equals(source.source());
    }

    private static void handlesNonIntegerRateRatio() {
        AudioData source = tone(48_000f, 1.25, 220.0);
        AudioData result = AnalysisAudioResampler.forPitchTracking(source);
        assert result.sampleRate() == 22_050f;
        assert Math.abs(result.durationSeconds() - 1.25) <= 1.0 / 22_050.0;
        for (float sample : result.samples()) assert Float.isFinite(sample);
    }

    private static void rejectsNyquistAliasWithoutMovingMelodicF0() {
        float[] sourceRates = {44_100f, 48_000f, 96_000f};
        double[] highFrequencies = {20_800.0, 23_000.0, 42_850.0};
        for (int caseIndex = 0; caseIndex < sourceRates.length; caseIndex++) {
            AudioData source = mixedTone(sourceRates[caseIndex], 3.0,
                    220.0, highFrequencies[caseIndex]);
            AudioData result = AnalysisAudioResampler.forPitchTracking(source);
            double aliasFrequency = foldedFrequency(highFrequencies[caseIndex], result.sampleRate());
            double lowAmplitude = projectionAmplitude(result, 220.0, 0.35, 2.85);
            double aliasAmplitude = projectionAmplitude(result, aliasFrequency, 0.35, 2.85);
            assert lowAmplitude > 0.40 : "melodic tone was attenuated excessively at " + sourceRates[caseIndex];
            assert aliasAmplitude < lowAmplitude * 0.01
                    : "Nyquist component folded into analysis band at " + sourceRates[caseIndex]
                    + ": alias=" + aliasAmplitude + " low=" + lowAmplitude;

            List<com.makambox.studio.model.PitchFrame> frames =
                    new PitchTracker().track(result, ignored -> { });
            double[] hertz = frames.stream()
                    .filter(com.makambox.studio.model.PitchFrame::voiced)
                    .filter(frame -> frame.timeSeconds() >= 0.35 && frame.timeSeconds() <= 2.70)
                    .mapToDouble(com.makambox.studio.model.PitchFrame::hertz)
                    .sorted().toArray();
            assert hertz.length > 50 : "too few reliable F0 frames at " + sourceRates[caseIndex];
            double median = hertz[hertz.length / 2];
            double errorCent = 1200.0 * Math.log(median / 220.0) / Math.log(2.0);
            assert Math.abs(errorCent) < 1.0
                    : "melodic F0 moved after anti-alias conversion at " + sourceRates[caseIndex]
                    + ": " + errorCent + " C";
        }
    }

    private static AudioData tone(float sampleRate, double seconds, double frequency) {
        float[] samples = new float[(int) Math.round(sampleRate * seconds)];
        for (int index = 0; index < samples.length; index++) {
            samples[index] = (float) (0.7 * Math.sin(2.0 * Math.PI * frequency * index / sampleRate));
        }
        return new AudioData("resampler-test.wav", Path.of("resampler-test.wav"), sampleRate, samples);
    }

    private static AudioData mixedTone(float sampleRate, double seconds,
                                       double lowFrequency, double highFrequency) {
        float[] samples = new float[(int) Math.round(sampleRate * seconds)];
        for (int index = 0; index < samples.length; index++) {
            double time = index / sampleRate;
            samples[index] = (float) (0.50 * Math.sin(2.0 * Math.PI * lowFrequency * time)
                    + 0.40 * Math.sin(2.0 * Math.PI * highFrequency * time));
        }
        return new AudioData("resampler-alias-test.wav", Path.of("resampler-alias-test.wav"),
                sampleRate, samples);
    }

    private static double foldedFrequency(double frequency, double sampleRate) {
        double remainder = frequency % sampleRate;
        if (remainder < 0.0) remainder += sampleRate;
        return remainder <= sampleRate / 2.0 ? remainder : sampleRate - remainder;
    }

    private static double projectionAmplitude(AudioData audio, double frequency,
                                              double startSeconds, double endSeconds) {
        int start = Math.max(0, (int) Math.round(startSeconds * audio.sampleRate()));
        int end = Math.min(audio.samples().length,
                (int) Math.round(endSeconds * audio.sampleRate()));
        double sine = 0.0;
        double cosine = 0.0;
        for (int index = start; index < end; index++) {
            double phase = 2.0 * Math.PI * frequency * index / audio.sampleRate();
            sine += audio.samples()[index] * Math.sin(phase);
            cosine += audio.samples()[index] * Math.cos(phase);
        }
        return 2.0 * Math.hypot(sine, cosine) / Math.max(1, end - start);
    }
}
