package com.makambox.studio.study.glissando;

import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.PitchFrame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Detects continuous movements between two explicitly selected makam pitches. */
public final class PitchPairGlissandoAnalyzer {
    private enum Side { NONE, LOW, HIGH }

    private record Sample(double time, double absoluteCent, double relativeCent,
                          double confidence, boolean reliable) {
        boolean finite() { return Double.isFinite(absoluteCent) && Double.isFinite(relativeCent); }
    }

    public PitchPairGlissandoStudyResult analyze(List<PitchFrame> frames,
                                                  PitchPairGlissandoRequest request) {
        if (frames == null || request == null) throw new IllegalArgumentException("Perde izi ve istek gerekli");
        if (frames.size() < 3) return empty(request);
        double step = frameStep(frames);
        List<Sample> samples = samples(frames, request);
        List<PitchPairGlissandoEvent> events = detect(samples, request, step);
        List<PitchPairGlissandoEvent> numbered = new ArrayList<>(events.size());
        for (int i = 0; i < events.size(); i++) numbered.add(renumber(events.get(i), i + 1));
        return new PitchPairGlissandoStudyResult(request, numbered,
                statistics(numbered, GlissandoDirection.ASCENDING),
                statistics(numbered, GlissandoDirection.DESCENDING));
    }

    private static PitchPairGlissandoStudyResult empty(PitchPairGlissandoRequest request) {
        return new PitchPairGlissandoStudyResult(request, List.of(),
                PitchPairGlissandoStatistics.empty(GlissandoDirection.ASCENDING),
                PitchPairGlissandoStatistics.empty(GlissandoDirection.DESCENDING));
    }

    private static List<Sample> samples(List<PitchFrame> frames, PitchPairGlissandoRequest request) {
        double[] raw = new double[frames.size()];
        Arrays.fill(raw, Double.NaN);
        for (int i = 0; i < frames.size(); i++) {
            PitchFrame frame = frames.get(i);
            if (frame.voiced() && frame.confidence() >= request.auditConfidence()) {
                raw[i] = frame.absoluteCent();
            } else if (frame.candidateVoiced() && frame.confidence() >= request.auditConfidence()) {
                raw[i] = frame.candidateAbsoluteCent();
            }
        }
        double tonicCent = PitchTracker.hertzToCent(request.tonicHertz());
        double center = (request.lowerRelativeCent() + request.upperRelativeCent()) / 2.0;
        List<Sample> result = new ArrayList<>(frames.size());
        for (int i = 0; i < frames.size(); i++) {
            double smoothed = localMedian(raw, i, 1);
            double relative = Double.isFinite(smoothed)
                    ? normalizeRegister(smoothed - tonicCent, center) : Double.NaN;
            PitchFrame frame = frames.get(i);
            boolean reliable = frame.voiced() && frame.confidence() >= request.reliableConfidence();
            result.add(new Sample(frame.timeSeconds(), smoothed, relative,
                    frame.confidence(), reliable));
        }
        return result;
    }

    private static List<PitchPairGlissandoEvent> detect(List<Sample> samples,
                                                         PitchPairGlissandoRequest request,
                                                         double step) {
        List<PitchPairGlissandoEvent> result = new ArrayList<>();
        Side anchor = Side.NONE;
        int anchorLast = -1;
        double lastFiniteTime = Double.NaN;
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            if (!sample.finite()) {
                if (Double.isFinite(lastFiniteTime)
                        && sample.time() - lastFiniteTime > request.maximumGapSeconds() + step) {
                    anchor = Side.NONE;
                    anchorLast = -1;
                }
                continue;
            }
            lastFiniteTime = sample.time();
            if (anchorLast >= 0
                    && sample.time() - samples.get(anchorLast).time() > request.maximumDurationSeconds()) {
                anchor = Side.NONE;
                anchorLast = -1;
            }
            Side side = classify(sample.relativeCent(), request);
            if (side == Side.NONE) continue;
            if (anchor == Side.NONE) {
                anchor = side;
                anchorLast = i;
                continue;
            }
            if (side == anchor) {
                anchorLast = i;
                continue;
            }
            GlissandoDirection direction = anchor == Side.LOW
                    ? GlissandoDirection.ASCENDING : GlissandoDirection.DESCENDING;
            PitchPairGlissandoEvent event = measure(samples, anchorLast, i, direction, request, step);
            if (event != null) result.add(event);
            anchor = side;
            anchorLast = i;
        }
        return result;
    }

    private static Side classify(double relativeCent, PitchPairGlissandoRequest request) {
        double tolerance = request.endpointToleranceHc() * AnalysisConfig.COMMA_CENT;
        if (request.scopeMode() == GlissandoScopeMode.OPEN_INTERVAL) {
            double inset = Math.max(AnalysisConfig.COMMA_CENT, tolerance);
            double lowGate = request.lowerRelativeCent() + inset;
            double highGate = request.upperRelativeCent() - inset;
            if (lowGate >= highGate) {
                double midpoint = (request.lowerRelativeCent() + request.upperRelativeCent()) / 2.0;
                return relativeCent <= midpoint ? Side.LOW : Side.HIGH;
            }
            if (relativeCent <= lowGate) return Side.LOW;
            if (relativeCent >= highGate) return Side.HIGH;
            return Side.NONE;
        }
        double lowDistance = Math.abs(relativeCent - request.lowerRelativeCent());
        double highDistance = Math.abs(relativeCent - request.upperRelativeCent());
        if (lowDistance <= tolerance && lowDistance <= highDistance) return Side.LOW;
        if (highDistance <= tolerance) return Side.HIGH;
        return Side.NONE;
    }

    private static PitchPairGlissandoEvent measure(List<Sample> samples, int start, int end,
                                                     GlissandoDirection direction,
                                                     PitchPairGlissandoRequest request,
                                                     double step) {
        if (end <= start) return null;
        double duration = samples.get(end).time() - samples.get(start).time();
        if (!(duration > 0.0)) return null;

        double startAbsolute = rangeMedian(samples, Math.max(0, start - 1), Math.min(end, start + 1), true);
        double endAbsolute = rangeMedian(samples, Math.max(start, end - 1), Math.min(samples.size() - 1, end + 1), true);
        if (!Double.isFinite(startAbsolute) || !Double.isFinite(endAbsolute)) return null;
        double tonicCent = PitchTracker.hertzToCent(request.tonicHertz());
        double center = (request.lowerRelativeCent() + request.upperRelativeCent()) / 2.0;
        double startRelative = normalizeRegister(startAbsolute - tonicCent, center);
        double endRelative = normalizeRegister(endAbsolute - tonicCent, center);
        double span = Math.abs(endRelative - startRelative);

        int finite = 0;
        int reliable = 0;
        int movingSteps = 0;
        int longestGapFrames = 0;
        int gapFrames = 0;
        double forward = 0.0;
        double reverse = 0.0;
        double maximumStep = 0.0;
        List<Double> confidenceValues = new ArrayList<>();
        Double previous = null;
        for (int i = start; i <= end; i++) {
            Sample sample = samples.get(i);
            if (!sample.finite()) {
                gapFrames++;
                longestGapFrames = Math.max(longestGapFrames, gapFrames);
                continue;
            }
            gapFrames = 0;
            finite++;
            if (sample.reliable()) reliable++;
            confidenceValues.add(sample.confidence());
            if (previous != null) {
                double delta = sample.relativeCent() - previous;
                maximumStep = Math.max(maximumStep, Math.abs(delta));
                if (Math.abs(delta) >= 0.10 * AnalysisConfig.COMMA_CENT) movingSteps++;
                double directed = delta * direction.sign();
                if (directed >= 0.0) forward += directed;
                else reverse += -directed;
            }
            previous = sample.relativeCent();
        }
        if (finite < 2) return null;
        int expected = Math.max(1, (int) Math.round(duration / step) + 1);
        double voicedCoverage = Math.min(1.0, finite / (double) expected);
        double reliableCoverage = Math.min(1.0, reliable / (double) expected);
        double totalMovement = forward + reverse;
        double monotonicity = totalMovement > 0.0 ? Math.min(1.0, span / totalMovement) : 0.0;
        double reverseHc = reverse / AnalysisConfig.COMMA_CENT;
        GlissandoShape shape = reverseHc <= request.maximumDirectReverseHc()
                ? GlissandoShape.DIRECT
                : monotonicity >= request.minimumMonotonicity()
                ? GlissandoShape.WITH_REVERSAL : GlissandoShape.MIXED;

        double originTarget = direction == GlissandoDirection.ASCENDING
                ? request.lowerRelativeCent() : request.upperRelativeCent();
        double destinationTarget = direction == GlissandoDirection.ASCENDING
                ? request.upperRelativeCent() : request.lowerRelativeCent();
        boolean originSettled = settledContact(samples, start, originTarget, request, step);
        boolean destinationSettled = settledContact(samples, end, destinationTarget, request, step);
        boolean intermediateHold = intermediateHold(samples, start, end, request, step);

        List<String> reasons = new ArrayList<>();
        if (duration < request.minimumDurationSeconds()) reasons.add("Süre kısa");
        if (duration > request.maximumDurationSeconds()) reasons.add("Süre uzun");
        if (span / AnalysisConfig.COMMA_CENT < request.minimumSpanHc()) reasons.add("Genişlik yetersiz");
        if (monotonicity < request.minimumMonotonicity()) reasons.add("Tek yönlülük düşük");
        if (movingSteps < 3) reasons.add("Sürekli hareket kanıtı yetersiz");
        if (maximumStep > request.maximumFrameStepCent()) reasons.add("Ani perde sıçraması");
        if (longestGapFrames * step > request.maximumGapSeconds() + 1.0e-9) reasons.add("F0 boşluğu uzun");
        if (reliableCoverage < request.minimumReliableCoverage()) reasons.add("Güvenilir F0 kapsamı düşük");
        if (intermediateHold) reasons.add("Arada kararlı duruş var");
        if (request.scopeMode() == GlissandoScopeMode.ENDPOINT_CONTACT_REQUIRED) {
            if (!originSettled) reasons.add("Başlangıç perdesinde kararlı temas yok");
            if (!destinationSettled) reasons.add("Bitiş perdesinde kararlı temas yok");
        }

        boolean primary = reasons.isEmpty();
        boolean auditCandidate = duration >= request.minimumDurationSeconds() * 0.70
                && span / AnalysisConfig.COMMA_CENT >= request.minimumSpanHc() * 0.65
                && voicedCoverage >= 0.55 && movingSteps >= 2;
        if (!primary && !auditCandidate) return null;

        double medianConfidence = median(confidenceValues);
        double endpointScore = (originSettled ? 0.5 : 0.0) + (destinationSettled ? 0.5 : 0.0);
        if (request.scopeMode() == GlissandoScopeMode.OPEN_INTERVAL) endpointScore = Math.max(0.65, endpointScore);
        double confidence = clamp(0.35 * medianConfidence + 0.25 * reliableCoverage
                + 0.25 * monotonicity + 0.15 * endpointScore);

        double theoreticalSpan = request.theoreticalSpanCent();
        double startDeviation = startRelative - originTarget;
        double endDeviation = endRelative - destinationTarget;
        double signedSpeed = direction.sign() * span / duration;
        return new PitchPairGlissandoEvent(0, direction, shape,
                primary ? GlissandoEventStatus.PRIMARY : GlissandoEventStatus.AUDIT,
                primary ? "" : String.join("; ", reasons),
                samples.get(start).time(), samples.get(end).time(),
                PitchTracker.centToHertz(startAbsolute), PitchTracker.centToHertz(endAbsolute),
                startRelative, endRelative,
                startRelative / AnalysisConfig.COMMA_CENT,
                endRelative / AnalysisConfig.COMMA_CENT,
                startDeviation, endDeviation,
                startDeviation / AnalysisConfig.COMMA_CENT,
                endDeviation / AnalysisConfig.COMMA_CENT,
                span, span / AnalysisConfig.COMMA_CENT,
                theoreticalSpan, request.theoreticalSpanHc(),
                span - theoreticalSpan,
                (span - theoreticalSpan) / AnalysisConfig.COMMA_CENT,
                signedSpeed, signedSpeed / AnalysisConfig.COMMA_CENT,
                reverse, reverseHc, monotonicity, voicedCoverage, confidence,
                originSettled, destinationSettled);
    }

    private static boolean settledContact(List<Sample> samples, int center, double target,
                                           PitchPairGlissandoRequest request, double step) {
        double tolerance = request.endpointToleranceHc() * AnalysisConfig.COMMA_CENT;
        int radius = Math.max(2, (int) Math.ceil(Math.max(0.20,
                request.minimumEndpointContactSeconds() * 2.0) / step));
        int first = Math.max(0, center - radius);
        int last = Math.min(samples.size() - 1, center + radius);
        int current = 0;
        int longest = 0;
        for (int i = first; i <= last; i++) {
            Sample sample = samples.get(i);
            if (sample.reliable() && sample.finite()
                    && Math.abs(sample.relativeCent() - target) <= tolerance) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        return longest * step + 1.0e-9 >= request.minimumEndpointContactSeconds();
    }

    private static boolean intermediateHold(List<Sample> samples, int start, int end,
                                             PitchPairGlissandoRequest request, double step) {
        if (request.intermediateHoldSeconds() <= 0.0 || end - start < 3) return false;
        double margin = Math.max(AnalysisConfig.COMMA_CENT,
                request.endpointToleranceHc() * AnalysisConfig.COMMA_CENT);
        int current = 0;
        int needed = Math.max(1, (int) Math.ceil(request.intermediateHoldSeconds() / step));
        Double previous = null;
        for (int i = start; i <= end; i++) {
            Sample sample = samples.get(i);
            boolean interior = sample.finite()
                    && sample.relativeCent() > request.lowerRelativeCent() + margin
                    && sample.relativeCent() < request.upperRelativeCent() - margin;
            boolean stable = interior && previous != null
                    && Math.abs(sample.relativeCent() - previous) < 0.10 * AnalysisConfig.COMMA_CENT;
            current = stable ? current + 1 : 0;
            if (current >= needed) return true;
            previous = sample.finite() ? sample.relativeCent() : null;
        }
        return false;
    }

    private static PitchPairGlissandoEvent renumber(PitchPairGlissandoEvent value, int index) {
        return new PitchPairGlissandoEvent(index, value.direction(), value.shape(), value.status(),
                value.auditReason(), value.startSeconds(), value.endSeconds(),
                value.startHertz(), value.endHertz(), value.startRelativeCent(), value.endRelativeCent(),
                value.startRelativeHc(), value.endRelativeHc(),
                value.startEndpointDeviationCent(), value.endEndpointDeviationCent(),
                value.startEndpointDeviationHc(), value.endEndpointDeviationHc(),
                value.observedSpanCent(), value.observedSpanHc(),
                value.theoreticalSpanCent(), value.theoreticalSpanHc(),
                value.intervalErrorCent(), value.intervalErrorHc(),
                value.signedSpeedCentPerSecond(), value.signedSpeedHcPerSecond(),
                value.reverseMovementCent(), value.reverseMovementHc(), value.monotonicity(),
                value.voicedCoverage(), value.confidence(),
                value.originEndpointSettled(), value.destinationEndpointSettled());
    }

    private static PitchPairGlissandoStatistics statistics(List<PitchPairGlissandoEvent> events,
                                                            GlissandoDirection direction) {
        List<PitchPairGlissandoEvent> values = events.stream()
                .filter(PitchPairGlissandoEvent::primary)
                .filter(value -> value.direction() == direction).toList();
        if (values.isEmpty()) return PitchPairGlissandoStatistics.empty(direction);
        return new PitchPairGlissandoStatistics(direction, values.size(),
                mean(values, PitchPairGlissandoEvent::durationSeconds),
                median(values, PitchPairGlissandoEvent::durationSeconds),
                mean(values, PitchPairGlissandoEvent::startRelativeCent),
                median(values, PitchPairGlissandoEvent::startRelativeCent),
                mean(values, PitchPairGlissandoEvent::endRelativeCent),
                median(values, PitchPairGlissandoEvent::endRelativeCent),
                mean(values, PitchPairGlissandoEvent::observedSpanCent),
                median(values, PitchPairGlissandoEvent::observedSpanCent),
                mean(values, PitchPairGlissandoEvent::intervalErrorCent),
                median(values, PitchPairGlissandoEvent::intervalErrorCent),
                mean(values, PitchPairGlissandoEvent::signedSpeedCentPerSecond),
                median(values, PitchPairGlissandoEvent::signedSpeedCentPerSecond),
                mean(values, PitchPairGlissandoEvent::reverseMovementCent),
                mean(values, PitchPairGlissandoEvent::monotonicity),
                mean(values, PitchPairGlissandoEvent::confidence));
    }

    private static double mean(List<PitchPairGlissandoEvent> values,
                               ToDoubleFunction<PitchPairGlissandoEvent> mapper) {
        return values.stream().mapToDouble(mapper).average().orElse(Double.NaN);
    }

    private static double median(List<PitchPairGlissandoEvent> values,
                                 ToDoubleFunction<PitchPairGlissandoEvent> mapper) {
        return median(values.stream().mapToDouble(mapper).boxed().toList());
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) return Double.NaN;
        double[] copy = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return copy.length % 2 == 0
                ? (copy[copy.length / 2 - 1] + copy[copy.length / 2]) / 2.0
                : copy[copy.length / 2];
    }

    private static double rangeMedian(List<Sample> samples, int first, int last, boolean absolute) {
        double[] values = new double[last - first + 1];
        int count = 0;
        for (int i = first; i <= last; i++) {
            Sample sample = samples.get(i);
            double value = absolute ? sample.absoluteCent() : sample.relativeCent();
            if (Double.isFinite(value)) values[count++] = value;
        }
        if (count == 0) return Double.NaN;
        Arrays.sort(values, 0, count);
        return count % 2 == 0 ? (values[count / 2 - 1] + values[count / 2]) / 2.0
                : values[count / 2];
    }

    private static double localMedian(double[] values, int center, int radius) {
        double[] copy = new double[radius * 2 + 1];
        int count = 0;
        for (int i = Math.max(0, center - radius); i <= Math.min(values.length - 1, center + radius); i++) {
            if (Double.isFinite(values[i])) copy[count++] = values[i];
        }
        if (count == 0) return Double.NaN;
        Arrays.sort(copy, 0, count);
        return count % 2 == 0 ? (copy[count / 2 - 1] + copy[count / 2]) / 2.0 : copy[count / 2];
    }

    private static double normalizeRegister(double relativeCent, double center) {
        while (relativeCent - center > 600.0) relativeCent -= 1200.0;
        while (relativeCent - center < -600.0) relativeCent += 1200.0;
        return relativeCent;
    }

    private static double frameStep(List<PitchFrame> frames) {
        double[] differences = new double[Math.max(0, frames.size() - 1)];
        int count = 0;
        for (int i = 1; i < frames.size(); i++) {
            double difference = frames.get(i).timeSeconds() - frames.get(i - 1).timeSeconds();
            if (difference > 0.0 && difference <= 0.25) differences[count++] = difference;
        }
        if (count == 0) return 0.02;
        Arrays.sort(differences, 0, count);
        return differences[count / 2];
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
