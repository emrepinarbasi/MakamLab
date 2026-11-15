package com.makambox.studio.study;

import com.makambox.studio.model.AnalysisConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares temporally ordered first-four-pitch paths in the two melodic
 * directions.  It deliberately uses the same profile candidates selected by
 * {@link MakamProfileComparator}; the settings screen therefore controls both
 * the non-directional and directional report layers.
 */
public final class FirstFourDirectionalAnalyzer {
    private static final double MAX_GAP_SECONDS = 0.80;
    private static final double MAX_SHORT_SPLIT_SECONDS = 0.12;
    private static final double MAX_MELODIC_LEAP_CENT = 500.0;
    private static final double SAME_PITCH_TOLERANCE_CENT = AnalysisConfig.COMMA_CENT;
    private static final double TIME_REGION_SECONDS = 30.0;
    /** Reporting boundary used by the non-directional first-four comparison too. */
    private static final double TRUSTED_SIMILARITY_PERCENT = 80.0;
    private static final List<List<Integer>> ASCENDING_PATTERNS = List.of(
            // Long paths come first so a complete phrase is not fragmented into
            // several shorter paths by the greedy matcher.
            List.of(1, 2, 3, 4),
            List.of(1, 2, 3), List.of(2, 3, 4), List.of(1, 3, 4),
            List.of(1, 2), List.of(2, 3), List.of(3, 4),
            List.of(1, 3), List.of(1, 4));
    private static final List<List<Integer>> DESCENDING_PATTERNS = List.of(
            List.of(4, 3, 2, 1),
            List.of(4, 3, 2), List.of(3, 2, 1), List.of(4, 2, 1),
            List.of(4, 3), List.of(3, 2), List.of(2, 1),
            List.of(4, 2), List.of(4, 1));

    public Result compare(List<StudyPitchEvent> events, MakamProfileComparison comparison) {
        if (comparison == null) return new Result(List.of(), List.of());
        List<StudyPitchEvent> orderedEvents = events.stream()
                .sorted(Comparator.comparingDouble(StudyPitchEvent::startSeconds)
                        .thenComparingDouble(StudyPitchEvent::endSeconds))
                .toList();

        // Directional phrases are extracted once from observed F0, before any
        // theoretical profile is consulted.  Every candidate is therefore
        // evaluated on exactly the same time spans and events.
        List<RawNode> commonNodes = mapCommonNodes(orderedEvents,
                -AnalysisConfig.COMMA_CENT, commonUpperCent(comparison));
        List<List<RawNode>> ascendingRuns = directionalRuns(commonNodes, true);
        List<List<RawNode>> descendingRuns = directionalRuns(commonNodes, false);

        List<Candidate> ascending = new ArrayList<>();
        List<Candidate> descending = new ArrayList<>();
        for (MakamProfileComparison.Candidate profile : comparison.candidates()) {
            List<MakamProfileComparison.DegreeMatch> degrees = profile.degreeMatches().stream()
                    .filter(value -> value.degree() >= 1 && value.degree() <= 4)
                    .sorted(Comparator.comparingInt(MakamProfileComparison.DegreeMatch::degree))
                    .toList();
            if (degrees.size() != 4) continue;
            ascending.add(score(profile, ascendingRuns, degrees, ASCENDING_PATTERNS));
            descending.add(score(profile, descendingRuns, degrees, DESCENDING_PATTERNS));
        }
        return new Result(rank(ascending), rank(descending));
    }

    private static double commonUpperCent(MakamProfileComparison comparison) {
        try {
            return FirstFourProfileReferenceCatalog.maximumFourthDegreeCent(
                    comparison.referenceSystem()) + AnalysisConfig.COMMA_CENT;
        } catch (RuntimeException unavailableSyntheticSystem) {
            return comparison.candidates().stream()
                    .flatMap(value -> value.degreeMatches().stream())
                    .filter(value -> value.degree() == 4)
                    .mapToDouble(MakamProfileComparison.DegreeMatch::theoreticalCent)
                    .max().orElse(600.0) + AnalysisConfig.COMMA_CENT;
        }
    }

    private static List<RawNode> mapCommonNodes(List<StudyPitchEvent> events,
                                                double lowerCent, double upperCent) {
        List<RawNode> nodes = new ArrayList<>();
        for (StudyPitchEvent event : events) {
            if (!directionalEvidence(event)) {
                // A rejected transient is missing evidence, not proof of a
                // phrase boundary.  Silently bridge such contacts; otherwise a
                // single low-confidence 20 ms frame would erase a perfectly
                // ordered path on both sides.  A longer rejected event remains
                // a boundary, as do reliable pitches outside the profile domain
                // below.
                if (event.measurable()) addSeparator(nodes);
                continue;
            }
            double observedCent = foldOctave(event.observedRelativeCent());
            if (observedCent <= lowerCent + 1.0e-9
                    || observedCent >= upperCent - 1.0e-9) {
                addSeparator(nodes);
                continue;
            }
            double weight = MakamProfileComparator.firstFourEvidenceWeight(
                    event.durationSeconds(), event.confidence(), event.stabilityCent());
            RawNode next = new RawNode(event.startSeconds(), event.endSeconds(), observedCent,
                    event.observedRelativeCent(), Math.max(1.0e-9, weight), 1, false);
            if (!nodes.isEmpty() && !nodes.get(nodes.size() - 1).separator()) {
                RawNode previous = nodes.get(nodes.size() - 1);
                double gap = next.startSeconds() - previous.endSeconds();
                if (gap > MAX_GAP_SECONDS
                        || Math.abs(next.sourceRelativeCent()
                        - previous.sourceRelativeCent()) > MAX_MELODIC_LEAP_CENT) {
                    addSeparator(nodes);
                }
                else if (Math.abs(previous.observedRelativeCent()
                        - next.observedRelativeCent()) <= SAME_PITCH_TOLERANCE_CENT
                        && gap <= MAX_SHORT_SPLIT_SECONDS) {
                    nodes.set(nodes.size() - 1, previous.merge(next));
                    if (event.phraseFinal()) addSeparator(nodes);
                    continue;
                }
            }
            nodes.add(next);
            if (event.phraseFinal()) addSeparator(nodes);
        }
        return List.copyOf(nodes);
    }

    private static double foldOctave(double value) {
        return value - 1200.0 * Math.floor((value + 600.0) / 1200.0);
    }

    private static boolean directionalEvidence(StudyPitchEvent event) {
        // A 20-80 ms contact is too short for the sustained-pitch summary, but
        // it can still be the indispensable middle degree of an ordered melodic
        // path.  Direction detection therefore keeps short contacts when their
        // F0 confidence and stability pass the same quality gates.  Duration is
        // retained in the statistical weight instead of being used as a binary
        // deletion rule.
        return MakamProfileComparator.reliableDirectionalFirstFourEvidence(
                event.durationSeconds(), event.confidence(), event.stabilityCent());
    }

    private static MappedDegree mapDegree(RawNode event,
                                          MakamProfileComparison.DegreeMatch match) {
        return new MappedDegree(match,
                event.observedRelativeCent() - match.theoreticalCent());
    }

    private static void addSeparator(List<RawNode> nodes) {
        if (!nodes.isEmpty() && !nodes.get(nodes.size() - 1).separator()) {
            nodes.add(RawNode.boundary());
        }
    }

    private static Candidate score(MakamProfileComparison.Candidate profile,
                                   List<List<RawNode>> commonRuns,
                                   List<MakamProfileComparison.DegreeMatch> degrees,
                                   List<List<Integer>> patterns) {
        Map<String, Integer> patternCounts = new LinkedHashMap<>();
        patterns.forEach(value -> patternCounts.put(label(value), 0));
        List<Node> evidence = new ArrayList<>();
        Set<Integer> timeRegions = new LinkedHashSet<>();
        double matchedWeight = 0.0;
        int phrases = commonRuns.size();
        for (List<RawNode> rawRun : commonRuns) {
            List<Node> run = rawRun.stream().map(raw -> mapNode(raw, degrees)).toList();
            evidence.addAll(run);
            timeRegions.add((int) Math.floor(rawRun.get(0).startSeconds()
                    / TIME_REGION_SECONDS));
            List<DegreeGroup> groups = degreeGroups(run);
            for (int index = 0; index < groups.size();) {
                boolean matched = false;
                for (List<Integer> pattern : patterns) {
                    if (!matches(groups, index, pattern)) continue;
                    String label = label(pattern);
                    patternCounts.put(label, patternCounts.get(label) + 1);
                    for (int offset = 0; offset < pattern.size(); offset++) {
                        matchedWeight += groups.get(index + offset).nodes().stream()
                                .filter(value -> value.degree() > 1)
                                .mapToDouble(Node::weight).sum();
                    }
                    index += pattern.size();
                    matched = true;
                    break;
                }
                if (!matched) index++;
            }
        }

        List<Node> scoredEvidence = evidence.stream().filter(value -> value.degree() > 1).toList();
        if (scoredEvidence.isEmpty()) {
            return new Candidate(0, profile.makamName(), 0, 0, 0,
                    immutableCounts(patternCounts), EvidenceStatus.INSUFFICIENT,
                    Double.NaN, 0.0, Double.NaN, Double.NaN, Double.NaN);
        }
        double totalWeight = scoredEvidence.stream().mapToDouble(Node::weight).sum();
        double closeness = scoredEvidence.stream().mapToDouble(value -> value.weight()
                * Math.exp(-Math.abs(value.residualCent()) / AnalysisConfig.COMMA_CENT)).sum()
                / Math.max(1.0e-12, totalWeight) * 100.0;
        Set<Integer> representedDegrees = new LinkedHashSet<>();
        scoredEvidence.forEach(value -> representedDegrees.add(value.degree()));
        double coverage = representedDegrees.size() * 100.0 / 3.0;
        double compatibility = Math.min(1.0, matchedWeight / Math.max(1.0e-12, totalWeight));
        double similarity = closeness * (.55 + .45 * coverage / 100.0) * compatibility;
        double median = weightedPercentile(scoredEvidence, .50);
        double p90 = weightedPercentile(scoredEvidence, .90);
        double meanSigned = scoredEvidence.stream().mapToDouble(value ->
                value.residualCent() * value.weight()).sum() / Math.max(1.0e-12, totalWeight);
        int eventCount = commonRuns.stream().flatMap(List::stream)
                .mapToInt(RawNode::eventCount).sum();
        EvidenceStatus status = phrases < 2 || eventCount < 6
                ? EvidenceStatus.INSUFFICIENT
                : phrases < 3 || timeRegions.size() < 2
                ? EvidenceStatus.LIMITED : EvidenceStatus.COMPARABLE;
        // Keep a computable percentage visible even when the amount or spread
        // of evidence is insufficient for a strong conclusion.  The status is
        // reported separately and prevents a limited sample from being mistaken
        // for robust evidence.  Only a true absence of matching paths is blank.
        return new Candidate(0, profile.makamName(), phrases, eventCount, timeRegions.size(),
                immutableCounts(patternCounts), status, similarity, coverage,
                median, p90, meanSigned);
    }

    private static Node mapNode(RawNode raw,
                                List<MakamProfileComparison.DegreeMatch> degrees) {
        MappedDegree nearest = degrees.stream().map(value -> mapDegree(raw, value))
                .min(Comparator.comparingDouble(value -> Math.abs(value.residualCent())))
                .orElseThrow();
        return new Node(nearest.match().degree(), raw.startSeconds(), raw.endSeconds(),
                raw.observedRelativeCent(), nearest.residualCent(), raw.weight(),
                raw.eventCount(), false);
    }

    private static List<List<RawNode>> directionalRuns(List<RawNode> nodes,
                                                       boolean ascending) {
        List<List<RawNode>> result = new ArrayList<>();
        List<RawNode> current = new ArrayList<>();
        for (RawNode value : nodes) {
            if (value.separator()) {
                finishRun(result, current);
                continue;
            }
            if (!current.isEmpty() && !directionalStep(current.get(current.size() - 1), value,
                    ascending)) {
                finishRun(result, current);
            }
            current.add(value);
        }
        finishRun(result, current);
        return result;
    }

    private static boolean directionalStep(RawNode previous, RawNode next,
                                           boolean ascending) {
        double motion = next.sourceRelativeCent() - previous.sourceRelativeCent();
        if (Math.abs(motion) > MAX_MELODIC_LEAP_CENT) return false;
        if (Math.abs(motion) <= SAME_PITCH_TOLERANCE_CENT) return true;
        if (ascending) return motion > 0.0;
        return motion < 0.0;
    }

    private static void finishRun(List<List<RawNode>> result, List<RawNode> current) {
        if (directionalMovement(current)) result.add(List.copyOf(current));
        current.clear();
    }

    private static boolean directionalMovement(List<RawNode> values) {
        if (values.size() < 2) return false;
        double minimum = values.stream().mapToDouble(RawNode::observedRelativeCent)
                .min().orElse(0.0);
        double maximum = values.stream().mapToDouble(RawNode::observedRelativeCent)
                .max().orElse(0.0);
        return maximum - minimum > SAME_PITCH_TOLERANCE_CENT + 1.0e-9;
    }

    private static List<DegreeGroup> degreeGroups(List<Node> run) {
        List<DegreeGroup> groups = new ArrayList<>();
        for (Node value : run) {
            if (!groups.isEmpty() && groups.get(groups.size() - 1).degree() == value.degree()) {
                DegreeGroup previous = groups.remove(groups.size() - 1);
                List<Node> repeated = new ArrayList<>(previous.nodes());
                repeated.add(value);
                groups.add(new DegreeGroup(value.degree(), List.copyOf(repeated)));
            } else {
                groups.add(new DegreeGroup(value.degree(), List.of(value)));
            }
        }
        return List.copyOf(groups);
    }

    private static boolean matches(List<DegreeGroup> groups, int start, List<Integer> pattern) {
        if (start + pattern.size() > groups.size()) return false;
        for (int offset = 0; offset < pattern.size(); offset++) {
            if (groups.get(start + offset).degree() != pattern.get(offset)) return false;
        }
        return true;
    }

    private static List<Candidate> rank(List<Candidate> values) {
        List<Candidate> ordered = values.stream()
                // Match the ordinary first-four report: scores at or above the
                // explicit 80% reporting boundary come first, followed by the
                // remaining finite scores in descending order.  Profiles with
                // no computable directional evidence remain visible at the end.
                .sorted(Comparator.comparingInt(FirstFourDirectionalAnalyzer::rankingBand)
                        .thenComparing(Comparator.comparingDouble((Candidate value) ->
                                Double.isFinite(value.scorePercent())
                                        ? value.scorePercent() : -1.0).reversed())
                        .thenComparingInt(value -> value.evidenceStatus().rank())
                        .thenComparing(Candidate::profileName))
                .toList();
        List<Candidate> result = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            Candidate value = ordered.get(index);
            result.add(new Candidate(index + 1, value.profileName(), value.phraseCount(),
                    value.evidenceEventCount(), value.timeRegionCount(), value.patternCounts(),
                    value.evidenceStatus(), value.scorePercent(), value.coveragePercent(),
                    value.medianAbsoluteErrorCent(), value.p90AbsoluteErrorCent(),
                    value.meanSignedErrorCent()));
        }
        return List.copyOf(result);
    }

    private static int rankingBand(Candidate value) {
        if (!Double.isFinite(value.scorePercent())) return 2;
        return value.scorePercent() + 1.0e-9 >= TRUSTED_SIMILARITY_PERCENT
                && value.evidenceStatus() != EvidenceStatus.INSUFFICIENT ? 0 : 1;
    }

    private static double weightedPercentile(List<Node> values, double percentile) {
        List<Node> ordered = values.stream().sorted(Comparator.comparingDouble(value ->
                Math.abs(value.residualCent()))).toList();
        double total = ordered.stream().mapToDouble(Node::weight).sum();
        double threshold = total * percentile;
        double cumulative = 0.0;
        for (Node value : ordered) {
            cumulative += value.weight();
            if (cumulative + 1.0e-12 >= threshold) return Math.abs(value.residualCent());
        }
        return Math.abs(ordered.get(ordered.size() - 1).residualCent());
    }

    private static String label(List<Integer> pattern) {
        return pattern.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining("→"));
    }

    private static Map<String, Integer> immutableCounts(Map<String, Integer> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public enum EvidenceStatus {
        COMPARABLE(0), LIMITED(1), INSUFFICIENT(2);

        private final int rank;
        EvidenceStatus(int rank) { this.rank = rank; }
        int rank() { return rank; }
    }

    public record Result(List<Candidate> ascending, List<Candidate> descending) {
        public Result {
            ascending = List.copyOf(ascending);
            descending = List.copyOf(descending);
        }
    }

    public record Candidate(int rank, String profileName, int phraseCount,
                            int evidenceEventCount, int timeRegionCount,
                            Map<String, Integer> patternCounts, EvidenceStatus evidenceStatus,
                            double scorePercent, double coveragePercent,
                            double medianAbsoluteErrorCent, double p90AbsoluteErrorCent,
                            double meanSignedErrorCent) {
        public Candidate {
            patternCounts = immutableCounts(patternCounts);
        }
    }

    private record Node(int degree, double startSeconds, double endSeconds,
                        double observedRelativeCent, double residualCent,
                        double weight, int eventCount,
                        boolean separator) {
        private Node(int degree, double startSeconds, double endSeconds,
                     double observedRelativeCent, double residualCent,
                     double weight, int eventCount) {
            this(degree, startSeconds, endSeconds, observedRelativeCent,
                    residualCent, weight, eventCount, false);
        }

        private static Node boundary() {
            return new Node(0, Double.NaN, Double.NaN, Double.NaN,
                    0.0, 0.0, 0, true);
        }

        private Node merge(Node next) {
            double combined = weight + next.weight;
            return new Node(degree, startSeconds, next.endSeconds,
                    (observedRelativeCent * weight + next.observedRelativeCent * next.weight)
                            / combined,
                    (residualCent * weight + next.residualCent * next.weight) / combined,
                    combined, eventCount + next.eventCount);
        }
    }

    private record MappedDegree(MakamProfileComparison.DegreeMatch match,
                                double residualCent) { }

    private record DegreeGroup(int degree, List<Node> nodes) { }

    private record RawNode(double startSeconds, double endSeconds,
                           double observedRelativeCent, double sourceRelativeCent, double weight,
                           int eventCount, boolean separator) {
        private static RawNode boundary() {
            return new RawNode(Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    0.0, 0, true);
        }

        private RawNode merge(RawNode next) {
            double combined = weight + next.weight;
            return new RawNode(startSeconds, next.endSeconds,
                    (observedRelativeCent * weight
                            + next.observedRelativeCent * next.weight) / combined,
                    (sourceRelativeCent * weight
                            + next.sourceRelativeCent * next.weight) / combined,
                    combined, eventCount + next.eventCount, false);
        }
    }
}
