package com.makambox.studio.ui;

import com.makambox.studio.i18n.AppLanguage;
import com.makambox.studio.i18n.LocalizationService;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Release gate that opens every MakamLab module in every supported locale.
 * Visible translated actions must fit their buttons and unresolved [keys]
 * must never reach a window.
 */
public final class AllLocaleButtonFitRegressionTest {
    private static final List<String> MODULES = List.of(
            "makam-home-single_pitch",
            "makam-home-ussak_segah_directional",
            "makam-home-hicaz_augmented_interval",
            "makam-home-glissando_analysis"
    );

    private AllLocaleButtonFitRegressionTest() { }

    public static void main(String[] arguments) throws Exception {
        System.setProperty(LocalizationService.DISABLE_PREFERENCE_WRITES_PROPERTY, "true");
        LocalizationService localization = LocalizationService.getInstance();
        AppLanguage original = localization.currentLanguage();
        Method showHome = MakamFrame.class.getDeclaredMethod("showHomeMenu");
        showHome.setAccessible(true);
        int exitCode = 0;
        try {
            SwingUtilities.invokeAndWait(() -> {
                MakamFrame frame = new MakamFrame();
                try {
                    frame.setLocation(-10_000, -10_000);
                    frame.setSize(1180, 760);
                    frame.setVisible(true);
                    for (AppLanguage language : AppLanguage.supported()) {
                        localization.setLanguage(language);
                        showHome.invoke(frame);
                        layout(frame);
                        assertWindow(frame, language.tag(), "home");
                        for (String moduleName : MODULES) {
                            AbstractButton module = (AbstractButton) findNamed(frame, moduleName);
                            if (module == null) throw new AssertionError("Missing module " + moduleName);
                            module.doClick();
                            layout(frame);
                            assertWindow(frame, language.tag(), moduleName);
                            if ("makam-home-single_pitch".equals(moduleName)) {
                                JComboBox<?> mode = (JComboBox<?>) findNamed(frame,
                                        "makam-analysis-mode");
                                if (mode == null || mode.getItemCount() < 2) {
                                    throw new AssertionError("Between-pitches mode is missing");
                                }
                                mode.setSelectedIndex(1);
                                layout(frame);
                                assertWindow(frame, language.tag(),
                                        moduleName + "/between-pitches");
                            }
                            showHome.invoke(frame);
                        }
                    }
                } catch (ReflectiveOperationException error) {
                    throw new RuntimeException(error);
                } finally {
                    frame.dispose();
                }
            });
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            exitCode = 1;
        } finally {
            localization.setLanguage(original);
        }
        if (exitCode == 0) {
            System.out.println("ALL_LOCALE_BUTTON_FIT_OK locales="
                    + AppLanguage.supported().size() + " modules=" + MODULES.size());
        }
        System.exit(exitCode);
    }

    private static void layout(MakamFrame frame) {
        frame.validate();
        frame.getContentPane().doLayout();
        frame.validate();
    }

    private static void assertWindow(Container root, String locale, String view) {
        for (Component child : root.getComponents()) {
            if (child.isShowing() && child instanceof JTextComponent text) {
                assertResolved(text.getText(), locale, view);
                if ("makam-theoretical-target".equals(text.getName())) {
                    assertTextComponentVisible(text, locale, view);
                }
            }
            if (child.isShowing() && child instanceof AbstractButton button) {
                assertResolved(button.getText(), locale, view);
            }
            if (child.isShowing() && child instanceof AutoFitButton button
                    && !(button instanceof LanguageSelectorButton<?>)) {
                paint(button);
                String text = button.getText();
                if (text != null && !text.isBlank() && !text.startsWith("<html>")
                        && text.codePointCount(0, text.length()) > 2) {
                    Insets insets = button.getInsets();
                    int required = button.getFontMetrics(button.getFont()).stringWidth(text)
                            + insets.left + insets.right + 24;
                    if (button.getIcon() != null) {
                        required += button.getIcon().getIconWidth() + button.getIconTextGap();
                    }
                    if (required > button.getWidth()) {
                        throw new AssertionError(locale + "/" + view
                                + " clipped button '" + text + "': required=" + required
                                + " actual=" + button.getWidth()
                                + " font=" + button.getFont().getSize2D());
                    }
                }
            }
            if (child instanceof Container nested) assertWindow(nested, locale, view);
        }
    }

    private static void assertTextComponentVisible(JTextComponent text, String locale,
                                                   String view) {
        Container parent = text.getParent();
        if (parent == null || text.getY() < 0
                || text.getY() + text.getHeight() > parent.getHeight()) {
            throw new AssertionError(locale + "/" + view
                    + " theory summary extends beyond its card: bounds=" + text.getBounds()
                    + " parent=" + (parent == null ? "null" : parent.getSize()));
        }
        View rootView = text.getUI().getRootView(text);
        Insets insets = text.getInsets();
        float neededHeight = rootView.getPreferredSpan(View.Y_AXIS)
                + insets.top + insets.bottom;
        if (neededHeight > text.getHeight() + 1.0f) {
            throw new AssertionError(locale + "/" + view
                    + " theory summary text is clipped: required=" + neededHeight
                    + " actual=" + text.getHeight() + " text='" + text.getText() + "'");
        }
    }

    private static void assertResolved(String text, String locale, String view) {
        if (text != null && text.matches("(?s).*\\[[A-Za-z][A-Za-z0-9_.-]+].*")) {
            throw new AssertionError(locale + "/" + view
                    + " contains unresolved localization key: " + text);
        }
    }

    private static void paint(AutoFitButton button) {
        BufferedImage image = new BufferedImage(Math.max(1, button.getWidth()),
                Math.max(1, button.getHeight()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        button.paint(graphics);
        graphics.dispose();
    }

    private static Component findNamed(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) {
                Component found = findNamed(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
