package com.makambox.studio.study;

import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.EventType;
import com.makambox.studio.model.MelodicDirection;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.model.PitchOccurrence;
import com.makambox.studio.theory.TheoryCatalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Context-aware analysis of the second degree of an Uşşak tetrachord.
 *
 * <p>This service intentionally does not require a candidate to have been
 * named {@code segah} by the generic nearest-centre classifier.  A slightly
 * high functional Segâh can otherwise become Bûselik, and a low one can become
 * Dik Kürdî.  The F0 plateau is first found in a broad second-degree band; its
 * function is then proven by the surrounding Dügâh/Çargâh anchors.</p>
 */
public final class UssakSegahDirectionalAnalyzer {
    public static final double MINIMUM_FRAME_CONFIDENCE = 0.35;
    public static final double PRIMARY_STATISTICS_CONFIDENCE = 0.65;
    public static final double MINIMUM_EVENT_SECONDS = 0.10;
    public static final double DIRECT_HOLD_SECONDS = 0.70;
    public static final double MAXIMUM_ANCHOR_GAP_SECONDS = 2.50;
    public static final double MAXIMUM_INCLUDED_SPREAD_HC = 0.75;
    public static final double MAXIMUM_HOLD_SPREAD_HC = 0.55;
    /** A plateau below this remains visible, but is not forced to be functional Segâh. */
    public static final double FUNCTIONAL_SEGAH_LOWER_OFFSET_HC = -2.50;
    public static final double MAXIMUM_WITHIN_REGION_FRAME_STEP_HC = .65;

    public UssakSegahDirectionalStudyResult analyze(
            List<PitchFrame> frames,
            List<PitchEvent> allCanonicalEvents,
            AnalysisConfig config,
            TheoryCatalog catalog,
            TheoryCatalog.Makam makam,
            double tonicHertz) {
        return analyze(frames, allCanonicalEvents, config, catalog, makam, tonicHertz,
                catalog.note("dugah"), catalog.note("buselik"));
    }

    /**
     * Runs the Segâh directional study inside a user-selected open pitch range.
     *
     * <p>The selected notes only define where candidate F0 plateaux are sought;
     * Dügâh/Çargâh anchors and the theoretical Segâh reference still determine
     * the musical function and ascending/descending classification.  This keeps
     * the research question stable while allowing recordings whose practical
     * Segâh region needs a narrower or wider inspection window.</p>
     */
    public UssakSegahDirectionalStudyResult analyze(
            List<PitchFrame> frames,
            List<PitchEvent> allCanonicalEvents,
            AnalysisConfig config,
            TheoryCatalog catalog,
            TheoryCatalog.Makam makam,
            double tonicHertz,
            TheoryCatalog.Note firstBoundary,
            TheoryCatalog.Note secondBoundary) {
        Objects.requireNonNull(frames, "frames");
        Objects.requireNonNull(allCanonicalEvents, "allCanonicalEvents");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(makam, "makam");
        Objects.requireNonNull(firstBoundary, "firstBoundary");
        Objects.requireNonNull(secondBoundary, "secondBoundary");
        if (!Double.isFinite(tonicHertz) || tonicHertz <= 0.0) {
            throw new IllegalArgumentException("tonicHertz must be finite and positive");
        }
        if (!"dugah".equals(catalog.tonicFor(makam).key())) {
            throw new IllegalArgumentException(
                    "Uşşak dörtlüsü Segâh analizi için karar perdesi Dügâh olmalıdır");
        }

        double tonicAbsoluteCent = PitchTracker.hertzToCent(tonicHertz);
        double segahCent = catalog.relativeCent(makam, catalog.note("segah"));
        double buselikCent = catalog.relativeCent(makam, catalog.note("buselik"));
        double firstBoundaryCent = catalog.relativeCent(makam, firstBoundary);
        double secondBoundaryCent = catalog.relativeCent(makam, secondBoundary);
        if (firstBoundary.key().equals(secondBoundary.key())
                || Math.abs(firstBoundaryCent - secondBoundaryCent) < 1.0e-9) {
            throw new IllegalArgumentException("Yönsel analiz sınır perdeleri farklı olmalıdır");
        }
        // Open research boundary: lower < observed F0 < upper.  The two named
        // boundary pitches themselves are never candidate events.
        double selectedLowerCent = Math.min(firstBoundaryCent, secondBoundaryCent);
        double selectedUpperCent = Math.max(firstBoundaryCent, secondBoundaryCent);
        double lower = tonicAbsoluteCent + selectedLowerCent;
        double upper = tonicAbsoluteCent + selectedUpperCent;
        // A user may widen the inspection window, but that must not turn an
        // adjacent pitch into functional Segâh.  Conversely a narrower window
        // is respected exactly.
        double functionalLower = Math.max(lower, tonicAbsoluteCent + segahCent
                + FUNCTIONAL_SEGAH_LOWER_OFFSET_HC * AnalysisConfig.COMMA_CENT);
        double functionalUpper = Math.min(upper, tonicAbsoluteCent + buselikCent);
        double step = frameStep(frames);
        List<FrameRegion> regions = candidateRegions(frames, lower, upper, step);
        List<UssakSegahEvent> events = new ArrayList<>(regions.size());
        for (FrameRegion region : regions) {
            events.add(event(events.size() + 1, region, frames, allCanonicalEvents,
                    tonicAbsoluteCent, segahCent, functionalLower, functionalUpper, step));
        }

        EnumMap<UssakSegahUsageClass, StudyStatistics> statistics =
                new EnumMap<>(UssakSegahUsageClass.class);
        for (UssakSegahUsageClass usageClass : UssakSegahUsageClass.values()) {
            statistics.put(usageClass, StudyStatistics.from(events.stream()
                    .filter(UssakSegahEvent::included)
                    .filter(value -> value.usageClass() == usageClass)
                    .map(UssakSegahEvent::measurement).toList()));
        }
        StudyStatistics overall = StudyStatistics.from(events.stream()
                .filter(UssakSegahEvent::included)
                .map(UssakSegahEvent::measurement).toList());
        return new UssakSegahDirectionalStudyResult(makam.toString(), tonicHertz,
                segahCent, segahCent / AnalysisConfig.COMMA_CENT,
                tonicHertz * Math.pow(2.0, segahCent / 1200.0),
                events, statistics, overall);
    }

    private static UssakSegahEvent event(int index, FrameRegion region,
                                         List<PitchFrame> frames,
                                         List<PitchEvent> canonical,
                                         double tonicAbsoluteCent,
                                         double theoreticalSegahCent,
                                         double functionalLowerAbsoluteCent,
                                         double buselikBoundaryAbsoluteCent,
                                         double step) {
        List<PitchFrame> reliable = frames.subList(region.start(), region.end() + 1).stream()
                .filter(UssakSegahDirectionalAnalyzer::eligible).toList();
        double duration = frames.get(region.end()).timeSeconds()
                - frames.get(region.start()).timeSeconds() + step;
        double[] values = reliable.stream().mapToDouble(PitchFrame::absoluteCent).toArray();
        double[] central = timeSlice(values, .20, .80);
        double observed = median(central);
        double expected = tonicAbsoluteCent + theoreticalSegahCent;
        double entry = median(timeSlice(values, 0.0, .25)) - expected;
        double settled = observed - expected;
        double exit = median(timeSlice(values, .75, 1.0)) - expected;
        double stability = robustSpread(central);
        double vibratoSpan = percentile(values, .90) - percentile(values, .10);
        double averageConfidence = reliable.stream().mapToDouble(PitchFrame::confidence)
                .average().orElse(0.0);
        double stationaryFraction = stationaryFraction(reliable);
        double velocity = duration > 0.0
                ? Math.abs(median(timeSlice(values, .75, 1.0))
                - median(timeSlice(values, 0.0, .25))) / duration
                : Double.POSITIVE_INFINITY;
        boolean plateau = duration + 1.0e-9 >= MINIMUM_EVENT_SECONDS
                && reliable.size() >= 5
                && stationaryFraction >= .45
                && stability <= 1.10 * AnalysisConfig.COMMA_CENT
                && velocity <= 300.0;
        boolean functionallySegah = observed >= functionalLowerAbsoluteCent
                && observed < buselikBoundaryAbsoluteCent;

        double startSeconds = frames.get(region.start()).timeSeconds();
        double endSeconds = frames.get(region.end()).timeSeconds() + step;
        Anchor previous = nearestAnchor(canonical, startSeconds, true);
        Anchor next = nearestAnchor(canonical, endSeconds, false);
        Anchor beforePrevious = previous == Anchor.NONE ? Anchor.NONE
                : nearestAnchor(canonical, previous.startSeconds(), true);
        Anchor afterNext = next == Anchor.NONE ? Anchor.NONE
                : nearestAnchor(canonical, next.endSeconds(), false);
        boolean phraseFinal = followingSilence(frames, region.end(), .28, step);

        UssakSegahUsageClass usageClass = UssakSegahUsageClass.OTHER;
        boolean fullAscending = is(previous, "dugah") && is(next, "cargah");
        boolean extendedAscending = fullAscending && is(afterNext, "neva");
        boolean fullDescending = is(previous, "cargah") && is(next, "dugah");
        boolean extendedDescending = fullDescending && is(beforePrevious, "neva");
        boolean partialAscending = false;
        boolean partialDescending = false;
        if (is(previous, "dugah") && !is(next, "dugah")) partialAscending = true;
        if (!is(previous, "dugah") && is(next, "dugah")) partialDescending = true;
        if (is(previous, "dugah") && is(next, "dugah")) {
            double beforeGap = Math.max(0.0, startSeconds - previous.endSeconds());
            double afterGap = Math.max(0.0, next.startSeconds() - endSeconds);
            partialAscending = beforeGap + .12 < afterGap;
            partialDescending = afterGap + .12 < beforeGap;
        }
        boolean directHoldEvidence = duration + 1.0e-9 >= DIRECT_HOLD_SECONDS
                && stability <= MAXIMUM_HOLD_SPREAD_HC * AnalysisConfig.COMMA_CENT
                && (phraseFinal || duration >= 1.20);
        if (plateau && functionallySegah && fullAscending) {
            usageClass = UssakSegahUsageClass.ASCENDING_PHRASE;
        } else if (plateau && functionallySegah && fullDescending) {
            usageClass = UssakSegahUsageClass.DESCENDING_PHRASE;
        } else if (plateau && functionallySegah && directHoldEvidence) {
            usageClass = UssakSegahUsageClass.DIRECT_HOLD;
        } else if (plateau && functionallySegah && partialAscending) {
            usageClass = UssakSegahUsageClass.ASCENDING_PHRASE;
        } else if (plateau && functionallySegah && partialDescending) {
            usageClass = UssakSegahUsageClass.DESCENDING_PHRASE;
        }

        boolean fullPhrase = fullAscending || fullDescending;
        boolean extendedPhrase = extendedAscending || extendedDescending;
        double anchorConfidence = switch (usageClass) {
            case ASCENDING_PHRASE, DESCENDING_PHRASE -> fullPhrase
                    ? Math.min(previous.confidence(), next.confidence())
                    : (is(previous, "dugah") ? previous.confidence() : next.confidence()) * .85;
            case DIRECT_HOLD -> phraseFinal ? 1.0 : .80;
            case OTHER -> 0.0;
        };
        double gapScore = switch (usageClass) {
            case ASCENDING_PHRASE, DESCENDING_PHRASE -> {
                double beforeGap = previous == Anchor.NONE ? 0.0
                        : Math.max(0.0, startSeconds - previous.endSeconds());
                double afterGap = next == Anchor.NONE ? 0.0
                        : Math.max(0.0, next.startSeconds() - endSeconds);
                double widest = Math.max(beforeGap, afterGap);
                yield Math.max(0.0, 1.0 - widest / MAXIMUM_ANCHOR_GAP_SECONDS);
            }
            case DIRECT_HOLD -> 1.0;
            case OTHER -> 0.0;
        };
        double plateauScore = Math.min(1.0, stationaryFraction / .70)
                * Math.max(0.0, 1.0 - stability / (1.10 * AnalysisConfig.COMMA_CENT));
        double classificationConfidence = usageClass == UssakSegahUsageClass.OTHER ? 0.0
                : Math.min(1.0, .45 * averageConfidence + .25 * anchorConfidence
                + .15 * gapScore + .15 * plateauScore
                + (fullPhrase ? .04 : 0.0) + (extendedPhrase ? .03 : 0.0));

        MelodicDirection direction = switch (usageClass) {
            case ASCENDING_PHRASE -> MelodicDirection.ASCENDING;
            case DESCENDING_PHRASE -> MelodicDirection.DESCENDING;
            case DIRECT_HOLD, OTHER -> MelodicDirection.LEVEL;
        };
        EventType type = duration < MINIMUM_EVENT_SECONDS ? EventType.SHORT_CONTACT
                : usageClass == UssakSegahUsageClass.DIRECT_HOLD ? EventType.CADENTIAL_HOLD
                : duration >= .35 ? EventType.SUSTAINED : EventType.PASSING;
        double observedRelative = observed - tonicAbsoluteCent;
        StudyPitchEvent measurement = new StudyPitchEvent(index, "segah", "Segâh",
                startSeconds, endSeconds, duration, PitchTracker.centToHertz(observed),
                theoreticalSegahCent, observedRelative,
                observedRelative / AnalysisConfig.COMMA_CENT,
                observed - expected, (observed - expected) / AnalysisConfig.COMMA_CENT,
                entry, settled, exit, stability, vibratoSpan,
                averageConfidence, direction, direction, direction, type, phraseFinal);

        Anchor localTonic = is(previous, "dugah") ? previous
                : is(next, "dugah") ? next : Anchor.NONE;
        double localTonicHertz = localTonic == Anchor.NONE ? Double.NaN
                : PitchTracker.centToHertz(localTonic.observedCent());
        double localDeviation = localTonic == Anchor.NONE ? Double.NaN
                : observed - (localTonic.observedCent() + theoreticalSegahCent);
        boolean included = usageClass != UssakSegahUsageClass.OTHER
                && plateau
                && averageConfidence + 1.0e-9 >= PRIMARY_STATISTICS_CONFIDENCE
                && stability <= MAXIMUM_INCLUDED_SPREAD_HC * AnalysisConfig.COMMA_CENT
                && classificationConfidence >= .60;
        String exclusion = exclusionReason(usageClass, duration, reliable.size(), plateau,
                functionallySegah,
                averageConfidence, stability, classificationConfidence);
        String path = usageClass.defaultPathLabel();
        if (extendedAscending) {
            path = "Dügâh→Segâh→Çargâh→Nevâ";
        } else if (extendedDescending) {
            path = "Nevâ→Çargâh→Segâh→Dügâh";
        } else if (usageClass == UssakSegahUsageClass.ASCENDING_PHRASE && !fullAscending) {
            path = "Dügâh→Segâh (üst çıpalar doğrulanmadı)";
        } else if (usageClass == UssakSegahUsageClass.DESCENDING_PHRASE && !fullDescending) {
            path = "Segâh→Dügâh (üst çıpalar doğrulanmadı)";
        } else if (usageClass == UssakSegahUsageClass.OTHER) {
            path = anchorName(previous) + "→Segâh→" + anchorName(next);
        }
        return new UssakSegahEvent(index, measurement, usageClass,
                previous.key(), previous.name(), next.key(), next.name(), path,
                classificationConfidence, localTonicHertz, localDeviation,
                included, included ? "" : exclusion);
    }

    private static String exclusionReason(UssakSegahUsageClass usageClass, double duration,
                                          int frameCount, boolean plateau,
                                          boolean functionallySegah,
                                          double confidence, double spread,
                                          double classificationConfidence) {
        if (duration + 1.0e-9 < MINIMUM_EVENT_SECONDS || frameCount < 5) {
            return "Kısa temas; ana ortalamaya alınmadı";
        }
        if (!plateau) return "Yerleşik Segâh gövdesi yok; glissando/geçiş adayı";
        if (!functionallySegah) {
            return "Dügâh–Bûselik açık aralığında, fakat Segâh işlev bandının dışında";
        }
        if (usageClass == UssakSegahUsageClass.OTHER) {
            return "Dügâh yön çıpası eksik veya iki yönde çelişkili";
        }
        if (confidence + 1.0e-9 < PRIMARY_STATISTICS_CONFIDENCE) {
            return "F0 güveni %65'in altında; denetim kanıtı";
        }
        if (spread > MAXIMUM_INCLUDED_SPREAD_HC * AnalysisConfig.COMMA_CENT) {
            return "Perde gövdesi ana istatistik için fazla değişken";
        }
        if (classificationConfidence < .60) return "Cümle sınıflandırma güveni yetersiz";
        return "Araştırma ölçütlerini karşılamadı";
    }

    private static List<FrameRegion> candidateRegions(List<PitchFrame> frames,
                                                       double lower, double upper,
                                                       double step) {
        if (frames.isEmpty()) return List.of();
        int allowedWeakGap = Math.max(1, (int) Math.round(.060 / step));
        List<FrameRegion> result = new ArrayList<>();
        int index = 0;
        while (index < frames.size()) {
            if (!inside(frames.get(index), lower, upper)) {
                index++;
                continue;
            }
            int start = index;
            int lastInside = index;
            int weakGap = 0;
            index++;
            while (index < frames.size()) {
                PitchFrame frame = frames.get(index);
                if (inside(frame, lower, upper)) {
                    PitchFrame previous = frames.get(lastInside);
                    if (eligible(previous)
                            && Math.abs(frame.absoluteCent() - previous.absoluteCent())
                            > MAXIMUM_WITHIN_REGION_FRAME_STEP_HC * AnalysisConfig.COMMA_CENT) {
                        break;
                    }
                    lastInside = index;
                    weakGap = 0;
                } else if ((!frame.voiced() || frame.confidence() < MINIMUM_FRAME_CONFIDENCE)
                        && weakGap < allowedWeakGap) {
                    weakGap++;
                } else {
                    break;
                }
                index++;
            }
            result.add(new FrameRegion(start, lastInside));
            index = Math.max(index, lastInside + 1);
        }
        return List.copyOf(result);
    }

    private static boolean inside(PitchFrame frame, double lower, double upper) {
        // Strict inequalities implement the requested open Dügâh–Bûselik range.
        return eligible(frame) && frame.absoluteCent() > lower + 1.0e-9
                && frame.absoluteCent() < upper - 1.0e-9;
    }

    private static boolean eligible(PitchFrame frame) {
        return frame.voiced() && Double.isFinite(frame.confidence())
                && frame.confidence() >= MINIMUM_FRAME_CONFIDENCE;
    }

    private static Anchor nearestAnchor(List<PitchEvent> events, double boundary,
                                        boolean previous) {
        Anchor best = Anchor.NONE;
        double bestGap = Double.POSITIVE_INFINITY;
        for (PitchEvent value : events) {
            if (!value.noteKey().equals("dugah") && !value.noteKey().equals("cargah")
                    && !value.noteKey().equals("neva")) continue;
            PitchOccurrence occurrence = value.occurrence();
            if (occurrence.confidence() < MINIMUM_FRAME_CONFIDENCE
                    || occurrence.durationSeconds() < .060 - 1.0e-9) continue;
            double gap;
            if (previous) {
                if (occurrence.endSeconds() > boundary + .041) continue;
                gap = Math.max(0.0, boundary - occurrence.endSeconds());
            } else {
                if (occurrence.startSeconds() < boundary - .041) continue;
                gap = Math.max(0.0, occurrence.startSeconds() - boundary);
            }
            if (gap <= MAXIMUM_ANCHOR_GAP_SECONDS + 1.0e-9 && gap < bestGap) {
                best = new Anchor(value.noteKey(), value.noteName(),
                        occurrence.startSeconds(), occurrence.endSeconds(),
                        occurrence.observedCent(), occurrence.confidence());
                bestGap = gap;
            }
        }
        return best;
    }

    private static boolean is(Anchor anchor, String key) {
        return anchor != null && key.equals(anchor.key());
    }

    private static String anchorName(Anchor anchor) {
        return anchor == Anchor.NONE || anchor.name().isBlank() ? "?" : anchor.name();
    }

    private static boolean followingSilence(List<PitchFrame> frames, int end,
                                            double seconds, double step) {
        int needed = Math.max(1, (int) Math.ceil(seconds / step));
        if (end + needed >= frames.size()) return true;
        for (int i = end + 1; i <= end + needed; i++) {
            if (frames.get(i).voiced() && frames.get(i).confidence() >= MINIMUM_FRAME_CONFIDENCE) {
                return false;
            }
        }
        return true;
    }

    private static double stationaryFraction(List<PitchFrame> frames) {
        if (frames.size() < 2) return 0.0;
        int stationary = 0;
        int comparisons = 0;
        double threshold = .35 * AnalysisConfig.COMMA_CENT;
        for (int i = 1; i < frames.size(); i++) {
            double delta = Math.abs(frames.get(i).absoluteCent()
                    - frames.get(i - 1).absoluteCent());
            if (delta <= threshold) stationary++;
            comparisons++;
        }
        return comparisons == 0 ? 0.0 : stationary / (double) comparisons;
    }

    private static double[] timeSlice(double[] values, double from, double to) {
        if (values.length == 0) return new double[0];
        int start = Math.max(0, Math.min(values.length - 1,
                (int) Math.floor(values.length * from)));
        int end = Math.max(start + 1, Math.min(values.length,
                (int) Math.ceil(values.length * to)));
        return Arrays.copyOfRange(values, start, end);
    }

    private static double median(double[] values) {
        if (values.length == 0) return Double.NaN;
        double[] copy = values.clone();
        Arrays.sort(copy);
        return copy.length % 2 == 0
                ? (copy[copy.length / 2 - 1] + copy[copy.length / 2]) / 2.0
                : copy[copy.length / 2];
    }

    private static double robustSpread(double[] values) {
        double center = median(values);
        return median(Arrays.stream(values).map(value -> Math.abs(value - center)).toArray())
                * 1.4826;
    }

    private static double percentile(double[] values, double percentile) {
        if (values.length == 0) return Double.NaN;
        double[] copy = values.clone();
        Arrays.sort(copy);
        double index = percentile * (copy.length - 1);
        int lower = (int) Math.floor(index);
        int upper = Math.min(copy.length - 1, lower + 1);
        return copy[lower] + (copy[upper] - copy[lower]) * (index - lower);
    }

    private static double frameStep(List<PitchFrame> frames) {
        return frames.size() > 1
                ? Math.max(.001, frames.get(1).timeSeconds() - frames.get(0).timeSeconds())
                : .020;
    }

    private record FrameRegion(int start, int end) { }

    private record Anchor(String key, String name, double startSeconds,
                          double endSeconds, double observedCent, double confidence) {
        private static final Anchor NONE = new Anchor("", "", Double.NaN, Double.NaN,
                Double.NaN, 0.0);
    }
}
