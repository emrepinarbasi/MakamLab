package com.makambox.studio.report.docx;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

/** Protects the dense open-interval report chart from merged pitch labels. */
public final class AcademicStudyTheoryLabelLayoutRegressionTest {
    public static void main(String[] args) {
        BufferedImage image = new BufferedImage(1600, 860, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        List<AcademicStudyCharts.TheoryLabelSpec> labels = List.of(
                label(95, "Rast", "alt sınır", "+0,00 Hc - hariç", true),
                label(420, "Nim Zirgüle", "makamın beklenen referansı", "+4,00 Hc", true),
                label(485, "Zirgüle", "", "+5,00 Hc", false),
                label(650, "Rast Eviç Kürdî arası", "", "+7,00 Hc", true),
                label(715, "Dik Zirgüle", "", "+8,00 Hc", false),
                label(780, "Dügâh", "", "+9,00 Hc", true),
                label(1360, "Kürdî", "makamın beklenen referansı", "+13,00 Hc", true),
                label(1450, "Dik Kürdî", "üst sınır", "+14,00 Hc - hariç", true));

        List<AcademicStudyCharts.TheoryLabelPlacement> placements =
                AcademicStudyCharts.layoutTheoryLabels(graphics, labels, 95, 1530, 430);
        graphics.dispose();

        if (placements.size() != labels.size()) {
            throw new AssertionError("Bütün kuramsal etiketler yerleştirilmedi");
        }
        for (AcademicStudyCharts.TheoryLabelPlacement placement : placements) {
            Rectangle bounds = placement.bounds();
            if (bounds.x < 95 || bounds.x + bounds.width > 1530) {
                throw new AssertionError("Kuramsal etiket grafik dışına taştı: " + bounds);
            }
            if (placement.lane() < 0 || placement.lane() > 3) {
                throw new AssertionError("Geçersiz etiket şeridi: " + placement.lane());
            }
        }
        for (int first = 0; first < placements.size(); first++) {
            for (int second = first + 1; second < placements.size(); second++) {
                Rectangle a = placements.get(first).bounds();
                Rectangle b = placements.get(second).bounds();
                if (a.intersects(b)) {
                    throw new AssertionError("Kuramsal etiketler üst üste geldi: " + a + " / " + b);
                }
            }
        }
        System.out.println("ACADEMIC_STUDY_THEORY_LABEL_LAYOUT_OK");
    }

    private static AcademicStudyCharts.TheoryLabelSpec label(
            int anchorX, String primary, String role, String position,
            boolean preferredAbove) {
        return new AcademicStudyCharts.TheoryLabelSpec(anchorX, primary, role,
                position, Color.BLACK, true, !role.isBlank(), preferredAbove);
    }
}
