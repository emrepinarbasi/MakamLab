package com.makambox.studio.ui;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.DoubleConsumer;

/** Optional macOS magnification gesture bridge; silently falls back on other platforms. */
final class TrackpadZoomSupport {
    private static final String LISTENER_KEY = TrackpadZoomSupport.class.getName() + ".listener";

    private TrackpadZoomSupport() {}

    static boolean install(JComponent component, DoubleConsumer listener) {
        try {
            Class<?> gestureListener = Class.forName("com.apple.eawt.event.GestureListener");
            Class<?> magnificationListener = Class.forName("com.apple.eawt.event.MagnificationListener");
            Class<?> utilities = Class.forName("com.apple.eawt.event.GestureUtilities");
            Object proxy = Proxy.newProxyInstance(TrackpadZoomSupport.class.getClassLoader(),
                    new Class<?>[]{magnificationListener}, (ignored, method, arguments) -> {
                        if ("magnify".equals(method.getName()) && arguments != null && arguments.length == 1) {
                            Method getter = arguments[0].getClass().getMethod("getMagnification");
                            double magnification = ((Number) getter.invoke(arguments[0])).doubleValue();
                            SwingUtilities.invokeLater(() -> listener.accept(magnification));
                        }
                        return null;
                    });
            Method add = utilities.getMethod("addGestureListenerTo", JComponent.class, gestureListener);
            add.invoke(null, component, proxy);
            // Keep the proxy strongly reachable for the complete component lifetime.
            component.putClientProperty(LISTENER_KEY, proxy);
            return true;
        } catch (Throwable failure) {
            component.putClientProperty(LISTENER_KEY, failure);
            // Windows/Linux and newer runtimes still receive precise mouse-wheel zoom events.
            return false;
        }
    }

    static boolean nativeMagnificationInstalled(JComponent component) {
        Object value = component.getClientProperty(LISTENER_KEY);
        return value != null && !(value instanceof Throwable);
    }
}
