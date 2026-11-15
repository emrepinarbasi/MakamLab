package com.makambox.studio.ui;

import com.makambox.studio.model.AnalysisReport;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.study.FirstFourProfileSelection;
import com.makambox.studio.study.UssakSegahDirectionalStudyResult;
import com.makambox.studio.study.glissando.PitchPairGlissandoStudyResult;
import com.makambox.studio.study.hicaz.HicazAugmentedIntervalStudyResult;
import com.makambox.studio.theory.TheoryCatalog;

import java.nio.file.Path;
import java.util.List;

/** Immutable hand-off from the guided Makam workspace to report exporters. */
public record MakamStudyContext(
        Path sourcePath,
        AudioData audio,
        List<PitchFrame> frames,
        AnalysisReport report,
        List<PitchEvent> allPitchEvents,
        List<PitchEvent> targetPitchEvents,
        TheoryCatalog.Makam makam,
        MakamAnalysisMode analysisMode,
        TheoryCatalog.Note target,
        TheoryCatalog.Note lowerBoundary,
        TheoryCatalog.Note upperBoundary,
        double tonicHertz,
        String tonicMethod,
        double tonicStartSeconds,
        double tonicEndSeconds,
        double tonicConfidence,
        UssakSegahDirectionalStudyResult directionalSegahStudy,
        HicazAugmentedIntervalStudyResult hicazStudy,
        PitchPairGlissandoStudyResult glissandoStudy,
        FirstFourProfileSelection firstFourProfileSelection
) {
    public MakamStudyContext {
        frames = List.copyOf(frames);
        allPitchEvents = List.copyOf(allPitchEvents);
        targetPitchEvents = List.copyOf(targetPitchEvents);
        analysisMode = analysisMode == null ? MakamAnalysisMode.SINGLE_PITCH : analysisMode;
        tonicMethod = tonicMethod == null || tonicMethod.isBlank() ? "Belirtilmedi" : tonicMethod;
        firstFourProfileSelection = firstFourProfileSelection == null
                ? FirstFourProfileSelection.defaults() : firstFourProfileSelection;
        if (report != null && Double.isFinite(tonicHertz)
                && Double.doubleToLongBits(report.config().tonicHz())
                != Double.doubleToLongBits(tonicHertz)) {
            throw new IllegalArgumentException(
                    "Report tonic must equal the immutable analysis tonic");
        }
    }

    /** Compatibility hand-off using the six-profile product default. */
    public MakamStudyContext(Path sourcePath, AudioData audio, List<PitchFrame> frames,
                             AnalysisReport report, List<PitchEvent> allPitchEvents,
                             List<PitchEvent> targetPitchEvents, TheoryCatalog.Makam makam,
                             MakamAnalysisMode analysisMode, TheoryCatalog.Note target,
                             TheoryCatalog.Note lowerBoundary, TheoryCatalog.Note upperBoundary,
                             double tonicHertz, String tonicMethod,
                             double tonicStartSeconds, double tonicEndSeconds,
                             double tonicConfidence,
                             UssakSegahDirectionalStudyResult directionalSegahStudy,
                             HicazAugmentedIntervalStudyResult hicazStudy,
                             PitchPairGlissandoStudyResult glissandoStudy) {
        this(sourcePath, audio, frames, report, allPitchEvents, targetPitchEvents,
                makam, analysisMode, target, lowerBoundary, upperBoundary,
                tonicHertz, tonicMethod, tonicStartSeconds, tonicEndSeconds,
                tonicConfidence, directionalSegahStudy, hicazStudy, glissandoStudy,
                FirstFourProfileSelection.defaults());
    }
}
