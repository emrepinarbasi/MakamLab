package com.makambox.studio.report.docx;

import com.makambox.studio.i18n.LocalizationService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;

public final class DocxStudyReportRegressionTest {
    public static void main(String[] args) throws Exception {
        LocalizationService.getInstance().setLanguageTag("tr-TR");
        Path output = args.length > 0 ? Path.of(args[0]) : Files.createTempDirectory("makam-docx-test");
        Files.createDirectories(output);
        Path reportPath = output.resolve("Makam-Kurdilihicazkar-Nim-Zirgule.docx");
        AcademicStudyDocument report = sampleReport();
        var distributionPanel = new AcademicStudyCharts().distributionAndDirections(report);
        if (distributionPanel.getWidth() != 1600 || distributionPanel.getHeight() != 820) {
            throw new AssertionError("Birleşik dağılım/yön panelinin boyutu beklenenden farklı");
        }
        new DocxStudyReportExporter().export(report, reportPath);

        if (!Files.exists(reportPath) || Files.size(reportPath) < 80_000) {
            throw new AssertionError("DOCX beklenenden küçük veya oluşturulmadı: " + reportPath);
        }
        try (ZipFile zip = new ZipFile(reportPath.toFile())) {
            for (String required : List.of("[Content_Types].xml", "word/document.xml",
                    "word/styles.xml", "word/_rels/document.xml.rels")) {
                if (zip.getEntry(required) == null) throw new AssertionError("Eksik DOCX parçası: " + required);
            }
            long media = zip.stream().filter(entry -> entry.getName().startsWith("word/media/")).count();
            if (media < 8) throw new AssertionError("Bütün grafikler DOCX'e gömülmedi: " + media);
            String documentXml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(),
                    StandardCharsets.UTF_8);
            int combinedPanelOccurrences = occurrences(documentXml, "dagilim-ve-yon.png");
            // OOXML records the source name once in wp:docPr and once in
            // pic:cNvPr for each drawing.  Two hits therefore mean one panel;
            // four hits would reveal the accidental duplicate insertion that
            // this regression protects against.
            if (combinedPanelOccurrences != 2) {
                throw new AssertionError("Birleşik dağılım/yön paneli DOCX'te tam bir kez bulunmalı: "
                        + combinedPanelOccurrences);
            }
            for (String chart : List.of("perde-ve-ara-ses-tepeleri.png",
                    "perde-merkezleri-ve-kuramsal-uzakliklar.png",
                    "ilk-dort-ses-envanter-benzerligi.png",
                    "ilk-dort-ses-sirali-cumle-benzerligi.png")) {
                if (occurrences(documentXml, chart) != 2) {
                    throw new AssertionError("Yeni akademik rapor grafiği bulunmalı: " + chart);
                }
            }
            for (String chart : List.of("ilk-dort-ses-cikici-benzerligi.png",
                    "ilk-dort-ses-inici-benzerligi.png")) {
                if (occurrences(documentXml, chart) != 0) {
                    throw new AssertionError("Yönsel dörtlü grafiği rapordan kaldırılmalı: " + chart);
                }
            }
            if (occurrences(documentXml, "karara-hizali-profil-benzerligi.png") != 0) {
                throw new AssertionError("İlk dört ses dışındaki yanıltıcı tam-profil grafiği kaldırılmalı");
            }
        }

        try (XWPFDocument reopened = new XWPFDocument(Files.newInputStream(reportPath))) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : reopened.getParagraphs()) text.append(paragraph.getText()).append('\n');
            for (XWPFTable table : reopened.getTables()) table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> text.append(cell.getText()).append('\n')));
            for (String required : List.of("Kürdilihicazkâr", "Nim Zirgüle", "Karar perdesinin belirlenmesi",
                    "Kuramsal hedef ve sınıflandırma", "1 Hc = 1200/53 = 22,641509 C",
                    "Terimleri nasıl okuyacaksınız?", "Holder koması (Hc)",
                    "Süre ağırlıklı ortalama", "Kutu grafik",
                    "f(Nim Zirgüle) = 123,880500 x 2^(90,566100/1200) = 130,533600 Hz",
                    "6. Karardan başlayan ilk dört sesin dizi / çeşni benzerliği",
                    "Dizi / çeşni grubu",
                    "En yakın ilk-dört-ses grubu: Kürdî",
                    "Kürdî grubu içinde birlikte değerlendirilen makam profilleri",
                    "Akademik yorum: Rapor kapsamındaki",
                    "Kuramsal merkezin ±1 Hc",
                    "tek başına kesin bir makam kuralı veya makam teşhisi",
                    "Bütün kanonik hedef-perde temasları")) {
                if (!text.toString().contains(required)) throw new AssertionError("DOCX metni eksik: " + required);
            }
            for (String countEvidence : List.of(
                    "176 / 29 / 22",
                    "Olay sayısı — Hepsi: 176 • Ölçülebilir olaylar: 29 • Kararlı alt küme: 22.")) {
                if (!text.toString().contains(countEvidence)) {
                    throw new AssertionError("Temas/ölçülebilir/kararlı olay katmanları eksik: "
                            + countEvidence);
                }
            }
            if (text.toString().contains("{2}")) {
                throw new AssertionError("DOCX metninde çözümlenmemiş MessageFormat yer tutucusu kaldı");
            }
            if (!text.toString().contains("%89,2")) {
                throw new AssertionError("İlk-dört-ses benzerlik yüzdesi rapora yazılmadı");
            }
            if (reopened.getTables().size() < 7) throw new AssertionError("Rapor tabloları eksik");
            assertTableHeaders(reopened, List.of("Sıra", "Dizi / çeşni grubu",
                    "2.-4. derece benzerliği", "Kanıt", "Sonuç"),
                    "İlk dört ses karşılaştırması");
            assertTableHeaders(reopened, List.of("Derece", "Kuramsal", "Gözlenen", "Sapma", "Kanıt"),
                    "İlk dört derece kanıtı");
            if (text.toString().contains("Çıkıcı kanıt — Karardan başlayan ilk dört sesin")
                    || text.toString().contains("İnici kanıt — Karardan başlayan ilk dört sesin")) {
                throw new AssertionError("Yönsel dörtlü karşılaştırması rapordan kaldırılmalı");
            }
            XWPFTable directionTable = reopened.getTables().stream()
                    .filter(table -> !table.getRows().isEmpty()
                            && "Net yön".equals(table.getRow(0).getCell(0).getText()))
                    .findFirst().orElseThrow(() -> new AssertionError("Yön grupları tablosu eksik"));
            List<String> directionOrder = directionTable.getRows().stream().skip(1)
                    .map(row -> row.getCell(0).getText()).toList();
            if (!directionOrder.equals(List.of("Çıkıcı", "İnici", "Yatay/Karma"))) {
                throw new AssertionError("Yön sırası grafikle tutarsız: " + directionOrder);
            }
            XWPFTable summaryTable = reopened.getTables().stream()
                    .filter(table -> !table.getRows().isEmpty()
                            && "Gösterge".equals(table.getRow(0).getCell(0).getText()))
                    .findFirst().orElseThrow(() -> new AssertionError("Sayısal sonuç özeti eksik"));
            var extremeSummaryRow = summaryTable.getRows().stream()
                    .filter(row -> "En pest / en dik".equals(row.getCell(0).getText()))
                    .findFirst().orElseThrow(() -> new AssertionError(
                            "Genel Perde Analizi özetinde en pest/en dik satırı eksik"));
            assertCentAndHcRange(extremeSummaryRow.getCell(1).getText(),
                    "ölçülebilir olay uçları");
            assertCentAndHcRange(extremeSummaryRow.getCell(2).getText(),
                    "kararlı olay uçları");
            if (!"En pest / en dik".equals(directionTable.getRow(0).getCell(5).getText())) {
                throw new AssertionError("Yön grupları en pest/en dik sütunu eksik");
            }
            for (int rowIndex = 1; rowIndex < directionTable.getRows().size(); rowIndex++) {
                assertCentAndHcRange(directionTable.getRow(rowIndex).getCell(5).getText(),
                        "yön grubu uçları");
            }
        }
        verifyOpenIntervalReport(output, report);
        verifyDirectionalLayerAfterInsufficientFirstFour(output, report);
        verifyDirectionalSegahReport(output, report);
        System.out.println("DOCX_STUDY_REPORT_OK " + reportPath + " bytes=" + Files.size(reportPath));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private static void assertCentAndHcRange(String text, String label) {
        if (!text.contains(" C / ") || !text.contains(" Hc") || !text.contains("↔")) {
            throw new AssertionError(label + " hem C hem Hc aralığı içermiyor: " + text);
        }
    }

    private static void assertTableHeaders(XWPFDocument document, List<String> expected,
                                           String label) {
        boolean found = document.getTables().stream().anyMatch(table -> {
            if (table.getRows().isEmpty()) return false;
            List<String> actual = table.getRow(0).getTableCells().stream()
                    .map(cell -> cell.getText().strip()).toList();
            return actual.equals(expected);
        });
        if (!found) throw new AssertionError(label + " tablo başlıkları eksik veya değişmiş: " + expected);
    }

    private static void verifyOpenIntervalReport(Path output, AcademicStudyDocument base) throws Exception {
        Path path = output.resolve("Makam-Kurdilihicazkar-Rast-Dugah-Arasi.docx");
        AcademicStudyDocument.Theory theory = new AcademicStudyDocument.Theory(
                "Kürdilihicazkâr", "Rast", "Rast–Dügâh arası (sınırlar hariç)",
                123.8805, 101.887, 4.5, 131.39, "Arel-Ezgi-Uzdilek / 53-Hc",
                "Perdeler arası analiz", "Rast", 0.0, 0.0, 123.8805,
                "Dügâh", 203.774, 9.0, 139.30, false, sampleTheoryPitches(),
                List.of(theoryPitch("Nim Zirgüle", 4.0)));
        AcademicStudyDocument interval = new AcademicStudyDocument(
                "MakamLab — Perdeler Arası Analiz", base.source(), theory,
                base.tonic(), base.contacts(), base.tonicTrace(), base.profileComparisons(),
                List.of("Alt ve üst sınırlar araştırma dışıdır."), base.limitations(), base.generatedAt(),
                List.of(), base.directionalFirstFourProfiles());
        new DocxStudyReportExporter().export(interval, path);
        try (XWPFDocument reopened = new XWPFDocument(Files.newInputStream(path))) {
            StringBuilder text = new StringBuilder();
            reopened.getParagraphs().forEach(value -> text.append(value.getText()).append('\n'));
            reopened.getTables().forEach(table -> table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> text.append(cell.getText()).append('\n'))));
            for (String required : List.of("Kuramsal inceleme aralığı ve sınıflandırma",
                    "Alt sınır (araştırma dışı)", "Üst sınır (araştırma dışı)",
                    "Rast < F0 < Dügâh; iki sınır da araştırma dışı",
                    "Makamın aralık içindeki beklenen referansı",
                    "Nim Zirgüle = +90,5660 C / +4,0000 Hc",
                    "adlandırılamayan gözlem",
                    "Aralık içindeki perdelerin ortalama icra değerleri",
                    "Genel ortalama (tüm olaylar)", "Ort. Hz",
                    "Ort. gözlenen konum", "Ortalama sapma", "Medyan sapma")) {
                if (!text.toString().contains(required)) {
                    throw new AssertionError("Perdeler arası DOCX metni eksik: " + required);
                }
            }
            for (String countEvidence : List.of(
                    "176 / 29 / 22",
                    "Olay sayısı — Seçilen açık aralık: 176 • Ölçülebilir olaylar: 29 • Kararlı alt küme: 22.")) {
                if (!text.toString().contains(countEvidence)) {
                    throw new AssertionError("Açık aralık olay katmanları eksik: " + countEvidence);
                }
            }
        }
    }

    private static void verifyDirectionalLayerAfterInsufficientFirstFour(
            Path output, AcademicStudyDocument base) throws Exception {
        Path path = output.resolve("Makam-Yetersiz-Genel-Ilk-Dort-Yonsel.docx");
        AcademicStudyDocument.ProfileComparison source = base.profileComparisons().get(0);
        AcademicStudyDocument.ProfileComparison insufficient =
                new AcademicStudyDocument.ProfileComparison(source.rank(), source.profileName(),
                        source.scorePercent(), source.medianAbsoluteErrorCent(),
                        source.p90AbsoluteErrorCent(), source.withinOneHcPercent(),
                        source.degreeCoveragePercent(), source.evidenceEventCount(),
                        source.verdict(), source.indistinguishable(),
                        source.firstFourScorePercent(), source.firstFourObservedCount(),
                        source.firstFourDegrees(), false,
                        "İlk dört dereceyi karşılaştırmak için yeterli kararlı aralık kanıtı bulunmadı.",
                        source.firstFourSignature(), source.firstFourIndistinguishableProfiles());
        AcademicStudyDocument report = new AcademicStudyDocument(
                base.reportTitle(), base.source(), base.theory(), base.tonic(), base.contacts(),
                base.tonicTrace(), List.of(insufficient), base.methodNotes(), base.limitations(),
                base.generatedAt(), List.of(), base.directionalFirstFourProfiles());
        new DocxStudyReportExporter().export(report, path);
        try (XWPFDocument reopened = new XWPFDocument(Files.newInputStream(path))) {
            StringBuilder text = new StringBuilder();
            reopened.getParagraphs().forEach(value -> text.append(value.getText()).append('\n'));
            reopened.getTables().forEach(table -> table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> text.append(cell.getText()).append('\n'))));
            if (text.toString().contains("Çıkıcı kanıt — Karardan başlayan ilk dört sesin")
                    || text.toString().contains("İnici kanıt — Karardan başlayan ilk dört sesin")) {
                throw new AssertionError("Yetersiz ana kanıtta da yönsel dörtlü katmanı görünmemeli");
            }
        }
    }

    private static void verifyDirectionalSegahReport(Path output,
                                                     AcademicStudyDocument base) throws Exception {
        Path path = output.resolve("Makam-Ussak-Inici-Cikici-Segah.docx");
        double hc = AcademicStudyDocument.HC_CENT;
        double tonic = 220.0;
        List<AcademicStudyDocument.TheoryPitch> pitches = List.of(
                new AcademicStudyDocument.TheoryPitch("Dügâh", 0.0, 0.0, tonic),
                new AcademicStudyDocument.TheoryPitch("Dik Kürdî", 7 * hc, 7.0,
                        tonic * Math.pow(2.0, 7 * hc / 1200.0)),
                new AcademicStudyDocument.TheoryPitch("Segâh", 8 * hc, 8.0,
                        tonic * Math.pow(2.0, 8 * hc / 1200.0)),
                new AcademicStudyDocument.TheoryPitch("Bûselik", 9 * hc, 9.0,
                        tonic * Math.pow(2.0, 9 * hc / 1200.0)));
        AcademicStudyDocument.Theory theory = new AcademicStudyDocument.Theory(
                "Uşşak", "Dügâh", "Segâh", tonic, 8 * hc, 8.0,
                tonic * Math.pow(2.0, 8 * hc / 1200.0), "Arel-Ezgi-Uzdilek / 53-Hc",
                "İnici-Çıkıcı Segâh analizi", "Dügâh", 0.0, 0.0, tonic,
                "Bûselik", 9 * hc, 9.0,
                tonic * Math.pow(2.0, 9 * hc / 1200.0), false, pitches,
                List.of(pitches.get(2)));
        List<AcademicStudyDocument.Event> events = new ArrayList<>();
        List<AcademicStudyDocument.DirectionalContext> contexts = new ArrayList<>();
        String[] directions = {"Çıkıcı", "Çıkıcı", "Çıkıcı", "İnici", "İnici",
                "Segâh kalışı", "Segâh kalışı", "Belirsiz/diğer"};
        double[] deviations = {-14, -8, 3, 7, 12, -2, 1, -28};
        for (int i = 0; i < directions.length; i++) {
            boolean included = i < 7;
            double relative = 8 * hc + deviations[i];
            String phrasePath = directions[i].equals("Çıkıcı") ? "Dügâh→Segâh→Çargâh"
                    : directions[i].equals("İnici") ? "Çargâh→Segâh→Dügâh"
                    : directions[i].equals("Segâh kalışı") ? "Segâh kalışı"
                    : "?→Segâh→?";
            events.add(new AcademicStudyDocument.Event(i + 1, "Segâh",
                    2.0 + i * 1.4, 2.5 + i * 1.4,
                    tonic * Math.pow(2.0, relative / 1200.0), relative, 8 * hc,
                    deviations[i], deviations[i] - 2, deviations[i], deviations[i] + 1,
                    4.0, included ? .86 : .51, directions[i], phrasePath, true,
                    included, included, included ? "" : "Bağlam yetersiz"));
            contexts.add(new AcademicStudyDocument.DirectionalContext(i + 1,
                    directions[i], phrasePath,
                    directions[i].equals("Çıkıcı") ? "Dügâh" : "Çargâh",
                    directions[i].equals("İnici") ? "Dügâh" : "Çargâh",
                    tonic, deviations[i] - 1.5, included ? .82 : .30));
        }
        AcademicStudyDocument directional = new AcademicStudyDocument(
                "MakamLab — İnici-Çıkıcı Segâh Perdesi Analizi",
                base.source(), theory, base.tonic(), events, base.tonicTrace(),
                base.profileComparisons(),
                List.of("Dügâh→Segâh çıkıcı; Segâh→Dügâh inici kabul edilir."),
                List.of("Bu sınıflandırma yalnız kayıtta duyulan Segâh kullanım bağlamını açıklar."),
                base.generatedAt(), contexts);
        new DocxStudyReportExporter().export(directional, path);
        try (ZipFile zip = new ZipFile(path.toFile())) {
            long media = zip.stream().filter(entry -> entry.getName().startsWith("word/media/")).count();
            if (media != 6) {
                throw new AssertionError("Yönsel raporun grafik kümesi değişmemeli: " + media);
            }
            String documentXml = new String(zip.getInputStream(zip.getEntry("word/document.xml"))
                    .readAllBytes(), StandardCharsets.UTF_8);
            for (String chart : List.of("segah-cikici-konumlari.png",
                    "segah-inici-konumlari.png", "segah-kalis-konumlari.png")) {
                if (occurrences(documentXml, chart) != 2) {
                    throw new AssertionError("Yönsel Segâh grafiği ayrı bir Word görseli olmalı: "
                            + chart);
                }
            }
            for (String forbiddenChart : List.of("ilk-dort-ses-benzerligi.png",
                    "ilk-dort-ses-envanter-benzerligi.png",
                    "ilk-dort-ses-sirali-cumle-benzerligi.png",
                    "ilk-dort-ses-cikici-benzerligi.png", "ilk-dort-ses-inici-benzerligi.png")) {
                if (occurrences(documentXml, forbiddenChart) != 0) {
                    throw new AssertionError("Yönsel Segâh raporuna ilgisiz profil grafiği sızdı: "
                            + forbiddenChart);
                }
            }
        }
        try (XWPFDocument reopened = new XWPFDocument(Files.newInputStream(path))) {
            StringBuilder text = new StringBuilder();
            reopened.getParagraphs().forEach(value -> text.append(value.getText()).append('\n'));
            reopened.getTables().forEach(table -> table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> text.append(cell.getText()).append('\n'))));
            for (String required : List.of("İnici-Çıkıcı Segâh Kullanım Analizi",
                    "Segâh referansı ve aday alanı", "Dügâh < F0 < Bûselik",
                    "Negatif sapma = Segâh referansından pest",
                    "Segâh kullanım grupları", "Çıkıcı", "İnici", "Segâh kalışı",
                    "Tümü", "Standart sapma ve MAD", "ortalamaların basit ortalaması değildir",
                    "En pest / en dik C/Hc",
                    "Çıkıcı cümlelerde Segâh konumları",
                    "İnici cümlelerde Segâh konumları",
                    "Doğrudan Segâh kalışlarının konumları",
                    "İzlenen yol", "Önceki", "Sonraki", "Yerel Dügâh'a göre C/Hc",
                    "Sınıf güveni", "Dışlandı", "6. Akademik yorum ve sınırlılıklar",
                    "Tekrarlanabilirlik", "Ek A. Bütün kanonik hedef-perde temasları")) {
                if (!text.toString().contains(required)) {
                    throw new AssertionError("Yönsel Segâh DOCX metni eksik: " + required);
                }
            }
            if (reopened.getTables().size() < 11) {
                throw new AssertionError("Yönsel raporun eski tablo kümesi eksik: "
                        + reopened.getTables().size());
            }
            assertTableHeaders(reopened, List.of("Kullanım", "n", "Ortalama C/Hc",
                    "Medyan C/Hc", "Süre ağ. ort.", "SS / MAD", "En pest / en dik C/Hc"),
                    "Segâh kullanım grupları");
            assertTableHeaders(reopened, List.of("#", "Sınıf", "İzlenen yol", "Önceki",
                    "Sonraki", "Zaman", "Süre", "Hz", "Sapma C/Hc", "Sonuç"),
                    "Yönsel olay dökümü");
            assertTableHeaders(reopened, List.of("#", "Gözlenen konum C/Hc",
                    "Genel sapma C/Hc", "Yerel Dügâh'a göre C/Hc", "F0 güveni",
                    "Sınıf güveni", "Dahil / gerekçe"), "Yönsel güven ayrıntıları");
            assertTableHeaders(reopened, List.of("#", "Perde", "Zaman", "Süre", "Hz",
                    "Konum C", "Konum Hc", "Sapma C", "Sapma Hc", "Tür", "Dahil"),
                    "Yönsel denetim eki");
            for (String forbidden : List.of("Karara hizalı dizi / çeşni grubu benzerliği",
                    "Karardan başlayan ilk dört sesin makam profili benzerliği",
                    "Ezgi yönüne göre kullanım grupları",
                    "Aralık profili benzerliği")) {
                if (text.toString().contains(forbidden)) {
                    throw new AssertionError("Yönsel Segâh DOCX'inde ilgisiz bölüm bulunuyor: "
                            + forbidden);
                }
            }
        }
    }

    static AcademicStudyDocument sampleReport() {
        List<AcademicStudyDocument.Event> events = new ArrayList<>();
        for (int i = 0; i < 176; i++) {
            boolean measurable = i % 9 == 0 || i % 17 == 0;
            boolean stable = measurable && i % 4 != 0;
            double deviation = Math.sin(i * 0.61) * 13.5 - (i % 7 == 0 ? 8.0 : 0.0);
            events.add(new AcademicStudyDocument.Event(i + 1, i * 14.2, i * 14.2 + (measurable ? .16 + i % 4 * .06 : .02),
                    130.53 * Math.pow(2.0, deviation / 1200.0), 90.566 + deviation,
                    90.566, deviation, deviation - 2.0, deviation + 1.0, deviation - 1.0,
                    stable ? 8.0 : 17.0, stable ? .87 : .68,
                    switch (i % 3) { case 0 -> "Çıkıcı"; case 1 -> "İnici"; default -> "Yatay/Karma"; },
                    measurable ? "Geçiş" : "Kısa temas", measurable, stable, true, ""));
        }
        List<AcademicStudyDocument.TracePoint> trace = new ArrayList<>();
        for (int i = 0; i < 180; i++) {
            double time = 2498 + i * .07;
            boolean accepted = time >= 2503.3 && time <= 2506.4;
            double hz = accepted ? 123.88 + Math.sin(i * .31) * .18 : (i % 7 == 0 ? 247.8 : 123.9);
            trace.add(new AcademicStudyDocument.TracePoint(time, hz, accepted ? .91 : .45, accepted));
        }
        return new AcademicStudyDocument(
                "MakamLab — Tek Perde Akademik İcra Analizi",
                new AcademicStudyDocument.Source(
                        "Cumhurbaşkanlığı Klasik Türk Müziği Korosu - Kürdilihicazkâr Faslı.m4a",
                        "/Users/example/recording.m4a", "0123456789abcdef", 2529.76, 44100,
                        "Sağ kanal", "MakamLab 1.0.0", "YIN 53-Hc 1.0"),
                new AcademicStudyDocument.Theory("Kürdilihicazkâr", "Rast", "Nim Zirgüle",
                        123.8805, 90.5661, 4.0, 130.5336, "Arel-Ezgi-Uzdilek / 53-Hc",
                        sampleTheoryPitches()),
                new AcademicStudyDocument.TonicEvidence("Son kararlı ses + kullanıcı onayı",
                        123.8805, 2503.3, 2506.4, .691, 5.3, 84, true,
                        "Alkış bölümü karar hesabına katılmadı."),
                events, trace,
                List.of(
                        new AcademicStudyDocument.ProfileComparison(1, "Kürdilihicazkâr", 91.4, 8.7, 18.2,
                                88.0, 75.0, 44, "Seçilen profile yakın", false, 89.2, 4,
                                List.of(
                                        new AcademicStudyDocument.DegreeComparison(1, 0.0, 0.0, true,
                                                1.2, .053, 1.2, .053, 18, 8.2),
                                        new AcademicStudyDocument.DegreeComparison(2, 90.566, 4.0, true,
                                                93.1, 4.112, 2.534, .112, 11, 5.1),
                                        new AcademicStudyDocument.DegreeComparison(3, 203.774, 9.0, true,
                                                199.8, 8.825, -3.974, -.175, 9, 3.8),
                                        new AcademicStudyDocument.DegreeComparison(4, 294.340, 13.0, true,
                                                300.2, 13.259, 5.860, .259, 6, 2.7))),
                        new AcademicStudyDocument.ProfileComparison(2, "Kürdî", 91.3, 8.9, 18.5, 87.0, 75.0, 44, "Yalnız aralık profiliyle ayrışmıyor", true),
                        new AcademicStudyDocument.ProfileComparison(3, "Uşşak", 73.2, 17.4, 34.1, 64.0, 62.5, 39, "İkinci derece kanıtı farklı", false)),
                List.of("40 ms pencere, 20 ms adım.", "Kanonik perde kimlikleri karşılıklı dışlanır.",
                        "%35-%65 güven aralığı denetim izinde korunur; ana istatistiğe alınmaz."),
                List.of("Bu sonuç tek bir koristin çıplak sesi değildir.",
                        "Aralık profili benzerliği makam kimliğini tek başına kanıtlamaz."),
                ZonedDateTime.of(2026, 10, 30, 10, 0, 0, 0, ZoneId.of("Europe/Istanbul")),
                List.of(), sampleDirectionalFirstFourProfiles());
    }

    private static List<AcademicStudyDocument.DirectionalFirstFourProfile>
    sampleDirectionalFirstFourProfiles() {
        return List.of(
                new AcademicStudyDocument.DirectionalFirstFourProfile("Çıkıcı", 1, "Kürdî",
                        4, 15, 3, patternCounts("1→2→3→4", 1, "1→3→4", 1,
                        "2→3→4", 2), "COMPARABLE", 91.2, 100.0, 2.4, 6.2, -1.1),
                new AcademicStudyDocument.DirectionalFirstFourProfile("Çıkıcı", 2, "Uşşak",
                        2, 7, 1, patternCounts("1→2→3→4", 0, "1→3→4", 1,
                        "2→3→4", 1), "LIMITED", 73.4, 66.7, 8.8, 17.1, 4.2),
                new AcademicStudyDocument.DirectionalFirstFourProfile("İnici", 1, "Kürdî",
                        3, 12, 3, patternCounts("4→3→2→1", 1, "4→2→1", 1,
                        "3→2→1", 1), "COMPARABLE", 88.7, 100.0, 3.1, 7.5, -2.0),
                new AcademicStudyDocument.DirectionalFirstFourProfile("İnici", 2, "Uşşak",
                        1, 4, 1, patternCounts("4→3→2→1", 1, "4→2→1", 0,
                        "3→2→1", 0), "INSUFFICIENT", Double.NaN, 100.0,
                        10.2, 18.4, 6.7));
    }

    private static Map<String, Integer> patternCounts(String first, int firstCount,
                                                      String second, int secondCount,
                                                      String third, int thirdCount) {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put(first, firstCount);
        values.put(second, secondCount);
        values.put(third, thirdCount);
        return values;
    }

    private static List<AcademicStudyDocument.TheoryPitch> sampleTheoryPitches() {
        return List.of(
                theoryPitch("Rast", 0.0),
                theoryPitch("Nim Zirgüle", 4.0),
                theoryPitch("Zirgüle", 5.0),
                theoryPitch("Dik Zirgüle", 8.0),
                theoryPitch("Dügâh", 9.0));
    }

    private static AcademicStudyDocument.TheoryPitch theoryPitch(String name, double hc) {
        double cent = hc * AcademicStudyDocument.HC_CENT;
        return new AcademicStudyDocument.TheoryPitch(name, cent, hc,
                123.8805 * Math.pow(2.0, cent / 1200.0));
    }
}
