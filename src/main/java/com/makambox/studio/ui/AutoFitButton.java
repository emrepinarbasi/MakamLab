package com.makambox.studio.ui;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * A button that preserves its complete label when a translated string is wider
 * than the space offered by the current layout.
 *
 * <p>The normal UI font remains the preferred size.  The font is reduced only
 * while the actual button bounds require it and never below the configured
 * readable minimum.  The minimum width also accounts for the complete text at
 * that minimum size, so layout managers can give a long translated action more
 * room instead of silently clipping it.</p>
 */
public class AutoFitButton extends JButton {
    public static final float DEFAULT_MINIMUM_FONT_SIZE = 9.0f;

    private float minimumFontSize = DEFAULT_MINIMUM_FONT_SIZE;
    private Font preferredFont;
    private boolean applyingFit;
    private boolean fitPending;

    public AutoFitButton() {
        this(null);
    }

    public AutoFitButton(String text) {
        super(text);
        preferredFont = super.getFont();
        setFocusPainted(false);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                scheduleFit();
            }
        });
        addPropertyChangeListener("text", event -> scheduleFit());
        addPropertyChangeListener("icon", event -> scheduleFit());
        addPropertyChangeListener("font", event -> {
            if (!applyingFit && event.getNewValue() instanceof Font font) {
                preferredFont = font;
                scheduleFit();
            }
        });
    }

    /** Set the smallest font size this button is allowed to use. */
    public final void setMinimumAutoFitFontSize(float size) {
        if (!Float.isFinite(size) || size <= 0f) {
            throw new IllegalArgumentException("Minimum font size must be positive");
        }
        minimumFontSize = size;
        revalidate();
        scheduleFit();
    }

    public final float minimumAutoFitFontSize() {
        return minimumFontSize;
    }

    @Override public void addNotify() {
        super.addNotify();
        if (preferredFont == null) preferredFont = super.getFont();
        scheduleFit();
    }

    @Override public Dimension getMinimumSize() {
        Dimension normal = super.getMinimumSize();
        String text = getText();
        if (text == null || text.isBlank() || text.startsWith("<html>")) return normal;
        Font base = preferredFont == null ? super.getFont() : preferredFont;
        Font minimum = base.deriveFont(Math.min(base.getSize2D(), minimumFontSize));
        FontMetrics metrics = getFontMetrics(minimum);
        Insets insets = getInsets();
        // Match fitNow's renderer safety allowance. Advertising a narrower
        // minimum would let the layout truncate a translated label even after
        // it reached the configured minimum font size.
        int width = metrics.stringWidth(text) + insets.left + insets.right + 26;
        Icon icon = getIcon();
        if (icon != null) width += icon.getIconWidth() + getIconTextGap();
        int height = metrics.getHeight() + insets.top + insets.bottom + 2;
        // JButton's default minimum width is normally identical to its
        // preferred width at the full-size font.  Returning that value would
        // prevent the layout manager from ever offering a narrower bound and
        // therefore prevent auto-fitting.  Keep the normal height, but expose
        // the complete label width at the readable minimum font size.
        return new Dimension(width, Math.max(normal.height, height));
    }

    @Override protected void paintComponent(java.awt.Graphics graphics) {
        fitNow();
        super.paintComponent(graphics);
    }

    private void scheduleFit() {
        if (fitPending) return;
        fitPending = true;
        SwingUtilities.invokeLater(() -> {
            fitPending = false;
            fitNow();
        });
    }

    private void fitNow() {
        if (applyingFit || getWidth() <= 0 || getHeight() <= 0) return;
        String text = getText();
        Font base = preferredFont == null ? super.getFont() : preferredFont;
        if (text == null || text.isBlank() || text.startsWith("<html>")) {
            applyFont(base);
            return;
        }

        Insets insets = getInsets();
        // BasicButtonUI keeps a small internal view/text gap in addition to
        // the public border insets. Reserving it here prevents borderline
        // labels from being replaced by an ellipsis by the UI delegate.
        int availableWidth = Math.max(1, getWidth() - insets.left - insets.right - 26);
        int availableHeight = Math.max(1, getHeight() - insets.top - insets.bottom - 2);
        Icon icon = getIcon();
        if (icon != null) availableWidth -= icon.getIconWidth() + getIconTextGap();

        float maximum = base.getSize2D();
        float minimum = Math.min(maximum, minimumFontSize);
        float chosen = maximum;
        if (!fits(base, text, availableWidth, availableHeight)) {
            float low = minimum;
            float high = maximum;
            chosen = minimum;
            for (int index = 0; index < 10; index++) {
                float candidate = (low + high) / 2f;
                if (fits(base.deriveFont(candidate), text, availableWidth, availableHeight)) {
                    chosen = candidate;
                    low = candidate;
                } else {
                    high = candidate;
                }
            }
        }
        applyFont(base.deriveFont(chosen));
        setToolTipText(chosen + .05f < maximum ? text : getToolTipText());
    }

    private boolean fits(Font font, String text, int width, int height) {
        FontMetrics metrics = getFontMetrics(font);
        return metrics.stringWidth(text) <= width && metrics.getHeight() <= height;
    }

    private void applyFont(Font font) {
        if (font.equals(super.getFont())) return;
        applyingFit = true;
        try {
            super.setFont(font);
        } finally {
            applyingFit = false;
        }
    }

    /** Package-private diagnostic used by the headless regression test. */
    float diagnosticPreferredFontSize() {
        return (preferredFont == null ? getFont() : preferredFont).getSize2D();
    }
}
