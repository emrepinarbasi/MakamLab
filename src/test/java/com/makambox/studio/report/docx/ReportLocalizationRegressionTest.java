package com.makambox.studio.report.docx;

import com.makambox.studio.i18n.AppLanguage;
import com.makambox.studio.i18n.LocalizationService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Ensures the selected application locale also controls DOCX report output. */
public final class ReportLocalizationRegressionTest {
    private ReportLocalizationRegressionTest() { }

    public static void main(String[] arguments) throws Exception {
        LocalizationService service = LocalizationService.getInstance();
        AppLanguage original = service.currentLanguage();
        Path directory = Files.createTempDirectory("makamlab-report-locale-");
        AcademicStudyDocument sample = localizedSampleReport();
        try {
            service.setLanguageTag("tr-TR");
            Path turkish = directory.resolve("tr.docx");
            new DocxStudyReportExporter().export(sample, turkish);
            String turkishText = documentText(turkish);
            check(turkishText.contains("1. Kaynak ve yöntem"),
                    "Turkish selection must produce Turkish report headings");
            check(turkishText.contains("22,641509 C"),
                    "Turkish report numbers must use the Turkish decimal separator");
            check(!turkishText.contains("{2}"),
                    "Turkish report must not expose an unresolved MessageFormat placeholder");
            check(turkishText.contains("C'dir; merkezî eğilim"),
                    "Turkish academic interpretation must preserve the apostrophe and tendency value");
            assertNoUnresolvedKeys(turkishText, "Turkish");

            service.setLanguageTag("en-US");
            Path english = directory.resolve("en.docx");
            new DocxStudyReportExporter().export(sample, english);
            String englishText = documentText(english);
            check(englishText.contains("1. Source and method"),
                    "English selection must produce English report headings");
            check(englishText.contains("22.641509 C"),
                    "English report numbers must use the English decimal separator");
            check(!englishText.contains("1. Kaynak ve yöntem"),
                    "English report must not retain the Turkish source/method heading");
            assertNoUnresolvedKeys(englishText, "English");

            for (String properName : new String[]{"Kürdilihicazkâr", "Nim Zirgüle"}) {
                check(turkishText.contains(properName),
                        "Turkish report lost a makam/pitch proper name: " + properName);
                check(englishText.contains(properName),
                        "English report translated a makam/pitch proper name: " + properName);
            }
            check(!ReportLocalization.dateTime(sample.generatedAt()).isBlank(),
                    "Exporter metadata date must follow the selected locale");
            check(AppLanguage.supported().size() == 2,
                    "reports must expose only Turkish and English selections");
            System.out.println("REPORT_LOCALIZATION_REGRESSION_OK tr=" + turkish + " en="
                    + english);
        } finally {
            service.setLanguage(original);
        }
    }

    /**
     * Uses the same numerical evidence as the general report regression but
     * replaces fixture-only Turkish prose with the canonical internal labels
     * produced by the application.  This makes the foreign-locale check cover
     * the complete DOCX, not merely its headings.
     */
    private static AcademicStudyDocument localizedSampleReport() {
        AcademicStudyDocument base = DocxStudyReportRegressionTest.sampleReport();
        AcademicStudyDocument.TonicEvidence sourceTonic = base.tonic();
        AcademicStudyDocument.TonicEvidence tonic = new AcademicStudyDocument.TonicEvidence(
                "Son kararlı sesten otomatik öneri, kullanıcı onaylı",
                sourceTonic.hertz(), sourceTonic.startSeconds(), sourceTonic.endSeconds(),
                sourceTonic.confidence(), sourceTonic.robustSpreadCent(),
                sourceTonic.voicedFrameCount(), sourceTonic.userConfirmed(), "");
        List<AcademicStudyDocument.ProfileComparison> profiles = base.profileComparisons().stream()
                .map(value -> new AcademicStudyDocument.ProfileComparison(
                        value.rank(), value.profileName(), value.scorePercent(),
                        value.medianAbsoluteErrorCent(), value.p90AbsoluteErrorCent(),
                        value.withinOneHcPercent(), value.degreeCoveragePercent(),
                        value.evidenceEventCount(), value.rank() == 1
                        ? "Kullanıcının seçtiği makam profili"
                        : "Karara hizalı aralık benzerliği",
                        value.indistinguishable(), value.firstFourScorePercent(),
                        value.firstFourObservedCount(), value.firstFourDegrees(),
                        value.evidenceSufficient(), value.evidenceSummary(),
                        value.firstFourSignature(), value.firstFourIndistinguishableProfiles()))
                .toList();
        return new AcademicStudyDocument("MakamLab", base.source(), base.theory(), tonic,
                base.contacts(), base.tonicTrace(), profiles, List.of(), List.of(),
                base.generatedAt(), base.directionalContexts());
    }

    private static String documentText(Path path) throws Exception {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(path))) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> text.append(paragraph.getText()).append('\n'));
            document.getTables().forEach(table -> table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> text.append(cell.getText()).append('\n'))));
            document.getHeaderList().forEach(header -> header.getParagraphs().forEach(
                    paragraph -> text.append(paragraph.getText()).append('\n')));
            document.getFooterList().forEach(footer -> footer.getParagraphs().forEach(
                    paragraph -> text.append(paragraph.getText()).append('\n')));
            return text.toString();
        }
    }

    private static void assertNoForeignReportLeak(String text, String language) {
        for (String forbidden : new String[]{
                "1. Kaynak ve yöntem", "Karar perdesinin belirlenmesi",
                "Ölçülebilir olayların tek tek dökümü", "Net yön", "Kısa temas",
                "Yatay/Karma", "Çıkıcı\n", "İnici\n", "Segâh kalışı\n",
                "Geçiş\n", "Uzatma\n", "Kalış\n", "Ölçülebilir\n",
                "Belirsiz/diğer", "Otomatik aday", "Kullanıcı tarafından onaylandı",
                "Son kararlı ses + kullanıcı onayı", "Dahil\n", "Dışlandı",
                "Sağ kanal", "Belirtilmedi", "Sayfa ", "40 ms pencere",
                "Kanonik perde kimlikleri", "Alkış bölümü", "Bu sonuç tek",
                "Aralık profili benzerliği", "Seçilen profile yakın",
                "Yalnız aralık profiliyle ayrışmıyor", "İkinci derece kanıtı farklı",
                "1. Source and method", "Determining the tonic",
                "Individual measurable events", "Net direction", "Brief contact",
                "Horizontal/Mixed", "Transition\n", "Sustained\n", "Cadential hold\n",
                "Measurable\n", "Uncertain/other", "Automatic candidate",
                "Confirmed by the user", "Right channel", "Not specified", "Page "}) {
            check(!text.contains(forbidden), language + " report leaked a foreign label: "
                    + forbidden);
        }
    }

    private static void assertNoUnresolvedKeys(String text, String language) {
        for (String prefix : new String[]{"[report.", "[unit.", "[audio.",
                "[format.", "[common.", "[result."}) {
            check(!text.contains(prefix), language + " report contains an unresolved i18n key: "
                    + prefix);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
