package com.makambox.studio.ui;

import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.AnalysisReport;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.theory.TheoryCatalog;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** Guards display-only register folding and honest out-of-ruler rendering. */
public final class PitchEditorRegisterNormalizationRegressionTest {
    private static final double C0_HZ = 16.351597831287414;

    public static void main(String[] args) throws Exception {
        verifyLowStableRegisterMovesIntoYegahNevaBandWithoutChangingData();
        verifyAnchorBelowYegahButInsideOldRulerAlsoMovesIntoBand();
        verifyHighRegisterAddsTizTheoryLanes();
        verifyOutOfRangePitchIsNeverClampedToAnEdgeLane();
        System.out.println("PITCH_EDITOR_REGISTER_OK low_fold=true tiz_lanes=true no_edge_clamp=true");
    }

    private static void verifyAnchorBelowYegahButInsideOldRulerAlsoMovesIntoBand() throws Exception {
        double tonicHz = 220.0;
        double tonicCent = absoluteCent(tonicHz);
        double measuredCent = tonicCent - 800.0;
        PitchEditorPanel editor = editor(report(tonicHz, stableFrames(measuredCent, 40)));
        check(editor.diagnosticDisplayOctaveShiftCent() == 1_200.0,
                "a stable anchor below Yegah must move even when it still fits the old outer ruler");
        double displayed = editor.diagnosticStableRegisterAnchorCent()
                + editor.diagnosticDisplayOctaveShiftCent();
        TheoryCatalog theory = TheoryCatalog.loadDefault();
        TheoryCatalog.Makam ussak = theory.makams().stream()
                .filter(value -> value.name().equals("Ussak")).findFirst().orElseThrow();
        double yegah = tonicCent + theory.relativeCent(ussak, theory.note("yegah"));
        double neva = tonicCent + theory.relativeCent(ussak, theory.note("neva"));
        check(displayed >= Math.min(yegah, neva) - 0.5
                        && displayed <= Math.max(yegah, neva) + 0.5,
                "below-Yegah anchor did not land in the requested Yegah-Neva display band");
    }

    private static void verifyLowStableRegisterMovesIntoYegahNevaBandWithoutChangingData() throws Exception {
        double tonicHz = 220.0;
        double tonicCent = absoluteCent(tonicHz);
        double measuredCent = tonicCent - 1_980.0;
        AnalysisReport report = report(tonicHz, stableFrames(measuredCent, 48));
        double originalCent = report.frames().get(0).absoluteCent();
        double originalHz = report.frames().get(0).hertz();
        PitchEditorPanel editor = editor(report);

        check(editor.diagnosticDisplayOctaveShiftCent() == 2_400.0,
                "low register should use the smallest whole-octave display shift");
        TheoryCatalog theory = TheoryCatalog.loadDefault();
        TheoryCatalog.Makam ussak = theory.makams().stream()
                .filter(value -> value.name().equals("Ussak")).findFirst().orElseThrow();
        double yegah = tonicCent + theory.relativeCent(ussak, theory.note("yegah"));
        double neva = tonicCent + theory.relativeCent(ussak, theory.note("neva"));
        double shiftedAnchor = editor.diagnosticStableRegisterAnchorCent()
                + editor.diagnosticDisplayOctaveShiftCent();
        check(shiftedAnchor >= Math.min(yegah, neva) - 0.5
                        && shiftedAnchor <= Math.max(yegah, neva) + 0.5,
                "stable/cadential anchor did not land in the Yegah-Neva band");
        check(report.frames().get(0).absoluteCent() == originalCent
                        && report.frames().get(0).hertz() == originalHz,
                "display folding changed measured Hz/cent data");
        check(editor.diagnosticNormalizationStatus().contains("Hz/C/Hc değişmedi"),
                "display normalization is not disclosed to the user");
        check(Double.isFinite(editor.diagnosticLanePositionForMeasuredCent(measuredCent)),
                "normalized low-register trace is not visible on the ruler");
    }

    private static void verifyHighRegisterAddsTizTheoryLanes() throws Exception {
        double tonicHz = 220.0;
        double highCent = absoluteCent(tonicHz) + 2_050.0;
        AnalysisReport report = report(tonicHz, stableFrames(highCent, 36));
        PitchEditorPanel editor = editor(report);
        check(editor.diagnosticDisplayOctaveShiftCent() == 0.0,
                "a legitimate high register must not be octave-folded down");
        check(editor.diagnosticPitchLaneMaximumCent() >= highCent,
                "high-register trace is not covered by dynamically expanded lanes");
        check(editor.diagnosticPitchLaneNames().stream().anyMatch(name -> name.startsWith("Tiz ")),
                "expanded ruler contains no named Tiz theory lanes");
        check(Double.isFinite(editor.diagnosticLanePositionForMeasuredCent(highCent)),
                "legitimate high-register pitch is not addressable on the ruler");
    }

    private static void verifyOutOfRangePitchIsNeverClampedToAnEdgeLane() throws Exception {
        double tonicHz = 220.0;
        double tonicCent = absoluteCent(tonicHz);
        List<PitchFrame> frames = new ArrayList<>(stableFrames(tonicCent, 40));
        double extreme = tonicCent - 4_000.0;
        frames.add(new PitchFrame(1.20, PitchTracker.centToHertz(extreme), extreme, 0.92, 0.2));
        PitchEditorPanel editor = editor(report(tonicHz, frames));
        check(editor.diagnosticDisplayOctaveShiftCent() == 0.0,
                "one isolated low outlier moved the whole recording");
        check(Double.isNaN(editor.diagnosticLanePositionForMeasuredCent(extreme)),
                "out-of-ruler F0 was clamped to the first/last note row");
        check(editor.diagnosticOutOfRulerFrameCount() == 1,
                "out-of-ruler frame should be counted and disclosed safely");
    }

    private static PitchEditorPanel editor(AnalysisReport report) throws Exception {
        PitchEditorPanel[] holder = new PitchEditorPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new PitchEditorPanel();
            holder[0].setData(report);
        });
        return holder[0];
    }

    private static AnalysisReport report(double tonicHz, List<PitchFrame> frames) {
        TheoryCatalog theory = TheoryCatalog.loadDefault();
        TheoryCatalog.Makam ussak = theory.makams().stream()
                .filter(value -> value.name().equals("Ussak")).findFirst().orElseThrow();
        TheoryCatalog.Note target = theory.note("segah");
        AnalysisConfig config = new AnalysisConfig(ussak.toString(), theory.tonicFor(ussak).toString(),
                target.toString(), tonicHz, theory.relativeCent(ussak, target),
                1.25, 1.25, 1.0, 0.10, 0.35, 0.70, 0.28, 0.75, 0.55,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_SECONDS,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_SPAN_COMMA,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_MONOTONICITY,
                AnalysisConfig.DEFAULT_MAXIMUM_GLISSANDO_FRAME_STEP_CENT,
                700.0, 1.5, 0.02, 32.0, true);
        return new AnalysisReport(new AudioData("register-test.wav", Path.of("register-test.wav"),
                100.0f, new float[400]), config, List.copyOf(frames), List.of(), List.of(), ZonedDateTime.now());
    }

    private static List<PitchFrame> stableFrames(double cent, int count) {
        List<PitchFrame> frames = new ArrayList<>();
        double hertz = PitchTracker.centToHertz(cent);
        for (int index = 0; index < count; index++) {
            frames.add(new PitchFrame(index * 0.02, hertz, cent, 0.94, 0.2));
        }
        return frames;
    }

    private static double absoluteCent(double hertz) {
        return 1_200.0 * Math.log(hertz / C0_HZ) / Math.log(2.0);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
