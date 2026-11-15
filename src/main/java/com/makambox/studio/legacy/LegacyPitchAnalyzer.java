package com.makambox.studio.legacy;

import com.makambox.studio.model.AudioData;
import com.makambox.studio.theory.TheoryCatalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * Compatibility implementation of the pitch-analysis path shipped in
 * MakamBox v1.0. It preserves the original 40 ms non-overlapping frames,
 * YIN threshold 0.10, 1/3 Holderian-comma histogram, L1 template matching,
 * and histogram-peak tonic correction.
 *
 * <p>Derived from MakamBox v1.0 by Mirac Atıcı and the bundled TarsosDSP YIN
 * implementation. Modified for safe resource loading and the modern UI on
 * 2026-10-04. GPL-3.0-or-later.</p>
 */
public final class LegacyPitchAnalyzer {
    private static final int TEMPLATE_LENGTH = 3272;
    private static final int HISTOGRAM_LENGTH = 636;
    private static final int TEMPLATE_TONIC_INDEX = 1636;
    private static final double REFERENCE_C0 = 16.35159784;
    private static final double HISTOGRAM_COMMA_CENT = 1200.0 / 159.0;

    private final TheoryCatalog theory = TheoryCatalog.loadDefault();

    public LegacyPitchAnalysis analyze(AudioData audio, DoubleConsumer progress) {
        int frameSize = Math.max(2, (int) Math.round(audio.sampleRate() * 0.040));
        int frameCount = audio.samples().length / frameSize + 1;
        LegacyYin yin = new LegacyYin(audio.sampleRate(), frameSize, 0.10);
        float[] frame = new float[frameSize];
        List<LegacyPitchAnalysis.PitchPoint> points = new ArrayList<>(frameCount);
        int[] cents = new int[frameCount];
        int voicedCount = 0;

        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            Arrays.fill(frame, 0f);
            int start = frameIndex * frameSize;
            int available = Math.max(0, Math.min(frameSize, audio.samples().length - start));
            if (available > 0) System.arraycopy(audio.samples(), start, frame, 0, available);
            double hertz = yin.detect(frame);
            double cent = hertz > 0.0 ? hertzToCent(hertz) : Double.NaN;
            if (hertz > 0.0) {
                cents[voicedCount++] = hertzToLegacyCent((float) hertz);
            }
            points.add(new LegacyPitchAnalysis.PitchPoint(
                    start / (double) audio.sampleRate(), Math.max(0.0, hertz), cent));
            if (frameIndex % 20 == 0 || frameIndex == frameCount - 1) {
                progress.accept((frameIndex + 1.0) / frameCount * 0.35);
            }
        }

        if (voicedCount == 0) throw new IllegalArgumentException("Klasik analiz için sesli perde bulunamadı.");
        int[] sorted = Arrays.copyOf(cents, voicedCount);
        Arrays.sort(sorted);
        int medianCent = median(sorted);
        int minimumCent = medianCent - 2400;
        int maximumCent = medianCent + 2400;
        double[] histogram = createHistogram(sorted, minimumCent, maximumCent);
        progress.accept(0.42);

        Match best = findBestMatch(histogram, progress);
        return buildResult(points, histogram, minimumCent, maximumCent, best,
                voicedCount * 100.0 / frameCount, progress);
    }

    /** Repeats the original template/tonic decision with a makam selected by the user. */
    public LegacyPitchAnalysis reclassify(LegacyPitchAnalysis source, String makamName) {
        Match match = matchTemplate(source.histogram(), makamName);
        return buildResult(source.pitchPoints(), source.histogram().clone(), source.minimumCent(),
                source.maximumCent(), match, source.voicedPercent(), ignored -> { });
    }

    /** Returns the normalized and smoothed v1.0 template used by the matcher. */
    public double[] templateFor(String makamName) {
        return preparedTemplate(makamName).clone();
    }

    /** Applies the v1.0 histogram-based manual tonic correction in cent. */
    public LegacyPitchAnalysis shiftTonic(LegacyPitchAnalysis source, double deltaCent) {
        int bins = (int) Math.round(deltaCent / HISTOGRAM_COMMA_CENT);
        double[] shifted = new double[source.alignedHistogram().length];
        for (int i = 0; i < source.alignedHistogram().length; i++) {
            int target = i - bins;
            if (target >= 0 && target < shifted.length) shifted[target] = source.alignedHistogram()[i];
        }
        double tonicCent = source.tonicCent() + deltaCent;
        return new LegacyPitchAnalysis(source.pitchPoints(), source.histogram().clone(), shifted,
                source.makamTemplate().clone(), source.minimumCent(), source.maximumCent(),
                source.makamName(), source.tonicNote(), centToHertz(tonicCent), tonicCent,
                source.templateDistance(), source.voicedPercent());
    }

    private LegacyPitchAnalysis buildResult(List<LegacyPitchAnalysis.PitchPoint> points,
                                            double[] histogram,
                                            int minimumCent,
                                            int maximumCent,
                                            Match best,
                                            double voicedPercent,
                                            DoubleConsumer progress) {
        int originalTonicIndex = TEMPLATE_TONIC_INDEX - best.shift();
        int tonicIndex = nearestHistogramPeak(histogram, originalTonicIndex, 6);
        int correctedShift = 1635 - tonicIndex;
        double tonicCent = minimumCent + tonicIndex * HISTOGRAM_COMMA_CENT;
        double tonicHertz = centToHertz(tonicCent);
        double[] alignedHistogram = alignHistogram(histogram, correctedShift);
        String tonicNote = tonicNote(best.makamName());
        progress.accept(1.0);

        return new LegacyPitchAnalysis(
                List.copyOf(points), histogram, alignedHistogram, best.template(),
                minimumCent, maximumCent, TheoryCatalog.displayName(best.makamName()),
                tonicNote, tonicHertz, tonicCent, best.distance(),
                voicedPercent);
    }

    private Match findBestMatch(double[] histogram, DoubleConsumer progress) {
        Match best = null;
        List<TheoryCatalog.Makam> makams = theory.makams();
        for (int makamIndex = 0; makamIndex < makams.size(); makamIndex++) {
            String name = makams.get(makamIndex).name();
            Match candidate = matchTemplate(histogram, name);
            if (best == null || candidate.distance() < best.distance()) best = candidate;
            progress.accept(0.42 + (makamIndex + 1.0) / makams.size() * 0.55);
        }
        if (best == null) throw new IllegalStateException("Klasik makam şablonları yüklenemedi.");
        return best;
    }

    private Match matchTemplate(double[] histogram, String makamName) {
        double[] template = preparedTemplate(makamName);
        double total = Arrays.stream(template).sum();
        double[] prefix = new double[template.length + 1];
        for (int i = 0; i < template.length; i++) prefix[i + 1] = prefix[i] + template[i];
        double bestDistance = Double.POSITIVE_INFINITY;
        int bestShift = 0;
        for (int shift = 0; shift <= TEMPLATE_LENGTH - HISTOGRAM_LENGTH; shift++) {
            double distance = total - (prefix[shift + HISTOGRAM_LENGTH] - prefix[shift]);
            for (int i = 0; i < HISTOGRAM_LENGTH; i++) {
                distance += Math.abs(template[shift + i] - histogram[i]);
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                bestShift = shift;
            }
        }
        return new Match(makamName, template, bestShift, bestDistance);
    }

    private double[] preparedTemplate(String makamName) {
        double[] template = loadTemplate(makamName);
        normalizeMaximum(template);
        normalizeSum(template);
        return smooth(template, 3);
    }

    private double[] loadTemplate(String makamName) {
        String resource = "/legacy/templates/" + makamName.toLowerCase(Locale.ROOT) + ".txt";
        double[] values = new double[TEMPLATE_LENGTH];
        try (InputStream stream = LegacyPitchAnalyzer.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Eksik makam şablonu: " + resource);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                int index = 0;
                while ((line = reader.readLine()) != null && index < values.length) {
                    if (!line.isBlank()) values[index++] = Double.parseDouble(line.trim());
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Klasik makam şablonu okunamadı: " + makamName, exception);
        }
        return values;
    }

    private String tonicNote(String makamName) {
        return theory.makams().stream()
                .filter(makam -> makam.name().equalsIgnoreCase(makamName))
                .findFirst()
                .map(theory::tonicFor)
                .map(TheoryCatalog.Note::toString)
                .orElse("—");
    }

    private static double[] createHistogram(int[] sortedCents, int minimumCent, int maximumCent) {
        double[] histogram = new double[Math.round((float) ((maximumCent - minimumCent) / HISTOGRAM_COMMA_CENT))];
        for (int cent : sortedCents) {
            if (cent >= minimumCent && cent < maximumCent) {
                int bin = (int) ((cent - minimumCent) / HISTOGRAM_COMMA_CENT);
                if (bin >= 0 && bin < histogram.length) histogram[bin]++;
            }
        }
        normalizeMaximum(histogram);
        normalizeSum(histogram);
        return smooth(histogram, 3);
    }

    private static double[] alignHistogram(double[] histogram, int shift) {
        double[] aligned = new double[TEMPLATE_LENGTH];
        for (int i = 0; i < histogram.length; i++) {
            int target = shift + i;
            if (target >= 0 && target < aligned.length) aligned[target] = histogram[i];
        }
        return aligned;
    }

    private static int nearestHistogramPeak(double[] histogram, int center, int offset) {
        int start = Math.max(0, center - offset);
        int end = Math.min(histogram.length, center + offset);
        if (start >= end) return Math.max(0, Math.min(histogram.length - 1, center));
        int best = start;
        for (int i = start + 1; i < end; i++) if (histogram[i] > histogram[best]) best = i;
        return best;
    }

    private static void normalizeMaximum(double[] values) {
        double maximum = Arrays.stream(values).max().orElse(0.0);
        if (maximum <= 0.0) return;
        for (int i = 0; i < values.length; i++) values[i] /= maximum;
    }

    private static void normalizeSum(double[] values) {
        double sum = Arrays.stream(values).sum();
        if (sum <= 0.0) return;
        for (int i = 0; i < values.length; i++) values[i] /= sum;
    }

    private static double[] smooth(double[] values, int range) {
        double[] smoothed = new double[values.length];
        for (int i = range; i < values.length - range; i++) {
            for (int j = -range; j <= range; j++) smoothed[i] += values[i + j];
            smoothed[i] /= range * 2 + 1;
        }
        for (int i = 0; i < range; i++) {
            smoothed[i] = values[i];
            smoothed[smoothed.length - 1 - i] = values[values.length - 1 - i];
        }
        return smoothed;
    }

    private static int median(int[] sorted) {
        if (sorted.length % 2 == 0) return Math.round((sorted[sorted.length / 2] + sorted[sorted.length / 2 - 1]) / 2);
        return sorted[(sorted.length - 1) / 2];
    }

    private static double hertzToCent(double hertz) {
        return 1200.0 * (Math.log(hertz / REFERENCE_C0) / Math.log(2.0));
    }

    private static int hertzToLegacyCent(float hertz) {
        final float reference = 8.17579892f * 2f;
        return (int) Math.round(1200.0 * (Math.log(hertz / reference) / Math.log(2.0)));
    }

    private static double centToHertz(double cent) {
        return REFERENCE_C0 * Math.pow(2.0, cent / 1200.0);
    }

    private record Match(String makamName, double[] template, int shift, double distance) { }

    /** Direct adaptation of the YIN implementation bundled with MakamBox v1.0. */
    private static final class LegacyYin {
        private final float sampleRate;
        private final double threshold;
        private final float[] yin;

        private LegacyYin(float sampleRate, int bufferSize, double threshold) {
            this.sampleRate = sampleRate;
            this.threshold = threshold;
            this.yin = new float[bufferSize / 2];
        }

        private double detect(float[] audioBuffer) {
            Arrays.fill(yin, 0f);
            for (int tau = 1; tau < yin.length; tau++) {
                for (int index = 0; index < yin.length; index++) {
                    float delta = audioBuffer[index] - audioBuffer[index + tau];
                    yin[tau] += delta * delta;
                }
            }
            yin[0] = 1f;
            float runningSum = 0f;
            for (int tau = 1; tau < yin.length; tau++) {
                runningSum += yin[tau];
                yin[tau] *= tau / runningSum;
            }
            int tauEstimate = -1;
            for (int tau = 2; tau < yin.length; tau++) {
                if (yin[tau] < threshold) {
                    while (tau + 1 < yin.length && yin[tau + 1] < yin[tau]) tau++;
                    tauEstimate = tau;
                    break;
                }
            }
            if (tauEstimate < 0 || yin[tauEstimate] >= threshold) return -1.0;
            float betterTau = parabolicInterpolation(tauEstimate);
            return betterTau > 0.0 ? sampleRate / betterTau : -1.0;
        }

        private float parabolicInterpolation(int tau) {
            int left = tau < 1 ? tau : tau - 1;
            int right = tau + 1 < yin.length ? tau + 1 : tau;
            if (left == tau) return yin[tau] <= yin[right] ? tau : right;
            if (right == tau) return yin[tau] <= yin[left] ? tau : left;
            float y0 = yin[left], y1 = yin[tau], y2 = yin[right];
            return tau + (y2 - y0) / (2f * (2f * y1 - y2 - y0));
        }
    }
}
