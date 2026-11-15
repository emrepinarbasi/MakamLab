package com.makambox.studio.report.docx;

import com.makambox.studio.study.FirstFourMakamGroupCatalog;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFldChar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPBdr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes Makam's complete academic single-pitch study as a true, editable DOCX.
 * Text and tables remain native Word content; only deterministic charts are PNG.
 */
public final class DocxStudyReportExporter {
    private static final int CONTENT_WIDTH_TWIPS = 9_746;
    private static final String FONT = "Arial";
    private static final String INK = "182334";
    private static final String MUTED = "5B6F86";
    private static final String RED = "BE1F34";
    private static final String PALE_RED = "FCEBED";
    private static final String PALE_BLUE = "EAF1FC";
    private static final String PALE_GREEN = "EAF8F2";
    private static final String HEADER = "DCE6F1";
    private static final String BORDER = "C9D5E1";
    private final AcademicStudyCharts charts = new AcademicStudyCharts();

    public void export(AcademicStudyDocument report, Path requestedPath) throws IOException {
        if (report == null) throw new IllegalArgumentException(t(
                "report.dataRequired", "Report data is required", "Rapor verisi gerekli"));
        if (requestedPath == null) throw new IllegalArgumentException(t(
                "report.pathRequired", "A DOCX path is required", "DOCX yolu gerekli"));
        Path absolute = requestedPath.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".makam-word-", ".docx.part");
        boolean moved = false;
        try (XWPFDocument document = new XWPFDocument()) {
            configureDocument(document, report);
            cover(document, report);
            sourceAndMethod(document, report);
            tonicSection(document, report);
            theorySection(document, report);
            resultsSection(document, report);
            eventsSection(document, report);
            // The directional Segâh study answers a deliberately narrower
            // question: how the same reference pitch changes by phrase
            // direction and direct hold.  A makam-profile ranking in this
            // report would mix a separate inferential task into that result.
            if (!report.directionalSegahAnalysis()) profileSection(document, report);
            limitationsSection(document, report);
            contactsAppendix(document, report);
            try (var output = Files.newOutputStream(temporary)) {
                document.write(output);
            }
        }
        try {
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private void configureDocument(XWPFDocument document, AcademicStudyDocument report) {
        document.createStyles();
        var properties = document.getProperties().getCoreProperties();
        properties.setTitle(report.reportTitle());
        properties.setSubjectProperty(report.theory().makamName() + " / " + report.theory().targetNote());
        properties.setCreator("MakamLab - " + t("report.academicPerformanceAnalysis",
                "Academic Performance Analysis", "Akademik İcra Analizi"));
        properties.setDescription(t("report.documentDescription",
                "53-Hc pitch-performance, direction and makam-profile analysis",
                "53-Hc tek-perde icra, yön ve makam-profili analizi"));
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz size = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        size.setW(BigInteger.valueOf(11_906));
        size.setH(BigInteger.valueOf(16_838));
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(1_080));
        margins.setBottom(BigInteger.valueOf(1_080));
        margins.setLeft(BigInteger.valueOf(1_080));
        margins.setRight(BigInteger.valueOf(1_080));
        margins.setHeader(BigInteger.valueOf(500));
        margins.setFooter(BigInteger.valueOf(500));
        addHeader(document, report);
        addFooter(document);
    }

    private void cover(XWPFDocument document, AcademicStudyDocument report) {
        spacer(document, 3);
        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = run(title, coverTitle(report), 27, true, INK);
        titleRun.setTextPosition(2);
        XWPFParagraph subtitle = document.createParagraph();
        subtitle.setAlignment(ParagraphAlignment.CENTER);
        String targetLabel = report.theory().intervalAnalysis()
                ? t("report.analysisInterval", "Analysis interval", "İnceleme aralığı")
                : report.directionalSegahAnalysis()
                ? t("report.directionalTarget", "Directional target", "Yönsel hedef")
                : t("report.targetPitch", "Target pitch", "Hedef perde");
        run(subtitle, t("report.coverSubtitle", "Tonic: {0} — {1}: {2} — 53-Hc theory",
                "Karar perdesi: {0} — {1}: {2} — 53-Hc kuramı",
                report.theory().tonicNote(), targetLabel, report.theory().targetNote()), 13, false, MUTED);
        spacer(document, 1);
        coverMetadata(document, report);
        summaryCards(document, report);

        paragraph(document, t("report.shortConclusion", "Brief conclusion", "Kısa sonuç"), 14, true, INK, 10, 4);
        paragraph(document, report.shortConclusion(), 11, true, INK, 0, 8);
        paragraph(document,
                t("report.coverInterpretation", "Negative deviation indicates a flatter use relative to the theoretical target; positive deviation indicates a sharper use. In polyphonic sources the result represents the dominant melodic F0 track in the selected channel rather than one performer.",
                        "Negatif sapma kuramsal hedefe göre pest, pozitif sapma dik kullanımı gösterir. Çok sesli kaynaklarda sonuç tek bir icracı yerine seçilmiş kanaldaki baskın melodik F0 izini temsil eder."),
                10, false, MUTED, 0, 8);
        pageBreak(document);
    }

    private static String coverTitle(AcademicStudyDocument report) {
        if (report.directionalSegahAnalysis()) {
            return t("report.title.directionalSegah", "{0} Makam\nAscending–Descending Segâh Usage Analysis",
                    "{0} Makamında\nİnici-Çıkıcı Segâh Kullanım Analizi", report.theory().makamName());
        }
        if (report.theory().intervalAnalysis()) {
            return t("report.title.interval", "{0} Makam\n{1} Analysis",
                    "{0} Makamında\n{1} Analizi", report.theory().makamName(), report.theory().targetNote());
        }
        return t("report.title.singlePitch", "{0} Makam\n{1} Usage Analysis",
                "{0} Makamında\n{1} Kullanım Analizi", report.theory().makamName(), report.theory().targetNote());
    }

    private void coverMetadata(XWPFDocument document, AcademicStudyDocument report) {
        XWPFParagraph source = document.createParagraph();
        source.setSpacingAfter(35);
        run(source, t("report.recording", "Recording", "İncelenen kayıt") + ": ", 10, true, INK);
        run(source, report.source().fileName(), 10, false, INK);
        XWPFParagraph details = document.createParagraph();
        details.setSpacingAfter(120);
        run(details, t("report.duration", "Duration", "Süre") + ": ", 10, true, INK);
        run(details, formatTime(report.source().durationSeconds()), 10, false, INK);
        run(details, " — " + t("report.channel", "Analysis channel", "Analiz kanalı") + ": ", 10, true, INK);
        run(details, dynamicLabel(report.source().channelDescription()), 10, false, INK);
        run(details, " — " + t("report.date", "Report date", "Rapor tarihi") + ": ", 10, true, INK);
        run(details, ReportLocalization.dateTime(report.generatedAt()), 10, false, INK);
    }

    private void summaryCards(XWPFDocument document, AcademicStudyDocument report) {
        XWPFTable table = document.createTable(2, 2);
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);
        CTTblPr properties = table.getCTTbl().getTblPr();
        var layout = properties.isSetTblLayout() ? properties.getTblLayout() : properties.addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);
        CTTblGrid grid = table.getCTTbl().getTblGrid();
        if (grid == null) grid = table.getCTTbl().addNewTblGrid();
        while (grid.sizeOfGridColArray() > 0) grid.removeGridCol(0);
        grid.addNewGridCol().setW(BigInteger.valueOf(CONTENT_WIDTH_TWIPS / 2));
        grid.addNewGridCol().setW(BigInteger.valueOf(CONTENT_WIDTH_TWIPS / 2));
        CTTblBorders borders = properties.isSetTblBorders() ? properties.getTblBorders()
                : properties.addNewTblBorders();
        for (CTBorder border : List.of(borders.addNewTop(), borders.addNewLeft(), borders.addNewBottom(),
                borders.addNewRight(), borders.addNewInsideH(), borders.addNewInsideV())) {
            border.setVal(STBorder.SINGLE); border.setColor(BORDER); border.setSz(BigInteger.valueOf(4));
        }

        double median = report.measurableStatistics().medianCent();
        setSummaryCard(table.getRow(0).getCell(0), t("report.summary.tonicFrequency",
                        "TONIC FREQUENCY", "KARAR FREKANSI"),
                format(report.theory().tonicHz(), 4) + " Hz",
                report.theory().tonicNote() + " - "
                        + t("report.confidence", "confidence", "güven") + " "
                        + percent(report.tonic().confidence()),
                PALE_BLUE);
        setSummaryCard(table.getRow(0).getCell(1),
                report.theory().intervalAnalysis() ? t("report.summary.intervalCentre",
                        "THEORETICAL INTERVAL CENTRE", "KURAMSAL ARALIK MERKEZİ")
                        : report.directionalSegahAnalysis() ? t("report.summary.segahReference",
                        "THEORETICAL SEGÂH REFERENCE", "KURAMSAL SEGÂH REFERANSI")
                        : t("report.summary.theoreticalTarget", "THEORETICAL TARGET", "KURAMSAL HEDEF"),
                format(report.theory().targetHz(), 4) + " Hz",
                signed(report.theory().targetRelativeCent(), 4) + " C / "
                        + signed(report.theory().targetRelativeHc(), 4) + " Hc",
                PALE_GREEN);
        if (report.directionalSegahAnalysis()) {
            setSummaryCard(table.getRow(1).getCell(0),
                    t("report.summary.reliableSegahEvent",
                            "RELIABLE SEGÂH EVENT", "GÜVENİLİR SEGÂH OLAYI"),
                    Integer.toString(report.measurableStatistics().count()),
                    t("report.summary.eventDetail", "{0} flatter - {1} sharper - total {2} {3}",
                            "{0} pest - {1} dik - toplam {2} {3}",
                            report.measurableStatistics().pestCount(),
                            report.measurableStatistics().dikCount(),
                            format(report.measurableStatistics().totalDurationSeconds(), 2),
                            t("unit.secondsShort", "s", "sn")),
                    "F5F8FC");
        } else {
            // Keep the three evidence layers visibly separate.  "All" is the complete
            // canonical target set (or every contact inside an open interval), while
            // measurable and stable are progressively stricter subsets of that same set.
            String allContactsLabel = report.theory().intervalAnalysis()
                    ? t("report.scope.openInterval", "Selected open interval", "Seçilen açık aralık")
                    : t("report.all", "All", "Hepsi");
            setSummaryCard(table.getRow(1).getCell(0),
                    t("report.eventCount", "EVENT COUNT", "OLAY SAYISI"),
                    report.contacts().size() + " / "
                            + report.measurableStatistics().count() + " / "
                            + report.stableStatistics().count(),
                    allContactsLabel + " / "
                            + t("report.measurableEvents", "Measurable events", "Ölçülebilir olaylar") + " / "
                            + t("report.stableSubset", "Stable subset", "Kararlı alt küme") + " • "
                            + t("report.totalDuration", "Measurable duration", "Ölçülebilir süre") + " "
                            + format(report.measurableStatistics().totalDurationSeconds(), 2) + " "
                            + t("unit.secondsShort", "s", "sn"),
                    "F5F8FC");
        }
        setSummaryCard(table.getRow(1).getCell(1), t("report.summary.mainMedianDeviation",
                        "MAIN MEDIAN DEVIATION", "ANA MEDYAN SAPMA"),
                signed(median, 2) + " C",
                signed(report.measurableStatistics().medianHc(), 3) + " Hc",
                median < -1.0e-9 ? PALE_RED : median > 1.0e-9 ? PALE_BLUE : PALE_GREEN);
        XWPFParagraph after = document.createParagraph();
        after.setSpacingAfter(40);
    }

    private void setSummaryCard(XWPFTableCell cell, String label, String value,
                                String detail, String fill) {
        CTTcPr properties = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr()
                : cell.getCTTc().addNewTcPr();
        CTShd shading = properties.isSetShd() ? properties.getShd() : properties.addNewShd();
        shading.setFill(fill);
        cell.setWidth("50%");
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        XWPFParagraph paragraph = cell.getParagraphs().isEmpty()
                ? cell.addParagraph() : cell.getParagraphs().get(0);
        while (!paragraph.getRuns().isEmpty()) paragraph.removeRun(0);
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingBefore(55);
        paragraph.setSpacingAfter(55);
        XWPFRun labelRun = run(paragraph, label, 8.5, false, INK);
        labelRun.addBreak();
        XWPFRun valueRun = run(paragraph, value, 18, true, INK);
        valueRun.addBreak();
        run(paragraph, detail, 9.5, false, INK);
    }

    private void sourceAndMethod(XWPFDocument document, AcademicStudyDocument report) {
        heading(document, section("1", "report.sourceMethod", "Source and method", "Kaynak ve yöntem"), 1);
        table(document, new String[][]{
                {t("report.recording", "Recording", "Kayıt"), report.source().fileName()},
                {t("report.duration", "Duration", "Süre"), formatTime(report.source().durationSeconds())},
                {t("report.sampleRate", "Sample rate", "Örnekleme"), format(report.source().sampleRate(), 0) + " Hz"},
                {t("report.channel", "Analysis channel", "Analiz kanalı"),
                        dynamicLabel(report.source().channelDescription())},
                {t("report.application", "Application", "Uygulama"), report.source().applicationVersion()},
                {t("report.engine", "Analysis engine", "Analiz motoru"), report.source().engineVersion()},
                {"SHA-256", blankDash(report.source().sha256())},
                {t("report.sourcePath", "Source path", "Kaynak yolu"), blankDash(report.source().absolutePath())}
        }, new int[]{24, 76}, false);
        paragraph(document,
                t("report.prose.sourceMethod",
                        "The recording was filtered to avoid artificial frequencies introduced during analysis and converted to a 22.05 kHz working copy. Pitch height was determined from the fundamental frequency, or F0. F0 expresses the basic vibration frequency of the heard sound in hertz. Reliable F0 measurements were assigned to mutually exclusive 53-Hc theoretical pitch centres in the selected makam; statistical evaluation used only canonical events that met the study criteria. Wide analysis regions were not accepted as proof of pitch identity by themselves.",
                        "Ses kaydı, analiz sırasında oluşabilecek yapay frekansları önlemek amacıyla süzülmüş ve 22,05 kHz örnekleme hızında bir çalışma kopyasına dönüştürülmüştür. Perde yüksekliği, temel frekans ya da F0 adı verilen ölçüm üzerinden belirlenmiştir. F0, işitilen sesin temel titreşim frekansını Hz cinsinden ifade eder. Güvenilir F0 ölçümleri, seçilen makamın 53-Hc kuramsal perde merkezlerine karşılıklı dışlayan biçimde atanmış; istatistiksel değerlendirme yalnız araştırma ölçütlerini karşılayan kanonik olaylar üzerinden yürütülmüştür. Geniş inceleme alanları tek başına perde kimliğinin kanıtı olarak kabul edilmemiştir."),
                10, false, INK, 8, 6);
        for (String note : report.methodNotes()) bullet(document, note);
        readerGuide(document);
    }

    /** Compact, plain-language glossary placed before the analytical sections. */
    private void readerGuide(XWPFDocument document) {
        pageBreak(document);
        heading(document, t("report.readingGuide", "How to read this report",
                "Terimleri nasıl okuyacaksınız?"), 1);
        paragraph(document,
                t("report.prose.readingGuide",
                        "The explanations below help readers understand the measurements in tables and charts without requiring expertise in music theory or audio analysis. C and Hc positions are stated relative to the tonic; deviations are stated relative to the relevant theoretical pitch centre.",
                        "Aşağıdaki açıklamalar, tablolar ve grafiklerde kullanılan ölçüleri müzik kuramı veya ses analizi uzmanlığı gerektirmeden okuyabilmek için hazırlanmıştır. C ve Hc değerleri karar perdesine göre; sapma değerleri ise ilgili kuramsal perde merkezine göre verilir."),
                10, false, INK, 0, 6);
        table(document, new String[][]{
                {t("report.glossary.term", "Term", "Terim"), t("report.glossary.plainExplanation", "Plain explanation", "Sade açıklama"),
                        t("report.glossary.term", "Term", "Terim"), t("report.glossary.plainExplanation", "Plain explanation", "Sade açıklama")},
                {"F0", t("report.glossary.f0", "The fundamental vibration frequency that determines pitch height.", "Sesin perde yüksekliğini belirleyen temel titreşim frekansı."),
                        "Hz", t("report.glossary.hz", "The number of vibrations per second; the physical unit of frequency.", "Bir saniyedeki titreşim sayısı. Frekansın fiziksel ölçü birimi.")},
                {"Cent (C)", t("report.glossary.cent", "A small interval unit obtained by dividing an octave into 1,200 equal parts.", "Bir oktavın 1.200 eşit parçaya bölünmesiyle elde edilen küçük aralık birimi."),
                        "Holder comma (Hc)", t("report.glossary.hc", "An interval unit obtained by dividing an octave into 53 equal parts; 1 Hc is approximately 22.64 C.", "Bir oktavın 53 eşit parçaya bölünmesiyle elde edilen aralık birimi; 1 Hc yaklaşık 22,64 C'dir.")},
                {t("report.glossary.theoryTarget", "Theoretical target / centre", "Kuramsal hedef / merkez"), t("report.glossary.theoryTargetDefinition", "The expected C, Hc and Hz position of a pitch according to makam theory.", "Makam kuramına göre perdenin beklenen C, Hc ve Hz konumu."),
                        t("report.glossary.deviation", "Deviation", "Sapma"), t("report.glossary.deviationDefinition", "Calculation: observed position - theoretical target. Example: if the target is 90 C and the measurement is 86 C, deviation is -4 C, meaning 4 C flat. A 94 C measurement would be +4 C, or 4 C sharp.", "Hesap: gözlenen konum - kuramsal hedef. Örnek: hedef 90 C, ölçüm 86 C ise sapma -4 C'dir; ses hedefin 4 C pestindedir. Ölçüm 94 C olsaydı sapma +4 C, yani 4 C dik olurdu.")},
                {t("report.glossary.negativeFlat", "Negative / flat", "Negatif / pest"), t("report.glossary.negativeFlatDefinition", "The observed sound is lower than the theoretical centre.", "Gözlenen sesin kuramsal merkezden daha düşük olması."),
                        t("report.glossary.positiveSharp", "Positive / sharp", "Pozitif / dik"), t("report.glossary.positiveSharpDefinition", "The observed sound is higher than the theoretical centre.", "Gözlenen sesin kuramsal merkezden daha yüksek olması.")},
                {t("report.median", "Median", "Medyan"), t("report.glossary.medianDefinition", "The typical middle value after sorting. Example: the median of -12, -5, -4, +2 and +9 C is -4 C, meaning the typical performance is about 4 C flat of the target.", "Değerler sıralandığında ortada kalan tipik değerdir. Örnek: -12, -5, -4, +2 ve +9 C ölçümlerinin medyanı -4 C'dir; bu, tipik icranın hedeften yaklaşık 4 C pest olduğunu anlatır."),
                        t("report.glossary.arithmeticMean", "Arithmetic mean", "Aritmetik ortalama"), t("report.glossary.arithmeticMeanDefinition", "The overall centre obtained by dividing the sum of all values by the number of events.", "Bütün değerlerin toplamının olay sayısına bölünmesiyle elde edilen genel merkez.")},
                {t("report.weightedMean", "Duration-weighted mean", "Süre ağırlıklı ortalama"), t("report.glossary.weightedMeanDefinition", "A mean that gives longer events more weight than brief contacts.", "Uzun duyulan olaylara kısa temaslardan daha fazla ağırlık veren ortalama."),
                        t("report.glossary.standardDeviation", "Standard deviation / spread", "Standart sapma / dağılım"), t("report.glossary.standardDeviationDefinition", "This is not one sound's difference from the target; it shows how close all performances are to one another. Small values indicate similarity, larger values greater variability.", "Tek bir sesin hedefe farkı değildir; bütün basışların birbirine ne kadar yakın olduğunu anlatır. Küçük değerler benzer, büyük değerler daha değişken basışlara işaret eder.")},
                {t("report.glossary.stableEvent", "Stable event", "Kararlı olay"), t("report.glossary.stableEventDefinition", "An event whose pitch fluctuation within its body remains inside the predefined limit.", "Olay gövdesindeki perde dalgalanması önceden belirlenen sınırın içinde kalan olay."),
                        t("report.glossary.measurableEvent", "Measurable event", "Ölçülebilir olay"), t("report.glossary.measurableEventDefinition", "An event that meets minimum duration and confidence conditions and can enter the numerical summary.", "Asgari süre ve güven koşullarını karşılayarak sayısal özete alınabilen olay.")},
                {"±1 Hc", t("report.glossary.oneHcDefinition", "A proximity band of approximately ±22.64 C around the theoretical centre.", "Kuramsal merkezin yaklaşık ±22,64 C çevresindeki yakınlık bandı."),
                        t("report.confidence", "Confidence", "Güven"), t("report.glossary.confidenceDefinition", "A 0–100 ratio indicating how strongly audio evidence supports the F0 measurement.", "F0 ölçümünün ses kanıtıyla ne ölçüde desteklendiğini gösteren 0–100 arası oran.")},
                {t("report.glossary.boxPlot", "Box plot", "Kutu grafik"), t("report.glossary.boxPlotDefinition", "The middle line shows the median, the box the middle half of events, and points individual events.", "Ortadaki çizgi medyanı, kutu olayların ortadaki yarısını, noktalar ise tek tek olayları gösterir."),
                        "", ""}
        }, new int[]{14, 36, 14, 36}, true, 7.7);
    }

    private void tonicSection(XWPFDocument document, AcademicStudyDocument report) throws IOException {
        heading(document, section("2", "report.tonicDetermination", "Determining the tonic",
                "Karar perdesinin belirlenmesi"), 1);
        table(document, new String[][]{
                {t("report.method", "Method", "Yöntem"), dynamicLabel(report.tonic().method()),
                        t("report.status", "Status", "Durum"), report.tonic().userConfirmed()
                        ? t("report.userConfirmed", "Confirmed by the user", "Kullanıcı tarafından onaylandı")
                        : t("report.automaticCandidate", "Automatic candidate", "Otomatik aday")},
                {t("report.tonic", "Tonic", "Karar"), format(report.tonic().hertz(), 4) + " Hz",
                        t("report.confidence", "Confidence", "Güven"), percent(report.tonic().confidence())},
                {t("report.selectionRange", "Selection range", "Seçim aralığı"), formatTime(report.tonic().startSeconds()) + "-" + formatTime(report.tonic().endSeconds()),
                        t("report.voicedFrames", "Voiced frames", "Sesli çerçeve"), Integer.toString(report.tonic().voicedFrameCount())},
                {t("report.robustSpread", "Robust spread", "Sağlam yayılım"), format(report.tonic().robustSpreadCent(), 2) + " C",
                        t("report.note", "Note", "Not"), localizedReason(report.tonic().note())}
        }, new int[]{18, 32, 18, 32}, true);
        image(document, charts.tonicEvidence(report), "karar-kaniti.png", 6.35);
        paragraph(document,
                t("report.prose.tonicDetermination",
                        "The tonic frequency is determined from all reliable measurements in the selected time region, not from a single point on the pitch trace. The robust median used in this calculation is less affected by brief jumps and unusual measurements. Confidence, spread and possible octave errors are evaluated together to limit the effect of transient frequency changes on the tonic estimate.",
                        "Karar frekansı, ses izi üzerindeki tek bir noktadan değil, seçilen zaman bölgesindeki güvenilir ölçümlerin bütünü üzerinden belirlenmiştir. Hesaplamada kullanılan sağlam medyan, kısa süreli sıçramalardan ve olağan dışı ölçümlerden daha az etkilenen merkezî değerdir. Ölçümlerin güven düzeyi, yayılımı ve olası oktav hataları birlikte değerlendirilerek geçici frekans değişimlerinin karar kestirimi üzerindeki etkisi sınırlandırılmıştır."),
                10, false, MUTED, 3, 6);
    }

    private void theorySection(XWPFDocument document, AcademicStudyDocument report) throws IOException {
        if (report.directionalSegahAnalysis()) {
            heading(document, section("3", "report.segahReferenceArea",
                    "Segâh reference and candidate region", "Segâh referansı ve aday alanı"), 1);
            paragraph(document,
                    t("report.prose.directionalTheory",
                            "This analysis uses Segâh as the measurement centre. It first finds candidate sounds between Dügâh and Bûselik, excluding the boundary pitches themselves. It then checks each candidate's proximity to Segâh and whether a motion toward Dügâh occurs before or after it.",
                            "Bu analizde ölçümün merkezi Segâh perdesidir. Program önce Dügâh ile Bûselik arasında kalan sesleri aday olarak bulur; Dügâh ve Bûselik sınırlarının kendisini aday saymaz. Ardından her adayın Segâh'a ne kadar yakın olduğunu ve adaydan önce ya da sonra Dügâh yönünde bir hareket bulunup bulunmadığını denetler."),
                    10, false, INK, 0, 6);
            table(document, new String[][]{
                    {t("report.makam", "Makam", "Makam"), report.theory().makamName()},
                    {t("report.tonic", "Tonic", "Karar perdesi"), report.theory().tonicNote() + " = "
                            + format(report.theory().tonicHz(), 6) + " Hz"},
                    {t("report.segahReference", "Segâh reference", "Segâh referansı"), signed(report.theory().targetRelativeCent(), 4)
                            + " C / " + signed(report.theory().targetRelativeHc(), 4) + " Hc / "
                            + format(report.theory().targetHz(), 4) + " Hz"},
                    {t("report.candidateArea", "Candidate region", "Aday alanı"), report.theory().lowerBoundaryNote()
                            + " < F0 < " + report.theory().upperBoundaryNote() + "; "
                            + t("report.boundariesExcluded", "both boundaries excluded", "iki sınır da hariç")},
                    {t("report.signMeaning", "Meaning of the sign", "İşaretin anlamı"),
                            t("report.segahSignMeaning", "Negative deviation = flatter than the Segâh reference; positive deviation = sharper",
                                    "Negatif sapma = Segâh referansından pest; pozitif sapma = dik/tiz")},
                    {t("report.ascendingEvidence", "Ascending evidence", "Çıkıcı kanıt"),
                            t("report.segahAscendingEvidence", "Dügâh→Segâh is sufficient; confidence rises when Çargâh and Nevâ continue in the same direction",
                                    "Dügâh→Segâh yeterlidir; Çargâh ve Nevâ aynı yönde görülürse güven artar")},
                    {t("report.descendingEvidence", "Descending evidence", "İnici kanıt"),
                            t("report.segahDescendingEvidence", "Segâh→Dügâh is sufficient; confidence rises when Çargâh and Nevâ precede it",
                                    "Segâh→Dügâh yeterlidir; önce Çargâh ve Nevâ görülürse güven artar")},
                    {t("report.sustain", "Sustain", "Kalış"), t("report.segahSustainDefinition",
                            "A long and stable Segâh without a clear directional path",
                            "Belirgin yön yolu olmadan uzun ve kararlı Segâh")},
                    {t("report.hcConversion", "53-Hc conversion", "53-Hc dönüşümü"), "1 Hc = 1200/53 = "
                            + format(AcademicStudyDocument.HC_CENT, 6) + " C"}
            }, new int[]{31, 69}, false);
            heading(document, t("report.segahAscendingPositions", "Segâh positions in ascending phrases",
                    "Çıkıcı cümlelerde Segâh konumları"), 2);
            image(document, charts.directionalSegahClassification(report, "Çıkıcı"),
                    "segah-cikici-konumlari.png", 6.35);
            heading(document, t("report.segahDescendingPositions", "Segâh positions in descending phrases",
                    "İnici cümlelerde Segâh konumları"), 2);
            image(document, charts.directionalSegahClassification(report, "İnici"),
                    "segah-inici-konumlari.png", 6.35);
            heading(document, t("report.segahSustainedPositions", "Positions of direct Segâh sustains",
                    "Doğrudan Segâh kalışlarının konumları"), 2);
            image(document, charts.directionalSegahClassification(report, "Segâh kalışı"),
                    "segah-kalis-konumlari.png", 6.35);
            paragraph(document,
                    t("report.prose.directionalCharts",
                            "The three charts are independent: the first shows only ascending phrases, the second only descending phrases, and the third only direct Segâh sustains. The shaded region is the same candidate-search area in each chart; the entire region is not automatically treated as Segâh. The turquoise line marks the theoretical Segâh centre, and coloured points mark the accepted merged musical usages in the relevant group.",
                            "Üç grafik birbirinden bağımsızdır: birincisi yalnız çıkıcı cümleleri, ikincisi yalnız inici cümleleri, üçüncüsü yalnız doğrudan Segâh kalışlarını gösterir. Her grafikteki boyalı alan aynı aday tarama alanıdır; bu alanın tamamı otomatik olarak Segâh kabul edilmez. Turkuaz çizgi Segâh'ın kuramsal merkezini, renkli noktalar ise ilgili grupta kabul edilen birleştirilmiş müziksel kullanımları gösterir."),
                    10, false, INK, 6, 6);
            return;
        }
        if (report.theory().intervalAnalysis()) {
            heading(document, section("3", "report.theoreticalIntervalClassification",
                    "Theoretical analysis interval and classification",
                    "Kuramsal inceleme aralığı ve sınıflandırma"), 1);
            paragraph(document,
                    t("report.prose.intervalTheory",
                            "The selected interval is positioned from the user-confirmed tonic frequency in 53 equal Holder commas (Hc). Hc divides an octave into 53 equal steps, while cents (C) divide an octave into 1,200 equal parts. The values below show the lower and upper theoretical boundaries and their frequency equivalents.",
                            "Seçilen aralık, kullanıcı tarafından onaylanan karar frekansına göre 53 eşit Holder koması (Hc) birimiyle konumlandırılmıştır. Hc, bir oktavı 53 eşit basamağa bölen ölçü birimidir; C (cent) ise bir oktavı 1.200 eşit parçaya böler. Aşağıdaki değerler, araştırmanın alt ve üst kuramsal sınırlarını ve bu sınırların frekans karşılıklarını gösterir."),
                    10, false, INK, 0, 6);
            table(document, new String[][]{
                    {t("report.makam", "Makam", "Makam"), report.theory().makamName()},
                    {t("report.tonic", "Tonic", "Karar perdesi"), report.theory().tonicNote()},
                    {t("report.hcConversion", "53-Hc conversion", "53-Hc dönüşümü"), "1 Hc = 1200/53 = "
                            + format(AcademicStudyDocument.HC_CENT, 6) + " C"},
                    {t("report.lowerBoundaryExcluded", "Lower boundary (excluded)", "Alt sınır (araştırma dışı)"), report.theory().lowerBoundaryNote() + " = "
                            + signed(report.theory().lowerBoundaryCent(), 4)
                            + " C / " + signed(report.theory().lowerBoundaryHc(), 4) + " Hc / "
                            + format(report.theory().lowerBoundaryHz(), 4) + " Hz"},
                    {t("report.upperBoundaryExcluded", "Upper boundary (excluded)", "Üst sınır (araştırma dışı)"), report.theory().upperBoundaryNote() + " = "
                            + signed(report.theory().upperBoundaryCent(), 4)
                            + " C / " + signed(report.theory().upperBoundaryHc(), 4) + " Hc / "
                            + format(report.theory().upperBoundaryHz(), 4) + " Hz"},
                    {t("report.openIntervalRule", "Open-interval rule", "Açık aralık kuralı"), report.theory().lowerBoundaryNote()
                            + " < F0 < " + report.theory().upperBoundaryNote() + "; "
                            + t("report.boundariesExcluded", "both boundaries excluded", "iki sınır da araştırma dışı")},
                    {t("report.expectedMakamReference", "Expected makam reference within the interval",
                            "Makamın aralık içindeki beklenen referansı"),
                            expectedReferenceSummary(report.theory())},
                    {t("report.referenceSystem", "Reference system", "Referans sistemi"), report.theory().referenceSystem()}
            }, new int[]{34, 66}, false);
            paragraph(document,
                    t("report.prose.intervalPlainExplanation",
                            "What does this show? The measured fundamental frequency (F0) is the main frequency value that determines perceived pitch height. Only F0 values strictly between the lower and upper boundaries are studied; the boundary pitches themselves are excluded. Relative to the tonic, negative values indicate a lower position and positive values a higher position.",
                            "Bu neyi gösterir? Ölçülen temel frekans (F0), sesin algılanan yüksekliğini belirleyen ana frekans değeridir. Yalnız alt ve üst sınırın arasında kalan F0 değerleri incelenir; sınır perdelerinin kendisi araştırmaya alınmaz. Karara göre negatif değerler daha pest, pozitif değerler daha dik bir konumu belirtir."),
                    10, false, MUTED, 4, 6);
            image(document, charts.theoryClassification(report), "kuramsal-acik-aralik.png", 6.35);
            paragraph(document,
                    t("report.prose.intervalChart",
                            "The shaded region represents the open analysis interval with both boundaries excluded, so events belonging to the boundary pitches are not included in the study set. Each reliable measurable event in the interval is shown as a small burgundy point; these are the same performances listed individually in Section 5. The turquoise vertical line and large marker show the theoretical reference pitch expected in this interval for the selected makam. Each F0 event is evaluated against the nearest theoretical 53-Hc pitch centre. Intermediate sonorities beyond the naming threshold are not deleted; they remain in the report as unnamed observations with their C/Hc distance to the nearest centre. This classification is not a makam diagnosis by itself; it quantifies which theoretical centres the performances in the selected interval approach.",
                            "Boyalı bölge, iki sınırın dışlandığı açık analiz aralığını temsil eder; dolayısıyla sınır perdelerine ait olaylar araştırma kümesine alınmaz. Aralık içinde kalan her güvenilir ölçülebilir olay grafikte küçük bordo bir noktayla gösterilir; bu noktalar Bölüm 5'teki tek tek olay tablosuyla aynı seslendirmelerdir. Turkuaz dikey çizgi ve büyük işaret, seçilen makamın bu aralıkta beklenen kuramsal referans perdesini belirtir. F0 olayı en yakın kuramsal 53-Hc perde merkezine göre değerlendirilir. En yakın merkeze uzaklığı adlandırma eşiğini aşan ara seslendirmeler silinmez; adlandırılamayan gözlem olarak ve en yakın merkeze C/Hc uzaklığıyla raporda korunur. Bu sınıflandırma, tek başına makam teşhisi değildir; seçilen aralık içindeki seslendirmelerin hangi kuramsal merkezlere yaklaştığını sayısal olarak gösterir."),
                    10, false, INK, 7, 6);
            return;
        }
        heading(document, section("3", "report.theoreticalTarget",
                "Theoretical target and classification", "Kuramsal hedef ve sınıflandırma"), 1);
        paragraph(document,
                t("report.prose.singleTheory",
                        "The target pitch's position relative to the tonic is calculated in the 53-equal Holder-comma (Hc) system. Hc divides an octave into 53 equal steps, while cents (C) divide it into 1,200 equal parts. The approved tonic frequency is used to obtain the theoretical target frequency in hertz (Hz).",
                        "Hedef perdenin karara göre yeri, 53 eşit Holder koması (Hc) sistemi içinde hesaplanmıştır. Hc, bir oktavı 53 eşit basamağa bölen ölçü birimidir; C (cent) ise bir oktavı 1.200 eşit parçaya böler. Onaylanan karar frekansı kullanılarak hedef perdenin kuramsal frekansı hertz (Hz) cinsinden elde edilmiştir."),
                10, false, INK, 0, 6);
        table(document, new String[][]{
                {t("report.makam", "Makam", "Makam"), report.theory().makamName()},
                {t("report.tonic", "Tonic", "Karar perdesi"), report.theory().tonicNote() + " = "
                        + format(report.theory().tonicHz(), 6) + " Hz"},
                {t("report.hcConversion", "53-Hc conversion", "53-Hc dönüşümü"), "1 Hc = 1200/53 = "
                        + format(AcademicStudyDocument.HC_CENT, 6) + " C"},
                {t("report.targetPitch", "Target pitch", "İncelenen perde"), report.theory().targetNote() + " = "
                        + signed(report.theory().targetRelativeHc(), 4) + " Hc = "
                        + signed(report.theory().targetRelativeCent(), 6) + " C"},
                {t("report.theoreticalFrequency", "Theoretical target frequency", "Kuramsal hedef frekansı"), "f(" + report.theory().targetNote() + ") = "
                        + format(report.theory().tonicHz(), 6) + " x 2^("
                        + format(report.theory().targetRelativeCent(), 6) + "/1200) = "
                        + format(report.theory().targetHz(), 6) + " Hz"},
                {t("report.referenceSystem", "Reference system", "Referans sistemi"), report.theory().referenceSystem()},
        }, new int[]{34, 66}, false);
        paragraph(document,
                t("report.prose.singlePlainExplanation",
                        "What does this show? Starting from the tonic frequency, the formula calculates the theoretical frequency in Hz of the studied pitch. Relative to the tonic, negative Hc/C values indicate a lower position and positive values a higher position. Subsequent performance measurements are compared with this theoretical target.",
                        "Bu neyi gösterir? Formül, karar perdesinin frekansından başlayarak incelenen perdenin kuramsal olarak kaç Hz olması gerektiğini hesaplar. Karara göre negatif Hc/C değerleri daha pest, pozitif değerler daha dik bir konumu gösterir. Daha sonraki icra ölçümleri bu kuramsal hedefle karşılaştırılır."),
                10, false, MUTED, 4, 6);
        image(document, charts.theoryClassification(report), "kuramsal-hedef.png", 6.35);
        paragraph(document,
                t("report.prose.singleClassification",
                        "The shaded area is not a general tolerance window chosen by the user. It is the mutually exclusive classification region formed by the midpoints between the target pitch and its lower and upper neighbouring theoretical pitch centres. Thus, the same F0 frame cannot be assigned to two pitches. Deviation is calculated by subtracting the theoretical target position from the observed tonic-relative position; negative values indicate flatter and positive values sharper performance. This classification measures how the pitch centre is realized in performance and does not determine makam identity by itself.",
                        "Grafikteki boyalı alan, kullanıcı tarafından seçilen genel bir tolerans penceresi değildir. Hedef perdenin alt ve üst komşu kuramsal perde merkezleriyle orta noktalarından oluşan, birbirini dışlayan sınıflandırma alanıdır. Böylece aynı F0 çerçevesi iki farklı perdeye birden atanmaz. Rapordaki sapma, gözlenen karara göre konumdan hedefin kuramsal konumunun çıkarılmasıyla hesaplanır; negatif değer pest, pozitif değer dik icrayı gösterir. Bu sınıflandırma perde merkezinin icra içindeki gerçekleşmesini ölçer, tek başına makam kimliğini belirlemez."),
                10, false, INK, 7, 6);
    }

    private void resultsSection(XWPFDocument document, AcademicStudyDocument report) throws IOException {
        heading(document, section("4", "report.numericResultsCharts",
                "Numerical results and charts", "Sayısal sonuçlar ve grafikler"), 1);
        paragraph(document,
                t("report.prose.resultsIntro",
                        "This section presents event count, mean, median, duration-weighted mean and distribution width. The median is the central value with half the events below it and half above it. In the duration-weighted mean, longer sounds carry more weight than brief contacts. These measures are descriptive and explain only the tendency observed in the recording. Without considering sample size, they must not be interpreted as proof of a general performance rule or statistical significance.",
                        "Bu bölümde olay sayısı, ortalama, medyan, süre ağırlıklı ortalama ve dağılım genişliği sunulmaktadır. Medyan, olayların yarısının altında ve yarısının üstünde kaldığı merkezî değerdir. Süre ağırlıklı ortalamada uzun süre duyulan sesler kısa temaslardan daha fazla ağırlık taşır. Bu ölçüler betimseldir; yalnız incelenen kayıtta gözlenen eğilimi açıklar. Örneklem büyüklüğü dikkate alınmadan genel bir icra kuralı veya istatistiksel anlamlılık kanıtı olarak yorumlanmamalıdır."),
                10, false, INK, 0, 6);
        AcademicStudyDocument.Statistics main = report.measurableStatistics();
        AcademicStudyDocument.Statistics stable = report.stableStatistics();
        String allContactsLabel = report.theory().intervalAnalysis()
                ? t("report.scope.openInterval", "Selected open interval", "Seçilen açık aralık")
                : t("report.all", "All", "Hepsi");
        XWPFParagraph countLayers = document.createParagraph();
        countLayers.setSpacingAfter(100);
        run(countLayers, t("report.eventCount", "Event count", "Olay sayısı") + " — ",
                10, true, INK);
        run(countLayers,
                allContactsLabel + ": " + report.contacts().size()
                        + " • " + t("report.measurableEvents", "Measurable events", "Ölçülebilir olaylar")
                        + ": " + main.count()
                        + " • " + t("report.stableSubset", "Stable subset", "Kararlı alt küme")
                        + ": " + stable.count() + ".",
                10, false, INK);
        List<String[]> summaryRows = new ArrayList<>();
        summaryRows.add(new String[]{t("report.indicator", "Indicator", "Gösterge"),
                t("report.measurableEvents", "Measurable events", "Ölçülebilir olaylar"),
                t("report.stableSubset", "Stable subset", "Kararlı alt küme")});
        summaryRows.add(new String[]{t("report.eventCount", "Event count", "Olay sayısı"),
                Integer.toString(main.count()), Integer.toString(stable.count())});
        String seconds = t("unit.secondsShort", "s", "sn");
        summaryRows.add(new String[]{t("report.totalDuration", "Total duration", "Toplam süre"),
                format(main.totalDurationSeconds(), 2) + " " + seconds,
                format(stable.totalDurationSeconds(), 2) + " " + seconds});
        summaryRows.add(new String[]{t("report.meanDeviation", "Mean deviation", "Ortalama sapma"),
                cHc(main.meanCent()), cHc(stable.meanCent())});
        summaryRows.add(new String[]{t("report.medianDeviation", "Median deviation", "Medyan sapma"),
                cHc(main.medianCent()), cHc(stable.medianCent())});
        summaryRows.add(new String[]{t("report.weightedMean", "Duration-weighted mean", "Süre ağırlıklı ortalama"),
                cHc(main.weightedMeanCent()), cHc(stable.weightedMeanCent())});
        summaryRows.add(new String[]{t("report.standardDeviation", "Standard deviation", "Standart sapma"),
                format(main.standardDeviationCent(), 2) + " C", format(stable.standardDeviationCent(), 2) + " C"});
        if (!report.directionalSegahAnalysis()) {
            summaryRows.add(new String[]{t("report.minimumMaximum", "Lowest / highest", "En pest / en dik"),
                    extremeRange(report.measurableEvents()), extremeRange(report.stableEvents())});
        }
        summaryRows.add(new String[]{t("report.flatSharp", "Flat / sharp", "Pest / dik"),
                main.pestCount() + " / " + main.dikCount(), stable.pestCount() + " / " + stable.dikCount()});
        summaryRows.add(new String[]{t("report.withinOneHc", "Within ±1 Hc", "±1 Hc içinde"),
                main.withinOneHcCount() + "/" + main.count() + " (" + percent(main.withinOneHcPercent()) + ")",
                stable.withinOneHcCount() + "/" + stable.count() + " (" + percent(stable.withinOneHcPercent()) + ")"});
        summaryRows.add(new String[]{t("report.entrySettledExit", "Entry / settled / exit", "Giriş / yerleşik / çıkış"),
                signed(main.entryMeanCent(), 2) + " / " + signed(main.settledMeanCent(), 2)
                + " / " + signed(main.exitMeanCent(), 2) + " C",
                signed(stable.entryMeanCent(), 2) + " / " + signed(stable.settledMeanCent(), 2)
                        + " / " + signed(stable.exitMeanCent(), 2) + " C"});
        table(document, summaryRows.toArray(String[][]::new), new int[]{34, 33, 33}, true);
        if (!report.directionalSegahAnalysis()) {
            heading(document, t("report.pitchPeakDistribution",
                    "Performed pitch and intermediate-sound peaks",
                    "İcra edilen perde ve ara-ses tepeleri"), 2);
            paragraph(document,
                    t("report.prose.pitchPeakDistribution",
                            "Every detected contact in the selected analysis region is retained in this chart. Filled points indicate events admitted to the main numerical summary; pale or open points are shorter audit contacts. The horizontal position is the measured tonic-relative Hc value, so sounds between named theoretical centres remain visible rather than being silently discarded.",
                            "Bu grafik, seçilen inceleme alanında bulunan bütün temasları korur. Dolu noktalar ana sayısal özete alınan olayları; soluk veya içi boş noktalar ise daha kısa denetim temaslarını gösterir. Yatay konum, karar perdesine göre ölçülen Hc değeridir. Böylece adlandırılmış iki kuramsal merkezin arasında kalan sesler sessizce atılmadan görülebilir."),
                    9.5, false, INK, 1, 5);
            image(document, charts.pitchPeakDistribution(report),
                    "perde-ve-ara-ses-tepeleri.png", 6.35);

            heading(document, t("report.pitchCentreDeviations",
                    "Observed pitch centres and theoretical distances",
                    "Gözlenen perde merkezleri ve kuramsal uzaklıklar"), 2);
            paragraph(document,
                    t("report.prose.pitchCentreDeviations",
                            "Observed centres are compared with their nearest theoretical pitch centres by using the same measured F0 evidence. A negative difference means that the observed centre is flatter; a positive difference means that it is sharper. Separate observed modes are preserved instead of being replaced by a single artificial mean.",
                            "Gözlenen merkezler, aynı gerçek F0 kanıtı korunarak en yakın kuramsal perde merkezleriyle karşılaştırılır. Negatif fark gözlenen merkezin kuramsal merkezden pest; pozitif fark ise dik olduğunu gösterir. Ayrı gözlenen tepeler, tek bir yapay ortalamada eritilmeden korunur."),
                    9.5, false, INK, 1, 5);
            image(document, charts.pitchCenterDeviations(report),
                    "perde-merkezleri-ve-kuramsal-uzakliklar.png", 6.35);
        }
        image(document, charts.timeline(report), "zaman-sapma.png", 6.35);
        image(document, charts.distributionAndDirections(report), "dagilim-ve-yon.png", 6.35);
        paragraph(document, distributionInterpretation(report),
                10, false, INK, 2, 7);
        heading(document, report.directionalSegahAnalysis()
                ? t("report.segahUsageGroups", "Segâh usage groups", "Segâh kullanım grupları")
                : t("report.directionGroups", "Direction groups", "Yön grupları"), 2);
        if (report.directionalSegahAnalysis()) {
            directionalGroupTable(document, report);
        } else {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{t("report.netDirection", "Net direction", "Net yön"), "n",
                t("report.mean", "Mean", "Ortalama"), t("report.median", "Median", "Medyan"),
                t("report.weightedMeanShort", "Duration-wtd. mean", "Süre ağ. ort."),
                t("report.minimumMaximum", "Lowest / highest", "En pest / en dik"), "±1 Hc"});
        for (Map.Entry<String, AcademicStudyDocument.Statistics> entry
                : orderedDirectionStatistics(report).entrySet()) {
            AcademicStudyDocument.Statistics value = entry.getValue();
            List<AcademicStudyDocument.Event> directionEvents = report.measurableEvents().stream()
                    .filter(AcademicStudyDocument.Event::included)
                    .filter(event -> event.direction().equals(entry.getKey())).toList();
            rows.add(new String[]{dynamicLabel(entry.getKey()), Integer.toString(value.count()), cHc(value.meanCent()),
                    cHc(value.medianCent()), cHc(value.weightedMeanCent()),
                    extremeRange(directionEvents),
                    "%" + format(value.withinOneHcPercent(), 1)});
        }
        table(document, rows.toArray(String[][]::new),
                new int[]{16, 6, 16, 16, 16, 20, 10}, true, 7.2);
        }
        if (report.theory().intervalAnalysis()) {
            heading(document, t("report.intervalPitchMeans",
                    "Mean performance values of pitches in the interval",
                    "Aralık içindeki perdelerin ortalama icra değerleri"), 2);
            paragraph(document,
                    t("report.prose.intervalMeans",
                            "The first table shows, for each pitch, the mean frequency heard in the recording and its mean C/Hc position relative to the tonic. The theoretical position is the expected place in the makam scale; the mean observed position is the measured performance. The second table summarizes the difference between the two, namely deviation. The overall-mean row is the arithmetic centre of all events and must not be interpreted as a new pitch name by itself.",
                            "Aşağıdaki ilk tablo, her perde için sesin kayıtta ortalama olarak hangi frekansta ve karara göre hangi C/Hc konumunda duyulduğunu gösterir. Kuramsal konum, makam dizisinde beklenen yerdir; ortalama gözlenen konum ise icranın ölçülen yeridir. İkinci tablo bu iki konum arasındaki farkı, yani sapmayı özetler. Genel ortalama satırı bütün olayların aritmetik merkezidir; tek başına yeni bir perde adı olarak yorumlanmamalıdır."),
                    9.5, false, MUTED, 1, 5);
            List<AcademicStudyDocument.Event> intervalEvents = report.measurableEvents().stream()
                    .filter(AcademicStudyDocument.Event::included).toList();
            Map<String, List<AcademicStudyDocument.Event>> byPitch = intervalEvents.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            value -> value.noteName().isBlank() ? "" : value.noteName(),
                            LinkedHashMap::new, java.util.stream.Collectors.toList()));
            List<Map.Entry<String, List<AcademicStudyDocument.Event>>> orderedPitches = byPitch.entrySet().stream()
                    .sorted(Comparator.comparingDouble(entry -> entry.getValue().stream()
                            .mapToDouble(AcademicStudyDocument.Event::theoreticalCent)
                            .average().orElse(0.0)))
                    .toList();

            List<String[]> positionRows = new ArrayList<>();
            positionRows.add(new String[]{t("report.pitch", "Pitch", "Perde"), "n",
                    t("report.theoreticalPosition", "Theoretical position", "Kuramsal konum"),
                    t("report.meanHertz", "Mean Hz", "Ort. Hz"),
                    t("report.meanObservedPosition", "Mean observed position", "Ort. gözlenen konum")});
            if (!intervalEvents.isEmpty()) {
                positionRows.add(new String[]{t("report.overallMeanAllEvents",
                        "Overall mean (all events)", "Genel ortalama (tüm olaylar)"),
                        Integer.toString(intervalEvents.size()), "—",
                        format(intervalEvents.stream().mapToDouble(AcademicStudyDocument.Event::observedHz)
                                .average().orElse(0.0), 3) + " Hz",
                        cHc(intervalEvents.stream().mapToDouble(AcademicStudyDocument.Event::relativeCent)
                                .average().orElse(0.0))});
            }
            for (Map.Entry<String, List<AcademicStudyDocument.Event>> entry : orderedPitches) {
                List<AcademicStudyDocument.Event> values = entry.getValue();
                double theoreticalCent = values.stream()
                        .mapToDouble(AcademicStudyDocument.Event::theoreticalCent)
                        .average().orElse(0.0);
                double observedHz = values.stream()
                        .mapToDouble(AcademicStudyDocument.Event::observedHz)
                        .average().orElse(0.0);
                double observedCent = values.stream()
                        .mapToDouble(AcademicStudyDocument.Event::relativeCent)
                        .average().orElse(0.0);
                positionRows.add(new String[]{entry.getKey().isBlank()
                        ? t("report.unnamed", "Unnamed", "Adlandırılamayan") : entry.getKey(),
                        Integer.toString(values.size()),
                        cHc(theoreticalCent), format(observedHz, 3) + " Hz", cHc(observedCent)});
            }
            table(document, positionRows.toArray(String[][]::new),
                    new int[]{29, 7, 23, 16, 25}, true, 7.6);

            List<String[]> deviationRows = new ArrayList<>();
            deviationRows.add(new String[]{t("report.pitch", "Pitch", "Perde"),
                    t("report.totalDuration", "Total duration", "Toplam süre"),
                    t("report.meanDeviation", "Mean deviation", "Ortalama sapma"),
                    t("report.medianDeviation", "Median deviation", "Medyan sapma"),
                    t("report.flatSharp", "Flat / sharp", "Pest / dik")});
            orderedPitches.forEach(entry -> {
                        AcademicStudyDocument.Statistics value = AcademicStudyDocument.Statistics.from(
                                entry.getValue());
                        deviationRows.add(new String[]{entry.getKey().isBlank()
                                ? t("report.unnamed", "Unnamed", "Adlandırılamayan") : entry.getKey(),
                                format(value.totalDurationSeconds(), 2) + " " + seconds,
                                cHc(value.meanCent()), cHc(value.medianCent()),
                                value.pestCount() + " / " + value.dikCount()});
                    });
            table(document, deviationRows.toArray(String[][]::new),
                    new int[]{28, 14, 24, 24, 10}, true, 7.8);
        }
    }

    private static Map<String, AcademicStudyDocument.Statistics> orderedDirectionStatistics(
            AcademicStudyDocument report) {
        LinkedHashMap<String, AcademicStudyDocument.Statistics> source =
                new LinkedHashMap<>(report.directionStatistics());
        LinkedHashMap<String, AcademicStudyDocument.Statistics> ordered = new LinkedHashMap<>();
        for (String direction : List.of("Çıkıcı", "İnici", "Segâh kalışı", "Yatay/Karma")) {
            AcademicStudyDocument.Statistics value = source.remove(direction);
            if (value != null && value.count() > 0) ordered.put(direction, value);
        }
        source.entrySet().stream().filter(entry -> entry.getValue().count() > 0)
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return ordered;
    }

    private void eventsSection(XWPFDocument document, AcademicStudyDocument report) {
        heading(document, section("5", "report.individualEvents",
                "Individual measurable events", "Ölçülebilir olayların tek tek dökümü"), 1);
        if (report.directionalSegahAnalysis()) {
            directionalEventsSection(document, report);
            return;
        }
        paragraph(document,
                t("report.prose.eventsIntro",
                        "An asterisk (*) marks events that do not meet the predefined stability criterion. Rows meeting exclusion criteria are retained for traceability but are not included in the primary descriptive statistics.",
                        "Yıldız (*) işareti, önceden tanımlanan kararlılık ölçütünü karşılamayan olayları belirtir. Dışlama ölçütlerini karşılayan satırlar izlenebilirlik amacıyla tabloda korunmuş, ancak birincil betimsel istatistiklerin hesaplanmasına dâhil edilmemiştir."),
                10, false, MUTED, 0, 5);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"#", t("report.pitch", "Pitch", "Perde"),
                t("report.time", "Time", "Zaman"), t("report.duration", "Duration", "Süre"), "Hz",
                t("report.positionCent", "Position C", "Konum C"),
                t("report.positionHc", "Position Hc", "Konum Hc"),
                t("report.deviationCent", "Deviation C", "Sapma C"),
                t("report.deviationHc", "Deviation Hc", "Sapma Hc"),
                t("report.direction", "Direction", "Yön"), t("report.type", "Type", "Tür"),
                t("report.interpretation", "Interpretation", "Yorum")});
        for (AcademicStudyDocument.Event event : report.measurableEvents()) {
            rows.add(new String[]{event.index() + (event.stable() ? "" : "*"),
                    localizedNoteName(event.noteName()), formatTime(event.startSeconds()), format(event.durationSeconds(), 2),
                    format(event.observedHz(), 3), format(event.relativeCent(), 2),
                    format(event.relativeHc(), 3), signed(event.deviationCent(), 2),
                    signed(event.deviationHc(), 3), dynamicLabel(event.direction()), dynamicLabel(event.eventType()),
                    event.included() ? localizedIntonation(event)
                            : t("report.excludedWithReason", "Excluded: {0}", "Dışlandı: {0}",
                            localizedReason(event.exclusionReason()))});
        }
        table(document, rows.toArray(String[][]::new),
                new int[]{6, 9, 9, 6, 8, 9, 8, 9, 8, 9, 9, 10}, true, 7.0);

        heading(document, t("report.eventBodyValues",
                "Entry, settled and exit values of the event body",
                "Olay gövdesinin giriş, yerleşik ve çıkış değerleri"), 2);
        List<String[]> details = new ArrayList<>();
        details.add(new String[]{"#", t("report.entryCent", "Entry C", "Giriş C"),
                t("report.settledCent", "Settled C", "Yerleşik C"),
                t("report.exitCent", "Exit C", "Çıkış C"),
                t("report.robustSpreadCent", "Robust spread C", "Sağlam yayılım C"),
                t("report.confidence", "Confidence", "Güven"),
                t("report.included", "Included", "Dahil")});
        for (AcademicStudyDocument.Event event : report.measurableEvents()) {
            details.add(new String[]{Integer.toString(event.index()), signed(event.entryDeviationCent(), 2),
                    signed(event.settledDeviationCent(), 2), signed(event.exitDeviationCent(), 2),
                    format(event.stabilityCent(), 2), percent(event.confidence()),
                    event.included() ? t("report.yes", "Yes", "Evet") : t("report.no", "No", "Hayır")});
        }
        table(document, details.toArray(String[][]::new), new int[]{8, 15, 17, 15, 21, 12, 12}, true, 8.5);
    }

    private void directionalGroupTable(XWPFDocument document, AcademicStudyDocument report) {
        paragraph(document,
                t("report.prose.directionalGroupStats",
                        "The mean shows the overall tendency of all events; the median is the typical middle event after sorting; and the duration-weighted mean gives more weight to longer Segâh performances. Smaller standard deviation and MAD indicate more similar performances, while larger values indicate greater variability.",
                        "Ortalama, bütün olayların toplam eğilimini; medyan, değerler sıralandığında ortada kalan tipik olayı; süre ağırlıklı ortalama ise uzun duyulan Segâh basışlarına daha fazla ağırlık veren sonucu gösterir. Standart sapma ve MAD küçüldükçe basışlar birbirine daha yakın, büyüdükçe daha değişkendir."),
                9.5, false, MUTED, 1, 5);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{t("report.usage", "Usage", "Kullanım"), "n",
                t("report.meanCentHc", "Mean C/Hc", "Ortalama C/Hc"),
                t("report.medianCentHc", "Median C/Hc", "Medyan C/Hc"),
                t("report.weightedMeanShort", "Duration-wtd. mean", "Süre ağ. ort."),
                t("report.sdMad", "SD / MAD", "SS / MAD"),
                t("report.minimumMaximumCentHc", "Lowest / highest C/Hc", "En pest / en dik C/Hc")});
        for (String label : List.of("Çıkıcı", "İnici", "Segâh kalışı", "Tümü")) {
            List<AcademicStudyDocument.Event> values = label.equals("Tümü")
                    ? report.measurableEvents().stream().filter(AcademicStudyDocument.Event::included).toList()
                    : report.measurableEvents().stream().filter(AcademicStudyDocument.Event::included)
                    .filter(value -> value.direction().equals(label)).toList();
            AcademicStudyDocument.Statistics statistics = AcademicStudyDocument.Statistics.from(values);
            rows.add(new String[]{dynamicLabel(label), Integer.toString(statistics.count()),
                    cHc(statistics.meanCent()), cHc(statistics.medianCent()),
                    cHc(statistics.weightedMeanCent()),
                    format(statistics.standardDeviationCent(), 2) + " / "
                            + format(medianAbsoluteDeviation(values), 2) + " C",
                    values.isEmpty() ? "—" : cHc(values.stream()
                            .mapToDouble(AcademicStudyDocument.Event::deviationCent).min().orElse(0))
                            + " ↔ " + cHc(values.stream()
                            .mapToDouble(AcademicStudyDocument.Event::deviationCent).max().orElse(0))});
        }
        table(document, rows.toArray(String[][]::new),
                new int[]{14, 5, 17, 17, 17, 12, 18}, true, 7.2);
        List<String> small = List.of("Çıkıcı", "İnici", "Segâh kalışı").stream()
                .filter(label -> report.measurableEvents().stream()
                        .filter(AcademicStudyDocument.Event::included)
                        .filter(value -> value.direction().equals(label)).count() < 5)
                .toList();
        if (!small.isEmpty()) {
            paragraph(document, t("report.prose.smallDirectionalSample",
                            "Sample-size warning: fewer than five reliable events are available in {0}. These rows describe the recording and must not be interpreted as a general performance rule.",
                            "Örneklem uyarısı: {0} grubunda beşten az güvenilir olay vardır. Bu satırlar kaydı betimler; genel bir icra kuralı olarak yorumlanmamalıdır.",
                            small.stream().map(DocxStudyReportExporter::dynamicLabel)
                                    .collect(java.util.stream.Collectors.joining(", "))),
                    9, false, RED, 3, 5);
        }
        paragraph(document, t("report.prose.allRow",
                        "The All row is not the simple mean of the three group means. It is recalculated by using every accepted event once, so a group with more events contributes its natural weight to the overall result.",
                        "'Tümü' satırı üç satırdaki ortalamaların basit ortalaması değildir. Kabul edilen her olay bir kez kullanılarak yeniden hesaplanmıştır; bu nedenle olay sayısı fazla olan grup genel sonuca doğal ağırlığıyla katılır."),
                9, false, MUTED, 2, 5);
    }

    private static double medianAbsoluteDeviation(List<AcademicStudyDocument.Event> values) {
        if (values.isEmpty()) return 0.0;
        double[] ordered = values.stream().mapToDouble(AcademicStudyDocument.Event::deviationCent)
                .sorted().toArray();
        double median = ordered.length % 2 == 0
                ? (ordered[ordered.length / 2 - 1] + ordered[ordered.length / 2]) / 2.0
                : ordered[ordered.length / 2];
        double[] deviations = java.util.Arrays.stream(ordered)
                .map(value -> Math.abs(value - median)).sorted().toArray();
        return deviations.length % 2 == 0
                ? (deviations[deviations.length / 2 - 1] + deviations[deviations.length / 2]) / 2.0
                : deviations[deviations.length / 2];
    }

    private void directionalEventsSection(XWPFDocument document, AcademicStudyDocument report) {
        paragraph(document, t("report.prose.directionalEvents",
                        "Each row shows one Segâh usage included in the primary statistics. The Observed path column explains the pitch sequence on which the directional decision is based. Stable F0 segments split by the engine within the same musical usage are not counted again; they are retained separately in Appendix A for audit.",
                        "Her satır birincil istatistiğe alınan tek bir Segâh kullanımını gösterir. 'İzlenen yol' sütunu yön kararının hangi perde dizilimine dayandığını açıklar. Aynı müziksel kullanım içinde motorun ayrı parçalara böldüğü kararlı F0 bölümleri yeniden sayılmaz; denetlenebilmeleri için Ek A'da ayrıca korunur."),
                10, false, MUTED, 0, 5);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"#", t("report.class", "Class", "Sınıf"),
                t("report.observedPath", "Observed path", "İzlenen yol"),
                t("report.previous", "Previous", "Önceki"),
                t("report.next", "Next", "Sonraki"), t("report.time", "Time", "Zaman"),
                t("report.duration", "Duration", "Süre"), "Hz",
                t("report.deviationCentHc", "Deviation C/Hc", "Sapma C/Hc"),
                t("report.result", "Result", "Sonuç")});
        for (AcademicStudyDocument.Event event : report.measurableEvents()) {
            AcademicStudyDocument.DirectionalContext context = report.directionalContext(event.index());
            rows.add(new String[]{Integer.toString(event.index()), dynamicLabel(event.direction()),
                    localizedPath(context.pathLabel()), blankDash(context.previousAnchor()),
                    blankDash(context.nextAnchor()), formatTime(event.startSeconds()),
                    format(event.durationSeconds(), 2), format(event.observedHz(), 3),
                    cHc(event.deviationCent()), event.included() ? dynamicLabel(event.intonationLabel())
                    : t("report.excluded", "Excluded", "Dışlandı")});
        }
        table(document, rows.toArray(String[][]::new),
                new int[]{5, 11, 20, 9, 9, 9, 7, 9, 11, 10}, true, 6.8);

        heading(document, t("report.eventMeasurementConfidence",
                "Event measurement and confidence details",
                "Olayların ölçüm ve güven ayrıntıları"), 2);
        List<String[]> details = new ArrayList<>();
        details.add(new String[]{"#", t("report.observedPositionCentHc",
                "Observed position C/Hc", "Gözlenen konum C/Hc"),
                t("report.overallDeviationCentHc", "Overall deviation C/Hc", "Genel sapma C/Hc"),
                t("report.localDugahCentHc", "Relative to local Dügâh C/Hc", "Yerel Dügâh'a göre C/Hc"),
                t("report.f0Confidence", "F0 confidence", "F0 güveni"),
                t("report.classConfidence", "Class confidence", "Sınıf güveni"),
                t("report.includedReason", "Included / reason", "Dahil / gerekçe")});
        for (AcademicStudyDocument.Event event : report.measurableEvents()) {
            AcademicStudyDocument.DirectionalContext context = report.directionalContext(event.index());
            String local = Double.isFinite(context.localDeviationCent())
                    ? cHc(context.localDeviationCent()) : "—";
            details.add(new String[]{Integer.toString(event.index()), cHc(event.relativeCent()),
                    cHc(event.deviationCent()), local, percent(event.confidence()),
                    Double.isFinite(context.classificationConfidence())
                            ? percent(context.classificationConfidence()) : "—",
                    event.included() ? t("report.yes", "Yes", "Evet")
                            : t("report.noWithReason", "No — {0}", "Hayır — {0}",
                            localizedReason(event.exclusionReason()))});
        }
        table(document, details.toArray(String[][]::new),
                new int[]{6, 18, 17, 20, 11, 12, 16}, true, 7.1);
    }

    private void profileSection(XWPFDocument document, AcademicStudyDocument report) throws IOException {
        heading(document, section("6", "report.firstFourSimilarity",
                "Scale / çeşni similarity of the first four pitches from the tonic",
                "Karardan başlayan ilk dört sesin dizi / çeşni benzerliği"), 1);
        paragraph(document,
                t("report.prose.firstFourComparison",
                        "Two evidence layers are deliberately kept separate. The global pitch inventory preserves every dominant and secondary mode heard in the tonic-to-fourth domain; distinct peaks are never collapsed into an artificial average. The inventory-ranking graph compares this one fixed, multimodal evidence set continuously with every displayed theoretical first-four-pitch reference. Exactly the same observed centres are used for every row, so no profile may select, omit or rename evidence in its own favour. Candidate-independent, time-ordered phrase paths extracted from the real F0 sequence are then evaluated separately in the ordered-phrase graph. When those paths are partial, the graph is explicitly labelled as partial/mixed evidence rather than being presented as proof of a complete tetrachord phrase. Duration, repetition, confidence and stability weight the common evidence, while repeated notes are capped so that one phrase cannot dominate the result. Neither graph is an automatic makam diagnosis.",
                        "İki kanıt katmanı bilinçli olarak birbirinden ayrı tutulur. Genel perde envanteri, karar ile dördüncü derece arasındaki alanda duyulan baskın ve ikincil tepelerin tümünü korur; ayrı tepeler yapay bir ortalamada birleştirilmez. Envanter sıralaması grafiği, bu tek ve sabit çok tepeli kanıt kümesini gösterilen bütün kuramsal ilk-dört-ses referanslarıyla sürekli uzaklık üzerinden karşılaştırır. Her satırda tam olarak aynı gözlenen merkezler kullanılır; hiçbir profil kendi lehine kanıt seçemez, atlayamaz veya yeniden adlandıramaz. Gerçek F0 zaman dizisinden, aday profillerden bağımsız olarak çıkarılan sıralı cümle yolları ise ikinci grafikte ayrıca değerlendirilir. Bu yollar kısmi olduğunda grafik tam bir dörtlü cümlesinin kanıtı gibi sunulmaz; açıkça kısmi/karma kanıt olarak işaretlenir. Süre, tekrar, güven ve kararlılık ortak kanıtı ağırlıklandırır; aynı sesin tekrarları tek bir cümlenin sonucu orantısız biçimde baskılamaması için sınırlandırılır. İki grafik de otomatik makam teşhisi değildir."),
                10, false, INK, 0, 6);

        boolean explicitPhraseContract = hasExplicitFirstFourEvidence(report);
        if (explicitPhraseContract) firstFourEvidenceLayers(document, report);

        List<AcademicStudyDocument.ProfileComparison> comparisonProfiles =
                report.profileComparisons().stream()
                        .filter(value -> !isBuselikComparisonProfile(value.profileName()))
                        .toList();

        if (comparisonProfiles.isEmpty()) {
            paragraph(document, t("report.noProfileEvidence",
                            "No comparison was produced because there was insufficient tonic-aligned interval evidence.",
                            "Yeterli karara hizalı aralık kanıtı bulunmadığı için karşılaştırma üretilmedi."),
                    10, true, MUTED, 0, 8);
            return;
        }

        keepLastParagraphWithNext(document);
        AcademicStudyDocument.ProfileComparison evidence = comparisonProfiles.stream()
                .filter(value -> !value.firstFourDegrees().isEmpty())
                .findFirst().orElse(comparisonProfiles.get(0));
        boolean completeEvidence = explicitPhraseContract
                ? report.firstFourPhraseEvidence().complete()
                : evidence.evidenceSufficient();
        if (!completeEvidence) {
            String warning = explicitPhraseContract
                    ? report.firstFourPhraseEvidence().partialOrMixed()
                    ? firstFourText(
                    "Partial/mixed evidence: the ordered phrase evidence does not contain at least three distinct degrees including the tonic or the fourth. The ranked graph below is therefore an inventory-similarity ranking, not proof of an ordered tetrachord phrase.",
                    "Kısmi/karma kanıt: sıralı cümle kanıtında karar veya dördüncü dereceyi içeren en az üç farklı derece bulunmamaktadır. Bu nedenle aşağıdaki sıralı grafik bir envanter benzerliği sıralamasıdır; sıralı dörtlü cümlesinin kanıtı değildir.")
                    : firstFourText(
                    "Insufficient ordered tetrachord evidence: the ranked graph below uses the global first-four-pitch inventory and is not proof of an ordered tetrachord phrase.",
                    "Yetersiz sıralı dörtlü kanıt: aşağıdaki sıralı grafik genel ilk-dört-ses perde envanterini kullanır ve sıralı dörtlü cümlesinin kanıtı değildir.")
                    : localizedReason(nonBlank(evidence.evidenceSummary(),
                    t("report.insufficientFirstFourEvidence",
                            "There was insufficient stable interval evidence to compare the first four degrees.",
                            "İlk dört dereceyi karşılaştırmak için yeterli kararlı aralık kanıtı bulunmadı.")));
            paragraph(document, warning, 10, true, MUTED, 0, 8);
        }
        if (!evidence.evidenceSufficient() && !explicitPhraseContract) {
            paragraph(document, localizedReason(nonBlank(evidence.evidenceSummary(),
                            t("report.insufficientFirstFourEvidence",
                                    "There was insufficient stable interval evidence to compare the first four degrees.",
                                    "İlk dört dereceyi karşılaştırmak için yeterli kararlı aralık kanıtı bulunmadı."))),
                    10, true, MUTED, 0, 8);
        }

        heading(document, firstFourText(
                "Similarity based on the global pitch inventory",
                "Genel perde envanterine göre benzerlik"), 2);
        image(document, charts.profileRanking(report),
                "ilk-dort-ses-envanter-benzerligi.png", 6.35);

        heading(document, firstFourText(
                "Similarity based on ordered phrase evidence",
                "Sıralı cümle kanıtına göre benzerlik"), 2);
        paragraph(document, firstFourText(
                        "This graph uses only the candidate-independent degree peaks retained from chronological phrase paths. The same observed path evidence is compared continuously with every displayed reference. A partial/mixed label means that the available path does not cover a literal four-degree course and must be interpreted cautiously.",
                        "Bu grafik yalnız kronolojik cümle yollarından, aday profillerden bağımsız biçimde korunan derece tepelerini kullanır. Aynı gözlenen yol kanıtı, gösterilen her referansla sürekli uzaklık üzerinden karşılaştırılır. Kısmi/karma etiketi, eldeki yolun dört dereceli tam bir seyri kapsamadığını ve sonucun dikkatle yorumlanması gerektiğini belirtir."),
                9.5, false, MUTED, 0, 4);
        image(document, charts.orderedPhraseProfileRanking(report),
                "ilk-dort-ses-sirali-cumle-benzerligi.png", 6.35);

        List<AcademicStudyDocument.ProfileComparison> orderedFirstFour = comparisonProfiles.stream()
                .filter(value -> !value.firstFourDegrees().isEmpty())
                .sorted(Comparator.comparing((AcademicStudyDocument.ProfileComparison value) ->
                                value.firstFourScorePercent() < 80.0)
                        .thenComparing(Comparator.comparingDouble(
                                AcademicStudyDocument.ProfileComparison::firstFourScorePercent).reversed())
                        .thenComparing(AcademicStudyDocument.ProfileComparison::profileName))
                .toList();
        if (orderedFirstFour.isEmpty()) {
            paragraph(document, t("report.noFirstFourMatchEvidence",
                            "No sufficient matching evidence could be produced for the first four degrees.",
                            "İlk dört derece için yeterli eşleşme kanıtı üretilemedi."),
                    10, true, MUTED, 0, 8);
            return;
        }
        List<String[]> firstFourRanking = new ArrayList<>();
        firstFourRanking.add(new String[]{t("report.rank", "Rank", "Sıra"),
                t("report.scaleCesniGroup", "Scale / çeşni group", "Dizi / çeşni grubu"),
                t("report.degreeTwoToFourSimilarity", "2nd–4th degree similarity", "2.-4. derece benzerliği"),
                t("report.evidence", "Evidence", "Kanıt"),
                t("report.result", "Result", "Sonuç")});
        for (int index = 0; index < orderedFirstFour.size(); index++) {
            AcademicStudyDocument.ProfileComparison value = orderedFirstFour.get(index);
            int observedDiscriminating = Math.max(0, value.firstFourObservedCount() - 1);
            int discriminatingCount = (int) value.firstFourDegrees().stream()
                    .filter(degree -> degree.degree() > 1).count();
            firstFourRanking.add(new String[]{Integer.toString(index + 1), value.profileName(),
                    "%" + format(value.firstFourScorePercent(), 1),
                    t("report.firstFourEvidenceCount",
                            "1 tonic anchor + {0}/{1} observed",
                            "1 karar ankrajı + {0}/{1} gözlenen",
                            observedDiscriminating, discriminatingCount),
                    value.firstFourScorePercent() < 80.0
                            ? t("report.lowSimilarity", "Low similarity", "Az benzerlik")
                            : t("report.similarity", "Similarity", "Benzerlik")});
        }
        table(document, firstFourRanking.toArray(String[][]::new),
                new int[]{7, 25, 23, 27, 18}, true, 7.7);

        AcademicStudyDocument.ProfileComparison closest = orderedFirstFour.get(0);
        FirstFourGroup closestGroup = firstFourGroup(List.of(closest));
        if (closestGroup.profileNames().size() > 1 || closestGroup.canonicalLabel().isPresent()) {
            heading(document, t("report.closestFirstFourGroup",
                    "Closest first-four-pitch group: {0}", "En yakın ilk-dört-ses grubu: {0}",
                    closestGroup.label()), 3);
            paragraph(document, t("report.prose.closestFirstFourGroup",
                            "Makam profiles evaluated together within the {0} group: {1}. These profiles have the same first four theoretical degrees and cannot be distinguished from one another by this evidence alone.",
                            "{0} grubu içinde birlikte değerlendirilen makam profilleri: {1}. Bu profiller ilk dört kuramsal derece bakımından aynıdır ve yalnız bu kanıtla birbirinden ayırt edilemez.",
                            closestGroup.label(), closestGroup.memberLabel()),
                    10, true, MUTED, 0, 5);
        } else {
            heading(document, t("report.closestFirstFourProfile",
                    "Closest first-four-pitch profile: {0}", "En yakın ilk-dört-ses profili: {0}",
                    closestGroup.label()), 3);
        }
        List<String[]> firstFourEvidence = new ArrayList<>();
        firstFourEvidence.add(new String[]{t("report.degree", "Degree", "Derece"),
                t("report.theoreticalReference", "Theoretical", "Kuramsal"),
                t("report.measuredValue", "Observed", "Gözlenen"),
                t("report.deviation", "Deviation", "Sapma"), t("report.evidence", "Evidence", "Kanıt")});
        for (AcademicStudyDocument.DegreeComparison degree : closest.firstFourDegrees()) {
            boolean tonicAnchor = degree.degree() == 1;
            String observed = tonicAnchor
                    ? "0.00 C / 0.000 Hc (" + t("report.anchor", "anchor", "ankraj") + ")"
                    : degree.observed()
                    ? signed(degree.observedCent(), 2) + " C / " + signed(degree.observedHc(), 3) + " Hc"
                    : t("report.notFoundWithinOneHc", "Not found within ±1 Hc", "±1 Hc içinde bulunamadı");
            String residual = tonicAnchor
                    ? t("report.notIncludedInScore", "Not included in score", "Skora katılmaz")
                    : degree.observed()
                    ? signed(degree.residualCent(), 2) + " C / " + signed(degree.residualHc(), 3) + " Hc"
                    : "—";
            firstFourEvidence.add(new String[]{degree.degree() == 1
                    ? "1 (" + t("report.tonic", "tonic", "karar") + ")" : Integer.toString(degree.degree()),
                    signed(degree.theoreticalCent(), 2) + " C / " + signed(degree.theoreticalHc(), 3) + " Hc",
                    observed, residual,
                    tonicAnchor
                            ? (report.tonic().userConfirmed()
                            ? t("report.userConfirmedTonicAnchor", "User-confirmed tonic anchor", "Kullanıcı onaylı karar ankrajı")
                            : t("report.automaticTonicAnchor", "Tonic anchor (automatic candidate)", "Karar ankrajı (otomatik aday)"))
                            : degree.observed()
                            ? t("report.eventDurationEvidence", "{0} events / {1} {2}", "{0} olay / {1} {2}",
                            degree.eventCount(), format(degree.totalDurationSeconds(), 2),
                            t("unit.secondsShort", "s", "sn"))
                            : t("report.noEvidence", "No evidence", "Kanıt yok")});
        }
        table(document, firstFourEvidence.toArray(String[][]::new),
                new int[]{12, 24, 28, 22, 14}, true, 7.7);

        if (explicitPhraseContract) candidateResidualAudit(document, report);

    }

    private static boolean hasExplicitFirstFourEvidence(AcademicStudyDocument report) {
        AcademicStudyDocument.FirstFourPhraseEvidence phrase = report.firstFourPhraseEvidence();
        return !report.firstFourInventoryPeaks().isEmpty()
                || !phrase.degreePeaks().isEmpty()
                || phrase.qualifyingPhraseCount() > 0
                || phrase.completePhraseCount() > 0
                || phrase.partialPhraseCount() > 0
                || phrase.evidenceEventCount() > 0;
    }

    /**
     * Defensive report-level guard.  Bûselik remains a valid pitch name and
     * interval boundary elsewhere in the report, but it is deliberately not a
     * first-four-pitch comparison candidate.
     */
    private static boolean isBuselikComparisonProfile(String profileName) {
        if (profileName == null) return false;
        String plain = profileName.strip()
                .replace('û', 'u').replace('Û', 'U')
                .replace('ü', 'u').replace('Ü', 'U');
        return plain.equalsIgnoreCase("Buselik");
    }

    /** Writes the global pitch inventory and ordered phrase evidence as two independent layers. */
    private void firstFourEvidenceLayers(XWPFDocument document, AcademicStudyDocument report) {
        heading(document, firstFourText(
                "Global pitch inventory — dominant and secondary peaks",
                "Genel perde envanteri — baskın ve ikincil tepeler"), 2);
        paragraph(document, firstFourText(
                        "This inventory describes which stable pitch modes were heard in the tonic-to-fourth domain. It is not the tetrachord phrase sample. Separate modes are retained as separate rows rather than being replaced by one average pitch.",
                        "Bu envanter, karar ile dördüncü derece arasındaki alanda hangi kararlı perde tepelerinin duyulduğunu gösterir; dörtlü cümle örneklemi değildir. Ayrı tepeler tek bir ortalama perdeye dönüştürülmeden ayrı satırlarda korunur."),
                9, false, MUTED, 0, 4);
        if (report.firstFourInventoryPeaks().isEmpty()) {
            paragraph(document, firstFourText(
                            "No stable global pitch peak was retained in the first-four domain.",
                            "İlk-dört-ses alanında kararlı bir genel perde tepesi korunmadı."),
                    9, true, MUTED, 0, 5);
        } else {
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{t("report.degree", "Degree", "Derece"),
                    firstFourText("Role", "Rol"),
                    t("report.observedPositionCentHc", "Observed position C/Hc", "Gözlenen konum C/Hc"),
                    t("report.evidence", "Evidence", "Kanıt"),
                    t("report.f0Confidence", "F0 confidence", "F0 güveni")});
            for (AcademicStudyDocument.FirstFourPeak peak : report.firstFourInventoryPeaks()) {
                rows.add(new String[]{peak.degree() > 0 ? Integer.toString(peak.degree()) : "—",
                        peak.dominant()
                                ? firstFourText("Dominant peak", "Baskın tepe")
                                : firstFourText("Secondary peak", "İkincil tepe"),
                        signed(peak.centerCent(), 2) + " C / " + signed(peak.centerHc(), 3) + " Hc",
                        t("report.eventDurationEvidence", "{0} events / {1} {2}",
                                "{0} olay / {1} {2}", peak.eventCount(),
                                format(peak.totalDurationSeconds(), 2),
                                t("unit.secondsShort", "s", "sn")),
                        percent(peak.meanConfidence())});
            }
            table(document, rows.toArray(String[][]::new),
                    new int[]{10, 19, 28, 27, 16}, true, 7.5);
        }

        AcademicStudyDocument.FirstFourPhraseEvidence phrase = report.firstFourPhraseEvidence();
        heading(document, firstFourText(
                "Ordered tetrachord phrase evidence", "Sıralı dörtlü cümle kanıtı"), 2);
        String status = phrase.complete()
                ? firstFourText("Complete evidence", "Tam kanıt")
                : phrase.partialOrMixed()
                ? firstFourText("Partial/mixed evidence", "Kısmi/karma kanıt")
                : firstFourText("Insufficient evidence", "Yetersiz kanıt");
        paragraph(document, firstFourText(
                        "Status: {0}. Qualifying phrases: {1}; literal four-degree paths: {2}; partial paths: {3}; events admitted to percentage scoring: {4}.",
                        "Durum: {0}. Nitelikli cümle: {1}; dört dereceli tam yol: {2}; kısmi yol: {3}; yüzde hesabına alınan olay: {4}.",
                        status, phrase.qualifyingPhraseCount(), phrase.completePhraseCount(),
                        phrase.partialPhraseCount(), phrase.evidenceEventCount()),
                9, true, phrase.complete() ? INK : MUTED, 0, 4);
        if (!phrase.degreePeaks().isEmpty()) {
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{t("report.degree", "Degree", "Derece"),
                    firstFourText("Role", "Rol"),
                    t("report.measuredValue", "Observed", "Gözlenen"),
                    t("report.evidence", "Evidence", "Kanıt"),
                    t("report.f0Confidence", "F0 confidence", "F0 güveni")});
            for (AcademicStudyDocument.FirstFourPeak peak : phrase.degreePeaks()) {
                rows.add(new String[]{Integer.toString(peak.degree()),
                        peak.dominant()
                                ? firstFourText("Dominant path peak", "Baskın yol tepesi")
                                : firstFourText("Secondary path peak", "İkincil yol tepesi"),
                        signed(peak.centerCent(), 2) + " C / " + signed(peak.centerHc(), 3) + " Hc",
                        t("report.eventDurationEvidence", "{0} events / {1} {2}",
                                "{0} olay / {1} {2}", peak.eventCount(),
                                format(peak.totalDurationSeconds(), 2),
                                t("unit.secondsShort", "s", "sn")),
                        percent(peak.meanConfidence())});
            }
            table(document, rows.toArray(String[][]::new),
                    new int[]{10, 22, 28, 25, 15}, true, 7.5);
        }
    }

    /**
     * Candidate-independent residual audit.  It proves that every reference is
     * evaluated against the same observed F0 centres and does not introduce a
     * second report-only peak selector.
     */
    private void candidateResidualAudit(XWPFDocument document, AcademicStudyDocument report) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{t("report.scaleCesniGroup", "Scale / çeşni group", "Dizi / çeşni grubu"),
                t("report.degree", "Degree", "Derece"),
                firstFourText("Inventory peak", "Envanter tepesi"),
                t("report.theoreticalReference", "Theoretical", "Kuramsal"),
                t("report.measuredValue", "Observed", "Gözlenen"),
                t("report.deviation", "Deviation", "Sapma")});
        for (AcademicStudyDocument.ProfileComparison profile : report.profileComparisons()) {
            if (isBuselikComparisonProfile(profile.profileName())) continue;
            for (AcademicStudyDocument.DegreeComparison degree : profile.firstFourDegrees()) {
                if (degree.degree() <= 1) continue;
                List<AcademicStudyDocument.FirstFourPeak> peaks =
                        report.firstFourInventoryPeaks().stream()
                                .filter(peak -> peak.degree() == degree.degree()).toList();
                for (AcademicStudyDocument.FirstFourPeak peak : peaks) {
                    double residualCent = peak.centerCent() - degree.theoreticalCent();
                    rows.add(new String[]{profile.profileName(), Integer.toString(degree.degree()),
                            peak.dominant()
                                    ? firstFourText("Dominant inventory peak", "Baskın envanter tepesi")
                                    : firstFourText("Secondary inventory peak", "İkincil envanter tepesi"),
                            signed(degree.theoreticalCent(), 2) + " C / "
                                    + signed(degree.theoreticalHc(), 3) + " Hc",
                            signed(peak.centerCent(), 2) + " C / "
                                    + signed(peak.centerHc(), 3) + " Hc",
                            signed(residualCent, 2) + " C / "
                                    + signed(residualCent / AcademicStudyDocument.HC_CENT, 3) + " Hc"});
                }
            }
        }
        if (rows.size() <= 1) return;
        heading(document, firstFourText(
                "Same observed evidence — continuous residual audit",
                "Aynı gözlenen kanıt — sürekli artık denetimi"), 3);
        paragraph(document, firstFourText(
                        "Every profile is audited against the same global first-four inventory peaks used by the percentage ranking. Observed inventory values remain identical from one reference row to the next; only the theoretical value and its signed residual change. Thus a value at 8.95 Hc is −0.05 Hc from 9 Hc and +0.95 Hc from 8 Hc. Ordered phrase peaks are reported separately and are not substituted into this inventory audit.",
                        "Her profil, yüzde sıralamasında kullanılan aynı genel ilk-dört-ses envanter tepelerine göre denetlenir. Gözlenen envanter değerleri bir referans satırından diğerine değişmez; yalnız kuramsal değer ve ona göre hesaplanan imzalı artık değişir. Böylece 8,95 Hc'lik bir değer 9 Hc'ye göre −0,05 Hc, 8 Hc'ye göre +0,95 Hc olur. Sıralı cümle tepeleri ayrıca raporlanır ve bu envanter denetiminde onların yerine kullanılmaz."),
                9, false, MUTED, 0, 4);
        table(document, rows.toArray(String[][]::new),
                new int[]{19, 7, 16, 20, 20, 18}, true, 6.9);
    }

    private static String firstFourGroupKey(AcademicStudyDocument.ProfileComparison value) {
        if (value.firstFourSignature() != null && !value.firstFourSignature().isBlank()) {
            return value.firstFourSignature();
        }
        return value.firstFourDegrees().stream()
                .map(degree -> Long.toString(Math.round(degree.theoreticalCent() * 1000.0)))
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static FirstFourGroup firstFourGroup(List<AcademicStudyDocument.ProfileComparison> values) {
        AcademicStudyDocument.ProfileComparison representative = values.get(0);
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (AcademicStudyDocument.ProfileComparison value : values) {
            names.add(value.profileName());
            names.addAll(value.firstFourIndistinguishableProfiles());
        }
        return new FirstFourGroup(representative, List.copyOf(names));
    }

    private record FirstFourGroup(AcademicStudyDocument.ProfileComparison representative,
                                  List<String> profileNames) {
        private java.util.Optional<String> canonicalLabel() {
            return FirstFourMakamGroupCatalog.canonicalLabel(
                    representative.firstFourDegrees().stream()
                            .map(AcademicStudyDocument.DegreeComparison::theoreticalHc).toList(),
                    profileNames);
        }

        private String label() {
            return FirstFourMakamGroupCatalog.displayLabel(
                    representative.firstFourDegrees().stream()
                            .map(AcademicStudyDocument.DegreeComparison::theoreticalHc).toList(),
                    profileNames);
        }

        private String memberLabel() { return String.join(", ", profileNames); }
    }

    private void limitationsSection(XWPFDocument document, AcademicStudyDocument report) {
        heading(document, report.directionalSegahAnalysis()
                ? section("6", "report.academicInterpretationLimits",
                "Academic interpretation and limitations", "Akademik yorum ve sınırlılıklar")
                : section("7", "report.academicInterpretationLimits",
                "Academic interpretation and limitations", "Akademik yorum ve sınırlılıklar"), 1);
        if (report.limitations().isEmpty()) {
            bullet(document, t("report.limit.polyphonic",
                    "In polyphonic choir or instrumental textures, the result represents the dominant melodic F0 track in the selected channel and cannot be attributed directly to one performer.",
                    "Çok sesli koro veya çalgı dokusunda sonuç, seçilen kanaldaki baskın melodik F0 izini temsil eder; tekil bir icracıya doğrudan atfedilemez."));
            bullet(document, t("report.limit.lowConfidence",
                    "Brief and low-confidence contacts are retained for audit but excluded from the primary statistical summaries.",
                    "Kısa süreli ve düşük güvenli temaslar denetim amacıyla korunmuş, birincil istatistiksel özetlerden dışlanmıştır."));
            bullet(document, t("report.limit.profileNotDiagnosis",
                    "Interval-profile similarity does not by itself verify makam identity; interpret it together with musicological context and melodic-course evidence.",
                    "Aralık profili benzerliği makam kimliğini tek başına doğrulamaz; sonuçlar müzikolojik bağlam ve seyir özellikleriyle birlikte yorumlanmalıdır."));
        } else report.limitations().forEach(note -> bullet(document, localizedReason(note)));
        heading(document, t("report.reproducibility", "Reproducibility", "Tekrarlanabilirlik"), 2);
        table(document, new String[][]{
                {t("report.fileSha256", "File SHA-256", "Dosya SHA-256"), blankDash(report.source().sha256())},
                {t("report.application", "Application", "Uygulama"), report.source().applicationVersion()},
                {t("report.engine", "Analysis engine", "Motor"), report.source().engineVersion()},
                {t("report.referenceSystem", "Reference system", "Referans sistemi"), report.theory().referenceSystem()},
                {t("report.tonicSelection", "Tonic selection", "Karar seçimi"), dynamicLabel(report.tonic().method())
                        + (report.tonic().userConfirmed()
                        ? t("report.confirmedSuffix", " / confirmed", " / onaylı")
                        : t("report.candidateSuffix", " / candidate", " / aday"))},
                {t("report.generatedAt", "Report generated", "Rapor zamanı"),
                        ReportLocalization.dateTime(report.generatedAt())}
        }, new int[]{26, 74}, false);
    }

    private void contactsAppendix(XWPFDocument document, AcademicStudyDocument report) {
        // The directional report has a compact appendix. Let it follow the short
        // repeatability table so the preceding page is not left almost empty.
        // Ordinary reports keep their established appendix-on-a-new-page layout.
        if (!report.directionalSegahAnalysis()) pageBreak(document);
        heading(document, t("report.appendixAllContacts",
                "Appendix A. All canonical target-pitch contacts",
                "Ek A. Bütün kanonik hedef-perde temasları"), 1);
        paragraph(document,
                t("report.appendixContactsDescription",
                        "This appendix contains all canonical target-pitch events, including brief and transitional contacts. Brief contacts are shown to audit boundary crossings and possible false positives.",
                        "Bu ek kısa ve geçici temaslar dahil bütün kanonik hedef-perde olaylarını içerir. Kısa temaslar sınır geçişlerini ve olası yanlış pozitifleri denetlemek için gösterilir."),
                9, false, MUTED, 0, 4);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"#", t("report.pitch", "Pitch", "Perde"),
                t("report.time", "Time", "Zaman"), t("report.duration", "Duration", "Süre"),
                "Hz", t("report.positionC", "Position C", "Konum C"),
                t("report.positionHc", "Position Hc", "Konum Hc"),
                t("report.deviationC", "Deviation C", "Sapma C"),
                t("report.deviationHc", "Deviation Hc", "Sapma Hc"),
                t("report.type", "Type", "Tür"), t("report.included", "Included", "Dahil")});
        for (AcademicStudyDocument.Event event : report.contacts()) {
            rows.add(new String[]{Integer.toString(event.index()), localizedNoteName(event.noteName()),
                    formatTime(event.startSeconds()),
                    format(event.durationSeconds(), 2), format(event.observedHz(), 3),
                    format(event.relativeCent(), 2), format(event.relativeHc(), 3),
                    signed(event.deviationCent(), 2), signed(event.deviationHc(), 3),
                    dynamicLabel(event.eventType()), event.included()
                    ? t("report.yes", "Yes", "Evet") : t("report.no", "No", "Hayır")});
        }
        table(document, rows.toArray(String[][]::new),
                new int[]{6, 9, 10, 7, 9, 10, 9, 10, 9, 13, 8}, true, 7.1);
    }

    private void addHeader(XWPFDocument document, AcademicStudyDocument report) {
        XWPFHeader header = document.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
        XWPFParagraph paragraph = header.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        run(paragraph, "MakamLab - " + report.theory().makamName() + " / " + report.theory().targetNote(),
                8, false, MUTED);
        CTPBdr borders = paragraphProperties(paragraph).isSetPBdr()
                ? paragraphProperties(paragraph).getPBdr() : paragraphProperties(paragraph).addNewPBdr();
        CTBorder bottom = borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom();
        bottom.setVal(STBorder.SINGLE);
        bottom.setSz(BigInteger.valueOf(4));
        bottom.setColor(BORDER);
    }

    private void addFooter(XWPFDocument document) {
        XWPFFooter footer = document.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
        XWPFParagraph paragraph = footer.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.RIGHT);
        run(paragraph, t("report.page", "Page", "Sayfa") + " ", 8, false, MUTED);
        XWPFRun pageRun = paragraph.createRun();
        configureRun(pageRun, 8, false, MUTED);
        CTR ctr = pageRun.getCTR();
        CTFldChar begin = ctr.addNewFldChar(); begin.setFldCharType(STFldCharType.BEGIN);
        CTText instruction = ctr.addNewInstrText(); instruction.setStringValue(" PAGE ");
        CTFldChar separate = ctr.addNewFldChar(); separate.setFldCharType(STFldCharType.SEPARATE);
        ctr.addNewT().setStringValue("1");
        CTFldChar end = ctr.addNewFldChar(); end.setFldCharType(STFldCharType.END);
    }

    private void heading(XWPFDocument document, String text, int level) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(level == 1 ? 250 : 150);
        paragraph.setSpacingAfter(80);
        paragraph.setKeepNext(true);
        run(paragraph, text, level == 1 ? 18 : 13, true, INK);
    }

    private void paragraph(XWPFDocument document, String text, double size, boolean bold,
                           String color, int before, int after) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(before * 20);
        paragraph.setSpacingAfter(after * 20);
        paragraph.setSpacingBetween(1.12);
        run(paragraph, text, size, bold, color);
    }

    private static void keepLastParagraphWithNext(XWPFDocument document) {
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        if (!paragraphs.isEmpty()) paragraphs.get(paragraphs.size() - 1).setKeepNext(true);
    }

    private void bullet(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(360);
        paragraph.setIndentationHanging(220);
        paragraph.setSpacingAfter(50);
        run(paragraph, "•  " + text, 9.5, false, INK);
    }

    private void image(XWPFDocument document, BufferedImage image, String name, double widthInches) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        double heightInches = widthInches * image.getHeight() / image.getWidth();
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingBefore(100); paragraph.setSpacingAfter(100);
        XWPFRun run = paragraph.createRun();
        try {
            run.addPicture(new ByteArrayInputStream(bytes.toByteArray()), Document.PICTURE_TYPE_PNG,
                    name, Units.toEMU(widthInches * 72.0), Units.toEMU(heightInches * 72.0));
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException exception) {
            throw new IOException(t("report.chartInsertFailed",
                    "The chart could not be added to the DOCX file: {0}",
                    "Grafik DOCX dosyasına eklenemedi: {0}", name), exception);
        }
    }

    private void table(XWPFDocument document, String[][] rows, int[] widths, boolean firstRowHeader) {
        table(document, rows, widths, firstRowHeader, 8.5);
    }

    private void table(XWPFDocument document, String[][] rows, int[] widths,
                       boolean firstRowHeader, double fontSize) {
        if (rows.length == 0) return;
        XWPFTable table = document.createTable(rows.length, rows[0].length);
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);
        CTTblPr properties = table.getCTTbl().getTblPr();
        var layout = properties.isSetTblLayout() ? properties.getTblLayout() : properties.addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);
        if (widths != null && widths.length == rows[0].length) {
            CTTblGrid grid = table.getCTTbl().getTblGrid();
            if (grid == null) grid = table.getCTTbl().addNewTblGrid();
            while (grid.sizeOfGridColArray() > 0) grid.removeGridCol(0);
            for (int width : widths) {
                grid.addNewGridCol().setW(BigInteger.valueOf(
                        Math.max(1, Math.round(CONTENT_WIDTH_TWIPS * width / 100.0))));
            }
        }
        CTTblBorders borders = properties.isSetTblBorders() ? properties.getTblBorders() : properties.addNewTblBorders();
        for (CTBorder border : List.of(borders.addNewTop(), borders.addNewLeft(), borders.addNewBottom(),
                borders.addNewRight(), borders.addNewInsideH(), borders.addNewInsideV())) {
            border.setVal(STBorder.SINGLE); border.setColor(BORDER); border.setSz(BigInteger.valueOf(4));
        }
        for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            row.setCantSplitRow(true);
            if (firstRowHeader && rowIndex == 0) row.setRepeatHeader(true);
            for (int column = 0; column < rows[rowIndex].length; column++) {
                XWPFTableCell cell = row.getCell(column);
                String fill = firstRowHeader && rowIndex == 0 ? HEADER
                        : rowIndex % 2 == 0 ? "F7F9FB" : "FFFFFF";
                setCell(cell, rows[rowIndex][column], fontSize,
                        firstRowHeader && rowIndex == 0, fill,
                        widths != null && column < widths.length ? widths[column] : 0);
            }
        }
        XWPFParagraph after = document.createParagraph();
        after.setSpacingAfter(30);
    }

    private void setCell(XWPFTableCell cell, String text, double size, boolean bold,
                         String fill, int widthPercent) {
        CTTcPr properties = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTShd shading = properties.isSetShd() ? properties.getShd() : properties.addNewShd();
        shading.setFill(fill);
        if (widthPercent > 0) {
            cell.setWidth(widthPercent + "%");
        }
        XWPFParagraph paragraph;
        if (cell.getParagraphs().isEmpty()) paragraph = cell.addParagraph();
        else paragraph = cell.getParagraphs().get(0);
        paragraph.setSpacingBefore(20); paragraph.setSpacingAfter(20); paragraph.setSpacingBetween(1.0);
        while (paragraph.getRuns().size() > 0) paragraph.removeRun(0);
        run(paragraph, text == null ? "" : text, size, bold, INK);
        cell.setVerticalAlignment(org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign.CENTER);
    }

    private XWPFRun run(XWPFParagraph paragraph, String text, double size, boolean bold, String color) {
        XWPFRun run = paragraph.createRun();
        configureRun(run, size, bold, color);
        run.setText(text == null ? "" : localizeLiteral(text));
        return run;
    }

    /** Translate stable report vocabulary while leaving dynamic makam/pitch names untouched. */
    private static String localizeLiteral(String value) {
        return switch (value) {
            case "1. Kaynak ve yöntem" -> section("1", "report.sourceMethod", "Source and method", "Kaynak ve yöntem");
            case "Terimleri nasıl okuyacaksınız?" -> t("report.readingGuide", "How to read the terms", "Terimleri nasıl okuyacaksınız?");
            case "2. Karar perdesinin belirlenmesi" -> section("2", "report.tonicDetermination", "Tonic determination", "Karar perdesinin belirlenmesi");
            case "3. Segâh referansı ve aday alanı" -> section("3", "report.segahReferenceArea", "Segâh reference and candidate area", "Segâh referansı ve aday alanı");
            case "3. Kuramsal inceleme aralığı ve sınıflandırma" -> section("3", "report.theoreticalIntervalClassification", "Theoretical analysis interval and classification", "Kuramsal inceleme aralığı ve sınıflandırma");
            case "3. Kuramsal hedef ve sınıflandırma" -> section("3", "report.theoreticalTarget", "Theoretical target and classification", "Kuramsal hedef ve sınıflandırma");
            case "4. Sayısal sonuçlar ve grafikler" -> section("4", "report.numericResultsCharts", "Numerical results and charts", "Sayısal sonuçlar ve grafikler");
            case "5. Ölçülebilir olayların tek tek dökümü" -> section("5", "report.individualEvents", "Individual measurable events", "Ölçülebilir olayların tek tek dökümü");
            case "6. Karara hizalı dizi / çeşni grubu benzerliği" -> section("6", "report.profileSimilarity", "Tonic-aligned scale / çeşni-group similarity", "Karara hizalı dizi / çeşni grubu benzerliği");
            case "6. Akademik yorum ve sınırlılıklar" -> section("6", "report.academicInterpretationLimits", "Academic interpretation and limitations", "Akademik yorum ve sınırlılıklar");
            case "7. Akademik yorum ve sınırlılıklar" -> section("7", "report.academicInterpretationLimits", "Academic interpretation and limitations", "Akademik yorum ve sınırlılıklar");
            case "Ek A. Bütün kanonik hedef-perde temasları" -> t("report.appendixAllContacts", "Appendix A. All canonical target-pitch contacts", "Ek A. Bütün kanonik hedef-perde temasları");
            case "Çıkıcı cümlelerde Segâh konumları" -> t("report.segahAscendingPositions", "Segâh positions in ascending phrases", "Çıkıcı cümlelerde Segâh konumları");
            case "İnici cümlelerde Segâh konumları" -> t("report.segahDescendingPositions", "Segâh positions in descending phrases", "İnici cümlelerde Segâh konumları");
            case "Doğrudan Segâh kalışlarının konumları" -> t("report.segahSustainedPositions", "Positions of direct Segâh sustains", "Doğrudan Segâh kalışlarının konumları");
            case "Aralık içindeki perdelerin ortalama icra değerleri" -> t("report.intervalPitchMeans", "Mean performance values of pitches in the interval", "Aralık içindeki perdelerin ortalama icra değerleri");
            case "Olay gövdesinin giriş, yerleşik ve çıkış değerleri" -> t("report.eventBodyValues", "Entry, settled and exit values of the event body", "Olay gövdesinin giriş, yerleşik ve çıkış değerleri");
            case "Olayların ölçüm ve güven ayrıntıları" -> t("report.eventMeasurementConfidence", "Event measurement and confidence details", "Olayların ölçüm ve güven ayrıntıları");
            case "Karardan başlayan ilk dört sesin makam profili benzerliği" -> t("report.firstFourSimilarity", "Makam-profile similarity of the first four pitches from the tonic", "Karardan başlayan ilk dört sesin makam profili benzerliği");
            case "Tekrarlanabilirlik" -> t("report.reproducibility", "Reproducibility", "Tekrarlanabilirlik");
            case "Kayıt", "İncelenen kayıt" -> t("report.recording", "Recording", "Kayıt");
            case "Süre" -> t("report.duration", "Duration", "Süre");
            case "Örnekleme" -> t("report.sampleRate", "Sample rate", "Örnekleme");
            case "Analiz kanalı" -> t("report.channel", "Analysis channel", "Analiz kanalı");
            case "Uygulama" -> t("report.application", "Application", "Uygulama");
            case "Analiz motoru", "Motor" -> t("report.engine", "Analysis engine", "Analiz motoru");
            case "Kaynak yolu" -> t("report.sourcePath", "Source path", "Kaynak yolu");
            case "Yöntem" -> t("report.method", "Method", "Yöntem");
            case "Durum" -> t("report.status", "Status", "Durum");
            case "Karar", "Karar perdesi" -> t("report.tonic", "Tonic", "Karar");
            case "Güven" -> t("report.confidence", "Confidence", "Güven");
            case "Seçim aralığı" -> t("report.selectionRange", "Selection range", "Seçim aralığı");
            case "Sesli çerçeve" -> t("report.voicedFrames", "Voiced frames", "Sesli çerçeve");
            case "Sağlam yayılım" -> t("report.robustSpread", "Robust spread", "Sağlam yayılım");
            case "Not" -> t("report.note", "Note", "Not");
            case "Makam" -> "Makam";
            case "Perde", "İncelenen perde" -> t("report.pitch", "Pitch", "Perde");
            case "Zaman" -> t("report.time", "Time", "Zaman");
            case "Frekans" -> t("report.frequency", "Frequency", "Frekans");
            case "Yön" -> t("report.direction", "Direction", "Yön");
            case "Net yön" -> t("report.netDirection", "Net direction", "Net yön");
            case "Tür" -> t("report.type", "Type", "Tür");
            case "Yorum" -> t("report.interpretation", "Interpretation", "Yorum");
            case "Sonuç" -> t("report.conclusion", "Conclusion", "Sonuç");
            case "Sıra" -> t("report.rank", "Rank", "Sıra");
            case "Profil" -> t("report.profile", "Profile", "Profil");
            case "Dizi / çeşni grubu" -> t("report.scaleCesniGroup",
                    "Scale / çeşni group", "Dizi / çeşni grubu");
            case "Benzerlik" -> t("report.similarity", "Similarity", "Benzerlik");
            case "Kapsam" -> t("report.coverage", "Coverage", "Kapsam");
            case "Kanıt" -> t("report.evidence", "Evidence", "Kanıt");
            case "Derece" -> t("report.degree", "Degree", "Derece");
            case "Kuramsal" -> t("report.theoreticalReference", "Theoretical", "Kuramsal");
            case "Gözlenen" -> t("report.measuredValue", "Observed", "Gözlenen");
            case "Sapma" -> t("report.deviation", "Deviation", "Sapma");
            case "Ortalama" -> t("report.mean", "Mean", "Ortalama");
            case "Ortalama sapma" -> t("report.meanDeviation", "Mean deviation", "Ortalama sapma");
            case "Medyan" -> t("report.median", "Median", "Medyan");
            case "Medyan sapma" -> t("report.medianDeviation", "Median deviation", "Medyan sapma");
            case "Süre ağırlıklı ortalama" -> t("report.weightedMean", "Duration-weighted mean", "Süre ağırlıklı ortalama");
            case "Standart sapma" -> t("report.standardDeviation", "Standard deviation", "Standart sapma");
            case "En pest / en dik" -> t("report.minimumMaximum", "Lowest / highest", "En pest / en dik");
            case "Olay sayısı" -> t("report.eventCount", "Event count", "Olay sayısı");
            case "Toplam süre" -> t("report.totalDuration", "Total duration", "Toplam süre");
            case "Evet" -> t("report.yes", "Yes", "Evet");
            case "Hayır" -> t("report.no", "No", "Hayır");
            case "Dahil" -> t("report.included", "Included", "Dahil");
            case "Dışlandı" -> t("report.excluded", "Excluded", "Dışlandı");
            case "Çıkıcı" -> t("report.ascending", "Ascending", "Çıkıcı");
            case "İnici" -> t("report.descending", "Descending", "İnici");
            case "Segâh kalışı" -> t("report.sustained", "Segâh sustain", "Segâh kalışı");
            case "Tümü" -> t("report.all", "All", "Tümü");
            case "Terim" -> t("report.glossary.term", "Term", "Terim");
            case "Sade açıklama" -> t("report.glossary.plainExplanation", "Plain explanation", "Sade açıklama");
            case "Sesin perde yüksekliğini belirleyen temel titreşim frekansı." -> t("report.glossary.f0",
                    "The fundamental vibration frequency that determines pitch height.",
                    "Sesin perde yüksekliğini belirleyen temel titreşim frekansı.");
            case "Bir saniyedeki titreşim sayısı. Frekansın fiziksel ölçü birimi." -> t("report.glossary.hz",
                    "The number of vibrations per second; the physical unit of frequency.",
                    "Bir saniyedeki titreşim sayısı. Frekansın fiziksel ölçü birimi.");
            case "Bir oktavın 1.200 eşit parçaya bölünmesiyle elde edilen küçük aralık birimi." -> t("report.glossary.cent",
                    "A small interval unit obtained by dividing an octave into 1,200 equal parts.",
                    "Bir oktavın 1.200 eşit parçaya bölünmesiyle elde edilen küçük aralık birimi.");
            case "Bir oktavın 53 eşit parçaya bölünmesiyle elde edilen aralık birimi; 1 Hc yaklaşık 22,64 C'dir." -> t("report.glossary.hc",
                    "An interval unit obtained by dividing an octave into 53 equal parts; 1 Hc is approximately 22.64 C.",
                    "Bir oktavın 53 eşit parçaya bölünmesiyle elde edilen aralık birimi; 1 Hc yaklaşık 22,64 C'dir.");
            case "Kuramsal hedef / merkez" -> t("report.glossary.theoryTarget", "Theoretical target / centre", "Kuramsal hedef / merkez");
            case "Makam kuramına göre perdenin beklenen C, Hc ve Hz konumu." -> t("report.glossary.theoryTargetDefinition",
                    "The expected C, Hc and Hz position of a pitch according to makam theory.",
                    "Makam kuramına göre perdenin beklenen C, Hc ve Hz konumu.");
            case "Hesap: gözlenen konum - kuramsal hedef. Örnek: hedef 90 C, ölçüm 86 C ise sapma -4 C'dir; ses hedefin 4 C pestindedir. Ölçüm 94 C olsaydı sapma +4 C, yani 4 C dik olurdu." -> t("report.glossary.deviation",
                    "Calculation: observed position - theoretical target. Example: if the target is 90 C and the measurement is 86 C, deviation is -4 C, meaning 4 C flat. A 94 C measurement would be +4 C, or 4 C sharp.",
                    "Hesap: gözlenen konum - kuramsal hedef. Örnek: hedef 90 C, ölçüm 86 C ise sapma -4 C'dir; ses hedefin 4 C pestindedir. Ölçüm 94 C olsaydı sapma +4 C, yani 4 C dik olurdu.");
            case "Negatif / pest" -> t("report.glossary.negativeFlat", "Negative / flat", "Negatif / pest");
            case "Gözlenen sesin kuramsal merkezden daha düşük olması." -> t("report.glossary.negativeFlatDefinition",
                    "The observed sound is lower than the theoretical centre.",
                    "Gözlenen sesin kuramsal merkezden daha düşük olması.");
            case "Pozitif / dik" -> t("report.glossary.positiveSharp", "Positive / sharp", "Pozitif / dik");
            case "Gözlenen sesin kuramsal merkezden daha yüksek olması." -> t("report.glossary.positiveSharpDefinition",
                    "The observed sound is higher than the theoretical centre.",
                    "Gözlenen sesin kuramsal merkezden daha yüksek olması.");
            case "Aritmetik ortalama" -> t("report.glossary.arithmeticMean", "Arithmetic mean", "Aritmetik ortalama");
            case "Bütün değerlerin toplamının olay sayısına bölünmesiyle elde edilen genel merkez." -> t("report.glossary.arithmeticMeanDefinition",
                    "The overall centre obtained by dividing the sum of all values by the number of events.",
                    "Bütün değerlerin toplamının olay sayısına bölünmesiyle elde edilen genel merkez.");
            case "Uzun duyulan olaylara kısa temaslardan daha fazla ağırlık veren ortalama." -> t("report.glossary.weightedMeanDefinition",
                    "A mean that gives longer events more weight than brief contacts.",
                    "Uzun duyulan olaylara kısa temaslardan daha fazla ağırlık veren ortalama.");
            case "Standart sapma / dağılım" -> t("report.glossary.standardDeviation", "Standard deviation / spread", "Standart sapma / dağılım");
            case "Kararlı olay" -> t("report.glossary.stableEvent", "Stable event", "Kararlı olay");
            case "Ölçülebilir olay" -> t("report.glossary.measurableEvent", "Measurable event", "Ölçülebilir olay");
            case "Kutu grafik" -> t("report.glossary.boxPlot", "Box plot", "Kutu grafik");
            case "Değerler sıralandığında ortada kalan tipik değerdir. Örnek: -12, -5, -4, +2 ve +9 C ölçümlerinin medyanı -4 C'dir; bu, tipik icranın hedeften yaklaşık 4 C pest olduğunu anlatır." -> t("report.glossary.medianDefinition",
                    "The typical middle value after sorting. Example: the median of -12, -5, -4, +2 and +9 C is -4 C, meaning the typical performance is about 4 C flat of the target.",
                    "Değerler sıralandığında ortada kalan tipik değerdir. Örnek: -12, -5, -4, +2 ve +9 C ölçümlerinin medyanı -4 C'dir; bu, tipik icranın hedeften yaklaşık 4 C pest olduğunu anlatır.");
            case "Tek bir sesin hedefe farkı değildir; bütün basışların birbirine ne kadar yakın olduğunu anlatır. Küçük değerler benzer, büyük değerler daha değişken basışlara işaret eder." -> t("report.glossary.standardDeviationDefinition",
                    "This is not one sound's difference from the target; it shows how close all performances are to one another. Small values indicate similarity, larger values greater variability.",
                    "Tek bir sesin hedefe farkı değildir; bütün basışların birbirine ne kadar yakın olduğunu anlatır. Küçük değerler benzer, büyük değerler daha değişken basışlara işaret eder.");
            case "Olay gövdesindeki perde dalgalanması önceden belirlenen sınırın içinde kalan olay." -> t("report.glossary.stableEventDefinition",
                    "An event whose pitch fluctuation within its body remains inside the predefined limit.",
                    "Olay gövdesindeki perde dalgalanması önceden belirlenen sınırın içinde kalan olay.");
            case "Asgari süre ve güven koşullarını karşılayarak sayısal özete alınabilen olay." -> t("report.glossary.measurableEventDefinition",
                    "An event that meets minimum duration and confidence conditions and can enter the numerical summary.",
                    "Asgari süre ve güven koşullarını karşılayarak sayısal özete alınabilen olay.");
            case "Kuramsal merkezin yaklaşık ±22,64 C çevresindeki yakınlık bandı." -> t("report.glossary.oneHcDefinition",
                    "A proximity band of approximately ±22.64 C around the theoretical centre.",
                    "Kuramsal merkezin yaklaşık ±22,64 C çevresindeki yakınlık bandı.");
            case "F0 ölçümünün ses kanıtıyla ne ölçüde desteklendiğini gösteren 0–100 arası oran." -> t("report.glossary.confidenceDefinition",
                    "A 0–100 ratio indicating how strongly audio evidence supports the F0 measurement.",
                    "F0 ölçümünün ses kanıtıyla ne ölçüde desteklendiğini gösteren 0–100 arası oran.");
            case "Ortadaki çizgi medyanı, kutu olayların ortadaki yarısını, noktalar ise tek tek olayları gösterir." -> t("report.glossary.boxPlotDefinition",
                    "The middle line shows the median, the box the middle half of events, and points individual events.",
                    "Ortadaki çizgi medyanı, kutu olayların ortadaki yarısını, noktalar ise tek tek olayları gösterir.");
            case "Kullanıcı tarafından onaylandı" -> t("report.userConfirmed", "Confirmed by the user", "Kullanıcı tarafından onaylandı");
            case "Otomatik aday" -> t("report.automaticCandidate", "Automatic candidate", "Otomatik aday");
            default -> value;
        };
    }

    private static String section(String number, String key, String english, String turkish) {
        return number + ". " + t(key, english, turkish);
    }

    private void configureRun(XWPFRun run, double size, boolean bold, String color) {
        run.setFontFamily(FONT);
        run.setFontSize(size);
        run.setBold(bold);
        run.setColor(color);
        CTFonts fonts = run.getCTR().isSetRPr() && run.getCTR().getRPr().sizeOfRFontsArray() > 0
                ? run.getCTR().getRPr().getRFontsArray(0)
                : run.getCTR().isSetRPr() ? run.getCTR().getRPr().addNewRFonts()
                : run.getCTR().addNewRPr().addNewRFonts();
        fonts.setAscii(FONT); fonts.setHAnsi(FONT); fonts.setEastAsia(FONT); fonts.setCs(FONT);
    }

    private static CTPPr paragraphProperties(XWPFParagraph paragraph) {
        CTP ctp = paragraph.getCTP();
        return ctp.isSetPPr() ? ctp.getPPr() : ctp.addNewPPr();
    }

    private void spacer(XWPFDocument document, int lines) {
        for (int i = 0; i < lines; i++) document.createParagraph();
    }

    private void pageBreak(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setPageBreak(true);
    }

    private static String expectedReferenceSummary(AcademicStudyDocument.Theory theory) {
        if (theory.expectedReferences().isEmpty()) {
            return t("report.noExpectedReference",
                    "No theoretical degree in the selected makam profile falls within this open interval.",
                    "Seçilen makam profilinde bu açık aralığa düşen bir kuramsal derece bulunmadı.");
        }
        return theory.expectedReferences().stream()
                .map(value -> value.noteName() + " = " + signed(value.relativeCent(), 4)
                        + " C / " + signed(value.relativeHc(), 4) + " Hc / "
                        + format(value.hertz(), 4) + " Hz")
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private static String cHc(double cent) {
        return signed(cent, 2) + " C / " + signed(cent / AcademicStudyDocument.HC_CENT, 3) + " Hc";
    }

    private static String extremeRange(List<AcademicStudyDocument.Event> events) {
        List<AcademicStudyDocument.Event> included = events.stream()
                .filter(AcademicStudyDocument.Event::included).toList();
        if (included.isEmpty()) return "—";
        double minimum = included.stream()
                .mapToDouble(AcademicStudyDocument.Event::deviationCent).min().orElse(0.0);
        double maximum = included.stream()
                .mapToDouble(AcademicStudyDocument.Event::deviationCent).max().orElse(0.0);
        return cHc(minimum) + " ↔ " + cHc(maximum);
    }

    /** Data-dependent interpretation printed immediately below the combined panel. */
    private static String distributionInterpretation(AcademicStudyDocument report) {
        List<AcademicStudyDocument.Event> events = report.measurableEvents().stream()
                .filter(AcademicStudyDocument.Event::included).toList();
        if (events.isEmpty()) {
            return t("report.interpretation.noMeasurableEvents",
                    "Academic interpretation: No measurable event met the report criteria, so no central-tendency interpretation could be made for the distribution or direction groups.",
                    "Akademik yorum: Rapor kapsamına alınabilen ölçülebilir bir olay bulunmadığı için dağılım ve yön grupları hakkında merkezî eğilim yorumu yapılamamıştır.");
        }
        AcademicStudyDocument.Statistics overall = AcademicStudyDocument.Statistics.from(events);
        String tendency = tendency(overall.medianCent());

        List<String> canonicalDirections = List.of("Çıkıcı", "İnici", "Segâh kalışı", "Yatay/Karma");
        LinkedHashMap<String, List<AcademicStudyDocument.Event>> collected = new LinkedHashMap<>();
        for (AcademicStudyDocument.Event event : events) {
            collected.computeIfAbsent(event.direction(), ignored -> new ArrayList<>()).add(event);
        }
        LinkedHashMap<String, List<AcademicStudyDocument.Event>> groups = new LinkedHashMap<>();
        for (String direction : canonicalDirections) {
            List<AcademicStudyDocument.Event> values = collected.remove(direction);
            if (values != null && !values.isEmpty()) groups.put(direction, values);
        }
        groups.putAll(collected);

        List<DirectionSummary> summaries = groups.entrySet().stream().map(entry -> {
            AcademicStudyDocument.Statistics statistics =
                    AcademicStudyDocument.Statistics.from(entry.getValue());
            int outside = (int) entry.getValue().stream()
                    .filter(event -> Math.abs(event.deviationCent()) > AcademicStudyDocument.HC_CENT)
                    .count();
            return new DirectionSummary(entry.getKey(), statistics.count(),
                    statistics.medianCent(), outside);
        }).filter(value -> value.count() > 0).toList();
        String groupEvidence = summaries.stream()
                .map(value -> t("report.interpretation.groupEvidence", "{0} n={1}, median {2} C",
                        "{0} n={1}, medyan {2} C", directionLabel(value.label()), value.count(),
                        signed(value.medianCent(), 2)))
                .collect(java.util.stream.Collectors.joining("; "));
        DirectionSummary strongest = summaries.stream()
                .max(Comparator.comparingDouble(value -> Math.abs(value.medianCent())))
                .orElse(null);
        int outside = (int) events.stream()
                .filter(event -> Math.abs(event.deviationCent()) > AcademicStudyDocument.HC_CENT)
                .count();
        String outsideEvidence = summaries.stream().filter(value -> value.outsideCount() > 0)
                .map(value -> directionLabel(value.label()) + " " + value.outsideCount())
                .collect(java.util.stream.Collectors.joining(", "));
        String smallSamples = summaries.stream().filter(value -> value.count() < 5)
                .map(value -> directionLabel(value.label()) + " (n=" + value.count() + ")")
                .collect(java.util.stream.Collectors.joining(", "));

        StringBuilder result = new StringBuilder(t("report.interpretation.opening",
                "Academic interpretation: The median (the typical middle deviation after sorting) of the {0} measurable events in the report is {1} C; the central tendency {2}. ",
                "Akademik yorum: Rapor kapsamındaki {0} ölçülebilir olayın medyanı (sıralandığında ortada kalan tipik sapması) {1} C''dir; merkezî eğilim {2}. ",
                events.size(), signed(overall.medianCent(), 2), tendency));
        if (!groupEvidence.isBlank()) {
            result.append(t(report.directionalSegahAnalysis()
                            ? "report.interpretation.bySegahContext" : "report.interpretation.byDirection",
                    report.directionalSegahAnalysis()
                            ? "By Segâh usage context, {0} was observed. "
                            : "By net melodic direction, {0} was observed. ",
                    report.directionalSegahAnalysis()
                            ? "Segâh kullanım bağlamlarına göre {0} gözlenmiştir. "
                            : "Net ezgi yönlerine göre {0} gözlenmiştir. ", groupEvidence));
        }
        if (strongest != null) {
            result.append(t("report.interpretation.strongest",
                    "The group with the greatest absolute median distance from the theoretical centre is {0} ({1} C). ",
                    "Kuramsal merkezden mutlak medyan uzaklığı en yüksek grup {0} ({1} C) olarak görünmektedir. ",
                    directionLabel(strongest.label()), signed(strongest.medianCent(), 2)));
        }
        result.append(t("report.interpretation.outsideBand",
                "There are {0} events outside the ±1 Hc ({1} C) band around the theoretical centre",
                "Kuramsal merkezin ±1 Hc ({1} C) bandı dışında {0} olay vardır",
                outside, format(AcademicStudyDocument.HC_CENT, 2)));
        if (!outsideEvidence.isBlank()) result.append(" (").append(outsideEvidence).append(")");
        result.append(". ");
        if (!smallSamples.isBlank()) {
            result.append(t("report.interpretation.smallSample",
                    "Small-sample warning: {0} have fewer than five observations; uncertainty is high. ",
                    "Küçük örneklem uyarısı: {0} gruplarında gözlem sayısı beşin altındadır; belirsizlik yüksektir. ",
                    smallSamples));
        }
        result.append(t("report.interpretation.chartReading",
                "Negative values show flatter and positive values sharper usage; boxes contain the middle half of events and points represent individual events. These directional differences are descriptive evidence and must not be interpreted alone as a definitive makam rule or diagnosis.",
                "Grafikte negatif değerler pest, pozitif değerler dik kullanımı gösterir; kutular olayların ortadaki yarısının toplandığı alanı, noktalar ise tek tek olayları temsil eder. Bu yönsel farklılıklar betimleyici kanıttır; tek başına kesin bir makam kuralı veya makam teşhisi olarak yorumlanmamalıdır."));
        return result.toString();
    }

    private static String tendency(double medianCent) {
        if (medianCent < -0.5) return t("report.tendency.flat",
                "is on the flat side of the theoretical target", "kuramsal hedefin pest tarafındadır");
        if (medianCent > 0.5) return t("report.tendency.sharp",
                "is on the sharp side of the theoretical target", "kuramsal hedefin dik tarafındadır");
        return t("report.tendency.balanced", "is balanced around the theoretical target",
                "kuramsal hedef çevresinde dengelidir");
    }

    private record DirectionSummary(String label, int count, double medianCent,
                                    int outsideCount) { }

    private static String signed(double value, int digits) {
        return ReportLocalization.signed(value, digits);
    }

    private static String format(double value, int digits) {
        return ReportLocalization.number(value, digits);
    }

    private static String percent(double value) {
        double percent = value <= 1.0 ? value * 100.0 : value;
        return ReportLocalization.percentValue(percent, 1);
    }

    private static String formatTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) return "—";
        int minutes = (int) (seconds / 60.0);
        double remainder = seconds - minutes * 60.0;
        String secondsPart = ReportLocalization.number(remainder, 2);
        if (remainder < 10.0) secondsPart = "0" + secondsPart;
        return String.format(java.util.Locale.ROOT, "%02d:", minutes) + secondsPart;
    }

    private static String t(String key, String english, String turkish, Object... arguments) {
        return ReportLocalization.text(key, english, turkish, arguments);
    }

    /**
     * Transitional text for the new first-four evidence contract.  Turkish is
     * rendered natively; other locales receive a coherent English sentence
     * until the complete translation catalogue is regenerated for a release.
     */
    private static String firstFourText(String english, String turkish, Object... arguments) {
        String pattern = "tr".equals(ReportLocalization.locale().getLanguage())
                ? turkish : english;
        if (arguments == null || arguments.length == 0) return pattern;
        return new java.text.MessageFormat(pattern, ReportLocalization.locale()).format(arguments);
    }

    private static String directionLabel(String value) {
        return ReportLocalization.displayLabel(value);
    }

    private static String dynamicLabel(String value) {
        return ReportLocalization.displayLabel(value);
    }

    private static String localizedReason(String value) {
        return ReportLocalization.reason(value);
    }

    private static String localizedPath(String value) {
        if (value == null || value.isBlank()) return "—";
        String clean = value.strip();
        String suffix = " (üst çıpalar doğrulanmadı)";
        if (clean.endsWith(suffix)) {
            return clean.substring(0, clean.length() - suffix.length()) + t(
                    "report.path.upperAnchorsNotConfirmed",
                    " (upper anchors not confirmed)", " (üst çıpalar doğrulanmadı)");
        }
        return ReportLocalization.displayLabel(value);
    }

    private static String localizedVerdict(String value) {
        if (value == null || value.isBlank()) return "—";
        String clean = value.strip();
        if (clean.startsWith("Aynı aralık profili:")) {
            String profiles = clean.substring("Aynı aralık profili:".length()).strip();
            return t("report.sameIntervalProfile", "Same interval profile: {0}",
                    "Aynı aralık profili: {0}", profiles);
        }
        if (clean.equals("Karara hizalı aralık benzerliği")) {
            return t("report.tonicAlignedIntervalSimilarity", "Tonic-aligned interval similarity",
                    "Karara hizalı aralık benzerliği");
        }
        if (clean.equals("Kullanıcının seçtiği makam profili")) {
            return t("report.userSelectedProfile", "Makam profile selected by the user",
                    "Kullanıcının seçtiği makam profili");
        }
        if (clean.contains(" grubu — en iyi eşleşen temsilci: ")) {
            String marker = " grubu — en iyi eşleşen temsilci: ";
            int markerAt = clean.indexOf(marker);
            String family = clean.substring(0, markerAt);
            String remainder = clean.substring(markerAt + marker.length());
            String membersMarker = "; birlikte değerlendirilen üyeler: ";
            int membersAt = remainder.indexOf(membersMarker);
            if (membersAt >= 0) {
                String representative = remainder.substring(0, membersAt);
                String membersAndTail = remainder.substring(membersAt + membersMarker.length());
                String sameMarker = "; temsilciyle aynı tam aralık profili: ";
                String selectedMarker = ". Kullanıcının seçtiği makam bu gruptadır.";
                boolean selected = membersAndTail.endsWith(selectedMarker);
                String withoutSelected = selected
                        ? membersAndTail.substring(0, membersAndTail.length() - selectedMarker.length())
                        : membersAndTail.endsWith(".")
                        ? membersAndTail.substring(0, membersAndTail.length() - 1) : membersAndTail;
                int sameAt = withoutSelected.indexOf(sameMarker);
                String members = sameAt >= 0 ? withoutSelected.substring(0, sameAt) : withoutSelected;
                String same = sameAt >= 0 ? withoutSelected.substring(sameAt + sameMarker.length()) : "";
                String result = t("report.profileGroupVerdict",
                        "{0} group — best-matching representative: {1}; members evaluated together: {2}",
                        "{0} grubu — en iyi eşleşen temsilci: {1}; birlikte değerlendirilen üyeler: {2}",
                        family, representative, members);
                if (!same.isBlank()) result += t("report.profileSameFullProfile",
                        "; same complete interval profile as the representative: {0}",
                        "; temsilciyle aynı tam aralık profili: {0}", same);
                if (selected) result += t("report.profileUserSelectedGroup",
                        ". The makam selected by the user belongs to this group.",
                        ". Kullanıcının seçtiği makam bu gruptadır.");
                else result += ".";
                return result;
            }
        }
        return localizedReason(clean);
    }

    /** Localizes only the presentation prefix; the nearest pitch remains a proper name. */
    private static String localizedNoteName(String value) {
        if (value == null || value.isBlank()) return "—";
        String clean = value.strip();
        String prefix = "Adlandırılamayan (en yakın: ";
        if (clean.startsWith(prefix) && clean.endsWith(")")) {
            String nearestPitch = clean.substring(prefix.length(), clean.length() - 1);
            return t("report.unnamedNearest", "Unnamed (nearest: {0})",
                    "Adlandırılamayan (en yakın: {0})", nearestPitch);
        }
        if (clean.equals("Adlandırılamayan")) {
            return t("report.unnamed", "Unnamed", "Adlandırılamayan");
        }
        return clean;
    }

    private static String localizedIntonation(AcademicStudyDocument.Event event) {
        if (event.noteName().startsWith("Adlandırılamayan")
                && !event.exclusionReason().isBlank()) {
            return localizedNearestDistance(event.exclusionReason());
        }
        return dynamicLabel(event.intonationLabel());
    }

    /** Localizes “nearest” while preserving the theory pitch name and numeric evidence. */
    private static String localizedNearestDistance(String value) {
        if (value == null || value.isBlank()) return "—";
        String clean = value.strip();
        String prefix = "En yakın ";
        int separator = clean.indexOf(": ");
        if (clean.startsWith(prefix) && separator > prefix.length()) {
            String pitch = clean.substring(prefix.length(), separator);
            String distance = clean.substring(separator + 2);
            return t("report.nearestPitchDistance", "Nearest {0}: {1}",
                    "En yakın {0}: {1}", pitch, distance);
        }
        return localizedReason(clean);
    }

    private static String blankDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
