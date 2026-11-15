package com.makambox.studio.analysis;

import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.EventType;
import com.makambox.studio.model.MelodicDirection;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.model.PitchOccurrence;

import java.util.Arrays;
import java.util.List;

/** Shared measurement and classification semantics for every kind of pitch occurrence. */
final class PitchOccurrenceSemantics {
    private PitchOccurrenceSemantics() { }

    static PitchOccurrence create(int index, List<PitchFrame> frames, int start, int end,
                                  double expected, AnalysisConfig config) {
        if (start < 0 || end < start || end >= frames.size()) {
            throw new IllegalArgumentException("Invalid pitch occurrence frame range");
        }
        double frameStep = frameStep(frames);
        double startSeconds = frames.get(start).timeSeconds();
        double endSeconds = frames.get(end).timeSeconds() + frameStep;
        double[] cents = voicedCents(frames, start, end);
        if (cents.length == 0) throw new IllegalArgumentException("Pitch occurrence has no voiced frame");

        double observed = medianCentral(cents, 0.20, 0.80);
        double stability = robustSpread(cents);
        double entry = medianCentral(cents, 0.0, 0.25) - expected;
        double settled = medianCentral(cents, 0.25, 0.75) - expected;
        double exit = medianCentral(cents, 0.75, 1.0) - expected;
        double vibrato = percentile(cents, 0.90) - percentile(cents, 0.10);
        boolean phraseFinal = hasFollowingSilence(frames, end,
                config.phraseSilenceSeconds(), frameStep);
        DirectionContext directions = classifyDirections(frames, start, end, expected, config, phraseFinal);
        boolean directionChange = directionChangeAround(frames, start, end, expected, config);
        EventType type = classifyType(endSeconds - startSeconds, stability, phraseFinal,
                directionChange, config);
        double averageConfidence = averageConfidence(frames, start, end);
        double stabilityScore = Math.max(0.0, 1.0 - stability /
                Math.max(1.0, config.maximumStableSpreadComma() * AnalysisConfig.COMMA_CENT * 2.0));
        double confidence = Math.min(1.0, 0.65 * averageConfidence + 0.35 * stabilityScore);
        return new PitchOccurrence(index, startSeconds, endSeconds, observed, expected,
                stability, entry, settled, exit, vibrato, phraseFinal, confidence,
                directions.entry(), directions.exit(), directions.primary(), type);
    }

    static double frameStep(List<PitchFrame> frames) {
        return frames.size() > 1
                ? Math.max(0.001, frames.get(1).timeSeconds() - frames.get(0).timeSeconds())
                : 0.02;
    }

    private static EventType classifyType(double duration, double stability, boolean phraseFinal,
                                          boolean directionChange, AnalysisConfig config) {
        if (duration < config.minimumOccurrenceSeconds()) return EventType.SHORT_CONTACT;
        boolean stable = stability <= config.maximumStableSpreadComma() * AnalysisConfig.COMMA_CENT;
        if (duration >= config.holdMinimumSeconds() && stable && (phraseFinal || directionChange)) {
            return EventType.CADENTIAL_HOLD;
        }
        if (duration > config.passingMaximumSeconds() || (duration >= config.holdMinimumSeconds() && stable)) {
            return EventType.SUSTAINED;
        }
        return EventType.PASSING;
    }

    private static DirectionContext classifyDirections(List<PitchFrame> frames, int start, int end,
                                                        double expected, AnalysisConfig config,
                                                        boolean phraseFinal) {
        double step = frameStep(frames);
        int window = Math.max(4, (int) Math.round(0.55 / step));
        double before = contextualMedian(frames, Math.max(0, start - window), start - 1);
        double after = contextualMedian(frames, end + 1, Math.min(frames.size() - 1, end + window));
        double threshold = config.directionThresholdComma() * AnalysisConfig.COMMA_CENT;
        MelodicDirection entry = Double.isFinite(before)
                ? directionOf(expected - before, threshold) : MelodicDirection.LEVEL;
        MelodicDirection exit = Double.isFinite(after)
                ? directionOf(after - expected, threshold) : MelodicDirection.LEVEL;
        MelodicDirection primary;
        if (phraseFinal && Double.isFinite(before)) primary = entry;
        else if (entry == exit && entry != MelodicDirection.LEVEL) primary = entry;
        else if (entry == MelodicDirection.LEVEL) primary = exit;
        else if (exit == MelodicDirection.LEVEL) primary = entry;
        else primary = MelodicDirection.LEVEL;
        return new DirectionContext(entry, exit, primary);
    }

    private static MelodicDirection directionOf(double delta, double threshold) {
        return delta > threshold ? MelodicDirection.ASCENDING
                : delta < -threshold ? MelodicDirection.DESCENDING : MelodicDirection.LEVEL;
    }

    private static boolean directionChangeAround(List<PitchFrame> frames, int start, int end,
                                                  double expected, AnalysisConfig config) {
        double step = frameStep(frames);
        int window = Math.max(4, (int) Math.round(0.45 / step));
        double before = contextualMedian(frames, Math.max(0, start - window), start - 1);
        double after = contextualMedian(frames, end + 1, Math.min(frames.size() - 1, end + window));
        double threshold = config.directionThresholdComma() * AnalysisConfig.COMMA_CENT;
        if (!Double.isFinite(before) || !Double.isFinite(after)) return false;
        double in = expected - before;
        double out = after - expected;
        return Math.abs(in) > threshold && Math.abs(out) > threshold && Math.signum(in) != Math.signum(out);
    }

    private static boolean hasFollowingSilence(List<PitchFrame> frames, int end,
                                               double seconds, double step) {
        int needed = Math.max(1, (int) Math.ceil(seconds / step));
        if (end + needed >= frames.size()) return true;
        for (int i = end + 1; i <= end + needed; i++) if (frames.get(i).voiced()) return false;
        return true;
    }

    private static double contextualMedian(List<PitchFrame> frames, int start, int end) {
        if (start > end || start >= frames.size() || end < 0) return Double.NaN;
        return median(frames.subList(Math.max(0, start), Math.min(frames.size() - 1, end) + 1).stream()
                .filter(PitchFrame::voiced).mapToDouble(PitchFrame::absoluteCent).toArray());
    }

    private static double[] voicedCents(List<PitchFrame> frames, int start, int end) {
        return frames.subList(start, end + 1).stream().filter(PitchFrame::voiced)
                .mapToDouble(PitchFrame::absoluteCent).toArray();
    }

    private static double medianCentral(double[] input, double from, double to) {
        if (input.length == 0) return Double.NaN;
        int start = Math.max(0, Math.min(input.length - 1, (int) Math.floor(input.length * from)));
        int end = Math.max(start + 1, Math.min(input.length, (int) Math.ceil(input.length * to)));
        return median(Arrays.copyOfRange(input, start, end));
    }

    private static double median(double[] input) {
        if (input.length == 0) return Double.NaN;
        double[] copy = input.clone();
        Arrays.sort(copy);
        return copy.length % 2 == 0
                ? (copy[copy.length / 2 - 1] + copy[copy.length / 2]) / 2.0
                : copy[copy.length / 2];
    }

    private static double robustSpread(double[] input) {
        double center = median(input);
        double[] distances = Arrays.stream(input).map(value -> Math.abs(value - center)).toArray();
        return median(distances) * 1.4826;
    }

    private static double percentile(double[] input, double percentile) {
        if (input.length == 0) return Double.NaN;
        double[] copy = input.clone();
        Arrays.sort(copy);
        double index = percentile * (copy.length - 1);
        int lower = (int) Math.floor(index);
        int upper = Math.min(copy.length - 1, lower + 1);
        return copy[lower] + (copy[upper] - copy[lower]) * (index - lower);
    }

    private static double averageConfidence(List<PitchFrame> frames, int start, int end) {
        return frames.subList(start, end + 1).stream().filter(PitchFrame::voiced)
                .mapToDouble(PitchFrame::confidence).average().orElse(0.0);
    }

    private record DirectionContext(MelodicDirection entry, MelodicDirection exit,
                                    MelodicDirection primary) { }
}
