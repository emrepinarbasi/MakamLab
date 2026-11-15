package com.makambox.studio.report.docx;

import com.makambox.studio.i18n.LocalizationService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Protects the three distinct event-count layers in single-pitch and open-interval reports. */
public final class EventCountReportingRegressionTest {
    private EventCountReportingRegressionTest() { }

    public static void main(String[] arguments) throws Exception {
        LocalizationService.getInstance().setLanguageTag("tr-TR");
        Path output = Files.createTempDirectory("makamlab-event-count-report-");
        AcademicStudyDocument single = DocxStudyReportRegressionTest.sampleReport();
        if (AcademicStudyCharts.theoryChartEvents(single).size() != single.contacts().size()) {
            throw new AssertionError("Kuramsal grafik bütün kanonik temasları göstermeli; "
                    + "yalnız ölçülebilir olaylara daralmamalı");
        }
        long chartMeasurable = AcademicStudyCharts.theoryChartEvents(single).stream()
                .filter(event -> event.measurable() && event.included()).count();
        if (chartMeasurable != single.measurableEvents().stream()
                .filter(AcademicStudyDocument.Event::included).count()) {
            throw new AssertionError("Kuramsal grafiğin bordo istatistik katmanı ölçülebilir olaylarla eşleşmeli");
        }
        Path singlePath = output.resolve("single.docx");
        new DocxStudyReportExporter().export(single, singlePath);
        String singleText = documentText(singlePath);
        require(singleText, "176 / 29 / 22", "single-pitch summary card count layers");
        require(singleText,
                "Olay sayısı — Hepsi: 176 • Ölçülebilir olaylar: 29 • Kararlı alt küme: 22.",
                "single-pitch count explanation");
        require(singleText, "Sayısal sonuçlar ve grafikler", "numerical results section");
        require(singleText, "Ölçülebilir olayların tek tek dökümü", "event appendix");
        require(singleText, "Karardan başlayan ilk dört sesin dizi / çeşni benzerliği",
                "profile-comparison section");
        require(singleText, "C'dir; merkezî eğilim", "formatted academic interpretation");
        if (singleText.contains("{2}")) {
            throw new AssertionError("Unresolved MessageFormat placeholder in Turkish DOCX");
        }

        AcademicStudyDocument.Theory source = single.theory();
        AcademicStudyDocument.Theory intervalTheory = new AcademicStudyDocument.Theory(
                source.makamName(), source.tonicNote(), "Rast–Dügâh arası (sınırlar hariç)",
                source.tonicHz(), 101.887, 4.5, 131.39, source.referenceSystem(),
                "Perdeler arası analiz", "Rast", 0.0, 0.0, source.tonicHz(),
                "Dügâh", 203.774, 9.0, 139.30, false,
                source.pitchCenters(), List.of());
        AcademicStudyDocument interval = new AcademicStudyDocument(
                "MakamLab — Perdeler Arası Analiz", single.source(), intervalTheory,
                single.tonic(), single.contacts(), single.tonicTrace(),
                single.profileComparisons(), single.methodNotes(), single.limitations(),
                single.generatedAt(), List.of(), single.directionalFirstFourProfiles());
        Path intervalPath = output.resolve("interval.docx");
        new DocxStudyReportExporter().export(interval, intervalPath);
        String intervalText = documentText(intervalPath);
        require(intervalText, "176 / 29 / 22", "open-interval summary card count layers");
        require(intervalText,
                "Olay sayısı — Seçilen açık aralık: 176 • Ölçülebilir olaylar: 29 • Kararlı alt küme: 22.",
                "open-interval count explanation");

        System.out.println("EVENT_COUNT_REPORTING_OK single=" + singlePath
                + " interval=" + intervalPath);
    }

    private static String documentText(Path path) throws Exception {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(path))) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(value -> text.append(value.getText()).append('\n'));
            document.getTables().forEach(table -> table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> text.append(cell.getText()).append('\n'))));
            return text.toString();
        }
    }

    private static void require(String text, String expected, String label) {
        if (!text.contains(expected)) {
            throw new AssertionError("Missing " + label + ": " + expected);
        }
    }
}
