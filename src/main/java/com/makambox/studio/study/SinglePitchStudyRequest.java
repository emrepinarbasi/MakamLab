package com.makambox.studio.study;

import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.theory.TheoryCatalog;

import java.util.List;
import java.util.Objects;

/** Complete, immutable input for one academically reproducible target-pitch study. */
public record SinglePitchStudyRequest(
        AudioData audio,
        List<PitchFrame> frames,
        TheoryCatalog theory,
        TheoryCatalog.Makam makam,
        TheoryCatalog.Note target,
        TonicSelection tonicSelection,
        AnalysisConfig analysisTemplate,
        String applicationVersion,
        String profileReferenceSystem,
        FirstFourProfileSelection firstFourProfileSelection
) {
    public SinglePitchStudyRequest {
        audio = Objects.requireNonNull(audio, "audio");
        frames = List.copyOf(Objects.requireNonNull(frames, "frames"));
        if (frames.isEmpty()) throw new IllegalArgumentException("Perde izi boş olamaz");
        theory = Objects.requireNonNull(theory, "theory");
        makam = Objects.requireNonNull(makam, "makam");
        target = Objects.requireNonNull(target, "target");
        tonicSelection = Objects.requireNonNull(tonicSelection, "tonicSelection");
        analysisTemplate = Objects.requireNonNull(analysisTemplate, "analysisTemplate");
        applicationVersion = applicationVersion == null || applicationVersion.isBlank()
                ? "Bilinmiyor" : applicationVersion.strip();
        profileReferenceSystem = profileReferenceSystem == null || profileReferenceSystem.isBlank()
                ? MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM : profileReferenceSystem.strip();
        firstFourProfileSelection = Objects.requireNonNullElseGet(firstFourProfileSelection,
                FirstFourProfileSelection::defaults);
    }

    /** Compatibility constructor using the six-profile product default. */
    public SinglePitchStudyRequest(AudioData audio, List<PitchFrame> frames,
                                   TheoryCatalog theory, TheoryCatalog.Makam makam,
                                   TheoryCatalog.Note target, TonicSelection tonicSelection,
                                   AnalysisConfig analysisTemplate, String applicationVersion,
                                   String profileReferenceSystem) {
        this(audio, frames, theory, makam, target, tonicSelection, analysisTemplate,
                applicationVersion, profileReferenceSystem, FirstFourProfileSelection.defaults());
    }
}
