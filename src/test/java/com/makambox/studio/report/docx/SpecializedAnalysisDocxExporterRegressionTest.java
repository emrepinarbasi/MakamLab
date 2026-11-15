package com.makambox.studio.report.docx;

import com.makambox.studio.study.glissando.GlissandoDirection;
import com.makambox.studio.study.glissando.GlissandoEventStatus;
import com.makambox.studio.study.glissando.GlissandoScopeMode;
import com.makambox.studio.study.glissando.GlissandoShape;
import com.makambox.studio.study.glissando.PitchPairGlissandoEvent;
import com.makambox.studio.study.glissando.PitchPairGlissandoRequest;
import com.makambox.studio.study.glissando.PitchPairGlissandoStatistics;
import com.makambox.studio.study.glissando.PitchPairGlissandoStudyResult;
import com.makambox.studio.study.hicaz.HicazAugmentedIntervalStudyResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * Contract test for the two specialised reports.
 *
 * <p>The assertions intentionally protect the established report outline, the academic
 * C/Hc columns and the number of tables/figures. A report can therefore no longer pass
 * merely because it is a syntactically valid DOCX containing two isolated words.</p>
 */
public final class SpecializedAnalysisDocxExporterRegressionTest {
    private SpecializedAnalysisDocxExporterRegressionTest() {}

    public static void main(String[] args) throws Exception {
        SpecializedAnalysisMetadata metadata = new SpecializedAnalysisMetadata(
                "örnek.wav", Path.of("/tmp/örnek.wav"), 12.5, 44_100,
                "Tek kanal", "Hicaz", "Dügâh", 220.0,
                "Grafikten kullanıcı seçimi", "MakamLab V1");
        SpecializedAnalysisDocxExporter exporter = new SpecializedAnalysisDocxExporter();
        boolean keepOutputs = args.length > 0;
        Path folder = keepOutputs ? Path.of(args[0]).toAbsolutePath()
                : Files.createTempDirectory("makamlab-special-report-");
        Files.createDirectories(folder);
        Path hicazPath = folder.resolve("hicaz.docx");
        Path glissPath = folder.resolve("glissando.docx");
        try {
            HicazAugmentedIntervalStudyResult hicaz = new HicazAugmentedIntervalStudyResult(
                    220.0, List.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of());
            exporter.exportHicaz(metadata, hicaz, hicazPath);
            assertDocxContract(hicazPath, 10, 3,
                    List.of(
                            "Artık Aralık Analizi",
                            "Rapor nasıl okunur?",
                            "Kuramsal Hicaz dörtlüsü",
                            "Genel derece özeti",
                            "Çıkıcı cümleler",
                            "İnici cümleler",
                            "Tespit edilen yönsel cümleler",
                            "Doğrudan artık aralık ölçümleri",
                            "Derece olaylarının tek tek dökümü",
                            "Yöntem özeti",
                            "Yorumlama sınırları"),
                    List.of(
                            "Karara göre C",
                            "Karara göre Hc",
                            "Ortalama C/Hc",
                            "Medyan C/Hc",
                            "Süre ağ. ort. C/Hc",
                            "En pest / en dik",
                            "±1 Hc",
                            "Gözlenen aralık C/Hc",
                            "12 Hc'den sapma",
                            "Sapma C/Hc"));

            PitchPairGlissandoRequest request = PitchPairGlissandoRequest.academicDefaults(
                    "Hicaz", 220.0, "Dügâh", 0.0, "Bûselik", 203.773,
                    GlissandoScopeMode.OPEN_INTERVAL);
            PitchPairGlissandoEvent ascending = glissandoEvent(1, GlissandoDirection.ASCENDING);
            PitchPairGlissandoEvent descending = glissandoEvent(2, GlissandoDirection.DESCENDING);
            PitchPairGlissandoStudyResult glissando = new PitchPairGlissandoStudyResult(
                    request, List.of(ascending, descending),
                    glissandoStatistics(GlissandoDirection.ASCENDING),
                    glissandoStatistics(GlissandoDirection.DESCENDING));
            exporter.exportGlissando(metadata, glissando, glissPath);
            assertDocxContract(glissPath, 4, 2,
                    List.of(
                            "Glissando Analizi",
                            "Rapor nasıl okunur?",
                            "Seçilen perde çifti ve kuramsal referans",
                            "Yönlere göre glissando özeti",
                            "Çıkıcı glissandolar",
                            "İnici glissandolar",
                            "Glissando olaylarının tek tek dökümü",
                            "Yöntem özeti",
                            "Yorumlama sınırları"),
                    List.of(
                            "Ort. başlangıç C/Hc",
                            "Ort. bitiş C/Hc",
                            "Ort. genişlik C/Hc",
                            "Aralık hatası C/Hc",
                            "Ort. hız C/sn / Hc/sn",
                            "Geri dönüş C/Hc",
                            "Tek yön %",
                            "Karara göre C/Hc",
                            "Uç sapmaları C/Hc",
                            "F0 kapsamı"));
            System.out.println("SPECIALIZED_ANALYSIS_DOCX_EXPORTER_REGRESSION_OK");
        } finally {
            if (!keepOutputs) {
                Files.deleteIfExists(hicazPath);
                Files.deleteIfExists(glissPath);
                Files.deleteIfExists(folder);
            }
        }
    }

    private static PitchPairGlissandoEvent glissandoEvent(int index,
                                                           GlissandoDirection direction) {
        boolean ascending = direction == GlissandoDirection.ASCENDING;
        double startCent = ascending ? 0.0 : 203.773;
        double endCent = ascending ? 203.773 : 0.0;
        double startHertz = ascending ? 220.0 : 247.5;
        double endHertz = ascending ? 247.5 : 220.0;
        double speed = ascending ? 339.622 : -339.622;
        return new PitchPairGlissandoEvent(
                index, direction, ascending ? GlissandoShape.DIRECT : GlissandoShape.WITH_REVERSAL,
                GlissandoEventStatus.PRIMARY, "", index * 2.0, index * 2.0 + 0.6,
                startHertz, endHertz, startCent, endCent,
                startCent / 22.641509, endCent / 22.641509,
                1.2, -1.4, 1.2 / 22.641509, -1.4 / 22.641509,
                203.773, 9.0, 203.773, 9.0, 0.0, 0.0,
                speed, speed / 22.641509, ascending ? 0.0 : 5.0,
                ascending ? 0.0 : 5.0 / 22.641509,
                ascending ? 0.98 : 0.91, 0.97, 0.92, true, true);
    }

    private static PitchPairGlissandoStatistics glissandoStatistics(
            GlissandoDirection direction) {
        boolean ascending = direction == GlissandoDirection.ASCENDING;
        double startCent = ascending ? 0.0 : 203.773;
        double endCent = ascending ? 203.773 : 0.0;
        double speed = ascending ? 339.622 : -339.622;
        return new PitchPairGlissandoStatistics(direction, 1, 0.6, 0.6,
                startCent, startCent, endCent, endCent,
                203.773, 203.773, 0.0, 0.0,
                speed, speed, ascending ? 0.0 : 5.0,
                ascending ? 0.98 : 0.91, 0.92);
    }

    private static void assertDocxContract(Path path, int expectedTableCount,
                                           int expectedImageCount,
                                           List<String> orderedSections,
                                           List<String> requiredTableLabels) throws Exception {
        if (!Files.isRegularFile(path) || Files.size(path) < 1_000) {
            throw new AssertionError("DOCX üretilmedi: " + path);
        }
        try (ZipFile archive = new ZipFile(path.toFile())) {
            var entry = archive.getEntry("word/document.xml");
            if (entry == null) throw new AssertionError("Word document.xml eksik");
            String xml = new String(archive.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            if (xml.contains("{0}")) {
                throw new AssertionError("DOCX içinde çözümlenmemiş yer tutucu kaldı: {0}");
            }
            int cursor = -1;
            for (String value : orderedSections) {
                int position = xml.indexOf(value, cursor + 1);
                if (position < 0) throw new AssertionError("DOCX bölümü eksik: " + value);
                if (position <= cursor) throw new AssertionError("DOCX bölüm sırası bozuk: " + value);
                cursor = position;
            }
            for (String value : requiredTableLabels) {
                if (!xml.contains(value)) throw new AssertionError("DOCX tablo alanı eksik: " + value);
            }
            int tableCount = occurrences(xml, "<w:tbl>");
            if (tableCount != expectedTableCount) {
                throw new AssertionError("DOCX tablo sayısı değişti: beklenen="
                        + expectedTableCount + ", gerçek=" + tableCount);
            }
            long imageCount = archive.stream()
                    .filter(value -> !value.isDirectory() && value.getName().startsWith("word/media/"))
                    .count();
            if (imageCount != expectedImageCount) {
                throw new AssertionError("DOCX grafik sayısı değişti: beklenen="
                        + expectedImageCount + ", gerçek=" + imageCount);
            }
        }
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int cursor = 0; (cursor = text.indexOf(needle, cursor)) >= 0;
             cursor += needle.length()) count++;
        return count;
    }
}
