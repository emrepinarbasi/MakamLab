package com.makambox.studio.study;

import com.makambox.studio.analysis.AllPitchEventAnalyzer;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.MelodicDirection;
import com.makambox.studio.model.PitchEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Builds the single source of truth used by Makam's UI, DOCX and CSV reports. */
public final class SinglePitchStudyService {
    public static final double STABLE_EVENT_MAXIMUM_SPREAD_HC = 0.55;
    public static final double STABLE_EVENT_MINIMUM_CONFIDENCE = 0.65;

    private final MakamProfileComparator profileComparator;

    public SinglePitchStudyService() {
        this(new MakamProfileComparator());
    }

    public SinglePitchStudyService(MakamProfileComparator profileComparator) {
        this.profileComparator = profileComparator;
    }

    public SinglePitchStudyResult analyze(SinglePitchStudyRequest request) {
        AnalysisConfig config = studyConfig(request);
        List<PitchEvent> canonical = new AllPitchEventAnalyzer().analyze(
                request.frames(), config, request.theory(), request.makam());
        double tonicCent = hertzToCent(request.tonicSelection().hertz());
        List<StudyPitchEvent> allEvents = canonical.stream()
                .map(value -> StudyPitchEvent.from(value, tonicCent)).toList();
        List<StudyPitchEvent> targetContacts = allEvents.stream()
                .filter(value -> PitchClassInterval.samePitchClass(
                        value.theoreticalRelativeCent(),
                        request.theory().relativeCent(request.makam(), request.target())))
                .toList();
        List<StudyPitchEvent> measurable = targetContacts.stream()
                .filter(StudyPitchEvent::measurable).toList();
        List<StudyPitchEvent> stable = measurable.stream()
                .filter(value -> value.stabilityCent()
                        <= STABLE_EVENT_MAXIMUM_SPREAD_HC * AnalysisConfig.COMMA_CENT + 1.0e-9)
                .filter(value -> value.confidence() + 1.0e-9 >= STABLE_EVENT_MINIMUM_CONFIDENCE)
                .toList();

        Map<MelodicDirection, StudyStatistics> byDirection = statisticsByDirection(measurable);
        Map<MelodicDirection, StudyStatistics> stableByDirection = statisticsByDirection(stable);
        double theoreticalCent = request.theory().relativeCent(request.makam(), request.target());
        double expectedHertz = request.tonicSelection().hertz() * Math.pow(2.0, theoreticalCent / 1200.0);
        MakamProfileComparison comparison = profileComparator.compare(canonical,
                request.tonicSelection().hertz(), request.makam().name(),
                request.profileReferenceSystem(), request.firstFourProfileSelection());

        Path source = request.audio().source();
        return new SinglePitchStudyResult(
                request.audio().name(), source == null ? "" : source.toAbsolutePath().toString(),
                sourceSha256(source), request.audio().durationSeconds(), request.audio().sampleRate(),
                request.audio().sourceChannelCount(), request.audio().channelDescription(),
                request.applicationVersion(), ZonedDateTime.now(), request.makam().toString(),
                request.theory().tonicFor(request.makam()).toString(), request.target().key(),
                request.target().toString(), request.tonicSelection(), config,
                theoreticalCent, theoreticalCent / AnalysisConfig.COMMA_CENT, expectedHertz,
                allEvents, targetContacts, measurable, stable,
                StudyStatistics.from(measurable), StudyStatistics.from(stable),
                byDirection, stableByDirection, comparison);
    }

    private static AnalysisConfig studyConfig(SinglePitchStudyRequest request) {
        AnalysisConfig template = request.analysisTemplate();
        boolean automatic = request.tonicSelection().source()
                == TonicSelection.Source.AUTOMATIC_FINAL_NOTE;
        return new AnalysisConfig(request.makam().toString(),
                request.theory().tonicFor(request.makam()).toString(), request.target().toString(),
                request.tonicSelection().hertz(), request.theory().relativeCent(request.makam(), request.target()),
                template.targetLowerToleranceComma(), template.targetUpperToleranceComma(),
                template.similarityToleranceComma(), template.minimumOccurrenceSeconds(),
                template.passingMaximumSeconds(), template.holdMinimumSeconds(),
                template.phraseSilenceSeconds(), template.directionThresholdComma(),
                template.maximumStableSpreadComma(), template.minimumGlissandoSeconds(),
                template.minimumGlissandoSpanComma(), template.minimumGlissandoMonotonicity(),
                template.maximumGlissandoFrameStepCent(), template.maximumAutomaticGlissandoSpanCent(),
                template.glissandoTargetToleranceComma(), template.minimumGlissandoTargetContactSeconds(),
                template.maximumGlissandoTrajectoryResidualCent(), automatic);
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
