package com.makambox.studio.ui;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;

/** Verifies native magnification registration and macOS horizontal-scroll routing. */
public final class TrackpadGestureRegressionTest {
    public static void main(String[] args) throws Exception {
        List<TrackpadNavigationSupport.Gesture> gestures = new ArrayList<>();
        JFrame[] holder = new JFrame[1];
        JLabel[] target = new JLabel[1];
        JPanel[] region = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame();
            JPanel panel = new JPanel(new BorderLayout());
            JLabel child = new JLabel("gesture target");
            panel.add(child);
            TrackpadNavigationSupport.install(panel, gestures::add);
            if (!TrackpadZoomSupport.install(panel, ignored -> {})) {
                throw new AssertionError("macOS magnification listener could not be installed");
            }
            frame.setContentPane(panel);
            frame.setSize(320, 180);
            frame.setLocation(-10000, -10000);
            frame.setVisible(true);
            holder[0] = frame;
            target[0] = child;
            region[0] = panel;
        });

        post(target[0], InputEvent.SHIFT_DOWN_MASK, 1.25);
        post(target[0], 0, -0.75);
        post(target[0], InputEvent.CTRL_DOWN_MASK, 0.50);
        post(target[0], InputEvent.ALT_DOWN_MASK, -0.50);
        SwingUtilities.invokeAndWait(() -> {});

        if (gestures.size() != 4) throw new AssertionError("expected four gestures, got " + gestures.size());
        assertAction(gestures.get(0), TrackpadNavigationSupport.Action.TIME_PAN);
        assertAction(gestures.get(1), TrackpadNavigationSupport.Action.PITCH_PAN);
        assertAction(gestures.get(2), TrackpadNavigationSupport.Action.TIME_ZOOM);
        assertAction(gestures.get(3), TrackpadNavigationSupport.Action.PITCH_ZOOM);
        if (!TrackpadZoomSupport.nativeMagnificationInstalled(region[0])) {
            throw new AssertionError("native magnification registration was lost");
        }

        SwingUtilities.invokeAndWait(holder[0]::dispose);
        System.out.println("TRACKPAD_GESTURE_OK native=true horizontal=true vertical=true");
    }

    private static void post(JLabel source, int modifiers, double rotation) {
        MouseWheelEvent event = new MouseWheelEvent(source, MouseEvent.MOUSE_WHEEL,
                System.currentTimeMillis(), modifiers, 20, 20, 20, 20, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, (int) Math.signum(rotation), rotation);
        Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(event);
    }

    private static void assertAction(TrackpadNavigationSupport.Gesture gesture,
                                     TrackpadNavigationSupport.Action expected) {
        if (gesture.action() != expected) {
            throw new AssertionError("expected " + expected + ", got " + gesture.action());
        }
    }
}
