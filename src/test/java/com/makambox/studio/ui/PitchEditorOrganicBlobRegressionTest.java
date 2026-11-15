package com.makambox.studio.ui;

import com.makambox.studio.AnalyzerSelfTest;
import com.makambox.studio.model.AnalysisReport;
import com.makambox.studio.model.PitchFrame;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.Shape;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Guards the blue, contour-following note-body presentation. */
public final class PitchEditorOrganicBlobRegressionTest {
    public static void main(String[] args) throws Exception {
        AnalysisReport report = AnalyzerSelfTest.createReport();
        double center = report.config().expectedAbsoluteCent();
        List<PitchFrame> contour = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            double time = 0.40 + index * 0.02;
            double cent = center + 17.0 * Math.sin(index * Math.PI / 7.0);
            double rms = 0.05 + 0.08 * Math.sin(index * Math.PI / 29.0);
            contour.add(new PitchFrame(time, 220.0 * Math.pow(2.0, (cent - center) / 1200.0),
                    cent, 0.93, rms));
        }

        Method build = PitchEditorPanel.class.getDeclaredMethod("buildAllPitchBlobs", List.class);
        build.setAccessible(true);
        List<?> blobs = (List<?>) build.invoke(null, contour);
        if (blobs.size() != 1) throw new AssertionError("contour should remain one organic note body");
        Object blob = blobs.get(0);
        Method frames = blob.getClass().getDeclaredMethod("frames");
        frames.setAccessible(true);
        if (((List<?>) frames.invoke(blob)).size() != contour.size()) {
            throw new AssertionError("note body lost its source F0 frames");
        }

        PitchEditorPanel[] holder = new PitchEditorPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            PitchEditorPanel panel = new PitchEditorPanel();
            panel.setData(report);
            panel.setSize(1_200, 720);
            panel.doLayout();
            holder[0] = panel;
        });
        Object canvas = field(PitchEditorPanel.class, "canvas").get(holder[0]);
        JComponent component = (JComponent) canvas;
        SwingUtilities.invokeAndWait(() -> {
            component.setSize(1_050, 540);
            component.doLayout();
        });

        Method timeToX = canvas.getClass().getDeclaredMethod("timeToX", double.class);
        Method pitchToY = canvas.getClass().getDeclaredMethod("pitchToY", double.class);
        Method samples = canvas.getClass().getDeclaredMethod("blobSamples", blob.getClass(),
                int.class, int.class, int.class, boolean.class, boolean.class);
        Method organicBody = canvas.getClass().getDeclaredMethod("organicBody", List.class);
        timeToX.setAccessible(true);
        pitchToY.setAccessible(true);
        samples.setAccessible(true);
        organicBody.setAccessible(true);
        int x1 = (int) timeToX.invoke(canvas, 0.40);
        int x2 = (int) timeToX.invoke(canvas, 0.98);
        int laneY = (int) pitchToY.invoke(canvas, center);
        List<?> points = (List<?>) samples.invoke(canvas, blob, x1, x2, laneY, false, true);
        if (points.size() < 10) throw new AssertionError("F0 contour was flattened into a rectangle");

        Method actualY = points.get(0).getClass().getDeclaredMethod("actualY");
        actualY.setAccessible(true);
        long distinctY = points.stream().map(point -> {
            try { return (int) actualY.invoke(point); }
            catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
        }).distinct().count();
        if (distinctY < 4) throw new AssertionError("organic body no longer follows pitch movement");

        Shape body = (Shape) organicBody.invoke(canvas, points);
        Rectangle bounds = body.getBounds();
        if (bounds.width < 25 || bounds.height < 12) {
            throw new AssertionError("organic body collapsed: " + bounds);
        }
        System.out.println("PITCH_EDITOR_ORGANIC_BLOB_OK samples=" + points.size()
                + " distinctF0Y=" + distinctY + " bounds=" + bounds.width + "x" + bounds.height);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
