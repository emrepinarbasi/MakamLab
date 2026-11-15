package com.makambox.studio.study;

import java.util.List;

/** Result of a tonic-aligned interval-profile comparison, not a makam diagnosis. */
public record MakamProfileComparison(
        String referenceSystem,
        String selectedMakam,
        boolean sufficientEvidence,
        String evidenceSummary,
        int acceptedEventCount,
        int observedClusterCount,
        double acceptedDurationSeconds,
        int representedTimeRegions,
        List<ObservedCluster> observedClusters,
        List<Candidate> candidates,
        FirstFourProfileSelection firstFourProfileSelection,
        PhraseEvidence phraseEvidence
) {
    public MakamProfileComparison {
        observedClusters = List.copyOf(observedClusters);
        candidates = List.copyOf(candidates);
        firstFourProfileSelection = firstFourProfileSelection == null
                ? FirstFourProfileSelection.defaults() : firstFourProfileSelection;
        phraseEvidence = phraseEvidence == null ? PhraseEvidence.empty() : phraseEvidence;
    }

    /** Compatibility constructor for callers created before phrase-context evidence. */
    public MakamProfileComparison(String referenceSystem, String selectedMakam,
                                  boolean sufficientEvidence, String evidenceSummary,
                                  int acceptedEventCount, int observedClusterCount,
                                  double acceptedDurationSeconds, int representedTimeRegions,
                                  List<ObservedCluster> observedClusters,
                                  List<Candidate> candidates,
                                  FirstFourProfileSelection firstFourProfileSelection) {
        this(referenceSystem, selectedMakam, sufficientEvidence, evidenceSummary,
                acceptedEventCount, observedClusterCount, acceptedDurationSeconds,
                representedTimeRegions, observedClusters, candidates,
                firstFourProfileSelection, PhraseEvidence.empty());
    }

    /** Compatibility constructor for frozen documents created before profile selection. */
    public MakamProfileComparison(String referenceSystem, String selectedMakam,
                                  boolean sufficientEvidence, String evidenceSummary,
                                  int acceptedEventCount, int observedClusterCount,
                                  double acceptedDurationSeconds, int representedTimeRegions,
                                  List<ObservedCluster> observedClusters,
                                  List<Candidate> candidates) {
        this(referenceSystem, selectedMakam, sufficientEvidence, evidenceSummary,
                acceptedEventCount, observedClusterCount, acceptedDurationSeconds,
                representedTimeRegions, observedClusters, candidates,
                FirstFourProfileSelection.defaults(), PhraseEvidence.empty());
    }

    public enum PhraseEvidenceStatus {
        COMPLETE,
        PARTIAL_MIXED,
        INSUFFICIENT
    }

    /** One retained mode inside a fixed, candidate-independent degree band. */
    public record DegreePeak(
            int degree,
            double centerCent,
            double centerHc,
            int eventCount,
            double totalDurationSeconds,
            double evidenceWeight,
            double meanConfidence,
            boolean dominant
    ) { }

    /** Ordered phrase evidence kept separate from the global pitch inventory. */
    public record PhraseEvidence(
            PhraseEvidenceStatus status,
            int qualifyingPhraseCount,
            int completePhraseCount,
            int partialPhraseCount,
            int evidenceEventCount,
            List<DegreePeak> degreePeaks
    ) {
        public PhraseEvidence {
            status = status == null ? PhraseEvidenceStatus.INSUFFICIENT : status;
            degreePeaks = List.copyOf(degreePeaks == null ? List.of() : degreePeaks);
        }

        public static PhraseEvidence empty() {
            return new PhraseEvidence(PhraseEvidenceStatus.INSUFFICIENT,
                    0, 0, 0, 0, List.of());
        }
    }

    public record ObservedCluster(
            double centerCent,
            double centerHc,
            int eventCount,
            double totalDurationSeconds,
            double evidenceWeight,
            double meanConfidence
    ) { }

    public record DegreeMatch(
            int degree,
            double theoreticalCent,
            double theoreticalHc,
            boolean observed,
            double observedCent,
            double observedHc,
            double residualCent,
            double residualHc,
            int eventCount,
            double totalDurationSeconds
    ) { }

    public record Candidate(
            int rank,
            String makamName,
            List<String> indistinguishableProfiles,
            boolean selectedProfile,
            double scorePercent,
            double robustErrorCent,
            double medianAbsoluteErrorCent,
            double p90AbsoluteErrorCent,
            double withinOneHcWeightPercent,
            double degreeCoveragePercent,
            int matchedDegreeCount,
            int theoreticalDegreeCount,
            List<DegreeMatch> degreeMatches
    ) {
        public Candidate {
            indistinguishableProfiles = List.copyOf(indistinguishableProfiles);
            degreeMatches = List.copyOf(degreeMatches);
        }
    }
}
