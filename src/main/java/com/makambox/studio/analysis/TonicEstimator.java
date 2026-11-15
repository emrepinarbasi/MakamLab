package com.makambox.studio.analysis;

import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.PitchFrame;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class TonicEstimator {
    public record Estimate(double hertz, double confidence) {}

    public Estimate estimate(List<PitchFrame> frames) {
        if (frames.isEmpty()) return new Estimate(220.0, 0.0);
        Estimate finalNote = estimateFromFinalStableNote(frames);
        if (finalNote != null) return finalNote;
        return estimateFromGlobalHistogram(frames);
    }

    private static Estimate estimateFromFinalStableNote(List<PitchFrame> frames) {
        int window = Math.min(45, frames.size());
        int minimumVoiced = Math.min(12, Math.max(5, window / 3));
        for (int end = frames.size() - 1; end >= window - 1; end--) {
            List<Double> values = new ArrayList<>();
            for (int i = end - window + 1; i <= end; i++) {
                if (frames.get(i).voiced()) values.add(frames.get(i).absoluteCent());
            }
            if (values.size() < minimumVoiced) continue;
            double reference = median(values.stream().mapToDouble(Double::doubleValue).toArray());
            double[] folded = values.stream().mapToDouble(value -> foldToOctave(value, reference)).toArray();
            double center = median(folded);
            double spread = robustSpread(folded, center);
            if (spread > 38.0) continue;

            double bestCent = Double.NaN;
            double bestSupport = -1.0;
            double totalSupport = 0.0;
            for (int octave = -3; octave <= 3; octave++) {
                double candidate = center + octave * 1200.0;
                double hertz = PitchTracker.centToHertz(candidate);
                if (hertz < 45.0 || hertz > 800.0) continue;
                double support = octaveSupport(frames, candidate);
                totalSupport += support;
                if (support > bestSupport) {
                    bestSupport = support;
                    bestCent = candidate;
                }
            }
            if (!Double.isFinite(bestCent)) continue;
            double stabilityScore = Math.max(0.0, 1.0 - spread / 38.0);
            double supportShare = bestSupport / Math.max(1.0, totalSupport);
            double confidence = Math.min(1.0, 0.55 * stabilityScore + 0.45 * supportShare);
            return new Estimate(PitchTracker.centToHertz(bestCent), confidence);
        }
        return null;
    }

    private static Estimate estimateFromGlobalHistogram(List<PitchFrame> frames) {
        Map<Integer, Double> bins = new HashMap<>();
        int lastVoiced = -1;
        for (int i = 0; i < frames.size(); i++) if (frames.get(i).voiced()) lastVoiced = i;

        for (int i = 0; i < frames.size(); i++) {
            PitchFrame frame = frames.get(i);
            if (!frame.voiced()) continue;
            double weight = 0.5 + frame.confidence();
            if (isStable(frames, i)) weight *= 1.8;
            if (followedBySilence(frames, i, 12)) weight *= 4.0;
            if (lastVoiced >= 0 && i >= lastVoiced - 20) weight *= 5.0;
            int bin = (int) Math.round(frame.absoluteCent() / 5.0);
            bins.merge(bin, weight, Double::sum);
        }
        if (bins.isEmpty()) return new Estimate(220.0, 0.0);
        Map.Entry<Integer, Double> best = bins.entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElseThrow();
        double total = bins.values().stream().mapToDouble(Double::doubleValue).sum();
        return new Estimate(PitchTracker.centToHertz(best.getKey() * 5.0),
                Math.min(1.0, best.getValue() / Math.max(1.0, total) * 8.0));
    }

    private static double octaveSupport(List<PitchFrame> frames, double candidateCent) {
        double support = 0.0;
        double radius = 55.0;
        for (int i = 0; i < frames.size(); i++) {
            PitchFrame frame = frames.get(i);
            if (!frame.voiced()) continue;
            double distance = Math.abs(frame.absoluteCent() - candidateCent);
            if (distance > radius) continue;
            double weight = 0.4 + frame.confidence();
            if (isStable(frames, i)) weight *= 1.5;
            support += weight * (1.0 - distance / radius);
        }
        return support;
    }

    private static double foldToOctave(double value, double reference) {
        while (value - reference > 600.0) value -= 1200.0;
        while (value - reference < -600.0) value += 1200.0;
        return value;
    }

    private static double median(double[] values) {
        double[] copy = values.clone();
        Arrays.sort(copy);
        return copy.length % 2 == 0
                ? (copy[copy.length / 2 - 1] + copy[copy.length / 2]) / 2.0
                : copy[copy.length / 2];
    }

    private static double robustSpread(double[] values, double center) {
        double[] distances = Arrays.stream(values).map(value -> Math.abs(value - center)).toArray();
        return median(distances) * 1.4826;
    }

    private static boolean isStable(List<PitchFrame> frames, int index) {
        int before = Math.max(0, index - 2);
        int after = Math.min(frames.size() - 1, index + 2);
        if (!frames.get(before).voiced() || !frames.get(after).voiced()) return false;
        return Math.abs(frames.get(after).absoluteCent() - frames.get(before).absoluteCent()) < 18.0;
    }

    private static boolean followedBySilence(List<PitchFrame> frames, int index, int needed) {
        if (index + needed >= frames.size()) return false;
        for (int i = index + 1; i <= index + needed; i++) if (frames.get(i).voiced()) return false;
        return true;
    }
}
