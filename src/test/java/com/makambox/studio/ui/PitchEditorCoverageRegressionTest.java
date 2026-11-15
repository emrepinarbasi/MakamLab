package com.makambox.studio.ui;

import com.makambox.studio.AnalyzerSelfTest;
import com.makambox.studio.model.EventType;
import com.makambox.studio.model.MelodicDirection;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.model.PitchOccurrence;

import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Ensures one-frame pitch detections remain available to the all-detections view. */
public final class PitchEditorCoverageRegressionTest {
    public static void main(String[] args) throws Exception {
        List<PitchFrame> frames = List.of(
                frame(0.00, 4200.0), silence(0.02),
                frame(0.04, 4300.0), frame(0.06, 4304.0), silence(0.08));
        Method build = PitchEditorPanel.class.getDeclaredMethod("buildAllPitchBlobs", List.class);
        build.setAccessible(true);
        List<?> blobs = (List<?>) build.invoke(null, frames);
        if (blobs.size() != 2) throw new AssertionError("expected transient + stable blob, got " + blobs.size());
        Method frameCount = blobs.get(0).getClass().getDeclaredMethod("frameCount");
        frameCount.setAccessible(true);
        int first = (int) frameCount.invoke(blobs.get(0));
        int second = (int) frameCount.invoke(blobs.get(1));
        if (first != 1 || second != 2) {
            throw new AssertionError("unexpected coverage: " + first + ", " + second);
        }
        verifyAdjacentEventFrameMembership();

        PitchEditorPanel[] holder = new PitchEditorPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            PitchEditorPanel editor = new PitchEditorPanel();
            editor.setData(AnalyzerSelfTest.createReport());
            holder[0] = editor;
        });
        Field scrollField = PitchEditorPanel.class.getDeclaredField("pitchScroll");
        scrollField.setAccessible(true);
        JScrollBar scroll = (JScrollBar) scrollField.get(holder[0]);
        if (scroll.getVisibleAmount() > 23) {
            throw new AssertionError("initial pitch ruler is too dense: " + scroll.getVisibleAmount());
        }

        Field lanesField = PitchEditorPanel.class.getDeclaredField("pitchLanes");
        lanesField.setAccessible(true);
        List<?> lanes = (List<?>) lanesField.get(holder[0]);
        int targetIndex = -1;
        for (int i = 0; i < lanes.size(); i++) {
            Method target = lanes.get(i).getClass().getDeclaredMethod("target");
            target.setAccessible(true);
            if ((boolean) target.invoke(lanes.get(i))) { targetIndex = i; break; }
        }
        if (targetIndex < scroll.getValue()
                || targetIndex >= scroll.getValue() + scroll.getVisibleAmount()) {
            throw new AssertionError("target pitch is outside the initial ruler view");
        }

        SwingUtilities.invokeAndWait(() -> {
            // Same compact laptop window class used by the UI smoke tests.
            holder[0].setSize(1180, 760);
            holder[0].doLayout();
            layoutTree(holder[0]);
        });
        SwingUtilities.invokeAndWait(() -> { });
        if (holder[0].diagnosticPitchLaneHeight() < 17.5) {
            throw new AssertionError("short-window pitch ruler labels still collide: laneHeight="
                    + holder[0].diagnosticPitchLaneHeight());
        }
        if (targetIndex < scroll.getValue()
                || targetIndex >= scroll.getValue() + holder[0].diagnosticVisiblePitchLaneCount()) {
            throw new AssertionError("target pitch moved outside the resized ruler view");
        }
        System.out.println("PITCH_EDITOR_COVERAGE_OK transient=1 stableFrames=2 initialLanes="
                + scroll.getVisibleAmount() + " laneHeight="
                + holder[0].diagnosticPitchLaneHeight() + " targetVisible=true");
    }

    private static void layoutTree(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container nested) layoutTree(nested);
        }
    }

    /**
     * A floating half-open end such as 0.020000000000000004 must not steal the
     * 0.02 frame from the following event.  This was the source of the editor's
     * stable/transient disagreement with the canonical event table.
     */
    private static void verifyAdjacentEventFrameMembership() throws Exception {
        double step = 0.02;
        List<PitchFrame> frames = List.of(
                frame(0.00, 4200.0), frame(0.02, 4300.0), frame(0.04, 4304.0),
                frame(0.06, 4400.0));
        PitchOccurrence oneFrame = occurrence(1, 0.00, 0.00 + step + 4.0e-18, 4200.0);
        PitchOccurrence twoFrames = occurrence(2, 0.02, 0.04 + step + 4.0e-18, 4302.0);
        PitchOccurrence finalFrame = occurrence(3, 0.06, 0.06 + step, 4400.0);
        List<PitchEvent> events = List.of(
                new PitchEvent("a", "A", 0.0, oneFrame),
                new PitchEvent("b", "B", 100.0, twoFrames),
                new PitchEvent("c", "C", 200.0, finalFrame));

        Method build = PitchEditorPanel.class.getDeclaredMethod("buildAllPitchBlobs", List.class, List.class);
        build.setAccessible(true);
        List<?> blobs = (List<?>) build.invoke(null, events, frames);
        if (blobs.size() != events.size()) {
            throw new AssertionError("canonical events and editor blobs are not 1:1");
        }
        Method frameCount = blobs.get(0).getClass().getDeclaredMethod("frameCount");
        Method start = blobs.get(0).getClass().getDeclaredMethod("startSeconds");
        Method end = blobs.get(0).getClass().getDeclaredMethod("endSeconds");
        frameCount.setAccessible(true);
        start.setAccessible(true);
        end.setAccessible(true);
        int[] expected = {1, 2, 1};
        for (int index = 0; index < blobs.size(); index++) {
            int actual = (int) frameCount.invoke(blobs.get(index));
            if (actual != expected[index]) {
                throw new AssertionError("event " + index + " has " + actual
                        + " frames; expected " + expected[index]);
            }
            PitchOccurrence source = events.get(index).occurrence();
            if (Double.compare((double) start.invoke(blobs.get(index)), source.startSeconds()) != 0
                    || Double.compare((double) end.invoke(blobs.get(index)), source.endSeconds()) != 0) {
                throw new AssertionError("blob timing changed for event " + index);
            }
        }
    }

    private static PitchOccurrence occurrence(int index, double start, double end, double cent) {
        return new PitchOccurrence(index, start, end, cent, cent, 0.0,
                0.0, 0.0, 0.0, 0.0, false, 0.95,
                MelodicDirection.LEVEL, MelodicDirection.LEVEL,
                MelodicDirection.LEVEL, EventType.SHORT_CONTACT);
    }

    private static PitchFrame frame(double time, double cent) {
        return new PitchFrame(time, 220.0, cent, 0.92, 0.12);
    }

    private static PitchFrame silence(double time) {
        return new PitchFrame(time, 0.0, Double.NaN, 0.0, 0.0);
    }
}
