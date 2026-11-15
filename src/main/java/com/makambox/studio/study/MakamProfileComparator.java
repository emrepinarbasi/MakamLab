package com.makambox.studio.study;

import com.makambox.studio.legacy.TuningSystemCatalog;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchOccurrence;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compares an observed, tonic-aligned pitch inventory with theoretical makam
 * interval profiles.  The result deliberately describes interval similarity;
 * it must not be presented as automatic makam identification.
 */
public final class MakamProfileComparator {
    public static final String DEFAULT_REFERENCE_SYSTEM = "Arel_Ezgi_Uzdilek";
    public static final double MAXIMUM_STABLE_SPREAD_HC = 0.55;
    public static final double MINIMUM_CONFIDENCE = 0.65;
    public static final double MINIMUM_EVENT_SECONDS = 0.10;
    public static final double MINIMUM_SHORT_MEASURABLE_SECONDS = 0.060;
    public static final double MINIMUM_SHORT_CONFIDENCE = 0.75;
    /**
     * Twenty milliseconds is one analysis hop in the standard study
     * configuration. Contacts in the 20-60 ms band remain available to the
     * event audit and directional-path layer, but are not direct observations
     * in the non-directional first-four mean.
     */
    public static final double MINIMUM_SECONDARY_EVENT_SECONDS = 0.020;
    private static final double CLUSTER_GAP_CENT = 32.0;
    private static final double EDGE_WRAP_CENT = 25.0;
    private static final double HUBER_DELTA_CENT = AnalysisConfig.COMMA_CENT;
    private static final double MAXIMUM_PHRASE_GAP_SECONDS = 0.80;
    private static final double MAXIMUM_REPEAT_MERGE_GAP_SECONDS = 0.12;
    private static final double MAXIMUM_PHRASE_LEAP_CENT = 500.0;
    private static final double SAME_PITCH_TOLERANCE_CENT = AnalysisConfig.COMMA_CENT;

    private final Map<String, TuningSystemCatalog.SystemData> systems;

    public MakamProfileComparator() {
        systems = new TuningSystemCatalog().loadBuiltIns();
    }

    public MakamProfileComparison compare(List<PitchEvent> events, double tonicHertz,
                                          String selectedMakam) {
        return compare(events, tonicHertz, selectedMakam, DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.defaults());
    }

    public MakamProfileComparison compare(List<PitchEvent> events, double tonicHertz,
                                          String selectedMakam, String referenceSystem) {
        return compare(events, tonicHertz, selectedMakam, referenceSystem,
                FirstFourProfileSelection.defaults());
    }

    public MakamProfileComparison compare(List<PitchEvent> events, double tonicHertz,
                                          String selectedMakam, String referenceSystem,
                                          FirstFourProfileSelection profileSelection) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(selectedMakam, "selectedMakam");
        profileSelection = Objects.requireNonNullElseGet(profileSelection,
                FirstFourProfileSelection::defaults);
        TuningSystemCatalog.SystemData system = Objects.requireNonNull(systems.get(referenceSystem),
                "Unknown reference system: " + referenceSystem);
        if (!Double.isFinite(tonicHertz) || tonicHertz <= 0.0) {
            throw new IllegalArgumentException("tonicHertz must be finite and positive");
        }

        double tonicCent = hertzToCent(tonicHertz);
        // The primary comparison references share the same fourth-degree
        // evidence domain. Optional rows are a display preference and must
        // not change the observations used by the default comparison.
        // Optional profile rows are a display preference and must not expand
        // the common evidence domain or alter an existing primary score.
        double firstFourUpperCent =
                FirstFourProfileReferenceCatalog.coreFourthDegreeCent(system);
        // The global inventory is descriptive.  It deliberately contains all
        // reliable first-four-domain contacts, including pitches that do not
        // belong to a tonic/fourth-anchored ordered phrase.
        List<Observation> observations = acceptedObservations(events, tonicCent,
                firstFourUpperCent);
        List<Cluster> clusters = cluster(observations);
        DegreeBands degreeBands = degreeBands(system);
        PhraseSet phraseSet = phraseEvidence(events, tonicCent, firstFourUpperCent,
                degreeBands);
        // The ranked, non-directional first-four comparison uses the complete
        // candidate-independent inventory.  Ordered phrase evidence remains a
        // separate diagnostic layer: an incomplete phrase path must not make
        // the inventory ranking disappear, and the inventory must never be
        // presented as proof that a complete melodic path occurred.
        List<DegreeEvidence> inventoryDegreeEvidence = inventoryDegreeEvidence(
                observations, degreeBands);
        List<DegreeEvidence> phraseDegreeEvidence = commonDegreeEvidence(
                phraseSet.scoredEvents(), degreeBands);
        int timeRegions = representedPhraseTimeRegions(phraseSet.scoredEvents());
        double totalDuration = observations.stream().mapToDouble(Observation::duration).sum();
        // A similarity ranking is reportable whenever at least one
        // discriminating degree is present in the global first-four
        // inventory. Phrase completeness is reported independently below.
        boolean sufficient = inventoryDegreeEvidence.stream().anyMatch(DegreeEvidence::observed);

        List<FirstFourProfileReferenceCatalog.ReferenceProfile> references =
                FirstFourProfileReferenceCatalog.references(system, profileSelection).stream()
                        // Bûselik remains a valid pitch name throughout the
                        // theory and event layers, but is deliberately not a
                        // first-four comparison candidate in MakamLab.
                        .filter(reference -> !sameName(reference.displayName(), "Bûselik")
                                && !sameName(reference.sourceProfile(), "Buselik"))
                        .toList();
        List<ScoredCandidate> scored = new ArrayList<>();
        for (FirstFourProfileReferenceCatalog.ReferenceProfile reference : references) {
            boolean selected = sameName(reference.displayName(), selectedMakam)
                    || sameName(reference.sourceProfile(), selectedMakam)
                    || selectedProfileFamilyMatches(reference.displayName(), selectedMakam);
            scored.add(score(reference.order(), reference.displayName(),
                    List.of(reference.displayName()), reference.centArray(),
                    inventoryDegreeEvidence, selected));
        }
        // The threshold is an explicit reporting boundary: strong candidates
        // are always shown first; low-similarity candidates remain visible for
        // audit instead of being silently discarded.
        scored.sort(Comparator.comparing((ScoredCandidate value) -> value.score() < 80.0)
                .thenComparing(Comparator.comparingDouble(ScoredCandidate::score).reversed())
                .thenComparingInt(ScoredCandidate::referenceOrder));

        List<MakamProfileComparison.Candidate> candidates = new ArrayList<>(scored.size());
        for (int i = 0; i < scored.size(); i++) {
            ScoredCandidate value = scored.get(i);
            candidates.add(new MakamProfileComparison.Candidate(i + 1, value.name(), value.groupNames(),
                    value.selected(), value.score(), value.robustError(), value.medianError(),
                    value.p90Error(), value.withinOneHc(), value.coverage(), value.matchedDegrees(),
                    value.degreeCount(), value.matches()));
        }

        MakamProfileComparison.PhraseEvidence phraseEvidence = publicPhraseEvidence(
                phraseSet, phraseDegreeEvidence);
        String summary;
        if (!sufficient) {
            summary = "Karar ile dördüncü derece arasında karşılaştırılabilir kararlı perde envanteri bulunmadı.";
        } else if (phraseEvidence.status()
                == MakamProfileComparison.PhraseEvidenceStatus.INSUFFICIENT) {
            summary = "Benzerlik sıralaması ortak genel perde envanterinden hesaplandı; sıralı dörtlü cümle kanıtı ayrıca yetersiz olarak işaretlendi.";
        } else if (phraseEvidence.status()
                == MakamProfileComparison.PhraseEvidenceStatus.PARTIAL_MIXED) {
            summary = "Benzerlik sıralaması ortak genel perde envanterinden hesaplandı; sıralı dörtlü cümle kanıtı ayrıca kısmi/karma olarak işaretlendi.";
        } else if (candidates.size() > 1
                && Math.abs(candidates.get(0).scorePercent() - candidates.get(1).scorePercent()) < 3.0) {
            summary = "En yakın profiller arasındaki fark küçüktür; aralık profili tek başına ayırt edici değildir.";
        } else {
            summary = "Sıralama karara hizalı aralık benzerliğini gösterir; seyir veya makam teşhisi değildir.";
        }

        return new MakamProfileComparison(referenceSystem, selectedMakam, sufficient, summary,
                observations.size(), clusters.size(), totalDuration, timeRegions,
                clusters.stream().map(Cluster::asPublic).toList(), candidates, profileSelection,
                phraseEvidence);
    }

    private static boolean selectedProfileFamilyMatches(String referenceName, String selectedMakam) {
        return FirstFourMakamGroupCatalog.canonicalLabel(List.of(), List.of(selectedMakam))
                .map(label -> sameName(label, referenceName))
                .orElse(false);
    }

    private static List<Observation> acceptedObservations(List<PitchEvent> events,
                                                          double tonicCent,
                                                          double firstFourUpperCent) {
        List<Observation> result = new ArrayList<>();
        for (PitchEvent event : events) {
            PitchOccurrence occurrence = event.occurrence();
            if (!measurableFirstFourEvidence(occurrence.durationSeconds(),
                    occurrence.confidence(), occurrence.stabilityCent(),
                    occurrence.eventType().measurable())) {
                continue;
            }
            double relative = foldOctave(occurrence.observedCent() - tonicCent);
            if (relative > 1200.0 - EDGE_WRAP_CENT) relative -= 1200.0;
            // Only the common first-four domain can contribute.  This fixed
            // catalogue-wide boundary is independent of optional profile rows,
            // so upper degrees and display toggles cannot change core scores.
            if (relative <= -AnalysisConfig.COMMA_CENT - 1.0e-9
                    || relative >= firstFourUpperCent + AnalysisConfig.COMMA_CENT - 1.0e-9) {
                continue;
            }
            double weight = firstFourEvidenceWeight(occurrence.durationSeconds(),
                    occurrence.confidence(), occurrence.stabilityCent());
            double evidenceUnits = Math.min(1.0,
                    occurrence.durationSeconds() / MINIMUM_EVENT_SECONDS);
            result.add(new Observation(relative, occurrence.durationSeconds(), weight,
                    occurrence.confidence(), occurrence.startSeconds(), occurrence.endSeconds(),
                    evidenceUnits));
        }
        return result;
    }

    /** Quality contract for the non-directional first-four statistical mean. */
    static boolean measurableFirstFourEvidence(double durationSeconds, double confidence,
                                               double stabilityCent,
                                               boolean semanticallyMeasurable) {
        if (!basicFirstFourQuality(durationSeconds, stabilityCent)) return false;
        if (durationSeconds + 1.0e-9 >= MINIMUM_EVENT_SECONDS) {
            return semanticallyMeasurable
                    && confidence + 1.0e-9 >= MINIMUM_CONFIDENCE;
        }
        return durationSeconds + 1.0e-9 >= MINIMUM_SHORT_MEASURABLE_SECONDS
                && confidence + 1.0e-9 >= MINIMUM_SHORT_CONFIDENCE;
    }

    /**
     * Directional paths may use one-hop contacts as secondary ordering
     * evidence; they still receive the small continuous weight below.
     */
    static boolean reliableDirectionalFirstFourEvidence(double durationSeconds,
                                                        double confidence,
                                                        double stabilityCent) {
        return basicFirstFourQuality(durationSeconds, stabilityCent)
                && confidence + 1.0e-9 >= MINIMUM_CONFIDENCE;
    }

    private static boolean basicFirstFourQuality(double durationSeconds,
                                                 double stabilityCent) {
        return Double.isFinite(durationSeconds)
                && durationSeconds + 1.0e-9 >= MINIMUM_SECONDARY_EVENT_SECONDS
                && Double.isFinite(stabilityCent)
                && stabilityCent <= MAXIMUM_STABLE_SPREAD_HC
                * AnalysisConfig.COMMA_CENT + 1.0e-9;
    }

    /**
     * Gives sub-100 ms evidence a continuous but deliberately small
     * contribution. Duration is counted twice below the primary boundary
     * (physical duration and a linear reliability ramp), while confidence and
     * stability remain continuous quality factors. The ordinary profile mean
     * admits this weight from 60 ms; the directional path layer may use a
     * 20-60 ms contact only as secondary ordering evidence.
     */
    static double firstFourEvidenceWeight(double durationSeconds, double confidence,
                                          double stabilityCent) {
        if (!reliableDirectionalFirstFourEvidence(durationSeconds, confidence, stabilityCent)) {
            return 0.0;
        }
        double durationReliability = Math.min(1.0,
                durationSeconds / MINIMUM_EVENT_SECONDS);
        double stabilityHc = Math.max(0.0, stabilityCent / AnalysisConfig.COMMA_CENT);
        return durationSeconds * durationReliability * confidence / (1.0 + stabilityHc);
    }

    private static List<Cluster> cluster(List<Observation> observations) {
        if (observations.isEmpty()) return List.of();
        List<Observation> sorted = observations.stream()
                .sorted(Comparator.comparingDouble(Observation::cent)).toList();
        List<List<Observation>> groups = new ArrayList<>();
        List<Observation> current = new ArrayList<>();
        current.add(sorted.get(0));
        for (int i = 1; i < sorted.size(); i++) {
            Observation next = sorted.get(i);
            if (next.cent() - current.get(current.size() - 1).cent() <= CLUSTER_GAP_CENT) {
                current.add(next);
            } else {
                groups.add(current);
                current = new ArrayList<>();
                current.add(next);
            }
        }
        groups.add(current);
        return groups.stream().map(Cluster::from).sorted(Comparator.comparingDouble(Cluster::center)).toList();
    }

    private static DegreeBands degreeBands(TuningSystemCatalog.SystemData system) {
        List<FirstFourProfileReferenceCatalog.ReferenceProfile> core =
                FirstFourProfileReferenceCatalog.references(system).stream()
                        .limit(FirstFourProfileReferenceCatalog.coreDisplayNames().size())
                        .toList();
        double degree2Maximum = core.stream()
                .mapToDouble(value -> value.centPositions().get(1)).max().orElseThrow();
        double degree2Minimum = core.stream()
                .mapToDouble(value -> value.centPositions().get(1)).min().orElseThrow();
        double degree3Minimum = core.stream()
                .mapToDouble(value -> value.centPositions().get(2)).min().orElseThrow();
        double degree3Maximum = core.stream()
                .mapToDouble(value -> value.centPositions().get(2)).max().orElseThrow();
        double degree4Minimum = core.stream()
                .mapToDouble(value -> value.centPositions().get(3)).min().orElseThrow();
        // Midpoint between the fixed tonic anchor (0 C) and the lowest
        // primary second degree. Near-tonic events are not degree-2 evidence.
        double boundary12 = degree2Minimum / 2.0;
        double boundary23 = (degree2Maximum + degree3Minimum) / 2.0;
        double boundary34 = (degree3Maximum + degree4Minimum) / 2.0;
        return new DegreeBands(boundary12, boundary23, boundary34);
    }

    /**
     * Extracts ordered, candidate-independent phrase evidence before any
     * theoretical reference is scored.  Repetitions remain visible, while the
     * total contribution of one repeated degree is capped to one unit per
     * phrase so a tremolo or re-attack cannot dominate a scale comparison.
     */
    private static PhraseSet phraseEvidence(List<PitchEvent> events, double tonicCent,
                                            double firstFourUpperCent,
                                            DegreeBands degreeBands) {
        List<PathNode> nodes = pathNodes(events, tonicCent, firstFourUpperCent,
                degreeBands);
        List<List<PathNode>> runs = monotonicRuns(nodes);
        List<EvidenceObservation> scored = new ArrayList<>();
        int qualifying = 0;
        int complete = 0;
        int partial = 0;
        for (List<PathNode> run : runs) {
            Set<Integer> primaryDegrees = new LinkedHashSet<>();
            run.stream().filter(PathNode::primary)
                    .forEach(value -> primaryDegrees.add(value.degree()));
            if (primaryDegrees.size() < 3
                    || (!primaryDegrees.contains(1) && !primaryDegrees.contains(4))) {
                if (primaryDegrees.size() >= 2) partial++;
                continue;
            }
            qualifying++;
            if (primaryDegrees.containsAll(List.of(1, 2, 3, 4))) complete++;

            Map<Integer, List<PathNode>> byDegree = new LinkedHashMap<>();
            for (PathNode value : run) {
                if (value.primary()) {
                    byDegree.computeIfAbsent(value.degree(), ignored -> new ArrayList<>())
                            .add(value);
                }
            }
            for (Map.Entry<Integer, List<PathNode>> entry : byDegree.entrySet()) {
                double phraseDegreeWeight = entry.getValue().stream()
                        .mapToDouble(PathNode::weight).sum();
                for (PathNode value : entry.getValue()) {
                    double cappedWeight = phraseDegreeWeight <= 0.0 ? 0.0
                            : value.weight() / phraseDegreeWeight;
                    scored.add(new EvidenceObservation(entry.getKey(), value.cent(),
                            value.duration(), cappedWeight, value.confidence(),
                            value.eventCount(), value.start(), value.end()));
                }
            }
        }
        return new PhraseSet(List.copyOf(scored), qualifying, complete, partial);
    }

    private static List<PathNode> pathNodes(List<PitchEvent> events, double tonicCent,
                                            double firstFourUpperCent,
                                            DegreeBands degreeBands) {
        List<PathNode> result = new ArrayList<>();
        List<PitchEvent> ordered = events.stream()
                .sorted(Comparator.comparingDouble(value -> value.occurrence().startSeconds()))
                .toList();
        for (PitchEvent event : ordered) {
            PitchOccurrence occurrence = event.occurrence();
            double sourceRelative = occurrence.observedCent() - tonicCent;
            double relative = foldOctave(sourceRelative);
            if (relative > 1200.0 - EDGE_WRAP_CENT) relative -= 1200.0;
            boolean quality = reliableDirectionalFirstFourEvidence(
                    occurrence.durationSeconds(), occurrence.confidence(),
                    occurrence.stabilityCent());
            boolean inDomain = relative > -AnalysisConfig.COMMA_CENT - 1.0e-9
                    && relative < firstFourUpperCent + AnalysisConfig.COMMA_CENT - 1.0e-9;
            if (!quality || !inDomain) {
                if ((quality && !inDomain)
                        || occurrence.durationSeconds() >= MINIMUM_EVENT_SECONDS
                        || occurrence.phraseFinal()) {
                    addPathBoundary(result);
                }
                continue;
            }

            boolean primary = measurableFirstFourEvidence(occurrence.durationSeconds(),
                    occurrence.confidence(), occurrence.stabilityCent(),
                    occurrence.eventType().measurable());
            PathNode next = new PathNode(degreeBands.degree(relative), relative,
                    sourceRelative, occurrence.startSeconds(), occurrence.endSeconds(),
                    occurrence.durationSeconds(), firstFourEvidenceWeight(
                    occurrence.durationSeconds(), occurrence.confidence(),
                    occurrence.stabilityCent()), occurrence.confidence(), 1,
                    primary, false);
            if (!result.isEmpty() && !result.get(result.size() - 1).separator()) {
                PathNode previous = result.get(result.size() - 1);
                double gap = next.start() - previous.end();
                double foldedSeparation = Math.abs(next.cent() - previous.cent());
                if (gap > MAXIMUM_PHRASE_GAP_SECONDS
                        || (Math.abs(next.sourceCent() - previous.sourceCent())
                        > MAXIMUM_PHRASE_LEAP_CENT
                        && foldedSeparation > SAME_PITCH_TOLERANCE_CENT)) {
                    addPathBoundary(result);
                } else if (next.degree() == previous.degree()
                        && foldedSeparation <= SAME_PITCH_TOLERANCE_CENT
                        && gap <= MAXIMUM_REPEAT_MERGE_GAP_SECONDS) {
                    result.set(result.size() - 1, previous.merge(next));
                    if (occurrence.phraseFinal()) addPathBoundary(result);
                    continue;
                }
            }
            result.add(next);
            if (occurrence.phraseFinal()) addPathBoundary(result);
        }
        return List.copyOf(result);
    }

    private static void addPathBoundary(List<PathNode> values) {
        if (!values.isEmpty() && !values.get(values.size() - 1).separator()) {
            values.add(PathNode.boundary());
        }
    }

    private static List<List<PathNode>> monotonicRuns(List<PathNode> nodes) {
        List<List<PathNode>> result = new ArrayList<>();
        List<PathNode> current = new ArrayList<>();
        int direction = 0;
        for (PathNode value : nodes) {
            if (value.separator()) {
                finishPathRun(result, current);
                direction = 0;
                continue;
            }
            if (!current.isEmpty()) {
                int step = Integer.compare(value.degree(),
                        current.get(current.size() - 1).degree());
                if (step != 0 && direction != 0 && step != direction) {
                    // The turning pitch belongs to both adjacent monotonic
                    // paths (1-2-3 and 3-2-1).  Carry it into the new run;
                    // otherwise the descending path would silently lose its
                    // fourth/upper anchor.
                    PathNode turningPoint = current.get(current.size() - 1);
                    finishPathRun(result, current);
                    current.add(turningPoint);
                    direction = step;
                }
                if (step != 0 && direction == 0) direction = step;
            }
            current.add(value);
        }
        finishPathRun(result, current);
        return List.copyOf(result);
    }

    private static void finishPathRun(List<List<PathNode>> result,
                                      List<PathNode> current) {
        if (!current.isEmpty()) result.add(List.copyOf(current));
        current.clear();
    }

    /**
     * Builds immutable multimodal evidence for degrees 2, 3 and 4. Separate
     * performed peaks such as 4.93 and 9.03 Hc are retained; an unperformed
     * mean between them is never synthesized.
     */
    private static List<DegreeEvidence> commonDegreeEvidence(
            List<EvidenceObservation> observations,
            DegreeBands degreeBands) {

        List<DegreeEvidence> result = new ArrayList<>(3);
        for (int degree = 2; degree <= 4; degree++) {
            int selectedDegree = degree;
            result.add(DegreeEvidence.from(degree, observations.stream()
                    .filter(value -> value.degree() == selectedDegree).toList()));
        }
        return List.copyOf(result);
    }

    /**
     * Converts the global first-four inventory into immutable multimodal
     * degree evidence. Every profile is scored against this exact same set;
     * candidate names can neither select nor rename observations. Separate
     * performed modes remain separate through {@link DegreeEvidence#from}.
     */
    private static List<DegreeEvidence> inventoryDegreeEvidence(
            List<Observation> observations, DegreeBands degreeBands) {
        List<EvidenceObservation> evidence = observations.stream()
                .map(value -> new EvidenceObservation(degreeBands.degree(value.cent()),
                        value.cent(), value.duration(), value.weight(),
                        value.confidence(), 1, value.start(), value.end()))
                .toList();
        return commonDegreeEvidence(evidence, degreeBands);
    }

    private static MakamProfileComparison.PhraseEvidence publicPhraseEvidence(
            PhraseSet phraseSet, List<DegreeEvidence> evidence) {
        MakamProfileComparison.PhraseEvidenceStatus status =
                phraseSet.qualifyingPhraseCount() > 0
                        ? MakamProfileComparison.PhraseEvidenceStatus.COMPLETE
                        : phraseSet.partialPhraseCount() > 0
                        ? MakamProfileComparison.PhraseEvidenceStatus.PARTIAL_MIXED
                        : MakamProfileComparison.PhraseEvidenceStatus.INSUFFICIENT;
        List<MakamProfileComparison.DegreePeak> peaks = new ArrayList<>();
        for (DegreeEvidence degree : evidence) {
            for (DegreePeak peak : degree.peaks()) {
                peaks.add(new MakamProfileComparison.DegreePeak(degree.degree(),
                        peak.centerCent(), peak.centerCent() / AnalysisConfig.COMMA_CENT,
                        peak.eventCount(), peak.duration(), peak.weight(),
                        peak.meanConfidence(), peak.dominant()));
            }
        }
        return new MakamProfileComparison.PhraseEvidence(status,
                phraseSet.qualifyingPhraseCount(), phraseSet.completePhraseCount(),
                phraseSet.partialPhraseCount(), phraseSet.scoredEvents().stream()
                .mapToInt(EvidenceObservation::eventCount).sum(), List.copyOf(peaks));
    }

    private static ScoredCandidate score(int referenceOrder, String name,
                                         List<String> groupNames, double[] intervals,
                                         List<DegreeEvidence> commonEvidence,
                                         boolean selected) {
        if (intervals.length == 0) {
            return new ScoredCandidate(referenceOrder, name, groupNames, selected,
                    0.0, 1200.0, 1200.0,
                    1200.0, 0.0, 0.0, 0, intervals.length, List.of());
        }
        if (commonEvidence.stream().noneMatch(DegreeEvidence::observed)) {
            List<MakamProfileComparison.DegreeMatch> missing = new ArrayList<>(intervals.length);
            for (int index = 0; index < intervals.length; index++) {
                double interval = intervals[index];
                boolean tonicAnchor = index == 0;
                missing.add(new MakamProfileComparison.DegreeMatch(index + 1, interval,
                        interval / AnalysisConfig.COMMA_CENT, tonicAnchor,
                        tonicAnchor ? 0.0 : Double.NaN,
                        tonicAnchor ? 0.0 : Double.NaN,
                        tonicAnchor ? 0.0 : Double.NaN,
                        tonicAnchor ? 0.0 : Double.NaN, 0, 0.0));
            }
            return new ScoredCandidate(referenceOrder, name, groupNames, selected,
                    0.0, 1200.0, 1200.0, 1200.0, 0.0, 0.0,
                    intervals.length == 0 ? 0 : 1, intervals.length, List.copyOf(missing));
        }
        List<Residual> residuals = new ArrayList<>();
        double weightedLoss = 0.0;
        double withinWeight = 0.0;
        double totalWeight = 0.0;
        double similaritySum = 0.0;
        int observedDegrees = 0;
        for (DegreeEvidence evidence : commonEvidence) {
            if (!evidence.observed() || evidence.degree() >= intervals.length + 1) continue;
            double expected = intervals[evidence.degree() - 1];
            double peakWeight = evidence.peaks().stream()
                    .mapToDouble(DegreePeak::weight).sum();
            double degreeSimilarity = 0.0;
            for (DegreePeak peak : evidence.peaks()) {
                // Every reference sees exactly the same immutable performed
                // modes.  A peak is compared continuously with the candidate;
                // the candidate may never choose a different event subset.
                double normalized = peakWeight <= 1.0e-12
                        ? 1.0 / evidence.peaks().size()
                        : peak.weight() / peakWeight;
                double signed = peak.centerCent() - expected;
                double absolute = Math.abs(signed);
                residuals.add(new Residual(absolute, normalized));
                weightedLoss += normalized * huberLoss(absolute, HUBER_DELTA_CENT);
                if (absolute <= AnalysisConfig.COMMA_CENT + 1.0e-9) {
                    withinWeight += normalized;
                }
                totalWeight += normalized;
                degreeSimilarity += normalized
                        * Math.exp(-absolute / AnalysisConfig.COMMA_CENT);
            }
            // Degrees are equally diagnostic; repetition within one phrase
            // has already been capped when the fixed evidence was assembled.
            similaritySum += degreeSimilarity;
            observedDegrees++;
        }
        double robustError = totalWeight <= 0.0 ? 1200.0
                : Math.sqrt(2.0 * weightedLoss / totalWeight);
        double medianError = weightedPercentile(residuals, 0.50);
        double p90Error = weightedPercentile(residuals, 0.90);
        double within = totalWeight <= 0.0 ? 0.0 : withinWeight * 100.0 / totalWeight;

        List<MakamProfileComparison.DegreeMatch> matches = new ArrayList<>();
        int matched = 0;
        for (int i = 0; i < intervals.length; i++) {
            int degreeNumber = i + 1;
            double interval = intervals[i];
            if (i == 0) {
                // The tonic is a fixed reference decision, not an observed
                // cluster that may raise or lower every candidate equally.
                matches.add(new MakamProfileComparison.DegreeMatch(1, interval,
                        interval / AnalysisConfig.COMMA_CENT, true,
                        0.0, 0.0, 0.0, 0.0, 0, 0.0));
                continue;
            }
            DegreeEvidence evidence = commonEvidence.stream()
                    .filter(value -> value.degree() == degreeNumber).findFirst().orElse(null);
            boolean observed = evidence != null && evidence.observed();
            DegreePeak dominant = observed ? evidence.dominant() : null;
            double residual = dominant == null ? Double.NaN
                    : dominant.centerCent() - interval;
            if (observed && Math.abs(residual) <= AnalysisConfig.COMMA_CENT + 1.0e-9) matched++;
            matches.add(new MakamProfileComparison.DegreeMatch(degreeNumber, interval,
                    interval / AnalysisConfig.COMMA_CENT, observed,
                    dominant == null ? Double.NaN : dominant.centerCent(),
                    dominant == null ? Double.NaN
                            : dominant.centerCent() / AnalysisConfig.COMMA_CENT,
                    residual, residual / AnalysisConfig.COMMA_CENT,
                    dominant == null ? 0 : dominant.eventCount(),
                    dominant == null ? 0.0 : dominant.duration()));
        }
        int discriminatingDegreeCount = Math.max(0, intervals.length - 1);
        double coverage = discriminatingDegreeCount == 0 ? 0.0
                : observedDegrees * 100.0 / discriminatingDegreeCount;
        // Continuous distance score on the same observed degree centres for
        // every reference. Missing degrees contribute zero through coverage.
        double score = discriminatingDegreeCount == 0 ? 0.0
                : 100.0 * similaritySum / discriminatingDegreeCount;
        return new ScoredCandidate(referenceOrder, name, groupNames, selected,
                clampPercent(score), robustError,
                medianError, p90Error, within, coverage,
                matched + (intervals.length == 0 ? 0 : 1), intervals.length, List.copyOf(matches));
    }

    private static int representedPhraseTimeRegions(
            List<EvidenceObservation> observations) {
        if (observations.isEmpty()) return 0;
        double end = observations.stream().mapToDouble(EvidenceObservation::end)
                .max().orElse(0.0);
        if (end <= 0.0) return 1;
        boolean[] used = new boolean[5];
        for (EvidenceObservation observation : observations) {
            int region = Math.min(4, Math.max(0, (int) Math.floor(observation.start() / end * 5.0)));
            used[region] = true;
        }
        int count = 0;
        for (boolean value : used) if (value) count++;
        return count;
    }

    private static double nearestInterval(double center, double[] intervals) {
        double best = intervals[0];
        double distance = Math.abs(signedCircularDifference(center, best));
        for (int i = 1; i < intervals.length; i++) {
            double candidateDistance = Math.abs(signedCircularDifference(center, intervals[i]));
            if (candidateDistance < distance) {
                best = intervals[i];
                distance = candidateDistance;
            }
        }
        return best;
    }

    private static double nearestDiscriminatingInterval(double center, double[] intervals) {
        if (intervals.length <= 1) return intervals.length == 0 ? 0.0 : intervals[0];
        double best = intervals[1];
        double distance = Math.abs(signedCircularDifference(center, best));
        for (int i = 2; i < intervals.length; i++) {
            double candidateDistance = Math.abs(signedCircularDifference(center, intervals[i]));
            if (candidateDistance < distance) {
                best = intervals[i];
                distance = candidateDistance;
            }
        }
        return best;
    }

    private static double signedCircularDifference(double observed, double expected) {
        double difference = foldOctave(observed) - foldOctave(expected);
        while (difference > 600.0) difference -= 1200.0;
        while (difference < -600.0) difference += 1200.0;
        return difference;
    }

    private static double foldOctave(double value) {
        double folded = value % 1200.0;
        if (folded < 0.0) folded += 1200.0;
        if (folded >= 1200.0 - 1.0e-9) folded = 0.0;
        return folded;
    }

    private static double hertzToCent(double hertz) {
        return 1200.0 * Math.log(hertz / 16.351597831287414) / Math.log(2.0);
    }

    private static double huberLoss(double value, double delta) {
        return value <= delta ? 0.5 * value * value : delta * (value - 0.5 * delta);
    }

    private static double weightedPercentile(List<Residual> values, double percentile) {
        if (values.isEmpty()) return 0.0;
        List<Residual> sorted = values.stream().sorted(Comparator.comparingDouble(Residual::value)).toList();
        double total = sorted.stream().mapToDouble(Residual::weight).sum();
        double threshold = total * percentile;
        double cumulative = 0.0;
        for (Residual value : sorted) {
            cumulative += value.weight();
            if (cumulative + 1.0e-12 >= threshold) return value.value();
        }
        return sorted.get(sorted.size() - 1).value();
    }

    private static double weightedMedian(List<Observation> values) {
        List<Observation> sorted = values.stream().sorted(Comparator.comparingDouble(Observation::cent)).toList();
        double half = sorted.stream().mapToDouble(Observation::weight).sum() / 2.0;
        double cumulative = 0.0;
        for (Observation value : sorted) {
            cumulative += value.weight();
            if (cumulative + 1.0e-12 >= half) return value.cent();
        }
        return sorted.get(sorted.size() - 1).cent();
    }

    private static double weightedEvidenceMedian(
            List<EvidenceObservation> values) {
        List<EvidenceObservation> sorted = values.stream()
                .sorted(Comparator.comparingDouble(EvidenceObservation::cent)).toList();
        double total = sorted.stream().mapToDouble(EvidenceObservation::weight).sum();
        if (total <= 1.0e-12) {
            return sorted.get(sorted.size() / 2).cent();
        }
        double half = total / 2.0;
        double cumulative = 0.0;
        for (EvidenceObservation value : sorted) {
            cumulative += value.weight();
            if (cumulative + 1.0e-12 >= half) return value.cent();
        }
        return sorted.get(sorted.size() - 1).cent();
    }

    private static boolean sameName(String first, String second) {
        return normalizedName(first).equals(normalizedName(second));
    }

    private static String normalizedName(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('ı', 'i')
                .replace('ş', 's')
                .replace('ç', 'c')
                .replace('ğ', 'g')
                .replace('ü', 'u')
                .replace('ö', 'o')
                .replaceAll("[^a-z0-9]", "");
    }

    private static double clampPercent(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private record Observation(double cent, double duration, double weight, double confidence,
                               double start, double end, double evidenceUnits) { }

    /** Fixed candidate-independent degree boundaries derived from the core set. */
    private record DegreeBands(double boundary12, double boundary23,
                               double boundary34) {
        int degree(double cent) {
            if (cent < boundary12) return 1;
            if (cent < boundary23) return 2;
            if (cent < boundary34) return 3;
            return 4;
        }
    }

    /** One chronological node used before any theoretical profile is scored. */
    private record PathNode(int degree, double cent, double sourceCent,
                            double start, double end, double duration,
                            double weight, double confidence, int eventCount,
                            boolean primary, boolean separator) {
        static PathNode boundary() {
            return new PathNode(0, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, 0.0, 0.0, 0.0, 0, false, true);
        }

        PathNode merge(PathNode next) {
            double combinedWeight = weight + next.weight;
            double combinedCent = combinedWeight <= 1.0e-12
                    ? (cent + next.cent) / 2.0
                    : (cent * weight + next.cent * next.weight) / combinedWeight;
            double combinedConfidence = combinedWeight <= 1.0e-12
                    ? (confidence + next.confidence) / 2.0
                    : (confidence * weight + next.confidence * next.weight)
                    / combinedWeight;
            // sourceCent follows the last physical event. Averaging source
            // cents across octave-equivalent repetitions would manufacture a
            // false half-octave leap at the following node.
            return new PathNode(degree, combinedCent, next.sourceCent, start,
                    Math.max(end, next.end), duration + next.duration,
                    combinedWeight, combinedConfidence,
                    eventCount + next.eventCount, primary || next.primary, false);
        }
    }

    /** One immutable observed event admitted by an anchored ordered phrase. */
    private record EvidenceObservation(int degree, double cent, double duration,
                                       double weight, double confidence,
                                       int eventCount, double start, double end) { }

    private record PhraseSet(List<EvidenceObservation> scoredEvents,
                             int qualifyingPhraseCount,
                             int completePhraseCount,
                             int partialPhraseCount) {
        PhraseSet {
            scoredEvents = List.copyOf(scoredEvents);
        }
    }

    /** A performed mode; separate modes are never replaced by their mean. */
    private record DegreePeak(double centerCent, int eventCount,
                              double duration, double weight,
                              double meanConfidence, boolean dominant) { }

    private record DegreeEvidence(int degree, List<DegreePeak> peaks) {
        DegreeEvidence {
            peaks = List.copyOf(peaks);
        }

        boolean observed() {
            return !peaks.isEmpty();
        }

        DegreePeak dominant() {
            return peaks.stream().filter(DegreePeak::dominant).findFirst()
                    .orElseGet(() -> peaks.isEmpty() ? null : peaks.get(0));
        }

        private static DegreeEvidence from(int degree,
                                           List<EvidenceObservation> events) {
            if (events.isEmpty()) return new DegreeEvidence(degree, List.of());
            List<EvidenceObservation> sorted = events.stream()
                    .sorted(Comparator.comparingDouble(EvidenceObservation::cent))
                    .toList();
            List<List<EvidenceObservation>> groups = new ArrayList<>();
            List<EvidenceObservation> current = new ArrayList<>();
            current.add(sorted.get(0));
            for (int i = 1; i < sorted.size(); i++) {
                EvidenceObservation next = sorted.get(i);
                if (next.cent() - current.get(current.size() - 1).cent()
                        <= CLUSTER_GAP_CENT) {
                    current.add(next);
                } else {
                    groups.add(List.copyOf(current));
                    current.clear();
                    current.add(next);
                }
            }
            groups.add(List.copyOf(current));

            List<DegreePeak> raw = new ArrayList<>(groups.size());
            for (List<EvidenceObservation> group : groups) {
                double weight = group.stream()
                        .mapToDouble(EvidenceObservation::weight).sum();
                double duration = group.stream()
                        .mapToDouble(EvidenceObservation::duration).sum();
                int eventCount = group.stream()
                        .mapToInt(EvidenceObservation::eventCount).sum();
                double confidence = weight <= 1.0e-12
                        ? group.stream().mapToDouble(EvidenceObservation::confidence)
                        .average().orElse(0.0)
                        : group.stream().mapToDouble(value -> value.confidence()
                        * value.weight()).sum() / weight;
                raw.add(new DegreePeak(weightedEvidenceMedian(group), eventCount,
                        duration, weight, confidence, false));
            }
            int dominantIndex = 0;
            for (int i = 1; i < raw.size(); i++) {
                DegreePeak currentPeak = raw.get(i);
                DegreePeak best = raw.get(dominantIndex);
                if (currentPeak.weight() > best.weight() + 1.0e-12
                        || (Math.abs(currentPeak.weight() - best.weight()) <= 1.0e-12
                        && (currentPeak.duration() > best.duration() + 1.0e-12
                        || (Math.abs(currentPeak.duration() - best.duration()) <= 1.0e-12
                        && currentPeak.eventCount() > best.eventCount())))) {
                    dominantIndex = i;
                }
            }
            List<DegreePeak> peaks = new ArrayList<>(raw.size());
            for (int i = 0; i < raw.size(); i++) {
                DegreePeak value = raw.get(i);
                peaks.add(new DegreePeak(value.centerCent(), value.eventCount(),
                        value.duration(), value.weight(), value.meanConfidence(),
                        i == dominantIndex));
            }
            return new DegreeEvidence(degree, List.copyOf(peaks));
        }
    }
    private record Residual(double value, double weight) { }
    private record ScoredCandidate(int referenceOrder, String name,
                                   List<String> groupNames, boolean selected,
                                   double score, double robustError, double medianError,
                                   double p90Error, double withinOneHc, double coverage,
                                   int matchedDegrees, int degreeCount,
                                   List<MakamProfileComparison.DegreeMatch> matches) { }

    private record Cluster(double center, List<Observation> events, double duration,
                           double weight, double meanConfidence) {
        static Cluster from(List<Observation> events) {
            double weight = events.stream().mapToDouble(Observation::weight).sum();
            double duration = events.stream().mapToDouble(Observation::duration).sum();
            double confidence = events.stream().mapToDouble(value -> value.confidence() * value.weight()).sum()
                    / Math.max(1.0e-12, weight);
            double center = weightedMedian(events);
            return new Cluster(center, List.copyOf(events), duration, weight, confidence);
        }

        MakamProfileComparison.ObservedCluster asPublic() {
            // `center` is already tonic-relative and intentionally keeps the
            // narrow negative edge around the confirmed tonic. Folding that
            // edge back to ~53 Hc would falsely label a slightly flat tonic as
            // an upper-fourth-domain peak in the report.
            return new MakamProfileComparison.ObservedCluster(center,
                    center / AnalysisConfig.COMMA_CENT, events.size(), duration,
                    weight, meanConfidence);
        }
    }
}
