package com.makambox.studio.ui;

import com.makambox.studio.i18n.I18n;
import com.makambox.studio.i18n.LocalizationService;
import com.makambox.studio.study.glissando.GlissandoDirection;
import com.makambox.studio.study.glissando.PitchPairGlissandoEvent;
import com.makambox.studio.study.glissando.PitchPairGlissandoStatistics;
import com.makambox.studio.study.glissando.PitchPairGlissandoStudyResult;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Read-only results view for glissandi between two selected makam pitches. */
public final class PitchPairGlissandoResultsPanel extends JPanel {
    private static final Color SURFACE = new Color(252, 250, 247);
    private static final Color INK = new Color(29, 39, 53);
    private static final Color MUTED = new Color(91, 111, 134);
    private static final Color BORDER = new Color(216, 209, 200);
    private static final Color BURGUNDY = new Color(169, 22, 50);
    private static final Color BLUE = new Color(35, 104, 168);
    private static final double HC_CENT = 1200.0 / 53.0;

    private final Map<GlissandoDirection, JLabel> summaryLabels =
            new EnumMap<>(GlissandoDirection.class);
    private final Map<GlissandoDirection, JLabel> summaryHeadings =
            new EnumMap<>(GlissandoDirection.class);
    private final LocalizationService i18n = I18n.get();
    private final EventModel primaryModel = new EventModel();
    private final EventModel auditModel = new EventModel();
    private final JTable primaryTable = table(primaryModel);
    private final JTable auditTable = table(auditModel);
    private final JTabbedPane tabs = new JTabbedPane();
    private PitchPairGlissandoStudyResult currentResult;
    private BiConsumer<Double, Double> timeRangeListener = (start, end) -> { };

    public PitchPairGlissandoResultsPanel() {
        super(new BorderLayout(0, 10));
        setOpaque(true);
        setBackground(SURFACE);
        setName("makam-glissando-results");
        setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        add(buildSummary(), BorderLayout.NORTH);
        tabs.addTab("", scroll(primaryTable));
        tabs.addTab("", scroll(auditTable));
        add(tabs, BorderLayout.CENTER);
        bindSelection(primaryTable, primaryModel);
        bindSelection(auditTable, auditModel);
        setResult(null);
        refreshLocalization();
    }

    public void setTimeRangeListener(BiConsumer<Double, Double> listener) {
        timeRangeListener = listener == null ? (start, end) -> { } : listener;
    }

    public void setResult(PitchPairGlissandoStudyResult result) {
        currentResult = result;
        primaryModel.setRows(result == null ? List.of() : result.primaryEvents());
        auditModel.setRows(result == null ? List.of() : result.auditEvents());
        configureEventColumns(primaryTable);
        configureEventColumns(auditTable);
        summaryLabels.get(GlissandoDirection.ASCENDING).setText(statisticsHtml(
                result == null ? null : result.ascendingStatistics()));
        summaryLabels.get(GlissandoDirection.DESCENDING).setText(statisticsHtml(
                result == null ? null : result.descendingStatistics()));
    }

    /** Updates tabs, headings, columns and locale-specific numbers in place. */
    public void refreshLocalization() {
        tabs.setTitleAt(0, tr("result.glissando.tab.primary", "Ana istatistikteki glissandolar"));
        tabs.setTitleAt(1, tr("result.glissando.tab.audit", "Denetim adayları"));
        for (GlissandoDirection direction : GlissandoDirection.values()) {
            JLabel heading = summaryHeadings.get(direction);
            if (heading != null) {
                String localizedDirection = directionLabel(direction);
                heading.setText(tr("result.glissando.summary.directionTitle",
                        localizedDirection + " glissandolar", localizedDirection));
            }
        }
        primaryModel.fireTableStructureChanged();
        auditModel.fireTableStructureChanged();
        configureEventColumns(primaryTable);
        configureEventColumns(auditTable);
        setResult(currentResult);
        i18n.applyOrientation(this);
        revalidate();
        repaint();
    }

    private JPanel buildSummary() {
        JPanel summary = new JPanel(new GridLayout(1, 2, 10, 0));
        summary.setOpaque(false);
        for (GlissandoDirection direction : GlissandoDirection.values()) {
            JLabel values = new JLabel("", SwingConstants.CENTER);
            values.setForeground(INK);
            values.setFont(values.getFont().deriveFont(10.5f));
            summaryLabels.put(direction, values);
            JPanel card = new JPanel(new BorderLayout(0, 4));
            card.setBackground(Color.WHITE);
            Color accent = direction == GlissandoDirection.ASCENDING ? BLUE : BURGUNDY;
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(2, 1, 1, 1, accent),
                    BorderFactory.createEmptyBorder(7, 10, 7, 10)));
            JLabel heading = new JLabel("", SwingConstants.CENTER);
            heading.setForeground(accent);
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, 12f));
            summaryHeadings.put(direction, heading);
            card.add(heading, BorderLayout.NORTH);
            card.add(values, BorderLayout.CENTER);
            summary.add(card);
        }
        return summary;
    }

    private void bindSelection(JTable table, EventModel model) {
        table.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || table.getSelectedRow() < 0) return;
            PitchPairGlissandoEvent value = model.row(
                    table.convertRowIndexToModel(table.getSelectedRow()));
            timeRangeListener.accept(value.startSeconds(), value.endSeconds());
        });
    }

    private String statisticsHtml(PitchPairGlissandoStatistics value) {
        if (value == null || value.count() == 0) {
            return "<html><div style='text-align:center'>n: 0<br>"
                    + tr("common.noMeasurableEvent", "Henüz ölçülebilir olay yok")
                    + "</div></html>";
        }
        return "<html><div style='text-align:center'>n: " + i18n.formatInteger(value.count())
                + " • " + tr("result.glissando.summary.medianDuration", "Medyan süre") + ": "
                + number(value.medianDurationSeconds(), 3) + " " + tr("common.seconds", "sn") + "<br>"
                + tr("result.glissando.summary.medianSpan", "Medyan genişlik") + ": "
                + number(value.medianSpanCent(), 2) + " C / "
                + number(value.medianSpanCent() / HC_CENT, 3) + " Hc • "
                + tr("result.glissando.summary.intervalError", "Aralık hatası") + ": "
                + signed(value.medianIntervalErrorCent(), 2) + " C / "
                + signed(value.medianIntervalErrorCent() / HC_CENT, 3) + " Hc<br>"
                + tr("result.glissando.summary.medianSpeed", "Medyan hız") + ": "
                + signed(value.medianSignedSpeedCentPerSecond(), 1) + " C/"
                + tr("common.seconds", "sn") + " / "
                + signed(value.medianSignedSpeedCentPerSecond() / HC_CENT, 3) + " Hc/"
                + tr("common.seconds", "sn") + " • "
                + tr("result.glissando.summary.meanReverse", "Ort. geri dönüş") + ": "
                + number(value.meanReverseMovementCent(), 2) + " C</div></html>";
    }

    private static JTable table(AbstractTableModel model) {
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setGridColor(BORDER);
        table.setForeground(INK);
        table.setBackground(Color.WHITE);
        return table;
    }

    private static JScrollPane scroll(JTable table) {
        JScrollPane pane = new JScrollPane(table);
        pane.getViewport().setBackground(Color.WHITE);
        return pane;
    }

    private static void configureEventColumns(JTable table) {
        int[] widths = {44, 72, 92, 112, 76, 96, 96, 96, 96, 96, 96,
                148, 148, 138, 138, 138, 138, 138, 92, 92, 78, 126, 300};
        for (int index = 0; index < Math.min(widths.length,
                table.getColumnModel().getColumnCount()); index++) {
            table.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
        }
    }

    private final class EventModel extends AbstractTableModel {
        private final String[] keys = {"result.glissando.column.number",
                "result.glissando.column.direction", "result.glissando.column.shape",
                "result.glissando.column.time", "result.glissando.column.duration",
                "result.glissando.column.startHz", "result.glissando.column.endHz",
                "result.glissando.column.startCent", "result.glissando.column.startHc",
                "result.glissando.column.endCent", "result.glissando.column.endHc",
                "result.glissando.column.startDeviation", "result.glissando.column.endDeviation",
                "result.glissando.column.span", "result.glissando.column.theoretical",
                "result.glissando.column.intervalError", "result.glissando.column.speed",
                "result.glissando.column.reverseMovement", "result.glissando.column.monotonicity",
                "result.glissando.column.voicedCoverage", "result.glissando.column.confidence",
                "result.glissando.column.endpoints", "result.glissando.column.status"};
        private final String[] fallbacks = {"#", "Yön", "Biçim", "Zaman", "Süre",
                "Başlangıç Hz", "Bitiş Hz", "Başlangıç C", "Başlangıç Hc", "Bitiş C",
                "Bitiş Hc", "Başlangıç sapması C/Hc", "Bitiş sapması C/Hc",
                "Genişlik C/Hc", "Kuramsal C/Hc", "Aralık hatası C/Hc",
                "Hız C/sn / Hc/sn", "Geri dönüş C/Hc", "Tek yönlülük", "F0 kapsamı",
                "Güven", "Uç temasları", "Durum / denetim notu"};
        private List<PitchPairGlissandoEvent> rows = List.of();
        void setRows(List<PitchPairGlissandoEvent> values) {
            rows = List.copyOf(values == null ? List.of() : values);
            fireTableStructureChanged();
        }
        PitchPairGlissandoEvent row(int index) { return rows.get(index); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return keys.length; }
        @Override public String getColumnName(int column) { return tr(keys[column], fallbacks[column]); }
        @Override public Object getValueAt(int row, int column) {
            PitchPairGlissandoEvent value = rows.get(row);
            return switch (column) {
                case 0 -> value.index();
                case 1 -> directionLabel(value.direction());
                case 2 -> shapeLabel(value.shape().name());
                case 3 -> range(value.startSeconds(), value.endSeconds(), 2);
                case 4 -> number(value.durationSeconds(), 3) + " " + tr("common.seconds", "sn");
                case 5 -> number(value.startHertz(), 3);
                case 6 -> number(value.endHertz(), 3);
                case 7 -> signed(value.startRelativeCent(), 2);
                case 8 -> signed(value.startRelativeHc(), 3);
                case 9 -> signed(value.endRelativeCent(), 2);
                case 10 -> signed(value.endRelativeHc(), 3);
                case 11 -> pair(value.startEndpointDeviationCent(),
                        value.startEndpointDeviationHc());
                case 12 -> pair(value.endEndpointDeviationCent(),
                        value.endEndpointDeviationHc());
                case 13 -> pair(value.observedSpanCent(), value.observedSpanHc());
                case 14 -> pair(value.theoreticalSpanCent(), value.theoreticalSpanHc());
                case 15 -> pair(value.intervalErrorCent(), value.intervalErrorHc());
                case 16 -> signed(value.signedSpeedCentPerSecond(), 1) + " / "
                        + signed(value.signedSpeedHcPerSecond(), 3);
                case 17 -> pair(value.reverseMovementCent(), value.reverseMovementHc());
                case 18 -> i18n.formatPercent(value.monotonicity(), 0);
                case 19 -> i18n.formatPercent(value.voicedCoverage(), 0);
                case 20 -> i18n.formatPercent(value.confidence(), 0);
                case 21 -> value.bothEndpointsSettled()
                        ? tr("result.glissando.endpoints.both", "İkisi de kararlı")
                        : value.originEndpointSettled()
                        ? tr("result.glissando.endpoints.originOnly", "Yalnız başlangıç")
                        : value.destinationEndpointSettled()
                        ? tr("result.glissando.endpoints.destinationOnly", "Yalnız bitiş")
                        : tr("result.glissando.endpoints.neither", "Kararlı değil");
                default -> value.primary() ? tr("glissando.status.primary", "Ana istatistikte")
                        : auditReason(value.auditReason());
            };
        }

        private String pair(double cent, double hc) {
            return signed(cent, 2) + " / " + signed(hc, 3);
        }
    }

    private String tr(String key, String fallback, Object... arguments) {
        return i18n.textOrDefault(key, fallback, arguments);
    }

    private String number(double value, int digits) {
        return i18n.formatNumber(value, digits, digits);
    }

    private String signed(double value, int digits) {
        return i18n.formatSignedNumber(value, digits, digits);
    }

    private String range(double start, double end, int digits) {
        return number(start, digits) + "–" + number(end, digits);
    }

    private String directionLabel(GlissandoDirection direction) {
        return direction == GlissandoDirection.ASCENDING
                ? tr("glissando.direction.ascending", "Çıkıcı")
                : tr("glissando.direction.descending", "İnici");
    }

    private String shapeLabel(String name) {
        return switch (name) {
            case "DIRECT" -> tr("glissando.shape.direct", "Düz");
            case "WITH_REVERSAL" -> tr("glissando.shape.reverse", "Geri dönüşlü");
            default -> tr("glissando.shape.mixed", "Karma");
        };
    }

    private String auditReason(String reason) {
        if (reason == null || reason.isBlank()) return tr("common.auditNote", "Denetim notu");
        String[] reasons = reason.split(";\\s*");
        java.util.ArrayList<String> translated = new java.util.ArrayList<>();
        for (String part : reasons) {
            translated.add(switch (part) {
                case "Süre kısa" -> tr("result.glissando.audit.short", part);
                case "Süre uzun" -> tr("result.glissando.audit.long", part);
                case "Genişlik yetersiz" -> tr("result.glissando.audit.narrow", part);
                case "Tek yönlülük düşük" -> tr("result.glissando.audit.lowMonotonicity", part);
                case "Sürekli hareket kanıtı yetersiz" -> tr("result.glissando.audit.lowMovement", part);
                case "Ani perde sıçraması" -> tr("result.glissando.audit.jump", part);
                case "F0 boşluğu uzun" -> tr("result.glissando.audit.gap", part);
                case "Güvenilir F0 kapsamı düşük" -> tr("result.glissando.audit.lowCoverage", part);
                case "Arada kararlı duruş var" -> tr("result.glissando.audit.intermediateHold", part);
                case "Başlangıç perdesinde kararlı temas yok" -> tr("result.glissando.audit.origin", part);
                case "Bitiş perdesinde kararlı temas yok" -> tr("result.glissando.audit.destination", part);
                // Keep the visible surface language-pure even if a future
                // analyser adds a reason that this view does not know yet.
                default -> tr("common.auditNote", "Denetim notu");
            });
        }
        return String.join("; ", translated);
    }

    String diagnosticTabTitle(int index) {
        return tabs.getTitleAt(index);
    }

    String diagnosticColumnName(int column) {
        return primaryModel.getColumnName(column);
    }

    String diagnosticSummaryHeading(GlissandoDirection direction) {
        return summaryHeadings.get(direction).getText();
    }

    String diagnosticFormattedNumber(double value) {
        return number(value, 2);
    }
}
