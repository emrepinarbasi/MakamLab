package com.makambox.studio.study;

import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.AudioChannelMode;
import com.makambox.studio.model.EventType;
import com.makambox.studio.model.MelodicDirection;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.model.PitchOccurrence;
import com.makambox.studio.theory.TheoryCatalog;

import java.util.ArrayList;
import java.util.List;

/** Regression coverage for the report data contract and interval-profile comparison. */
public final class SinglePitchStudyServiceRegressionTest {
    private static final double STEP = 0.020;
    private static final double TONIC_HZ = 220.0;
    private static final double TONIC_CENT = PitchTracker.hertzToCent(TONIC_HZ);
    private static final TheoryCatalog THEORY = TheoryCatalog.loadDefault();
    private static final TheoryCatalog.Makam MAKAM = THEORY.makams().stream()
            .filter(value -> value.name().equals("Kurdilihicazkar")).findFirst().orElseThrow();
    private static final TheoryCatalog.Note TARGET = THEORY.note("nim zirgule");

    public static void main(String[] args) {
        verifyCanonicalTargetStudy();
        verifyOctaveEquivalentTargetStudy();
        verifyProfileGroupingAndRanking();
        System.out.println("SINGLE_PITCH_STUDY_SERVICE_REGRESSION_OK");
    }

    private static void verifyCanonicalTargetStudy() {
        List<PitchFrame> frames = new ArrayList<>();
        addNote(frames, "rast", 7, 0.94, 0.0);
        addSilence(frames, 5);
        addNote(frames, "nim zirgule", 7, 0.94, -5.0);
        addSilence(frames, 5);
        addNote(frames, "nim zirgule", 8, 0.95, 3.0);
        addSilence(frames, 6);
        addNote(frames, "zirgule", 7, 0.94, 0.0);

        AudioData audio = new AudioData("sentetik.wav", null, 22_050f,
                new float[Math.max(1, (int) Math.ceil(frames.size() * STEP * 22_050))],
                AudioChannelMode.RIGHT, 2);
        TonicSelection tonic = new TonicSelection(TONIC_HZ,
                TonicSelection.Source.GRAPH_REGION, 0.0, 0.14, 0.94, 2.0, 7,
                "Sağ kanal", true);
        SinglePitchStudyRequest request = new SinglePitchStudyRequest(audio, frames, THEORY,
                MAKAM, TARGET, tonic, config(), "test", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM);
        SinglePitchStudyResult result = new SinglePitchStudyService().analyze(request);

        check(result.allTargetContacts().size() == 2,
                "yalnız kanonik Nim Zirgüle kimliği hedef olaylara girmeli");
        check(result.measurableTargetEvents().size() == 2, "iki ölçülebilir olay bekleniyor");
        check(result.stableTargetEvents().size() == 2, "kararlı olaylar korunmalı");
        check(result.measuredStatistics().pestCount() == 1
                        && result.measuredStatistics().dikCount() == 1,
                "pest/dik sayıları rapor sözleşmesinde tutulmalı");
        check(Math.abs(result.measuredStatistics().medianDeviationCent() + 1.0) < 0.01,
                "medyan sapma olay merkezlerinden hesaplanmalı");
        check(Math.abs(result.theoreticalTargetHc() - 4.0) < 0.001,
                "Kürdilihicazkâr Nim Zirgüle +4 Hc olmalı");
        check(result.directionStatistics().size() == 3, "üç yön grubu her zaman bulunmalı");
    }

    private static void verifyProfileGroupingAndRanking() {
        double[] intervals = {0.0, 90.56604, 294.33963, 498.11322};
        List<PitchEvent> events = new ArrayList<>();
        int index = 1;
        for (int repeat = 0; repeat < 3; repeat++) {
            for (double interval : intervals) {
                double start = repeat * 8.0 + interval / 300.0;
                double observed = TONIC_CENT + interval + (repeat - 1) * 1.5;
                PitchOccurrence occurrence = new PitchOccurrence(index++, start, start + 0.45,
                        observed, TONIC_CENT + interval, 2.0, 0.0, 0.0, 0.0, 4.0,
                        false, 0.94, MelodicDirection.LEVEL, MelodicDirection.LEVEL,
                        MelodicDirection.LEVEL, EventType.SUSTAINED);
                events.add(new PitchEvent("test " + index, "Test", interval, occurrence));
            }
        }
        MakamProfileComparison comparison = new MakamProfileComparator().compare(
                events, TONIC_HZ, "Kurdilihicazkar");
        check(comparison.sufficientEvidence(), "dört derece ve 12 olay yeterli kanıt olmalı");
        check(comparison.candidates().size() == 5,
                "ilk dört ses karşılaştırması varsayılan olarak beş temel referans göstermeli");
        MakamProfileComparison.Candidate selected = comparison.candidates().stream()
                .filter(MakamProfileComparison.Candidate::selectedProfile).findFirst().orElseThrow();
        check(selected.makamName().equals("Kürdî"),
                "Kürdilihicazkâr seçimi 14 referans içindeki Kürdî ailesini işaretlemeli");
        check(selected.scorePercent() > 90.0, "temiz kuramsal profil yüksek benzerlik vermeli");
        MakamProfileComparison.Candidate ussak = comparison.candidates().stream()
                .filter(value -> value.makamName().equals("Uşşak"))
                .findFirst().orElseThrow();
        check(selected.scorePercent() > ussak.scorePercent(),
                "90,6 C ikinci derece Kürdilihicazkâr profilini Uşşak'tan ayırmalı");
    }

    private static void verifyOctaveEquivalentTargetStudy() {
        List<PitchFrame> frames = new ArrayList<>();
        double relative = THEORY.relativeCent(MAKAM, TARGET) + 1200.0;
        double absolute = TONIC_CENT + relative;
        for (int index = 0; index < 8; index++) {
            frames.add(new PitchFrame(index * STEP, PitchTracker.centToHertz(absolute),
                    absolute, 0.95, 0.15));
        }
        AudioData audio = new AudioData("oktav-hedef.wav", null, 22_050f,
                new float[Math.max(1, (int) Math.ceil(frames.size() * STEP * 22_050))],
                AudioChannelMode.MIX, 1);
        TonicSelection tonic = new TonicSelection(TONIC_HZ,
                TonicSelection.Source.GRAPH_REGION, 0.0, 0.14, 0.95, 2.0, 7,
                "Mono", true);
        SinglePitchStudyResult result = new SinglePitchStudyService().analyze(
                new SinglePitchStudyRequest(audio, frames, THEORY, MAKAM, TARGET, tonic,
                        config(), "test", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM));
        check(result.allTargetContacts().size() == 1,
                "hedef perdenin bir üst oktavdaki eşdeğeri tek-perde analizine girmeli");
    }

    private static void addNote(List<PitchFrame> frames, String key, int count,
                                double confidence, double offsetCent) {
        double relative = THEORY.relativeCent(MAKAM, THEORY.note(key));
        double absolute = TONIC_CENT + relative + offsetCent;
        for (int i = 0; i < count; i++) {
            frames.add(new PitchFrame(frames.size() * STEP,
                    PitchTracker.centToHertz(absolute), absolute, confidence, 0.15));
        }
    }

    private static void addSilence(List<PitchFrame> frames, int count) {
        for (int i = 0; i < count; i++) {
            frames.add(new PitchFrame(frames.size() * STEP, 0.0, Double.NaN, 0.0, 0.0));
        }
    }

    private static AnalysisConfig config() {
        return new AnalysisConfig(MAKAM.toString(), THEORY.tonicFor(MAKAM).toString(), TARGET.toString(),
                TONIC_HZ, THEORY.relativeCent(MAKAM, TARGET), 2.0, 2.0, 1.0,
                0.10, 0.35, 0.70, 0.28, 0.75, 0.55,
                0.12, 1.10, 0.72, 100.0, 700.0, 1.5, 0.02, 32.0, false);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
