package com.makambox.studio.analysis;

import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.model.PitchOccurrence;
import com.makambox.studio.theory.TheoryCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Segments the complete reliable pitch trace into stable makam-theory note events.
 *
 * <p>The public {@link #analyze} result is the canonical, presentation-safe
 * layer. Switching has an adaptive 4–12 C hysteresis, and an interior contact
 * of at most 60 ms is folded only when the same stable identity surrounds it
 * (A-B-A). Isolated short contacts at an attack, release or silence boundary
 * therefore remain visible. This prevents a sustained pitch on a 53-Hc
 * midpoint from alternating note names every 20 ms without erasing the short
 * contacts users inspect. Midpoint-only evidence is additionally available
 * through {@link #analyzeRaw}.</p>
 */
public final class AllPitchEventAnalyzer {
    public static final double MINIMUM_FRAME_CONFIDENCE = 0.35;
    public static final double MAXIMUM_UNVOICED_GAP_SECONDS = 0.060;
    public static final double MINIMUM_CANONICAL_PERSISTENCE_SECONDS = 0.060;
    public static final double MINIMUM_HYSTERESIS_CENT = 4.0;
    public static final double MAXIMUM_HYSTERESIS_CENT = 12.0;

    public List<PitchEvent> analyze(List<PitchFrame> frames, AnalysisConfig config,
                                    TheoryCatalog catalog, TheoryCatalog.Makam makam) {
        return analyze(frames, config, catalog, makam, false);
    }

    /**
     * Segments an interval study by makam pitch class rather than by one
     * physical tonic register.  A confirmed tonic and its exact octave
     * equivalents must produce the same normalized event evidence; the
     * physical F0 and register are still retained in each occurrence for audit.
     */
    public List<PitchEvent> analyzePitchClasses(List<PitchFrame> frames,
                                                AnalysisConfig config,
                                                TheoryCatalog catalog,
                                                TheoryCatalog.Makam makam) {
        return analyze(frames, config, catalog, makam, true);
    }

    private List<PitchEvent> analyze(List<PitchFrame> frames, AnalysisConfig config,
                                     TheoryCatalog catalog, TheoryCatalog.Makam makam,
                                     boolean pitchClassMode) {
        Objects.requireNonNull(frames, "frames");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(makam, "makam");
        if (frames.isEmpty()) return List.of();
        if (!Double.isFinite(config.tonicHz()) || config.tonicHz() <= 0.0) {
            throw new IllegalArgumentException("tonicHz must be finite and positive");
        }

        List<NoteCenter> centers = pitchClassMode
                ? pitchClassCenters(catalog, makam) : noteCenters(catalog, makam);
        double tonicAbsoluteCent = hertzToAbsoluteCent(config.tonicHz());
        NoteCenter[] rawAssignments = new NoteCenter[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            PitchFrame frame = frames.get(i);
            if (eligible(frame)) {
                double relativeCent = frame.absoluteCent() - tonicAbsoluteCent;
                rawAssignments[i] = pitchClassMode
                        ? nearestPitchClassCenter(relativeCent, centers)
                        : nearestCenter(relativeCent, centers);
            }
        }

        double frameStep = PitchOccurrenceSemantics.frameStep(frames);
        NoteCenter[] canonicalAssignments = stabilizeAssignments(frames, rawAssignments,
                tonicAbsoluteCent, frameStep);
        return eventsFromAssignments(frames, config, canonicalAssignments,
                tonicAbsoluteCent, frameStep);
    }

    /**
     * Returns unsmoothed midpoint contacts for diagnostics and transient evidence.
     *
     * <p>Do not interpret the number of events returned by this method as a note
     * count: vibrato, glissando and boundary noise can each create several raw
     * contacts. User-facing tables and editor blocks should use {@link #analyze}.</p>
     */
    public List<PitchEvent> analyzeRaw(List<PitchFrame> frames, AnalysisConfig config,
                                       TheoryCatalog catalog, TheoryCatalog.Makam makam) {
        Objects.requireNonNull(frames, "frames");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(makam, "makam");
        if (frames.isEmpty()) return List.of();
        if (!Double.isFinite(config.tonicHz()) || config.tonicHz() <= 0.0) {
            throw new IllegalArgumentException("tonicHz must be finite and positive");
        }

        List<NoteCenter> centers = noteCenters(catalog, makam);
        double tonicAbsoluteCent = hertzToAbsoluteCent(config.tonicHz());
        NoteCenter[] rawAssignments = new NoteCenter[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            PitchFrame frame = frames.get(i);
            if (eligible(frame)) {
                rawAssignments[i] = nearestCenter(frame.absoluteCent() - tonicAbsoluteCent, centers);
            }
        }
        double frameStep = PitchOccurrenceSemantics.frameStep(frames);
        return eventsFromAssignments(frames, config, rawAssignments,
                tonicAbsoluteCent, frameStep);
    }

    private static NoteCenter[] stabilizeAssignments(List<PitchFrame> frames,
                                                      NoteCenter[] rawAssignments,
                                                      double tonicAbsoluteCent,
                                                      double frameStep) {
        NoteCenter[] canonical = new NoteCenter[rawAssignments.length];
        int cursor = 0;
        while (cursor < rawAssignments.length) {
            while (cursor < rawAssignments.length && rawAssignments[cursor] == null) cursor++;
            if (cursor >= rawAssignments.length) break;
            int blockEnd = cursor;
            while (blockEnd + 1 < rawAssignments.length && rawAssignments[blockEnd + 1] != null) {
                blockEnd++;
            }
            applyHysteresis(frames, rawAssignments, canonical, cursor, blockEnd,
                    tonicAbsoluteCent);
            restoreTrailingContact(rawAssignments, canonical, cursor, blockEnd);
            collapseInteriorChatter(frames, canonical, cursor, blockEnd,
                    tonicAbsoluteCent, frameStep);
            cursor = blockEnd + 1;
        }
        return canonical;
    }

    private static void applyHysteresis(List<PitchFrame> frames,
                                        NoteCenter[] rawAssignments,
                                        NoteCenter[] canonical,
                                        int blockStart, int blockEnd,
                                        double tonicAbsoluteCent) {
        NoteCenter current = rawAssignments[blockStart];
        canonical[blockStart] = current;
        for (int cursor = blockStart + 1; cursor <= blockEnd; cursor++) {
            NoteCenter raw = rawAssignments[cursor];
            if (!sameNote(raw, current)) {
                double relativeCent = frames.get(cursor).absoluteCent() - tonicAbsoluteCent;
                if (clearlyCloser(relativeCent, current, raw)) current = raw;
            }
            canonical[cursor] = current;
        }
    }

    private static void collapseInteriorChatter(List<PitchFrame> frames,
                                                NoteCenter[] canonical,
                                                int blockStart, int blockEnd,
                                                double tonicAbsoluteCent,
                                                double frameStep) {
        boolean changed;
        do {
            changed = false;
            int runStart = blockStart;
            while (runStart <= blockEnd) {
                NoteCenter center = canonical[runStart];
                int runEnd = runStart;
                while (runEnd + 1 <= blockEnd && sameNote(canonical[runEnd + 1], center)) {
                    runEnd++;
                }
                int before = runStart - 1;
                int after = runEnd + 1;
                if (before >= blockStart && after <= blockEnd
                        && sameNote(canonical[before], canonical[after])
                        && runDuration(frames, runStart, runEnd, frameStep)
                        <= MINIMUM_CANONICAL_PERSISTENCE_SECONDS + 1.0e-9
                        && weakBoundaryEvidence(frames, runStart, runEnd, center,
                        canonical[before], tonicAbsoluteCent)) {
                    fill(canonical, runStart, runEnd, canonical[before]);
                    changed = true;
                }
                runStart = runEnd + 1;
            }
        } while (changed);
    }

    private static void restoreTrailingContact(NoteCenter[] rawAssignments,
                                               NoteCenter[] canonical,
                                               int blockStart, int blockEnd) {
        NoteCenter trailing = rawAssignments[blockEnd];
        if (sameNote(trailing, canonical[blockEnd])) return;
        int trailingStart = blockEnd;
        while (trailingStart > blockStart
                && sameNote(rawAssignments[trailingStart - 1], trailing)) {
            trailingStart--;
        }
        fill(canonical, trailingStart, blockEnd, trailing);
    }

    private static boolean weakBoundaryEvidence(List<PitchFrame> frames, int start, int end,
                                                NoteCenter candidate, NoteCenter surrounding,
                                                double tonicAbsoluteCent) {
        List<Double> advantages = new ArrayList<>(end - start + 1);
        for (int index = start; index <= end; index++) {
            double relativeCent = frames.get(index).absoluteCent() - tonicAbsoluteCent;
            double surroundingDistance = Math.abs(relativeCent - surrounding.relativeCent());
            double candidateDistance = Math.abs(relativeCent - candidate.relativeCent());
            advantages.add(surroundingDistance - candidateDistance);
        }
        advantages.sort(Double::compareTo);
        double medianAdvantage = advantages.size() % 2 == 0
                ? (advantages.get(advantages.size() / 2 - 1)
                + advantages.get(advantages.size() / 2)) / 2.0
                : advantages.get(advantages.size() / 2);
        double separation = Math.abs(candidate.relativeCent() - surrounding.relativeCent());
        double strongEvidenceThreshold = Math.max(8.0, Math.min(30.0, separation * 0.50));
        return medianAdvantage < strongEvidenceThreshold;
    }

    private static double runDuration(List<PitchFrame> frames, int start, int end,
                                      double frameStep) {
        double duration = frames.get(end).timeSeconds()
                - frames.get(start).timeSeconds() + frameStep;
        if (!Double.isFinite(duration)) {
            duration = (end - start + 1) * frameStep;
        }
        return Math.max(frameStep, duration);
    }

    private static boolean clearlyCloser(double relativeCent, NoteCenter current,
                                           NoteCenter candidate) {
        double currentDistance = Math.abs(relativeCent - current.relativeCent());
        double candidateDistance = Math.abs(relativeCent - candidate.relativeCent());
        double separation = Math.abs(candidate.relativeCent() - current.relativeCent());
        double hysteresis = Math.max(MINIMUM_HYSTERESIS_CENT,
                Math.min(MAXIMUM_HYSTERESIS_CENT, separation * 0.25));
        return candidateDistance + hysteresis < currentDistance;
    }

    private static boolean sameNote(NoteCenter first, NoteCenter second) {
        return first != null && second != null
                && first.note().key().equals(second.note().key())
                && Math.abs(first.relativeCent() - second.relativeCent()) <= 1.0e-7;
    }

    private static void fill(NoteCenter[] assignments, int start, int end, NoteCenter value) {
        if (value == null || start < 0 || end < start) return;
        for (int index = start; index <= end; index++) assignments[index] = value;
    }

    private static List<PitchEvent> eventsFromAssignments(List<PitchFrame> frames,
                                                           AnalysisConfig config,
                                                           NoteCenter[] assignments,
                                                           double tonicAbsoluteCent,
                                                           double frameStep) {

        List<PitchEvent> events = new ArrayList<>();
        int i = 0;
        while (i < frames.size()) {
            NoteCenter center = assignments[i];
            if (center == null) {
                i++;
                continue;
            }

            int start = i;
            int lastAssigned = i;
            int cursor = i + 1;
            while (cursor < frames.size()) {
                NoteCenter nextCenter = assignments[cursor];
                if (nextCenter != null) {
                    if (!nextCenter.note().key().equals(center.note().key())) break;
                    lastAssigned = cursor++;
                    continue;
                }

                // Low-confidence or out-of-theory voiced frames are hard boundaries.
                // Only an actually unvoiced run is eligible for dropout bridging.
                if (frames.get(cursor).voiced()) break;
                while (cursor < frames.size() && !frames.get(cursor).voiced()) cursor++;
                if (cursor >= frames.size()) break;
                NoteCenter afterGap = assignments[cursor];
                if (afterGap == null
                        || !afterGap.note().key().equals(center.note().key())
                        || unvoicedGapSeconds(frames, lastAssigned, cursor, frameStep)
                        > MAXIMUM_UNVOICED_GAP_SECONDS + 1.0e-9) {
                    break;
                }
                lastAssigned = cursor++;
            }

            double expectedAbsoluteCent = tonicAbsoluteCent + center.relativeCent();
            PitchOccurrence occurrence = PitchOccurrenceSemantics.create(events.size() + 1,
                    frames, start, lastAssigned, expectedAbsoluteCent, config);
            events.add(new PitchEvent(center.note().key(), center.note().displayName(),
                    center.relativeCent(), occurrence));
            i = lastAssigned + 1;
        }
        return List.copyOf(events);
    }

    private static boolean eligible(PitchFrame frame) {
        return frame.voiced()
                && Double.isFinite(frame.confidence())
                && frame.confidence() >= MINIMUM_FRAME_CONFIDENCE;
    }

    private static List<NoteCenter> noteCenters(TheoryCatalog catalog, TheoryCatalog.Makam makam) {
        // Event analysis follows the complete named Kaba/Tiz/Tiz Tiz theory
        // range. notesAround(makam) is intentionally only a compact selector /
        // initial-view range and used to stop at Muhayyer for Uşşak, which
        // left genuine high-register F0 frames without blue event blocks.
        List<TheoryCatalog.Note> notes = catalog.notesForPitchAnalysis(makam).stream()
                .sorted(Comparator.comparingDouble(note -> catalog.relativeCent(makam, note)))
                .toList();
        List<NoteCenter> centers = new ArrayList<>(notes.size());
        for (int index = 0; index < notes.size(); index++) {
            TheoryCatalog.Note note = notes.get(index);
            centers.add(new NoteCenter(note, catalog.relativeCent(makam, note)));
        }
        if (centers.size() < 2) {
            throw new IllegalArgumentException("At least two theory notes are required for midpoint assignment");
        }
        for (int i = 1; i < centers.size(); i++) {
            if (!(centers.get(i).relativeCent() > centers.get(i - 1).relativeCent())) {
                throw new IllegalArgumentException("Theory note centers must be unique and strictly ordered");
            }
        }
        return centers;
    }

    private static List<NoteCenter> pitchClassCenters(TheoryCatalog catalog,
                                                       TheoryCatalog.Makam makam) {
        List<NoteCenter> unique = new ArrayList<>();
        for (NoteCenter center : noteCenters(catalog, makam)) {
            double normalized = center.relativeCent() % 1200.0;
            if (normalized < 0.0) normalized += 1200.0;
            if (normalized >= 1200.0 - 1.0e-7) normalized = 0.0;
            NoteCenter candidate = new NoteCenter(center.note(), normalized);
            int duplicate = -1;
            for (int index = 0; index < unique.size(); index++) {
                if (Math.abs(unique.get(index).relativeCent() - normalized) <= 0.10) {
                    duplicate = index;
                    break;
                }
            }
            NoteCenter previous = duplicate < 0 ? null : unique.get(duplicate);
            boolean candidateIsBase = center.relativeCent() >= -1.0e-7
                    && center.relativeCent() < 1200.0 - 1.0e-7;
            boolean previousIsBase = previous != null
                    && catalog.relativeCent(makam, previous.note()) >= -1.0e-7
                    && catalog.relativeCent(makam, previous.note()) < 1200.0 - 1.0e-7;
            if (previous == null || candidateIsBase && !previousIsBase) {
                if (duplicate < 0) unique.add(candidate);
                else unique.set(duplicate, candidate);
            }
        }
        return unique.stream()
                .sorted(Comparator.comparingDouble(NoteCenter::relativeCent)).toList();
    }

    private static NoteCenter nearestPitchClassCenter(double relativeCent,
                                                       List<NoteCenter> centers) {
        if (!Double.isFinite(relativeCent) || centers.isEmpty()) return null;
        NoteCenter nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (NoteCenter center : centers) {
            double shifted = center.relativeCent()
                    + Math.rint((relativeCent - center.relativeCent()) / 1200.0) * 1200.0;
            double distance = Math.abs(relativeCent - shifted);
            if (distance + 1.0e-9 < nearestDistance) {
                nearestDistance = distance;
                nearest = new NoteCenter(center.note(), shifted);
            }
        }
        return nearest;
    }

    /** Returns null outside the two finite outer half-interval boundaries. */
    private static NoteCenter nearestCenter(double relativeCent, List<NoteCenter> centers) {
        double first = centers.get(0).relativeCent();
        double second = centers.get(1).relativeCent();
        double penultimate = centers.get(centers.size() - 2).relativeCent();
        double last = centers.get(centers.size() - 1).relativeCent();
        double lowerOuterBoundary = first - (second - first) / 2.0;
        double upperOuterBoundary = last + (last - penultimate) / 2.0;
        if (!Double.isFinite(relativeCent)
                || relativeCent < lowerOuterBoundary
                || relativeCent > upperOuterBoundary) {
            return null;
        }

        int low = 0;
        int high = centers.size() - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            double midpoint = (centers.get(middle).relativeCent()
                    + centers.get(middle + 1).relativeCent()) / 2.0;
            // An exact tie deterministically belongs to the lower center.
            if (relativeCent <= midpoint + 1.0e-9) high = middle;
            else low = middle + 1;
        }
        return centers.get(low);
    }

    private static double unvoicedGapSeconds(List<PitchFrame> frames, int before, int after,
                                             double frameStep) {
        double byTimeline = frames.get(after).timeSeconds()
                - frames.get(before).timeSeconds() - frameStep;
        if (Double.isFinite(byTimeline)) return Math.max(0.0, byTimeline);
        return Math.max(0, after - before - 1) * frameStep;
    }

    private static double hertzToAbsoluteCent(double hertz) {
        return 1200.0 * Math.log(hertz / 16.351597831287414) / Math.log(2.0);
    }

    private record NoteCenter(TheoryCatalog.Note note, double relativeCent) { }
}
