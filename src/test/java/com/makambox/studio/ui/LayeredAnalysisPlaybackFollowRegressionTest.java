package com.makambox.studio.ui;

import com.makambox.studio.model.AudioData;

import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import java.nio.file.Path;

/** Regression coverage for click-to-seek, centred playback follow and the time scrollbar. */
public final class LayeredAnalysisPlaybackFollowRegressionTest {
    private LayeredAnalysisPlaybackFollowRegressionTest() { }

    public static void main(String[] arguments) throws Exception {
        SwingUtilities.invokeAndWait(LayeredAnalysisPlaybackFollowRegressionTest::runChecks);
        System.out.println("LAYERED_PLAYBACK_FOLLOW_OK seek=true follow=true scrollbar=true manualPan=true");
    }

    private static void runChecks() {
        LayeredAnalysisPanel panel = new LayeredAnalysisPanel();
        panel.setSize(1_000, 420);
        float[] samples = new float[10_000];
        for (int index = 0; index < samples.length; index++) {
            samples[index] = (float) (Math.sin(index * 0.07) * 0.6);
        }
        panel.setData(new AudioData("follow.wav", Path.of("follow.wav"), 100f, samples), null, null);

        panel.beginPlaybackFollow(50.0);
        assertNear(30.0, panel.diagnosticViewEnd() - panel.diagnosticViewStart(), 0.001,
                "Long recordings must begin with a memory-safe 30-second detail window");
        assertNear(50.0, centre(panel), 0.001,
                "Playback must initially be centred in the visible window");

        JScrollBar scroll = panel.timeScrollBar();
        if (!scroll.isEnabled() || scroll.getVisibleAmount() >= scroll.getMaximum()) {
            throw new AssertionError("The horizontal scrollbar must expose the visible time window");
        }
        int initialValue = scroll.getValue();
        panel.setPlaybackPosition(62.0, true);
        assertNear(62.0, centre(panel), 0.001,
                "Active playback must move the view so the playhead stays centred");
        if (scroll.getValue() == initialValue) {
            throw new AssertionError("Playback follow must move the scrollbar thumb with the view");
        }

        panel.zoomTime(.5);
        if (!panel.diagnosticPlaybackFollowing()) {
            throw new AssertionError("Zooming during playback must preserve automatic follow");
        }
        assertNear(62.0, centre(panel), 0.001,
                "Zooming during playback must keep the playhead inside and centred");
        if (scroll.getPreferredSize().height < 17 || scroll.getMinimumSize().height < 17) {
            throw new AssertionError("The timeline scrollbar must retain a visible in-panel height");
        }

        scroll.setValue(100_000);
        if (panel.diagnosticPlaybackFollowing()) {
            throw new AssertionError("Dragging the scrollbar must temporarily suspend automatic follow");
        }
        double manuallyPositionedStart = panel.diagnosticViewStart();
        panel.setPlaybackPosition(82.0, true);
        assertNear(manuallyPositionedStart, panel.diagnosticViewStart(), 0.001,
                "A manually positioned view must remain stable until playback is restarted or sought");

        panel.beginPlaybackFollow(75.0);
        if (!panel.diagnosticPlaybackFollowing()) {
            throw new AssertionError("A new playback/seek must re-enable automatic follow");
        }
        assertNear(75.0, centre(panel), 0.001,
                "Restarted playback must re-centre the time view");

        double[] sought = {Double.NaN};
        int[] selections = {0};
        panel.setSeekListener(value -> sought[0] = value);
        panel.setSelectionListener((start, end) -> selections[0]++);
        panel.setPlaybackPosition(75.0, true);
        int clickX = 700;
        double expectedClickTime = panel.diagnosticViewStart()
                + (clickX - 54.0) / (panel.getWidth() - 74.0)
                * (panel.diagnosticViewEnd() - panel.diagnosticViewStart());
        long now = System.currentTimeMillis();
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, now, 0,
                clickX, 80, 1, false, MouseEvent.BUTTON1));
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, now + 1, 0,
                clickX, 80, 1, false, MouseEvent.BUTTON1));
        assertNear(expectedClickTime, sought[0], 0.001,
                "Click-to-seek must use the exact clicked time");
        if (selections[0] != 0 || panel.hasSelection()) {
            throw new AssertionError("A single click must not create or report a tonic interval");
        }

        int dragEndX = 820;
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, now + 2, 0,
                clickX, 80, 1, false, MouseEvent.BUTTON1));
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, now + 3, 0,
                dragEndX, 80, 0, false, MouseEvent.BUTTON1));
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, now + 4, 0,
                dragEndX, 80, 1, false, MouseEvent.BUTTON1));
        if (selections[0] != 1 || !panel.hasSelection()) {
            throw new AssertionError("A dragged range must be the only gesture that reports a tonic interval");
        }
    }

    private static double centre(LayeredAnalysisPanel panel) {
        return (panel.diagnosticViewStart() + panel.diagnosticViewEnd()) / 2.0;
    }

    private static void assertNear(double expected, double actual, double tolerance, String message) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }
}
