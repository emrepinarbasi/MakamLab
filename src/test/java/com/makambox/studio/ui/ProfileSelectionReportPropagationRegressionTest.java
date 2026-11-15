package com.makambox.studio.ui;

import com.makambox.studio.i18n.LocalizationService;
import com.makambox.studio.report.docx.AcademicStudyCharts;
import com.makambox.studio.report.docx.AcademicStudyDocument;
import com.makambox.studio.report.docx.DocxStudyReportExporter;
import com.makambox.studio.study.FirstFourProfileReferenceCatalog;
import com.makambox.studio.study.FirstFourProfileSelection;
import com.makambox.studio.study.MakamProfileComparator;
import com.makambox.studio.study.MakamProfileComparison;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** Ensures one frozen profile selection reaches comparison, DOCX rows and chart unchanged. */
public final class ProfileSelectionReportPropagationRegressionTest {
    private static final List<String> CORE = List.of(
            "Çargâh", "Kürdî", "Rast", "Uşşak", "Hicaz");
    private static final List<String> OPTIONAL = List.of(
            "Sabâ", "Segâh", "Müstear", "Hüzzam", "Nikriz", "Pençgâh", "Ferahnâk", "Nişabur");

    public static void main(String[] args) throws Exception {
        System.setProperty(LocalizationService.DISABLE_PREFERENCE_WRITES_PROPERTY, "true");
        LocalizationService.getInstance().setLanguageTag("tr-TR");

        FrozenReport defaults = freeze(FirstFourProfileSelection.defaults());
        check(defaults.names().equals(CORE),
                "default comparison must contain exactly the five required profiles");

        FirstFourProfileSelection withSaba =
                FirstFourProfileSelection.ofOptionalDisplayNames(List.of("Sabâ"));
        FrozenReport saba = freeze(withSaba);
        List<String> expectedSaba = new ArrayList<>(CORE);
        expectedSaba.add("Sabâ");
        check(saba.names().equals(expectedSaba),
                "one optional toggle must add only that profile after the five core profiles");

        Path directory = Files.createTempDirectory("makamlab-profile-selection-report");
        String defaultsText = exportText(defaults.document(), directory.resolve("default-five.docx"));
        assertContainsExactly(defaultsText, CORE, OPTIONAL,
                "default DOCX must not retain a stale supplementary profile");

        String sabaText = exportText(saba.document(), directory.resolve("default-plus-saba.docx"));
        List<String> unselectedOptional = OPTIONAL.stream()
                .filter(value -> !value.equals("Sabâ")).toList();
        assertContainsExactly(sabaText, expectedSaba, unselectedOptional,
                "DOCX must use the same frozen five-plus-one selection as the comparator");

        FrozenReport all = freeze(FirstFourProfileSelection.allProfiles());
        List<String> allNames = new ArrayList<>(CORE);
        allNames.addAll(OPTIONAL);
        check(all.names().equals(allNames),
                "all-profiles audit must preserve the exact thirteen-name catalogue order");
        BufferedImage chart = new AcademicStudyCharts().profileRanking(all.document());
        int expectedHeight = Math.max(720, 125 + allNames.size() * 62 + 100);
        check(chart.getHeight() == expectedHeight,
                "profile chart must grow to display all selected rows instead of truncating at eight");
        String allText = exportText(all.document(), directory.resolve("all-thirteen.docx"));
        assertContainsExactly(allText, allNames, List.of(),
                "all thirteen selected profiles must survive into the DOCX tables");

        check(FirstFourProfileReferenceCatalog.coreDisplayNames().equals(CORE),
                "the five required display names and diacritics must remain exact");
        check(FirstFourProfileReferenceCatalog.optionalDisplayNames().equals(OPTIONAL),
                "the eight optional display names and diacritics must remain exact");
        System.out.println("PROFILE_SELECTION_REPORT_PROPAGATION_OK " + directory);
    }

    private static FrozenReport freeze(FirstFourProfileSelection selection) {
        MakamProfileComparison comparison = new MakamProfileComparator().compare(
                List.of(), 220.0, "Uşşak", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                selection);
        check(comparison.firstFourProfileSelection().equals(selection),
                "comparison result must retain the immutable selection snapshot");
        List<String> comparisonNames = comparison.candidates().stream()
                .map(MakamProfileComparison.Candidate::makamName).toList();
        check(comparisonNames.equals(selection.includedDisplayNames()),
                "comparator candidates must match the frozen selection exactly");

        List<AcademicStudyDocument.ProfileComparison> rows =
                MakamDocxReportHandler.profileRows(comparison).stream()
                        // This fixture tests frozen profile selection propagation,
                        // not the independent insufficient-evidence gate. Mark the
                        // otherwise empty comparison as reportable so every selected
                        // first-four profile must survive into the DOCX table.
                        .map(value -> new AcademicStudyDocument.ProfileComparison(
                                value.rank(), value.profileName(), value.scorePercent(),
                                value.medianAbsoluteErrorCent(), value.p90AbsoluteErrorCent(),
                                value.withinOneHcPercent(), value.degreeCoveragePercent(),
                                value.evidenceEventCount(), value.verdict(),
                                value.indistinguishable(), value.firstFourScorePercent(),
                                value.firstFourObservedCount(), value.firstFourDegrees(),
                                true, "Selection propagation fixture", value.firstFourSignature(),
                                value.firstFourIndistinguishableProfiles()))
                        .toList();
        List<String> reportNames = rows.stream()
                .map(AcademicStudyDocument.ProfileComparison::profileName).toList();
        check(reportNames.equals(comparisonNames),
                "DOCX adapter must neither add, drop nor rename a selected profile");
        return new FrozenReport(reportNames, document(rows));
    }

    private static AcademicStudyDocument document(
            List<AcademicStudyDocument.ProfileComparison> profiles) {
        return new AcademicStudyDocument("MakamLab — Tek Perde Akademik İcra Analizi",
                new AcademicStudyDocument.Source("test.wav", "/test.wav", "abc", 12.0, 22_050,
                        "Mono", "MakamLab test", "Test motoru"),
                new AcademicStudyDocument.Theory("Uşşak", "Dügâh", "Nevâ",
                        220.0, 498.113, 22.0, 293.67, "Arel-Ezgi-Uzdilek / 53-Hc"),
                new AcademicStudyDocument.TonicEvidence("Kullanıcı seçimi", 220.0,
                        10.0, 11.0, 0.95, 2.0, 25, true, "Onaylandı"),
                List.of(new AcademicStudyDocument.Event(1, 1.0, 1.5, 293.67,
                        498.113, 498.113, 0.0, 0.0, 0.0, 0.0, 2.0, 0.95,
                        "Yatay/Karma", "Uzatma", true, true, true, "")),
                List.of(new AcademicStudyDocument.TracePoint(10.2, 220.0, 0.95, true)),
                profiles, List.of("Regresyon yöntemi"), List.of("Regresyon sınırlılığı"),
                ZonedDateTime.of(2026, 11, 4, 12, 0, 0, 0,
                        ZoneId.of("Europe/Istanbul")));
    }

    private static String exportText(AcademicStudyDocument document, Path path) throws Exception {
        new DocxStudyReportExporter().export(document, path);
        try (XWPFDocument exported = new XWPFDocument(Files.newInputStream(path))) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : exported.getParagraphs()) {
                text.append(paragraph.getText()).append('\n');
            }
            for (XWPFTable table : exported.getTables()) {
                table.getRows().forEach(row -> row.getTableCells().forEach(cell ->
                        text.append(cell.getText()).append('\n')));
            }
            return text.toString();
        }
    }

    private static void assertContainsExactly(String text, List<String> included,
                                              List<String> excluded, String message) {
        for (String name : included) {
            check(text.contains(name), message + ": missing " + name);
        }
        for (String name : excluded) {
            check(!text.contains(name), message + ": stale " + name);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record FrozenReport(List<String> names, AcademicStudyDocument document) { }
}
