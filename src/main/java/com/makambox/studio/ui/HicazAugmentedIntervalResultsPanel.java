package com.makambox.studio.ui;

import com.makambox.studio.i18n.I18n;
import com.makambox.studio.i18n.LocalizationService;
import com.makambox.studio.study.hicaz.HicazAugmentedIntervalMeasurement;
import com.makambox.studio.study.hicaz.HicazAugmentedIntervalStudyResult;
import com.makambox.studio.study.hicaz.HicazDegreeEvent;
import com.makambox.studio.study.hicaz.HicazDegreeStatistics;
import com.makambox.studio.study.hicaz.HicazIntervalStatistics;
import com.makambox.studio.study.hicaz.HicazPhrase;
import com.makambox.studio.study.hicaz.HicazPhraseDirection;
import com.makambox.studio.study.hicaz.HicazTetrachordDegree;

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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Read-only results view for the academic Hicaz augmented-interval study. */
public final class HicazAugmentedIntervalResultsPanel extends JPanel {
    private static final Color SURFACE = new Color(252, 250, 247);
    private static final Color INK = new Color(29, 39, 53);
    private static final Color MUTED = new Color(91, 111, 134);
    private static final Color BORDER = new Color(216, 209, 200);
    private static final Color BURGUNDY = new Color(169, 22, 50);
    private static final Color BLUE = new Color(35, 104, 168);
    private static final double HC_CENT = 1200.0 / 53.0;

    private final Map<HicazPhraseDirection, Map<HicazTetrachordDegree, JLabel>> degreeCards =
            new EnumMap<>(HicazPhraseDirection.class);
    private final Map<HicazPhraseDirection, JLabel> intervalCards =
            new EnumMap<>(HicazPhraseDirection.class);
    private final Map<HicazPhraseDirection, Map<HicazTetrachordDegree, JLabel>> degreeHeadings =
            new EnumMap<>(HicazPhraseDirection.class);
    private final Map<HicazPhraseDirection, JLabel> intervalHeadings =
            new EnumMap<>(HicazPhraseDirection.class);
    private final LocalizationService i18n = I18n.get();
    private final PhraseModel phraseModel = new PhraseModel();
    private final DegreeEventModel degreeEventModel = new DegreeEventModel();
    private final IntervalModel intervalModel = new IntervalModel();
    private final JTable phraseTable = table(phraseModel);
    private final JTable degreeEventTable = table(degreeEventModel);
    private final JTable intervalTable = table(intervalModel);
    private final JTabbedPane tabs = new JTabbedPane();
    private HicazAugmentedIntervalStudyResult currentResult;
    private BiConsumer<Double, Double> timeRangeListener = (start, end) -> { };

    public HicazAugmentedIntervalResultsPanel() {
        super(new BorderLayout(0, 10));
        setOpaque(true);
        setBackground(SURFACE);
        setName("makam-hicaz-results");
        setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        add(buildSummary(), BorderLayout.NORTH);
        tabs.addTab("", scroll(phraseTable));
        tabs.addTab("", scroll(degreeEventTable));
        tabs.addTab("", scroll(intervalTable));
        add(tabs, BorderLayout.CENTER);
        bindSelections();
        setResult(null);
        refreshLocalization();
    }

    public void setTimeRangeListener(BiConsumer<Double, Double> listener) {
        timeRangeListener = listener == null ? (start, end) -> { } : listener;
    }

    public void setResult(HicazAugmentedIntervalStudyResult result) {
        currentResult = result;
        phraseModel.setRows(result == null ? List.of() : result.phrases());
        degreeEventModel.setRows(result == null ? List.of() : result.events());
        intervalModel.setRows(result == null ? List.of() : result.directIntervals());
        setColumnWidths(phraseTable, 44, 76, 104, 112, 74, 72, 70, 280);
        setColumnWidths(degreeEventTable, 44, 126, 112, 74, 88, 96, 96, 96,
                88, 88, 78, 74, 280);
        setColumnWidths(intervalTable, 44, 58, 76, 112, 148, 148, 118, 118,
                112, 112, 74, 280);
        for (HicazPhraseDirection direction : HicazPhraseDirection.values()) {
            for (HicazTetrachordDegree degree : List.of(
                    HicazTetrachordDegree.DEGREE_2,
                    HicazTetrachordDegree.DEGREE_3,
                    HicazTetrachordDegree.DEGREE_4)) {
                HicazDegreeStatistics statistics = result == null ? null
                        : result.degreeStatistics(direction, degree);
                degreeCards.get(direction).get(degree).setText(degreeHtml(statistics));
            }
            HicazIntervalStatistics interval = result == null ? null
                    : result.intervalStatistics(direction);
            intervalCards.get(direction).setText(intervalHtml(interval));
        }
    }

    /** Updates all visible labels and locale-dependent values without rebuilding the view. */
    public void refreshLocalization() {
        tabs.setTitleAt(0, tr("result.hicaz.tab.phrases", "Yönsel cümleler"));
        tabs.setTitleAt(1, tr("result.hicaz.tab.degreeEvents", "Derece olayları"));
        tabs.setTitleAt(2, tr("result.hicaz.tab.directInterval", "Doğrudan 2↔3 artık aralığı"));
        for (HicazPhraseDirection direction : HicazPhraseDirection.values()) {
            String directionText = directionLabel(direction);
            Map<HicazTetrachordDegree, JLabel> headings = degreeHeadings.get(direction);
            if (headings != null) {
                for (HicazTetrachordDegree degree : headings.keySet()) {
                    headings.get(degree).setText(tr("result.hicaz.summary.degreeTitle",
                            directionText + " • " + degree.number() + ". derece",
                            directionText, degree.number()));
                }
            }
            JLabel intervalHeading = intervalHeadings.get(direction);
            if (intervalHeading != null) {
                intervalHeading.setText(tr("result.hicaz.summary.intervalTitle",
                        directionText + " • 2↔3 artık aralık", directionText));
            }
        }
        phraseModel.fireTableStructureChanged();
        degreeEventModel.fireTableStructureChanged();
        intervalModel.fireTableStructureChanged();
        setColumnWidths(phraseTable, 44, 76, 104, 112, 74, 72, 70, 280);
        setColumnWidths(degreeEventTable, 44, 126, 112, 74, 88, 96, 96, 96,
                88, 88, 78, 74, 280);
        setColumnWidths(intervalTable, 44, 58, 76, 112, 148, 148, 118, 118,
                112, 112, 74, 280);
        setResult(currentResult);
        i18n.applyOrientation(this);
        revalidate();
        repaint();
    }

    private JPanel buildSummary() {
        JPanel directions = new JPanel(new GridLayout(2, 1, 0, 8));
        directions.setOpaque(false);
        for (HicazPhraseDirection direction : HicazPhraseDirection.values()) {
            JPanel row = new JPanel(new GridLayout(1, 4, 8, 0));
            row.setOpaque(false);
            Map<HicazTetrachordDegree, JLabel> labels =
                    new EnumMap<>(HicazTetrachordDegree.class);
            Map<HicazTetrachordDegree, JLabel> headings =
                    new EnumMap<>(HicazTetrachordDegree.class);
            for (HicazTetrachordDegree degree : List.of(
                    HicazTetrachordDegree.DEGREE_2,
                    HicazTetrachordDegree.DEGREE_3,
                    HicazTetrachordDegree.DEGREE_4)) {
                JLabel values = new JLabel("", SwingConstants.CENTER);
                labels.put(degree, values);
                JPanel card = summaryCard("", values, BLUE);
                JLabel heading = (JLabel) card.getComponent(0);
                headings.put(degree, heading);
                row.add(card);
            }
            JLabel interval = new JLabel("", SwingConstants.CENTER);
            intervalCards.put(direction, interval);
            JPanel intervalCard = summaryCard("", interval, BURGUNDY);
            intervalHeadings.put(direction, (JLabel) intervalCard.getComponent(0));
            row.add(intervalCard);
            degreeCards.put(direction, labels);
            degreeHeadings.put(direction, headings);
            directions.add(row);
        }
        return directions;
    }

    private static JPanel summaryCard(String title, JLabel values, Color accent) {
        JPanel card = new JPanel(new BorderLayout(0, 3));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 1, 1, 1, accent),
                BorderFactory.createEmptyBorder(6, 7, 6, 7)));
        JLabel heading = new JLabel(title, SwingConstants.CENTER);
        heading.setForeground(accent);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 11f));
        values.setForeground(INK);
        values.setFont(values.getFont().deriveFont(10f));
        card.add(heading, BorderLayout.NORTH);
        card.add(values, BorderLayout.CENTER);
        card.setMinimumSize(new Dimension(160, 82));
        return card;
    }

    private void bindSelections() {
        phraseTable.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || phraseTable.getSelectedRow() < 0) return;
            HicazPhrase value = phraseModel.row(phraseTable.convertRowIndexToModel(
                    phraseTable.getSelectedRow()));
            timeRangeListener.accept(value.startSeconds(), value.endSeconds());
        });
        degreeEventTable.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || degreeEventTable.getSelectedRow() < 0) return;
            HicazDegreeEvent value = degreeEventModel.row(degreeEventTable.convertRowIndexToModel(
                    degreeEventTable.getSelectedRow()));
            timeRangeListener.accept(value.startSeconds(), value.endSeconds());
        });
        intervalTable.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || intervalTable.getSelectedRow() < 0) return;
            HicazAugmentedIntervalMeasurement value = intervalModel.row(
                    intervalTable.convertRowIndexToModel(intervalTable.getSelectedRow()));
            timeRangeListener.accept(value.startSeconds(), value.endSeconds());
        });
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

    private static void setColumnWidths(JTable table, int... widths) {
        for (int index = 0; index < Math.min(widths.length,
                table.getColumnModel().getColumnCount()); index++) {
            table.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
        }
    }

    private String degreeHtml(HicazDegreeStatistics value) {
        if (value == null || value.count() == 0) return emptyHtml();
        return "<html><div style='text-align:center'>n: " + i18n.formatInteger(value.count())
                + " • " + tr("common.average", "Ortalama") + ": "
                + signed(value.meanDeviationCent(), 2) + " C / "
                + signed(value.meanDeviationHc(), 3) + " Hc<br>"
                + tr("common.median", "Medyan") + ": "
                + signed(value.medianDeviationCent(), 2) + " C / "
                + signed(value.medianDeviationHc(), 3) + " Hc<br>"
                + tr("common.minimum", "En pest") + "/" + tr("common.maximum", "en dik")
                + ": " + signed(value.minimumDeviationCent(), 2) + "/"
                + signed(value.maximumDeviationCent(), 2) + " C</div></html>";
    }

    private String intervalHtml(HicazIntervalStatistics value) {
        if (value == null || value.count() == 0) return emptyHtml();
        return "<html><div style='text-align:center'>n: " + i18n.formatInteger(value.count())
                + " • " + tr("common.observed", "Gözlenen") + ": "
                + number(value.meanObservedIntervalCent(), 2) + " C / "
                + number(value.meanObservedIntervalHc(), 3) + " Hc<br>"
                + tr("common.theoreticalDeviation", "Kuramdan fark") + ": "
                + signed(value.meanDeviationCent(), 2) + " C / "
                + signed(value.meanDeviationHc(), 3) + " Hc<br>"
                + tr("common.withinOneHc", "±1 Hc içinde") + ": "
                + i18n.formatPercent(value.withinOneHcPercent() / 100.0, 0)
                + "</div></html>";
    }

    private String emptyHtml() {
        return "<html><div style='text-align:center'>n: 0<br>"
                + tr("common.noMeasurableEvent", "Henüz ölçülebilir olay yok")
                + "</div></html>";
    }

    private abstract class RowsModel<T> extends AbstractTableModel {
        private List<T> rows = List.of();
        void setRows(List<T> values) {
            rows = List.copyOf(values == null ? List.of() : values);
            fireTableStructureChanged();
        }
        T row(int index) { return rows.get(index); }
        @Override public int getRowCount() { return rows.size(); }
    }

    private final class PhraseModel extends RowsModel<HicazPhrase> {
        private final String[] keys = {"result.hicaz.column.number",
                "result.hicaz.column.direction", "result.hicaz.column.path",
                "result.hicaz.column.time", "result.hicaz.column.duration",
                "result.hicaz.column.confidence", "result.hicaz.column.hasDirectInterval",
                "result.hicaz.column.status"};
        private final String[] fallbacks = {"#", "Yön", "Dizi yolu", "Zaman", "Süre",
                "Güven", "2↔3 var", "Durum / denetim notu"};
        @Override public int getColumnCount() { return keys.length; }
        @Override public String getColumnName(int column) { return tr(keys[column], fallbacks[column]); }
        @Override public Object getValueAt(int row, int column) {
            HicazPhrase value = row(row);
            return switch (column) {
                case 0 -> value.index();
                case 1 -> directionLabel(value.direction());
                case 2 -> value.pathLabel();
                case 3 -> range(value.startSeconds(), value.endSeconds(), 2);
                case 4 -> seconds(value.durationSeconds(), 2);
                case 5 -> i18n.formatPercent(value.confidence(), 0);
                case 6 -> value.containsDirectAugmentedInterval()
                        ? tr("common.yes", "Evet") : tr("common.no", "Hayır");
                default -> value.included() ? tr("common.mainStatistics", "Ana istatistikte")
                        : auditReason(value.auditReason());
            };
        }
    }

    private final class DegreeEventModel extends RowsModel<HicazDegreeEvent> {
        private final String[] keys = {"result.hicaz.column.number", "result.hicaz.column.degree",
                "result.hicaz.column.time", "result.hicaz.column.duration",
                "result.hicaz.column.hertz", "result.hicaz.column.observedCent",
                "result.hicaz.column.observedHc", "result.hicaz.column.theoreticalHc",
                "result.hicaz.column.deviationCent", "result.hicaz.column.deviationHc",
                "result.hicaz.column.intonation", "result.hicaz.column.confidence",
                "result.hicaz.column.status"};
        private final String[] fallbacks = {"#", "Derece", "Zaman", "Süre", "Hz",
                "Gözlenen C", "Gözlenen Hc", "Kuramsal Hc", "Sapma C", "Sapma Hc",
                "Pest/Dik", "Güven", "Durum / denetim notu"};
        @Override public int getColumnCount() { return keys.length; }
        @Override public String getColumnName(int column) { return tr(keys[column], fallbacks[column]); }
        @Override public Object getValueAt(int row, int column) {
            HicazDegreeEvent value = row(row);
            return switch (column) {
                case 0 -> value.index();
                case 1 -> degreeLabel(value.degree());
                case 2 -> range(value.startSeconds(), value.endSeconds(), 2);
                case 3 -> seconds(value.durationSeconds(), 2);
                case 4 -> number(value.observedHertz(), 3);
                case 5 -> signed(value.observedRelativeCent(), 2);
                case 6 -> signed(value.observedRelativeHc(), 3);
                case 7 -> signed(value.theoreticalRelativeHc(), 3);
                case 8 -> signed(value.deviationCent(), 2);
                case 9 -> signed(value.deviationHc(), 3);
                case 10 -> intonationLabel(value.deviationCent());
                case 11 -> i18n.formatPercent(value.confidence(), 0);
                default -> value.included() ? tr("common.mainStatistics", "Ana istatistikte")
                        : auditReason(value.auditReason());
            };
        }
    }

    private final class IntervalModel
            extends RowsModel<HicazAugmentedIntervalMeasurement> {
        private final String[] keys = {"result.hicaz.column.number", "result.hicaz.column.phrase",
                "result.hicaz.column.direction", "result.hicaz.column.time",
                "result.hicaz.column.secondDegree", "result.hicaz.column.thirdDegree",
                "result.hicaz.column.observedIntervalCent",
                "result.hicaz.column.observedIntervalHc",
                "result.hicaz.column.intervalDeviationCent",
                "result.hicaz.column.intervalDeviationHc",
                "result.hicaz.column.confidence", "result.hicaz.column.status"};
        private final String[] fallbacks = {"#", "Cümle", "Yön", "Zaman", "2. derece C/Hc",
                "3. derece C/Hc", "Gözlenen aralık C", "Gözlenen aralık Hc",
                "Kuramdan fark C", "Kuramdan fark Hc", "Güven", "Durum / denetim notu"};
        @Override public int getColumnCount() { return keys.length; }
        @Override public String getColumnName(int column) { return tr(keys[column], fallbacks[column]); }
        @Override public Object getValueAt(int row, int column) {
            HicazAugmentedIntervalMeasurement value = row(row);
            return switch (column) {
                case 0 -> value.index();
                case 1 -> value.phraseIndex();
                case 2 -> directionLabel(value.direction());
                case 3 -> range(value.startSeconds(), value.endSeconds(), 2);
                case 4 -> pair(value.secondDegree().observedRelativeCent(),
                        value.secondDegree().observedRelativeHc(), 2, 3);
                case 5 -> pair(value.thirdDegree().observedRelativeCent(),
                        value.thirdDegree().observedRelativeHc(), 2, 3);
                case 6 -> number(value.observedIntervalCent(), 2);
                case 7 -> number(value.observedIntervalHc(), 3);
                case 8 -> signed(value.deviationCent(), 2);
                case 9 -> signed(value.deviationHc(), 3);
                case 10 -> i18n.formatPercent(value.confidence(), 0);
                default -> value.included() ? tr("common.mainStatistics", "Ana istatistikte")
                        : auditReason(value.auditReason());
            };
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

    private String seconds(double value, int digits) {
        return number(value, digits) + " " + tr("common.seconds", "sn");
    }

    private String pair(double cent, double hc, int centDigits, int hcDigits) {
        return signed(cent, centDigits) + " / " + signed(hc, hcDigits);
    }

    private String directionLabel(HicazPhraseDirection direction) {
        return direction == HicazPhraseDirection.ASCENDING
                ? tr("direction.ascending", "Çıkıcı")
                : tr("direction.descending", "İnici");
    }

    private String degreeLabel(HicazTetrachordDegree degree) {
        return tr("result.hicaz.degree.label", degree.number() + ". derece", degree.number());
    }

    private String intonationLabel(double deviationCent) {
        if (Math.abs(deviationCent) < .01) {
            return tr("intonation.theoreticalCenter", "Kuramsal merkez");
        }
        return deviationCent < 0 ? tr("intonation.flat", "Pest")
                : tr("intonation.sharp", "Dik");
    }

    private String auditReason(String reason) {
        if (reason == null || reason.isBlank()) return tr("common.auditNote", "Denetim notu");
        if (reason.startsWith("F0 güveni")) {
            return tr("result.hicaz.audit.lowConfidence", reason);
        }
        if (reason.startsWith("Temas süresi")) {
            return tr("result.hicaz.audit.shortContact", reason);
        }
        if (reason.startsWith("Düşük güvenli doğrudan")) {
            return tr("result.hicaz.audit.lowConfidenceInterval", reason);
        }
        if (reason.startsWith("Düşük güvenli derece")) {
            return tr("result.hicaz.audit.lowConfidencePhrase", reason);
        }
        // Analysis reasons are internal data, not user-interface copy.  Never
        // leak an unrecognised Turkish reason into a differently localised UI.
        return tr("common.auditNote", "Denetim notu");
    }

    String diagnosticTabTitle(int index) {
        return tabs.getTitleAt(index);
    }

    String diagnosticColumnName(int tableIndex, int column) {
        return switch (tableIndex) {
            case 0 -> phraseModel.getColumnName(column);
            case 1 -> degreeEventModel.getColumnName(column);
            case 2 -> intervalModel.getColumnName(column);
            default -> throw new IllegalArgumentException("Unknown Hicaz table index: " + tableIndex);
        };
    }

    String diagnosticSummaryHeading(HicazPhraseDirection direction,
                                    HicazTetrachordDegree degree) {
        return degree == null ? intervalHeadings.get(direction).getText()
                : degreeHeadings.get(direction).get(degree).getText();
    }

    String diagnosticFormattedNumber(double value) {
        return number(value, 2);
    }
}
