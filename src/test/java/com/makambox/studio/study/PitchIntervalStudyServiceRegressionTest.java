package com.makambox.studio.study;

import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.AudioChannelMode;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.theory.TheoryCatalog;

import java.util.ArrayList;
import java.util.List;

/** Regression coverage for open, actual-F0 two-pitch interval selection. */
public final class PitchIntervalStudyServiceRegressionTest {
    private static final double STEP = 0.020;
    private static final double TONIC_HZ = 220.0;
    private static final double TONIC_CENT = PitchTracker.hertzToCent(TONIC_HZ);
    private static final TheoryCatalog THEORY = TheoryCatalog.loadDefault();
    private static final TheoryCatalog.Makam MAKAM = THEORY.makams().stream()
            .filter(value -> value.name().equals("Kurdilihicazkar")).findFirst().orElseThrow();

    public static void main(String[] args) {
        verifyOpenActualF0IntervalAndAutomaticBoundaryOrder();
        verifyIntervalEvidenceTiersStayConsistentAcrossSummaries();
        verifyEveryCoherentRegisterIsRetainedAndNormalized();
        verifyIsolatedOctaveNoiseDoesNotEnterCoherentRegisterEvidence();
        verifyIdenticalBoundariesRejected();
        System.out.println("PITCH_INTERVAL_STUDY_SERVICE_REGRESSION_OK");
    }

    private static void verifyIntervalEvidenceTiersStayConsistentAcrossSummaries() {
        List<PitchFrame> frames = new ArrayList<>();
        // Three 20-ms frames form a 60-ms, high-quality conditional event.
        addRelativeHc(frames, 4.0, 3, 0.95);
        addSilence(frames, 4);
        // Two frames form a 40-ms audit-only contact.
        addRelativeHc(frames, 5.0, 2, 0.95);
        addSilence(frames, 4);
        // Five frames form a 100-ms strong event and establish the register.
        addRelativeHc(frames, 9.0, 5, 0.95);

        AudioData audio = new AudioData("katmanli-aralik.wav", null, 22_050f,
                new float[Math.max(1, (int) Math.ceil(frames.size() * STEP * 22_050))],
                AudioChannelMode.MIX, 1);
        TonicSelection tonic = new TonicSelection(TONIC_HZ,
                TonicSelection.Source.GRAPH_REGION, 0.0, 0.14, 0.95, 2.0, 7,
                "Mono", true);
        PitchIntervalStudyRequest request = new PitchIntervalStudyRequest(audio, frames, THEORY,
                MAKAM, THEORY.note("rast"), THEORY.note("dik kurdi"), tonic, config(),
                "test", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM);
        PitchIntervalStudyResult result = new PitchIntervalStudyService().analyze(request);

        check(result.intervalContacts().size() == 3,
                "60, 40 ve 100 ms olayların üçü de denetim katmanında korunmalı");
        check(result.measurableIntervalEvents().size() == 2,
                "yalnız kaliteli 60 ms ve güçlü 100 ms olay ana istatistiğe girmeli");
        check(result.stableIntervalEvents().size() == 2,
                "iki ölçülebilir olay da kararlılık katmanında kalmalı");
        StudyPitchEvent conditional = result.intervalContacts().stream()
                .filter(value -> value.durationSeconds() >= 0.059
                        && value.durationSeconds() < 0.099).findFirst().orElseThrow();
        check(conditional.eventType() == com.makambox.studio.model.EventType.SHORT_CONTACT,
                "60 ms olay Kısa temas etiketiyle görünmeye devam etmeli");
        check(result.measurableIntervalEvents().contains(conditional),
                "Kısa temas etiketi kaliteli 60 ms olayı istatistikten dışlamamalı");
        int summaryContacts = result.noteSummaries().stream()
                .mapToInt(value -> value.contacts().size()).sum();
        int summaryMeasurable = result.noteSummaries().stream()
                .mapToInt(value -> value.measurableEvents().size()).sum();
        int summaryStable = result.noteSummaries().stream()
                .mapToInt(value -> value.stableEvents().size()).sum();
        check(summaryContacts == result.intervalContacts().size(),
                "perde özetindeki temas sayısı sonuçla aynı olmalı");
        check(summaryMeasurable == result.measurableIntervalEvents().size(),
                "perde özetindeki ölçülebilir sayı sonuçla aynı olmalı");
        check(summaryStable == result.stableIntervalEvents().size(),
                "perde özetindeki kararlı sayı sonuçla aynı olmalı");
    }

    private static void verifyOpenActualF0IntervalAndAutomaticBoundaryOrder() {
        List<PitchFrame> frames = new ArrayList<>();
        addNote(frames, "irak", 7, 0.95, 0.0);       // below Rast: excluded
        addSilence(frames, 4);
        addNote(frames, "rast", 7, 0.95, 18.0);      // sharp boundary: still excluded by identity
        addSilence(frames, 4);
        addRelativeHc(frames, 2.0, 7, 0.95);         // inside interval, too far from Rast to name
        addSilence(frames, 4);
        addNote(frames, "nim zirgule", 7, 0.95, -2.0);
        addSilence(frames, 4);
        addNote(frames, "zirgule", 7, 0.95, 2.0);
        addSilence(frames, 4);
        addNote(frames, "dik zirgule", 7, 0.95, 0.0);
        addSilence(frames, 4);
        addNote(frames, "dugah", 7, 0.95, -5.0);     // flat boundary: still excluded by identity
        addSilence(frames, 4);
        addNote(frames, "kurdi", 7, 0.95, 0.0);      // above Dügâh: excluded

        AudioData audio = new AudioData("sentetik-aralik.wav", null, 22_050f,
                new float[Math.max(1, (int) Math.ceil(frames.size() * STEP * 22_050))],
                AudioChannelMode.MIX, 1);
        TonicSelection tonic = new TonicSelection(TONIC_HZ,
                TonicSelection.Source.GRAPH_REGION, 0.0, 0.14, 0.95, 2.0, 7,
                "Mono", true);
        // Supply the selectors in reverse order: the service must still report
        // a normalised Rast–Dügâh interval.
        PitchIntervalStudyRequest request = new PitchIntervalStudyRequest(audio, frames, THEORY,
                MAKAM, THEORY.note("dugah"), THEORY.note("rast"), tonic, config(),
                "test", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM);
        PitchIntervalStudyResult result = new PitchIntervalStudyService().analyze(request);

        check(result.lowerBoundaryKey().equals("rast"), "alt sınır Rast olmalı");
        check(result.upperBoundaryKey().equals("dugah"), "üst sınır Dügâh olmalı");
        check(result.intervalContacts().size() == 4,
                "açık Rast–Dügâh aralığındaki üç adlı ve bir adlandırılamayan temas korunmalı");
        check(result.measurableIntervalEvents().size() == 4,
                "dört ara temasın tamamı ölçülebilir olmalı");
        check(result.stableIntervalEvents().size() == 4,
                "dört ara temasın tamamı kararlı olmalı");
        check(result.namedIntervalEvents().size() == 3,
                "yalnız merkezine en fazla 1 Hc uzaklıktaki üç olay adlandırılmalı");
        check(result.unnamedIntervalEvents().size() == 1,
                "2 Hc ara ses silinmeden adlandırılamayan olarak korunmalı");
        check(result.noteSummaries().size() == 4,
                "adlandırılamayan ara ses için de ayrı özet üretilmeli");
        check(result.noteSummaries().stream().anyMatch(value ->
                        value.noteName().equals("Adlandırılamayan (en yakın: Rast)")),
                "ara ses özeti en yakın perdeyi açıkça söylemeli");
        check(Math.abs(result.unnamedIntervalEvents().get(0).deviationHc() - 2.0) < 0.02,
                "ara sesin en yakın Rast'a Hc uzaklığı korunmalı");
        check(result.noteSummaries().stream().noneMatch(value -> value.noteName().equals("Rast")),
                "alt sınır Rast, dik icra edilse bile araştırmaya girmemeli");
        check(result.noteSummaries().stream().noneMatch(value -> value.noteName().equals("Dügâh")),
                "üst sınır Dügâh, pest icra edilse bile araştırmaya girmemeli");
        check(result.noteSummaries().stream().noneMatch(value -> value.noteKey().equals("irak")),
                "alt sınırın dışındaki Irak rapora girmemeli");
        check(result.noteSummaries().stream().noneMatch(value -> value.noteKey().equals("kurdi")),
                "üst sınırın dışındaki Kürdî rapora girmemeli");
        List<String> comparedProfiles = result.profileComparison().candidates().stream()
                .map(MakamProfileComparison.Candidate::makamName).toList();
        check(comparedProfiles.size() == FirstFourProfileReferenceCatalog.coreDisplayNames().size()
                        && comparedProfiles.containsAll(
                        FirstFourProfileReferenceCatalog.coreDisplayNames()),
                "perdeler arası analiz varsayılan altı profili kullanmalı");
        check(result.distribution().minimumObservedRelativeCent()
                        >= result.lowerBoundaryCent() - 1.0e-6,
                "gerçek F0 dağılımı alt sınırın altında kalmamalı");
        check(result.distribution().maximumObservedRelativeCent()
                        <= result.upperBoundaryCent() + 1.0e-6,
                "gerçek F0 dağılımı üst sınırı aşmamalı");
    }

    private static void verifyIdenticalBoundariesRejected() {
        List<PitchFrame> frames = new ArrayList<>();
        addNote(frames, "rast", 7, 0.95, 0.0);
        AudioData audio = new AudioData("sentetik.wav", null, 22_050f,
                new float[Math.max(1, (int) Math.ceil(frames.size() * STEP * 22_050))],
                AudioChannelMode.MIX, 1);
        TonicSelection tonic = new TonicSelection(TONIC_HZ,
                TonicSelection.Source.GRAPH_REGION, 0.0, 0.14, 0.95, 2.0, 7,
                "Mono", true);
        boolean rejected = false;
        try {
            new PitchIntervalStudyRequest(audio, frames, THEORY, MAKAM,
                    THEORY.note("rast"), THEORY.note("rast"), tonic, config(),
                    "test", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "aynı iki sınır perde aralığı olarak kabul edilmemeli");
    }

    private static void verifyEveryCoherentRegisterIsRetainedAndNormalized() {
        List<PitchFrame> frames = new ArrayList<>();
        addRelativeHcAtOctave(frames, 0.0, 1, 7, 0.95);  // octave Rast: boundary
        addSilence(frames, 4);
        addRelativeHcAtOctave(frames, 4.0, 1, 7, 0.95);
        addSilence(frames, 4);
        addRelativeHcAtOctave(frames, 5.0, 1, 7, 0.95);
        addSilence(frames, 4);
        addRelativeHcAtOctave(frames, 9.0, 1, 7, 0.95);
        addSilence(frames, 4);
        addRelativeHcAtOctave(frames, 14.0, 1, 7, 0.95); // octave Dik Kürdî: boundary
        addSilence(frames, 4);
        // A denser second-upper-octave passage is a second coherent performance
        // register.  It must be retained but displayed in the same reference
        // geometry instead of appearing with misleading "Tiz" identities.
        addRelativeHcAtOctave(frames, 4.0, 2, 14, 0.95);
        addSilence(frames, 4);
        addRelativeHcAtOctave(frames, 5.0, 2, 14, 0.95);
        addSilence(frames, 4);
        addRelativeHcAtOctave(frames, 9.0, 2, 14, 0.95);

        AudioData audio = new AudioData("oktav-aralik.wav", null, 22_050f,
                new float[Math.max(1, (int) Math.ceil(frames.size() * STEP * 22_050))],
                AudioChannelMode.MIX, 1);
        TonicSelection tonic = new TonicSelection(TONIC_HZ,
                TonicSelection.Source.GRAPH_REGION, 0.0, 0.14, 0.95, 2.0, 7,
                "Mono", true);
        PitchIntervalStudyRequest request = new PitchIntervalStudyRequest(audio, frames, THEORY,
                MAKAM, THEORY.note("rast"), THEORY.note("dik kurdi"), tonic, config(),
                "test", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM);
        PitchIntervalStudyResult result = new PitchIntervalStudyService().analyze(request);

        check(result.intervalContacts().size() == 6,
                "iki kanıtlı icra registerındaki altı olayın tamamı korunmalı");
        check(result.intervalContacts().stream().allMatch(value ->
                        value.observedRelativeCent() > result.lowerBoundaryCent()
                                && value.observedRelativeCent() < result.upperBoundaryCent()),
                "oktav eşdeğerleri rapor aralığının kayıt düzenine hizalanmalı");
        check(result.intervalContacts().stream().noneMatch(value ->
                        value.noteName().startsWith("Tiz ")),
                "üst oktav olayları raporda temel aralığın perde kimliğine katlanmalı");
        check(result.intervalContacts().stream().anyMatch(value ->
                        value.observedHertz() > TONIC_HZ * 4.0),
                "üst icra registerının fiziksel Hz kanıtı denetim için korunmalı");
    }

    private static void verifyIsolatedOctaveNoiseDoesNotEnterCoherentRegisterEvidence() {
        List<PitchFrame> frames = new ArrayList<>();
        // One 20-ms isolated estimate in the tonic register is not a coherent
        // register and must not be folded into the study as if it were a note.
        addRelativeHcAtOctave(frames, 5.0, 0, 1, 0.95);
        addSilence(frames, 4);
        addRelativeHcAtOctave(frames, 4.0, 1, 14, 0.95);
        addSilence(frames, 4);
        addRelativeHcAtOctave(frames, 5.0, 1, 14, 0.95);
        addSilence(frames, 4);
        addRelativeHcAtOctave(frames, 9.0, 1, 14, 0.95);
        addSilence(frames, 4);
        // Three long but low-confidence contacts in another octave are not a
        // coherent register merely because they exceed the duration gate.
        addRelativeHcAtOctave(frames, 4.0, 2, 7, 0.40);
        addSilence(frames, 4);
        addRelativeHcAtOctave(frames, 5.0, 2, 7, 0.40);
        addSilence(frames, 4);
        addRelativeHcAtOctave(frames, 9.0, 2, 7, 0.40);

        AudioData audio = new AudioData("karar-register-onceligi.wav", null, 22_050f,
                new float[Math.max(1, (int) Math.ceil(frames.size() * STEP * 22_050))],
                AudioChannelMode.MIX, 1);
        TonicSelection tonic = new TonicSelection(TONIC_HZ,
                TonicSelection.Source.GRAPH_REGION, 0.0, 0.14, 0.95, 2.0, 7,
                "Mono", true);
        PitchIntervalStudyRequest request = new PitchIntervalStudyRequest(audio, frames, THEORY,
                MAKAM, THEORY.note("rast"), THEORY.note("dik kurdi"), tonic, config(),
                "test", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM);
        PitchIntervalStudyResult result = new PitchIntervalStudyService().analyze(request);

        check(result.intervalContacts().size() == 3,
                "yalnız kanıt eşiğini geçen üst register korunmalı");
        check(result.intervalContacts().stream().allMatch(value ->
                        value.observedHertz() >= TONIC_HZ * 2.0
                                && value.observedHertz() < TONIC_HZ * 4.0),
                "yalıtılmış veya düşük güvenli register araştırmaya karışmamalı");
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

    private static void addRelativeHc(List<PitchFrame> frames, double relativeHc,
                                      int count, double confidence) {
        double absolute = TONIC_CENT + relativeHc * AnalysisConfig.COMMA_CENT;
        for (int i = 0; i < count; i++) {
            frames.add(new PitchFrame(frames.size() * STEP,
                    PitchTracker.centToHertz(absolute), absolute, confidence, 0.15));
        }
    }

    private static void addRelativeHcAtOctave(List<PitchFrame> frames, double relativeHc,
                                              int octave, int count, double confidence) {
        double absolute = TONIC_CENT + octave * 1200.0
                + relativeHc * AnalysisConfig.COMMA_CENT;
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
        TheoryCatalog.Note target = THEORY.note("nim zirgule");
        return new AnalysisConfig(MAKAM.toString(), THEORY.tonicFor(MAKAM).toString(),
                target.toString(), TONIC_HZ, THEORY.relativeCent(MAKAM, target),
                2.0, 2.0, 1.0, 0.10, 0.35, 0.70, 0.28, 0.75, 0.55,
                0.12, 1.10, 0.72, 100.0, 700.0, 1.5, 0.02, 32.0, false);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
