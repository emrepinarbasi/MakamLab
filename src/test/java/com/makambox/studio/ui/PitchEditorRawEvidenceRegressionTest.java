package com.makambox.studio.ui;

import com.makambox.studio.AnalyzerSelfTest;
import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisReport;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.PitchFrame;

import javax.swing.SwingUtilities;
import javax.swing.JComboBox;
import javax.swing.JScrollBar;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** Guards raw-candidate visibility without admitting it to academic events. */
public final class PitchEditorRawEvidenceRegressionTest {
    public static void main(String[] args) throws Exception {
        double cent = PitchTracker.hertzToCent(220.0);
        PitchFrame weak = new PitchFrame(5.00, 0.0, Double.NaN, 0.48, 0.10,
                220.0, cent);
        if (weak.voiced() || !weak.candidateVoiced()) {
            throw new AssertionError("weak F0 must be visual-only evidence");
        }
        List<PitchFrame> frames = new ArrayList<>();
        frames.add(weak);
        for (int index = 1; index <= 5; index++) {
            double value = cent + index * 10.0;
            frames.add(new PitchFrame(5.00 + index * 0.02, PitchTracker.centToHertz(value),
                    value, 0.92, 0.11));
        }

        Method rawBuilder = PitchEditorPanel.class.getDeclaredMethod("buildRawPitchBlobs", List.class);
        rawBuilder.setAccessible(true);
        List<?> raw = (List<?>) rawBuilder.invoke(null, frames);
        if (raw.size() != 1) throw new AssertionError("raw candidate contour was split or lost");
        Method frameCount = raw.get(0).getClass().getDeclaredMethod("frameCount");
        frameCount.setAccessible(true);
        if ((int) frameCount.invoke(raw.get(0)) != frames.size()) {
            throw new AssertionError("raw layer did not retain the weak candidate");
        }

        Method canonicalBuilder = PitchEditorPanel.class.getDeclaredMethod("buildAllPitchBlobs", List.class);
        canonicalBuilder.setAccessible(true);
        List<?> canonical = (List<?>) canonicalBuilder.invoke(null, frames);
        if (canonical.isEmpty() || (int) frameCount.invoke(canonical.get(0)) != frames.size() - 1) {
            throw new AssertionError("academic layer admitted or lost the wrong frames");
        }

        Method glideBuilder = PitchEditorPanel.class.getDeclaredMethod("buildGlobalVisualGlides", List.class);
        glideBuilder.setAccessible(true);
        if (((List<?>) glideBuilder.invoke(null, raw)).isEmpty()) {
            throw new AssertionError("global visual glide was not derived from the raw contour");
        }

        AnalysisReport base = AnalyzerSelfTest.createReport();
        AnalysisReport longReport = new AnalysisReport(
                new AudioData("long.wav", Path.of("long.wav"), 1_000f, new float[90_000]),
                base.config(), frames, List.of(), List.of(), ZonedDateTime.now());
        PitchEditorPanel[] holder = new PitchEditorPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new PitchEditorPanel();
            holder[0].setData(longReport);
        });
        Object canvas = field(PitchEditorPanel.class, "canvas").get(holder[0]);
        double start = (double) field(canvas.getClass(), "viewStart").get(canvas);
        double end = (double) field(canvas.getClass(), "viewEnd").get(canvas);
        if (Math.abs((end - start) - 30.0) > 0.001 || start <= 0.0) {
            throw new AssertionError("long recording did not open in a 30-second evidence window: "
                    + start + ".." + end);
        }
        JComboBox<?> viewMode = (JComboBox<?>) field(PitchEditorPanel.class,
                "evidenceLayerBox").get(holder[0]);
        if (!"Sade (Melodyne tipi)".equals(String.valueOf(viewMode.getSelectedItem()))) {
            throw new AssertionError("clean visual mode is not the default presentation view");
        }
        JScrollBar pitchScroll = (JScrollBar) field(PitchEditorPanel.class,
                "pitchScroll").get(holder[0]);
        SwingUtilities.invokeAndWait(holder[0]::resetView);
        if (pitchScroll.getVisibleAmount() > 23) {
            throw new AssertionError("reset view squeezed the entire 53-Hc ruler into one screen");
        }
        System.out.printf("PITCH_EDITOR_RAW_EVIDENCE_OK raw=%d canonical=%d view=%.2f..%.2f%n",
                frames.size(), frames.size() - 1, start, end);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
