package com.makambox.studio;

import com.makambox.studio.analysis.AllPitchEventAnalyzer;
import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.EventType;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.theory.TheoryCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Regression coverage for raw midpoint evidence and stable canonical pitch events. */
public final class AllPitchEventAnalyzerRegressionTest {
    private static final double STEP = 0.020;
    private static final double TONIC_HZ = 220.0;
    private static final double TONIC_CENT = PitchTracker.hertzToCent(TONIC_HZ);
    private static final TheoryCatalog CATALOG = TheoryCatalog.loadDefault();
    private static final TheoryCatalog.Makam USSAK = CATALOG.makams().stream()
            .filter(value -> value.name().equals("Ussak")).findFirst().orElseThrow();
    private static final AllPitchEventAnalyzer ANALYZER = new AllPitchEventAnalyzer();

    public static void main(String[] args) {
        verifyShortUnvoicedGapRules();
        verifyRawTransientEvidenceRemainsAvailable();
        verifyIntermediateTheoryCentersRemainDistinct();
        verifyHighRegisterTheoryCentersRemainDistinct();
        verifyMidpointTieAndFiniteOuterBoundaries();
        verifyConfidenceThresholdIsHardBoundary();
        verifyTargetToleranceIndependence();
        verifyMidpointChatterDoesNotSplitCanonicalEvent();
        verifyPersistentAdjacentChangeRemainsAnEvent();
        verifyStrongBriefOrnamentRemainsCanonical();
        verifyWeakTrailingContactRemainsCanonical();
        System.out.println("ALL_PITCH_EVENT_ANALYZER_REGRESSION_OK");
    }

    private static void verifyShortUnvoicedGapRules() {
        List<PitchFrame> frames = new ArrayList<>();
        addNote(frames, "dugah", 4, 0.92);
        addSilence(frames, 3); // exactly 60 ms
        addNote(frames, "dugah", 4, 0.92);
        addSilence(frames, 4); // 80 ms: must split
        addNote(frames, "dugah", 3, 0.92);

        List<PitchEvent> events = analyze(frames, config(4.0, 2.5));
        check(events.size() == 2, "60 ms aynı perdeyi birleştirmeli; 80 ms bölmeli");
        check(events.get(0).noteKey().equals("dugah") && events.get(1).noteKey().equals("dugah"),
                "birleşme perde kimliğini korumalı");
        check(close(events.get(0).occurrence().startSeconds(), 0.0)
                        && close(events.get(0).occurrence().endSeconds(), 0.22),
                "birleşen olay gerçek boşluk üzerinden sürmeli");
    }

    private static void verifyRawTransientEvidenceRemainsAvailable() {
        List<PitchFrame> frames = new ArrayList<>();
        addNote(frames, "dugah", 3, 0.95);
        addSilence(frames, 2);
        addNote(frames, "segah", 1, 0.95);
        addNote(frames, "dugah", 3, 0.95);

        List<PitchEvent> raw = analyzeRaw(frames, config(4.0, 2.5));
        check(keys(raw).equals(List.of("dugah", "segah", "dugah")),
                "tek karelik temas ham kanıt katmanında korunmalı");
        check(raw.get(1).occurrence().eventType() == EventType.SHORT_CONTACT,
                "ham tek karelik farklı perde kısa temas olmalı");

        List<PitchEvent> canonical = analyze(frames, config(4.0, 2.5));
        check(keys(canonical).equals(List.of("dugah", "segah", "dugah")),
                "sessizlik sınırındaki izole kısa temas kanonik tabloda korunmalı");
    }

    private static void verifyIntermediateTheoryCentersRemainDistinct() {
        List<PitchFrame> frames = new ArrayList<>();
        addNote(frames, "dugah", 3, 0.95);
        addNote(frames, "kurdi", 3, 0.95);
        addNote(frames, "dik kurdi", 3, 0.95);
        addNote(frames, "segah", 3, 0.95);

        List<PitchEvent> events = analyze(frames, config(0.0, 12.0));
        check(keys(events).equals(List.of("dugah", "kurdi", "dik kurdi", "segah")),
                "Dügâh–Segâh arasındaki Kürdî ve Dik Kürdî merkezleri ayrı kalmalı");
        double covered = events.stream().mapToDouble(value -> value.occurrence().durationSeconds()).sum();
        check(close(covered, frames.size() * STEP), "her güvenilir voiced kare yalnız bir olaya atanmalı");
    }

    private static void verifyHighRegisterTheoryCentersRemainDistinct() {
        List<PitchFrame> frames = new ArrayList<>();
        addNote(frames, "muhayyer", 3, 0.96);
        addNote(frames, "tiz segah", 3, 0.96);
        addNote(frames, "tiz dik buselik", 3, 0.96);
        addNote(frames, "tiz cargah", 3, 0.96);
        addNote(frames, "tiz huseyni", 3, 0.96);
        addNote(frames, "tiz tiz segah", 3, 0.96);

        List<PitchEvent> events = analyze(frames, config(4.0, 4.0));
        check(keys(events).equals(List.of("muhayyer", "tiz segah", "tiz dik buselik",
                        "tiz cargah", "tiz huseyni", "tiz tiz segah")),
                "Muhayyer üstü Kaba/Tiz/Tiz Tiz merkezleri adları ve registerleriyle olay olmalı");
        double covered = events.stream().mapToDouble(value -> value.occurrence().durationSeconds()).sum();
        check(close(covered, frames.size() * STEP),
                "tiz registerdeki her güvenilir kare adlandırılmış bir olaya atanmalı");
        PitchEvent tizSegah = events.get(1);
        check(close(tizSegah.theoreticalRelativeCent(),
                        CATALOG.relativeCent(USSAK, CATALOG.note("tiz segah"))),
                "Tiz Segâh olayı temel Segâh'a oktav katlanmamalı");
    }

    private static void verifyMidpointTieAndFiniteOuterBoundaries() {
        List<TheoryPoint> points = theoryPoints();
        TheoryPoint dugah = point("dugah", points);
        TheoryPoint kurdi = point("kurdi", points);
        check(points.indexOf(kurdi) == points.indexOf(dugah) + 1,
                "test için Dügâh ve Kürdî komşu merkez olmalı");
        double midpoint = (dugah.relativeCent() + kurdi.relativeCent()) / 2.0;
        List<PitchFrame> tie = new ArrayList<>();
        addRelative(tie, midpoint, 1, 0.95);
        check(analyzeRaw(tie, config(4.0, 4.0)).get(0).noteKey().equals("dugah"),
                "tam midpoint bağı deterministik olarak alt merkeze gitmeli");

        TheoryPoint first = points.get(0);
        TheoryPoint second = points.get(1);
        TheoryPoint penultimate = points.get(points.size() - 2);
        TheoryPoint last = points.get(points.size() - 1);
        double lower = first.relativeCent() - (second.relativeCent() - first.relativeCent()) / 2.0;
        double upper = last.relativeCent() + (last.relativeCent() - penultimate.relativeCent()) / 2.0;
        List<PitchFrame> outer = new ArrayList<>();
        addRelative(outer, lower - 0.01, 1, 0.95);
        addRelative(outer, first.relativeCent(), 1, 0.95);
        addRelative(outer, last.relativeCent(), 1, 0.95);
        addRelative(outer, upper + 0.01, 1, 0.95);
        List<PitchEvent> events = analyzeRaw(outer, config(4.0, 4.0));
        check(keys(events).equals(List.of(first.key(), last.key())),
                "dış yarım aralığın ötesindeki F0 sınır notasına zorlanmamalı");
    }

    private static void verifyConfidenceThresholdIsHardBoundary() {
        List<PitchFrame> frames = new ArrayList<>();
        addNote(frames, "dugah", 3, 0.95);
        addNote(frames, "dugah", 1, 0.349);
        addNote(frames, "dugah", 3, 0.95);
        List<PitchEvent> split = analyze(frames, config(4.0, 4.0));
        check(split.size() == 2, "0.35 altındaki voiced kare gerçek sessizlik gibi köprülenmemeli");

        List<PitchFrame> threshold = new ArrayList<>();
        addNote(threshold, "dugah", 3, AllPitchEventAnalyzer.MINIMUM_FRAME_CONFIDENCE);
        check(analyze(threshold, config(4.0, 4.0)).size() == 1,
                "0.35 güven eşiği dahil olmalı");
    }

    private static void verifyTargetToleranceIndependence() {
        List<PitchFrame> frames = new ArrayList<>();
        addNote(frames, "dugah", 3, 0.90);
        addNote(frames, "kurdi", 2, 0.90);
        addNote(frames, "segah", 4, 0.90);
        List<PitchEvent> narrow = analyze(frames, config(0.0, 0.0));
        List<PitchEvent> wide = analyze(frames, config(12.0, 12.0));
        check(keys(narrow).equals(keys(wide)), "hedef toleransı tüm-perde kimliklerini değiştirmemeli");
        for (int i = 0; i < narrow.size(); i++) {
            check(close(narrow.get(i).occurrence().startSeconds(), wide.get(i).occurrence().startSeconds())
                            && close(narrow.get(i).occurrence().endSeconds(), wide.get(i).occurrence().endSeconds()),
                    "hedef toleransı tüm-perde olay sınırlarını değiştirmemeli");
        }
    }

    private static void verifyMidpointChatterDoesNotSplitCanonicalEvent() {
        List<TheoryPoint> points = theoryPoints();
        TheoryPoint lower = point("dik zirgule", points);
        TheoryPoint upper = point("dugah", points);
        check(points.indexOf(upper) == points.indexOf(lower) + 1,
                "Dik Zirgüle ve Dügâh test için komşu olmalı");
        double midpoint = (lower.relativeCent() + upper.relativeCent()) / 2.0;

        List<PitchFrame> frames = new ArrayList<>();
        addNote(frames, "dugah", 5, 0.96);
        for (int index = 0; index < 8; index++) {
            addRelative(frames, midpoint + (index % 2 == 0 ? -0.75 : 0.75), 1, 0.96);
        }
        addNote(frames, "dugah", 5, 0.96);

        List<PitchEvent> raw = analyzeRaw(frames, config(4.0, 4.0));
        check(raw.size() >= 9, "1,5 C sınır titreşimi ham katmanda görünür kalmalı");
        List<PitchEvent> canonical = analyze(frames, config(4.0, 4.0));
        check(keys(canonical).equals(List.of("dugah")),
                "1,5 C sınır titreşimi Dügâh/Dik Zirgüle A-B-A olayları üretmemeli");
        check(close(canonical.get(0).occurrence().durationSeconds(), frames.size() * STEP),
                "histerezis kararlı Dügâh olayının sürekliliğini korumalı");
    }

    private static void verifyPersistentAdjacentChangeRemainsAnEvent() {
        List<PitchFrame> frames = new ArrayList<>();
        addNote(frames, "dugah", 5, 0.96);
        addNote(frames, "dik zirgule", 3, 0.96);
        addNote(frames, "dugah", 5, 0.96);

        List<PitchEvent> events = analyze(frames, config(4.0, 4.0));
        check(keys(events).equals(List.of("dugah", "dik zirgule", "dugah")),
                "60 ms süren belirgin komşu perde değişimi kanonik olay olarak korunmalı");
        check(close(events.get(1).occurrence().durationSeconds(), 0.060),
                "kısa ama kararlı geçişin gerçek süresi korunmalı");
    }

    private static void verifyStrongBriefOrnamentRemainsCanonical() {
        List<PitchFrame> frames = new ArrayList<>();
        addNote(frames, "dugah", 5, 0.96);
        addNote(frames, "segah", 1, 0.96);
        addNote(frames, "dugah", 5, 0.96);

        check(keys(analyzeRaw(frames, config(4.0, 4.0)))
                        .equals(List.of("dugah", "segah", "dugah")),
                "belirgin tek karelik temas ham geçici kanıtta korunmalı");
        List<PitchEvent> canonical = analyze(frames, config(4.0, 4.0));
        check(keys(canonical).equals(List.of("dugah", "segah", "dugah")),
                "kuramsal merkeze güçlü biçimde oturan kısa süsleme korunmalı");
        check(close(canonical.get(1).occurrence().durationSeconds(), STEP),
                "korunan kısa süslemenin gerçek süresi değişmemeli");
    }

    private static void verifyWeakTrailingContactRemainsCanonical() {
        List<TheoryPoint> points = theoryPoints();
        TheoryPoint lower = point("dik zirgule", points);
        TheoryPoint upper = point("dugah", points);
        double midpoint = (lower.relativeCent() + upper.relativeCent()) / 2.0;
        List<PitchFrame> frames = new ArrayList<>();
        addNote(frames, "dugah", 5, 0.96);
        addRelative(frames, midpoint - 0.75, 1, 0.96);

        check(keys(analyze(frames, config(4.0, 4.0)))
                        .equals(List.of("dugah", "dik zirgule")),
                "sesli blok sonundaki 20 ms temas histerezis tarafından silinmemeli");
    }

    private static List<PitchEvent> analyze(List<PitchFrame> frames, AnalysisConfig config) {
        return ANALYZER.analyze(frames, config, CATALOG, USSAK);
    }

    private static List<PitchEvent> analyzeRaw(List<PitchFrame> frames, AnalysisConfig config) {
        return ANALYZER.analyzeRaw(frames, config, CATALOG, USSAK);
    }

    private static List<String> keys(List<PitchEvent> events) {
        return events.stream().map(PitchEvent::noteKey).toList();
    }

    private static List<TheoryPoint> theoryPoints() {
        return CATALOG.notesForPitchAnalysis(USSAK).stream()
                .map(note -> new TheoryPoint(note.key(), CATALOG.relativeCent(USSAK, note)))
                .sorted(Comparator.comparingDouble(TheoryPoint::relativeCent)).toList();
    }

    private static TheoryPoint point(String key, List<TheoryPoint> points) {
        return points.stream().filter(value -> value.key().equals(key)).findFirst().orElseThrow();
    }

    private static void addNote(List<PitchFrame> frames, String key, int count, double confidence) {
        addRelative(frames, CATALOG.relativeCent(USSAK, CATALOG.note(key)), count, confidence);
    }

    private static void addRelative(List<PitchFrame> frames, double relativeCent,
                                    int count, double confidence) {
        double absoluteCent = TONIC_CENT + relativeCent;
        for (int i = 0; i < count; i++) {
            frames.add(new PitchFrame(frames.size() * STEP,
                    PitchTracker.centToHertz(absoluteCent), absoluteCent, confidence, 0.12));
        }
    }

    private static void addSilence(List<PitchFrame> frames, int count) {
        for (int i = 0; i < count; i++) {
            frames.add(new PitchFrame(frames.size() * STEP, 0.0, Double.NaN, 0.0, 0.0));
        }
    }

    private static AnalysisConfig config(double lowerTolerance, double upperTolerance) {
        return new AnalysisConfig("Uşşak", "Dügâh", "Segâh", TONIC_HZ,
                CATALOG.relativeCent(USSAK, CATALOG.note("segah")),
                lowerTolerance, upperTolerance, 1.0,
                0.10, 0.35, 0.70, 0.28, 0.75, 0.55,
                0.12, 1.10, 0.72, 100.0, 700.0, 1.5, 0.02, 32.0, false);
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0e-8;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record TheoryPoint(String key, double relativeCent) { }
}
