package com.makambox.studio.study;

import com.makambox.studio.analysis.AllPitchEventAnalyzer;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.MelodicDirection;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.theory.TheoryCatalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds a study of canonical contacts strictly between two excluded pitch boundaries. */
public final class PitchIntervalStudyService {
    private final MakamProfileComparator profileComparator;

    public PitchIntervalStudyService() {
        this(new MakamProfileComparator());
    }

    public PitchIntervalStudyService(MakamProfileComparator profileComparator) {
        this.profileComparator = profileComparator;
    }

    public PitchIntervalStudyResult analyze(PitchIntervalStudyRequest request) {
        double firstCent = request.theory().relativeCent(request.makam(), request.firstBoundary());
        double secondCent = request.theory().relativeCent(request.makam(), request.secondBoundary());
        TheoryCatalog.Note lower = firstCent <= secondCent
                ? request.firstBoundary() : request.secondBoundary();
        TheoryCatalog.Note upper = firstCent <= secondCent
                ? request.secondBoundary() : request.firstBoundary();
        double lowerCent = Math.min(firstCent, secondCent);
        double upperCent = Math.max(firstCent, secondCent);

        AnalysisConfig config = intervalConfig(request, lower, upper, lowerCent, upperCent);
        List<PitchEvent> canonical = new AllPitchEventAnalyzer().analyzePitchClasses(
                request.frames(), config, request.theory(), request.makam());
        double tonicAbsoluteCent = hertzToCent(request.tonicSelection().hertz());
        List<StudyPitchEvent> allEvents = canonical.stream()
                .map(value -> StudyPitchEvent.from(value, tonicAbsoluteCent)).toList();
        List<PitchClassInterval.RegisterSelection> selectedRegisters =
                PitchClassInterval.selectViableRegisters(canonical,
                        tonicAbsoluteCent, lowerCent, upperCent, config);

        // This is intentionally an open interval.  A confidently named event must have
        // both its theory identity and observed F0 strictly inside the selected bounds.
        // An in-between event farther than the naming tolerance from its nearest theory
        // centre remains analyzable as "Adlandirilamayan" on observed-F0 evidence alone.
        // This matters near a boundary: an event 2 Hc above Rast is inside Rast–Dügâh,
        // even though a mandatory nearest-centre assignment still points to Rast.  A true
        // Rast within the naming tolerance remains excluded.
        List<Integer> selectedOffsets = selectedRegisters.stream()
                .map(PitchClassInterval.RegisterSelection::octaveOffset).toList();
        List<StudyPitchEvent> contacts = new ArrayList<>();
        for (PitchEvent value : canonical) {
            int octaveOffset = PitchClassInterval.registerOffset(value,
                    tonicAbsoluteCent, lowerCent, upperCent);
            if (!selectedOffsets.contains(octaveOffset)
                    || !PitchClassInterval.containsInRegister(value,
                    tonicAbsoluteCent, lowerCent, upperCent, octaveOffset, config)) {
                continue;
            }
            PitchEvent normalized = PitchClassInterval.normalizeEvent(value,
                    octaveOffset, request.theory(), request.makam());
            contacts.add(normalizeSelectedRegister(
                    StudyPitchEvent.from(normalized, tonicAbsoluteCent), octaveOffset));
        }
        contacts = List.copyOf(contacts);
        List<StudyPitchEvent> measurable = contacts.stream()
                .filter(IntervalEventTier::statisticallyMeasurable).toList();
        List<StudyPitchEvent> stable = measurable.stream()
                .filter(PitchIntervalStudyService::stable).toList();

        Map<MelodicDirection, StudyStatistics> byDirection = statisticsByDirection(measurable);
        Map<MelodicDirection, StudyStatistics> stableByDirection = statisticsByDirection(stable);
        List<PitchIntervalNoteSummary> noteSummaries = noteSummaries(contacts,
                request.tonicSelection().hertz(), config);
        MakamProfileComparison comparison = profileComparator.compare(canonical,
                request.tonicSelection().hertz(), request.makam().name(),
                request.profileReferenceSystem(), request.firstFourProfileSelection());

        Path source = request.audio().source();
        double lowerHz = request.tonicSelection().hertz() * Math.pow(2.0, lowerCent / 1200.0);
        double upperHz = request.tonicSelection().hertz() * Math.pow(2.0, upperCent / 1200.0);
        return new PitchIntervalStudyResult(
                request.audio().name(), source == null ? "" : source.toAbsolutePath().toString(),
                sourceSha256(source), request.audio().durationSeconds(), request.audio().sampleRate(),
                request.audio().sourceChannelCount(), request.audio().channelDescription(),
                request.applicationVersion(), ZonedDateTime.now(), request.makam().toString(),
                request.theory().tonicFor(request.makam()).toString(), lower.key(), lower.toString(),
                upper.key(), upper.toString(), request.tonicSelection(), config,
                lowerCent, upperCent, lowerCent / AnalysisConfig.COMMA_CENT,
                upperCent / AnalysisConfig.COMMA_CENT, lowerHz, upperHz,
                allEvents, contacts, measurable, stable,
                PitchIntervalDistribution.from(measurable), StudyStatistics.from(measurable),
                StudyStatistics.from(stable), byDirection, stableByDirection,
                noteSummaries, comparison);
    }

    private static boolean stable(StudyPitchEvent value) {
        return value.stabilityCent()
                <= SinglePitchStudyService.STABLE_EVENT_MAXIMUM_SPREAD_HC
                * AnalysisConfig.COMMA_CENT + 1.0e-9
                && value.confidence() + 1.0e-9
                >= SinglePitchStudyService.STABLE_EVENT_MINIMUM_CONFIDENCE;
    }

    private static StudyPitchEvent normalizeSelectedRegister(StudyPitchEvent value,
                                                              int octaveOffset) {
        double theoretical = value.theoreticalRelativeCent();
        double observed = value.observedRelativeCent()
                - octaveOffset * PitchClassInterval.OCTAVE_CENT;
        double deviation = observed - theoretical;
        // Keep phase deviations in the same reference register as the event centre.
        double deviationShift = deviation - value.deviationCent();
        return new StudyPitchEvent(value.index(), value.noteKey(), value.noteName(),
                value.startSeconds(), value.endSeconds(), value.durationSeconds(),
                value.observedHertz(), theoretical, observed,
                observed / AnalysisConfig.COMMA_CENT,
                deviation, deviation / AnalysisConfig.COMMA_CENT,
                value.entryDeviationCent() + deviationShift,
                value.settledDeviationCent() + deviationShift,
                value.exitDeviationCent() + deviationShift,
                value.stabilityCent(), value.vibratoSpanCent(),
                value.confidence(), value.entryDirection(), value.exitDirection(),
                value.direction(), value.eventType(), value.phraseFinal());
    }

    private static List<PitchIntervalNoteSummary> noteSummaries(
            List<StudyPitchEvent> contacts, double tonicHertz, AnalysisConfig config) {
        Map<String, List<StudyPitchEvent>> grouped = new LinkedHashMap<>();
        contacts.stream().sorted(Comparator.comparingDouble(StudyPitchEvent::observedRelativeCent)
                        .thenComparingDouble(StudyPitchEvent::startSeconds))
                .forEach(value -> {
                    boolean named = IntervalPitchNaming.isCanonicallyNamed(value, config);
                    String key = (named ? "named:" : "unnamed:") + value.noteKey();
                    grouped.computeIfAbsent(key, ignored -> new ArrayList<>())
                            .add(value);
                });
        List<PitchIntervalNoteSummary> summaries = new ArrayList<>();
        for (List<StudyPitchEvent> values : grouped.values()) {
            StudyPitchEvent first = values.get(0);
            List<StudyPitchEvent> measurable = values.stream()
                    .filter(IntervalEventTier::statisticallyMeasurable).toList();
            List<StudyPitchEvent> stable = measurable.stream()
                    .filter(PitchIntervalStudyService::stable).toList();
            double theoreticalHz = tonicHertz
                    * Math.pow(2.0, first.theoreticalRelativeCent() / 1200.0);
            summaries.add(new PitchIntervalNoteSummary(first.noteKey(),
                    IntervalPitchNaming.displayName(first, config),
                    first.theoreticalRelativeCent(),
                    first.theoreticalRelativeCent() / AnalysisConfig.COMMA_CENT,
                    theoreticalHz, values, measurable, stable,
                    StudyStatistics.from(measurable), StudyStatistics.from(stable)));
        }
        return List.copyOf(summaries);
    }

    private static Map<MelodicDirection, StudyStatistics> statisticsByDirection(
            List<StudyPitchEvent> events) {
        EnumMap<MelodicDirection, StudyStatistics> result = new EnumMap<>(MelodicDirection.class);
        for (MelodicDirection direction : MelodicDirection.values()) {
            result.put(direction, StudyStatistics.from(events.stream()
                    .filter(value -> value.direction() == direction).toList()));
        }
        return result;
    }

    private static AnalysisConfig intervalConfig(PitchIntervalStudyRequest request,
                                                  TheoryCatalog.Note lower,
                                                  TheoryCatalog.Note upper,
                                                  double lowerCent,
                                                  double upperCent) {
        AnalysisConfig template = request.analysisTemplate();
        double midpoint = (lowerCent + upperCent) / 2.0;
        boolean automatic = request.tonicSelection().source()
                == TonicSelection.Source.AUTOMATIC_FINAL_NOTE;
        return new AnalysisConfig(request.makam().toString(),
                request.theory().tonicFor(request.makam()).toString(),
                lower + " – " + upper, request.tonicSelection().hertz(), midpoint,
                (midpoint - lowerCent) / AnalysisConfig.COMMA_CENT,
                (upperCent - midpoint) / AnalysisConfig.COMMA_CENT,
                template.similarityToleranceComma(), template.minimumOccurrenceSeconds(),
                template.passingMaximumSeconds(), template.holdMinimumSeconds(),
                template.phraseSilenceSeconds(), template.directionThresholdComma(),
                template.maximumStableSpreadComma(), template.minimumGlissandoSeconds(),
                template.minimumGlissandoSpanComma(), template.minimumGlissandoMonotonicity(),
                template.maximumGlissandoFrameStepCent(), template.maximumAutomaticGlissandoSpanCent(),
                template.glissandoTargetToleranceComma(), template.minimumGlissandoTargetContactSeconds(),
                template.maximumGlissandoTrajectoryResidualCent(), automatic);
    }

    private static String sourceSha256(Path source) {
        if (source == null || !Files.isRegularFile(source)) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream stream = Files.newInputStream(source)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = stream.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ignored) {
            return "";
        }
    }

    private static double hertzToCent(double hertz) {
        return 1200.0 * Math.log(hertz / 16.351597831287414) / Math.log(2.0);
    }
}
