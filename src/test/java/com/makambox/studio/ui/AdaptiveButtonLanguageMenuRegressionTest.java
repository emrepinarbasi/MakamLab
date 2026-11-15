package com.makambox.studio.ui;

import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Headless checks for translated button fitting and alphabetic language menus. */
public final class AdaptiveButtonLanguageMenuRegressionTest {
    private AdaptiveButtonLanguageMenuRegressionTest() {}

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AutoFitButton button = new AutoFitButton(
                    "Seçili bölgeyi karar olarak kullan");
            button.setFont(button.getFont().deriveFont(Font.BOLD, 16f));
            button.setSize(178, 32);
            paint(button);
            if (button.getFont().getSize2D() >= 15.95f) {
                throw new AssertionError("Long action did not reduce its font in a narrow bound");
            }
            if (button.getFont().getSize2D() < button.minimumAutoFitFontSize() - .05f) {
                throw new AssertionError("Auto-fit crossed the readable minimum font size");
            }
            if (button.getMinimumSize().width <= 120) {
                throw new AssertionError("Minimum width does not protect the complete translated label");
            }

            button.setSize(520, 42);
            paint(button);
            if (Math.abs(button.getFont().getSize2D()
                    - button.diagnosticPreferredFontSize()) > .1f) {
                throw new AssertionError("Button did not restore its preferred font when space returned");
            }

            record Language(String flag, String name) {}
            LanguageSelectorButton<Language> selector = new LanguageSelectorButton<>(
                    Language::flag, Language::name);
            Language turkish = new Language("🇹🇷", "Türkçe");
            Language english = new Language("🇬🇧", "English — United Kingdom");
            Language arabic = new Language("🇸🇦", "Arabic");
            selector.setOptions(List.of(turkish, english, arabic), turkish);
            List<String> sorted = selector.diagnosticSortedDisplayNames();
            if (!sorted.equals(List.of("Arabic", "English — United Kingdom", "Türkçe"))) {
                throw new AssertionError("Language list is not alphabetic: " + sorted);
            }
            AtomicReference<Language> callback = new AtomicReference<>();
            selector.setSelectionListener(callback::set);
            selector.diagnosticSelect(english);
            if (callback.get() != english || selector.selectedOption() != english
                    || !"🇬🇧".equals(selector.getText())) {
                throw new AssertionError("Language selection callback/flag was not updated");
            }
        });
        System.out.println("AdaptiveButtonLanguageMenuRegressionTest passed");
    }

    private static void paint(AutoFitButton button) {
        BufferedImage image = new BufferedImage(Math.max(1, button.getWidth()),
                Math.max(1, button.getHeight()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        button.paint(graphics);
        graphics.dispose();
    }
}
