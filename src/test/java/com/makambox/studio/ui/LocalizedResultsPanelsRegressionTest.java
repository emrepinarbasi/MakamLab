package com.makambox.studio.ui;

import com.makambox.studio.i18n.AppLanguage;
import com.makambox.studio.i18n.I18n;
import com.makambox.studio.i18n.LocalizationService;
import com.makambox.studio.study.glissando.GlissandoDirection;
import com.makambox.studio.study.hicaz.HicazPhraseDirection;
import com.makambox.studio.study.hicaz.HicazTetrachordDegree;

import javax.swing.SwingUtilities;

/** Regression coverage for live result-panel language switching and locale numbers. */
public final class LocalizedResultsPanelsRegressionTest {
    private LocalizedResultsPanelsRegressionTest() { }

    public static void main(String[] arguments) throws Exception {
        LocalizationService i18n = I18n.get();
        AppLanguage original = i18n.currentLanguage();
        HicazAugmentedIntervalResultsPanel[] hicaz = new HicazAugmentedIntervalResultsPanel[1];
        PitchPairGlissandoResultsPanel[] glissando = new PitchPairGlissandoResultsPanel[1];
        LayeredAnalysisPanel[] canvas = new LayeredAnalysisPanel[1];
        try {
            i18n.setLanguageTag("en-US");
            SwingUtilities.invokeAndWait(() -> {
                hicaz[0] = new HicazAugmentedIntervalResultsPanel();
                glissando[0] = new PitchPairGlissandoResultsPanel();
                canvas[0] = new LayeredAnalysisPanel();
                canvas[0].setLocalizedCanvasText(i18n.text("canvas.empty"),
                        i18n.text("canvas.tonicPick"), i18n.text("canvas.boundaryExcluded"),
                        i18n.text("canvas.navigationHint"), i18n.text("canvas.waveformBuilding"));
                hicaz[0].refreshLocalization();
                glissando[0].refreshLocalization();
            });
            check(hicaz[0].diagnosticTabTitle(0).equals("Phrases"),
                    "Hicaz tabs must use the English bundle");
            check(glissando[0].diagnosticTabTitle(0).equals("Primary glissandi"),
                    "glissando tabs must use the English bundle");
            check(hicaz[0].diagnosticColumnName(1, 1).equals("Degree"),
                    "Hicaz table headers must be localized");
            check(glissando[0].diagnosticColumnName(1).equals("Direction"),
                    "glissando table headers must be localized");
            check(hicaz[0].diagnosticSummaryHeading(HicazPhraseDirection.ASCENDING,
                            HicazTetrachordDegree.DEGREE_2).contains("Ascending"),
                    "Hicaz summary headings must be localized");
            check(glissando[0].diagnosticSummaryHeading(GlissandoDirection.DESCENDING)
                            .contains("Descending"),
                    "glissando summary headings must be localized");
            check(hicaz[0].diagnosticFormattedNumber(12.5).contains("."),
                    "English result numbers must use a decimal point");
            check(canvas[0].diagnosticEmptyAudioMessage().equals(
                            "Open an audio file or load the example melody"),
                    "the reusable canvas must accept MakamLab-localized messages");
            check(ProgressEta.describe(50, 10).contains("remaining"),
                    "progress and ETA text must use the English bundle");

            i18n.setLanguageTag("tr-TR");
            SwingUtilities.invokeAndWait(() -> {
                hicaz[0].refreshLocalization();
                glissando[0].refreshLocalization();
                canvas[0].setLocalizedCanvasText(i18n.text("canvas.empty"),
                        i18n.text("canvas.tonicPick"), i18n.text("canvas.boundaryExcluded"),
                        i18n.text("canvas.navigationHint"), i18n.text("canvas.waveformBuilding"));
            });
            check(!hicaz[0].diagnosticTabTitle(0).equals("Phrases"),
                    "Hicaz tabs must update without rebuilding the panel");
            check(!glissando[0].diagnosticTabTitle(0).equals("Primary glissandi"),
                    "glissando tabs must update without rebuilding the panel");
            check(hicaz[0].diagnosticFormattedNumber(12.5).contains(","),
                    "Turkish result numbers must use a decimal comma");
            check(glissando[0].diagnosticFormattedNumber(12.5).contains(","),
                    "glissando numbers must follow the selected locale");
            check(!canvas[0].diagnosticEmptyAudioMessage().equals(
                            "Open an audio file or load the example melody"),
                    "canvas messages must update without rebuilding the panel");
            check(!i18n.text("result.summary.single").startsWith("Theoretical"),
                    "dynamic result summaries must come from the selected-language catalogue");
            check(!ProgressEta.describe(50, 10).contains("remaining"),
                    "progress and ETA text must switch with the live locale");
        } finally {
            i18n.setLanguage(original);
        }
        System.out.println("LOCALIZED_RESULTS_PANELS_REGRESSION_OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
