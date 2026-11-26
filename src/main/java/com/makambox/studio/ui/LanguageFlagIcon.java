package com.makambox.studio.ui;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/** Paints the regional flags without depending on emoji fonts. */
enum LanguageFlagIcon implements Icon {
    TURKEY, UNITED_STATES;

    static Icon forCode(String code) {
        return switch (code) {
            case "TR" -> TURKEY;
            case "EN" -> UNITED_STATES;
            default -> null;
        };
    }

    @Override public int getIconWidth() { return 28; }
    @Override public int getIconHeight() { return 18; }

    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            var frame = new RoundRectangle2D.Double(0.5, 0.5, 27, 17, 4, 4);
            g.clip(frame);
            if (this == TURKEY) {
                Color red = new Color(227, 10, 23);
                g.setColor(red);
                g.fill(frame);
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(5.25, 4.5, 9, 9));
                g.setColor(red);
                g.fill(new Ellipse2D.Double(7.35, 5.4, 7.2, 7.2));
                g.setColor(Color.WHITE);
                g.fill(star(16.1, 9, 2.65, -Math.PI / 5));
            } else {
                double stripe = 18.0 / 13;
                g.setColor(Color.WHITE);
                g.fill(frame);
                g.setColor(new Color(178, 34, 52));
                for (int row = 0; row < 13; row += 2)
                    g.fill(new Rectangle2D.Double(0, row * stripe, 28, stripe));
                g.setColor(new Color(60, 59, 110));
                g.fill(new Rectangle2D.Double(0, 0, 12, stripe * 7));
                g.setColor(Color.WHITE);
                for (int row = 0; row < 9; row++) {
                    for (int column = 0; column < 6 - row % 2; column++) {
                        double cx = 1.1 + (column + (row % 2) * .5) * 1.95;
                        g.fill(star(cx, 0.9 + row * .98, .40, -Math.PI / 2));
                    }
                }
            }
            g.setColor(new Color(0, 0, 0, 35));
            g.draw(frame);
        } finally {
            g.dispose();
        }
    }

    private static Path2D star(double x, double y, double radius, double angle) {
        Path2D path = new Path2D.Double();
        path.moveTo(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius);
        for (int point = 1; point < 10; point++) {
            double r = point % 2 == 0 ? radius : radius * .382;
            double a = angle + point * Math.PI / 5;
            path.lineTo(x + Math.cos(a) * r, y + Math.sin(a) * r);
        }
        path.closePath();
        return path;
    }
}
