package com.makambox.studio;

import com.makambox.studio.analysis.DirectionalIntonationAnalyzer;
import com.makambox.studio.analysis.TonicEstimator;
import com.makambox.studio.audio.DemoMelodyFactory;
import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.legacy.LegacyPitchAnalysis;
import com.makambox.studio.legacy.LegacyPitchAnalyzer;
import com.makambox.studio.legacy.TuningSystemCatalog;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.AnalysisReport;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.EventType;
import com.makambox.studio.model.GlissandoType;
import com.makambox.studio.model.MelodicDirection;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.theory.TheoryCatalog;

import java.util.List;

public final class AnalyzerSelfTest {
    public static void main(String[] args) {
        AnalysisReport report = createReport();
        check(report.frames().stream().filter(PitchFrame::voiced).count() > 100, "yeterli sesli perde karesi");
        check(report.occurrences().size() >= 3, "hedef perde olayları");
        check(report.occurrences().stream().anyMatch(o -> o.direction() == MelodicDirection.ASCENDING), "çıkıcı olay");
        check(report.occurrences().stream().anyMatch(o -> o.direction() == MelodicDirection.DESCENDING), "inici olay");
        check(report.occurrences().stream().anyMatch(o -> o.eventType() == EventType.CADENTIAL_HOLD || o.eventType() == EventType.SUSTAINED), "uzatma veya kalış");
        check(report.glissandi().size() >= 2, "glissando olayları");
        check(report.glissandi().stream().anyMatch(e -> e.type() == GlissandoType.INTO_TARGET || e.type() == GlissandoType.THROUGH_TARGET), "perdeyle ilişkili glissando");
        LegacyPitchAnalyzer legacyAnalyzer = new LegacyPitchAnalyzer();
        LegacyPitchAnalysis classic = legacyAnalyzer.analyze(report.audio(), ignored -> { });
        check(classic.pitchPoints().size() > 50, "klasik perde izi");
        check(classic.histogram().length == 636, "klasik 1/3 koma histogramı");
        check(!classic.makamName().isBlank(), "klasik makam sınıflandırması");
        check(classic.tonicHertz() > 0.0, "klasik karar frekansı");
        LegacyPitchAnalysis manual = legacyAnalyzer.reclassify(classic, "Huseyni");
        check(manual.makamName().contains("Hüseyn"), "elle klasik makam atama");
        LegacyPitchAnalysis shifted = legacyAnalyzer.shiftTonic(manual, 10.0);
        check(Math.abs(shifted.tonicCent() - manual.tonicCent() - 10.0) < 0.001,
                "histogramdan karar düzeltmesi");
        TuningSystemCatalog.SystemData arel = new TuningSystemCatalog().loadBuiltIns().get("Arel_Ezgi_Uzdilek");
        check(arel != null && arel.intervalsFor("Huseyni") != null, "klasik akort sistemi karşılaştırma verisi");
        TheoryCatalog theoryCatalog = TheoryCatalog.loadDefault();
        TheoryCatalog.Note huseyniAsiran = theoryCatalog.note("huseyni asiran");
        check(Math.abs(huseyniAsiran.ratio() - 1.26542555) < 0.0000001,
                "Hüseynî Aşiran doğru oktav oranı");
        check(theoryCatalog.note("acemasiran").ratio() > huseyniAsiran.ratio()
                        && huseyniAsiran.ratio() > theoryCatalog.note("kaba dik hisar").ratio(),
                "Hüseynî Aşiran perde sırası");
        System.out.printf("SELF_TEST_OK frames=%d occurrences=%d glissandi=%d tonic=%.2f classic=%s/%.2f%n",
                report.frames().size(), report.occurrences().size(), report.glissandi().size(), report.config().tonicHz(),
                classic.makamName(), classic.tonicHertz());
    }

    public static AnalysisReport createReport() {
        AudioData audio = DemoMelodyFactory.create();
        List<PitchFrame> frames = new PitchTracker().track(audio, ignored -> { });
        double tonic = new TonicEstimator().estimate(frames).hertz();
        TheoryCatalog catalog = TheoryCatalog.loadDefault();
        TheoryCatalog.Makam makam = catalog.makams().stream().filter(m -> m.name().equalsIgnoreCase("Huseyni")).findFirst().orElseThrow();
        TheoryCatalog.Note target = catalog.notes().stream().filter(n -> n.key().equals("neva")).findFirst().orElseThrow();
        AnalysisConfig config = new AnalysisConfig(makam.toString(), catalog.tonicFor(makam).toString(), target.toString(),
                tonic, catalog.relativeCent(makam, target), 1.25, 1.25, 1.0, 0.10, 0.35, 0.70,
                0.28, 0.75, 0.55, AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_SECONDS,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_SPAN_COMMA,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_MONOTONICITY,
                AnalysisConfig.DEFAULT_MAXIMUM_GLISSANDO_FRAME_STEP_CENT,
                700.0, 1.5, 0.02, 32.0, true);
        return new DirectionalIntonationAnalyzer().analyze(audio, frames, config);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError("Eksik doğrulama: " + label);
    }
}
