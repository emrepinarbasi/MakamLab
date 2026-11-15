package com.makambox.studio.ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

/** Shared visual language for every workspace, including the restored classic tools. */
final class UiTheme {
    static final Color APP_BACKGROUND = new Color(242, 246, 250);
    static final Color SURFACE = Color.WHITE;
    static final Color SURFACE_SUBTLE = new Color(249, 251, 253);
    static final Color CANVAS = new Color(247, 250, 252);
    static final Color BORDER = new Color(218, 227, 236);
    static final Color GRID = new Color(226, 233, 240);
    static final Color INK = new Color(27, 38, 52);
    static final Color MUTED = new Color(95, 111, 129);
    static final Color ACCENT = new Color(13, 148, 136);
    static final Color ASCENDING = new Color(37, 99, 235);
    static final Color DESCENDING = new Color(220, 88, 52);
    static final Color LEVEL = new Color(100, 116, 139);
    static final Color HOLD = new Color(139, 92, 246);
    /** High-contrast glissando colour, shared by every on-screen analysis view. */
    static final Color GLISSANDO = new Color(190, 24, 48);
    static final Color GLISSANDO_FILL = new Color(190, 24, 48, 54);
    static final Color TARGET = new Color(13, 148, 136);

    private UiTheme() {}

    static JPanel card() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createLineBorder(BORDER));
        return panel;
    }

    static void applyText(Component component, boolean muted, boolean bold) {
        component.setForeground(muted ? MUTED : INK);
        Font font = component.getFont();
        component.setFont(font.deriveFont(bold ? Font.BOLD : Font.PLAIN));
    }

    static void outline(JComponent component) {
        component.setBorder(BorderFactory.createLineBorder(BORDER));
    }
}
