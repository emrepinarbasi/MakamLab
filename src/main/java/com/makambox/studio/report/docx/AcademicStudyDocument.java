package com.makambox.studio.report.docx;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, UI-independent input for Makam's editable Word report.
 *
 * <p>The study engine deliberately adapts its result to this small document
 * model before export.  PDF, CSV and DOCX exporters can therefore be fed from
 * the same frozen analysis result instead of scraping mutable Swing controls.</p>
 */
public record AcademicStudyDocument(
        String reportTitle,
        Source source,
        Theory theory,
        TonicEvidence tonic,
        List<Event> contacts,
        List<TracePoint> tonicTrace,
        List<ProfileComparison> profileComparisons,
        List<String> methodNotes,
        List<String> limitations,
        ZonedDateTime generatedAt,
        List<DirectionalContext> directionalContexts,
        List<DirectionalFirstFourProfile> directionalFirstFourProfiles,
        List<FirstFourPeak> firstFourInventoryPeaks,
        FirstFourPhraseEvidence firstFourPhraseEvidence
) {
    public static final double HC_CENT = 1200.0 / 53.0;

    public AcademicStudyDocument {
        reportTitle = nonBlank(reportTitle, "MakamLab");
        source = Objects.requireNonNull(source, "source");
        theory = Objects.requireNonNull(theory, "theory");
        tonic = Objects.requireNonNull(tonic, "tonic");
        contacts = List.copyOf(contacts == null ? List.of() : contacts);
        tonicTrace = List.copyOf(tonicTrace == null ? List.of() : tonicTrace);
        profileComparisons = List.copyOf(profileComparisons == null ? List.of() : profileComparisons);
        methodNotes = List.copyOf(methodNotes == null ? List.of() : methodNotes);
        limitations = List.copyOf(limitations == null ? List.of() : limitations);
        generatedAt = generatedAt == null ? ZonedDateTime.now() : generatedAt;
        directionalContexts = List.copyOf(directionalContexts == null ? List.of() : directionalContexts);
        directionalFirstFourProfiles = List.copyOf(directionalFirstFourProfiles == null
                ? List.of() : directionalFirstFourProfiles);
        firstFourInventoryPeaks = List.copyOf(firstFourInventoryPeaks == null
                ? List.of() : firstFourInventoryPeaks);
        firstFourPhraseEvidence = firstFourPhraseEvidence == null
                ? FirstFourPhraseEvidence.EMPTY : firstFourPhraseEvidence;
    }

    /** Backward-compatible constructor for ordinary single-pitch and interval reports. */
    public AcademicStudyDocument(String reportTitle, Source source, Theory theory,
                                 TonicEvidence tonic, List<Event> contacts,
                                 List<TracePoint> tonicTrace,
                                 List<ProfileComparison> profileComparisons,
                                 List<String> methodNotes, List<String> limitations,
                                 ZonedDateTime generatedAt) {
        this(reportTitle, source, theory, tonic, contacts, tonicTrace, profileComparisons,
                methodNotes, limitations, generatedAt, List.of(), List.of(), List.of(),
                FirstFourPhraseEvidence.EMPTY);
    }

    /** Backward-compatible constructor for the dedicated directional-pitch report. */
    public AcademicStudyDocument(String reportTitle, Source source, Theory theory,
                                 TonicEvidence tonic, List<Event> contacts,
                                 List<TracePoint> tonicTrace,
                                 List<ProfileComparison> profileComparisons,
                                 List<String> methodNotes, List<String> limitations,
                                 ZonedDateTime generatedAt,
                                 List<DirectionalContext> directionalContexts) {
        this(reportTitle, source, theory, tonic, contacts, tonicTrace, profileComparisons,
                methodNotes, limitations, generatedAt, directionalContexts, List.of(), List.of(),
                FirstFourPhraseEvidence.EMPTY);
    }

    /** Backward-compatible constructor for documents created before separate peak/path evidence. */
    public AcademicStudyDocument(String reportTitle, Source source, Theory theory,
                                 TonicEvidence tonic, List<Event> contacts,
                                 List<TracePoint> tonicTrace,
                                 List<ProfileComparison> profileComparisons,
                                 List<String> methodNotes, List<String> limitations,
                                 ZonedDateTime generatedAt,
                                 List<DirectionalContext> directionalContexts,
                                 List<DirectionalFirstFourProfile> directionalFirstFourProfiles) {
        this(reportTitle, source, theory, tonic, contacts, tonicTrace, profileComparisons,
                methodNotes, limitations, generatedAt, directionalContexts,
                directionalFirstFourProfiles, List.of(), FirstFourPhraseEvidence.EMPTY);
    }

    public List<Event> measurableEvents() {
        return contacts.stream().filter(Event::measurable).toList();
    }

    public List<Event> stableEvents() {
        return contacts.stream().filter(Event::measurable).filter(Event::stable).toList();
    }

    public Statistics measurableStatistics() { return Statistics.from(measurableEvents()); }
    public Statistics stableStatistics() { return Statistics.from(stableEvents()); }

    public Map<String, Statistics> directionStatistics() {
        return measurableEvents().stream().map(Event::direction).distinct()
                .collect(java.util.stream.Collectors.toMap(value -> value,
                        direction -> Statistics.from(measurableEvents().stream()
                                .filter(event -> event.direction().equals(direction)).toList()),
                        (first, ignored) -> first, java.util.LinkedHashMap::new));
    }

    public boolean directionalSegahAnalysis() { return theory.directionalSegahAnalysis(); }

    public DirectionalContext directionalContext(int eventIndex) {
        return directionalContexts.stream().filter(value -> value.eventIndex() == eventIndex)
                .findFirst().orElse(DirectionalContext.EMPTY);
    }

    public record Source(String fileName, String absolutePath, String sha256,
                         double durationSeconds, double sampleRate,
                         String channelDescription, String applicationVersion,
                         String engineVersion) {
        public Source {
            fileName = nonBlank(fileName, "—");
            absolutePath = Objects.requireNonNullElse(absolutePath, "");
            sha256 = Objects.requireNonNullElse(sha256, "");
            channelDescription = nonBlank(channelDescription, "—");
            applicationVersion = nonBlank(applicationVersion, "Makam");
            engineVersion = nonBlank(engineVersion, "—");
        }
    }

    public record Theory(String makamName, String tonicNote, String targetNote,
                         double tonicHz, double targetRelativeCent,
                         double targetRelativeHc, double targetHz,
                         String referenceSystem,
                         String analysisMode,
                         String lowerBoundaryNote, double lowerBoundaryCent,
                         double lowerBoundaryHc, double lowerBoundaryHz,
                         String upperBoundaryNote, double upperBoundaryCent,
                         double upperBoundaryHc, double upperBoundaryHz,
                         boolean boundariesIncluded,
                         List<TheoryPitch> pitchCenters,
                         List<TheoryPitch> expectedReferences) {
        public Theory(String makamName, String tonicNote, String targetNote,
                      double tonicHz, double targetRelativeCent,
                      double targetRelativeHc, double targetHz,
                      String referenceSystem) {
            this(makamName, tonicNote, targetNote, tonicHz, targetRelativeCent,
                    targetRelativeHc, targetHz, referenceSystem, "Tek perde analizi",
                    "", Double.NaN, Double.NaN, Double.NaN,
                    "", Double.NaN, Double.NaN, Double.NaN, true, List.of(), List.of());
        }

        public Theory(String makamName, String tonicNote, String targetNote,
                      double tonicHz, double targetRelativeCent,
                      double targetRelativeHc, double targetHz,
                      String referenceSystem, List<TheoryPitch> pitchCenters) {
            this(makamName, tonicNote, targetNote, tonicHz, targetRelativeCent,
                    targetRelativeHc, targetHz, referenceSystem, "Tek perde analizi",
                    "", Double.NaN, Double.NaN, Double.NaN,
                    "", Double.NaN, Double.NaN, Double.NaN, true, pitchCenters, List.of());
        }

        /** Backward-compatible constructor for interval-report fixtures. */
        public Theory(String makamName, String tonicNote, String targetNote,
                      double tonicHz, double targetRelativeCent,
                      double targetRelativeHc, double targetHz,
                      String referenceSystem, String analysisMode,
                      String lowerBoundaryNote, double lowerBoundaryCent,
                      double lowerBoundaryHc, double lowerBoundaryHz,
                      String upperBoundaryNote, double upperBoundaryCent,
                      double upperBoundaryHc, double upperBoundaryHz,
                      boolean boundariesIncluded) {
            this(makamName, tonicNote, targetNote, tonicHz, targetRelativeCent,
                    targetRelativeHc, targetHz, referenceSystem, analysisMode,
                    lowerBoundaryNote, lowerBoundaryCent, lowerBoundaryHc, lowerBoundaryHz,
                    upperBoundaryNote, upperBoundaryCent, upperBoundaryHc, upperBoundaryHz,
                    boundariesIncluded, List.of(), List.of());
        }

        /** Backward-compatible constructor for interval reports without an explicit expected-reference list. */
        public Theory(String makamName, String tonicNote, String targetNote,
                      double tonicHz, double targetRelativeCent,
                      double targetRelativeHc, double targetHz,
                      String referenceSystem, String analysisMode,
                      String lowerBoundaryNote, double lowerBoundaryCent,
                      double lowerBoundaryHc, double lowerBoundaryHz,
                      String upperBoundaryNote, double upperBoundaryCent,
                      double upperBoundaryHc, double upperBoundaryHz,
                      boolean boundariesIncluded, List<TheoryPitch> pitchCenters) {
            this(makamName, tonicNote, targetNote, tonicHz, targetRelativeCent,
                    targetRelativeHc, targetHz, referenceSystem, analysisMode,
                    lowerBoundaryNote, lowerBoundaryCent, lowerBoundaryHc, lowerBoundaryHz,
                    upperBoundaryNote, upperBoundaryCent, upperBoundaryHc, upperBoundaryHz,
                    boundariesIncluded, pitchCenters, List.of());
        }

        public Theory {
            makamName = nonBlank(makamName, "—");
            tonicNote = nonBlank(tonicNote, "—");
            targetNote = nonBlank(targetNote, "—");
            referenceSystem = nonBlank(referenceSystem, "53-Hc");
            analysisMode = nonBlank(analysisMode, "Tek perde analizi");
            lowerBoundaryNote = Objects.requireNonNullElse(lowerBoundaryNote, "");
            upperBoundaryNote = Objects.requireNonNullElse(upperBoundaryNote, "");
            pitchCenters = List.copyOf(pitchCenters == null ? List.of() : pitchCenters);
            expectedReferences = List.copyOf(expectedReferences == null ? List.of() : expectedReferences);
        }

        public boolean intervalAnalysis() {
            return analysisMode.equalsIgnoreCase("Perdeler arası analiz")
                    || analysisMode.equalsIgnoreCase("Between-pitches analysis")
                    || analysisMode.equalsIgnoreCase(ReportLocalization.text(
                    "report.analysisType.interval", "Between-pitches analysis", "Perdeler arası analiz"));
        }


        public boolean directionalSegahAnalysis() {
            return analysisMode.equalsIgnoreCase("İnici-Çıkıcı Segâh analizi")
                    || analysisMode.equalsIgnoreCase("Ascending–descending Segâh analysis")
                    || analysisMode.equalsIgnoreCase(ReportLocalization.text(
                    "report.analysisType.directionalSegah",
                    "Ascending–descending Segâh analysis", "İnici-Çıkıcı Segâh analizi"));
        }

        public boolean openBoundaryAnalysis() {
            return intervalAnalysis() || directionalSegahAnalysis();
        }
    }

    /** A named theoretical centre relative to the confirmed tonic. */
    public record TheoryPitch(String noteName, double relativeCent,
                              double relativeHc, double hertz) {
        public TheoryPitch {
            noteName = nonBlank(noteName, "—");
        }
    }

    public record TonicEvidence(String method, double hertz, double startSeconds,
                                double endSeconds, double confidence,
                                double robustSpreadCent, int voicedFrameCount,
                                boolean userConfirmed, String note) {
        public TonicEvidence {
            method = nonBlank(method, "—");
            note = Objects.requireNonNullElse(note, "");
        }
    }

    public record TracePoint(double timeSeconds, double hertz, double confidence,
                             boolean accepted) { }

    /** Extra phrase evidence kept separate so ordinary reports remain unchanged. */
    public record DirectionalContext(int eventIndex, String usageClass,
                                     String pathLabel, String previousAnchor,
                                     String nextAnchor, double localTonicHz,
                                     double localDeviationCent,
                                     double classificationConfidence) {
        public static final DirectionalContext EMPTY = new DirectionalContext(
                -1, "", "", "", "", Double.NaN, Double.NaN, Double.NaN);

        public DirectionalContext {
            usageClass = Objects.requireNonNullElse(usageClass, "");
            pathLabel = Objects.requireNonNullElse(pathLabel, "");
            previousAnchor = Objects.requireNonNullElse(previousAnchor, "");
            nextAnchor = Objects.requireNonNullElse(nextAnchor, "");
        }

        public double localDeviationHc() { return localDeviationCent / HC_CENT; }
        public boolean present() { return eventIndex >= 0; }
    }

    /** One reference-profile row in the ascending or descending first-four-path comparison. */
    public record DirectionalFirstFourProfile(
            String direction,
            int rank,
            String profileName,
            int phraseCount,
            int evidenceEventCount,
            int timeRegionCount,
            Map<String, Integer> patternCounts,
            String evidenceStatus,
            double scorePercent,
            double coveragePercent,
            double medianAbsoluteErrorCent,
            double p90AbsoluteErrorCent,
            double meanSignedErrorCent
    ) {
        public DirectionalFirstFourProfile {
            direction = nonBlank(direction, "—");
            profileName = nonBlank(profileName, "—");
            patternCounts = Collections.unmodifiableMap(new LinkedHashMap<>(
                    patternCounts == null ? Map.of() : patternCounts));
            evidenceStatus = nonBlank(evidenceStatus, "INSUFFICIENT");
        }

        public double medianAbsoluteErrorHc() { return medianAbsoluteErrorCent / HC_CENT; }
        public double p90AbsoluteErrorHc() { return p90AbsoluteErrorCent / HC_CENT; }
        public double meanSignedErrorHc() { return meanSignedErrorCent / HC_CENT; }
    }

    /** One preserved mode of the tonic-aligned first-four-domain inventory. */
    public record FirstFourPeak(
            int degree,
            double centerCent,
            double centerHc,
            int eventCount,
            double totalDurationSeconds,
            double evidenceWeight,
            double meanConfidence,
            boolean dominant
    ) { }

    /** Candidate-independent ordered-path evidence used for tetrachord comparison. */
    public record FirstFourPhraseEvidence(
            String status,
            int qualifyingPhraseCount,
            int completePhraseCount,
            int partialPhraseCount,
            int evidenceEventCount,
            List<FirstFourPeak> degreePeaks,
        com.makambox.studio.study.CadenceSimilarity.Result cadence
    ) {
        public FirstFourPhraseEvidence(String status, int qualifyingPhraseCount,
                                       int completePhraseCount, int partialPhraseCount,
                                       int evidenceEventCount, List<FirstFourPeak> degreePeaks) {
            this(status, qualifyingPhraseCount, completePhraseCount,
                    partialPhraseCount, evidenceEventCount, degreePeaks, null);
        }

        public static final FirstFourPhraseEvidence EMPTY = new FirstFourPhraseEvidence(
                "INSUFFICIENT", 0, 0, 0, 0, List.of());

        public FirstFourPhraseEvidence {
            status = nonBlank(status, "INSUFFICIENT");
            degreePeaks = List.copyOf(degreePeaks == null ? List.of() : degreePeaks);
        }

        public boolean complete() { return "COMPLETE".equals(status); }
        public boolean partialOrMixed() { return "PARTIAL_MIXED".equals(status); }
    }

    public record Event(int index, String noteName, double startSeconds, double endSeconds,
                        double observedHz, double relativeCent,
                        double theoreticalCent, double deviationCent,
                        double entryDeviationCent, double settledDeviationCent,
                        double exitDeviationCent, double stabilityCent,
                        double confidence, String direction, String eventType,
                        boolean measurable, boolean stable, boolean included,
                        String exclusionReason) {
        public Event(int index, double startSeconds, double endSeconds,
                     double observedHz, double relativeCent,
                     double theoreticalCent, double deviationCent,
                     double entryDeviationCent, double settledDeviationCent,
                     double exitDeviationCent, double stabilityCent,
                     double confidence, String direction, String eventType,
                     boolean measurable, boolean stable, boolean included,
                     String exclusionReason) {
            this(index, "", startSeconds, endSeconds, observedHz, relativeCent,
                    theoreticalCent, deviationCent, entryDeviationCent,
                    settledDeviationCent, exitDeviationCent, stabilityCent,
                    confidence, direction, eventType, measurable, stable,
                    included, exclusionReason);
        }

        public Event {
            noteName = Objects.requireNonNullElse(noteName, "");
            direction = nonBlank(direction, "Yatay/Karma");
            eventType = nonBlank(eventType, measurable ? "Ölçülebilir" : "Kısa temas");
            exclusionReason = Objects.requireNonNullElse(exclusionReason, "");
        }

        public double durationSeconds() { return Math.max(0.0, endSeconds - startSeconds); }
        public double relativeHc() { return relativeCent / HC_CENT; }
        public double deviationHc() { return deviationCent / HC_CENT; }
        public String intonationLabel() {
            if (noteName.startsWith("Adlandırılamayan") && !exclusionReason.isBlank()) {
                return exclusionReason;
            }
            if (Math.abs(deviationCent) < 0.05) return "Kuramsal merkez";
            return deviationCent < 0.0 ? "Pest" : "Dik";
        }
    }

    public record Statistics(int count, double totalDurationSeconds,
                             double meanCent, double medianCent,
                             double weightedMeanCent, double standardDeviationCent,
                             int pestCount, int dikCount, int withinOneHcCount,
                             double entryMeanCent, double settledMeanCent,
                             double exitMeanCent) {
        public static Statistics from(List<Event> events) {
            List<Event> included = events.stream().filter(Event::included).toList();
            if (included.isEmpty()) return new Statistics(0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0);
            double duration = included.stream().mapToDouble(Event::durationSeconds).sum();
            double mean = included.stream().mapToDouble(Event::deviationCent).average().orElse(0.0);
            double[] ordered = included.stream().mapToDouble(Event::deviationCent).sorted().toArray();
            double median = ordered.length % 2 == 0
                    ? (ordered[ordered.length / 2 - 1] + ordered[ordered.length / 2]) / 2.0
                    : ordered[ordered.length / 2];
            double weighted = duration <= 0.0 ? mean : included.stream()
                    .mapToDouble(event -> event.deviationCent() * event.durationSeconds()).sum() / duration;
            double variance = included.stream().mapToDouble(event ->
                    Math.pow(event.deviationCent() - mean, 2.0)).average().orElse(0.0);
            int pest = (int) included.stream().filter(event -> event.deviationCent() < -0.05).count();
            int dik = (int) included.stream().filter(event -> event.deviationCent() > 0.05).count();
            int within = (int) included.stream()
                    .filter(event -> Math.abs(event.deviationCent()) <= HC_CENT).count();
            return new Statistics(included.size(), duration, mean, median, weighted,
                    Math.sqrt(variance), pest, dik, within,
                    included.stream().mapToDouble(Event::entryDeviationCent).average().orElse(0.0),
                    included.stream().mapToDouble(Event::settledDeviationCent).average().orElse(0.0),
                    included.stream().mapToDouble(Event::exitDeviationCent).average().orElse(0.0));
        }

        public double medianHc() { return medianCent / HC_CENT; }
        public double meanHc() { return meanCent / HC_CENT; }
        public double weightedMeanHc() { return weightedMeanCent / HC_CENT; }
        public double withinOneHcPercent() {
            return count == 0 ? 0.0 : withinOneHcCount * 100.0 / count;
        }
    }

    public record ProfileComparison(int rank, String profileName, double scorePercent,
                                    double medianAbsoluteErrorCent,
                                    double p90AbsoluteErrorCent,
                                    double withinOneHcPercent,
                                    double degreeCoveragePercent,
                                    int evidenceEventCount,
                                    String verdict, boolean indistinguishable,
                                    double firstFourScorePercent,
                                    int firstFourObservedCount,
                                    List<DegreeComparison> firstFourDegrees,
                                    boolean evidenceSufficient,
                                    String evidenceSummary,
                                    String firstFourSignature,
                                    List<String> firstFourIndistinguishableProfiles) {
        public ProfileComparison {
            profileName = nonBlank(profileName, "—");
            verdict = Objects.requireNonNullElse(verdict, "");
            firstFourDegrees = List.copyOf(firstFourDegrees == null ? List.of() : firstFourDegrees);
            evidenceSummary = Objects.requireNonNullElse(evidenceSummary, "");
            firstFourSignature = Objects.requireNonNullElse(firstFourSignature, "");
            firstFourIndistinguishableProfiles = List.copyOf(firstFourIndistinguishableProfiles == null
                    ? List.of() : firstFourIndistinguishableProfiles);
        }

        /** Backward-compatible constructor for report fixtures that predate first-four-degree evidence. */
        public ProfileComparison(int rank, String profileName, double scorePercent,
                                 double medianAbsoluteErrorCent, double p90AbsoluteErrorCent,
                                 double withinOneHcPercent, double degreeCoveragePercent,
                                 int evidenceEventCount, String verdict, boolean indistinguishable) {
            this(rank, profileName, scorePercent, medianAbsoluteErrorCent, p90AbsoluteErrorCent,
                    withinOneHcPercent, degreeCoveragePercent, evidenceEventCount, verdict,
                    indistinguishable, 0.0, 0, List.of(), true, "", "",
                    List.of(nonBlank(profileName, "—")));
        }

        /** Backward-compatible constructor for callers that already supply first-four rows. */
        public ProfileComparison(int rank, String profileName, double scorePercent,
                                 double medianAbsoluteErrorCent, double p90AbsoluteErrorCent,
                                 double withinOneHcPercent, double degreeCoveragePercent,
                                 int evidenceEventCount, String verdict, boolean indistinguishable,
                                 double firstFourScorePercent, int firstFourObservedCount,
                                 List<DegreeComparison> firstFourDegrees) {
            this(rank, profileName, scorePercent, medianAbsoluteErrorCent, p90AbsoluteErrorCent,
                    withinOneHcPercent, degreeCoveragePercent, evidenceEventCount, verdict,
                    indistinguishable, firstFourScorePercent, firstFourObservedCount,
                    firstFourDegrees, true, "", signature(firstFourDegrees),
                    List.of(nonBlank(profileName, "—")));
        }

        private static String signature(List<DegreeComparison> degrees) {
            if (degrees == null || degrees.isEmpty()) return "";
            return degrees.stream().map(value -> Long.toString(Math.round(value.theoreticalCent() * 1000.0)))
                    .collect(java.util.stream.Collectors.joining(","));
        }
    }

    /** One of the first four theoretical degrees, counted from the tonic (degree 1). */
    public record DegreeComparison(int degree, double theoreticalCent, double theoreticalHc,
                                   boolean observed, double observedCent, double observedHc,
                                   double residualCent, double residualHc,
                                   int eventCount, double totalDurationSeconds) { }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public String shortConclusion() {
        Statistics all = measurableStatistics();
        Statistics stable = stableStatistics();
        if (all.count() == 0) return ReportLocalization.text("report.noMeasurableTargetEvent",
                "No measurable target-pitch event was found.",
                "Ölçülebilir hedef-perde olayı bulunamadı.");
        if (directionalSegahAnalysis()) {
            return ReportLocalization.text("report.shortConclusion.directional",
                    "For {0} reliable Segâh events, the overall mean deviation was {1} C ({2} Hc) and the median deviation was {3} C ({4} Hc).",
                    "{0} güvenilir Segâh olayının genel ortalama sapması {1} C ({2} Hc), medyan sapması {3} C ({4} Hc) olarak hesaplandı.",
                    all.count(), ReportLocalization.signed(all.meanCent(), 2),
                    ReportLocalization.signed(all.meanHc(), 3), ReportLocalization.signed(all.medianCent(), 2),
                    ReportLocalization.signed(all.medianHc(), 3));
        }
        return ReportLocalization.text("report.shortConclusion.standard",
                "The median deviation of {0} measurable events was {1} C ({2} Hc); the median of {3} stable events was {4} C ({5} Hc).",
                "{0} ölçülebilir olayın medyan sapması {1} C ({2} Hc); {3} kararlı olayın medyanı {4} C ({5} Hc).",
                all.count(), ReportLocalization.signed(all.medianCent(), 2),
                ReportLocalization.signed(all.medianHc(), 3), stable.count(),
                ReportLocalization.signed(stable.medianCent(), 2), ReportLocalization.signed(stable.medianHc(), 3));
    }
}
