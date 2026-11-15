package com.makambox.studio.i18n;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.ComponentOrientation;
import java.text.Collator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Regression coverage for the deliberately bilingual MakamLab interface. */
public final class LocalizationServiceRegressionTest {
    private LocalizationServiceRegressionTest() { }

    public static void main(String[] arguments) {
        List<AppLanguage> supported = AppLanguage.supported();
        check(supported.size() == 2, "MakamLab must expose only Turkish and English");
        check(new HashSet<>(supported.stream().map(AppLanguage::tag).toList()).size() == 2,
                "language tags must be unique");
        check(AppLanguage.matchSystemLocale(Locale.forLanguageTag("tr-TR")).tag().equals("tr-TR"),
                "Turkish systems must open in Turkish");
        check(AppLanguage.matchSystemLocale(Locale.forLanguageTag("en-AU")).tag().equals("en-US"),
                "every English system variant must use the single English interface");
        check(AppLanguage.matchSystemLocale(Locale.forLanguageTag("my-MM")).tag().equals("en-US"),
                "unsupported system languages must fall back to English");

        LocalizationService service = LocalizationService.getInstance();
        AppLanguage original = service.currentLanguage();
        try {
            service.setLanguageTag("tr-TR");
            check(!service.isRightToLeft(), "the bilingual interface is left-to-right");
            JPanel parent = new JPanel();
            JLabel child = new JLabel("Uşşak / Segâh");
            parent.add(child);
            service.applyOrientation(parent);
            check(parent.getComponentOrientation() == ComponentOrientation.LEFT_TO_RIGHT
                            && child.getComponentOrientation() == ComponentOrientation.LEFT_TO_RIGHT,
                    "orientation must reach nested Swing components");
            check(child.getText().equals("Uşşak / Segâh"),
                    "orientation must not alter scientific makam/pitch names");
        } finally {
            service.setLanguage(original);
        }

        List<AppLanguage> alphabetic = service.availableLanguages();
        Collator collator = Collator.getInstance(service.locale());
        collator.setStrength(Collator.PRIMARY);
        for (int index = 1; index < alphabetic.size(); index++) {
            String previous = service.languageDisplayName(alphabetic.get(index - 1));
            String current = service.languageDisplayName(alphabetic.get(index));
            check(collator.compare(previous, current) <= 0,
                    "language menu must be alphabetic: " + previous + " / " + current);
        }
        String missing = service.textOrDefault("test.missing.translation",
                "A fallback in another language must not appear");
        check(missing.equals("[test.missing.translation]"),
                "a missing key must be visible instead of mixing languages");
        String properNames = service.text("theory.glissando.span",
                "Kürdilihicazkâr", "Nim Zirgüle", "90.57", "4.000");
        check(properNames.contains("Kürdilihicazkâr") && properNames.contains("Nim Zirgüle"),
                "proper names passed as parameters must remain exact");
        System.out.println("LOCALIZATION_SERVICE_REGRESSION_OK languages=" + supported.size()
                + " locale=" + service.languageTag());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
