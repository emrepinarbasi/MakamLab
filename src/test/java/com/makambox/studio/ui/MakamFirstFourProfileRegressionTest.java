package com.makambox.studio.ui;

import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.i18n.LocalizationService;
import com.makambox.studio.report.docx.AcademicStudyDocument;
import com.makambox.studio.report.docx.DocxStudyReportExporter;
import com.makambox.studio.report.docx.ReportLocalization;
import com.makambox.studio.study.MakamProfileComparison;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** Regression coverage for the academically qualified first-four-degree report section. */
public final class MakamFirstFourProfileRegressionTest {
    public static void main(String[] args) throws Exception {
        LocalizationService.getInstance().setLanguageTag("tr-TR");
        verifySelectedMakamExpectedReference();
        verifyCanonicalFullProfileGroupsCollapse();
        verifyReportUsesAnalysisScoreWithoutReassignment();
        MakamProfileComparison sufficient = comparison(true, "Kanıt yeterli.");
        List<AcademicStudyDocument.ProfileComparison> rows = MakamDocxReportHandler.profileRows(sufficient);

        check(rows.size() == 10, "DOCX adaptation must retain every profile candidate");
        AcademicStudyDocument.ProfileComparison first = rows.get(0);
        AcademicStudyDocument.ProfileComparison ninth = rows.get(8);
        check(first.firstFourIndistinguishableProfiles().contains("Birinci")
                        && first.firstFourIndistinguishableProfiles().contains("Dokuzuncu"),
                "profiles sharing the same first four theoretical degrees must form one explicit group");
        check(ninth.firstFourIndistinguishableProfiles().contains("Birinci"),
                "first-four grouping must include candidates below the general top-eight display limit");

        AcademicStudyDocument.DegreeComparison tonic = first.firstFourDegrees().get(0);
        AcademicStudyDocument.DegreeComparison missing = first.firstFourDegrees().get(2);
        check(tonic.observed() && tonic.observedCent() == 0.0 && tonic.residualCent() == 0.0,
                "degree 1 must be the fixed tonic anchor, not a redetected cluster");
        check(tonic.eventCount() == 0 && tonic.totalDurationSeconds() == 0.0,
                "the tonic anchor must not claim independently observed event evidence");
        check(!missing.observed() && missing.eventCount() == 0 && missing.totalDurationSeconds() == 0.0,
                "a missing degree must not inherit evidence from the nearest unrelated cluster");

        double expected = sufficient.candidates().get(0).scorePercent();
        check(Math.abs(first.firstFourScorePercent() - expected) < 1.0e-8,
                "the report must preserve the analysis score instead of running a second assignment algorithm");
        check(first.firstFourObservedCount() == 3,
                "evidence count must contain one tonic anchor and the two observed discriminating degrees");

        Path directory = Files.createTempDirectory("makam-first-four-report");
        Path sufficientDocx = directory.resolve("sufficient.docx");
        new DocxStudyReportExporter().export(document(rows), sufficientDocx);
        String sufficientText = documentText(sufficientDocx);
        check(sufficientText.contains("En yakın ilk-dört-ses grubu: Birinci, Dokuzuncu"),
                "DOCX must label first-four-identical profiles as an indistinguishable group");
        check(sufficientText.contains("Kullanıcı onaylı karar ankrajı")
                        && sufficientText.contains("Skora katılmaz"),
                "DOCX must explain the confirmed tonic anchor and exclude it from scoring");
        check(sufficientText.contains("2.-4. derece benzerliği"),
                "DOCX must state that the score is based on discriminating degrees 2-4");
        check(sufficientText.contains("Benzerlik"),
                "first-four table must include the threshold-based similarity classification");
        check(sufficientText.contains("Genel perde envanteri — baskın ve ikincil tepeler")
                        && sufficientText.contains("Baskın tepe")
                        && sufficientText.contains("İkincil tepe"),
                "DOCX must report dominant and secondary global pitch peaks separately");
        check(sufficientText.contains("Envanter tepesi")
                        && sufficientText.contains("Baskın envanter tepesi")
                        && sufficientText.contains("İkincil envanter tepesi")
                        && sufficientText.contains("+4,930 Hc")
                        && sufficientText.contains("+9,030 Hc"),
                "candidate residual audit must use the same dominant and secondary global inventory peaks as ranking");
        check(sufficientText.contains("Sıralı cümle tepeleri ayrıca raporlanır")
                        && !sufficientText.contains("Baskın yol tepesi\n+90,00 C"),
                "residual audit must keep ordered phrase peaks separate from inventory residuals");
        check(!sufficientText.contains("Çıkıcı kanıt — Karardan başlayan ilk dört sesin")
                        && !sufficientText.contains("İnici kanıt — Karardan başlayan ilk dört sesin"),
                "removed directional first-four report sections must stay absent");

        String warning = "Kısmi/karma dörtlü kanıtı: deneme kanıtı.";
        List<AcademicStudyDocument.ProfileComparison> insufficientRows =
                MakamDocxReportHandler.profileRows(comparison(false, warning));
        check(insufficientRows.stream().noneMatch(AcademicStudyDocument.ProfileComparison::evidenceSufficient),
                "comparison evidence sufficiency must survive document adaptation");
        Path insufficientDocx = directory.resolve("insufficient.docx");
        new DocxStudyReportExporter().export(document(insufficientRows,
                partialMixedEvidence()), insufficientDocx);
        String insufficientText = documentText(insufficientDocx);
        check(insufficientText.contains("Kısmi/karma kanıt"),
                "DOCX must show the partial/mixed evidence warning in the selected report language");
        check(insufficientText.contains("2.-4. derece benzerliği")
                        && insufficientText.contains("envanter benzerliği sıralaması"),
                "DOCX must retain the ranked inventory-similarity graph and table for partial/mixed phrase evidence");
        check(!insufficientText.contains("Çıkıcı kanıt — Karardan başlayan ilk dört sesin")
                        && !insufficientText.contains("İnici kanıt — Karardan başlayan ilk dört sesin"),
                "partial evidence must not reintroduce directional first-four sections");

        Path noPathDocx = directory.resolve("no-ordered-path.docx");
        new DocxStudyReportExporter().export(document(insufficientRows,
                new AcademicStudyDocument.FirstFourPhraseEvidence(
                        "INSUFFICIENT", 0, 0, 0, 1, List.of())), noPathDocx);
        String noPathText = documentText(noPathDocx);
        check(noPathText.contains("Yetersiz sıralı dörtlü kanıt")
                        && noPathText.contains("2.-4. derece benzerliği")
                        && noPathText.contains("genel ilk-dört-ses perde envanterini kullanır"),
                "DOCX must keep the ranked global-inventory graph when no ordered phrase path qualifies");

        System.out.println("MAKAM_FIRST_FOUR_PROFILE_OK " + directory);
    }

    private static void verifySelectedMakamExpectedReference() {
        double comma = AnalysisConfig.COMMA_CENT;
        MakamProfileComparison.Candidate selected = fullProfileCandidate(1,
                "kurdilihicazkar", List.of("ferahfeza", "kurdi", "kurdilihicazkar"),
                88.7, 4.0, 13.0);
        MakamProfileComparison comparison = new MakamProfileComparison(
                "Arel_Ezgi_Uzdilek", "Kurdilihicazkar", true, "Kanıt yeterli.",
                20, 4, 8.0, 4, List.of(), List.of(selected));
        double tonicHz = 123.8805;
        List<AcademicStudyDocument.TheoryPitch> centers = List.of(
                theoryPitch("Rast", 0.0, tonicHz),
                theoryPitch("Nim Zirgüle", 4.0, tonicHz),
                theoryPitch("Zirgüle", 5.0, tonicHz),
                theoryPitch("Dik Zirgüle", 8.0, tonicHz),
                theoryPitch("Dügâh", 9.0, tonicHz));
        List<AcademicStudyDocument.TheoryPitch> references =
                MakamDocxReportHandler.expectedReferencePitches(
                        comparison, 0.0, 9.0 * comma, centers, tonicHz);
        check(references.size() == 1, "Rast-Dügâh open interval must contain one selected-profile degree");
        AcademicStudyDocument.TheoryPitch reference = references.get(0);
        check(reference.noteName().equals("Nim Zirgüle")
                        && Math.abs(reference.relativeHc() - 4.0) < 1.0e-9,
                "Kürdilihicazkâr's selected-profile reference in Rast-Dügâh must be named Nim Zirgüle at +4 Hc");
    }

    private static AcademicStudyDocument.TheoryPitch theoryPitch(
            String name, double hc, double tonicHz) {
        double cent = hc * AnalysisConfig.COMMA_CENT;
        return new AcademicStudyDocument.TheoryPitch(name, cent, hc,
                tonicHz * Math.pow(2.0, cent / 1200.0));
    }

    private static void verifyCanonicalFullProfileGroupsCollapse() throws Exception {
        double comma = AnalysisConfig.COMMA_CENT;
        List<MakamProfileComparison.Candidate> candidates = List.of(
                fullProfileCandidate(1, "kurdilihicazkar",
                        List.of("ferahfeza", "kurdi", "kurdilihicazkar"), 88.7, 4.0, 13.0),
                fullProfileCandidate(2, "acem",
                        List.of("acem", "beyati", "isfahan", "ussak"), 82.6, 8.0, 13.0),
                fullProfileCandidate(3, "nuhuft", List.of("nuhuft"), 80.9, 8.0, 13.0),
                fullProfileCandidate(4, "buselik", List.of("buselik", "nihavend"),
                        80.5, 9.0, 13.0),
                fullProfileCandidate(5, "gulizar",
                        List.of("gulizar", "huseyni", "muhayyer", "neva", "tahir", "tahirbuselik"),
                        75.7, 8.0, 13.0),
                fullProfileCandidate(6, "segah", List.of("segah"), 74.2, 5.0, 14.0),
                fullProfileCandidate(7, "hicaz", List.of("hicaz"), 68.5, 5.0, 17.0));
        MakamProfileComparison comparison = new MakamProfileComparison(
                "Arel_Ezgi_Uzdilek", "Kurdilihicazkar", true, "Kanıt yeterli.",
                76, 9, 14.0, 5,
                List.of(new MakamProfileComparison.ObservedCluster(4.0 * comma, 4.0,
                        8, 1.5, 2.0, .9)), candidates);

        List<AcademicStudyDocument.ProfileComparison> rows =
                MakamDocxReportHandler.profileRows(comparison);
        check(rows.size() == 5,
                "canonical Uşşak/Kürdî/Bûselik/Segâh families must occupy one row each");
        check(rows.stream().map(AcademicStudyDocument.ProfileComparison::profileName).toList()
                        .equals(List.of("Kürdî", "Uşşak", "Bûselik", "Segâh", "hicaz")),
                "full-profile ranking must use canonical group labels and preserve unknown profiles");
        AcademicStudyDocument.ProfileComparison ussak = rows.get(1);
        check(Math.abs(ussak.scorePercent() - 82.6) < 1.0e-9,
                "a canonical group must retain its best-scoring full-profile metrics");
        check(ussak.verdict().contains("acem") && ussak.verdict().contains("nuhuft")
                        && ussak.verdict().contains("gulizar"),
                "collapsed family evidence must retain every contributing profile name");
        check(rows.stream().map(AcademicStudyDocument.ProfileComparison::rank).toList()
                        .equals(List.of(1, 2, 3, 4, 5)),
                "collapsed rows must be re-ranked without gaps");

        Path directory = Files.createTempDirectory("makam-canonical-full-profile");
        Path report = directory.resolve("canonical-groups.docx");
        new DocxStudyReportExporter().export(document(rows), report);
        String text = documentText(report);
        check(text.contains("Dizi / çeşni grubu")
                        && text.contains("En yakın ilk-dört-ses grubu: Kürdî")
                        && text.contains("Kürdî grubu içinde birlikte değerlendirilen makam profilleri")
                        && text.contains("kurdilihicazkar"),
                "DOCX must present the first-four ranking as canonical groups and explain its members");
        try (XWPFDocument reopened = new XWPFDocument(Files.newInputStream(report))) {
            String firstFourTable = tableText(reopened, "2.-4. derece benzerliği");
            check(!firstFourTable.contains("en iyi eşleşen temsilci"),
                    "a removed complete-profile representative verdict must not leak into the first-four table");
            check(firstFourTable.contains("Benzerlik") || firstFourTable.contains("Az benzerlik"),
                    "the first-four table must use only its own similarity classification");
            check(reopened.getTables().stream().noneMatch(table -> table.getRows().stream()
                            .flatMap(row -> row.getTableCells().stream())
                            .anyMatch(cell -> cell.getText().strip().equals("Med. mutlak hata"))),
                    "the misleading whole-profile ranking table must no longer be exported");
        }
    }

    private static MakamProfileComparison.Candidate fullProfileCandidate(
            int rank, String name, List<String> equivalentNames, double score,
            double secondHc, double thirdHc) {
        double comma = AnalysisConfig.COMMA_CENT;
        List<MakamProfileComparison.DegreeMatch> degrees = List.of(
                new MakamProfileComparison.DegreeMatch(1, 0.0, 0.0, true,
                        0.0, 0.0, 0.0, 0.0, 0, 0.0),
                new MakamProfileComparison.DegreeMatch(2, secondHc * comma, secondHc, true,
                        secondHc * comma, secondHc, 0.0, 0.0, 5, 1.0),
                new MakamProfileComparison.DegreeMatch(3, thirdHc * comma, thirdHc, true,
                        thirdHc * comma, thirdHc, 0.0, 0.0, 5, 1.0),
                new MakamProfileComparison.DegreeMatch(4, 22.0 * comma, 22.0, true,
                        22.0 * comma, 22.0, 0.0, 0.0, 5, 1.0));
        return new MakamProfileComparison.Candidate(rank, name, equivalentNames,
                name.equals("kurdilihicazkar"), score, 4.0, 2.76, 5.38,
                94.2, 100.0, 4, 8, degrees);
    }

    private static void verifyReportUsesAnalysisScoreWithoutReassignment() {
        double comma = AnalysisConfig.COMMA_CENT;
        List<MakamProfileComparison.ObservedCluster> observations = List.of(
                new MakamProfileComparison.ObservedCluster(4.0 * comma, 4.0,
                        8, 1.80, 3.0, .92),
                // This is intentionally an in-between, unnamed cluster.  It must
                // still be evidence instead of disappearing from the first-four score.
                new MakamProfileComparison.ObservedCluster(6.4 * comma, 6.4,
                        4, .80, 1.0, .84),
                new MakamProfileComparison.ObservedCluster(8.0 * comma, 8.0,
                        1, .14, .2, .80),
                new MakamProfileComparison.ObservedCluster(13.0 * comma, 13.0,
                        9, 1.70, 2.0, .91),
                new MakamProfileComparison.ObservedCluster(22.0 * comma, 22.0,
                        14, 4.10, 3.0, .94));
        MakamProfileComparison.Candidate ussak = profileCandidate(1, "Ussak", 8.0,
                8.0, 1, .14, false);
        MakamProfileComparison.Candidate kurdi = profileCandidate(2, "Kurdilihicazkar", 4.0,
                4.0, 8, 1.80, true);
        MakamProfileComparison comparison = new MakamProfileComparison(
                "Arel_Ezgi_Uzdilek", "Kurdilihicazkar", true, "Kanıt yeterli.",
                36, observations.size(), 8.54, 5, observations, List.of(ussak, kurdi));

        List<AcademicStudyDocument.ProfileComparison> rows =
                MakamDocxReportHandler.profileRows(comparison);
        double ussakScore = rows.stream().filter(value -> value.profileName().equals("Uşşak"))
                .findFirst().orElseThrow().firstFourScorePercent();
        double kurdiScore = rows.stream().filter(value -> value.profileName().equals("Kürdî"))
                .findFirst().orElseThrow().firstFourScorePercent();
        check(Math.abs(ussakScore - 90.0) < 1.0e-9
                        && Math.abs(kurdiScore - 90.0) < 1.0e-9,
                "report adaptation must not recompute profile scores from raw clusters");
    }

    private static MakamProfileComparison.Candidate profileCandidate(
            int rank, String name, double secondHc, double observedSecondHc,
            int secondCount, double secondDuration, boolean selected) {
        double comma = AnalysisConfig.COMMA_CENT;
        List<MakamProfileComparison.DegreeMatch> degrees = List.of(
                new MakamProfileComparison.DegreeMatch(1, 0.0, 0.0, true,
                        0.0, 0.0, 0.0, 0.0, 0, 0.0),
                new MakamProfileComparison.DegreeMatch(2, secondHc * comma, secondHc, true,
                        observedSecondHc * comma, observedSecondHc, 0.0, 0.0,
                        secondCount, secondDuration),
                new MakamProfileComparison.DegreeMatch(3, 13.0 * comma, 13.0, true,
                        13.0 * comma, 13.0, 0.0, 0.0, 9, 1.70),
                new MakamProfileComparison.DegreeMatch(4, 22.0 * comma, 22.0, true,
                        22.0 * comma, 22.0, 0.0, 0.0, 14, 4.10));
        return new MakamProfileComparison.Candidate(rank, name, List.of(name), selected,
                90.0, 5.0, 4.0, 10.0, 88.0, 75.0, 4, 8, degrees);
    }

    private static MakamProfileComparison comparison(boolean sufficient, String summary) {
        List<MakamProfileComparison.Candidate> candidates = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            String name = switch (index) {
                case 1 -> "Birinci";
                case 9 -> "Dokuzuncu";
                case 10 -> "Onuncu";
                default -> "Profil " + index;
            };
            List<MakamProfileComparison.DegreeMatch> degrees;
            if (index == 10) {
                degrees = List.of();
            } else if (index == 1 || index == 9) {
                degrees = degrees(90.0, 200.0, 300.0);
            } else {
                degrees = degrees(90.0 + index, 200.0 + index, 300.0 + index);
            }
            candidates.add(new MakamProfileComparison.Candidate(index, name, List.of(name),
                    index == 1, 100.0 - index, 4.0 + index, 3.0 + index,
                    8.0 + index, 90.0, 75.0, 3, Math.max(4, degrees.size()), degrees));
        }
        return new MakamProfileComparison("Arel_Ezgi_Uzdilek", "Birinci", sufficient, summary,
                22, 5, 9.5, 4, List.of(), candidates);
    }

    private static List<MakamProfileComparison.DegreeMatch> degrees(double second, double third,
                                                                     double fourth) {
        double comma = AnalysisConfig.COMMA_CENT;
        return List.of(
                new MakamProfileComparison.DegreeMatch(1, 0.0, 0.0, false,
                        640.0, 640.0 / comma, 640.0, 640.0 / comma, 99, 8.0),
                new MakamProfileComparison.DegreeMatch(2, second, second / comma, true,
                        second, second / comma, 0.0, 0.0, 3, 1.2),
                new MakamProfileComparison.DegreeMatch(3, third, third / comma, false,
                        third + 60.0, (third + 60.0) / comma, 60.0, 60.0 / comma, 99, 9.0),
                new MakamProfileComparison.DegreeMatch(4, fourth, fourth / comma, true,
                        fourth + comma, fourth / comma + 1.0, comma, 1.0, 2, 0.8));
    }

    private static AcademicStudyDocument document(List<AcademicStudyDocument.ProfileComparison> profiles) {
        return document(profiles, completeEvidence());
    }

    private static AcademicStudyDocument document(
            List<AcademicStudyDocument.ProfileComparison> profiles,
            AcademicStudyDocument.FirstFourPhraseEvidence phraseEvidence) {
        List<AcademicStudyDocument.FirstFourPeak> inventory = List.of(
                new AcademicStudyDocument.FirstFourPeak(2,
                        4.93 * AnalysisConfig.COMMA_CENT, 4.93,
                        4, .88, 1.8, .92, true),
                new AcademicStudyDocument.FirstFourPeak(2,
                        9.03 * AnalysisConfig.COMMA_CENT, 9.03,
                        3, .66, 1.2, .90, false),
                new AcademicStudyDocument.FirstFourPeak(3,
                        13.0 * AnalysisConfig.COMMA_CENT, 13.0,
                        5, 1.1, 2.0, .94, true),
                new AcademicStudyDocument.FirstFourPeak(4,
                        22.0 * AnalysisConfig.COMMA_CENT, 22.0,
                        5, 1.1, 2.0, .94, true));
        return new AcademicStudyDocument("MakamLab — Tek Perde Akademik İcra Analizi",
                new AcademicStudyDocument.Source("test.wav", "/test.wav", "abc", 12.0, 22_050,
                        "Mono", "Makam test", "Test motoru"),
                new AcademicStudyDocument.Theory("Birinci", "Rast", "Nim Zirgüle",
                        220.0, 90.566, 4.0, 231.84, "Arel-Ezgi-Uzdilek / 53-Hc"),
                new AcademicStudyDocument.TonicEvidence("Kullanıcı seçimi", 220.0,
                        10.0, 11.0, 0.95, 2.0, 25, true, "Onaylandı"),
                List.of(new AcademicStudyDocument.Event(1, 1.0, 1.5, 231.84,
                        90.566, 90.566, 0.0, 0.0, 0.0, 0.0, 2.0, 0.95,
                        "Yatay/Karma", "Uzatma", true, true, true, "")),
                List.of(new AcademicStudyDocument.TracePoint(10.2, 220.0, 0.95, true)),
                profiles, List.of("Regresyon yöntemi"), List.of("Regresyon sınırlılığı"),
                ZonedDateTime.of(2026, 10, 30, 12, 0, 0, 0, ZoneId.of("Europe/Istanbul")),
                List.of(), List.of(), inventory, phraseEvidence);
    }

    private static AcademicStudyDocument.FirstFourPhraseEvidence completeEvidence() {
        return new AcademicStudyDocument.FirstFourPhraseEvidence(
                "COMPLETE", 2, 1, 1, 12,
                List.of(
                        new AcademicStudyDocument.FirstFourPeak(2,
                                8.95 * AnalysisConfig.COMMA_CENT, 8.95,
                                4, .8, 1.0, .93, true),
                        new AcademicStudyDocument.FirstFourPeak(3,
                                13.0 * AnalysisConfig.COMMA_CENT, 13.0,
                                4, .8, 1.0, .93, true),
                        new AcademicStudyDocument.FirstFourPeak(4,
                                22.0 * AnalysisConfig.COMMA_CENT, 22.0,
                                4, .8, 1.0, .93, true)));
    }

    private static AcademicStudyDocument.FirstFourPhraseEvidence partialMixedEvidence() {
        return new AcademicStudyDocument.FirstFourPhraseEvidence(
                "PARTIAL_MIXED", 0, 0, 1, 2,
                List.of(
                        new AcademicStudyDocument.FirstFourPeak(2,
                                8.95 * AnalysisConfig.COMMA_CENT, 8.95,
                                1, .12, 1.0, .93, true),
                        new AcademicStudyDocument.FirstFourPeak(3,
                                13.0 * AnalysisConfig.COMMA_CENT, 13.0,
                                1, .12, 1.0, .93, true)));
    }

    private static String documentText(Path path) throws Exception {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(path))) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append('\n');
            }
            for (XWPFTable table : document.getTables()) {
                table.getRows().forEach(row -> row.getTableCells().forEach(cell ->
                        text.append(cell.getText()).append('\n')));
            }
            return text.toString();
        }
    }

    private static String tableText(XWPFDocument document, String requiredHeader) {
        for (XWPFTable table : document.getTables()) {
            if (table.getRows().isEmpty()) continue;
            boolean headerMatches = table.getRow(0).getTableCells().stream()
                    .map(cell -> cell.getText().strip())
                    .anyMatch(requiredHeader::equals);
            if (!headerMatches) continue;
            StringBuilder text = new StringBuilder();
            table.getRows().forEach(row -> row.getTableCells().forEach(cell ->
                    text.append(cell.getText()).append('\n')));
            return text.toString();
        }
        throw new AssertionError("DOCX table is missing header: " + requiredHeader);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
