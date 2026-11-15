package com.makambox.studio.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Single-row layout that gives every action its preferred width when possible,
 * then proportionally contracts them toward their text-safe minimum widths.
 */
final class AdaptiveButtonRowLayout implements LayoutManager {
    private final int gap;

    AdaptiveButtonRowLayout(int gap) {
        this.gap = Math.max(0, gap);
    }

    @Override public void addLayoutComponent(String name, Component component) { }

    @Override public void removeLayoutComponent(Component component) { }

    @Override public Dimension preferredLayoutSize(Container parent) {
        return layoutSize(parent, false);
    }

    @Override public Dimension minimumLayoutSize(Container parent) {
        return layoutSize(parent, true);
    }

    private Dimension layoutSize(Container parent, boolean minimum) {
        Insets insets = parent.getInsets();
        int width = insets.left + insets.right;
        int height = 0;
        int count = 0;
        for (Component component : parent.getComponents()) {
            if (!component.isVisible()) continue;
            Dimension size = minimum ? component.getMinimumSize() : component.getPreferredSize();
            width += size.width;
            height = Math.max(height, size.height);
            count++;
        }
        width += Math.max(0, count - 1) * gap;
        return new Dimension(width, height + insets.top + insets.bottom);
    }

    @Override public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        List<Component> components = new ArrayList<>();
        for (Component component : parent.getComponents()) {
            if (component.isVisible()) components.add(component);
        }
        if (components.isEmpty()) return;

        int gaps = Math.max(0, components.size() - 1) * gap;
        int available = Math.max(0, parent.getWidth() - insets.left - insets.right - gaps);
        int[] preferred = new int[components.size()];
        int[] minimum = new int[components.size()];
        int preferredTotal = 0;
        int minimumTotal = 0;
        for (int index = 0; index < components.size(); index++) {
            Component component = components.get(index);
            minimum[index] = component.getMinimumSize().width;
            // A translated AutoFitButton can legitimately advertise a text-
            // safe minimum wider than Swing's look-and-feel preferred width.
            // Never cap that minimum at the stale preferred width, otherwise
            // the layout creates the very ellipsis the component is designed
            // to prevent.
            preferred[index] = Math.max(component.getPreferredSize().width, minimum[index]);
            preferredTotal += preferred[index];
            minimumTotal += minimum[index];
        }

        int[] widths = preferred.clone();
        if (available < preferredTotal) {
            if (available >= minimumTotal) {
                int flexibleTotal = Math.max(1, preferredTotal - minimumTotal);
                int extra = available - minimumTotal;
                for (int index = 0; index < widths.length; index++) {
                    int flexible = preferred[index] - minimum[index];
                    widths[index] = minimum[index]
                            + (int) Math.floor(extra * (double) flexible / flexibleTotal);
                }
            } else {
                double scale = available / (double) Math.max(1, minimumTotal);
                for (int index = 0; index < widths.length; index++) {
                    widths[index] = Math.max(30, (int) Math.floor(minimum[index] * scale));
                }
            }
        }
        int usedWidth = 0;
        for (int width : widths) usedWidth += width;
        // Only distribute rounding pixels when the row actually had to
        // contract.  When every button already has its preferred width, the
        // remaining space is deliberately kept at the line start so the
        // complete action group stays flush with the line end.
        if (available < preferredTotal) {
            int roundingRemainder = available - usedWidth;
            for (int index = 0;
                 roundingRemainder > 0 && index < widths.length;
                 index++, roundingRemainder--) {
                widths[index]++;
            }
        }

        int used = gaps;
        for (int width : widths) used += width;
        boolean leftToRight = parent.getComponentOrientation().isLeftToRight();
        // Align to LINE_END: physical right for LTR interfaces and physical
        // left for RTL interfaces.  The previous implementation always began
        // at the left inset, which left a large unused area to the right of
        // Turkish/English header actions on wide windows.
        int x = leftToRight
                ? Math.max(insets.left, parent.getWidth() - insets.right - used)
                : Math.min(parent.getWidth() - insets.right, insets.left + used);
        for (int index = 0; index < components.size(); index++) {
            Component component = components.get(index);
            int height = Math.min(component.getPreferredSize().height,
                    parent.getHeight() - insets.top - insets.bottom);
            int y = insets.top + Math.max(0,
                    (parent.getHeight() - insets.top - insets.bottom - height) / 2);
            if (leftToRight) {
                component.setBounds(x, y, widths[index], height);
                x += widths[index] + gap;
            } else {
                x -= widths[index];
                component.setBounds(x, y, widths[index], height);
                x -= gap;
            }
        }
    }
}
