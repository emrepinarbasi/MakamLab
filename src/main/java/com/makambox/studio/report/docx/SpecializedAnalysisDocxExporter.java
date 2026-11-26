package com.makambox.studio.report.docx;

import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.study.glissando.GlissandoDirection;
import com.makambox.studio.study.glissando.PitchPairGlissandoEvent;
import com.makambox.studio.study.glissando.PitchPairGlissandoStatistics;
import com.makambox.studio.study.glissando.PitchPairGlissandoStudyResult;
import com.makambox.studio.study.hicaz.HicazAugmentedIntervalMeasurement;
import com.makambox.studio.study.hicaz.HicazAugmentedIntervalStudyResult;
import com.makambox.studio.study.hicaz.HicazDegreeEvent;
import com.makambox.studio.study.hicaz.HicazDegreeStatistics;
import com.makambox.studio.study.hicaz.HicazIntervalStatistics;
import com.makambox.studio.study.hicaz.HicazPhrase;
import com.makambox.studio.study.hicaz.HicazPhraseDirection;
import com.makambox.studio.study.hicaz.HicazTetrachordDegree;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Editable academic Word reports for the Hicaz augmented-interval and selected-pair
 * glissando modules.  Tables remain native Word content; only charts are embedded PNG.
 */
public final class SpecializedAnalysisDocxExporter {
    private static final String FONT = "Arial";
    private static final String INK = "1D2735";
    private static final String MUTED = "5B6F86";
    private static final String CRIMSON = "A91632";
    private static final String PALE_CRIMSON = "F8E7EA";
    private static final String PALE_BLUE = "E9F1FA";
    private static final String HEADER = "E2E8EF";
    private static final String BORDER = "C9D2DB";

    public void exportHicaz(SpecializedAnalysisMetadata metadata,
                            HicazAugmentedIntervalStudyResult result,
                            Path requestedPath) throws IOException {
        if (metadata == null || result == null) throw new IllegalArgumentException(t(
                "report.dataRequired", "Report data is required", "Rapor verisi gerekli"));
        write(requestedPath, document -> {
            String title = t("result.hicaz.title", "Augmented Interval Analysis", "Artık Aralık Analizi");
            configure(document, title);
            cover(document, title, metadata, t("report.hicaz.purpose",
                    "Directional analysis of the augmented interval between degrees 2 and 3 of the Hicaz tetrachord and the intonation deviations of degrees 2, 3 and 4",
                    "Hicaz dörtlüsünün 2. ve 3. dereceleri arasındaki artık aralık ile 2., 3. ve 4. derece icra sapmalarının yönlere göre incelenmesi"));
            terminology(document);
            hicazTheory(document, metadata);
            hicazSummary(document, result);
            hicazDirectionSection(document, result, HicazPhraseDirection.ASCENDING);
            hicazDirectionSection(document, result, HicazPhraseDirection.DESCENDING);
            hicazPhrases(document, result);
            hicazEvents(document, result);
            methodAndLimits(document, List.of(
                    t("report.hicaz.method.reference", "The Hicaz tetrachord is represented in the 53-Hc system by the tonic, +5 Hc, +17 Hc and +22 Hc centres.", "Hicaz dörtlüsü 53-Hc sisteminde karar, +5 Hc, +17 Hc ve +22 Hc merkezleriyle temsil edilmiştir."),
                    t("report.hicaz.method.paths", "Ascending and descending phrases were built only from degree paths that are ordered in time and move in one direction. Short subpaths within a longer phrase were not counted separately.", "Çıkıcı ve inici cümleler yalnız zaman içinde sıralı ve tek yönde ilerleyen derece yollarından oluşturulmuştur. Bir uzun cümle içindeki kısa alt yollar ayrıca sayılmamıştır."),
                    t("report.hicaz.method.interval", "The augmented interval was measured only in an actual 2→3 or 3→2 transition. Leaps such as 1→3 do not substitute for an augmented-interval measurement.", "Artık aralık yalnız gerçek 2→3 veya 3→2 geçişinde ölçülmüştür. 1→3 gibi atlamalar artık aralık ölçümü yerine geçmez."),
                    t("report.method.auditConfidence", "Candidates in the 35–65 percent confidence range were retained in the audit table but excluded from the primary means and medians.", "Yüzde 35–65 güven aralığındaki adaylar silinmemiş, denetim tablosunda korunmuş; ana ortalama ve medyanlara katılmamıştır.")),
                    List.of(
                            t("report.limit.dominantF0", "Results describe the dominant melodic F0 track in the selected channel and must not be interpreted as the independent result of one performer in a polyphonic recording.", "Sonuçlar seçilen kanaldaki baskın melodik F0 izine aittir. Çok sesli kayıtta tek bir icracının bağımsız sonucu olarak yorumlanmamalıdır."),
                            t("report.limit.smallSample", "Direction groups with few events are descriptive and must not be generalized to a broader performance tradition.", "Az sayıda olay içeren yön grupları betimseldir; geniş bir icra geleneğine genellenmemelidir."),
                            t("report.limit.context", "A positive or negative deviation does not by itself indicate an incorrect performance; it must be evaluated together with style, melodic development and context.", "Pozitif veya negatif sapma, tek başına yanlış icra anlamına gelmez; tavır, seyir ve bağlamla birlikte değerlendirilmelidir.")));
        });
    }

    public void exportGlissando(SpecializedAnalysisMetadata metadata,
                                PitchPairGlissandoStudyResult result,
                                Path requestedPath) throws IOException {
        if (metadata == null || result == null) throw new IllegalArgumentException(t(
                "report.dataRequired", "Report data is required", "Rapor verisi gerekli"));
        String pair = result.request().lowerPitchName() + "–" + result.request().upperPitchName();
        write(requestedPath, document -> {
            String title = pair + " " + t("result.glissando.title", "Glissando/Portamento Analysis", "Glisendo/Portamento Analizi");
            configure(document, title);
            cover(document, title, metadata, t("report.glissando.purpose",
                    "Start, end, span, duration, speed and reversal characteristics of ascending and descending slides between the two selected pitches",
                    "Seçilen iki perde arasındaki çıkıcı ve inici kaymaların başlangıç, bitiş, genişlik, süre, hız ve geri dönüş özellikleri"));
            terminology(document);
            glissandoTheory(document, result);
            glissandoSummary(document, result);
            glissandoDirectionSection(document, result, GlissandoDirection.ASCENDING);
            glissandoDirectionSection(document, result, GlissandoDirection.DESCENDING);
            glissandoEvents(document, result);
            methodAndLimits(document, List.of(
                    t("report.glissando.method.continuous", "Each event was sought as a continuous F0 movement between the selected lower and upper pitches.", "Her olay, seçilen alt ve üst perde arasındaki kesintisiz F0 hareketi olarak aranmıştır."),
                    t("report.glissando.method.directions", "Ascending and descending events were calculated separately. A brief reverse movement does not remove the event; its shape is additionally labelled as returning or mixed.", "Çıkıcı ve inici olaylar ayrı hesaplanmıştır. Kaymanın içinde kısa bir ters hareket bulunması olayı silmez; biçim 'geri dönüşlü' veya 'karma' olarak ayrıca belirtilir."),
                    t("report.glissando.method.endpoints", "Start and end values are reported in C and Hc relative to the tonic, and also as deviations from the theoretical endpoints.", "Başlangıç ve bitiş değerleri karar frekansına göre C ve Hc cinsinden, ayrıca kuramsal uç noktalara göre sapma olarak verilmiştir."),
                    t("report.method.auditEvents", "Candidates that do not meet the primary statistical criteria but remain worthy of inspection are retained in the audit-events section.", "Ana istatistik koşullarını karşılamayan fakat incelemeye değer adaylar denetim olayları bölümünde korunmuştur.")),
                    List.of(
                            t("report.glissando.limit.monophonic", "Glissando/Portamento analysis assumes one continuous dominant melodic F0; simultaneous sounds may disturb the F0 track.", "Glisendo/Portamento analizi sürekli ve tek baskın melodik F0 varsayar; eşzamanlı sesler F0 izini bozabilir."),
                            t("report.glissando.limit.endpoints", "When endpoint contact is required, movements without sufficiently stable contact at both selected pitches are excluded from the primary statistics.", "Uç temasları zorunlu modda, seçilen iki perdede yeterince kararlı temas bulunmayan hareketler ana istatistiğe alınmaz."),
                            t("report.glissando.limit.speed", "Speed in C/s and Hc/s describes the slope of pitch movement; it does not directly measure the performer's physical movement speed.", "Hız, C/sn ve Hc/sn olarak perde hareketinin eğimini açıklar; icracının fiziksel hareket hızını doğrudan ölçmez.")));
        });
    }

    @FunctionalInterface
    private interface DocumentWriter { void write(XWPFDocument document) throws IOException; }

    private static void write(Path requestedPath, DocumentWriter writer) throws IOException {
        if (requestedPath == null) throw new IllegalArgumentException(t(
                "report.pathRequired", "A DOCX path is required", "DOCX yolu gerekli"));
        Path absolute = requestedPath.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".makamlab-special-", ".docx.part");
        boolean moved = false;
        try (XWPFDocument document = new XWPFDocument()) {
            writer.write(document);
            try (var output = Files.newOutputStream(temporary)) { document.write(output); }
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

    private static void configure(XWPFDocument document, String title) {
        document.getProperties().getCoreProperties().setTitle(title);
        document.getProperties().getCoreProperties().setCreator("MakamLab V1");
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz size = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        size.setW(BigInteger.valueOf(11_906)); size.setH(BigInteger.valueOf(16_838));
        CTPageMar margin = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margin.setTop(BigInteger.valueOf(900)); margin.setBottom(BigInteger.valueOf(900));
        margin.setLeft(BigInteger.valueOf(900)); margin.setRight(BigInteger.valueOf(900));
    }

    private static void cover(XWPFDocument document, String title,
                              SpecializedAnalysisMetadata metadata, String purpose) {
        XWPFParagraph heading = document.createParagraph();
        heading.setAlignment(ParagraphAlignment.CENTER);
        text(heading, "MakamLab", 14, true, CRIMSON);
        XWPFParagraph titleParagraph = document.createParagraph();
        titleParagraph.setAlignment(ParagraphAlignment.CENTER);
        text(titleParagraph, title, 25, true, INK);
        XWPFParagraph purposeParagraph = document.createParagraph();
        purposeParagraph.setAlignment(ParagraphAlignment.CENTER);
        text(purposeParagraph, purpose, 11, false, MUTED);
        document.createParagraph();
        String unspecified = t("common.unspecified", "Not specified", "Belirtilmedi");
        table(document, new String[]{t("report.sourceMethod", "Source and method", "Kaynak ve yöntem"),
                t("report.value", "Value", "Değer")}, List.of(
                row(t("report.recording", "Recording", "Kayıt"), metadata.sourceName()),
                row(t("report.sourcePath", "Source path", "Kaynak yolu"), metadata.sourcePath() == null ? unspecified : metadata.sourcePath().toString()),
                row(t("report.duration", "Duration", "Süre"), time(metadata.durationSeconds())),
                row(t("report.sampleRate", "Sample rate", "Örnekleme"), metadata.sampleRate() <= 0 ? unspecified
                        : number(metadata.sampleRate(), 0) + " Hz"),
                row(t("report.channel", "Analysis channel", "Analiz kanalı"),
                        ReportLocalization.displayLabel(metadata.channelDescription())),
                row(t("report.makam", "Makam", "Makam"), metadata.makamName()),
                row(t("report.tonic", "Tonic", "Karar"), metadata.tonicNoteName() + " — " + number(metadata.tonicHertz(), 4) + " Hz"),
                row(t("report.tonicMethod", "Tonic selection method", "Karar seçim yöntemi"),
                        ReportLocalization.displayLabel(metadata.tonicMethod())),
                row(t("report.application", "Application", "Uygulama"), metadata.applicationVersion()),
                row(t("report.date", "Report date", "Rapor tarihi"), ReportLocalization.dateTime(ZonedDateTime.now()))));
        pageBreak(document);
    }

    private static void terminology(XWPFDocument document) {
        heading(document, t("report.readingGuide", "How to read this report", "Rapor nasıl okunur?"), 1);
        paragraph(document, t("report.guide.units", "C (cent) divides an octave into 1,200 equal parts. Hc is the Holderian comma, which divides the same octave into 53 parts; 1 Hc is approximately 22.64 C.", "C (cent), bir oktavı 1200 eşit parçaya bölen ölçüdür. Hc ise aynı oktavı 53 parçaya bölen Holder komasıdır; 1 Hc yaklaşık 22,64 C'dir."));
        paragraph(document, t("report.guide.deviation", "Deviation is the difference between the measured performance and the theoretical reference. A negative value is flatter than the reference; a positive value is sharper.", "Sapma, icrada ölçülen değerin kuramsal referanstan farkıdır. Negatif değer perdenin referanstan daha pest, pozitif değer daha dik/tiz kullanıldığını gösterir."));
        paragraph(document, t("report.guide.meanMedian", "The mean describes the overall tendency of all events. The median is the typical middle event after the values are ordered and is less affected by a single extreme value.", "Ortalama bütün olayların toplam eğilimini verir. Medyan ise değerler küçükten büyüğe sıralandığında ortada kalan tipik olaydır; tek bir aşırı değerden ortalamaya göre daha az etkilenir."));
        paragraph(document, t("report.guide.mad", "MAD describes how far events spread around the median. A smaller MAD means more consistent events; a larger MAD means more variable performance.", "MAD, olayların medyan çevresinde ne kadar yayıldığını açıklar. Küçük MAD daha birbirine yakın, büyük MAD daha değişken icra anlamına gelir."));
    }

    private static void hicazTheory(XWPFDocument document, SpecializedAnalysisMetadata metadata) {
        heading(document, t("report.hicaz.theory", "Theoretical Hicaz tetrachord", "Kuramsal Hicaz dörtlüsü"), 1);
        paragraph(document, t("report.hicaz.referenceExplanation",
                "The tonic is degree 1. The reference sequence is 0–5–17–22 Hc. The augmented interval between degrees 2 and 3 is therefore 12 Hc, or {0} C.",
                "Karar sesi 1. derece kabul edilmiştir. Referans dizilim 0–5–17–22 Hc olarak tanımlanmıştır. Buna göre 2. ve 3. dereceler arasındaki artık aralık 12 Hc, yani {0} C olarak hesaplanır.",
                number(HicazAugmentedIntervalMeasurement.THEORETICAL_INTERVAL_CENT, 3)));
        List<String[]> rows = new ArrayList<>();
        for (HicazTetrachordDegree degree : HicazTetrachordDegree.values()) {
            rows.add(row(Integer.toString(degree.number()), degreeLabel(degree),
                    signed(degree.theoreticalCent(), 3) + " C",
                    signed(degree.theoreticalHc(), 3) + " Hc",
                    number(degree.theoreticalHertz(metadata.tonicHertz()), 4) + " Hz"));
        }
        table(document, new String[]{t("report.degree", "Degree", "Derece"), t("report.function", "Function", "İşlev"),
                t("report.relativeCent", "Relative to tonic (C)", "Karara göre C"),
                t("report.relativeHc", "Relative to tonic (Hc)", "Karara göre Hc"),
                t("report.frequency", "Frequency", "Frekans")}, rows);
    }

    private static void hicazSummary(XWPFDocument document,
                                     HicazAugmentedIntervalStudyResult result) throws IOException {
        heading(document, t("report.hicaz.overallDegrees", "Overall degree summary", "Genel derece özeti"), 1);
        paragraph(document, t("report.hicaz.overallExplanation", "This table is the overall summary in which every event from reliable ascending and descending phrases is used once.", "Bu tablo, güvenilir çıkıcı ve inici cümlelerdeki olayların birer kez kullanıldığı genel özettir."));
        List<String[]> rows = new ArrayList<>();
        for (HicazTetrachordDegree degree : List.of(HicazTetrachordDegree.DEGREE_2,
                HicazTetrachordDegree.DEGREE_3, HicazTetrachordDegree.DEGREE_4)) {
            HicazDegreeStatistics s = result.overallDegreeStatistics(degree);
            rows.add(degreeStatsRow(degreeLabel(degree), s));
        }
        table(document, degreeSummaryHeaders(), rows);
        addPicture(document, hicazScatter(result, null), t("report.hicaz.overallChart", "Overall Hicaz degree deviations", "Genel Hicaz derece sapmaları"));
    }

    private static void hicazDirectionSection(XWPFDocument document,
                                              HicazAugmentedIntervalStudyResult result,
                                              HicazPhraseDirection direction) throws IOException {
        // Each direction is a complete academic comparison unit (summary table,
        // direct 2↔3 measurement and chart).  Starting it on a fresh page prevents
        // the heading/header row from being orphaned at the previous page foot.
        pageBreak(document);
        heading(document, directionLabel(direction) + " " + t("report.phrases", "phrases", "cümleler"), 1);
        List<String[]> rows = new ArrayList<>();
        for (HicazTetrachordDegree degree : List.of(HicazTetrachordDegree.DEGREE_2,
                HicazTetrachordDegree.DEGREE_3, HicazTetrachordDegree.DEGREE_4)) {
            rows.add(degreeStatsRow(degreeLabel(degree), result.degreeStatistics(direction, degree)));
        }
        table(document, degreeSummaryHeaders(), rows);
        HicazIntervalStatistics interval = result.intervalStatistics(direction);
        table(document, new String[]{t("report.hicaz.direct23", "Direct 2↔3 measurement", "Doğrudan 2↔3 ölçümü"), t("report.value", "Value", "Değer")}, List.of(
                row(t("report.eventCount", "Event count", "Olay sayısı"), Integer.toString(interval.count())),
                row(t("report.hicaz.theoreticalAugmented", "Theoretical augmented interval", "Kuramsal artık aralık"), number(271.698, 3) + " C / " + number(12, 3) + " Hc"),
                row(t("report.observedMean", "Observed mean", "Ortalama gözlenen"), pair(interval.meanObservedIntervalCent())),
                row(t("report.observedMedian", "Observed median", "Medyan gözlenen"), pair(interval.medianObservedIntervalCent())),
                row(t("report.meanDeviation", "Mean deviation", "Ortalama sapma"), pair(interval.meanDeviationCent())),
                row(t("report.medianDeviation", "Median deviation", "Medyan sapma"), pair(interval.medianDeviationCent())),
                row(t("report.minimumMaximum", "Narrowest / widest", "En dar / en geniş"), pair(interval.minimumObservedIntervalCent()) + " / "
                        + pair(interval.maximumObservedIntervalCent())),
                row(t("report.withinOneHc", "Within ±1 Hc", "±1 Hc içinde"), interval.count() == 0 ? "—"
                        : interval.withinOneHcCount() + " " + t("report.events", "events", "olay") + " • " + percentValue(interval.withinOneHcPercent()))));
        addPicture(document, hicazScatter(result, direction), directionLabel(direction) + " " + t("report.hicaz.degreeDeviations", "Hicaz degree deviations", "Hicaz derece sapmaları"));
    }

    private static String[] degreeStatsRow(String label, HicazDegreeStatistics s) {
        return row(label, Integer.toString(s.count()), pair(s.meanDeviationCent()),
                pair(s.medianDeviationCent()), pair(s.durationWeightedDeviationCent()),
                pair(s.minimumDeviationCent()) + " / " + pair(s.maximumDeviationCent()),
                s.count() == 0 ? "—" : s.withinOneHcCount() + " • "
                        + percentValue(s.withinOneHcPercent()));
    }

    private static void hicazPhrases(XWPFDocument document,
                                     HicazAugmentedIntervalStudyResult result) {
        heading(document, t("report.hicaz.detectedPhrases", "Detected directional phrases", "Tespit edilen yönsel cümleler"), 1);
        List<String[]> rows = result.phrases().stream().map(value -> row(
                Integer.toString(value.index()), directionLabel(value.direction()), value.pathLabel(),
                timeRange(value.startSeconds(), value.endSeconds()), seconds(value.durationSeconds()),
                percent(value.confidence()), value.included() ? primaryStatus() : auditStatus(),
                value.included() ? "—" : localizedReason(value.auditReason()))).toList();
        table(document, new String[]{"#", t("report.direction", "Direction", "Yön"), t("report.degreePath", "Degree path", "Derece yolu"),
                t("report.time", "Time", "Zaman"), t("report.duration", "Duration", "Süre"),
                t("report.confidence", "Confidence", "Güven"), t("report.status", "Status", "Durum"), t("report.description", "Description", "Açıklama")}, rows);

        heading(document, t("report.hicaz.directMeasurements", "Direct augmented-interval measurements", "Doğrudan artık aralık ölçümleri"), 2);
        List<String[]> intervalRows = result.directIntervals().stream().map(value -> row(
                Integer.toString(value.index()), Integer.toString(value.phraseIndex()),
                directionLabel(value.direction()), timeRange(value.startSeconds(), value.endSeconds()),
                pair(value.observedIntervalCent()), pair(value.deviationCent()),
                percent(value.confidence()), value.included() ? primaryStatus()
                        : localizedReason(value.auditReason()))).toList();
        table(document, new String[]{"#", t("report.phrase", "Phrase", "Cümle"), t("report.direction", "Direction", "Yön"),
                t("report.time", "Time", "Zaman"), t("report.observedInterval", "Observed interval C/Hc", "Gözlenen aralık C/Hc"),
                t("report.deviationFrom12Hc", "Deviation from 12 Hc", "12 Hc'den sapma"),
                t("report.confidence", "Confidence", "Güven"), t("report.status", "Status", "Durum")}, intervalRows);
    }

    private static void hicazEvents(XWPFDocument document,
                                    HicazAugmentedIntervalStudyResult result) {
        heading(document, t("report.hicaz.individualDegrees", "Individual degree events", "Derece olaylarının tek tek dökümü"), 1);
        List<String[]> rows = result.events().stream().sorted(Comparator.comparingDouble(
                HicazDegreeEvent::startSeconds)).map(value -> row(
                Integer.toString(value.index()), degreeLabel(value.degree()),
                timeRange(value.startSeconds(), value.endSeconds()), seconds(value.durationSeconds()),
                number(value.observedHertz(), 3) + " Hz", pair(value.observedRelativeCent()),
                pair(value.deviationCent()), intonationLabel(value), percent(value.confidence()),
                value.included() ? t("report.primaryCandidate", "Primary-statistics candidate", "Ana istatistik adayı")
                        : localizedReason(value.auditReason()))).toList();
        table(document, new String[]{"#", t("report.degree", "Degree", "Derece"), t("report.time", "Time", "Zaman"),
                t("report.duration", "Duration", "Süre"), "Hz", t("report.relativeCHc", "Relative to tonic C/Hc", "Karara göre C/Hc"),
                t("report.deviationCHc", "Deviation C/Hc", "Sapma C/Hc"), t("report.interpretation", "Interpretation", "Yorum"),
                t("report.confidence", "Confidence", "Güven"), t("report.status", "Status", "Durum")}, rows);
    }

    private static void glissandoTheory(XWPFDocument document,
                                        PitchPairGlissandoStudyResult result) {
        var request = result.request();
        heading(document, t("report.glissando.pairReference", "Selected pitch pair and theoretical reference", "Seçilen perde çifti ve kuramsal referans"), 1);
        table(document, new String[]{t("report.measure", "Measure", "Ölçüt"), t("report.lowerPitch", "Lower pitch", "Alt perde"), t("report.upperPitch", "Upper pitch", "Üst perde")}, List.of(
                row(t("report.pitch", "Pitch", "Perde"), request.lowerPitchName(), request.upperPitchName()),
                row(t("report.relativeToTonic", "Relative to tonic", "Karara göre"), pair(request.lowerRelativeCent()), pair(request.upperRelativeCent())),
                row(t("report.theoreticalFrequency", "Theoretical frequency", "Kuramsal frekans"), number(request.lowerHertz(), 4) + " Hz",
                        number(request.upperHertz(), 4) + " Hz")));
        paragraph(document, t("report.glissando.spanScope", "Theoretical span: {0}. Scan mode: {1}.", "Kuramsal genişlik {0}. Tarama biçimi: {1}.",
                pair(request.theoreticalSpanCent()), scopeLabel(request.scopeMode().label())));
    }

    private static void glissandoSummary(XWPFDocument document,
                                         PitchPairGlissandoStudyResult result) {
        heading(document, t("report.glissando.directionSummary", "Glissando summary by direction", "Yönlere göre glissando özeti"), 1);
        table(document, new String[]{t("report.direction", "Direction", "Yön"), "n", t("report.meanDuration", "Mean duration", "Ort. süre"),
                t("report.medianDuration", "Median duration", "Medyan süre"), t("report.meanStart", "Mean start C/Hc", "Ort. başlangıç C/Hc"),
                t("report.meanEnd", "Mean end C/Hc", "Ort. bitiş C/Hc"), t("report.meanSpan", "Mean span C/Hc", "Ort. genişlik C/Hc"),
                t("report.intervalError", "Interval error C/Hc", "Aralık hatası C/Hc"),
                t("report.meanSpeed", "Mean speed C/s / Hc/s", "Ort. hız C/sn / Hc/sn"),
                t("report.reverseMovement", "Reverse movement C/Hc", "Geri dönüş C/Hc"),
                t("report.monotonicity", "Monotonicity", "Tek yön %"), t("report.confidence", "Confidence", "Güven")},
                List.of(glissStatsRow(result.ascendingStatistics()),
                        glissStatsRow(result.descendingStatistics())));
        paragraph(document, t("report.glissando.countSummary", "Primary statistics contain {0} events; the audit layer contains {1} candidates.", "Ana istatistikte {0} olay; denetim katmanında {1} aday bulunmaktadır.",
                result.primaryEvents().size(), result.auditEvents().size()));
    }

    private static String[] glissStatsRow(PitchPairGlissandoStatistics s) {
        return row(directionLabel(s.direction()), Integer.toString(s.count()),
                seconds(s.meanDurationSeconds()), seconds(s.medianDurationSeconds()),
                pair(s.meanStartRelativeCent()), pair(s.meanEndRelativeCent()),
                pair(s.meanSpanCent()), pair(s.meanIntervalErrorCent()),
                finite(s.meanSignedSpeedCentPerSecond())
                        ? signed(s.meanSignedSpeedCentPerSecond(), 1) + " / "
                        + signed(s.meanSignedSpeedHcPerSecond(), 3) : "—",
                pair(s.meanReverseMovementCent()), percent(s.meanMonotonicity()), percent(s.meanConfidence()));
    }

    private static void glissandoDirectionSection(XWPFDocument document,
                                                  PitchPairGlissandoStudyResult result,
                                                  GlissandoDirection direction) throws IOException {
        heading(document, directionLabel(direction) + " " + t("report.glissandos", "glissandos", "glissandolar"), 1);
        List<PitchPairGlissandoEvent> events = direction == GlissandoDirection.ASCENDING
                ? result.ascendingEvents() : result.descendingEvents();
        if (events.isEmpty()) {
            paragraph(document, t("report.glissando.noneForDirection", "No {0} glissando meets the primary statistical criteria.", "Ana istatistik koşullarını karşılayan {0} glissando bulunmamıştır.",
                    ReportLocalization.lowerCase(directionLabel(direction))));
        } else {
            addPicture(document, glissandoChart(result, direction), directionLabel(direction) + " " + t("report.glissando.paths", "glissando paths", "glissando yolları"));
        }
    }

    private static void glissandoEvents(XWPFDocument document,
                                        PitchPairGlissandoStudyResult result) {
        heading(document, t("report.glissando.individualEvents", "Individual glissando events", "Glissando olaylarının tek tek dökümü"), 1);
        List<String[]> rows = result.events().stream().map(value -> row(
                Integer.toString(value.index()), directionLabel(value.direction()), shapeLabel(value.shape()),
                timeRange(value.startSeconds(), value.endSeconds()), seconds(value.durationSeconds()),
                number(value.startHertz(), 3) + " → " + number(value.endHertz(), 3) + " Hz",
                pair(value.startRelativeCent()) + " → " + pair(value.endRelativeCent()),
                pair(value.startEndpointDeviationCent()) + " → " + pair(value.endEndpointDeviationCent()),
                pair(value.observedSpanCent()), pair(value.intervalErrorCent()),
                signed(value.signedSpeedCentPerSecond(), 1) + " "
                        + t("unit.centPerSecond", "C/s", "C/sn") + " / "
                        + signed(value.signedSpeedHcPerSecond(), 3) + " "
                        + t("unit.hcPerSecond", "Hc/s", "Hc/sn"),
                pair(value.reverseMovementCent()), percent(value.monotonicity()),
                percent(value.voicedCoverage()), percent(value.confidence()),
                value.primary() ? primaryStatus() : localizedReason(value.auditReason()))).toList();
        table(document, new String[]{"#", t("report.direction", "Direction", "Yön"), t("report.shape", "Shape", "Biçim"),
                t("report.time", "Time", "Zaman"), t("report.duration", "Duration", "Süre"),
                t("report.startEndHz", "Start→end Hz", "Başlangıç→bitiş Hz"),
                t("report.relativeCHc", "Relative to tonic C/Hc", "Karara göre C/Hc"),
                t("report.endpointDeviations", "Endpoint deviations C/Hc", "Uç sapmaları C/Hc"),
                t("report.span", "Span", "Genişlik"), t("report.intervalError", "Interval error", "Aralık hatası"),
                t("report.speed", "Speed", "Hız"), t("report.reverseMovement", "Reverse movement", "Geri dönüş"),
                t("report.monotonicity", "Monotonicity", "Tek yön %"), t("report.f0Coverage", "F0 coverage", "F0 kapsamı"),
                t("report.confidence", "Confidence", "Güven"), t("report.status", "Status", "Durum")}, rows);
    }

    private static void methodAndLimits(XWPFDocument document, List<String> methods,
                                        List<String> limitations) {
        heading(document, t("report.methodSummary", "Method summary", "Yöntem özeti"), 1);
        for (String value : methods) bullet(document, value);
        heading(document, t("report.limitations", "Interpretive limitations", "Yorumlama sınırları"), 1);
        for (String value : limitations) bullet(document, value);
    }

    private static BufferedImage hicazScatter(HicazAugmentedIntervalStudyResult result,
                                              HicazPhraseDirection direction) {
        int width = 1300, height = 520;
        BufferedImage image = canvas(width, height);
        Graphics2D g = image.createGraphics(); prepare(g);
        int left = 120, right = width - 70, top = 80, bottom = height - 95;
        g.setColor(new Color(29, 39, 53)); g.setFont(new Font("SansSerif", Font.BOLD, 30));
        g.drawString(direction == null
                ? t("report.chart.overallDeviations", "Overall degree deviations", "Genel derece sapmaları")
                : directionLabel(direction) + " " + t("report.chart.degreeDeviations", "degree deviations", "derece sapmaları"), left, 45);
        double limit = 90.0;
        int zeroY = valueY(0.0, -limit, limit, top, bottom);
        g.setColor(new Color(45, 55, 70)); g.setStroke(new BasicStroke(3f));
        g.drawLine(left, zeroY, right, zeroY);
        g.setColor(new Color(190, 31, 52, 90)); g.setStroke(new BasicStroke(2f));
        int plusOne = valueY(AnalysisConfig.COMMA_CENT, -limit, limit, top, bottom);
        int minusOne = valueY(-AnalysisConfig.COMMA_CENT, -limit, limit, top, bottom);
        g.drawLine(left, plusOne, right, plusOne); g.drawLine(left, minusOne, right, minusOne);
        for (int degreeIndex = 0; degreeIndex < 3; degreeIndex++) {
            int degreeNumber = degreeIndex + 2;
            int x = left + (degreeIndex + 1) * (right - left) / 4;
            g.setColor(new Color(201, 210, 220)); g.drawLine(x, top, x, bottom);
            g.setFont(new Font("SansSerif", Font.BOLD, 24)); g.setColor(new Color(29, 39, 53));
            g.drawString(degreeNumber + ". " + t("report.degree", "Degree", "derece"), x - 55, bottom + 38);
            final int number = degreeNumber;
            List<HicazDegreeEvent> events = result.events().stream()
                    .filter(HicazDegreeEvent::included)
                    .filter(value -> value.degree().number() == number)
                    .filter(value -> direction == null || result.includedPhrases().stream()
                            .filter(phrase -> phrase.direction() == direction)
                            .anyMatch(phrase -> phrase.events().stream()
                                    .anyMatch(event -> event.index() == value.index())))
                    .toList();
            for (int i = 0; i < events.size(); i++) {
                HicazDegreeEvent event = events.get(i);
                int jitter = ((i % 7) - 3) * 8;
                int y = valueY(clamp(event.deviationCent(), -limit, limit), -limit, limit, top, bottom);
                g.setColor(new Color(169, 22, 50, 210)); g.fillOval(x + jitter - 7, y - 7, 14, 14);
            }
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 19)); g.setColor(new Color(91, 111, 134));
        g.drawString(t("report.chart.zeroExplanation", "The zero line is the theoretical centre; above is sharper and below is flatter.", "0 çizgisi kuramsal merkezi; üst taraf dik, alt taraf pest kullanımı gösterir."), left, height - 25);
        g.dispose(); return image;
    }

    private static BufferedImage glissandoChart(PitchPairGlissandoStudyResult result,
                                                GlissandoDirection direction) {
        int width = 1300, height = 560;
        BufferedImage image = canvas(width, height);
        Graphics2D g = image.createGraphics(); prepare(g);
        int left = 110, right = width - 70, top = 80, bottom = height - 95;
        var request = result.request();
        g.setColor(new Color(29, 39, 53)); g.setFont(new Font("SansSerif", Font.BOLD, 30));
        g.drawString(directionLabel(direction) + " " + t("report.glissando.paths", "glissando paths", "glissando yolları"), left, 45);
        double min = request.lowerRelativeCent() - AnalysisConfig.COMMA_CENT;
        double max = request.upperRelativeCent() + AnalysisConfig.COMMA_CENT;
        int lowY = valueY(request.lowerRelativeCent(), min, max, top, bottom);
        int highY = valueY(request.upperRelativeCent(), min, max, top, bottom);
        g.setStroke(new BasicStroke(2f)); g.setColor(new Color(35, 104, 168, 150));
        g.drawLine(left, lowY, right, lowY); g.drawLine(left, highY, right, highY);
        g.setFont(new Font("SansSerif", Font.PLAIN, 20)); g.setColor(new Color(35, 104, 168));
        g.drawString(request.lowerPitchName(), left + 5, lowY - 8);
        g.drawString(request.upperPitchName(), left + 5, highY - 8);
        List<PitchPairGlissandoEvent> events = direction == GlissandoDirection.ASCENDING
                ? result.ascendingEvents() : result.descendingEvents();
        int lane = Math.max(1, (right - left) / Math.max(1, events.size()));
        for (int i = 0; i < events.size(); i++) {
            PitchPairGlissandoEvent event = events.get(i);
            int x1 = left + i * lane + lane / 6;
            int x2 = left + (i + 1) * lane - lane / 6;
            int y1 = valueY(clamp(event.startRelativeCent(), min, max), min, max, top, bottom);
            int y2 = valueY(clamp(event.endRelativeCent(), min, max), min, max, top, bottom);
            g.setColor(new Color(169, 22, 50, 210));
            g.setStroke(new BasicStroke(event.shape().name().equals("DIRECT") ? 6f : 9f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(x1, y1, x2, y2); g.fillOval(x1 - 7, y1 - 7, 14, 14); g.fillOval(x2 - 7, y2 - 7, 14, 14);
            g.setFont(new Font("SansSerif", Font.PLAIN, 17)); g.setColor(new Color(91, 111, 134));
            g.drawString("#" + event.index(), x1, bottom + 28);
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 19)); g.setColor(new Color(91, 111, 134));
        g.drawString(t("report.chart.redLine", "Each red line shows the measured start and end positions of one event.", "Her kırmızı çizgi bir olayın ölçülen başlangıç ve bitiş konumunu gösterir."), left, height - 24);
        g.dispose(); return image;
    }

    private static BufferedImage canvas(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); g.setColor(new Color(252, 250, 247));
        g.fillRect(0, 0, width, height); g.dispose(); return image;
    }

    private static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static int valueY(double value, double min, double max, int top, int bottom) {
        return bottom - (int) Math.round((value - min) / (max - min) * (bottom - top));
    }

    private static void addPicture(XWPFDocument document, BufferedImage image, String description)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes.toByteArray())) {
            XWPFRun run = paragraph.createRun();
            run.addPicture(input, Document.PICTURE_TYPE_PNG, description + ".png",
                    Units.toEMU(500), Units.toEMU(215));
        } catch (Exception exception) {
            throw new IOException(t("report.chartInsertFailed", "The report chart could not be inserted", "Rapor grafiği eklenemedi"), exception);
        }
        XWPFParagraph caption = document.createParagraph();
        caption.setAlignment(ParagraphAlignment.CENTER);
        text(caption, description, 9, false, MUTED);
    }

    private static void table(XWPFDocument document, String[] headers, List<String[]> rows) {
        XWPFTable table = document.createTable(Math.max(1, rows.size() + 1), headers.length);
        table.setWidth("100%"); table.setTableAlignment(TableRowAlign.CENTER);
        styleBorders(table);
        table.getRow(0).setRepeatHeader(true);
        for (int column = 0; column < headers.length; column++) {
            setCell(table.getRow(0).getCell(column), headers[column], true, HEADER);
        }
        for (int row = 0; row < rows.size(); row++) {
            String[] values = rows.get(row);
            for (int column = 0; column < headers.length; column++) {
                String value = column < values.length ? values[column] : "";
                setCell(table.getRow(row + 1).getCell(column), value, false,
                        row % 2 == 0 ? "FFFFFF" : "F6F8FA");
            }
        }
        for (int row = 0; row < table.getNumberOfRows(); row++) {
            table.getRow(row).setCantSplitRow(true);
        }
        document.createParagraph().setSpacingAfter(40);
    }

    private static void styleBorders(XWPFTable table) {
        CTTblPr properties = table.getCTTbl().getTblPr();
        CTTblBorders borders = properties.isSetTblBorders() ? properties.getTblBorders()
                : properties.addNewTblBorders();
        for (CTBorder border : List.of(borders.addNewTop(), borders.addNewLeft(), borders.addNewBottom(),
                borders.addNewRight(), borders.addNewInsideH(), borders.addNewInsideV())) {
            border.setVal(STBorder.SINGLE); border.setColor(BORDER); border.setSz(BigInteger.valueOf(4));
        }
    }

    private static void setCell(XWPFTableCell cell, String value, boolean bold, String fill) {
        CTTcPr properties = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTShd shade = properties.isSetShd() ? properties.getShd() : properties.addNewShd();
        shade.setFill(fill);
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        paragraph.setSpacingAfter(0); paragraph.setSpacingBefore(0);
        text(paragraph, value == null || value.isBlank() ? "—" : value, 8.2, bold, INK);
    }

    private static void heading(XWPFDocument document, String value, int level) {
        XWPFParagraph paragraph = document.createParagraph();
        if (!paragraph.getCTP().isSetPPr()) paragraph.getCTP().addNewPPr();
        paragraph.setKeepNext(true);
        paragraph.setSpacingBefore(level == 1 ? 160 : 100);
        paragraph.setSpacingAfter(70);
        text(paragraph, value, level == 1 ? 18 : 14, true, INK);
        if (level == 1) {
            XWPFRun line = paragraph.createRun(); line.addBreak();
            XWPFRun accent = paragraph.createRun(); accent.setText("━━━━"); accent.setColor(CRIMSON);
            accent.setFontSize(8);
        }
    }

    private static void paragraph(XWPFDocument document, String value) {
        XWPFParagraph paragraph = document.createParagraph(); paragraph.setSpacingAfter(80);
        text(paragraph, value, 10.5, false, INK);
    }

    private static void bullet(XWPFDocument document, String value) {
        XWPFParagraph paragraph = document.createParagraph(); paragraph.setIndentationLeft(300);
        paragraph.setSpacingAfter(55); text(paragraph, "• " + value, 10.2, false, INK);
    }

    private static void pageBreak(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph(); paragraph.setPageBreak(true);
    }

    private static XWPFRun text(XWPFParagraph paragraph, String value, double size,
                                boolean bold, String color) {
        XWPFRun run = paragraph.createRun(); run.setText(value == null ? "" : value);
        run.setFontFamily(FONT); run.setFontSize(size); run.setBold(bold); run.setColor(color);
        return run;
    }

    private static String[] row(String... values) { return values; }
    private static boolean finite(double value) { return Double.isFinite(value); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static String number(double value, int decimals) {
        return ReportLocalization.number(value, decimals);
    }
    private static String signed(double value, int decimals) {
        return ReportLocalization.signed(value, decimals);
    }
    private static String pair(double cent) {
        return finite(cent) ? signed(cent, 2) + " C / "
                + signed(cent / AnalysisConfig.COMMA_CENT, 3) + " Hc" : "—";
    }
    private static String percent(double ratio) {
        return finite(ratio) ? ReportLocalization.percentValue(ratio * 100.0, 1) : "—";
    }
    private static String percentValue(double value) {
        return finite(value) ? ReportLocalization.percentValue(value, 1) : "—";
    }
    private static String seconds(double value) {
        return finite(value) ? number(value, 3) + " " + t("unit.secondsShort", "s", "sn") : "—";
    }
    private static String time(double seconds) {
        int total = (int) Math.max(0, Math.round(seconds));
        return String.format(java.util.Locale.ROOT, "%02d:%02d", total / 60, total % 60);
    }
    private static String timeRange(double start, double end) {
        return number(start, 3) + "–" + number(end, 3) + " " + t("unit.secondsShort", "s", "sn");
    }

    private static String[] degreeSummaryHeaders() {
        return new String[]{t("report.degree", "Degree", "Derece"), "n",
                t("report.mean", "Mean C/Hc", "Ortalama C/Hc"),
                t("report.median", "Median C/Hc", "Medyan C/Hc"),
                t("report.weightedMean", "Duration-weighted mean C/Hc", "Süre ağ. ort. C/Hc"),
                t("report.minimumMaximum", "Lowest / highest", "En pest / en dik"), "±1 Hc"};
    }

    private static String directionLabel(HicazPhraseDirection direction) {
        return direction == HicazPhraseDirection.ASCENDING
                ? t("report.ascending", "Ascending", "Çıkıcı")
                : t("report.descending", "Descending", "İnici");
    }

    private static String directionLabel(GlissandoDirection direction) {
        return direction == GlissandoDirection.ASCENDING
                ? t("report.ascending", "Ascending", "Çıkıcı")
                : t("report.descending", "Descending", "İnici");
    }

    private static String shapeLabel(com.makambox.studio.study.glissando.GlissandoShape shape) {
        return switch (shape) {
            case DIRECT -> t("report.shape.direct", "Direct", "Düz");
            case WITH_REVERSAL -> t("report.shape.reversal", "With reversal", "Geri dönüşlü");
            case MIXED -> t("report.shape.mixed", "Mixed", "Karma");
        };
    }

    private static String intonationLabel(HicazDegreeEvent event) {
        if (event.pest()) return t("report.flatter", "Flatter", "Pest");
        if (event.dik()) return t("report.sharper", "Sharper", "Dik");
        return t("report.theoreticalCentre", "Theoretical centre", "Kuramsal merkez");
    }

    private static String degreeLabel(HicazTetrachordDegree degree) {
        return switch (degree) {
            case DEGREE_1 -> t("report.degreeOneTonic", "Degree 1 (tonic)", "1. derece (karar)");
            case DEGREE_2 -> t("report.degreeTwo", "Degree 2", "2. derece");
            case DEGREE_3 -> t("report.degreeThree", "Degree 3", "3. derece");
            case DEGREE_4 -> t("report.degreeFour", "Degree 4", "4. derece");
        };
    }

    private static String scopeLabel(String value) {
        return switch (value) {
            case "Uç temasları zorunlu" -> t("report.scope.endpointContactRequired",
                    "Endpoint contacts required", "Uç temasları zorunlu");
            case "Seçili açık aralık" -> t("report.scope.openInterval",
                    "Selected open interval", "Seçili açık aralık");
            default -> ReportLocalization.displayLabel(value);
        };
    }

    private static String localizedReason(String value) {
        if (value == null || value.isBlank()) return "—";
        return java.util.Arrays.stream(value.split(";\\s*"))
                .map(ReportLocalization::reason)
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private static String primaryStatus() {
        return t("report.primaryStatistics", "Primary statistics", "Ana istatistik");
    }

    private static String auditStatus() {
        return t("report.audit", "Audit", "Denetim");
    }

    private static String t(String key, String english, String turkish, Object... arguments) {
        return ReportLocalization.text(key, english, turkish, arguments);
    }
}
