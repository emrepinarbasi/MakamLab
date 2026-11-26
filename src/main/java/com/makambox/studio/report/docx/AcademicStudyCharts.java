package com.makambox.studio.report.docx;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** High-resolution, deterministic Java2D charts embedded in the DOCX report. */
public final class AcademicStudyCharts {
    private static final Color INK = new Color(24, 35, 52);
    private static final Color MUTED = new Color(91, 111, 134);
    private static final Color GRID = new Color(218, 227, 236);
    private static final Color RED = new Color(190, 31, 52);
    private static final Color BURGUNDY = new Color(143, 24, 50);
    private static final Color BLUE = new Color(45, 100, 210);
    private static final Color TEAL = new Color(23, 146, 133);
    private static final Color PALE = new Color(180, 198, 214, 120);
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 720;
    private static final int THEORY_CHART_HEIGHT = 860;

    public BufferedImage tonicEvidence(AcademicStudyDocument document) {
        BufferedImage image = canvas(t("report.chart.tonicEvidence",
                "Tonic audit in the final tonal region", "Karar sesinin son tonal bölgede denetimi"));
        Graphics2D g = graphics(image);
        int left = 120, right = WIDTH - 60, top = 110, bottom = HEIGHT - 105;
        List<AcademicStudyDocument.TracePoint> points = document.tonicTrace();
        if (points.isEmpty()) {
            empty(g, t("report.chart.noTonicTrace",
                    "No tonic trace is available in the report data.", "Karar izi rapor girdisinde bulunmuyor."));
            return image;
        }
        double minTime = points.stream().mapToDouble(AcademicStudyDocument.TracePoint::timeSeconds).min().orElse(0);
        double maxTime = points.stream().mapToDouble(AcademicStudyDocument.TracePoint::timeSeconds).max().orElse(minTime + 1);
        double tonic = document.tonic().hertz();
        double minHz = points.stream().mapToDouble(AcademicStudyDocument.TracePoint::hertz)
                .filter(value -> value > 0).min().orElse(tonic * 0.8);
        double maxHz = points.stream().mapToDouble(AcademicStudyDocument.TracePoint::hertz)
                .filter(value -> value > 0).max().orElse(tonic * 1.2);
        minHz = Math.min(minHz, tonic * 0.92);
        maxHz = Math.max(maxHz, tonic * 1.08);
        axes(g, left, right, top, bottom,
                t("report.chart.recordingTimeSeconds", "Recording time (s)", "Kayıt zamanı (sn)"),
                "F0 (Hz)", minTime, maxTime, minHz, maxHz);
        int selectionLeft = x(document.tonic().startSeconds(), minTime, maxTime, left, right);
        int selectionRight = x(document.tonic().endSeconds(), minTime, maxTime, left, right);
        g.setColor(new Color(25, 160, 135, 35));
        g.fillRect(Math.min(selectionLeft, selectionRight), top,
                Math.max(2, Math.abs(selectionRight - selectionLeft)), bottom - top);
        g.setColor(TEAL);
        int tonicY = y(tonic, minHz, maxHz, top, bottom);
        g.setStroke(new BasicStroke(3f));
        g.drawLine(left, tonicY, right, tonicY);
        label(g, t("report.chart.tonicHz", "Tonic {0} Hz", "Karar {0} Hz",
                        ReportLocalization.number(tonic, 3)), left + 12, tonicY - 10, TEAL);
        for (AcademicStudyDocument.TracePoint point : points) {
            if (!(point.hertz() > 0)) continue;
            g.setColor(point.accepted() ? BLUE : PALE);
            int radius = point.accepted() ? 5 : 3;
            int px = x(point.timeSeconds(), minTime, maxTime, left, right);
            int py = y(point.hertz(), minHz, maxHz, top, bottom);
            g.fillOval(px - radius, py - radius, radius * 2, radius * 2);
        }
        g.dispose();
        return image;
    }

    public BufferedImage timeline(AcademicStudyDocument document) {
        BufferedImage image = canvas(t("report.chart.targetDeviationTimeline",
                "Deviation of {0} contacts over time", "{0} temaslarının zaman içindeki sapması",
                document.theory().targetNote()));
        Graphics2D g = graphics(image);
        int left = 120, right = WIDTH - 95, top = 110, bottom = HEIGHT - 115;
        List<AcademicStudyDocument.Event> events = document.contacts();
        if (events.isEmpty()) {
            empty(g, t("report.chart.noTargetContact",
                    "No target-pitch contact was found.", "Hedef perde teması bulunmuyor."));
            return image;
        }
        double maxTime = Math.max(1.0, document.source().durationSeconds());
        double[] deviations = events.stream().mapToDouble(AcademicStudyDocument.Event::deviationCent).toArray();
        double min = Math.min(-AcademicStudyDocument.HC_CENT, Arrays.stream(deviations).min().orElse(-30));
        double max = Math.max(AcademicStudyDocument.HC_CENT, Arrays.stream(deviations).max().orElse(30));
        double margin = Math.max(5.0, (max - min) * 0.08);
        min -= margin; max += margin;
        axes(g, left, right, top, bottom,
                t("report.chart.recordingTimeMinutes", "Recording time (minutes)", "Kayıt zamanı (dakika)"),
                t("report.chart.deviationFromTheoryC", "Deviation from theoretical centre (C)", "Kuramsal merkezden sapma (C)"),
                0, maxTime / 60.0, min, max);
        int zero = y(0, min, max, top, bottom);
        g.setColor(TEAL); g.setStroke(new BasicStroke(3f)); g.drawLine(left, zero, right, zero);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{9f, 7f}, 0));
        g.setColor(new Color(80, 117, 150));
        for (double limit : new double[]{-AcademicStudyDocument.HC_CENT, AcademicStudyDocument.HC_CENT}) {
            int py = y(limit, min, max, top, bottom); g.drawLine(left, py, right, py);
        }
        for (AcademicStudyDocument.Event event : events) {
            int px = x(event.startSeconds() / 60.0, 0, maxTime / 60.0, left, right);
            int py = y(event.deviationCent(), min, max, top, bottom);
            if (!event.measurable() || !event.included()) {
                g.setColor(PALE); g.fillOval(px - 4, py - 4, 8, 8);
            } else if (document.directionalSegahAnalysis()) {
                g.setColor(directionColor(event.direction()));
                g.fillOval(px - 8, py - 8, 16, 16);
            } else {
                g.setColor(event.deviationCent() < 0 ? RED : BLUE);
                g.fillOval(px - 7, py - 7, 14, 14);
                if (event.stable()) {
                    g.setColor(INK); g.setStroke(new BasicStroke(2.2f));
                    g.drawLine(px - 8, py - 8, px + 8, py + 8);
                    g.drawLine(px - 8, py + 8, px + 8, py - 8);
                }
            }
        }
        legend(g, left + 20, bottom + 55, document.directionalSegahAnalysis()
                ? List.of(new Legend(t("report.chart.excludedAudit", "Excluded/audit", "Dışlanan/denetim"), PALE),
                new Legend(directionLabel("Çıkıcı"), BLUE), new Legend(directionLabel("İnici"), RED),
                new Legend(directionLabel("Segâh kalışı"), TEAL))
                : List.of(new Legend(t("report.chart.briefContact", "Brief contact", "Kısa temas"), PALE),
                new Legend(t("report.flat", "Flat", "Pest"), RED),
                new Legend(t("report.sharp", "Sharp", "Dik"), BLUE),
                new Legend(t("report.chart.stableEvent", "Stable event (x)", "Kararlı olay (x)"), INK)));
        g.dispose();
        return image;
    }

    public BufferedImage histogram(AcademicStudyDocument document) {
        BufferedImage image = canvas(t("report.chart.deviationDistribution",
                "Deviation distribution of measurable events", "Ölçülebilir olayların sapma dağılımı"));
        Graphics2D g = graphics(image);
        List<AcademicStudyDocument.Event> events = document.measurableEvents().stream()
                .filter(AcademicStudyDocument.Event::included).toList();
        if (events.isEmpty()) {
            empty(g, t("report.chart.noMeasurableEvent",
                    "No measurable target-pitch event was found.", "Ölçülebilir hedef-perde olayı bulunmuyor."));
            return image;
        }
        int left = 120, right = WIDTH - 60, top = 110, bottom = HEIGHT - 105;
        double min = Math.floor(events.stream().mapToDouble(AcademicStudyDocument.Event::deviationCent)
                .min().orElse(-10) / 5.0) * 5.0 - 2.5;
        double max = Math.ceil(events.stream().mapToDouble(AcademicStudyDocument.Event::deviationCent)
                .max().orElse(10) / 5.0) * 5.0 + 2.5;
        if (max - min < 20) { min -= 10; max += 10; }
        int bins = Math.max(8, Math.min(24, (int) Math.ceil((max - min) / 5.0)));
        int[] counts = new int[bins];
        for (AcademicStudyDocument.Event event : events) {
            int bin = (int) Math.floor((event.deviationCent() - min) / (max - min) * bins);
            counts[Math.max(0, Math.min(bins - 1, bin))]++;
        }
        int maximum = Arrays.stream(counts).max().orElse(1);
        axes(g, left, right, top, bottom,
                t("report.deviationC", "Deviation (C)", "Sapma (C)"),
                t("report.eventCount", "Event count", "Olay sayısı"), min, max, 0, maximum + 1);
        double binWidth = (right - left) / (double) bins;
        for (int i = 0; i < bins; i++) {
            double center = min + (i + .5) * (max - min) / bins;
            int height = (int) Math.round(counts[i] / (double) (maximum + 1) * (bottom - top));
            g.setColor(center < 0 ? new Color(211, 72, 86) : new Color(70, 117, 222));
            g.fillRect((int) Math.round(left + i * binWidth + 2), bottom - height,
                    Math.max(1, (int) Math.round(binWidth - 4)), height);
        }
        double median = document.measurableStatistics().medianCent();
        int medianX = x(median, min, max, left, right);
        g.setColor(INK); g.setStroke(new BasicStroke(4f)); g.drawLine(medianX, top, medianX, bottom);
        label(g, t("report.chart.medianC", "Median {0} C", "Medyan {0} C",
                        ReportLocalization.signed(median, 2)),
                Math.min(right - 230, medianX + 10), top + 28, INK);
        g.dispose();
        return image;
    }

    public BufferedImage directions(AcademicStudyDocument document) {
        BufferedImage image = canvas(t("report.chart.deviationByDirection",
                "Deviation by net melodic direction", "Net ezgi yönüne göre sapma"));
        Graphics2D g = graphics(image);
        Map<String, List<Double>> groups = new LinkedHashMap<>();
        for (AcademicStudyDocument.Event event : document.measurableEvents()) {
            if (event.included()) groups.computeIfAbsent(event.direction(), ignored -> new ArrayList<>())
                    .add(event.deviationCent());
        }
        if (groups.isEmpty()) {
            empty(g, t("report.chart.noDirectionGroup", "No direction group is available.", "Yön grubu bulunmuyor."));
            return image;
        }
        int left = 120, right = WIDTH - 60, top = 110, bottom = HEIGHT - 115;
        double min = groups.values().stream().flatMap(List::stream).mapToDouble(Double::doubleValue).min().orElse(-30);
        double max = groups.values().stream().flatMap(List::stream).mapToDouble(Double::doubleValue).max().orElse(30);
        min = Math.min(min - 5, -AcademicStudyDocument.HC_CENT * 1.2);
        max = Math.max(max + 5, AcademicStudyDocument.HC_CENT * 1.2);
        axes(g, left, right, top, bottom,
                t("report.directionGroup", "Direction group", "Yön grubu"),
                t("report.deviationC", "Deviation (C)", "Sapma (C)"), 0, groups.size(), min, max);
        int groupIndex = 0;
        for (Map.Entry<String, List<Double>> entry : groups.entrySet()) {
            double[] values = entry.getValue().stream().mapToDouble(Double::doubleValue).sorted().toArray();
            double q1 = percentile(values, .25), median = percentile(values, .5), q3 = percentile(values, .75);
            double low = values[0], high = values[values.length - 1];
            int center = left + (int) Math.round((groupIndex + .5) / groups.size() * (right - left));
            int boxWidth = Math.max(90, (right - left) / groups.size() / 3);
            g.setColor(new Color(210, 225, 248));
            g.fillRect(center - boxWidth / 2, y(q3, min, max, top, bottom), boxWidth,
                    y(q1, min, max, top, bottom) - y(q3, min, max, top, bottom));
            g.setColor(BLUE); g.setStroke(new BasicStroke(3f));
            g.drawRect(center - boxWidth / 2, y(q3, min, max, top, bottom), boxWidth,
                    y(q1, min, max, top, bottom) - y(q3, min, max, top, bottom));
            g.drawLine(center, y(low, min, max, top, bottom), center, y(high, min, max, top, bottom));
            g.setColor(RED); g.setStroke(new BasicStroke(4f));
            g.drawLine(center - boxWidth / 2, y(median, min, max, top, bottom),
                    center + boxWidth / 2, y(median, min, max, top, bottom));
            for (double value : values) {
                int jitter = (int) Math.round(((value * 31 + groupIndex * 19) % 1.0 - .5) * boxWidth * .7);
                g.setColor(TEAL); int py = y(value, min, max, top, bottom); g.fillOval(center + jitter - 5, py - 5, 10, 10);
            }
            centered(g, directionLabel(entry.getKey()), center, bottom + 42, INK);
            groupIndex++;
        }
        int zero = y(0, min, max, top, bottom);
        g.setColor(INK); g.setStroke(new BasicStroke(2f)); g.drawLine(left, zero, right, zero);
        g.dispose();
        return image;
    }

    /**
     * One report-ready panel that keeps the overall deviation distribution and
     * the direction groups on the same visual scale.  Only measurable events
     * accepted into the study are drawn; rejected audit rows remain available
     * in the detailed event table but cannot distort this summary.
     */
    public BufferedImage distributionAndDirections(AcademicStudyDocument document) {
        List<AcademicStudyDocument.Event> events = document.measurableEvents().stream()
                .filter(AcademicStudyDocument.Event::included).toList();
        BufferedImage image = new BufferedImage(WIDTH, 820, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(image);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(INK);
        g.setFont(new Font("SansSerif", Font.BOLD, 30));
        g.drawString(t("report.chart.measurableEventDistribution",
                "Distribution of {0} measurable events", "Ölçülebilir {0} olayın dağılımı",
                ReportLocalization.service().formatInteger(events.size())), 55, 58);
        g.setColor(RED);
        g.fillRoundRect(55, 74, 115, 7, 4, 4);
        if (events.isEmpty()) {
            g.setColor(MUTED);
            g.setFont(new Font("SansSerif", Font.BOLD, 28));
            centered(g, t("report.chart.noReportableEvent",
                    "No reportable measurable event was found.", "Raporlanabilir ölçülebilir olay bulunmuyor."),
                    WIDTH / 2, 405, MUTED);
            g.dispose();
            return image;
        }

        List<Double> deviations = events.stream()
                .map(AcademicStudyDocument.Event::deviationCent).toList();
        double dataMin = deviations.stream().mapToDouble(Double::doubleValue).min().orElse(-10.0);
        double dataMax = deviations.stream().mapToDouble(Double::doubleValue).max().orElse(10.0);
        double minimum = Math.floor(Math.min(dataMin - 4.0,
                -AcademicStudyDocument.HC_CENT * 1.18) / 5.0) * 5.0;
        double maximum = Math.ceil(Math.max(dataMax + 4.0,
                AcademicStudyDocument.HC_CENT * 1.18) / 5.0) * 5.0;
        if (maximum - minimum < 30.0) {
            double center = (minimum + maximum) / 2.0;
            minimum = center - 15.0;
            maximum = center + 15.0;
        }

        int leftChartLeft = 95, leftChartRight = 730;
        int rightChartLeft = 865, rightChartRight = 1540;
        int chartTop = 145, chartBottom = 665;
        g.setColor(INK);
        g.setFont(new Font("SansSerif", Font.BOLD, 25));
        g.drawString(t("report.distribution", "Distribution", "Dağılım"), leftChartLeft, 118);
        g.drawString(document.directionalSegahAnalysis()
                ? t("report.bySegahContext", "By Segâh usage context", "Segâh kullanım bağlamına göre")
                : t("report.byNetDirection", "By net melodic direction", "Net ezgi yönüne göre"),
                rightChartLeft, 118);

        int binCount = Math.max(8, Math.min(18,
                (int) Math.ceil((maximum - minimum) / 5.0)));
        int[] counts = new int[binCount];
        for (double value : deviations) {
            int bin = (int) Math.floor((value - minimum) / (maximum - minimum) * binCount);
            counts[Math.max(0, Math.min(binCount - 1, bin))]++;
        }
        int maximumCount = Math.max(1, Arrays.stream(counts).max().orElse(1));
        numericAxes(g, leftChartLeft, leftChartRight, chartTop, chartBottom,
                minimum, maximum, 0.0, maximumCount, true,
                t("report.deviationC", "Deviation (C)", "Sapma (C)"),
                t("report.eventCount", "Event count", "Olay sayısı"), 820);
        double binWidth = (leftChartRight - leftChartLeft) / (double) binCount;
        for (int index = 0; index < binCount; index++) {
            int height = (int) Math.round(counts[index] / (double) maximumCount
                    * (chartBottom - chartTop));
            int x = (int) Math.round(leftChartLeft + index * binWidth + 2);
            int width = Math.max(1, (int) Math.round(binWidth - 4));
            g.setColor(new Color(20, 171, 158));
            g.fillRect(x, chartBottom - height, width, height);
        }
        int zeroX = x(0.0, minimum, maximum, leftChartLeft, leftChartRight);
        g.setColor(INK);
        g.setStroke(new BasicStroke(3f));
        g.drawLine(zeroX, chartTop, zeroX, chartBottom);
        double median = percentile(deviations.stream().mapToDouble(Double::doubleValue).sorted().toArray(), .5);
        int medianX = x(median, minimum, maximum, leftChartLeft, leftChartRight);
        g.setColor(RED);
        g.setStroke(new BasicStroke(5f));
        g.drawLine(medianX, chartTop, medianX, chartBottom);
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g.setColor(RED);
        g.fillRect(leftChartLeft + 10, chartTop + 12, 42, 5);
        g.setColor(INK);
        g.drawString(t("report.chart.medianC", "Median {0} C", "Medyan {0} C",
                        ReportLocalization.signed(median, 2)),
                leftChartLeft + 62, chartTop + 21);

        LinkedHashMap<String, List<Double>> grouped = orderedDirectionGroups(events);
        numericAxes(g, rightChartLeft, rightChartRight, chartTop, chartBottom,
                0.0, grouped.size(), minimum, maximum, false,
                "", t("report.deviationC", "Deviation (C)", "Sapma (C)"), 820);
        dashedHorizontal(g, rightChartLeft, rightChartRight,
                y(AcademicStudyDocument.HC_CENT, minimum, maximum, chartTop, chartBottom));
        dashedHorizontal(g, rightChartLeft, rightChartRight,
                y(-AcademicStudyDocument.HC_CENT, minimum, maximum, chartTop, chartBottom));
        int zeroY = y(0.0, minimum, maximum, chartTop, chartBottom);
        g.setColor(INK);
        g.setStroke(new BasicStroke(3f));
        g.drawLine(rightChartLeft, zeroY, rightChartRight, zeroY);

        int groupIndex = 0;
        for (Map.Entry<String, List<Double>> entry : grouped.entrySet()) {
            double[] values = entry.getValue().stream().mapToDouble(Double::doubleValue).sorted().toArray();
            double q1 = percentile(values, .25);
            double groupMedian = percentile(values, .5);
            double q3 = percentile(values, .75);
            double iqr = q3 - q1;
            double lowFence = q1 - 1.5 * iqr;
            double highFence = q3 + 1.5 * iqr;
            double lowWhisker = Arrays.stream(values).filter(value -> value >= lowFence)
                    .min().orElse(values[0]);
            double highWhisker = Arrays.stream(values).filter(value -> value <= highFence)
                    .max().orElse(values[values.length - 1]);
            int center = rightChartLeft + (int) Math.round((groupIndex + .5)
                    / grouped.size() * (rightChartRight - rightChartLeft));
            int boxWidth = Math.max(72,
                    Math.min(125, (rightChartRight - rightChartLeft) / grouped.size() / 2));
            int q3Y = y(q3, minimum, maximum, chartTop, chartBottom);
            int q1Y = y(q1, minimum, maximum, chartTop, chartBottom);
            g.setColor(new Color(205, 225, 247));
            g.fillRect(center - boxWidth / 2, q3Y, boxWidth, Math.max(1, q1Y - q3Y));
            g.setColor(BLUE);
            g.setStroke(new BasicStroke(3f));
            g.drawRect(center - boxWidth / 2, q3Y, boxWidth, Math.max(1, q1Y - q3Y));
            g.drawLine(center, y(lowWhisker, minimum, maximum, chartTop, chartBottom), center,
                    y(highWhisker, minimum, maximum, chartTop, chartBottom));
            g.drawLine(center - boxWidth / 4,
                    y(lowWhisker, minimum, maximum, chartTop, chartBottom),
                    center + boxWidth / 4,
                    y(lowWhisker, minimum, maximum, chartTop, chartBottom));
            g.drawLine(center - boxWidth / 4,
                    y(highWhisker, minimum, maximum, chartTop, chartBottom),
                    center + boxWidth / 4,
                    y(highWhisker, minimum, maximum, chartTop, chartBottom));
            g.setColor(RED);
            g.setStroke(new BasicStroke(5f));
            g.drawLine(center - boxWidth / 2,
                    y(groupMedian, minimum, maximum, chartTop, chartBottom),
                    center + boxWidth / 2,
                    y(groupMedian, minimum, maximum, chartTop, chartBottom));
            for (int valueIndex = 0; valueIndex < values.length; valueIndex++) {
                double value = values[valueIndex];
                int jitter = (int) Math.round(Math.sin((valueIndex + 1) * 2.17
                        + groupIndex * .71) * boxWidth * .27);
                int pointX = center + jitter;
                int pointY = y(value, minimum, maximum, chartTop, chartBottom);
                g.setColor(TEAL);
                g.fillOval(pointX - 6, pointY - 6, 12, 12);
                if (value < lowFence || value > highFence) {
                    g.setColor(INK);
                    g.setStroke(new BasicStroke(2f));
                    g.drawOval(pointX - 8, pointY - 8, 16, 16);
                }
            }
            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            centered(g, directionLabel(entry.getKey()), center, chartBottom + 34, INK);
            groupIndex++;
        }
        g.dispose();
        return image;
    }

    private static LinkedHashMap<String, List<Double>> orderedDirectionGroups(
            List<AcademicStudyDocument.Event> events) {
        LinkedHashMap<String, List<Double>> collected = new LinkedHashMap<>();
        for (AcademicStudyDocument.Event event : events) {
            collected.computeIfAbsent(event.direction(), ignored -> new ArrayList<>())
                    .add(event.deviationCent());
        }
        LinkedHashMap<String, List<Double>> groups = new LinkedHashMap<>();
        for (String direction : List.of("Çıkıcı", "İnici", "Segâh kalışı", "Yatay/Karma")) {
            List<Double> values = collected.remove(direction);
            if (values != null && !values.isEmpty()) groups.put(direction, values);
        }
        groups.putAll(collected);
        return groups;
    }

    private static void numericAxes(Graphics2D g, int left, int right, int top, int bottom,
                                    double xMin, double xMax, double yMin, double yMax,
                                    boolean xNumbers, String xLabel, String yLabel,
                                    int imageHeight) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        for (int index = 0; index <= 5; index++) {
            int px = left + (right - left) * index / 5;
            int py = bottom - (bottom - top) * index / 5;
            g.setColor(GRID);
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(px, top, px, bottom);
            g.drawLine(left, py, right, py);
            g.setColor(MUTED);
            if (xNumbers) {
                centered(g, formatAxis(xMin + (xMax - xMin) * index / 5.0),
                        px, bottom + 27, MUTED);
            }
            String yText = formatAxis(yMin + (yMax - yMin) * index / 5.0);
            g.drawString(yText, left - 12 - g.getFontMetrics().stringWidth(yText), py + 6);
        }
        g.setColor(INK);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(left, bottom, right, bottom);
        g.drawLine(left, top, left, bottom);
        if (!xLabel.isBlank()) centered(g, xLabel, (left + right) / 2, imageHeight - 36, INK);
        g.rotate(-Math.PI / 2);
        centered(g, yLabel, -(top + bottom) / 2, left - 55, INK);
        g.rotate(Math.PI / 2);
    }

    private static void dashedHorizontal(Graphics2D g, int left, int right, int y) {
        g.setColor(new Color(114, 145, 174));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{8f, 6f}, 0f));
        g.drawLine(left, y, right, y);
    }

    /**
     * Draws the theoretical decision geometry used by the event classifier.
     * The shaded single-pitch band is bounded by midpoints to the neighbouring
     * canonical centres; it is deliberately independent of the user's display
     * tolerance.  Interval studies instead show the selected open interval.
     */
    public BufferedImage theoryClassification(AcademicStudyDocument document) {
        AcademicStudyDocument.Theory theory = document.theory();
        if (theory.directionalSegahAnalysis()) {
            return directionalSegahTheoryClassification(document, "Çıkıcı");
        }
        String title = theory.directionalSegahAnalysis()
                ? t("report.chart.segahCandidateArea", "Segâh candidate area and reference in {0}",
                "{0} dizisinde Segâh aday alanı ve referansı", theory.makamName())
                : theory.intervalAnalysis()
                ? t("report.chart.openInterval", "Open analysis interval in {0}",
                "{0} dizisinde açık inceleme aralığı", theory.makamName())
                : t("report.chart.theoreticalTargetPosition", "Theoretical position of the target pitch in {0}",
                "{0} dizisinde hedef perdenin kuramsal yeri", theory.makamName());
        // Theory charts carry several long, role-bearing pitch labels.  Give
        // them a little more vertical room than the compact statistical
        // charts instead of forcing every label onto the same staff line.
        BufferedImage image = canvas(title, THEORY_CHART_HEIGHT);
        Graphics2D g = graphics(image);
        List<TheoryMarker> all = theoryMarkers(theory);
        List<TheoryMarker> visible = theory.openBoundaryAnalysis()
                ? intervalMarkers(theory, all) : singlePitchMarkers(theory, all);
        if (visible.isEmpty()) {
            empty(g, t("report.chart.noTheoryCenters",
                    "No theoretical pitch centres are available in the report data.",
                    "Kuramsal perde merkezleri rapor girdisinde bulunmuyor."));
            return image;
        }

        double bandMinimum;
        double bandMaximum;
        if (theory.openBoundaryAnalysis()) {
            bandMinimum = Math.min(theory.lowerBoundaryHc(), theory.upperBoundaryHc());
            bandMaximum = Math.max(theory.lowerBoundaryHc(), theory.upperBoundaryHc());
        } else {
            double target = theory.targetRelativeHc();
            double lower = all.stream().mapToDouble(TheoryMarker::hc)
                    .filter(value -> value < target - .04).max().orElse(target - 1.0);
            double upper = all.stream().mapToDouble(TheoryMarker::hc)
                    .filter(value -> value > target + .04).min().orElse(target + 1.0);
            bandMinimum = (lower + target) / 2.0;
            bandMaximum = (target + upper) / 2.0;
        }

        double minimum = Math.min(bandMinimum,
                visible.stream().mapToDouble(TheoryMarker::hc).min().orElse(bandMinimum)) - .7;
        double maximum = Math.max(bandMaximum,
                visible.stream().mapToDouble(TheoryMarker::hc).max().orElse(bandMaximum)) + .7;
        List<AcademicStudyDocument.Event> eventPoints = theoryChartEvents(document);
        if (!eventPoints.isEmpty()) {
            double eventMinimum = eventPoints.stream()
                    .mapToDouble(AcademicStudyDocument.Event::relativeHc)
                    .min().orElse(Double.POSITIVE_INFINITY);
            double eventMaximum = eventPoints.stream()
                    .mapToDouble(AcademicStudyDocument.Event::relativeHc)
                    .max().orElse(Double.NEGATIVE_INFINITY);
            minimum = Math.min(minimum, eventMinimum - .25);
            maximum = Math.max(maximum, eventMaximum + .25);
        }
        if (maximum - minimum < 5.0) {
            double middle = (minimum + maximum) / 2.0;
            minimum = middle - 2.5;
            maximum = middle + 2.5;
        }

        int left = 95, right = WIDTH - 70, gridTop = 125, gridBottom = 600;
        int baseline = 430;
        int bandLeft = theoryX(bandMinimum, minimum, maximum, left, right);
        int bandRight = theoryX(bandMaximum, minimum, maximum, left, right);
        g.setColor(new Color(151, 236, 222, 95));
        g.fillRect(Math.min(bandLeft, bandRight), gridTop,
                Math.max(2, Math.abs(bandRight - bandLeft)), gridBottom - gridTop);

        double range = maximum - minimum;
        double tickStep = range <= 12.0 ? 1.0 : range <= 25.0 ? 2.0 : range <= 60.0 ? 5.0 : 10.0;
        double firstTick = Math.ceil(minimum / tickStep) * tickStep;
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        for (double tick = firstTick; tick <= maximum + 1.0e-9; tick += tickStep) {
            int px = theoryX(tick, minimum, maximum, left, right);
            g.setColor(GRID);
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(px, gridTop, px, gridBottom);
            String tickLabel = Math.abs(tick) < 1.0e-8
                    ? "0" : ReportLocalization.signed(tick, 0);
            centered(g, tickLabel, px, gridBottom + 27, MUTED);
        }
        g.setColor(new Color(126, 148, 170));
        g.setStroke(new BasicStroke(4f));
        g.drawLine(left, baseline, right, baseline);

        List<TheoryMarker> expectedReferences = new ArrayList<>();
        if (theory.directionalSegahAnalysis()) {
            closestMarker(all, theory.targetRelativeHc())
                    .ifPresent(value -> addTheoryMarker(expectedReferences, value));
        } else if (theory.intervalAnalysis()) {
            for (AcademicStudyDocument.TheoryPitch reference : theory.expectedReferences()) {
                if (!Double.isFinite(reference.relativeHc())) continue;
                addTheoryMarker(expectedReferences, new TheoryMarker(reference.noteName(),
                        reference.relativeHc(), reference.hertz()));
            }
        } else {
            closestMarker(all, theory.targetRelativeHc())
                    .ifPresent(value -> addTheoryMarker(expectedReferences, value));
        }
        for (TheoryMarker reference : expectedReferences) {
            if (reference.hc() < minimum || reference.hc() > maximum) continue;
            int px = theoryX(reference.hc(), minimum, maximum, left, right);
            g.setColor(new Color(23, 146, 133, 150));
            g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    1f, new float[]{12f, 7f}, 0f));
            g.drawLine(px, gridTop, px, gridBottom);
        }

        if (theory.openBoundaryAnalysis()) {
            g.setColor(RED);
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    1f, new float[]{10f, 7f}, 0f));
            g.drawLine(bandLeft, gridTop, bandLeft, gridBottom);
            g.drawLine(bandRight, gridTop, bandRight, gridBottom);
        }

        drawTheoryEventPoints(g, eventPoints, minimum, maximum, left, right, gridTop);

        visible.sort(Comparator.comparingDouble(TheoryMarker::hc));
        List<TheoryLabelSpec> labelSpecs = new ArrayList<>();
        for (int index = 0; index < visible.size(); index++) {
            TheoryMarker marker = visible.get(index);
            boolean tonic = Math.abs(marker.hc()) < .06;
            boolean target = !theory.intervalAnalysis()
                    && Math.abs(marker.hc() - theory.targetRelativeHc()) < .06;
            boolean expectedReference = expectedReferences.stream()
                    .anyMatch(reference -> Math.abs(reference.hc() - marker.hc()) < .06);
            boolean lowerBoundary = theory.openBoundaryAnalysis()
                    && Math.abs(marker.hc() - theory.lowerBoundaryHc()) < .06;
            boolean upperBoundary = theory.openBoundaryAnalysis()
                    && Math.abs(marker.hc() - theory.upperBoundaryHc()) < .06;
            boolean boundary = lowerBoundary || upperBoundary;
            int px = theoryX(marker.hc(), minimum, maximum, left, right);
            // In open-interval analysis a boundary may also be the tonic.  The
            // exclusion rule is the more important visual fact, so boundaries
            // always retain the red, hollow marker used at both ends.
            Color color = target || expectedReference ? TEAL : boundary ? RED : tonic ? BLUE : MUTED;
            int radius = expectedReference ? 17 : 13;
            if (boundary) {
                g.setColor(Color.WHITE);
                g.fillOval(px - radius, baseline - radius, radius * 2, radius * 2);
                g.setColor(color);
                g.setStroke(new BasicStroke(4f));
                g.drawOval(px - radius, baseline - radius, radius * 2, radius * 2);
            } else {
                g.setColor(color);
                g.fillOval(px - radius, baseline - radius, radius * 2, radius * 2);
            }

            String role = expectedReference ? (theory.directionalSegahAnalysis()
                    ? t("report.reference", "reference", "referans")
                    : t("report.expectedMakamReference", "expected makam reference", "makamın beklenen referansı"))
                    : target ? t("report.target", "target", "hedef")
                    : lowerBoundary ? t("report.lowerBoundary", "lower boundary", "alt sınır")
                    : upperBoundary ? t("report.upperBoundary", "upper boundary", "üst sınır")
                    : tonic ? t("report.tonicLower", "tonic", "karar") : "";
            // Keep the note name, semantic role and numeric position on
            // separate lines.  Their blocks are laid out in four independent
            // lanes below, so a long role translation cannot merge with the
            // neighbouring pitch label.
            boolean above = theory.directionalSegahAnalysis()
                    ? expectedReference || lowerBoundary
                    : tonic || target || expectedReference || index % 2 != 0;
            String position = ReportLocalization.signed(marker.hc(), 2) + " Hc";
            if (boundary) position += t("report.excludedSuffix", " - excluded", " - hariç");
            labelSpecs.add(new TheoryLabelSpec(px, marker.noteName(), role, position,
                    color, target || expectedReference || tonic || boundary,
                    expectedReference, above));
        }

        drawTheoryLabels(g, layoutTheoryLabels(g, labelSpecs, left, right, baseline), baseline);

        g.setFont(new Font("SansSerif", Font.PLAIN, 19));
        centered(g, t("report.chart.theoreticalPositionAxis",
                        "Theoretical position relative to the tonic (Hc) - 1 Hc = 1200/53 C",
                        "Karar perdesine göre kuramsal konum (Hc) - 1 Hc = 1200/53 C"),
                (left + right) / 2, 675, INK);
        List<Legend> eventLegend = new ArrayList<>();
        long measurableCount = eventPoints.stream()
                .filter(event -> event.measurable() && event.included()).count();
        if (!eventPoints.isEmpty()) eventLegend.add(new Legend(
                t("report.all", "All", "Hepsi") + " (n=" + eventPoints.size() + ")", PALE));
        if (measurableCount > 0) eventLegend.add(new Legend(
                t("report.measurableEvents", "Measurable events", "Ölçülebilir olaylar")
                        + " (n=" + measurableCount + ")", BURGUNDY));
        if (!eventLegend.isEmpty()) legend(g, 120, 720, eventLegend);
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        String note = theory.directionalSegahAnalysis()
                ? t("report.chart.segahBandNote",
                "The shaded area is the Dügâh–Bûselik candidate region; the turquoise line is the theoretical Segâh reference.",
                "Boyalı alan Dügâh–Bûselik aday alanı; turkuaz çizgi kuramsal Segâh referansıdır.")
                : theory.intervalAnalysis()
                ? t("report.chart.intervalBandNote",
                "The shaded area is the open analysis interval; the turquoise line shows the selected makam's expected reference.",
                "Boyalı alan açık analiz aralığıdır; turkuaz çizgi seçilen makamın beklenen referansını gösterir.")
                : t("report.chart.targetBandNote",
                "The shaded area is the target classification region; the turquoise line is the theoretical target reference.",
                "Boyalı alan hedefin sınıflandırma alanı; turkuaz çizgi kuramsal hedef referansıdır.");
        centered(g, note, WIDTH / 2, 790, MUTED);
        g.dispose();
        return image;
    }

    /**
     * Draws one self-contained direction-specific decision chart.  The three
     * charts are exported separately so ascending, descending and direct-hold
     * populations are never merged into one visual panel.
     */
    public BufferedImage directionalSegahTheoryClassification(
            AcademicStudyDocument document, String usageLabel) {
        AcademicStudyDocument.Theory theory = document.theory();
        String label = List.of("Çıkıcı", "İnici", "Segâh kalışı").contains(usageLabel)
                ? usageLabel : "Segâh kalışı";
        Color color = directionColor(label);
        BufferedImage image = canvas(t("report.chart.directionalSegahPositions",
                "{0} Segâh positions in {1}", "{1} dizisinde {0} Segâh konumları",
                ReportLocalization.lowerCase(directionLabel(label)), theory.makamName()));
        Graphics2D g = graphics(image);

        double lower = Math.min(theory.lowerBoundaryHc(), theory.upperBoundaryHc());
        double upper = Math.max(theory.lowerBoundaryHc(), theory.upperBoundaryHc());
        if (!Double.isFinite(lower) || !Double.isFinite(upper) || upper <= lower) {
            empty(g, t("report.chart.noDirectionalGeometry",
                    "No directional Segâh decision geometry is available in the report data.",
                    "Yönsel Segâh karar geometrisi rapor girdisinde bulunmuyor."));
            return image;
        }
        double minimum = lower - .45;
        double maximum = upper + .45;
        int left = 135, right = WIDTH - 70, top = 135, bottom = 500;
        int baseline = 330;
        int bandLeft = theoryX(lower, minimum, maximum, left, right);
        int bandRight = theoryX(upper, minimum, maximum, left, right);
        int referenceX = theoryX(theory.targetRelativeHc(), minimum, maximum, left, right);
        List<AcademicStudyDocument.Event> values = document.measurableEvents().stream()
                .filter(AcademicStudyDocument.Event::included)
                .filter(value -> label.equals(value.direction()))
                .filter(value -> Double.isFinite(value.relativeHc()))
                .sorted(Comparator.comparingDouble(AcademicStudyDocument.Event::relativeHc)
                        .thenComparingInt(AcademicStudyDocument.Event::index))
                .toList();

        g.setColor(new Color(151, 236, 222, 72));
        g.fillRect(Math.min(bandLeft, bandRight), top,
                Math.max(2, Math.abs(bandRight - bandLeft)), bottom - top);
        for (double tick = Math.ceil(lower); tick <= Math.floor(upper) + 1.0e-9; tick += 1.0) {
            int px = theoryX(tick, minimum, maximum, left, right);
            g.setColor(GRID);
            g.setStroke(new BasicStroke(1.4f));
            g.drawLine(px, top, px, bottom);
            g.setFont(new Font("SansSerif", Font.PLAIN, 17));
            centered(g, Math.abs(tick) < 1.0e-9 ? "0"
                    : ReportLocalization.signed(tick, 0), px, bottom + 27, MUTED);
        }
        g.setColor(new Color(126, 148, 170));
        g.setStroke(new BasicStroke(4f));
        g.drawLine(left, baseline, right, baseline);
        g.setColor(new Color(23, 146, 133, 175));
        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{11f, 7f}, 0f));
        g.drawLine(referenceX, top, referenceX, bottom);
        g.setColor(RED);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{9f, 7f}, 0f));
        g.drawLine(bandLeft, top, bandLeft, bottom);
        g.drawLine(bandRight, top, bandRight, bottom);

        int[] laneOffsets = new int[]{0, -27, 27, -52, 52, -78, 78};
        for (int index = 0; index < values.size(); index++) {
            AcademicStudyDocument.Event event = values.get(index);
            int px = theoryX(event.relativeHc(), minimum, maximum, left, right);
            int py = baseline + laneOffsets[index % laneOffsets.length];
            g.setColor(Color.WHITE);
            g.fillOval(px - 12, py - 12, 24, 24);
            g.setColor(color);
            g.fillOval(px - 8, py - 8, 16, 16);
            g.setColor(INK);
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval(px - 8, py - 8, 16, 16);
        }
        if (values.isEmpty()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 20));
            centered(g, t("report.chart.noAcceptedUsage",
                            "There is no accepted musical usage in this group.",
                            "Bu grupta kabul edilen müziksel kullanım yok."),
                    (left + right) / 2, baseline + 6, MUTED);
        }

        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        centered(g, theory.lowerBoundaryNote() + t("report.chart.lowerExcludedSuffix",
                " - lower boundary (excluded)", " - alt sınır (hariç)"), bandLeft, top + 25, RED);
        centered(g, theory.targetNote() + t("report.chart.theoreticalReferenceSuffix",
                " - theoretical reference", " - kuramsal referans"), referenceX, top + 25, TEAL);
        centered(g, theory.upperBoundaryNote() + t("report.chart.upperExcludedSuffix",
                " - upper boundary (excluded)", " - üst sınır (hariç)"), bandRight, top + 25, RED);
        g.setFont(new Font("SansSerif", Font.PLAIN, 19));
        centered(g, t("report.chart.positionAxis",
                        "Position relative to the tonic (Hc) - 1 Hc = 1200/53 C",
                        "Karar perdesine göre konum (Hc) - 1 Hc = 1200/53 C"),
                (left + right) / 2, 580, INK);
        legend(g, 120, 625, List.of(
                new Legend(t("report.chart.musicalUsageLegend", "{0} musical usage (n={1})",
                        "{0} müziksel kullanımı (n={1})", directionLabel(label), values.size()), color),
                new Legend(t("report.chart.segahTheoryReference",
                        "Theoretical Segâh reference", "Kuramsal Segâh referansı"), TEAL)));
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        centered(g, t("report.chart.directionalCandidateNote",
                        "The shaded area is the open Dügâh–Bûselik candidate region; each point is one merged usage.",
                        "Boyalı alan Dügâh–Bûselik açık aday alanıdır; her nokta tek birleştirilmiş kullanımdır."),
                WIDTH / 2, HEIGHT - 24, MUTED);
        g.dispose();
        return image;
    }

    /** A full-size, one-context decision chart used as a distinct Word figure. */
    public BufferedImage directionalSegahClassification(AcademicStudyDocument document,
                                                         String direction) {
        AcademicStudyDocument.Theory theory = document.theory();
        if (!theory.directionalSegahAnalysis()) return theoryClassification(document);
        Color color = directionColor(direction);
        String title = switch (direction) {
            case "Çıkıcı" -> t("report.chart.segahAscendingPositions",
                    "Segâh pitch positions in ascending phrases", "Çıkıcı cümlelerde Segâh perde konumları");
            case "İnici" -> t("report.chart.segahDescendingPositions",
                    "Segâh pitch positions in descending phrases", "İnici cümlelerde Segâh perde konumları");
            case "Segâh kalışı" -> t("report.chart.segahSustainPositions",
                    "Pitch positions in direct Segâh sustains", "Doğrudan Segâh kalışlarında perde konumları");
            default -> t("report.chart.segahContextPositions", "Segâh pitch positions in {0} context",
                    "{0} bağlamında Segâh perde konumları", directionLabel(direction));
        };
        BufferedImage image = canvas(title);
        Graphics2D g = graphics(image);
        double lower = Math.min(theory.lowerBoundaryHc(), theory.upperBoundaryHc());
        double upper = Math.max(theory.lowerBoundaryHc(), theory.upperBoundaryHc());
        if (!Double.isFinite(lower) || !Double.isFinite(upper) || upper <= lower) {
            empty(g, t("report.chart.noDirectionalGeometry",
                    "No directional Segâh decision geometry is available in the report data.",
                    "Yönsel Segâh karar geometrisi rapor girdisinde bulunmuyor."));
            return image;
        }
        double minimum = lower - .45;
        double maximum = upper + .45;
        int left = 125, right = WIDTH - 70, top = 145, bottom = 505;
        int baseline = 345;
        int bandLeft = theoryX(lower, minimum, maximum, left, right);
        int bandRight = theoryX(upper, minimum, maximum, left, right);
        int referenceX = theoryX(theory.targetRelativeHc(), minimum, maximum, left, right);
        List<AcademicStudyDocument.Event> values = document.measurableEvents().stream()
                .filter(AcademicStudyDocument.Event::included)
                .filter(value -> value.direction().equals(direction))
                .filter(value -> Double.isFinite(value.relativeHc()))
                .sorted(Comparator.comparingDouble(AcademicStudyDocument.Event::relativeHc)
                        .thenComparingInt(AcademicStudyDocument.Event::index))
                .toList();

        g.setColor(new Color(151, 236, 222, 68));
        g.fillRoundRect(Math.min(bandLeft, bandRight), top,
                Math.max(2, Math.abs(bandRight - bandLeft)), bottom - top, 18, 18);
        g.setColor(GRID);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(left, top, right - left, bottom - top, 18, 18);
        for (double tick = Math.ceil(lower); tick <= Math.floor(upper) + 1.0e-9; tick += 1.0) {
            int px = theoryX(tick, minimum, maximum, left, right);
            g.setColor(GRID);
            g.setStroke(new BasicStroke(1.3f));
            g.drawLine(px, top, px, bottom);
            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            centered(g, Math.abs(tick) < 1.0e-9 ? "0"
                    : ReportLocalization.signed(tick, 0), px, bottom + 29, MUTED);
        }
        g.setColor(new Color(126, 148, 170));
        g.setStroke(new BasicStroke(4f));
        g.drawLine(left, baseline, right, baseline);
        g.setColor(new Color(23, 146, 133, 175));
        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{11f, 7f}, 0f));
        g.drawLine(referenceX, top, referenceX, bottom);
        g.setColor(RED);
        g.setStroke(new BasicStroke(2.7f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{9f, 7f}, 0f));
        g.drawLine(bandLeft, top, bandLeft, bottom);
        g.drawLine(bandRight, top, bandRight, bottom);

        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        centered(g, theory.lowerBoundaryNote() + t("report.chart.lowerExcludedSuffix",
                " - lower boundary (excluded)", " - alt sınır (hariç)"), bandLeft, top - 22, RED);
        centered(g, theory.targetNote() + t("report.chart.theoreticalReferenceSuffix",
                " - theoretical reference", " - kuramsal referans"), referenceX, top - 22, TEAL);
        // Segâh (+8 Hc) and Bûselik (+9 Hc) are only one Holder comma apart.
        // Put their captions on separate vertical lanes so the two reference labels
        // remain legible after Word scales the figure to the page width.
        centered(g, theory.upperBoundaryNote() + t("report.chart.upperExcludedSuffix",
                " - upper boundary (excluded)", " - üst sınır (hariç)"), bandRight, top + 28, RED);
        if (values.isEmpty()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 23));
            centered(g, t("report.chart.noAcceptedUsageContext",
                            "There is no accepted musical usage in this context.",
                            "Bu bağlamda kabul edilen müziksel kullanım yok."),
                    (left + right) / 2, baseline + 8, MUTED);
        } else {
            int[] laneOffsets = new int[]{0, -34, 34, -68, 68, -102, 102};
            for (int index = 0; index < values.size(); index++) {
                AcademicStudyDocument.Event event = values.get(index);
                int px = theoryX(event.relativeHc(), minimum, maximum, left, right);
                int py = baseline + laneOffsets[index % laneOffsets.length];
                g.setColor(Color.WHITE);
                g.fillOval(px - 13, py - 13, 26, 26);
                g.setColor(color);
                g.fillOval(px - 9, py - 9, 18, 18);
                g.setColor(INK);
                g.setStroke(new BasicStroke(1.5f));
                g.drawOval(px - 9, py - 9, 18, 18);
            }
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 21));
        g.setColor(color);
        g.drawString(directionLabel(direction) + "  n=" + values.size(), left + 20, bottom - 22);
        g.setFont(new Font("SansSerif", Font.PLAIN, 19));
        centered(g, t("report.chart.theoreticalPositionHc",
                        "Theoretical position relative to the tonic (Hc)",
                        "Karar perdesine göre kuramsal konum (Hc)"),
                (left + right) / 2, 585, INK);
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        centered(g, t("report.chart.segahOpenCandidateNote",
                        "The shaded area is the open Dügâh–Bûselik candidate region; the turquoise line is the theoretical Segâh reference.",
                        "Boyalı alan Dügâh–Bûselik açık aday alanıdır; turkuaz çizgi kuramsal Segâh referansıdır."),
                WIDTH / 2, 650, MUTED);
        g.dispose();
        return image;
    }

    private static Color directionColor(String direction) {
        return switch (direction) {
            case "Çıkıcı" -> BLUE;
            case "İnici" -> RED;
            case "Segâh kalışı" -> TEAL;
            default -> MUTED;
        };
    }

    private static List<TheoryMarker> theoryMarkers(AcademicStudyDocument.Theory theory) {
        List<TheoryMarker> markers = new ArrayList<>();
        for (AcademicStudyDocument.TheoryPitch pitch : theory.pitchCenters()) {
            if (!Double.isFinite(pitch.relativeHc()) || !Double.isFinite(pitch.hertz())) continue;
            addTheoryMarker(markers, new TheoryMarker(pitch.noteName(), pitch.relativeHc(), pitch.hertz()));
        }
        for (AcademicStudyDocument.TheoryPitch reference : theory.expectedReferences()) {
            if (!Double.isFinite(reference.relativeHc()) || !Double.isFinite(reference.hertz())) continue;
            addTheoryMarker(markers, new TheoryMarker(reference.noteName(),
                    reference.relativeHc(), reference.hertz()));
        }
        addTheoryMarker(markers, new TheoryMarker(theory.tonicNote(), 0.0, theory.tonicHz()));
        if (theory.openBoundaryAnalysis()) {
            addTheoryMarker(markers, new TheoryMarker(theory.lowerBoundaryNote(),
                    theory.lowerBoundaryHc(), theory.lowerBoundaryHz()));
            addTheoryMarker(markers, new TheoryMarker(theory.upperBoundaryNote(),
                    theory.upperBoundaryHc(), theory.upperBoundaryHz()));
        }
        addTheoryMarker(markers, new TheoryMarker(theory.targetNote(),
                theory.targetRelativeHc(), theory.targetHz()));
        markers.sort(Comparator.comparingDouble(TheoryMarker::hc));
        return markers;
    }

    private static void addTheoryMarker(List<TheoryMarker> markers, TheoryMarker marker) {
        if (!Double.isFinite(marker.hc()) || !Double.isFinite(marker.hertz())) return;
        for (int index = 0; index < markers.size(); index++) {
            if (Math.abs(markers.get(index).hc() - marker.hc()) < .035) {
                // Later synthetic markers carry the exact user-facing target or
                // boundary name and should replace an equivalent catalogue centre.
                markers.set(index, marker);
                return;
            }
        }
        markers.add(marker);
    }

    private static List<TheoryMarker> singlePitchMarkers(AcademicStudyDocument.Theory theory,
                                                          List<TheoryMarker> all) {
        double target = theory.targetRelativeHc();
        List<TheoryMarker> selected = new ArrayList<>();
        closestMarker(all, 0.0).ifPresent(value -> addTheoryMarker(selected, value));
        closestMarker(all, target).ifPresent(value -> addTheoryMarker(selected, value));
        all.stream().filter(value -> value.hc() < target - .04)
                .sorted(Comparator.comparingDouble(TheoryMarker::hc).reversed()).limit(1)
                .forEach(value -> addTheoryMarker(selected, value));
        all.stream().filter(value -> value.hc() > target + .04)
                .sorted(Comparator.comparingDouble(TheoryMarker::hc)).limit(3)
                .forEach(value -> addTheoryMarker(selected, value));
        return selected;
    }

    private static List<TheoryMarker> intervalMarkers(AcademicStudyDocument.Theory theory,
                                                       List<TheoryMarker> all) {
        double lower = Math.min(theory.lowerBoundaryHc(), theory.upperBoundaryHc());
        double upper = Math.max(theory.lowerBoundaryHc(), theory.upperBoundaryHc());
        List<TheoryMarker> inside = all.stream()
                .filter(value -> value.hc() > lower + .04 && value.hc() < upper - .04).toList();
        List<TheoryMarker> selected = new ArrayList<>();
        closestMarker(all, lower).ifPresent(value -> addTheoryMarker(selected, value));
        if (inside.size() <= 7) inside.forEach(value -> addTheoryMarker(selected, value));
        else {
            for (int index = 0; index < 7; index++) {
                int source = (int) Math.round(index * (inside.size() - 1) / 6.0);
                addTheoryMarker(selected, inside.get(source));
            }
        }
        closestMarker(all, upper).ifPresent(value -> addTheoryMarker(selected, value));
        for (AcademicStudyDocument.TheoryPitch reference : theory.expectedReferences()) {
            closestMarker(all, reference.relativeHc())
                    .ifPresent(value -> addTheoryMarker(selected, value));
        }
        if (0.0 > lower - 2.0 && 0.0 < upper + 2.0) {
            closestMarker(all, 0.0).ifPresent(value -> addTheoryMarker(selected, value));
        }
        return selected;
    }

    private static java.util.Optional<TheoryMarker> closestMarker(List<TheoryMarker> markers,
                                                                   double hc) {
        return markers.stream().min(Comparator.comparingDouble(value -> Math.abs(value.hc() - hc)));
    }

    private static int theoryX(double value, double minimum, double maximum, int left, int right) {
        return left + (int) Math.round((value - minimum) / Math.max(1.0e-9, maximum - minimum)
                * (right - left));
    }

    /** Every canonical contact belongs to the theory chart's auditable point layer. */
    static List<AcademicStudyDocument.Event> theoryChartEvents(AcademicStudyDocument document) {
        return document.contacts().stream()
                .filter(event -> Double.isFinite(event.relativeHc())).toList();
    }

    /**
     * Draws all contacts: the statistical/measurable subset is burgundy and
     * brief or audit-only evidence remains visible as a pale hollow marker.
     */
    private static void drawTheoryEventPoints(Graphics2D g,
                                               List<AcademicStudyDocument.Event> events,
                                               double minimum, double maximum,
                                               int left, int right, int gridTop) {
        if (events.isEmpty()) return;
        List<AcademicStudyDocument.Event> ordered = events.stream()
                .sorted(Comparator.comparingDouble(AcademicStudyDocument.Event::relativeHc)
                        .thenComparingInt(AcademicStudyDocument.Event::index)).toList();
        int[] lastX = new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE};
        int overflow = 0;
        for (AcademicStudyDocument.Event event : ordered) {
            double hc = event.relativeHc();
            if (hc < minimum - 1.0e-9 || hc > maximum + 1.0e-9) continue;
            int px = theoryX(hc, minimum, maximum, left, right);
            int lane = -1;
            for (int candidate = 0; candidate < lastX.length; candidate++) {
                if (lastX[candidate] == Integer.MIN_VALUE || px - lastX[candidate] >= 17) {
                    lane = candidate;
                    break;
                }
            }
            if (lane < 0) {
                lane = Math.floorMod(event.index(), lastX.length);
                px += (overflow++ % 3 - 1) * 3;
            }
            lastX[lane] = px;
            int py = gridTop + 28 + lane * 22;
            g.setColor(Color.WHITE);
            g.fillOval(px - 8, py - 8, 16, 16);
            if (event.measurable() && event.included()) {
                g.setColor(BURGUNDY);
                g.fillOval(px - 6, py - 6, 12, 12);
            } else {
                g.setColor(new Color(MUTED.getRed(), MUTED.getGreen(), MUTED.getBlue(), 150));
                g.setStroke(new BasicStroke(2.5f));
                g.drawOval(px - 6, py - 6, 12, 12);
            }
        }
    }

    /**
     * Places long theory labels in four independent lanes.  The calculation is
     * deterministic and shared with a regression test because Word scales this
     * raster chart down to page width: even a small source-image collision
     * becomes illegible in the final report.
     */
    static List<TheoryLabelPlacement> layoutTheoryLabels(
            Graphics2D g, List<TheoryLabelSpec> specs,
            int left, int right, int baseline) {
        int[][] lineBaselines = {
                {baseline - 155, baseline - 136, baseline - 117},
                {baseline - 80, baseline - 61, baseline - 42},
                {baseline + 40, baseline + 59, baseline + 78},
                {baseline + 115, baseline + 134, baseline + 153}
        };
        int[] lastRight = {Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE};
        List<TheoryLabelPlacement> placements = new ArrayList<>();
        List<TheoryLabelSpec> ordered = specs.stream()
                .sorted(Comparator.comparingInt(TheoryLabelSpec::anchorX)).toList();
        for (TheoryLabelSpec spec : ordered) {
            int primaryWidth = textWidth(g, primaryFont(spec), spec.primary());
            int roleWidth = textWidth(g, roleFont(spec), spec.role());
            int positionWidth = textWidth(g, positionFont(), spec.position());
            int width = Math.max(72, Math.max(primaryWidth, Math.max(roleWidth, positionWidth)) + 22);
            width = Math.min(width, Math.max(72, right - left));
            int[] preferredLanes = spec.preferredAbove()
                    ? new int[]{1, 0, 2, 3} : new int[]{2, 3, 1, 0};
            TheoryLabelPlacement placement = null;
            for (int lane : preferredLanes) {
                int minimumCenter = left + width / 2;
                int maximumCenter = right - width / 2;
                int center = Math.max(minimumCenter, Math.min(maximumCenter, spec.anchorX()));
                int requiredLeft = lastRight[lane] == Integer.MIN_VALUE
                        ? left : lastRight[lane] + 18;
                center = Math.max(center, requiredLeft + width / 2);
                if (center + (width + 1) / 2 > right) continue;
                placement = placement(spec, center, lane, width, lineBaselines, g);
                break;
            }
            // Four lanes are sufficient for the normal eight-or-nine-marker
            // interval chart.  Keep a bounded fallback for unusually verbose
            // custom scale data rather than allowing text outside the image.
            if (placement == null) {
                int lane = preferredLanes[0];
                int center = Math.max(left + width / 2,
                        Math.min(right - width / 2, spec.anchorX()));
                placement = placement(spec, center, lane, width, lineBaselines, g);
            }
            lastRight[placement.lane()] = placement.bounds().x + placement.bounds().width;
            placements.add(placement);
        }
        return placements;
    }

    private static TheoryLabelPlacement placement(TheoryLabelSpec spec, int center,
                                                   int lane, int width,
                                                   int[][] lineBaselines,
                                                   Graphics2D g) {
        int primaryBaseline = lineBaselines[lane][0];
        int roleBaseline = lineBaselines[lane][1];
        int positionBaseline = lineBaselines[lane][2];
        int top = primaryBaseline - fontAscent(g, primaryFont(spec)) - 7;
        int bottom = positionBaseline + fontDescent(g, positionFont()) + 7;
        Rectangle bounds = new Rectangle(center - width / 2, top, width,
                Math.max(1, bottom - top));
        return new TheoryLabelPlacement(spec, center, lane,
                primaryBaseline, roleBaseline, positionBaseline, bounds);
    }

    private static void drawTheoryLabels(Graphics2D g,
                                         List<TheoryLabelPlacement> placements,
                                         int baseline) {
        for (TheoryLabelPlacement placement : placements) {
            TheoryLabelSpec spec = placement.spec();
            Rectangle bounds = placement.bounds();
            boolean above = placement.lane() < 2;
            int startY = baseline + (above ? -20 : 20);
            int endY = above ? bounds.y + bounds.height + 3 : bounds.y - 3;
            g.setColor(new Color(spec.color().getRed(), spec.color().getGreen(),
                    spec.color().getBlue(), 115));
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(spec.anchorX(), startY, placement.centerX(), endY);

            g.setColor(new Color(255, 255, 255, 225));
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 14, 14);
            g.setColor(new Color(207, 218, 228, 205));
            g.setStroke(new BasicStroke(1.2f));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 14, 14);

            g.setFont(primaryFont(spec));
            centered(g, spec.primary(), placement.centerX(), placement.primaryBaseline(), spec.color());
            if (!spec.role().isBlank()) {
                g.setFont(roleFont(spec));
                centered(g, spec.role(), placement.centerX(), placement.roleBaseline(), spec.color());
            }
            g.setFont(positionFont());
            centered(g, spec.position(), placement.centerX(), placement.positionBaseline(), MUTED);
        }
    }

    private static Font primaryFont(TheoryLabelSpec spec) {
        return new Font("SansSerif", spec.emphasized() ? Font.BOLD : Font.PLAIN,
                spec.expectedReference() ? 19 : 18);
    }

    private static Font roleFont(TheoryLabelSpec spec) {
        return new Font("SansSerif", Font.BOLD, spec.expectedReference() ? 15 : 14);
    }

    private static Font positionFont() {
        return new Font("SansSerif", Font.PLAIN, 14);
    }

    private static int textWidth(Graphics2D g, Font font, String text) {
        return text == null || text.isBlank() ? 0 : g.getFontMetrics(font).stringWidth(text);
    }

    private static int fontAscent(Graphics2D g, Font font) {
        return g.getFontMetrics(font).getAscent();
    }

    private static int fontDescent(Graphics2D g, Font font) {
        return g.getFontMetrics(font).getDescent();
    }

    private record TheoryMarker(String noteName, double hc, double hertz) { }

    static record TheoryLabelSpec(int anchorX, String primary, String role,
                                  String position, Color color,
                                  boolean emphasized, boolean expectedReference,
                                  boolean preferredAbove) { }

    static record TheoryLabelPlacement(TheoryLabelSpec spec, int centerX, int lane,
                                       int primaryBaseline, int roleBaseline,
                                       int positionBaseline, Rectangle bounds) { }

    /**
     * Displays every canonical contact in the selected pitch domain and keeps
     * auditable short contacts visible instead of silently dropping them.
     * Larger bubbles summarize the preserved observed modes; they are not
     * averages of neighbouring, acoustically distinct peaks.
     */
    public BufferedImage pitchPeakDistribution(AcademicStudyDocument document) {
        List<AcademicStudyDocument.Event> events = theoryChartEvents(document).stream()
                .sorted(Comparator.comparingDouble(AcademicStudyDocument.Event::relativeHc)
                        .thenComparingInt(AcademicStudyDocument.Event::index))
                .toList();
        BufferedImage image = canvas(firstFourText(
                "Observed pitches and intermediate-pitch modes",
                "Basılan perdeler ve ara-ses tepeleri"), 900);
        Graphics2D g = graphics(image);
        if (events.isEmpty()) {
            empty(g, firstFourText(
                    "No pitch contact is available in the selected domain.",
                    "Seçilen alanda perde teması bulunmuyor."));
            return image;
        }

        List<ObservedCluster> clusters = observedClusters(events);
        List<TheoryMarker> theory = theoryMarkers(document.theory());
        double minimum = events.stream().mapToDouble(AcademicStudyDocument.Event::relativeHc)
                .min().orElse(0.0);
        double maximum = events.stream().mapToDouble(AcademicStudyDocument.Event::relativeHc)
                .max().orElse(minimum + 1.0);
        if (document.theory().openBoundaryAnalysis()) {
            minimum = Math.min(minimum, Math.min(document.theory().lowerBoundaryHc(),
                    document.theory().upperBoundaryHc()));
            maximum = Math.max(maximum, Math.max(document.theory().lowerBoundaryHc(),
                    document.theory().upperBoundaryHc()));
        }
        for (TheoryMarker marker : theory) {
            if (marker.hc() >= minimum - 1.0 && marker.hc() <= maximum + 1.0) {
                minimum = Math.min(minimum, marker.hc());
                maximum = Math.max(maximum, marker.hc());
            }
        }
        double margin = Math.max(.55, (maximum - minimum) * .035);
        minimum -= margin;
        maximum += margin;

        int left = 105, right = WIDTH - 75, top = 135, bottom = 650;
        double tickStep = niceHcStep(maximum - minimum);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        for (double tick = Math.ceil(minimum / tickStep) * tickStep;
             tick <= maximum + 1.0e-9; tick += tickStep) {
            int px = theoryX(tick, minimum, maximum, left, right);
            g.setColor(GRID);
            g.setStroke(new BasicStroke(1.4f));
            g.drawLine(px, top, px, bottom);
            centered(g, Math.abs(tick) < 1.0e-8 ? "0"
                    : ReportLocalization.signed(tick, tickStep < 1.0 ? 1 : 0),
                    px, bottom + 29, MUTED);
        }

        final double chartMinimum = minimum;
        final double chartMaximum = maximum;
        List<TheoryMarker> visibleTheory = theory.stream()
                .filter(value -> value.hc() >= chartMinimum && value.hc() <= chartMaximum)
                .sorted(Comparator.comparingDouble(TheoryMarker::hc)).toList();
        int theoryLane = 0;
        for (TheoryMarker marker : visibleTheory) {
            int px = theoryX(marker.hc(), minimum, maximum, left, right);
            g.setColor(new Color(91, 111, 134, 105));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    1f, new float[]{7f, 6f}, 0f));
            g.drawLine(px, top, px, bottom);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            centered(g, marker.noteName(), px, bottom + 55 + (theoryLane++ % 2) * 20, MUTED);
        }

        int[] lastLaneX = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        int overflow = 0;
        for (AcademicStudyDocument.Event event : events) {
            int px = theoryX(event.relativeHc(), minimum, maximum, left, right);
            int lane = -1;
            for (int candidate = 0; candidate < lastLaneX.length; candidate++) {
                if (lastLaneX[candidate] == Integer.MIN_VALUE
                        || px - lastLaneX[candidate] >= 13) {
                    lane = candidate;
                    break;
                }
            }
            if (lane < 0) {
                lane = Math.floorMod(event.index(), lastLaneX.length);
                px += (overflow++ % 3 - 1) * 3;
            }
            lastLaneX[lane] = px;
            int py = top + 44 + lane * 31;
            boolean principal = event.measurable() && event.included();
            g.setColor(Color.WHITE);
            g.fillOval(px - 7, py - 7, 14, 14);
            if (principal) {
                g.setColor(BURGUNDY);
                g.fillOval(px - 6, py - 6, 12, 12);
            } else {
                g.setColor(new Color(MUTED.getRed(), MUTED.getGreen(), MUTED.getBlue(), 145));
                g.setStroke(new BasicStroke(2.2f));
                g.drawOval(px - 6, py - 6, 12, 12);
            }
        }

        int baseline = 505;
        g.setColor(new Color(126, 148, 170));
        g.setStroke(new BasicStroke(4f));
        g.drawLine(left, baseline, right, baseline);
        int labelLane = 0;
        for (ObservedCluster cluster : clusters) {
            int px = theoryX(cluster.centerHc(), minimum, maximum, left, right);
            int radius = Math.min(18, 7 + (int) Math.round(Math.sqrt(cluster.eventCount()) * 2.2));
            g.setColor(cluster.measurableCount() > 0 ? BURGUNDY : new Color(125, 145, 165));
            g.fillOval(px - radius, baseline - radius, radius * 2, radius * 2);
            int labelY = 400 - (labelLane++ % 3) * 57;
            g.setColor(new Color(126, 148, 170, 150));
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(px, baseline - radius - 2, px, labelY + 9);
            String primary = cluster.label() + "  "
                    + ReportLocalization.signed(cluster.centerHc(), 3) + " Hc";
            String secondary = "n=" + cluster.eventCount() + "  •  "
                    + ReportLocalization.number(cluster.totalDurationSeconds(), 2) + " sn";
            drawPeakLabel(g, primary, secondary, px, labelY, left, right,
                    cluster.measurableCount() > 0 ? BURGUNDY : MUTED);
        }

        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        centered(g, firstFourText(
                        "Observed position relative to the confirmed tonic (Hc)",
                        "Onaylı karar perdesine göre gözlenen konum (Hc)"),
                (left + right) / 2, 785, INK);
        legend(g, 115, 835, List.of(
                new Legend(t("report.measurableEvents", "Measurable events", "Ölçülebilir olaylar"), BURGUNDY),
                new Legend(firstFourText("Reliable short/audit contacts",
                        "Güvenilir kısa/denetim temasları"), PALE)));
        g.dispose();
        return image;
    }

    /**
     * Shows each preserved observed mode beside its nearest theoretical pitch
     * centre and prints the signed distance in both cent and Holder comma.
     */
    public BufferedImage pitchCenterDeviations(AcademicStudyDocument document) {
        List<AcademicStudyDocument.Event> events = theoryChartEvents(document).stream()
                .sorted(Comparator.comparingDouble(AcademicStudyDocument.Event::relativeHc))
                .toList();
        List<ObservedCluster> clusters = observedClusters(events);
        List<TheoryMarker> theory = theoryMarkers(document.theory());
        int rowHeight = 58;
        int top = 150;
        int height = Math.max(HEIGHT, top + Math.max(1, clusters.size()) * rowHeight + 105);
        BufferedImage image = canvas(firstFourText(
                "Observed pitch centres and distances from theory",
                "Perde merkezleri ve kuramsal uzaklıklar"), height);
        Graphics2D g = graphics(image);
        if (clusters.isEmpty() || theory.isEmpty()) {
            empty(g, firstFourText(
                    "No observed/theoretical pitch-centre pair is available.",
                    "Gözlenen/kuramsal perde merkezi çifti bulunmuyor."));
            return image;
        }

        List<CenterDeviation> rows = clusters.stream()
                .map(cluster -> nearestCenter(cluster, theory))
                .sorted(Comparator.comparingDouble(value -> value.cluster().centerHc()))
                .toList();
        double minimum = rows.stream().mapToDouble(value -> Math.min(
                value.cluster().centerHc(), value.reference().hc())).min().orElse(0.0);
        double maximum = rows.stream().mapToDouble(value -> Math.max(
                value.cluster().centerHc(), value.reference().hc())).max().orElse(minimum + 1.0);
        double margin = Math.max(.6, (maximum - minimum) * .04);
        minimum -= margin;
        maximum += margin;

        int labelRight = 470, plotLeft = 520, plotRight = 1215, valueLeft = 1245;
        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        g.setColor(MUTED);
        g.drawString(firstFourText("Observed mode", "Gözlenen tepe"), 60, 120);
        g.drawString(firstFourText("Theory ↔ observation", "Kuram ↔ gözlem"),
                plotLeft, 120);
        g.drawString(firstFourText("Signed distance", "İşaretli uzaklık"),
                valueLeft, 120);
        for (int i = 0; i < rows.size(); i++) {
            CenterDeviation row = rows.get(i);
            int y = top + i * rowHeight;
            if (i % 2 == 0) {
                g.setColor(new Color(244, 247, 250));
                g.fillRoundRect(45, y - 22, WIDTH - 90, rowHeight - 5, 12, 12);
            }
            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            g.setColor(INK);
            String label = row.cluster().label() + "  "
                    + ReportLocalization.signed(row.cluster().centerHc(), 3) + " Hc";
            g.drawString(ellipsize(g, label, labelRight - 65), 60, y + 9);
            int referenceX = theoryX(row.reference().hc(), minimum, maximum, plotLeft, plotRight);
            int observedX = theoryX(row.cluster().centerHc(), minimum, maximum, plotLeft, plotRight);
            g.setColor(row.residualHc() < 0.0 ? RED : BLUE);
            g.setStroke(new BasicStroke(4f));
            g.drawLine(referenceX, y, observedX, y);
            g.setColor(MUTED);
            g.fillOval(referenceX - 7, y - 7, 14, 14);
            g.setColor(BURGUNDY);
            g.fillOval(observedX - 8, y - 8, 16, 16);
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            g.setColor(MUTED);
            centered(g, row.reference().noteName(), referenceX, y + 25, MUTED);
            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            g.setColor(row.residualHc() < 0.0 ? RED : BLUE);
            g.drawString(ReportLocalization.signed(row.residualHc()
                            * AcademicStudyDocument.HC_CENT, 2) + " C  /  "
                            + ReportLocalization.signed(row.residualHc(), 3) + " Hc",
                    valueLeft, y + 8);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.setColor(MUTED);
            g.drawString("n=" + row.cluster().eventCount() + "  •  "
                    + row.reference().noteName(), valueLeft, y + 27);
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        centered(g, firstFourText(
                        "Grey: theoretical centre • burgundy: observed mode • line: signed distance",
                        "Gri: kuramsal merkez • bordo: gözlenen tepe • çizgi: işaretli uzaklık"),
                WIDTH / 2, height - 43, MUTED);
        g.dispose();
        return image;
    }

    /**
     * Compares one immutable, candidate-independent collection of ordered
     * phrase peaks with every visible first-four-pitch reference.
     */
    public BufferedImage orderedPhraseProfileRanking(AcademicStudyDocument document) {
        List<AcademicStudyDocument.FirstFourPeak> phrasePeaks =
                document.firstFourPhraseEvidence().degreePeaks().stream()
                        .filter(value -> value.degree() >= 2 && value.degree() <= 4)
                        .toList();
        var cadence = document.firstFourPhraseEvidence().cadence();
        List<PhraseProfileScore> rows = cadence != null
                ? cadence.scores().stream().map(score -> new PhraseProfileScore(score.profile(),
                        score.percent(), cadence.coveredDegrees() * 100.0 / 3,
                        score.meanAbsoluteHc())).toList()
                : document.profileComparisons().stream()
                .filter(value -> !"buselik".equals(profileKey(value.profileName())))
                .map(profile -> phraseProfileScore(profile, phrasePeaks))
                .filter(value -> Double.isFinite(value.scorePercent()))
                .sorted(Comparator.comparing((PhraseProfileScore value) ->
                                value.scorePercent() < 80.0)
                        .thenComparing(Comparator.comparingDouble(
                                PhraseProfileScore::scorePercent).reversed())
                        .thenComparing(PhraseProfileScore::profileName))
                .toList();
        int rowHeight = 67;
        int top = 145;
        int height = Math.max(HEIGHT, top + Math.max(1, rows.size()) * rowHeight + 125);
        BufferedImage image = canvas(cadence == null ? firstFourText(
                "First-four-pitch similarity from ordered phrase evidence",
                "Sıralı cümle kanıtına göre ilk dört ses benzerliği") : firstFourText(
                "Last-four-pitch similarity toward the tonic",
                "Karara yönelik son dört ses benzerliği"), height);
        Graphics2D g = graphics(image);
        if (rows.isEmpty()) {
            empty(g, firstFourText(
                    "No fixed ordered-phrase evidence is available for comparison.",
                    "Karşılaştırılabilir sabit sıralı-cümle kanıtı bulunmuyor."));
            g.dispose();
            return image;
        }

        AcademicStudyDocument.FirstFourPhraseEvidence evidence =
                document.firstFourPhraseEvidence();
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.setColor(evidence.complete() ? TEAL : RED);
        String evidenceNote = evidence.complete()
                ? firstFourText("Complete ordered-path evidence", "Tam sıralı-cümle kanıtı")
                : firstFourText("Partial/mixed ordered-path evidence", "Kısmi/karma sıralı-cümle kanıtı");
        String chartNote = cadence == null
                ? evidenceNote + "  •  n=" + evidence.qualifyingPhraseCount()
                    + "  •  e=" + evidence.evidenceEventCount()
                : firstFourText("Confirmed descents", "Doğrulanan iniş") + " "
                    + cadence.confirmed() + "  •  "
                    + firstFourText("Observed degrees", "Gözlenen derece") + " "
                    + cadence.coveredDegrees() + "/3";
        g.drawString(chartNote, 70, 112);

        int left = 350, right = WIDTH - 120;
        int usable = right - left;
        int thresholdX = left + (int) Math.round(usable * .80);
        g.setColor(new Color(190, 31, 52, 150));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{9f, 7f}, 0f));
        g.drawLine(thresholdX, top - 25, thresholdX,
                top + rows.size() * rowHeight - 24);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        centered(g, "80%", thresholdX, top - 34, RED);
        for (int i = 0; i < rows.size(); i++) {
            PhraseProfileScore row = rows.get(i);
            int y = top + i * rowHeight;
            g.setColor(new Color(231, 236, 241));
            g.fillRoundRect(left, y, usable, 35, 18, 18);
            g.setColor(row.scorePercent() + 1.0e-9 >= 80.0 ? RED : TEAL);
            g.fillRoundRect(left, y, (int) Math.round(usable
                    * Math.max(0.0, Math.min(100.0, row.scorePercent())) / 100.0),
                    35, 18, 18);
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.setColor(INK);
            g.drawString((i + 1) + ". " + row.profileName(), 70, y + 25);
            g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            g.setColor(MUTED);
            g.drawString(t("report.coverage", "Coverage", "Kapsam") + " "
                    + ReportLocalization.percentValue(row.coveragePercent(), 0)
                    + "  •  " + firstFourText("Mean absolute error", "Ortalama mutlak hata") + " "
                    + ReportLocalization.number(row.meanAbsoluteErrorHc(), 2) + " Hc",
                    70, y + 47);
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.setColor(INK);
            g.drawString(ReportLocalization.percentValue(row.scorePercent(), 1),
                    right + 15, y + 25);
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        g.setColor(MUTED);
        g.drawString(firstFourText(
                        "Every row uses the same observed phrase peaks; only continuous distance to theory changes.",
                        "Her satır aynı gözlenen cümle tepelerini kullanır; yalnız kurama sürekli uzaklık değişir."),
                70, height - 62);
        g.dispose();
        return image;
    }

    /**
     * Ranks the complete tonic-aligned interval profiles.  This is deliberately
     * separate from {@link #profileRanking(AcademicStudyDocument)}: the former
     * report contained both the complete-profile comparison and the newer
     * first-four-pitch comparison, and neither view should silently replace the
     * other.
     */
    public BufferedImage tonicAlignedProfileRanking(AcademicStudyDocument document) {
        List<AcademicStudyDocument.ProfileComparison> profiles = document.profileComparisons().stream()
                .sorted(Comparator.comparingDouble(
                        AcademicStudyDocument.ProfileComparison::scorePercent).reversed()
                        .thenComparing(AcademicStudyDocument.ProfileComparison::profileName))
                .toList();
        int rowHeight = 62;
        int top = 125;
        int chartHeight = Math.max(HEIGHT, top + profiles.size() * rowHeight + 100);
        BufferedImage image = canvas(t("report.chart.profileSimilarity",
                "Tonic-aligned scale / çeşni-group similarity",
                "Karara hizalı dizi / çeşni grubu benzerliği"), chartHeight);
        Graphics2D g = graphics(image);
        if (profiles.isEmpty()) {
            empty(g, t("report.chart.noProfileComparison",
                    "No scale / çeşni-group comparison was made.",
                    "Dizi / çeşni grubu karşılaştırması yapılmadı."));
            return image;
        }
        AcademicStudyDocument.FirstFourPhraseEvidence phrase = document.firstFourPhraseEvidence();
        boolean explicitPhraseContract = !document.firstFourInventoryPeaks().isEmpty()
                || !phrase.degreePeaks().isEmpty()
                || phrase.qualifyingPhraseCount() > 0
                || phrase.completePhraseCount() > 0
                || phrase.partialPhraseCount() > 0
                || phrase.evidenceEventCount() > 0;
        if (explicitPhraseContract && !phrase.complete()) {
            empty(g, phrase.partialOrMixed()
                    ? firstFourText(
                    "Partial/mixed evidence: no percentage is reported.",
                    "Kısmi/karma kanıt: yüzde verilmez.")
                    : firstFourText(
                    "Insufficient ordered tetrachord evidence: no percentage is reported.",
                    "Yetersiz sıralı dörtlü kanıt: yüzde verilmez."));
            return image;
        }
        int left = 320, right = WIDTH - 100;
        int usable = right - left;
        for (int i = 0; i < profiles.size(); i++) {
            AcademicStudyDocument.ProfileComparison profile = profiles.get(i);
            double score = profile.scorePercent();
            int y = top + i * rowHeight;
            g.setColor(new Color(231, 236, 241));
            g.fillRoundRect(left, y, usable, 35, 18, 18);
            g.setColor(i == 0 ? RED : TEAL);
            g.fillRoundRect(left, y, (int) Math.round(usable * score / 100.0), 35, 18, 18);
            g.setFont(new Font("SansSerif", Font.BOLD, 23));
            g.setColor(INK);
            g.drawString((i + 1) + ". " + profile.profileName(), 75, y + 27);
            g.drawString(ReportLocalization.percentValue(score, 1), right + 15, y + 27);
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 21));
        g.setColor(MUTED);
        g.drawString(t("report.chart.profileSimilarityNote",
                        "A high score indicates interval-profile proximity; it is not a makam diagnosis by itself.",
                        "Yüksek puan aralık profilinin yakınlığını gösterir; tek başına makam teşhisi değildir."),
                75, image.getHeight() - 65);
        g.dispose();
        return image;
    }

    /** Ranks only the discriminating second, third and fourth degrees. */
    public BufferedImage profileRanking(AcademicStudyDocument document) {
        List<AcademicStudyDocument.ProfileComparison> profiles = document.profileComparisons().stream()
                // Bûselik remains a valid pitch name elsewhere in the report,
                // but it is deliberately excluded from the user's comparison
                // profile set.  Keep this defensive filter here as well as in
                // the reference catalogue so an older serialized result cannot
                // reintroduce the row into a newly exported report.
                .filter(value -> !"buselik".equals(profileKey(value.profileName())))
                .sorted(Comparator.comparing((AcademicStudyDocument.ProfileComparison value) ->
                                value.firstFourScorePercent() < 80.0)
                        .thenComparing(Comparator.comparingDouble(
                                AcademicStudyDocument.ProfileComparison::firstFourScorePercent).reversed())
                        .thenComparing(AcademicStudyDocument.ProfileComparison::profileName))
                .toList();
        int rowHeight = 62;
        int top = 125;
        // Six profiles fit the historical 720 px chart.  Supplementary
        // profiles are user-selectable, so grow this one chart instead of
        // silently hiding rows 9-14 behind the former top-eight cap.
        int chartHeight = Math.max(HEIGHT, top + profiles.size() * rowHeight + 100);
        BufferedImage image = canvas(firstFourText(
                "First-four-pitch similarity from the global pitch inventory",
                "Genel perde envanterine göre ilk dört ses benzerliği"), chartHeight);
        Graphics2D g = graphics(image);
        if (profiles.isEmpty()) {
            empty(g, t("report.chart.noProfileComparison",
                    "No scale / çeşni-group comparison was made.",
                    "Dizi / çeşni grubu karşılaştırması yapılmadı."));
            return image;
        }
        AcademicStudyDocument.FirstFourPhraseEvidence phrase = document.firstFourPhraseEvidence();
        boolean explicitPhraseContract = !document.firstFourInventoryPeaks().isEmpty()
                || !phrase.degreePeaks().isEmpty()
                || phrase.qualifyingPhraseCount() > 0
                || phrase.completePhraseCount() > 0
                || phrase.partialPhraseCount() > 0
                || phrase.evidenceEventCount() > 0;
        if (explicitPhraseContract && !phrase.complete()) {
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            g.setColor(RED);
            String note = phrase.partialOrMixed()
                    ? firstFourText("Partial/mixed phrase evidence • ranked global-inventory similarity",
                    "Kısmi/karma cümle kanıtı • sıralı genel envanter benzerliği")
                    : firstFourText("Insufficient ordered phrase evidence • ranked global-inventory similarity",
                    "Yetersiz sıralı cümle kanıtı • sıralı genel envanter benzerliği");
            g.drawString(note, 75, 105);
        }
        int left = 320, right = WIDTH - 100;
        int usable = right - left;
        int thresholdX = left + (int) Math.round(usable * .80);
        g.setColor(new Color(190, 31, 52, 150));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{9f, 7f}, 0f));
        g.drawLine(thresholdX, top - 25, thresholdX,
                top + profiles.size() * rowHeight - 24);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        centered(g, "80%", thresholdX, top - 34, RED);
        for (int i = 0; i < profiles.size(); i++) {
            AcademicStudyDocument.ProfileComparison profile = profiles.get(i);
            double score = profile.firstFourScorePercent();
            int y = top + i * rowHeight;
            g.setColor(new Color(231, 236, 241)); g.fillRoundRect(left, y, usable, 35, 18, 18);
            g.setColor(score + 1.0e-9 >= 80.0 ? RED : TEAL);
            g.fillRoundRect(left, y, (int) Math.round(usable * score / 100.0), 35, 18, 18);
            g.setFont(new Font("SansSerif", Font.BOLD, 23));
            g.setColor(INK); g.drawString((i + 1) + ". " + profile.profileName(), 75, y + 27);
            g.setColor(INK); g.drawString(ReportLocalization.percentValue(score, 1), right + 15, y + 27);
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 21));
        g.setColor(MUTED);
        String footer = explicitPhraseContract && !phrase.complete()
                ? firstFourText(
                "Percentages rank the fixed global pitch inventory; ordered phrase evidence is reported separately.",
                "Yüzdeler sabit genel perde envanterini sıralar; sıralı cümle kanıtı ayrı raporlanır.")
                : t("report.chart.profileSimilarityNote",
                "The score uses only degrees 2-4 relative to the confirmed tonic; it is not a makam diagnosis by itself.",
                "Puan yalnız onaylı karara göre 2.-4. dereceleri kullanır; tek başına makam teşhisi değildir.");
        g.drawString(footer, 75, image.getHeight() - 65);
        g.dispose();
        return image;
    }

    /** Ranks temporally ordered first-four paths for one melodic direction. */
    public BufferedImage directionalFirstFourRanking(AcademicStudyDocument document,
                                                      String direction) {
        List<AcademicStudyDocument.DirectionalFirstFourProfile> profiles =
                document.directionalFirstFourProfiles().stream()
                        .filter(value -> value.direction().equals(direction))
                        .sorted(Comparator.comparingInt(
                                AcademicStudyDocument.DirectionalFirstFourProfile::rank))
                        .toList();
        boolean ascending = "Çıkıcı".equals(direction);
        String title = ascending
                ? t("report.ascendingEvidence", "Ascending evidence", "Çıkıcı kanıt") + " — "
                + t("report.firstFourSimilarity",
                "Scale / çeşni similarity of the first four pitches from the tonic",
                "Karardan başlayan ilk dört sesin dizi / çeşni benzerliği")
                : t("report.descendingEvidence", "Descending evidence", "İnici kanıt") + " — "
                + t("report.firstFourSimilarity",
                "Scale / çeşni similarity of the first four pitches from the tonic",
                "Karardan başlayan ilk dört sesin dizi / çeşni benzerliği");
        int rowHeight = 67;
        int top = 125;
        int chartHeight = Math.max(HEIGHT, top + profiles.size() * rowHeight + 125);
        BufferedImage image = canvas(title, chartHeight);
        Graphics2D g = graphics(image);
        if (profiles.isEmpty()) {
            empty(g, t("report.noFirstFourMatchEvidence",
                    "No sufficient matching evidence could be produced for the first four degrees.",
                    "İlk dört derece için yeterli eşleşme kanıtı üretilemedi."));
            return image;
        }
        int left = 355, right = WIDTH - 120;
        int usable = right - left;
        int thresholdX = left + (int) Math.round(usable * .80);
        g.setColor(new Color(190, 31, 52, 150));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{9f, 7f}, 0f));
        g.drawLine(thresholdX, top - 25, thresholdX,
                top + profiles.size() * rowHeight - 24);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        centered(g, "80%", thresholdX, top - 34, RED);
        for (int i = 0; i < profiles.size(); i++) {
            AcademicStudyDocument.DirectionalFirstFourProfile profile = profiles.get(i);
            int y = top + i * rowHeight;
            g.setColor(new Color(231, 236, 241));
            g.fillRoundRect(left, y, usable, 35, 18, 18);
            boolean scored = Double.isFinite(profile.scorePercent());
            if (scored) {
                boolean trusted = profile.scorePercent() + 1.0e-9 >= 80.0
                        && !"INSUFFICIENT".equals(profile.evidenceStatus());
                g.setColor(trusted ? RED : TEAL);
                g.fillRoundRect(left, y,
                        (int) Math.round(usable * Math.max(0.0,
                                Math.min(100.0, profile.scorePercent())) / 100.0),
                        35, 18, 18);
            }
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.setColor(INK);
            g.drawString((i + 1) + ". " + profile.profileName(), 70, y + 25);
            g.setFont(new Font("SansSerif", Font.PLAIN, 17));
            g.setColor(MUTED);
            g.drawString("n=" + profile.phraseCount() + " / e="
                    + profile.evidenceEventCount(), 70, y + 47);
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.setColor(INK);
            g.drawString(scored ? ReportLocalization.percentValue(profile.scorePercent(), 1) : "—",
                    right + 15, y + 25);
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        g.setColor(MUTED);
        String paths = ascending
                ? "1→2→3→4 | 1→2→3 | 2→3→4 | 1→3→4 | 1→2 | 2→3 | 3→4 | 1→3 | 1→4"
                : "4→3→2→1 | 4→3→2 | 3→2→1 | 4→2→1 | 4→3 | 3→2 | 2→1 | 4→2 | 4→1";
        g.drawString(t("report.observedPath", "Observed path", "Gözlenen yol") + ": " + paths,
                70, image.getHeight() - 87);
        g.drawString("n=" + t("report.phrase", "Phrase", "Cümle")
                        + "  •  e=" + t("report.eventCount", "Event count", "Olay sayısı")
                        + "  •  " + t("report.similarity", "Similarity", "Benzerlik") + " ≥ %80",
                70, image.getHeight() - 58);
        g.dispose();
        return image;
    }

    private static List<ObservedCluster> observedClusters(
            List<AcademicStudyDocument.Event> source) {
        List<AcademicStudyDocument.Event> events = source.stream()
                .filter(value -> Double.isFinite(value.relativeHc()))
                .sorted(Comparator.comparingDouble(AcademicStudyDocument.Event::relativeHc)
                        .thenComparingInt(AcademicStudyDocument.Event::index))
                .toList();
        if (events.isEmpty()) return List.of();
        List<ObservedCluster> result = new ArrayList<>();
        List<AcademicStudyDocument.Event> current = new ArrayList<>();
        for (AcademicStudyDocument.Event event : events) {
            if (!current.isEmpty()) {
                double centre = weightedEventCentre(current);
                // 0.38 Hc keeps genuinely separate microtonal modes visible;
                // it is well below the one-Hc spacing of adjacent AEU centres.
                if (Math.abs(event.relativeHc() - centre) > .38) {
                    result.add(observedCluster(current));
                    current.clear();
                }
            }
            current.add(event);
        }
        if (!current.isEmpty()) result.add(observedCluster(current));
        return List.copyOf(result);
    }

    private static double weightedEventCentre(List<AcademicStudyDocument.Event> events) {
        double numerator = 0.0;
        double denominator = 0.0;
        for (AcademicStudyDocument.Event event : events) {
            double confidence = event.confidence() > 1.0
                    ? event.confidence() / 100.0 : event.confidence();
            double weight = Math.max(.02, event.durationSeconds())
                    * Math.max(.25, Math.min(1.0, confidence));
            numerator += event.relativeHc() * weight;
            denominator += weight;
        }
        return denominator <= 0.0
                ? events.stream().mapToDouble(AcademicStudyDocument.Event::relativeHc)
                .average().orElse(0.0)
                : numerator / denominator;
    }

    private static ObservedCluster observedCluster(
            List<AcademicStudyDocument.Event> events) {
        Map<String, Integer> labelCounts = new LinkedHashMap<>();
        for (AcademicStudyDocument.Event event : events) {
            String label = event.noteName();
            if (label == null || label.isBlank() || label.startsWith("Adlandırılamayan")) {
                label = firstFourText("Intermediate pitch", "Ara ses");
            }
            labelCounts.merge(label, 1, Integer::sum);
        }
        String label = labelCounts.entrySet().stream()
                .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(firstFourText("Intermediate pitch", "Ara ses"));
        int measurable = (int) events.stream()
                .filter(value -> value.measurable() && value.included()).count();
        return new ObservedCluster(weightedEventCentre(events), label, events.size(), measurable,
                events.stream().mapToDouble(AcademicStudyDocument.Event::durationSeconds).sum());
    }

    private static CenterDeviation nearestCenter(ObservedCluster cluster,
                                                   List<TheoryMarker> theory) {
        TheoryMarker reference = theory.stream().min(Comparator.comparingDouble(
                        value -> Math.abs(value.hc() - cluster.centerHc())))
                .orElseThrow();
        return new CenterDeviation(cluster, reference, cluster.centerHc() - reference.hc());
    }

    private static PhraseProfileScore phraseProfileScore(
            AcademicStudyDocument.ProfileComparison profile,
            List<AcademicStudyDocument.FirstFourPeak> peaks) {
        double weightedSimilarity = 0.0;
        double totalWeight = 0.0;
        double weightedAbsoluteError = 0.0;
        java.util.Set<Integer> observedDegrees = new java.util.LinkedHashSet<>();
        for (AcademicStudyDocument.FirstFourPeak peak : peaks) {
            AcademicStudyDocument.DegreeComparison theory = profile.firstFourDegrees().stream()
                    .filter(value -> value.degree() == peak.degree())
                    .findFirst().orElse(null);
            if (theory == null || !Double.isFinite(theory.theoreticalHc())
                    || !Double.isFinite(peak.centerHc())) continue;
            double weight = peak.evidenceWeight() > 0.0 ? peak.evidenceWeight()
                    : peak.totalDurationSeconds() > 0.0 ? peak.totalDurationSeconds()
                    : Math.max(1.0, peak.eventCount());
            double residual = Math.abs(peak.centerHc() - theory.theoreticalHc());
            weightedSimilarity += weight * Math.exp(-residual);
            weightedAbsoluteError += weight * residual;
            totalWeight += weight;
            observedDegrees.add(peak.degree());
        }
        if (totalWeight <= 0.0) {
            return new PhraseProfileScore(profile.profileName(), Double.NaN, 0.0, Double.NaN);
        }
        double coverage = Math.min(1.0, observedDegrees.size() / 3.0);
        double score = 100.0 * weightedSimilarity / totalWeight * (.55 + .45 * coverage);
        return new PhraseProfileScore(profile.profileName(),
                Math.max(0.0, Math.min(100.0, score)), coverage * 100.0,
                weightedAbsoluteError / totalWeight);
    }

    private static double niceHcStep(double range) {
        if (range <= 8.0) return 1.0;
        if (range <= 18.0) return 2.0;
        if (range <= 40.0) return 5.0;
        return 10.0;
    }

    private static void drawPeakLabel(Graphics2D g, String primary, String secondary,
                                      int centerX, int baseline, int left, int right,
                                      Color color) {
        Font primaryFont = new Font("SansSerif", Font.BOLD, 15);
        Font secondaryFont = new Font("SansSerif", Font.PLAIN, 13);
        int width = Math.max(g.getFontMetrics(primaryFont).stringWidth(primary),
                g.getFontMetrics(secondaryFont).stringWidth(secondary)) + 20;
        width = Math.min(width, 270);
        int x = Math.max(left, Math.min(right - width, centerX - width / 2));
        g.setColor(new Color(255, 255, 255, 232));
        g.fillRoundRect(x, baseline - 23, width, 48, 12, 12);
        g.setColor(new Color(207, 218, 228));
        g.setStroke(new BasicStroke(1.1f));
        g.drawRoundRect(x, baseline - 23, width, 48, 12, 12);
        g.setFont(primaryFont);
        centered(g, ellipsize(g, primary, width - 10), x + width / 2, baseline - 3, color);
        g.setFont(secondaryFont);
        centered(g, secondary, x + width / 2, baseline + 17, MUTED);
    }

    private static String ellipsize(Graphics2D g, String text, int maximumWidth) {
        if (text == null) return "";
        if (g.getFontMetrics().stringWidth(text) <= maximumWidth) return text;
        String suffix = "…";
        int end = text.length();
        while (end > 1 && g.getFontMetrics().stringWidth(
                text.substring(0, end) + suffix) > maximumWidth) end--;
        return text.substring(0, Math.max(1, end)) + suffix;
    }

    private static String profileKey(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT)
                .replace('ı', 'i').replace("â", "a").replace("û", "u")
                .replaceAll("[^a-z0-9]", "");
    }

    private record ObservedCluster(double centerHc, String label, int eventCount,
                                   int measurableCount, double totalDurationSeconds) { }
    private record CenterDeviation(ObservedCluster cluster, TheoryMarker reference,
                                   double residualHc) { }
    private record PhraseProfileScore(String profileName, double scorePercent,
                                      double coveragePercent, double meanAbsoluteErrorHc) { }

    private static BufferedImage canvas(String title) {
        return canvas(title, HEIGHT);
    }

    private static BufferedImage canvas(String title, int height) {
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(image);
        g.setColor(Color.WHITE); g.fillRect(0, 0, WIDTH, height);
        g.setColor(INK); g.setFont(new Font("SansSerif", Font.BOLD, 30));
        g.drawString(title, 55, 58);
        g.setColor(RED); g.fillRoundRect(55, 74, 115, 7, 4, 4);
        g.dispose();
        return image;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static String directionLabel(String value) {
        return switch (value) {
            case "Çıkıcı" -> t("report.ascending", "Ascending", "Çıkıcı");
            case "İnici" -> t("report.descending", "Descending", "İnici");
            case "Segâh kalışı" -> t("report.sustained", "Segâh sustain", "Segâh kalışı");
            case "Yatay/Karma" -> t("report.horizontalMixed", "Horizontal/Mixed", "Yatay/Karma");
            default -> value;
        };
    }

    private static String t(String key, String english, String turkish, Object... arguments) {
        return ReportLocalization.text(key, english, turkish, arguments);
    }

    private static String firstFourText(String english, String turkish) {
        return "tr".equals(ReportLocalization.locale().getLanguage()) ? turkish : english;
    }

    private static void axes(Graphics2D g, int left, int right, int top, int bottom,
                             String xLabel, String yLabel,
                             double xMin, double xMax, double yMin, double yMax) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        for (int i = 0; i <= 5; i++) {
            int px = left + (right - left) * i / 5;
            int py = bottom - (bottom - top) * i / 5;
            g.setColor(GRID); g.setStroke(new BasicStroke(1.5f));
            g.drawLine(px, top, px, bottom); g.drawLine(left, py, right, py);
            g.setColor(MUTED);
            String xText = formatAxis(xMin + (xMax - xMin) * i / 5.0);
            centered(g, xText, px, bottom + 28, MUTED);
            String yText = formatAxis(yMin + (yMax - yMin) * i / 5.0);
            g.drawString(yText, left - 14 - g.getFontMetrics().stringWidth(yText), py + 6);
        }
        g.setColor(INK); g.setStroke(new BasicStroke(2f));
        g.drawLine(left, bottom, right, bottom); g.drawLine(left, top, left, bottom);
        centered(g, xLabel, (left + right) / 2, HEIGHT - 30, INK);
        g.rotate(-Math.PI / 2);
        centered(g, yLabel, -(top + bottom) / 2, 34, INK);
        g.rotate(Math.PI / 2);
    }

    private static void legend(Graphics2D g, int x, int y, List<Legend> entries) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        int cursor = x;
        for (Legend entry : entries) {
            g.setColor(entry.color()); g.fillOval(cursor, y - 12, 18, 18);
            g.setColor(INK); g.drawString(entry.label(), cursor + 26, y + 2);
            cursor += 45 + g.getFontMetrics().stringWidth(entry.label());
        }
    }

    private static void empty(Graphics2D g, String message) {
        g.setColor(MUTED); g.setFont(new Font("SansSerif", Font.BOLD, 28));
        centered(g, message, WIDTH / 2, HEIGHT / 2, MUTED);
        g.dispose();
    }

    private static void label(Graphics2D g, String text, int x, int y, Color color) {
        g.setFont(new Font("SansSerif", Font.BOLD, 19)); g.setColor(color); g.drawString(text, x, y);
    }

    private static void centered(Graphics2D g, String text, int center, int y, Color color) {
        g.setColor(color); FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, center - metrics.stringWidth(text) / 2, y);
    }

    private static int x(double value, double min, double max, int left, int right) {
        return left + (int) Math.round((value - min) / Math.max(1.0e-9, max - min) * (right - left));
    }

    private static int y(double value, double min, double max, int top, int bottom) {
        return bottom - (int) Math.round((value - min) / Math.max(1.0e-9, max - min) * (bottom - top));
    }

    private static String formatAxis(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 100 || Math.abs(value - Math.rint(value)) < 0.01) {
            return ReportLocalization.number(value, 0);
        }
        return ReportLocalization.number(value, 1);
    }

    private static double percentile(double[] values, double fraction) {
        if (values.length == 0) return 0.0;
        double position = Math.max(0, Math.min(values.length - 1, (values.length - 1) * fraction));
        int lower = (int) Math.floor(position), upper = (int) Math.ceil(position);
        if (lower == upper) return values[lower];
        return values[lower] * (upper - position) + values[upper] * (position - lower);
    }

    private record Legend(String label, Color color) { }
}
