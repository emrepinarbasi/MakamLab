package com.makambox.studio.ui;

import com.makambox.studio.AnalyzerSelfTest;
import com.makambox.studio.model.AnalysisReport;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchOccurrence;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** The old blow inspector opens from a note block and closes on empty graph space. */
public final class PitchEditorInspectorRegressionTest {
    public static void main(String[] args) throws Exception {
        AnalysisReport report = AnalyzerSelfTest.createReport();
        PitchOccurrence blow = report.occurrences().get(0);
        AnalysisReport backgroundOnly = new AnalysisReport(report.audio(), report.config(), report.frames(),
                List.of(), report.glissandi(), report.generatedAt());
        PitchEditorPanel panel = new PitchEditorPanel();
        SwingUtilities.invokeAndWait(() -> {
            // The event key deliberately differs from the selected target: this
            // is a blue, non-target blow rather than the coloured target overlay.
            panel.setData(backgroundOnly, List.of(new PitchEvent("rast", "Rast", 0.0, blow)));
            panel.setSize(1_180, 720);
            layoutTree(panel);
        });

        if (panel.diagnosticInspectorOpen()) {
            throw new AssertionError("blow inspector must start closed");
        }

        JComponent canvas = (JComponent) field(PitchEditorPanel.class, "canvas").get(panel);
        SwingUtilities.invokeAndWait(() -> {
            canvas.setSize(1_000, 520);
            BufferedImage image = new BufferedImage(1_000, 520, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            canvas.paint(graphics);
            graphics.dispose();
        });

        List<?> hits = (List<?>) field(canvas.getClass(), "hits").get(canvas);
        if (hits.isEmpty()) throw new AssertionError("rendered blow has no clickable hit region");
        Object first = hits.get(0);
        Method boundsAccessor = first.getClass().getDeclaredMethod("bounds");
        boundsAccessor.setAccessible(true);
        Rectangle bounds = (Rectangle) boundsAccessor.invoke(first);

        SwingUtilities.invokeAndWait(() -> click(canvas, bounds.x + bounds.width / 2,
                bounds.y + bounds.height / 2));
        if (!panel.diagnosticInspectorOpen()
                || panel.diagnosticInspectorDividerLocation() < 250
                || panel.diagnosticInspectorDividerSize() <= 0) {
            throw new AssertionError("clicking a blow did not open the inspector");
        }

        SwingUtilities.invokeAndWait(() -> click(canvas, canvas.getWidth() - 2, canvas.getHeight() - 2));
        if (panel.diagnosticInspectorOpen()
                || panel.diagnosticInspectorDividerLocation() != 0
                || panel.diagnosticInspectorDividerSize() != 0) {
            throw new AssertionError("clicking empty graph space did not fully close the inspector");
        }

        SwingUtilities.invokeAndWait(() -> {
            click(canvas, bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
            click(canvas, 2, 2);
        });
        if (panel.diagnosticInspectorOpen()) {
            throw new AssertionError("empty pitch-label margin did not close the inspector");
        }

        System.out.println("PITCH_EDITOR_BLOW_INSPECTOR_OK click_open=true blank_close=true label_blank_close=true");
    }

    private static void click(Component component, int x, int y) {
        component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layoutTree(nested);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
