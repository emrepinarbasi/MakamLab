package com.makambox.studio.dsp;

import com.makambox.studio.analysis.TonicEstimator;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.PitchFrame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleConsumer;

public final class PitchTracker {
    private static final double REFERENCE_C0 = 16.351597831287414;
    /** Minimum confidence used by academic reports and canonical note events. */
    public static final double RELIABLE_CONFIDENCE = 0.65;
    /** Minimum confidence retained only as a pale raw-F0 audit trace. */
    public static final double VISUAL_CANDIDATE_CONFIDENCE = 0.35;
    private static final double TONIC_NEAR_CENT = 48.0;
    private static final double PERSISTENCE_WINDOW_SECONDS = 1.0;
    private static final double SPECTRAL_EVIDENCE_SECONDS = 0.120;
    private static final int MAXIMUM_TEMPORAL_ALIAS_FRAMES = 5;
    private static final double TEMPORAL_ALIAS_MATCH_CENT = 110.0;
    private static final double COMMON_PERIOD_ALIAS_CEILING_HZ = 90.0;
    private static final double MAXIMUM_COMMON_PERIOD_ALIAS_SECONDS = 0.36;
    private static final double COMMON_PERIOD_CONTEXT_SECONDS = 0.36;
    private static final double COMMON_PERIOD_MATCH_CENT = 110.0;
    private static final double MAXIMUM_COMMON_PERIOD_SPREAD_CENT = 85.0;

    public List<PitchFrame> track(AudioData audio, DoubleConsumer progress) {
        List<PitchFrame> raw = trackSamples(audio, audio.samples(),
                value -> progress.accept(value * 0.48));
        TonicEstimator.Estimate preliminary = new TonicEstimator().estimate(raw);
        if (preliminary.confidence() < 0.25 || !hasPersistentTonic(raw, preliminary.hertz())) {
            progress.accept(1.0);
            return raw;
        }

        float[] filteredSamples = ResonantToneFilter.suppress(audio.samples(), audio.sampleRate(), preliminary.hertz());
        List<PitchFrame> filtered = trackSamples(audio, filteredSamples,
                value -> progress.accept(0.48 + value * 0.50));
        progress.accept(0.99);
        List<PitchFrame> merged = mergeMelodyCandidates(raw, filtered, preliminary.hertz());
        progress.accept(1.0);
        return suppressShortHarmonicAliases(medianSmooth(merged, 2));
    }

    private List<PitchFrame> trackSamples(AudioData audio, float[] samples, DoubleConsumer progress) {
        int frameSize = Math.max(512, (int) Math.round(audio.sampleRate() * 0.040));
        int hopSize = Math.max(256, (int) Math.round(audio.sampleRate() * 0.020));
        YinPitchDetector detector = new YinPitchDetector(audio.sampleRate(), frameSize, 0.20, 45.0, 1600.0);
        int frameCount = Math.max(1, 1 + Math.max(0, samples.length - frameSize) / hopSize);
        List<PitchFrame> raw = new ArrayList<>(frameCount);
        float[] frame = new float[frameSize];
        int evidenceSize = Math.max(frameSize,
                (int) Math.round(audio.sampleRate() * SPECTRAL_EVIDENCE_SECONDS));
        float[] spectralEvidence = new float[evidenceSize];

        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            int start = frameIndex * hopSize;
            Arrays.fill(frame, 0f);
            int available = Math.min(frameSize, samples.length - start);
            if (available > 0) System.arraycopy(samples, start, frame, 0, available);
            removeDc(frame);
            double rms = rms(frame);
            YinPitchDetector.Result result;
            if (rms < 0.0035) {
                result = new YinPitchDetector.Result(0.0, 0.0);
            } else {
                fillCentredEvidence(samples, start, frameSize, spectralEvidence);
                removeDc(spectralEvidence);
                result = detector.detect(frame, spectralEvidence);
            }
            double candidateHertz = result.confidence() >= VISUAL_CANDIDATE_CONFIDENCE
                    ? result.hertz() : 0.0;
            double candidateCent = candidateHertz > 0 ? hertzToCent(candidateHertz) : Double.NaN;
            double hertz = result.confidence() >= RELIABLE_CONFIDENCE ? result.hertz() : 0.0;
            double cent = hertz > 0 ? hertzToCent(hertz) : Double.NaN;
            raw.add(new PitchFrame(start / (double) audio.sampleRate(), hertz, cent,
                    result.confidence(), rms, candidateHertz, candidateCent));
            if (frameIndex % 20 == 0 || frameIndex == frameCount - 1) {
                progress.accept((frameIndex + 1.0) / frameCount);
            }
        }
        return suppressShortHarmonicAliases(medianSmooth(raw, 2));
    }

    private static void fillCentredEvidence(float[] samples, int shortStart, int shortSize,
                                            float[] evidence) {
        Arrays.fill(evidence, 0f);
        int requestedStart = shortStart + shortSize / 2 - evidence.length / 2;
        int sourceStart = Math.max(0, requestedStart);
        int destinationStart = Math.max(0, -requestedStart);
        int available = Math.min(samples.length - sourceStart, evidence.length - destinationStart);
        if (available > 0) System.arraycopy(samples, sourceStart, evidence, destinationStart, available);
    }

    /**
     * A played tonic is not automatically a drone.  The resonant-tone notch is
     * destructive, so it is enabled only when tonic energy is distributed
     * through most of the recording rather than concentrated in a few played
     * tonic notes.
     */
    static boolean hasPersistentTonic(List<PitchFrame> frames, double tonicHertz) {
        double tonicCent = hertzToCent(tonicHertz);
        int firstVoiced = -1;
        int lastVoiced = -1;
        for (int i = 0; i < frames.size(); i++) {
            if (!frames.get(i).voiced()) continue;
            if (firstVoiced < 0) firstVoiced = i;
            lastVoiced = i;
        }
        if (firstVoiced < 0) return false;

        double firstTime = frames.get(firstVoiced).timeSeconds();
        double hopSeconds = medianHopSeconds(frames);
        double endTime = frames.get(lastVoiced).timeSeconds() + hopSeconds;
        if (endTime - firstTime < 3.0) return false;

        int windowCount = Math.max(1, (int) Math.ceil((endTime - firstTime)
                / PERSISTENCE_WINDOW_SECONDS));
        int[] voiced = new int[windowCount];
        int[] nearby = new int[windowCount];
        int totalVoiced = 0;
        int totalNearby = 0;
        for (PitchFrame frame : frames) {
            if (!frame.voiced() || frame.timeSeconds() < firstTime || frame.timeSeconds() >= endTime) continue;
            int window = Math.min(windowCount - 1, Math.max(0, (int) ((frame.timeSeconds() - firstTime)
                    / PERSISTENCE_WINDOW_SECONDS)));
            voiced[window]++;
            totalVoiced++;
            if (octaveDistance(frame.absoluteCent(), tonicCent) <= TONIC_NEAR_CENT) {
                nearby[window]++;
                totalNearby++;
            }
        }
        if (totalNearby < Math.max(30, Math.round(totalVoiced * 0.06))) return false;

        int eligible = 0;
        int supporting = 0;
        int unsupportedRun = 0;
        int longestUnsupportedRun = 0;
        boolean supportInFirstHalf = false;
        boolean supportInSecondHalf = false;
        int minimumVoicedFrames = Math.max(1, (int) Math.round(0.50 / hopSeconds));
        int minimumNearbyFrames = Math.max(1, (int) Math.round(0.16 / hopSeconds));
        for (int window = 0; window < windowCount; window++) {
            // Half a second of voiced evidence is required; sparse partial
            // windows cannot establish a continuous resonant tone.
            if (voiced[window] < minimumVoicedFrames) {
                unsupportedRun = 0;
                continue;
            }
            eligible++;
            boolean supported = nearby[window] >= minimumNearbyFrames
                    && nearby[window] >= Math.ceil(voiced[window] * 0.20);
            if (!supported) {
                unsupportedRun++;
                longestUnsupportedRun = Math.max(longestUnsupportedRun, unsupportedRun);
                continue;
            }
            unsupportedRun = 0;
            supporting++;
            double center = firstTime + (window + 0.5) * PERSISTENCE_WINDOW_SECONDS;
            if (center <= firstTime + (endTime - firstTime) * 0.5) supportInFirstHalf = true;
            else supportInSecondHalf = true;
        }
        return eligible >= 4
                && supporting >= Math.ceil(eligible * 0.60)
                && longestUnsupportedRun <= 2
                && supportInFirstHalf && supportInSecondHalf;
    }

    private static double medianHopSeconds(List<PitchFrame> frames) {
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

    static List<PitchFrame> mergeMelodyCandidates(List<PitchFrame> raw,
                                                   List<PitchFrame> filtered,
                                                   double tonicHertz) {
        if (raw.size() != filtered.size()) {
            throw new IllegalArgumentException("Raw and filtered pitch tracks must have equal length");
        }
        List<PitchFrame> result = new ArrayList<>(raw.size());
        double tonicCent = hertzToCent(tonicHertz);
        double previous = Double.NaN;
        for (int i = 0; i < raw.size(); i++) {
            PitchFrame original = raw.get(i);
            PitchFrame alternative = filtered.get(i);
            PitchFrame selected = original;
            if (alternative.voiced() && alternative.confidence() >= 0.65) {
                if (!original.voiced()) {
                    selected = alternative;
                } else if (alternative.confidence() < original.confidence() - 0.04) {
                    // Notching a real tonic can leave a confident-looking upper
                    // partial. Never replace a stronger raw F0 with that residue.
                    selected = original;
                } else {
                    double separation = Math.abs(alternative.absoluteCent() - original.absoluteCent());
                    boolean rawLooksLikeDrone = octaveDistance(original.absoluteCent(), tonicCent) <= 60.0;
                    boolean plausibleSeparation = separation >= 45.0 && separation <= 750.0;
                    if (rawLooksLikeDrone && plausibleSeparation) {
                        selected = alternative;
                    } else if (Double.isFinite(previous)) {
                        double originalStep = Math.abs(original.absoluteCent() - previous);
                        double alternativeStep = Math.abs(alternative.absoluteCent() - previous);
                        if (alternativeStep + 35.0 < originalStep && alternativeStep < 500.0) selected = alternative;
                        else if (alternative.confidence() > original.confidence() + 0.12
                                && separation < 350.0) selected = alternative;
                    } else if (alternative.confidence() > original.confidence() + 0.12) {
                        selected = alternative;
                    }
                }
            }
            // The academic F0 may legitimately come from the tonic-filtered
            // pass, but the audit layer must continue to expose what the raw
            // detector actually heard.  Keep the selected reliable F0 in the
            // main fields and always carry the original raw candidate in the
            // candidate fields.
            if (selected != original) {
                double rawCandidateHertz = original.candidateVoiced()
                        ? original.candidateHertz()
                        : original.voiced() ? original.hertz() : 0.0;
                double rawCandidateCent = original.candidateVoiced()
                        ? original.candidateAbsoluteCent()
                        : original.voiced() ? original.absoluteCent() : Double.NaN;
                selected = new PitchFrame(selected.timeSeconds(), selected.hertz(),
                        selected.absoluteCent(), selected.confidence(), selected.rms(),
                        rawCandidateHertz, rawCandidateCent);
            }
            result.add(selected);
            if (selected.voiced()) previous = selected.absoluteCent();
        }
        return result;
    }

    /**
     * Repairs a second, narrower class of temporal aliases: a brief stable
     * sub-90 Hz common period between audible 2f/3f components.  The ordinary
     * island pass above intentionally requires the pitch before and after an
     * alias to be the same.  Real instrumental transitions can instead move
     * between 2f and 3f (for example, roughly 160 and 240 Hz), causing YIN to
     * report their inaudible common period near 80 Hz for several frames.
     *
     * <p>This pass remains deliberately local and register-neutral.  It needs
     * compatible voiced context on <em>both</em> sides, limits the candidate to
     * 360 ms, and refuses moving/unstable low contours.  Consequently a real
     * sustained low note, a permanent register change, a one-sided onset or a
     * glissando is not folded upward.  A context one octave above 2f/3f is
     * accepted as the same harmonic family; this covers the observed
     * 54/107/214 Hz transition without adding a general f/4 correction.</p>
     */
    private static List<PitchFrame> suppressShortCommonPeriodAliases(List<PitchFrame> frames) {
        List<PitchFrame> result = new ArrayList<>(frames);
        double hopSeconds = medianHopSeconds(frames);
        int index = 0;
        while (index < result.size()) {
            PitchFrame first = result.get(index);
            if (!isCommonPeriodCandidate(first)) {
                index++;
                continue;
            }

            int end = index;
            double minimumCent = first.absoluteCent();
            double maximumCent = first.absoluteCent();
            while (end + 1 < result.size() && isCommonPeriodCandidate(result.get(end + 1))) {
                end++;
                minimumCent = Math.min(minimumCent, result.get(end).absoluteCent());
                maximumCent = Math.max(maximumCent, result.get(end).absoluteCent());
            }

            double duration = result.get(end).timeSeconds() - first.timeSeconds() + hopSeconds;
            if (duration > MAXIMUM_COMMON_PERIOD_ALIAS_SECONDS + 1e-9
                    || maximumCent - minimumCent > MAXIMUM_COMMON_PERIOD_SPREAD_CENT) {
                index = end + 1;
                continue;
            }

            HarmonicContext left = harmonicContext(result, index - 1, -1,
                    first.timeSeconds(), COMMON_PERIOD_CONTEXT_SECONDS);
            HarmonicContext right = harmonicContext(result, end + 1, 1,
                    result.get(end).timeSeconds(), COMMON_PERIOD_CONTEXT_SECONDS);
            double medianLowCent = medianCent(result, index, end);
            if (left == null || right == null
                    || !matchesSecondOrThirdMultiple(medianLowCent, left.cent())
                    || !matchesSecondOrThirdMultiple(medianLowCent, right.cent())
                    || !matchesAbsoluteSecondOrThirdMultiple(medianLowCent, left.cent())
                       && !matchesAbsoluteSecondOrThirdMultiple(medianLowCent, right.cent())) {
                index = end + 1;
                continue;
            }

            for (int cursor = index; cursor <= end; cursor++) {
                PitchFrame original = result.get(cursor);
                double doubled = original.absoluteCent() + 1200.0;
                double tripled = original.absoluteCent() + 1200.0 * Math.log(3.0) / Math.log(2.0);
                double doubledScore = contextScore(doubled, original.timeSeconds(), left, right);
                double tripledScore = contextScore(tripled, original.timeSeconds(), left, right);
                double corrected = doubledScore <= tripledScore ? doubled : tripled;
                result.set(cursor, new PitchFrame(original.timeSeconds(), centToHertz(corrected), corrected,
                        original.confidence(), original.rms(), original.candidateHertz(),
                        original.candidateAbsoluteCent()));
            }
            index = end + 1;
        }
        return result;
    }

    private static boolean isCommonPeriodCandidate(PitchFrame frame) {
        return frame.voiced() && frame.hertz() < COMMON_PERIOD_ALIAS_CEILING_HZ;
    }

    private static HarmonicContext harmonicContext(List<PitchFrame> frames, int start, int step,
                                                    double boundaryTime, double maximumSeconds) {
        for (int cursor = start; cursor >= 0 && cursor < frames.size(); cursor += step) {
            PitchFrame frame = frames.get(cursor);
            if (Math.abs(frame.timeSeconds() - boundaryTime) > maximumSeconds + 1e-9) break;
            if (!frame.voiced() || frame.hertz() < COMMON_PERIOD_ALIAS_CEILING_HZ) continue;
            // The closest voiced value is the transition boundary.  Averaging
            // farther notes here is unsafe: fast makam passages can already be
            // on another degree 100 ms later, and that unrelated note must not
            // veto otherwise exact 2f/3f evidence at the boundary.
            return new HarmonicContext(frame.absoluteCent(), frame.timeSeconds());
        }
        return null;
    }

    private static double medianCent(List<PitchFrame> frames, int start, int end) {
        double[] cents = new double[end - start + 1];
        for (int cursor = start; cursor <= end; cursor++) {
            cents[cursor - start] = frames.get(cursor).absoluteCent();
        }
        Arrays.sort(cents);
        return cents[cents.length / 2];
    }

    private static boolean matchesSecondOrThirdMultiple(double lowCent, double contextCent) {
        double doubled = lowCent + 1200.0;
        double tripled = lowCent + 1200.0 * Math.log(3.0) / Math.log(2.0);
        return Math.min(octaveDistance(doubled, contextCent), octaveDistance(tripled, contextCent))
                <= COMMON_PERIOD_MATCH_CENT;
    }

    private static boolean matchesAbsoluteSecondOrThirdMultiple(double lowCent, double contextCent) {
        double doubled = lowCent + 1200.0;
        double tripled = lowCent + 1200.0 * Math.log(3.0) / Math.log(2.0);
        return Math.min(Math.abs(doubled - contextCent), Math.abs(tripled - contextCent))
                <= COMMON_PERIOD_MATCH_CENT;
    }

    private static double contextScore(double candidateCent, double timeSeconds,
                                       HarmonicContext left, HarmonicContext right) {
        double leftDistance = Math.max(0.01, Math.abs(timeSeconds - left.timeSeconds()));
        double rightDistance = Math.max(0.01, Math.abs(right.timeSeconds() - timeSeconds));
        double leftWeight = 1.0 / leftDistance;
        double rightWeight = 1.0 / rightDistance;
        return octaveDistance(candidateCent, left.cent()) * leftWeight
                + octaveDistance(candidateCent, right.cent()) * rightWeight;
    }

    private record HarmonicContext(double cent, double timeSeconds) {}

    private static double octaveDistance(double first, double second) {
        double difference = Math.abs(first - second) % 1200.0;
        return Math.min(difference, 1200.0 - difference);
    }

    public static double hertzToCent(double hertz) {
        return 1200.0 * Math.log(hertz / REFERENCE_C0) / Math.log(2.0);
    }

    public static double centToHertz(double cent) {
        return REFERENCE_C0 * Math.pow(2.0, cent / 1200.0);
    }

    private static void removeDc(float[] frame) {
        double mean = 0.0;
        for (float value : frame) mean += value;
        mean /= frame.length;
        for (int i = 0; i < frame.length; i++) frame[i] -= (float) mean;
    }

    private static double rms(float[] frame) {
        double sum = 0.0;
        for (float value : frame) sum += value * value;
        return Math.sqrt(sum / frame.length);
    }

    private static List<PitchFrame> medianSmooth(List<PitchFrame> frames, int radius) {
        List<PitchFrame> result = new ArrayList<>(frames.size());
        for (int i = 0; i < frames.size(); i++) {
            PitchFrame original = frames.get(i);
            if (!original.voiced()) {
                result.add(original);
                continue;
            }
            double[] window = new double[radius * 2 + 1];
            int count = 0;
            for (int j = Math.max(0, i - radius); j <= Math.min(frames.size() - 1, i + radius); j++) {
                if (frames.get(j).voiced()) window[count++] = frames.get(j).absoluteCent();
            }
            Arrays.sort(window, 0, count);
            double median = window[count / 2];
            result.add(new PitchFrame(original.timeSeconds(), centToHertz(median), median,
                    original.confidence(), original.rms(), original.candidateHertz(),
                    original.candidateAbsoluteCent()));
        }
        return result;
    }

    /**
     * Removes only very short octave/third-subharmonic islands which leave and
     * return to the same local pitch.  Sustained register changes are retained,
     * as are continuous glissandi (their intermediate frames cannot all be
     * mapped to one harmonic anchor).  This temporal condition complements the
     * spectral test without introducing a global preferred register.
     */
    static List<PitchFrame> suppressShortHarmonicAliases(List<PitchFrame> frames) {
        List<PitchFrame> result = new ArrayList<>(frames);
        int index = 1;
        while (index < frames.size() - 1) {
            PitchFrame before = result.get(index - 1);
            PitchFrame first = result.get(index);
            if (!before.voiced() || !first.voiced()
                    || Math.abs(first.absoluteCent() - before.absoluteCent()) < 900.0) {
                index++;
                continue;
            }

            int end = index;
            while (end < frames.size() - 1 && end - index < MAXIMUM_TEMPORAL_ALIAS_FRAMES) {
                PitchFrame candidate = result.get(end);
                if (!candidate.voiced()
                        || !Double.isFinite(foldedCentNear(candidate.hertz(), before.absoluteCent()))) break;
                end++;
            }
            if (end == index || end >= frames.size() || end - index > MAXIMUM_TEMPORAL_ALIAS_FRAMES) {
                index++;
                continue;
            }

            PitchFrame after = result.get(end);
            if (!after.voiced()
                    || Math.abs(after.absoluteCent() - before.absoluteCent()) > TEMPORAL_ALIAS_MATCH_CENT) {
                index++;
                continue;
            }

            boolean coherent = true;
            for (int cursor = index; cursor < end; cursor++) {
                double fraction = (cursor - index + 1.0) / (end - index + 1.0);
                double reference = before.absoluteCent()
                        + fraction * (after.absoluteCent() - before.absoluteCent());
                if (!Double.isFinite(foldedCentNear(result.get(cursor).hertz(), reference))) {
                    coherent = false;
                    break;
                }
            }
            if (!coherent) {
                index++;
                continue;
            }

            for (int cursor = index; cursor < end; cursor++) {
                PitchFrame original = result.get(cursor);
                double fraction = (cursor - index + 1.0) / (end - index + 1.0);
                double reference = before.absoluteCent()
                        + fraction * (after.absoluteCent() - before.absoluteCent());
                double corrected = foldedCentNear(original.hertz(), reference);
                result.set(cursor, new PitchFrame(original.timeSeconds(), centToHertz(corrected), corrected,
                        original.confidence(), original.rms(), original.candidateHertz(),
                        original.candidateAbsoluteCent()));
            }
            index = end + 1;
        }
        return suppressShortCommonPeriodAliases(result);
    }

    private static double foldedCentNear(double hertz, double referenceCent) {
        if (!(hertz > 0.0) || !Double.isFinite(referenceCent)) return Double.NaN;
        double[] candidates = {hertz * 0.5, hertz / 3.0, hertz * 2.0, hertz * 3.0};
        double originalCent = hertzToCent(hertz);
        double best = Double.NaN;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (double candidate : candidates) {
            if (candidate < 45.0 || candidate > 1600.0) continue;
            double candidateCent = hertzToCent(candidate);
            double distance = Math.abs(candidateCent - referenceCent);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidateCent;
            }
        }
        if (bestDistance > TEMPORAL_ALIAS_MATCH_CENT
                || Math.abs(originalCent - referenceCent) < 900.0) return Double.NaN;
        return best;
    }
}
