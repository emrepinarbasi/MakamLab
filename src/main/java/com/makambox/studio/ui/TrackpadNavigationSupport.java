package com.makambox.studio.ui;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseWheelEvent;
import java.util.function.Consumer;

/**
 * Captures precision touchpad scrolling before nested Swing scroll panes can
 * consume it. macOS reports native horizontal scrolling as a wheel event with
 * the Shift modifier, while vertical scrolling has no modifier.
 */
final class TrackpadNavigationSupport {
    enum Action { TIME_ZOOM, TIME_PAN, PITCH_ZOOM, PITCH_PAN }

    record Gesture(Action action, double amount, int x, int y) { }

    private static final String CLIENT_KEY = TrackpadNavigationSupport.class.getName() + ".registration";

    private TrackpadNavigationSupport() { }

    static void install(JComponent region, Consumer<Gesture> consumer) {
        Registration previous = (Registration) region.getClientProperty(CLIENT_KEY);
        if (previous != null) previous.detach();
        Registration registration = new Registration(region, consumer);
        region.putClientProperty(CLIENT_KEY, registration);
        registration.attach();
        region.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) return;
            if (region.isDisplayable()) registration.attach(); else registration.detach();
        });
    }

    private static final class Registration implements AWTEventListener {
        private final JComponent region;
        private final Consumer<Gesture> consumer;
        private boolean attached;

        private Registration(JComponent region, Consumer<Gesture> consumer) {
            this.region = region;
            this.consumer = consumer;
        }

        private void attach() {
            if (attached) return;
            Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.MOUSE_WHEEL_EVENT_MASK);
            attached = true;
        }

        private void detach() {
            if (!attached) return;
            Toolkit.getDefaultToolkit().removeAWTEventListener(this);
            attached = false;
        }

        @Override public void eventDispatched(AWTEvent awtEvent) {
            if (!(awtEvent instanceof MouseWheelEvent event) || event.isConsumed()) return;
            if (!(event.getSource() instanceof Component source)) return;
            if (source != region && !SwingUtilities.isDescendingFrom(source, region)) return;
            Point point = SwingUtilities.convertPoint(source, event.getPoint(), region);
            Action action;
            if (event.isControlDown() || event.isMetaDown()) action = Action.TIME_ZOOM;
            else if (event.isAltDown()) action = Action.PITCH_ZOOM;
            else if (event.isShiftDown()) action = Action.TIME_PAN;
            else action = Action.PITCH_PAN;
            double amount = event.getPreciseWheelRotation();
            if (!Double.isFinite(amount) || Math.abs(amount) < 0.0001) return;
            consumer.accept(new Gesture(action, amount, point.x, point.y));
            event.consume();
        }
    }
}
