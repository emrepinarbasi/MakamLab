package com.makambox.studio.analysis;

import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.AnalysisReport;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.GlissandoEvent;
import com.makambox.studio.model.GlissandoType;
import com.makambox.studio.model.MelodicDirection;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.model.PitchOccurrence;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class DirectionalIntonationAnalyzer {
    public AnalysisReport analyze(AudioData audio, List<PitchFrame> frames, AnalysisConfig config) {
        double expected = config.expectedAbsoluteCent();
        List<PitchOccurrence> occurrences = detectOccurrences(frames, config, expected);
        List<GlissandoEvent> glissandi = detectGlissandi(frames, config, expected);
        return new AnalysisReport(audio, config, List.copyOf(frames), occurrences, glissandi, ZonedDateTime.now());
    }

    private List<PitchOccurrence> detectOccurrences(List<PitchFrame> frames,
                                                    AnalysisConfig config, double expected) {
        List<PitchOccurrence> result = new ArrayList<>();
        if (frames.isEmpty()) return result;
        double lowerBound = config.targetWindowLowerAbsoluteCent();
        double upperBound = config.targetWindowUpperAbsoluteCent();
        double frameStep = frameStep(frames);
        int allowedGap = Math.max(1, (int) Math.round(0.060 / frameStep));
        int i = 0;

        while (i < frames.size()) {
            if (!near(frames.get(i), lowerBound, upperBound)) { i++; continue; }
            int start = i;
            int lastNear = i;
            int gaps = 0;
            i++;
            while (i < frames.size()) {
                if (near(frames.get(i), lowerBound, upperBound)) {
                    lastNear = i;
                    gaps = 0;
                } else if (gaps < allowedGap) {
                    gaps++;
                } else {
                    break;
                }
                i++;
            }
            int end = lastNear;
            result.add(PitchOccurrenceSemantics.create(result.size() + 1,
                    frames, start, end, expected, config));
        }
        return result;
    }

    private List<GlissandoEvent> detectGlissandi(List<PitchFrame> frames,
                                                  AnalysisConfig config, double expected) {
        List<GlissandoEvent> candidates = new ArrayList<>();
        if (frames.size() < 4) return candidates;
        double step = frameStep(frames);
        double[] contour = medianSmoothedContour(frames);
        double minimumSpan = config.minimumGlissandoSpanComma() * AnalysisConfig.COMMA_CENT;
        double directionEpsilon = 0.20;
        int i = 0;
        while (i < frames.size() - 2) {
            if (!frames.get(i).voiced()) { i++; continue; }
            int lookAhead = Math.max(4, (int) Math.round(0.100 / step));
            int firstDirection = nextTrendDirection(frames, contour, i,
                    Math.min(frames.size() - 1, i + lookAhead),
                    config.maximumGlissandoFrameStepCent(), minimumSpan);
            if (firstDirection == 0) { i++; continue; }
            int start = i;
            int end = i;
            int oppositeBudget = 2;
            int stationaryCount = 0;
            int stationaryBudget = Math.max(2, (int) Math.round(0.100 / step));
            int movingSteps = 0;
            double largestStep = 0.0;
            for (int j = i; j < frames.size() - 1; j++) {
                if (!frames.get(j).voiced() || !frames.get(j + 1).voiced()) break;
                double rawDelta = frames.get(j + 1).absoluteCent() - frames.get(j).absoluteCent();
                if (Math.abs(rawDelta) > config.maximumGlissandoFrameStepCent()) break;
                double delta = contour[j + 1] - contour[j];
                if (!Double.isFinite(delta) || Math.abs(delta) > config.maximumGlissandoFrameStepCent()) break;
                int direction = Math.abs(delta) < directionEpsilon ? 0 : (delta > 0 ? 1 : -1);
                if (direction == 0) {
                    stationaryCount++;
                    if (stationaryCount > stationaryBudget) {
                        end = Math.max(start, j - stationaryCount + 1);
                        break;
                    }
                } else {
                    stationaryCount = 0;
                    movingSteps++;
                    largestStep = Math.max(largestStep, Math.abs(delta));
                }
                if (direction != 0 && direction != firstDirection) {
                    if (oppositeBudget-- <= 0) break;
                }
                end = j + 1;
                if (end - start > Math.round(1.5 / step)) break;
            }
            if (end <= start) { i++; continue; }
            double duration = frames.get(end).timeSeconds() - frames.get(start).timeSeconds() + step;
            int sampleCount = end - start + 1;
            int endpointFrames = Math.max(1, Math.min((int) Math.round(0.040 / step), sampleCount / 3));
            double first = rangeMedian(contour, start, Math.min(end, start + endpointFrames - 1));
            double last = rangeMedian(contour, Math.max(start, end - endpointFrames + 1), end);
            double span = Math.abs(last - first);
            double movement = totalMovement(contour, start, end, directionEpsilon);
            double monotonicity = movement > 0 ? Math.min(1.0, span / movement) : 0.0;
            double residual = trajectoryResidual(contour, start, end, first, last);
            double targetTolerance = config.glissandoTargetToleranceComma() * AnalysisConfig.COMMA_CENT;
            int minimumTargetFrames = Math.max(1,
                    (int) Math.ceil(config.minimumGlissandoTargetContactSeconds() / step));
            boolean touchesTarget = targetContactCount(frames, start, end, expected, targetTolerance)
                    >= minimumTargetFrames;
            boolean distributedMotion = movingSteps >= Math.min(3, Math.max(1, end - start))
                    && largestStep <= Math.max(10.0, span * 0.72);
            boolean oscillatory = isOscillatory(contour, start, end, step, minimumSpan,
                    directionEpsilon);

            if (duration >= config.minimumGlissandoSeconds()
                    && span >= minimumSpan
                    && span <= config.maximumAutomaticGlissandoSpanCent()
                    && monotonicity >= config.minimumGlissandoMonotonicity()
                    && residual <= config.maximumGlissandoTrajectoryResidualCent()
                    && distributedMotion
                    && !oscillatory
                    && touchesTarget) {
                int crossing = nearestIndex(frames, start, end, expected);
                boolean beginsNear = Math.abs(first - expected) <= targetTolerance;
                boolean endsNear = Math.abs(last - expected) <= targetTolerance;
                GlissandoType type = endsNear && !beginsNear ? GlissandoType.INTO_TARGET
                        : beginsNear && !endsNear ? GlissandoType.OUT_OF_TARGET
                        : GlissandoType.THROUGH_TARGET;
                double residualScore = Math.max(0.0, 1.0
                        - residual / Math.max(1.0, config.maximumGlissandoTrajectoryResidualCent()));
                double continuityScore = Math.max(0.0, 1.0 - largestStep / Math.max(1.0, span));
                double confidence = Math.min(1.0, 0.45 * monotonicity
                        + 0.30 * averageConfidence(frames, start, end)
                        + 0.15 * residualScore + 0.10 * continuityScore);
                candidates.add(new GlissandoEvent(candidates.size() + 1, type,
                        last > first ? MelodicDirection.ASCENDING : MelodicDirection.DESCENDING,
                        frames.get(start).timeSeconds(), frames.get(end).timeSeconds() + step,
                        frames.get(crossing).timeSeconds(), first, last, expected,
                        monotonicity, confidence));
            }
            i = Math.max(i + 1, end);
        }
        return deduplicateGlissandi(candidates);
    }

    private static List<GlissandoEvent> deduplicateGlissandi(List<GlissandoEvent> candidates) {
        candidates.sort(Comparator.comparingDouble(GlissandoEvent::startSeconds));
        List<GlissandoEvent> result = new ArrayList<>();
        for (GlissandoEvent event : candidates) {
            if (!result.isEmpty()) {
                GlissandoEvent previous = result.get(result.size() - 1);
                double overlap = Math.min(previous.endSeconds(), event.endSeconds())
                        - Math.max(previous.startSeconds(), event.startSeconds());
                if (overlap > Math.min(previous.durationSeconds(), event.durationSeconds()) * 0.6
                        && previous.direction() == event.direction()) {
                    if (event.confidence() > previous.confidence()) result.set(result.size() - 1, renumber(event, result.size()));
                    continue;
                }
            }
            result.add(renumber(event, result.size()));
        }
        return result;
    }

    private static GlissandoEvent renumber(GlissandoEvent event, int zeroBased) {
        return new GlissandoEvent(zeroBased + 1, event.type(), event.direction(), event.startSeconds(),
                event.endSeconds(), event.crossingSeconds(), event.startCent(), event.endCent(),
                event.targetCent(), event.monotonicity(), event.confidence());
    }

    private static double median(double[] input) {
        if (input.length == 0) return Double.NaN;
        double[] copy = input.clone();
        Arrays.sort(copy);
        return copy.length % 2 == 0 ? (copy[copy.length / 2 - 1] + copy[copy.length / 2]) / 2.0
                : copy[copy.length / 2];
    }

    private static double averageConfidence(List<PitchFrame> frames, int start, int end) {
        return frames.subList(start, end + 1).stream().filter(PitchFrame::voiced)
                .mapToDouble(PitchFrame::confidence).average().orElse(0.0);
    }

    private static boolean near(PitchFrame frame, double lowerBound, double upperBound) {
        return frame.voiced() && frame.absoluteCent() >= lowerBound && frame.absoluteCent() <= upperBound;
    }

    private static double frameStep(List<PitchFrame> frames) {
        return frames.size() > 1 ? Math.max(0.001, frames.get(1).timeSeconds() - frames.get(0).timeSeconds()) : 0.02;
    }

    private static int nextTrendDirection(List<PitchFrame> frames, double[] contour,
                                          int start, int end, double maximumStepCent,
                                          double minimumSpanCent) {
        double seedDisplacement = Math.max(0.8, Math.min(2.0, minimumSpanCent * 0.10));
        for (int i = start + 1; i <= end; i++) {
            if (!frames.get(i - 1).voiced() || !frames.get(i).voiced()) continue;
            double rawStep = frames.get(i).absoluteCent() - frames.get(i - 1).absoluteCent();
            if (Math.abs(rawStep) > maximumStepCent) return 0;
            double displacement = contour[i] - contour[start];
            if (Math.abs(displacement) >= seedDisplacement) return displacement > 0 ? 1 : -1;
        }
        return 0;
    }

    private static double totalMovement(double[] contour, int start, int end, double epsilon) {
        double sum = 0.0;
        for (int i = start; i < end; i++) {
            if (!Double.isFinite(contour[i]) || !Double.isFinite(contour[i + 1])) continue;
            double delta = Math.abs(contour[i + 1] - contour[i]);
            if (delta >= epsilon) sum += delta;
        }
        return sum;
    }

    private static int targetContactCount(List<PitchFrame> frames, int start, int end,
                                          double target, double tolerance) {
        int count = 0;
        for (int i = start; i <= end; i++) {
            if (frames.get(i).voiced() && Math.abs(frames.get(i).absoluteCent() - target) <= tolerance) count++;
        }
        return count;
    }

    private static double rangeMedian(double[] contour, int start, int end) {
        double[] values = new double[end - start + 1];
        int count = 0;
        for (int i = start; i <= end; i++) {
            if (Double.isFinite(contour[i])) values[count++] = contour[i];
        }
        return median(Arrays.copyOf(values, count));
    }

    private static double trajectoryResidual(double[] contour, int start, int end,
                                             double first, double last) {
        if (end <= start) return Double.POSITIVE_INFINITY;
        double[] residuals = new double[end - start + 1];
        int count = 0;
        for (int i = start; i <= end; i++) {
            if (!Double.isFinite(contour[i])) continue;
            double position = (i - start) / (double) (end - start);
            double expected = first + (last - first) * position;
            residuals[count++] = Math.abs(contour[i] - expected);
        }
        return count == 0 ? Double.POSITIVE_INFINITY : median(Arrays.copyOf(residuals, count));
    }

    private static double[] medianSmoothedContour(List<PitchFrame> frames) {
        double[] result = new double[frames.size()];
        Arrays.fill(result, Double.NaN);
        for (int i = 0; i < frames.size(); i++) {
            if (!frames.get(i).voiced()) continue;
            double[] neighborhood = new double[3];
            int count = 0;
            for (int j = Math.max(0, i - 1); j <= Math.min(frames.size() - 1, i + 1); j++) {
                if (frames.get(j).voiced()) neighborhood[count++] = frames.get(j).absoluteCent();
            }
            // At a voiced-run boundary, averaging the only two available samples would
            // artificially shorten a genuinely brief glide. Keep the measured endpoint;
            // use the median only where a complete three-frame neighborhood exists.
            result[i] = count < 3 ? frames.get(i).absoluteCent()
                    : median(Arrays.copyOf(neighborhood, count));
        }
        return result;
    }

    /**
     * Rejects repeated, locally returning motion (vibrato) without rejecting a glide that merely
     * carries a small vibrato component.  The decision uses a wider temporal context than the
     * candidate itself: two substantial reversals plus little net progress identify oscillation.
     */
    private static boolean isOscillatory(double[] contour, int start, int end, double step,
                                         double minimumSpan, double epsilon) {
        int padding = Math.max(2, (int) Math.round(0.180 / step));
        int from = Math.max(0, start - padding);
        int to = Math.min(contour.length - 1, end + padding);
        double reversalThreshold = Math.max(8.0, minimumSpan * 0.35);
        int reversals = substantialReversals(contour, from, to, reversalThreshold);
        if (reversals < 2) return false;

        int first = firstFinite(contour, from, to);
        int last = lastFinite(contour, from, to);
        if (first < 0 || last <= first) return false;
        double contextMovement = totalMovement(contour, first, last, epsilon);
        double contextNet = Math.abs(contour[last] - contour[first]);
        return contextMovement > 0.0 && contextNet / contextMovement < 0.35;
    }

    private static int substantialReversals(double[] contour, int start, int end, double threshold) {
        int first = firstFinite(contour, start, end);
        if (first < 0) return 0;
        double pivot = contour[first];
        double extreme = pivot;
        int trend = 0;
        int reversals = 0;
        for (int i = first + 1; i <= end; i++) {
            double value = contour[i];
            if (!Double.isFinite(value)) continue;
            if (trend == 0) {
                double delta = value - pivot;
                if (Math.abs(delta) >= threshold) {
                    trend = delta > 0 ? 1 : -1;
                    extreme = value;
                }
            } else if (trend > 0) {
                if (value > extreme) extreme = value;
                else if (extreme - value >= threshold) {
                    reversals++;
                    trend = -1;
                    extreme = value;
                }
            } else {
                if (value < extreme) extreme = value;
                else if (value - extreme >= threshold) {
                    reversals++;
                    trend = 1;
                    extreme = value;
                }
            }
        }
        return reversals;
    }

    private static int firstFinite(double[] values, int start, int end) {
        for (int i = start; i <= end; i++) if (Double.isFinite(values[i])) return i;
        return -1;
    }

    private static int lastFinite(double[] values, int start, int end) {
        for (int i = end; i >= start; i--) if (Double.isFinite(values[i])) return i;
        return -1;
    }

    private static int nearestIndex(List<PitchFrame> frames, int start, int end, double target) {
        int selected = start;
        double best = Double.POSITIVE_INFINITY;
        for (int i = start; i <= end; i++) {
            double distance = Math.abs(frames.get(i).absoluteCent() - target);
            if (distance < best) { best = distance; selected = i; }
        }
        return selected;
    }

}
