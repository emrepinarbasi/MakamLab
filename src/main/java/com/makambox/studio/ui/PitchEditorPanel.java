package com.makambox.studio.ui;

import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.AnalysisReport;
import com.makambox.studio.model.EventType;
import com.makambox.studio.model.GlissandoEvent;
import com.makambox.studio.model.GroupStatistics;
import com.makambox.studio.model.MelodicDirection;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchOccurrence;
import com.makambox.studio.theory.TheoryCatalog;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Path2D;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.DoubleConsumer;

/** Note-object view for auditing and comparing target-pitch occurrences. */
public final class PitchEditorPanel extends JPanel {
    private static final double C0_HZ = 16.351597831287414;
    private static final double OCTAVE_CENT = 1200.0;
    private static final double DYNAMIC_LANE_MARGIN_CENT = 45.0;
    /**
     * Showing the complete two-octave theory ruler in the initial view made the
     * labels collide on normal laptop-height windows.  The complete ruler is
     * still available through "Tümünü göster"; the opening view deliberately
     * uses a readable excerpt centred on the selected pitch.
     */
    private static final int INITIAL_VISIBLE_PITCH_LANES = 23;
    /** Minimum height that keeps Turkish pitch names and 53-Hc lanes legible. */
    private static final int MIN_READABLE_LANE_HEIGHT = 18;
    private static final double INITIAL_DETAIL_SECONDS = 30.0;

    private enum DirectionBasis {
        ENTRY("Perdeye geliş"), PRIMARY("Net yerel yön"), EXIT("Perdeden ayrılış");
        private final String label;
        DirectionBasis(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private enum EventFilter {
        ALL("Tüm temaslar"), MEASURED("Ölçülebilir basışlar"), HOLDS("Uzatma ve kalışlar"), SHORT("Kısa temaslar");
        private final String label;
        EventFilter(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private enum PitchDisplayMode {
        ALL("Tüm kanonik olaylar"), STABLE("Kararlı kanonik olaylar");
        private final String label;
        PitchDisplayMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private enum EvidenceLayer {
        /**
         * A presentation-only view: stable note objects and their measured F0
         * contour stay visible while audit marks are suppressed.  The note
         * identities, ruler, cent/Hc calculations and exported reports still
         * come from the unchanged 53-comma academic analysis.
         */
        CLEAN("Sade (Melodyne tipi)"),
        ACADEMIC("Akademik 53-Hc"),
        RAW("Ham F0 denetimi"),
        BOTH("Tüm katmanlar");
        private final String label;
        EvidenceLayer(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private enum TimeScale {
        FULL("Tüm kayıt", Double.POSITIVE_INFINITY), SIXTY("60 sn", 60.0), THIRTY("30 sn", 30.0),
        TEN("10 sn", 10.0), FIVE("5 sn", 5.0), TWO("2 sn", 2.0), ONE("1 sn", 1.0),
        CUSTOM("Özel", Double.NaN);
        private final String label;
        private final double seconds;
        TimeScale(String label, double seconds) { this.label = label; this.seconds = seconds; }
        @Override public String toString() { return label; }
    }

    private final PitchCanvas canvas = new PitchCanvas();
    private final JScrollBar pitchScroll = new JScrollBar(JScrollBar.VERTICAL);
    private final JScrollBar timeScroll = new JScrollBar(JScrollBar.HORIZONTAL);
    private final Inspector inspector = new Inspector();
    private final ComparisonView comparison = new ComparisonView();
    private final JComboBox<DirectionBasis> basisBox = new JComboBox<>(DirectionBasis.values());
    private final JComboBox<EventFilter> eventFilterBox = new JComboBox<>(EventFilter.values());
    private final JComboBox<EvidenceLayer> evidenceLayerBox = new JComboBox<>(EvidenceLayer.values());
    private final JComboBox<PitchDisplayMode> pitchDisplayBox = new JComboBox<>(PitchDisplayMode.values());
    private final JCheckBox showCurve = new JCheckBox("Perde eğrisi", true);
    private final JCheckBox showGlissando = new JCheckBox("Hedef glisendosu", true);
    private final JCheckBox showGlobalGlides = new JCheckBox("Tüm görsel kaymalar", true);
    private final JCheckBox followPlayback = new JCheckBox("Çalmayı izle", true);
    private final JComboBox<TimeScale> timeScaleBox = new JComboBox<>(TimeScale.values());
    private final JLabel contextLabel = new JLabel("Analiz sonucu bekleniyor");
    private final JLabel viewLabel = new JLabel("Görünüm: tüm kayıt");
    private final JLabel coverageLabel = new JLabel("Perde tespitleri analizden sonra burada sayılır");
    private final TheoryCatalog theory = TheoryCatalog.loadDefault();
    private JSplitPane editorSplit;
    private JSplitPane verticalSplit;
    private int inspectorDividerSize = 8;
    private boolean inspectorOpen;
    private boolean updatingTimeScroll;
    private boolean updatingTimeScale;
    private boolean sidebarPitchLayerVisible = true;
    private boolean sidebarEventLayerVisible = true;
    private int requestedVisibleLanes = 17;
    /** A whole-octave offset used only while drawing; analysis data is never rewritten. */
    private double displayOctaveShiftCent;
    private double stableRegisterAnchorCent = Double.NaN;
    private int outOfRulerFrameCount;

    private AnalysisReport report;
    private PitchOccurrence selected;
    private List<PitchLane> pitchLanes = List.of();
    private List<PitchEvent> pitchEvents = List.of();
    private List<PitchBlob> allPitchBlobs = List.of();
    private List<PitchBlob> rawPitchBlobs = List.of();
    private List<VisualGlide> globalVisualGlides = List.of();
    private DoubleConsumer seekListener;
    private DoubleConsumer tonicPickListener;
    private BiConsumer<Double, Double> playRangeListener;

    public PitchEditorPanel() {
        super(new BorderLayout());
        setBackground(UiTheme.APP_BACKGROUND);
        add(buildToolbar(), BorderLayout.NORTH);
        JPanel editorArea = new JPanel(new BorderLayout());
        editorArea.setBackground(UiTheme.CANVAS);
        editorArea.add(canvas, BorderLayout.CENTER);
        pitchScroll.setUnitIncrement(1);
        pitchScroll.setBlockIncrement(8);
        pitchScroll.addAdjustmentListener(event -> canvas.repaint());
        editorArea.add(pitchScroll, BorderLayout.EAST);
        timeScroll.setUnitIncrement(500);
        timeScroll.setBlockIncrement(5_000);
        timeScroll.addAdjustmentListener(event -> {
            if (!updatingTimeScroll && report != null) canvas.scrollTo(timeScroll.getValue() / 1000.0);
        });
        editorArea.add(timeScroll, BorderLayout.SOUTH);
        inspector.setMinimumSize(new Dimension(0, 0));
        editorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inspector, editorArea);
        editorSplit.setBorder(BorderFactory.createEmptyBorder());
        editorSplit.setDividerLocation(0);
        editorSplit.setResizeWeight(0.0);
        editorSplit.setContinuousLayout(true);
        inspectorDividerSize = editorSplit.getDividerSize();
        closeInspector();
        comparison.setMinimumSize(new Dimension(400, 105));
        verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorSplit, comparison);
        verticalSplit.setBorder(BorderFactory.createEmptyBorder());
        verticalSplit.setContinuousLayout(true);
        verticalSplit.setResizeWeight(1.0);
        verticalSplit.setDividerLocation(0.76);
        verticalSplit.setToolTipText("Melodi ve karşılaştırma alanlarının yüksekliğini değiştirmek için sürükleyin");
        add(verticalSplit, BorderLayout.CENTER);
    }

    public void setSeekListener(DoubleConsumer listener) { seekListener = listener; }
    public void setTonicPickListener(DoubleConsumer listener) { tonicPickListener = listener; }
    public void setTonicPickMode(boolean enabled) { canvas.setTonicPickMode(enabled); }
    public void setPlayRangeListener(BiConsumer<Double, Double> listener) { playRangeListener = listener; }

    public void selectOccurrence(int oneBasedIndex) {
        if (report == null) return;
        report.occurrences().stream().filter(value -> value.index() == oneBasedIndex)
                .findFirst().ifPresent(value -> select(value, false));
    }

    /** Shows a precise excerpt of the recording; useful for reviewing a selected occurrence in detail. */
    public void setTimeWindow(double startSeconds, double endSeconds) {
        canvas.setViewRange(startSeconds, endSeconds);
    }

    public void setData(AnalysisReport value) {
        setData(value, List.of());
    }

    /**
     * Uses the same mutually-exclusive theory-note events as the pitch-event
     * table. This is deliberately separate from the user-adjustable target
     * window: widening a Segâh analysis window must not relabel a Dügâh event
     * as Segâh on the academic ruler.
     */
    public void setData(AnalysisReport value, List<PitchEvent> events) {
        report = value;
        selected = null;
        pitchEvents = events == null ? List.of() : List.copyOf(events);
        allPitchBlobs = value == null ? List.of()
                : pitchEvents.isEmpty() ? buildAllPitchBlobs(value.frames())
                : buildAllPitchBlobs(pitchEvents, value.frames());
        rawPitchBlobs = value == null ? List.of() : buildRawPitchBlobs(value.frames());
        globalVisualGlides = value == null ? List.of() : buildGlobalVisualGlides(rawPitchBlobs);
        DisplayRegister register = chooseDisplayRegister(value, allPitchBlobs);
        displayOctaveShiftCent = register.octaveShiftCent();
        stableRegisterAnchorCent = register.anchorCent();
        pitchLanes = buildPitchLanes(value);
        outOfRulerFrameCount = countOutOfRulerFrames(value);
        requestedVisibleLanes = initialVisiblePitchLanes();
        configurePitchScroll();
        canvas.openInitialView();
        inspector.showOccurrence(null);
        closeInspector();
        comparison.repaint();
        if (value == null) contextLabel.setText("Analiz sonucu bekleniyor");
        else contextLabel.setText(String.format(Locale.US, "%s • %s • teorik %.2f Hz • 53-Hc perde sistemi%s",
                value.config().makamName(), value.config().targetNote(),
                PitchTracker.centToHertz(value.config().expectedAbsoluteCent()), normalizationSuffix()));
        updateCoverageLabel();
        repaint();
    }

    public void setDataPreservingView(AnalysisReport value) {
        setDataPreservingView(value, List.of());
    }

    public void setDataPreservingView(AnalysisReport value, List<PitchEvent> events) {
        double previousStart = canvas.viewStart;
        double previousEnd = canvas.viewEnd;
        boolean hadDetailedView = report != null
                && previousEnd - previousStart < report.audio().durationSeconds() - 0.01;
        setData(value, events);
        if (hadDetailedView && value != null) canvas.setViewRange(previousStart, previousEnd);
    }

    public void setTheoryPreview(String makamName, String targetName, double tonicHz, double targetRelativeCent) {
        report = null;
        selected = null;
        pitchEvents = List.of();
        allPitchBlobs = List.of();
        rawPitchBlobs = List.of();
        globalVisualGlides = List.of();
        displayOctaveShiftCent = 0.0;
        stableRegisterAnchorCent = Double.NaN;
        outOfRulerFrameCount = 0;
        pitchLanes = buildPitchLanes(makamName, targetName, tonicHz, targetRelativeCent);
        requestedVisibleLanes = initialVisiblePitchLanes();
        configurePitchScroll();
        canvas.resetView();
        inspector.showOccurrence(null);
        closeInspector();
        comparison.repaint();
        double targetHz = tonicHz * Math.pow(2.0, targetRelativeCent / 1200.0);
        contextLabel.setText(String.format(Locale.US, "%s • %s • teorik %.2f Hz • 53-Hc perde sistemi",
                makamName, targetName, targetHz));
        updateCoverageLabel();
        repaint();
    }

    public void setPlaybackPosition(double seconds) { canvas.setPlaybackPosition(seconds, false); }
    public void setPlaybackPosition(double seconds, boolean playing) { canvas.setPlaybackPosition(seconds, playing); }
    void setSidebarLayerVisibility(boolean pitchVisible, boolean eventsVisible) {
        sidebarPitchLayerVisible = pitchVisible;
        sidebarEventLayerVisible = eventsVisible;
        canvas.repaint();
        comparison.repaint();
    }
    public void zoomTime(double factor) { canvas.zoom(factor, canvas.getWidth() / 2); }
    public void zoomPitch(double factor) {
        int current = Math.max(1, pitchScroll.getVisibleAmount());
        int next = Math.max(3, Math.min(Math.max(3, pitchLanes.size()), (int) Math.round(current * factor)));
        zoomPitch(next - current);
    }
    public void resetView() {
        // "Tümünü göster" resets the time axis, but never squeezes the whole
        // two-octave 53-Hc ruler into unreadable 5-8 px rows.  The remaining
        // lanes stay available through the vertical scrollbar.
        requestedVisibleLanes = initialVisiblePitchLanes();
        configurePitchScroll();
        canvas.resetView();
    }

    void focusPitchLaneForDiagnostic(String displayName, int visibleLanes) {
        requestedVisibleLanes = Math.max(3, Math.min(visibleLanes, Math.max(3, pitchLanes.size())));
        configurePitchScroll();
        for (int i = 0; i < pitchLanes.size(); i++) {
            if (theoryKey(pitchLanes.get(i).name()).equals(theoryKey(displayName))) {
                pitchScroll.setValue(Math.max(0,
                        Math.min(pitchLanes.size() - pitchScroll.getVisibleAmount(),
                                i - pitchScroll.getVisibleAmount() / 2)));
                break;
            }
        }
        canvas.repaint();
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(UiTheme.SURFACE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)));

        JPanel headingRow = new JPanel(new BorderLayout(12, 0));
        headingRow.setOpaque(false);
        JLabel title = new JLabel("Akademik Perde Editörü");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setForeground(UiTheme.INK);
        headingRow.add(title, BorderLayout.WEST);
        contextLabel.setForeground(UiTheme.MUTED);
        contextLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        headingRow.add(contextLabel, BorderLayout.CENTER);
        headingRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, headingRow.getPreferredSize().height));
        bar.add(headingRow);
        bar.add(Box.createVerticalStrut(5));

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        filterRow.setOpaque(false);
        filterRow.add(new JLabel("Yön ölçütü:"));
        filterRow.add(basisBox);
        filterRow.add(eventFilterBox);
        filterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, filterRow.getPreferredSize().height));
        bar.add(filterRow);
        bar.add(Box.createVerticalStrut(4));

        JPanel evidenceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        evidenceRow.setOpaque(false);
        evidenceRow.add(new JLabel("Görünüm modu:"));
        evidenceRow.add(evidenceLayerBox);
        evidenceRow.add(new JLabel("Akademik olaylar:"));
        evidenceRow.add(pitchDisplayBox);
        evidenceRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, evidenceRow.getPreferredSize().height));
        bar.add(evidenceRow);
        bar.add(Box.createVerticalStrut(4));

        JPanel layerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        layerRow.setOpaque(false);
        layerRow.add(new JLabel("Görsel katmanlar:"));
        for (JCheckBox box : List.of(showCurve, showGlissando, showGlobalGlides)) {
            box.setOpaque(false);
            box.setForeground(UiTheme.INK);
            layerRow.add(box);
        }
        layerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, layerRow.getPreferredSize().height));
        bar.add(layerRow);
        bar.add(Box.createVerticalStrut(5));

        JPanel zoomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        zoomRow.setOpaque(false);
        zoomRow.add(new JLabel("Yatay:"));
        JButton zoomOut = new AutoFitButton("−");
        zoomOut.setToolTipText("Zaman görünümünü uzaklaştır");
        zoomOut.addActionListener(event -> canvas.zoom(1.35, canvas.getWidth() / 2));
        JButton zoomIn = new AutoFitButton("+");
        zoomIn.setToolTipText("Zaman görünümünü yakınlaştır");
        zoomIn.addActionListener(event -> canvas.zoom(0.74, canvas.getWidth() / 2));
        zoomRow.add(zoomOut);
        zoomRow.add(zoomIn);
        zoomRow.add(timeScaleBox);
        zoomRow.add(Box.createHorizontalStrut(10));
        zoomRow.add(new JLabel("Dikey:"));
        JButton pitchOut = new AutoFitButton("−");
        pitchOut.setToolTipText("Daha fazla perde satırı göster");
        pitchOut.addActionListener(event -> zoomPitch(+2));
        JButton pitchIn = new AutoFitButton("+");
        pitchIn.setToolTipText("Perde satırlarını büyüt");
        pitchIn.addActionListener(event -> zoomPitch(-2));
        zoomRow.add(pitchOut);
        zoomRow.add(pitchIn);
        JButton fit = new AutoFitButton("Tümünü göster");
        fit.addActionListener(event -> resetView());
        zoomRow.add(fit);
        followPlayback.setOpaque(false);
        followPlayback.setForeground(UiTheme.INK);
        followPlayback.setToolTipText("Çalma başlığı sağ sınıra geldiğinde zaman görünümünü otomatik ilerlet");
        zoomRow.add(followPlayback);
        viewLabel.setForeground(UiTheme.MUTED);
        zoomRow.add(Box.createHorizontalStrut(10));
        zoomRow.add(viewLabel);
        zoomRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, zoomRow.getPreferredSize().height));
        bar.add(zoomRow);
        bar.add(Box.createVerticalStrut(4));
        coverageLabel.setForeground(UiTheme.MUTED);
        coverageLabel.setFont(coverageLabel.getFont().deriveFont(11f));
        coverageLabel.setAlignmentX(LEFT_ALIGNMENT);
        bar.add(coverageLabel);

        basisBox.addActionListener(event -> { canvas.repaint(); comparison.repaint(); inspector.showOccurrence(selected); });
        eventFilterBox.addActionListener(event -> canvas.repaint());
        evidenceLayerBox.setToolTipText("Yalnız görünümü değiştirir; 53-Hc analiz ve rapor değerlerini değiştirmez");
        evidenceLayerBox.addActionListener(event -> {
            updateLayerControlState();
            updateCoverageLabel();
            canvas.repaint();
        });
        pitchDisplayBox.addActionListener(event -> { updateCoverageLabel(); canvas.repaint(); });
        showCurve.addActionListener(event -> canvas.repaint());
        showGlissando.addActionListener(event -> canvas.repaint());
        showGlobalGlides.addActionListener(event -> canvas.repaint());
        timeScaleBox.addActionListener(event -> {
            if (updatingTimeScale) return;
            TimeScale scale = (TimeScale) timeScaleBox.getSelectedItem();
            if (scale != null && scale != TimeScale.CUSTOM) canvas.applyTimeScale(scale.seconds);
        });
        updateLayerControlState();
        return bar;
    }

    private void updateLayerControlState() {
        EvidenceLayer layer = evidenceLayer();
        pitchDisplayBox.setEnabled(layer == EvidenceLayer.ACADEMIC || layer == EvidenceLayer.BOTH);
        showCurve.setEnabled(layer == EvidenceLayer.RAW || layer == EvidenceLayer.BOTH);
        showGlissando.setEnabled(layer != EvidenceLayer.RAW);
        // Global visual glides are derived from raw detector candidates and
        // are not academic report events.  Keep them out of the clean/academic
        // modes so a pale audit candidate cannot be mistaken for a reported
        // glissando.
        showGlobalGlides.setEnabled(layer == EvidenceLayer.RAW || layer == EvidenceLayer.BOTH);
    }

    private List<PitchLane> buildPitchLanes(AnalysisReport value) {
        if (value == null) return List.of();
        return buildPitchLanes(value.config().makamName(), value.config().targetNote(),
                value.config().tonicHz(), value.config().targetRelativeCent(), value.frames());
    }

    private List<PitchLane> buildPitchLanes(String makamName, String targetName,
                                            double tonicHz, double targetRelativeCent) {
        return buildPitchLanes(makamName, targetName, tonicHz, targetRelativeCent, List.of());
    }

    private List<PitchLane> buildPitchLanes(String makamName, String targetName,
                                            double tonicHz, double targetRelativeCent,
                                            List<PitchFrame> frames) {
        TheoryCatalog.Makam makam = theory.makams().stream()
                .filter(candidate -> theoryKey(candidate.name()).equals(theoryKey(makamName))
                        || theoryKey(candidate.toString()).equals(theoryKey(makamName)))
                .findFirst().orElse(null);
        double tonicAbsoluteCent = hertzToAbsoluteCent(tonicHz);
        if (makam == null) return List.of(new PitchLane(theoryKey(targetName), targetName,
                targetRelativeCent, tonicAbsoluteCent + targetRelativeCent, true, false));
        List<TheoryCatalog.Note> standardNotes = theory.notesAround(makam);
        double standardMinimum = standardNotes.stream()
                .mapToDouble(note -> tonicAbsoluteCent + theory.relativeCent(makam, note)).min()
                .orElse(tonicAbsoluteCent - OCTAVE_CENT);
        double standardMaximum = standardNotes.stream()
                .mapToDouble(note -> tonicAbsoluteCent + theory.relativeCent(makam, note)).max()
                .orElse(tonicAbsoluteCent + OCTAVE_CENT);
        double measuredMinimum = frames.stream().filter(PitchEditorPanel::reliableFrame)
                .mapToDouble(frame -> displayCent(frame.absoluteCent())).min().orElse(standardMinimum);
        double measuredMaximum = frames.stream().filter(PitchEditorPanel::reliableFrame)
                .mapToDouble(frame -> displayCent(frame.absoluteCent())).max().orElse(standardMaximum);
        double wantedMinimum = Math.min(standardMinimum, measuredMinimum - DYNAMIC_LANE_MARGIN_CENT);
        double wantedMaximum = Math.max(standardMaximum, measuredMaximum + DYNAMIC_LANE_MARGIN_CENT);
        java.util.Set<String> standardKeys = standardNotes.stream()
                .map(TheoryCatalog.Note::key).collect(java.util.stream.Collectors.toSet());
        List<PitchLane> lanes = new ArrayList<>();
        for (TheoryCatalog.Note note : theory.notes()) {
            double relative = theory.relativeCent(makam, note);
            double absolute = tonicAbsoluteCent + relative;
            if (!standardKeys.contains(note.key())
                    && (absolute < wantedMinimum || absolute > wantedMaximum)) continue;
            boolean target = Math.abs(absolute - (tonicAbsoluteCent + targetRelativeCent
                    + displayOctaveShiftCent)) < 0.5;
            boolean tonic = Math.abs(absolute - (tonicAbsoluteCent + displayOctaveShiftCent)) < 0.5;
            lanes.add(new PitchLane(note.key(), note.toString(), relative,
                    absolute, target, tonic));
        }
        lanes.sort((left, right) -> Double.compare(right.absoluteCent(), left.absoluteCent()));
        return List.copyOf(lanes);
    }

    private static String theoryKey(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('ı', 'i');
    }

    /**
     * Chooses a display register without touching a single measured value.
     * A phrase-final hold wins; otherwise holds, stable blobs and finally the
     * robust median of reliable frames are tried in that order.  Only when the
     * resulting anchor lies below the Yegâh floor of the requested display
     * band, the smallest
     * positive whole-octave shift selected that puts it in the Yegâh–Nevâ
     * band.  Isolated sub-octave tracker errors therefore cannot move a whole
     * recording and reports/exports continue to contain the original Hz/C/Hc.
     */
    private DisplayRegister chooseDisplayRegister(AnalysisReport value, List<PitchBlob> blobs) {
        if (value == null) return new DisplayRegister(0.0, Double.NaN);
        TheoryCatalog.Makam makam = findMakam(value.config().makamName());
        if (makam == null) return new DisplayRegister(0.0, Double.NaN);
        double anchor = stableRegisterAnchor(blobs, value.frames());
        if (!Double.isFinite(anchor)) return new DisplayRegister(0.0, Double.NaN);
        double tonicCent = hertzToAbsoluteCent(value.config().tonicHz());
        double yegah = tonicCent + theory.relativeCent(makam, theory.note("yegah"));
        double neva = tonicCent + theory.relativeCent(makam, theory.note("neva"));
        double bandLow = Math.min(yegah, neva);
        double bandHigh = Math.max(yegah, neva);
        if (anchor >= bandLow - 0.5) return new DisplayRegister(0.0, anchor);
        int octaves = Math.max(1, (int) Math.ceil((bandLow - anchor - 1.0e-7) / OCTAVE_CENT));
        double shifted = anchor + octaves * OCTAVE_CENT;
        // Yegâh and Nevâ are exactly an octave apart in the bundled theory
        // data. Keep a defensive correction for a future non-octave catalog.
        while (shifted > bandHigh + 0.5 && octaves > 1
                && shifted - OCTAVE_CENT >= bandLow - 0.5) {
            octaves--;
            shifted -= OCTAVE_CENT;
        }
        return new DisplayRegister(octaves * OCTAVE_CENT, anchor);
    }

    private TheoryCatalog.Makam findMakam(String makamName) {
        return theory.makams().stream()
                .filter(candidate -> theoryKey(candidate.name()).equals(theoryKey(makamName))
                        || theoryKey(candidate.toString()).equals(theoryKey(makamName)))
                .findFirst().orElse(null);
    }

    private static double stableRegisterAnchor(List<PitchBlob> blobs, List<PitchFrame> frames) {
        List<WeightedCent> candidates = weightedBlobs(blobs, 3);
        if (candidates.isEmpty()) candidates = weightedBlobs(blobs, 2);
        if (candidates.isEmpty()) candidates = weightedBlobs(blobs, 1);
        if (candidates.isEmpty()) {
            candidates = frames.stream().filter(PitchEditorPanel::reliableFrame)
                    .map(frame -> new WeightedCent(frame.absoluteCent(), Math.max(0.05, frame.confidence())))
                    .toList();
        }
        return weightedMedian(candidates);
    }

    /** priority: 3 phrase-final hold; 2 hold; 1 any stable/long blob. */
    private static List<WeightedCent> weightedBlobs(List<PitchBlob> blobs, int priority) {
        List<WeightedCent> result = new ArrayList<>();
        for (PitchBlob blob : blobs) {
            if (!Double.isFinite(blob.observedCent()) || blob.confidence() < 0.35) continue;
            PitchOccurrence occurrence = blob.occurrence();
            boolean hold = occurrence != null && occurrence.eventType().holdLike();
            boolean phraseFinal = occurrence != null && occurrence.phraseFinal() && hold;
            boolean stable = blob.frameCount() >= 2 && blob.endSeconds() - blob.startSeconds() >= 0.18;
            if (priority == 3 && !phraseFinal) continue;
            if (priority == 2 && !hold) continue;
            if (priority == 1 && !stable) continue;
            double weight = Math.max(0.02, blob.endSeconds() - blob.startSeconds())
                    * Math.max(0.35, blob.confidence());
            result.add(new WeightedCent(blob.observedCent(), weight));
        }
        return result;
    }

    private static double weightedMedian(List<WeightedCent> source) {
        if (source.isEmpty()) return Double.NaN;
        List<WeightedCent> values = source.stream()
                .filter(value -> Double.isFinite(value.cent()) && value.weight() > 0.0)
                .sorted(java.util.Comparator.comparingDouble(WeightedCent::cent)).toList();
        if (values.isEmpty()) return Double.NaN;
        double total = values.stream().mapToDouble(WeightedCent::weight).sum();
        double cumulative = 0.0;
        for (WeightedCent value : values) {
            cumulative += value.weight();
            if (cumulative >= total / 2.0) return value.cent();
        }
        return values.get(values.size() - 1).cent();
    }

    private static boolean reliableFrame(PitchFrame frame) {
        return frame.voiced() && frame.confidence() >= 0.35 && Double.isFinite(frame.absoluteCent());
    }

    private double displayCent(double measuredAbsoluteCent) {
        return measuredAbsoluteCent + displayOctaveShiftCent;
    }

    private int countOutOfRulerFrames(AnalysisReport value) {
        if (value == null || pitchLanes.isEmpty()) return 0;
        double high = pitchLanes.get(0).absoluteCent();
        double low = pitchLanes.get(pitchLanes.size() - 1).absoluteCent();
        return (int) value.frames().stream().filter(PitchEditorPanel::reliableFrame)
                .mapToDouble(frame -> displayCent(frame.absoluteCent()))
                .filter(cent -> cent > high || cent < low).count();
    }

    private String normalizationSuffix() {
        if (Math.abs(displayOctaveShiftCent) < 0.5) return "";
        int octaves = (int) Math.round(displayOctaveShiftCent / OCTAVE_CENT);
        return String.format(Locale.US,
                " • görsel aktarım %+.0f C (%+d oktav; Hz/C/Hc değişmedi)",
                displayOctaveShiftCent, octaves);
    }

    double diagnosticDisplayOctaveShiftCent() { return displayOctaveShiftCent; }
    double diagnosticStableRegisterAnchorCent() { return stableRegisterAnchorCent; }
    List<String> diagnosticPitchLaneNames() { return pitchLanes.stream().map(PitchLane::name).toList(); }
    double diagnosticPitchLaneMinimumCent() {
        return pitchLanes.isEmpty() ? Double.NaN : pitchLanes.get(pitchLanes.size() - 1).absoluteCent();
    }
    double diagnosticPitchLaneMaximumCent() {
        return pitchLanes.isEmpty() ? Double.NaN : pitchLanes.get(0).absoluteCent();
    }
    double diagnosticLanePositionForMeasuredCent(double measuredCent) {
        return canvas.lanePosition(displayCent(measuredCent));
    }
    int diagnosticOutOfRulerFrameCount() { return outOfRulerFrameCount; }
    String diagnosticNormalizationStatus() { return normalizationSuffix(); }
    boolean diagnosticSidebarPitchLayerVisible() { return sidebarPitchLayerVisible; }
    boolean diagnosticSidebarEventLayerVisible() { return sidebarEventLayerVisible; }
    int diagnosticVisiblePitchLaneCount() { return canvas.visibleLaneCount(); }
    double diagnosticPitchLaneHeight() { return canvas.laneHeight(); }

    private static double hertzToAbsoluteCent(double hertz) {
        return OCTAVE_CENT * Math.log(hertz / C0_HZ) / Math.log(2.0);
    }

    private void configurePitchScroll() {
        int visible = effectiveVisiblePitchLanes();
        pitchScroll.setValues(0, visible, 0, Math.max(visible, pitchLanes.size()));
        if (!pitchLanes.isEmpty()) {
            int target = 0;
            for (int i = 0; i < pitchLanes.size(); i++) if (pitchLanes.get(i).target()) { target = i; break; }
            pitchScroll.setValue(Math.max(0, Math.min(pitchLanes.size() - visible, target - visible / 2)));
        }
        pitchScroll.setEnabled(pitchLanes.size() > visible);
    }

    /**
     * Recomputes the scroll extent after a window/split-pane resize without
     * jumping to a different register.  The user's requested zoom level is
     * retained, so enlarging the editor restores more lanes automatically.
     */
    private void configurePitchScrollPreservingCenter() {
        if (pitchLanes.isEmpty()) return;
        int oldVisible = Math.max(1, pitchScroll.getVisibleAmount());
        int center = pitchScroll.getValue() + oldVisible / 2;
        int visible = effectiveVisiblePitchLanes();
        int value = Math.max(0, Math.min(pitchLanes.size() - visible, center - visible / 2));
        pitchScroll.setValues(value, visible, 0, Math.max(visible, pitchLanes.size()));
        pitchScroll.setEnabled(pitchLanes.size() > visible);
        canvas.repaint();
    }

    private int effectiveVisiblePitchLanes() {
        int available = Math.max(1, pitchLanes.size());
        int requested = Math.max(1, Math.min(requestedVisibleLanes, available));
        int canvasHeight = canvas.getHeight();
        if (canvasHeight <= canvas.top() + 38) return requested;
        int readable = Math.max(3, (canvas.bottom() - canvas.top()) / MIN_READABLE_LANE_HEIGHT);
        return Math.max(1, Math.min(requested, readable));
    }

    private int initialVisiblePitchLanes() {
        return Math.max(3, Math.min(INITIAL_VISIBLE_PITCH_LANES, pitchLanes.size()));
    }

    private static List<PitchBlob> buildAllPitchBlobs(List<PitchFrame> frames) {
        List<PitchBlob> blobs = new ArrayList<>();
        List<PitchFrame> segment = new ArrayList<>();
        double previousTime = Double.NaN;
        double runningCent = Double.NaN;
        for (PitchFrame frame : frames) {
            boolean usable = frame.voiced() && frame.confidence() >= 0.35;
            boolean breakSegment = !usable;
            if (!breakSegment && !segment.isEmpty()) {
                breakSegment = frame.timeSeconds() - previousTime > 0.075
                        || Math.abs(frame.absoluteCent() - runningCent) > 75.0;
            }
            if (breakSegment) {
                finishBlob(blobs, segment);
                segment.clear();
                runningCent = Double.NaN;
                if (!usable) { previousTime = Double.NaN; continue; }
            }
            segment.add(frame);
            runningCent = Double.isFinite(runningCent)
                    ? runningCent * 0.82 + frame.absoluteCent() * 0.18 : frame.absoluteCent();
            previousTime = frame.timeSeconds();
        }
        finishBlob(blobs, segment);
        return List.copyOf(blobs);
    }

    private static void finishBlob(List<PitchBlob> blobs, List<PitchFrame> frames) {
        if (frames.isEmpty()) return;
        double start = frames.get(0).timeSeconds();
        double frameStep = frames.size() > 1 ? frames.get(1).timeSeconds() - frames.get(0).timeSeconds() : 0.02;
        double end = frames.get(frames.size() - 1).timeSeconds() + Math.max(0.01, frameStep);
        // Preserve even one 20 ms frame in "Tüm tespitler"; it is rendered as
        // a pale transient rather than silently discarded as a stable note.
        if (end - start < 0.015) return;
        double[] cents = frames.stream().mapToDouble(PitchFrame::absoluteCent).sorted().toArray();
        double median = cents[cents.length / 2];
        double confidence = frames.stream().mapToDouble(PitchFrame::confidence).average().orElse(0.0);
        blobs.add(new PitchBlob(start, end, median, median, confidence, frames.size(), "",
                hasAudibleVibrato(frames), null, List.copyOf(frames)));
    }

    /**
     * Builds an audit-only contour from every detector candidate above the
     * visual threshold.  Reliable hertz/cent remain untouched, so none of
     * these weaker candidates can enter the academic report or statistics.
     */
    private static List<PitchBlob> buildRawPitchBlobs(List<PitchFrame> frames) {
        List<PitchBlob> blobs = new ArrayList<>();
        List<PitchFrame> segment = new ArrayList<>();
        double previousTime = Double.NaN;
        double previousCent = Double.NaN;
        for (PitchFrame source : frames) {
            boolean usable = source.candidateVoiced()
                    && source.confidence() >= PitchTracker.VISUAL_CANDIDATE_CONFIDENCE;
            PitchFrame frame = usable
                    ? new PitchFrame(source.timeSeconds(), source.candidateHertz(),
                    source.candidateAbsoluteCent(), source.confidence(), source.rms(),
                    source.candidateHertz(), source.candidateAbsoluteCent()) : source;
            boolean breakSegment = !usable;
            if (!breakSegment && !segment.isEmpty()) {
                breakSegment = frame.timeSeconds() - previousTime > 0.075
                        || Math.abs(frame.absoluteCent() - previousCent) > 90.0;
            }
            if (breakSegment) {
                finishBlob(blobs, segment);
                segment.clear();
                if (!usable) {
                    previousTime = Double.NaN;
                    previousCent = Double.NaN;
                    continue;
                }
            }
            segment.add(frame);
            previousTime = frame.timeSeconds();
            previousCent = frame.absoluteCent();
        }
        finishBlob(blobs, segment);
        return List.copyOf(blobs);
    }

    /** Visual-only glides across every pitch; target-note report data is unchanged. */
    private static List<VisualGlide> buildGlobalVisualGlides(List<PitchBlob> rawBlobs) {
        List<VisualGlide> result = new ArrayList<>();
        for (PitchBlob blob : rawBlobs) {
            List<PitchFrame> frames = blob.frames();
            double duration = blob.endSeconds() - blob.startSeconds();
            if (frames.size() < 4 || duration < 0.06 || duration > 2.0) continue;
            double start = frames.get(0).absoluteCent();
            double end = frames.get(frames.size() - 1).absoluteCent();
            double span = end - start;
            if (Math.abs(span) < 35.0) continue;
            int direction = span > 0.0 ? 1 : -1;
            int moving = 0;
            int matching = 0;
            boolean implausibleStep = false;
            for (int index = 1; index < frames.size(); index++) {
                double delta = frames.get(index).absoluteCent() - frames.get(index - 1).absoluteCent();
                if (Math.abs(delta) > 65.0) { implausibleStep = true; break; }
                if (Math.abs(delta) < 0.8) continue;
                moving++;
                if (Math.signum(delta) == direction) matching++;
            }
            if (implausibleStep || moving < 3 || matching / (double) moving < 0.70) continue;
            double confidence = frames.stream().mapToDouble(PitchFrame::confidence).average().orElse(0.0);
            result.add(new VisualGlide(blob.startSeconds(), blob.endSeconds(), start, end, confidence));
        }
        return List.copyOf(result);
    }

    private static List<PitchBlob> buildAllPitchBlobs(List<PitchEvent> events, List<PitchFrame> frames) {
        if (events.isEmpty()) return List.of();
        double step = frames.size() > 1
                ? Math.max(0.001, frames.get(1).timeSeconds() - frames.get(0).timeSeconds()) : 0.02;
        // PitchOccurrence stores a half-open interval whose end is the last
        // assigned frame time plus one frame step.  Testing a following frame
        // directly against that floating-point end occasionally admitted it
        // into the preceding event (for example 0.02 < 0.020000000000000004).
        // That made adjacent one-frame/two-frame events look like two-frame/
        // one-frame events in the editor.  Compare against the occurrence's
        // reconstructed last frame time instead, with a tolerance much smaller
        // than half a frame so neighbouring grid positions can never overlap.
        double gridTolerance = Math.max(1.0e-9, step * 1.0e-4);
        List<PitchBlob> blobs = new ArrayList<>(events.size());
        int frameIndex = 0;
        for (PitchEvent event : events) {
            PitchOccurrence occurrence = event.occurrence();
            double firstFrameTime = occurrence.startSeconds();
            double lastFrameTime = occurrence.endSeconds() - step;
            while (frameIndex < frames.size()
                    && frames.get(frameIndex).timeSeconds() < firstFrameTime - gridTolerance) {
                frameIndex++;
            }
            int count = 0;
            int cursor = frameIndex;
            List<PitchFrame> eventFrames = new ArrayList<>();
            while (cursor < frames.size()) {
                PitchFrame frame = frames.get(cursor);
                if (frame.timeSeconds() > lastFrameTime + gridTolerance) break;
                if (frame.voiced() && frame.confidence() >= 0.35) {
                    count++;
                    eventFrames.add(frame);
                }
                cursor++;
            }
            frameIndex = cursor;
            blobs.add(new PitchBlob(occurrence.startSeconds(), occurrence.endSeconds(),
                    occurrence.observedCent(), occurrence.expectedCent(), occurrence.confidence(),
                    Math.max(1, count), event.noteKey(), hasAudibleVibrato(eventFrames), occurrence,
                    List.copyOf(eventFrames)));
        }
        return List.copyOf(blobs);
    }

    /**
     * Detects a clearly oscillating long tone rather than confusing a smooth
     * glissando or tracker jitter with vibrato. A linear drift is removed
     * first; the remaining contour must have both audible span and repeated
     * changes of direction in the normal vibrato-rate region.
     */
    static boolean hasAudibleVibrato(List<PitchFrame> source) {
        List<PitchFrame> frames = source.stream()
                .filter(frame -> frame.voiced() && frame.confidence() >= 0.35
                        && Double.isFinite(frame.absoluteCent()))
                .toList();
        if (frames.size() < 12) return false;
        double duration = frames.get(frames.size() - 1).timeSeconds() - frames.get(0).timeSeconds();
        if (duration < 0.45) return false;

        int size = frames.size();
        double first = frames.get(0).absoluteCent();
        double last = frames.get(size - 1).absoluteCent();
        double[] residual = new double[size];
        for (int index = 0; index < size; index++) {
            double ratio = index / (double) Math.max(1, size - 1);
            residual[index] = frames.get(index).absoluteCent() - (first + (last - first) * ratio);
        }
        double[] smooth = new double[size];
        for (int index = 0; index < size; index++) {
            int from = Math.max(0, index - 2), to = Math.min(size - 1, index + 2);
            double sum = 0.0;
            for (int cursor = from; cursor <= to; cursor++) sum += residual[cursor];
            smooth[index] = sum / (to - from + 1);
        }
        double[] sorted = smooth.clone();
        java.util.Arrays.sort(sorted);
        double low = sorted[(int) Math.floor((size - 1) * 0.10)];
        double high = sorted[(int) Math.ceil((size - 1) * 0.90)];
        double robustSpan = high - low;
        if (robustSpan < 14.0) return false;

        double swing = Math.max(3.0, robustSpan * 0.14);
        int direction = 0;
        int reversals = 0;
        double extreme = smooth[0];
        for (int index = 1; index < size; index++) {
            double value = smooth[index];
            if (direction == 0) {
                if (value - extreme >= swing) { direction = 1; extreme = value; }
                else if (extreme - value >= swing) { direction = -1; extreme = value; }
            } else if (direction > 0) {
                if (value > extreme) extreme = value;
                else if (extreme - value >= swing) { reversals++; direction = -1; extreme = value; }
            } else {
                if (value < extreme) extreme = value;
                else if (value - extreme >= swing) { reversals++; direction = 1; extreme = value; }
            }
        }
        double reversalsPerSecond = reversals / Math.max(0.001, duration);
        return reversals >= 3 && reversalsPerSecond >= 4.0 && reversalsPerSecond <= 24.0;
    }

    private boolean hasVibrato(double startSeconds, double endSeconds) {
        double duration = Math.max(0.001, endSeconds - startSeconds);
        for (PitchBlob blob : allPitchBlobs) {
            if (!blob.vibrato()) continue;
            double overlap = Math.min(endSeconds, blob.endSeconds()) - Math.max(startSeconds, blob.startSeconds());
            if (overlap >= Math.min(duration, blob.endSeconds() - blob.startSeconds()) * 0.60) return true;
        }
        return false;
    }

    private PitchDisplayMode pitchDisplayMode() {
        PitchDisplayMode mode = (PitchDisplayMode) pitchDisplayBox.getSelectedItem();
        return mode == null ? PitchDisplayMode.ALL : mode;
    }

    private EvidenceLayer evidenceLayer() {
        EvidenceLayer layer = (EvidenceLayer) evidenceLayerBox.getSelectedItem();
        return layer == null ? EvidenceLayer.CLEAN : layer;
    }

    private boolean cleanView() { return evidenceLayer() == EvidenceLayer.CLEAN; }
    private boolean showsRawEvidence() {
        return evidenceLayer() == EvidenceLayer.RAW || evidenceLayer() == EvidenceLayer.BOTH;
    }
    private boolean showsAcademicEvents() { return evidenceLayer() != EvidenceLayer.RAW; }

    private boolean displayBlob(PitchBlob blob) {
        if (cleanView()) {
            return blob.frameCount() >= 3 && blob.endSeconds() - blob.startSeconds() >= 0.05;
        }
        return pitchDisplayMode() == PitchDisplayMode.ALL || blob.frameCount() >= 2;
    }

    private void updateCoverageLabel() {
        if (report == null) {
            coverageLabel.setText("Perde tespitleri analizden sonra burada sayılır");
            return;
        }
        long voicedFrames = report.frames().stream().filter(PitchFrame::voiced).count();
        long candidateFrames = report.frames().stream().filter(PitchFrame::candidateVoiced).count();
        long weakFrames = Math.max(0, candidateFrames - voicedFrames);
        int transientFrames = allPitchBlobs.stream().filter(blob -> blob.frameCount() == 1)
                .mapToInt(PitchBlob::frameCount).sum();
        int stableFrames = allPitchBlobs.stream().filter(blob -> blob.frameCount() >= 2)
                .mapToInt(PitchBlob::frameCount).sum();
        long visiblePieces = allPitchBlobs.stream().filter(this::displayBlob).count();
        String detail = cleanView()
                ? String.format(trLocale(),
                "Sade görünümde %s kararlı nota nesnesi • perde kimlikleri ve hesaplar 53-Hc",
                formatCount(visiblePieces))
                : pitchDisplayMode() == PitchDisplayMode.ALL
                ? String.format(trLocale(), "%s parçanın tamamı gösteriliyor",
                formatCount(allPitchBlobs.size()))
                : String.format(trLocale(), "%s kararlı parça gösteriliyor • %s geçici tespit gizli",
                formatCount(visiblePieces), formatCount(transientFrames));
        String outside = outOfRulerFrameCount == 0 ? ""
                : String.format(trLocale(), " • %s cetvel-dışı çerçeve kenara yapıştırılmadan gizli",
                formatCount(outOfRulerFrameCount));
        coverageLabel.setText(String.format(trLocale(),
                "%s güvenilir F0 • %s düşük-güvenli ham aday • %s kararlı bloklarda • "
                        + "%s tek-çerçeveli kısa/geçici • %s%s%s",
                formatCount(voicedFrames), formatCount(weakFrames), formatCount(stableFrames),
                formatCount(transientFrames), detail, normalizationSuffix(), outside));
    }

    private static Locale trLocale() { return Locale.forLanguageTag("tr-TR"); }
    private static String formatCount(long value) { return String.format(trLocale(), "%,d", value); }

    private void zoomPitch(int laneDelta) {
        if (pitchLanes.isEmpty()) return;
        int currentVisible = Math.max(1, pitchScroll.getVisibleAmount());
        requestedVisibleLanes = Math.max(3, Math.min(pitchLanes.size(), currentVisible + laneDelta));
        configurePitchScrollPreservingCenter();
    }

    private boolean belongsToSelectedPitch(double absoluteCent) {
        if (report != null) return insideTargetWindow(report.config(), absoluteCent);
        if (pitchLanes.isEmpty()) return false;
        int target = -1;
        for (int i = 0; i < pitchLanes.size(); i++) {
            if (pitchLanes.get(i).target()) { target = i; break; }
        }
        if (target < 0) return false;
        double upper = target > 0 ? pitchLanes.get(target - 1).absoluteCent()
                : pitchLanes.get(target).absoluteCent() + 100.0;
        double lower = target + 1 < pitchLanes.size() ? pitchLanes.get(target + 1).absoluteCent()
                : pitchLanes.get(target).absoluteCent() - 100.0;
        return absoluteCent >= lower && absoluteCent <= upper;
    }

    private boolean belongsToSelectedPitch(PitchBlob blob) {
        if (blob.noteKey() != null && !blob.noteKey().isBlank()) {
            for (PitchLane lane : pitchLanes) {
                if (lane.target()) return lane.key().equals(blob.noteKey());
            }
            return false;
        }
        return belongsToSelectedPitch(blob.observedCent());
    }

    static boolean insideTargetWindow(AnalysisConfig config, double absoluteCent) {
        return config != null && Double.isFinite(absoluteCent)
                && absoluteCent >= config.targetWindowLowerAbsoluteCent()
                && absoluteCent <= config.targetWindowUpperAbsoluteCent();
    }

    private void selectTimeScale(TimeScale scale) {
        updatingTimeScale = true;
        try { timeScaleBox.setSelectedItem(scale); }
        finally { updatingTimeScale = false; }
    }

    private DirectionBasis basis() {
        DirectionBasis value = (DirectionBasis) basisBox.getSelectedItem();
        return value == null ? DirectionBasis.ENTRY : value;
    }

    private MelodicDirection directionOf(PitchOccurrence occurrence) {
        return switch (basis()) {
            case ENTRY -> occurrence.entryDirection();
            case PRIMARY -> occurrence.direction();
            case EXIT -> occurrence.exitDirection();
        };
    }

    private boolean visible(PitchOccurrence occurrence) {
        EventFilter filter = (EventFilter) eventFilterBox.getSelectedItem();
        if (filter == null || filter == EventFilter.ALL) return true;
        return switch (filter) {
            case MEASURED -> occurrence.eventType().measurable();
            case HOLDS -> occurrence.eventType().holdLike();
            case SHORT -> occurrence.eventType() == EventType.SHORT_CONTACT;
            default -> true;
        };
    }

    private Color directionColor(PitchOccurrence occurrence) {
        return switch (directionOf(occurrence)) {
            case ASCENDING -> UiTheme.TARGET;
            case DESCENDING -> UiTheme.DESCENDING;
            case LEVEL -> UiTheme.LEVEL;
        };
    }

    private void select(PitchOccurrence occurrence, boolean play) {
        selected = occurrence;
        inspector.showOccurrence(occurrence);
        if (occurrence == null) closeInspector(); else openInspector();
        canvas.repaint();
        if (occurrence == null) return;
        if (play && playRangeListener != null) playRangeListener.accept(occurrence.startSeconds(), occurrence.endSeconds());
        else if (seekListener != null) seekListener.accept(occurrence.startSeconds());
    }

    private void openInspector() {
        if (editorSplit == null) return;
        if (editorSplit.getLeftComponent() != inspector) editorSplit.setLeftComponent(inspector);
        inspector.setVisible(true);
        editorSplit.setDividerSize(Math.max(1, inspectorDividerSize));
        editorSplit.setDividerLocation(310);
        inspectorOpen = true;
        editorSplit.revalidate();
        editorSplit.repaint();
    }

    private void closeInspector() {
        if (editorSplit == null) return;
        inspectorOpen = false;
        inspector.setVisible(false);
        if (editorSplit.getLeftComponent() == inspector) editorSplit.setLeftComponent(null);
        editorSplit.setDividerSize(0);
        editorSplit.setDividerLocation(0);
        editorSplit.revalidate();
        editorSplit.repaint();
    }

    boolean diagnosticInspectorOpen() {
        return inspectorOpen && inspector.isVisible() && editorSplit.getLeftComponent() == inspector;
    }

    int diagnosticInspectorDividerLocation() { return editorSplit.getDividerLocation(); }

    int diagnosticInspectorDividerSize() { return editorSplit.getDividerSize(); }

    private final class PitchCanvas extends JPanel {
        private final List<Hit> hits = new ArrayList<>();
        private double viewStart;
        private double viewEnd = 1.0;
        private double playbackSeconds;
        private boolean playbackActive;
        private boolean tonicPickMode;
        private double verticalScrollRemainder;

        PitchCanvas() {
            setBackground(UiTheme.CANVAS);
            setBorder(BorderFactory.createEmptyBorder());
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent event) {
                    configurePitchScrollPreservingCenter();
                }
            });
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) {
                    if (tonicPickMode) {
                        pickTonicAt(event.getX(), event.getY());
                        return;
                    }
                    PitchOccurrence hit = null;
                    for (int i = hits.size() - 1; i >= 0; i--) {
                        if (hits.get(i).bounds().contains(event.getPoint())) { hit = hits.get(i).occurrence(); break; }
                    }
                    if (hit != null) select(hit, event.getClickCount() >= 2);
                    else if (report != null) {
                        select(null, false);
                        if (event.getX() >= left()) {
                            double time = xToTime(event.getX());
                            if (seekListener != null) seekListener.accept(time);
                        }
                    }
                }
            };
            addMouseListener(mouse);
            TrackpadNavigationSupport.install(this, gesture -> {
                double rotation = gesture.amount();
                switch (gesture.action()) {
                    case TIME_ZOOM -> zoom(Math.pow(1.16, rotation), gesture.x());
                    case TIME_PAN -> panTime(rotation * Math.max(0.05, (viewEnd - viewStart) * 0.035));
                    case PITCH_ZOOM -> PitchEditorPanel.this.zoomPitch(Math.pow(1.16, rotation));
                    case PITCH_PAN -> {
                        verticalScrollRemainder += rotation;
                        int lanes = (int) verticalScrollRemainder;
                        if (lanes != 0) {
                            pitchScroll.setValue(pitchScroll.getValue() + lanes);
                            verticalScrollRemainder -= lanes;
                        }
                    }
                }
            });
            TrackpadZoomSupport.install(this,
                    magnification -> zoom(Math.exp(-magnification * 1.8), getWidth() / 2));
        }

        void setTonicPickMode(boolean enabled) {
            tonicPickMode = enabled;
            setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.CROSSHAIR_CURSOR));
            repaint();
        }

        void resetView() {
            viewStart = 0.0;
            viewEnd = report == null ? 1.0 : Math.max(0.1, report.audio().durationSeconds());
            selectTimeScale(TimeScale.FULL);
            syncTimeControls();
            repaint();
        }

        void openInitialView() {
            if (report == null) { resetView(); return; }
            double total = Math.max(0.1, report.audio().durationSeconds());
            if (total <= INITIAL_DETAIL_SECONDS + 0.01) { resetView(); return; }
            double firstEvidence = !rawPitchBlobs.isEmpty() ? rawPitchBlobs.get(0).startSeconds()
                    : !allPitchBlobs.isEmpty() ? allPitchBlobs.get(0).startSeconds() : 0.0;
            viewStart = Math.max(0.0, Math.min(total - INITIAL_DETAIL_SECONDS, firstEvidence - 1.0));
            viewEnd = viewStart + INITIAL_DETAIL_SECONDS;
            selectTimeScale(TimeScale.THIRTY);
            syncTimeControls();
            repaint();
        }

        void setViewRange(double startSeconds, double endSeconds) {
            if (report == null) return;
            double total = Math.max(0.1, report.audio().durationSeconds());
            double duration = Math.max(0.1, Math.min(total, endSeconds - startSeconds));
            double start = Math.max(0.0, Math.min(total - duration, startSeconds));
            double end = start + duration;
            viewStart = start;
            viewEnd = end;
            selectTimeScale(TimeScale.CUSTOM);
            syncTimeControls();
            repaint();
        }

        void applyTimeScale(double seconds) {
            if (report == null) return;
            if (!Double.isFinite(seconds)) { resetView(); return; }
            double center = selected == null ? playbackSeconds
                    : (selected.startSeconds() + selected.endSeconds()) / 2.0;
            if (center <= 0.0 || center > report.audio().durationSeconds()) center = (viewStart + viewEnd) / 2.0;
            setViewRange(center - seconds / 2.0, center + seconds / 2.0);
            TimeScale chosen = null;
            for (TimeScale value : TimeScale.values()) {
                if (Double.isFinite(value.seconds) && Math.abs(value.seconds - seconds) < 0.01) { chosen = value; break; }
            }
            if (chosen != null) selectTimeScale(chosen);
        }

        void scrollTo(double startSeconds) {
            if (report == null) return;
            double duration = Math.max(0.1, viewEnd - viewStart);
            double total = report.audio().durationSeconds();
            viewStart = Math.max(0.0, Math.min(total - duration, startSeconds));
            viewEnd = viewStart + duration;
            updateViewLabel();
            repaint();
        }

        void panTime(double deltaSeconds) {
            if (report == null) return;
            scrollTo(viewStart + deltaSeconds);
            syncTimeControls();
        }

        void setPlaybackPosition(double seconds, boolean playing) {
            playbackSeconds = seconds;
            playbackActive = playing;
            if (playing && followPlayback.isSelected()) followPlayback(seconds);
            repaint();
        }

        private void followPlayback(double seconds) {
            if (report == null) return;
            double total = Math.max(0.1, report.audio().durationSeconds());
            double duration = Math.max(0.1, viewEnd - viewStart);
            if (duration >= total - 0.01) return;
            double nextStart = Double.NaN;
            // Turn the visible "page" shortly before the playhead reaches its
            // right edge, then leave enough preceding context for listening.
            if (seconds >= viewStart + duration * 0.88) nextStart = seconds - duration * 0.14;
            else if (seconds < viewStart + duration * 0.02) nextStart = seconds - duration * 0.84;
            if (Double.isFinite(nextStart)) {
                scrollTo(nextStart);
                syncTimeControls();
            }
        }

        void zoom(double factor, int anchorX) {
            if (report == null) return;
            double total = report.audio().durationSeconds();
            double duration = Math.max(0.001, viewEnd - viewStart);
            double next = Math.max(0.4, Math.min(total, duration * factor));
            double anchor = xToTime(anchorX);
            double ratio = (anchor - viewStart) / duration;
            viewStart = Math.max(0.0, Math.min(total - next, anchor - next * ratio));
            viewEnd = viewStart + next;
            selectTimeScale(TimeScale.CUSTOM);
            syncTimeControls();
            repaint();
        }

        private void syncTimeControls() {
            double total = report == null ? 1.0 : Math.max(0.1, report.audio().durationSeconds());
            double duration = Math.max(0.1, Math.min(total, viewEnd - viewStart));
            int maximum = Math.max(1, (int) Math.ceil(total * 1000.0));
            int extent = Math.max(1, Math.min(maximum, (int) Math.round(duration * 1000.0)));
            int value = Math.max(0, Math.min(maximum - extent, (int) Math.round(viewStart * 1000.0)));
            updatingTimeScroll = true;
            try {
                timeScroll.setValues(value, extent, 0, maximum);
                timeScroll.setUnitIncrement(Math.max(100, extent / 20));
                timeScroll.setBlockIncrement(Math.max(500, extent * 4 / 5));
                timeScroll.setEnabled(report != null && extent < maximum);
            } finally { updatingTimeScroll = false; }
            updateViewLabel();
        }

        private void updateViewLabel() {
            if (report == null) { viewLabel.setText("Görünüm: kayıt bekleniyor"); return; }
            double duration = Math.max(0.0, viewEnd - viewStart);
            String rendering = densityView() ? "yoğunluk" : "ayrıntılı bloklar";
            if (duration >= report.audio().durationSeconds() - 0.01)
                viewLabel.setText("Görünüm: tüm kayıt • " + rendering);
            else viewLabel.setText(String.format(Locale.US, "Görünüm: %.2f–%.2f sn (%.2f sn) • %s",
                    viewStart, viewEnd, duration, rendering));
        }

        private boolean densityView() { return viewEnd - viewStart > 30.0; }

        private int left() { return 122; }
        private int right() { return Math.max(left() + 1, getWidth() - 18); }
        private int top() { return 42; }
        private int bottom() { return Math.max(top() + 1, getHeight() - 38); }

        private int timeToX(double time) {
            return left() + (int) Math.round((time - viewStart) / Math.max(0.001, viewEnd - viewStart) * (right() - left()));
        }

        private double xToTime(int x) {
            double ratio = Math.max(0.0, Math.min(1.0, (x - left()) / (double) Math.max(1, right() - left())));
            return viewStart + ratio * (viewEnd - viewStart);
        }

        private int visibleLaneCount() {
            return Math.max(1, Math.min(effectiveVisiblePitchLanes(), pitchLanes.size()));
        }

        private double laneHeight() {
            return (bottom() - top()) / (double) visibleLaneCount();
        }

        private double lanePosition(double absoluteCent) {
            if (pitchLanes.isEmpty() || !Double.isFinite(absoluteCent)) return Double.NaN;
            if (absoluteCent > pitchLanes.get(0).absoluteCent() + 0.5) return Double.NaN;
            int last = pitchLanes.size() - 1;
            if (absoluteCent < pitchLanes.get(last).absoluteCent() - 0.5) return Double.NaN;
            if (Math.abs(absoluteCent - pitchLanes.get(0).absoluteCent()) <= 0.5) return 0.0;
            if (Math.abs(absoluteCent - pitchLanes.get(last).absoluteCent()) <= 0.5) return last;
            for (int i = 0; i < last; i++) {
                double upper = pitchLanes.get(i).absoluteCent();
                double lower = pitchLanes.get(i + 1).absoluteCent();
                if (absoluteCent <= upper && absoluteCent >= lower) {
                    return i + (upper - absoluteCent) / Math.max(0.001, upper - lower);
                }
            }
            return Double.NaN;
        }

        private int pitchToY(double absoluteCent) {
            double position = lanePosition(absoluteCent);
            if (!Double.isFinite(position)) return Integer.MIN_VALUE;
            double local = position - pitchScroll.getValue();
            return top() + (int) Math.round((local + 0.5) * laneHeight());
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            // A non-realized test panel, or a split-pane divider moved during
            // layout, may not deliver a resize event before the first paint.
            // Draw readably immediately and repair the scrollbar model on the
            // next EDT turn so every lower lane remains reachable.
            if (!pitchLanes.isEmpty()
                    && pitchScroll.getVisibleAmount() != effectiveVisiblePitchLanes()) {
                SwingUtilities.invokeLater(PitchEditorPanel.this::configurePitchScrollPreservingCenter);
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            hits.clear();
            drawBackground(g);
            if (report == null) {
                drawEmpty(g);
                g.dispose();
                return;
            }
            drawTargetBand(g);
            if (sidebarPitchLayerVisible && showsRawEvidence()) {
                if (showCurve.isSelected()) drawGlobalCurve(g);
                drawRawPitchBlobs(g);
            }
            if (sidebarPitchLayerVisible && showsAcademicEvents()) {
                drawAllPitchBlobs(g);
            }
            if (sidebarEventLayerVisible && showsAcademicEvents()) {
                // The clean note-editor view deliberately omits direction
                // badges. They remain available in the academic audit views.
                if (!cleanView()) {
                    for (PitchOccurrence occurrence : report.occurrences()) drawOccurrence(g, occurrence);
                }
            }
            // Keep glissandi above every blow/occurrence overlay so the thick red mark cannot be hidden.
            if (sidebarEventLayerVisible && showGlobalGlides.isSelected() && showsRawEvidence()) {
                drawGlobalVisualGlides(g, false);
            }
            if (sidebarEventLayerVisible && showGlissando.isSelected() && showsAcademicEvents()) {
                drawGlissandi(g);
            }
            drawPlayback(g);
            drawTonicPickHint(g);
            drawRegisterNotice(g);
            drawTimeRuler(g);
            g.dispose();
        }

        private void pickTonicAt(int mouseX, int mouseY) {
            if (report == null || tonicPickListener == null) return;
            PitchBlob best = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (PitchBlob blob : allPitchBlobs) {
                if (!displayBlob(blob) || blob.endSeconds() < viewStart || blob.startSeconds() > viewEnd) continue;
                int x1 = timeToX(blob.startSeconds());
                int x2 = timeToX(blob.endSeconds());
                int nearestX = Math.max(Math.min(x1, x2), Math.min(Math.max(x1, x2), mouseX));
                int y = pitchToY(displayCent(blob.laneCent()));
                double distance = Math.hypot(mouseX - nearestX, (mouseY - y) * 1.45);
                if (distance < bestDistance) { best = blob; bestDistance = distance; }
            }
            if (best != null && bestDistance <= 46.0) {
                tonicPickListener.accept(16.351597831287414 * Math.pow(2.0, best.observedCent() / 1200.0));
            }
        }

        private void drawTonicPickHint(Graphics2D g) {
            if (!tonicPickMode || report == null) return;
            String text = "Karar seçimi: karar kabul edeceğiniz nota bloğuna tıklayın";
            g.setFont(getFont().deriveFont(Font.BOLD, 12f));
            int width = g.getFontMetrics().stringWidth(text) + 24;
            g.setColor(new Color(15, 118, 110, 230));
            g.fillRoundRect(left() + 10, top() + 10, width, 30, 12, 12);
            g.setColor(Color.WHITE);
            g.drawString(text, left() + 22, top() + 30);
        }

        private void drawRegisterNotice(Graphics2D g) {
            if (Math.abs(displayOctaveShiftCent) < 0.5) return;
            int octaves = (int) Math.round(displayOctaveShiftCent / OCTAVE_CENT);
            String text = String.format(Locale.US,
                    "Görsel perde aktarımı: %+d oktav • ölçülen Hz/C/Hc korunur", octaves);
            g.setFont(getFont().deriveFont(Font.BOLD, 11f));
            int width = g.getFontMetrics().stringWidth(text) + 20;
            int x = Math.max(left() + 8, right() - width - 8);
            int y = top() + 8;
            g.setColor(new Color(15, 118, 110, 220));
            g.fillRoundRect(x, y, width, 25, 10, 10);
            g.setColor(Color.WHITE);
            g.drawString(text, x + 10, y + 17);
        }

        private void drawBackground(Graphics2D g) {
            g.setColor(UiTheme.SURFACE);
            g.fillRect(left(), top(), right() - left(), bottom() - top());
            int offset = pitchScroll.getValue();
            int visible = visibleLaneCount();
            double rowHeight = laneHeight();
            g.setFont(getFont().deriveFont(11f));
            for (int row = 0; row < visible && offset + row < pitchLanes.size(); row++) {
                PitchLane lane = pitchLanes.get(offset + row);
                int y1 = top() + (int) Math.round(row * rowHeight);
                int y2 = top() + (int) Math.round((row + 1) * rowHeight);
                if (lane.target()) g.setColor(new Color(13, 148, 136, 24));
                else if (lane.tonic()) g.setColor(new Color(37, 99, 235, 18));
                else g.setColor(row % 2 == 0 ? UiTheme.SURFACE : UiTheme.SURFACE_SUBTLE);
                g.fillRect(left(), y1, right() - left(), Math.max(1, y2 - y1));
                g.setColor(UiTheme.GRID);
                g.setStroke(new BasicStroke(1f));
                g.drawLine(left(), y2, right(), y2);
                g.setColor(lane.target() ? UiTheme.TARGET : lane.tonic() ? UiTheme.ASCENDING : UiTheme.MUTED);
                g.setFont(getFont().deriveFont(lane.target() || lane.tonic() ? Font.BOLD : Font.PLAIN, 11f));
                String label = lane.name() + (lane.tonic() ? " • karar" : "");
                g.drawString(label, 10, y1 + Math.max(13, (y2 - y1) / 2 + 4));
            }
            for (int i = 0; i <= 8; i++) {
                int x = left() + i * (right() - left()) / 8;
                g.setColor(UiTheme.GRID);
                g.drawLine(x, top(), x, bottom());
            }
            g.setColor(UiTheme.BORDER);
            g.drawRect(left(), top(), right() - left(), bottom() - top());
        }

        private void drawEmpty(Graphics2D g) {
            String message = "Bir kayıt yükleyip Analiz et düğmesine basın";
            g.setColor(UiTheme.MUTED);
            g.setFont(getFont().deriveFont(Font.BOLD, 16f));
            g.drawString(message, (getWidth() - g.getFontMetrics().stringWidth(message)) / 2, getHeight() / 2);
        }

        private void drawTargetBand(Graphics2D g) {
            AnalysisConfig config = report.config();
            double expected = displayCent(config.expectedAbsoluteCent());
            double lower = expected - config.targetLowerToleranceComma() * AnalysisConfig.COMMA_CENT;
            double upper = expected + config.targetUpperToleranceComma() * AnalysisConfig.COMMA_CENT;
            int y1 = pitchToY(upper), y2 = pitchToY(lower);
            if (y1 == Integer.MIN_VALUE || y2 == Integer.MIN_VALUE) return;
            g.setColor(new Color(13, 148, 136, 20));
            g.fillRect(left(), y1, right() - left(), Math.max(1, y2 - y1));
            g.setColor(new Color(13, 148, 136, 110));
            g.drawLine(left(), y1, right(), y1);
            g.drawLine(left(), y2, right(), y2);
            int center = pitchToY(expected);
            g.setStroke(new BasicStroke(1.8f));
            g.drawLine(left(), center, right(), center);
        }

        private void drawGlobalCurve(Graphics2D g) {
            Path2D reliable = new Path2D.Double();
            Path2D weak = new Path2D.Double();
            boolean drawingReliable = false;
            boolean drawingWeak = false;
            for (PitchFrame frame : report.frames()) {
                boolean candidate = frame.candidateVoiced();
                // This is the raw-audit curve.  Even when the academic F0 was
                // replaced by a tonic-filtered/harmonic-corrected candidate,
                // the curve must continue to follow the original detector.
                double cent = frame.candidateAbsoluteCent();
                double displayedCent = displayCent(cent);
                double position = lanePosition(displayedCent);
                if (!candidate || frame.timeSeconds() < viewStart || frame.timeSeconds() > viewEnd
                        || !Double.isFinite(position)
                        || position < pitchScroll.getValue() - 0.5
                        || position > pitchScroll.getValue() + visibleLaneCount() - 0.5) {
                    drawingReliable = false;
                    drawingWeak = false;
                    continue;
                }
                int x = timeToX(frame.timeSeconds()), y = pitchToY(displayedCent);
                if (frame.voiced()) {
                    if (!drawingReliable) { reliable.moveTo(x, y); drawingReliable = true; }
                    else reliable.lineTo(x, y);
                    drawingWeak = false;
                } else {
                    if (!drawingWeak) { weak.moveTo(x, y); drawingWeak = true; }
                    else weak.lineTo(x, y);
                    drawingReliable = false;
                }
            }
            g.setColor(new Color(100, 116, 139, 75));
            g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    10f, new float[]{3f, 3f}, 0f));
            g.draw(weak);
            g.setColor(new Color(15, 42, 78, 155));
            g.setStroke(new BasicStroke(1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(reliable);
        }

        private void drawRawPitchBlobs(Graphics2D g) {
            if (densityView()) {
                drawPitchDensity(g, rawPitchBlobs, true);
                return;
            }
            for (PitchBlob blob : rawPitchBlobs) drawRawPitchBlob(g, blob);
        }

        private void drawAllPitchBlobs(Graphics2D g) {
            if (densityView()) {
                drawPitchDensity(g, allPitchBlobs, false);
                drawActivePitchBlobs(g);
                return;
            }
            for (PitchBlob blob : allPitchBlobs) {
                drawOrganicPitchBlob(g, blob, false);
            }
        }

        /** Keeps the pitch under the moving playhead legible even in the full-record density view. */
        private void drawActivePitchBlobs(Graphics2D g) {
            if (!playbackActive) return;
            for (PitchBlob blob : allPitchBlobs) {
                if (!displayBlob(blob) || !currentlyPlaying(blob.startSeconds(), blob.endSeconds())) continue;
                drawOrganicPitchBlob(g, blob, true);
            }
        }

        /** Raw detector evidence is centred on measured F0, never on a theory lane. */
        private void drawRawPitchBlob(Graphics2D g, PitchBlob blob) {
            if (blob.endSeconds() < viewStart || blob.startSeconds() > viewEnd) return;
            int x1 = Math.max(left(), timeToX(blob.startSeconds()));
            int x2 = Math.min(right(), timeToX(blob.endSeconds()));
            int width = Math.max(3, x2 - x1);
            List<BlobSample> samples = new ArrayList<>();
            int lastX = Integer.MIN_VALUE;
            for (PitchFrame frame : blob.frames()) {
                int y = pitchToY(displayCent(frame.absoluteCent()));
                if (y == Integer.MIN_VALUE) continue;
                int x = Math.max(x1, Math.min(x2, timeToX(frame.timeSeconds())));
                double half = frame.confidence() >= PitchTracker.RELIABLE_CONFIDENCE ? 3.4 : 2.1;
                if (x == lastX && !samples.isEmpty()) {
                    BlobSample previous = samples.get(samples.size() - 1);
                    samples.set(samples.size() - 1, new BlobSample(x,
                            (previous.bodyY() + y) / 2, y, Math.max(previous.halfHeight(), half)));
                } else {
                    samples.add(new BlobSample(x, y, y, half));
                    lastX = x;
                }
            }
            if (samples.isEmpty()) return;
            boolean active = currentlyPlaying(blob.startSeconds(), blob.endSeconds());
            Color base = new Color(100, 116, 139);
            int alpha = active ? 175 : 58;
            if (samples.size() == 1 || width <= 4) {
                BlobSample sample = samples.get(0);
                g.setColor(withAlpha(base, alpha));
                g.fillOval(sample.x() - 3, sample.bodyY() - 3, 6, 6);
                return;
            }
            Path2D body = organicBody(samples);
            g.setColor(withAlpha(base, alpha));
            g.fill(body);
            g.setColor(withAlpha(darken(base), active ? 210 : 92));
            g.setStroke(new BasicStroke(active ? 1.8f : 0.85f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(body);

            // A blob can contain both 35-65% audit-only candidates and >=65%
            // reliable candidates.  Draw the entire candidate body pale,
            // then darken only reliable frame-to-frame runs so confidence is
            // represented per frame rather than by a misleading blob average.
            Path2D reliableRuns = new Path2D.Double();
            boolean drawing = false;
            for (PitchFrame frame : blob.frames()) {
                int y = pitchToY(displayCent(frame.absoluteCent()));
                boolean reliable = frame.confidence() >= PitchTracker.RELIABLE_CONFIDENCE
                        && y != Integer.MIN_VALUE;
                if (!reliable) {
                    drawing = false;
                    continue;
                }
                int x = Math.max(x1, Math.min(x2, timeToX(frame.timeSeconds())));
                if (!drawing) {
                    reliableRuns.moveTo(x, y);
                    drawing = true;
                } else reliableRuns.lineTo(x, y);
            }
            g.setColor(new Color(30, 64, 175, active ? 220 : 145));
            g.setStroke(new BasicStroke(active ? 3.8f : 3.0f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(reliableRuns);
        }

        /**
         * Draws a note as a softly shaded, contour-following body.  The note's
         * canonical theory lane still determines its identity; measured frame
         * deviations shape the body only inside that lane, while the dark
         * centre trace retains the exact F0 path.  This gives the editor the
         * legibility of a modern note editor without changing any analysis
         * value or disguising a Dügâh event as Segâh (or vice versa).
         */
        private void drawOrganicPitchBlob(Graphics2D g, PitchBlob blob, boolean activeOverlay) {
            if (!displayBlob(blob)) return;
            if (blob.endSeconds() < viewStart || blob.startSeconds() > viewEnd) return;
            int laneY = pitchToY(displayCent(blob.laneCent()));
            if (laneY == Integer.MIN_VALUE || laneY < top() - 22 || laneY > bottom() + 22) return;
            int x1 = Math.max(left(), timeToX(blob.startSeconds()));
            int x2 = Math.min(right(), timeToX(blob.endSeconds()));
            int width = Math.max(3, x2 - x1);
            boolean active = activeOverlay || currentlyPlaying(blob.startSeconds(), blob.endSeconds());
            boolean targetPitch = belongsToSelectedPitch(blob);
            boolean selectedEvent = matchesSelected(blob);
            Color base = targetPitch ? UiTheme.TARGET : new Color(47, 111, 224);
            if (active) base = darken(base);

            drawTheoryFrame(g, blob, x1, x2, laneY, targetPitch, selectedEvent);
            List<BlobSample> samples = blobSamples(blob, x1, x2, laneY, active, targetPitch, false);
            if (samples.size() < 2 || width <= 5 || blob.frameCount() == 1) {
                int centerY = samples.isEmpty() ? laneY : samples.get(0).bodyY();
                int ovalWidth = active ? 12 : Math.max(5, Math.min(9, width + 2));
                int ovalHeight = active ? 15 : targetPitch ? 11 : 8;
                int alpha = active ? 242 : confidenceAlpha(blob, targetPitch);
                g.setColor(withAlpha(base, alpha));
                g.fillOval(x1 - ovalWidth / 2, centerY - ovalHeight / 2, ovalWidth, ovalHeight);
                g.setColor(withAlpha(selectedEvent ? UiTheme.HOLD : darken(base), active ? 255 : 185));
                g.setStroke(new BasicStroke(active ? 3.0f : selectedEvent ? 2.4f : 1.1f));
                g.drawOval(x1 - ovalWidth / 2, centerY - ovalHeight / 2, ovalWidth, ovalHeight);
                addBlobHit(blob, new Rectangle(x1 - Math.max(7, ovalWidth / 2),
                        centerY - Math.max(8, ovalHeight / 2), Math.max(14, ovalWidth), Math.max(16, ovalHeight)));
                return;
            }

            Path2D body = organicBody(samples);
            int alpha = active ? 242 : confidenceAlpha(blob, targetPitch);
            Color highlight = lighten(base, active ? 0.18 : 0.35);
            Paint oldPaint = g.getPaint();
            Shape oldClip = g.getClip();
            g.clipRect(left(), top(), right() - left(), bottom() - top());
            g.setPaint(new GradientPaint(x1, laneY - 14,
                    withAlpha(highlight, alpha), x1, laneY + 14,
                    withAlpha(base, Math.min(255, alpha + 18)), false));
            g.fill(body);
            g.setPaint(oldPaint);
            Color outline = selectedEvent ? UiTheme.HOLD : darken(base);
            g.setColor(withAlpha(outline, active ? 255 : selectedEvent ? 245 : Math.min(235, alpha + 55)));
            g.setStroke(new BasicStroke(active ? 3.1f : selectedEvent ? 2.5f : targetPitch ? 1.65f : 1.05f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(body);

            // The navy centre line is the actual F0 contour, not a MIDI-note
            // centre. It is therefore the line used for cent/Hc visual audit.
            Path2D actualF0 = new Path2D.Double();
            boolean started = false;
            for (BlobSample sample : samples) {
                if (sample.actualY() == Integer.MIN_VALUE) continue;
                if (!started) { actualF0.moveTo(sample.x(), sample.actualY()); started = true; }
                else actualF0.lineTo(sample.x(), sample.actualY());
            }
            if (started) {
                g.setColor(new Color(15, 42, 78, active ? 245 : 175));
                g.setStroke(new BasicStroke(active ? 2.45f : 1.35f,
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(actualF0);
            }
            g.setClip(oldClip);

            int bodyHeight = active ? 25 : targetPitch ? 20 : 17;
            int visualTop = laneY - bodyHeight / 2;
            int visualBottom = laneY + bodyHeight / 2;
            for (BlobSample sample : samples) {
                visualTop = Math.min(visualTop,
                        (int) Math.floor(sample.bodyY() - sample.halfHeight()));
                visualBottom = Math.max(visualBottom,
                        (int) Math.ceil(sample.bodyY() + sample.halfHeight()));
            }
            int visualCenter = (visualTop + visualBottom) / 2;
            if (blob.vibrato()) drawVibratoMarker(g, x1, visualCenter, width,
                    Math.max(bodyHeight, visualBottom - visualTop));
            addBlobHit(blob, new Rectangle(x1, visualTop - 3,
                    Math.max(8, width), Math.max(10, visualBottom - visualTop + 6)));
        }

        private void drawTheoryFrame(Graphics2D g, PitchBlob blob, int x1, int x2, int laneY,
                                     boolean targetPitch, boolean selectedEvent) {
            int height = Math.max(8, (int) Math.round(laneHeight() * 0.42));
            int width = Math.max(4, x2 - x1);
            Color color = targetPitch ? UiTheme.TARGET : new Color(47, 111, 224);
            g.setColor(withAlpha(color, selectedEvent ? 42 : 22));
            g.fillRoundRect(x1, laneY - height / 2, width, height, 7, 7);
            g.setColor(withAlpha(color, selectedEvent ? 180 : 90));
            g.setStroke(new BasicStroke(selectedEvent ? 1.6f : 0.9f));
            g.drawRoundRect(x1, laneY - height / 2, width, height, 7, 7);
            if (width >= 52 && blob.noteKey() != null && !blob.noteKey().isBlank()) {
                g.setFont(getFont().deriveFont(Font.PLAIN, 9f));
                g.setColor(withAlpha(darken(color), 165));
                g.drawString("teorik", x1 + 4, laneY - height / 2 - 2);
            }
        }

        private List<BlobSample> blobSamples(PitchBlob blob, int x1, int x2, int laneY,
                                             boolean active, boolean targetPitch,
                                             boolean clampToTheoryLane) {
            List<PitchFrame> frames = blob.frames();
            double maximumRms = frames.stream().mapToDouble(PitchFrame::rms).filter(Double::isFinite)
                    .max().orElse(0.0);
            double laneLimit = Math.max(4.0, laneHeight() * 0.38);
            double baseHalf = (targetPitch ? 8.4 : 6.7) + (active ? 3.1 : 0.0);
            List<BlobSample> values = new ArrayList<>();
            if (frames.isEmpty()) {
                int actualY = pitchToY(displayCent(blob.observedCent()));
                int bodyY = clampToTheoryLane ? clampY(actualY, laneY, laneLimit) : actualY;
                values.add(new BlobSample(x1, bodyY, actualY, baseHalf));
                values.add(new BlobSample(x2, bodyY, actualY, baseHalf));
                return values;
            }
            int lastX = Integer.MIN_VALUE;
            for (PitchFrame frame : frames) {
                int x = Math.max(x1, Math.min(x2, timeToX(frame.timeSeconds())));
                int actualY = pitchToY(displayCent(frame.absoluteCent()));
                int bodyY = clampToTheoryLane ? clampY(actualY, laneY, laneLimit) : actualY;
                double energy = maximumRms > 1.0e-9 && Double.isFinite(frame.rms())
                        ? Math.max(0.0, Math.min(1.0, frame.rms() / maximumRms)) : 0.65;
                double confidence = Math.max(0.0, Math.min(1.0, frame.confidence()));
                double half = baseHalf * (0.72 + 0.18 * Math.sqrt(energy) + 0.10 * confidence);
                if (x == lastX && !values.isEmpty()) {
                    BlobSample previous = values.get(values.size() - 1);
                    values.set(values.size() - 1, new BlobSample(x,
                            (previous.bodyY() + bodyY) / 2,
                            actualY == Integer.MIN_VALUE ? previous.actualY() : actualY,
                            Math.max(previous.halfHeight(), half)));
                } else {
                    values.add(new BlobSample(x, bodyY, actualY, half));
                    lastX = x;
                }
            }
            if (values.isEmpty()) return values;
            BlobSample first = values.get(0);
            BlobSample last = values.get(values.size() - 1);
            if (first.x() > x1) values.add(0,
                    new BlobSample(x1, first.bodyY(), first.actualY(), first.halfHeight() * 0.82));
            if (last.x() < x2) values.add(
                    new BlobSample(x2, last.bodyY(), last.actualY(), last.halfHeight() * 0.82));
            return values;
        }

        /** Kept for diagnostic tools compiled against the previous renderer signature. */
        @SuppressWarnings("unused")
        private List<BlobSample> blobSamples(PitchBlob blob, int x1, int x2, int laneY,
                                             boolean active, boolean targetPitch) {
            return blobSamples(blob, x1, x2, laneY, active, targetPitch, false);
        }

        private int clampY(int actualY, int laneY, double laneLimit) {
            if (actualY == Integer.MIN_VALUE) return laneY;
            return (int) Math.round(Math.max(laneY - laneLimit, Math.min(laneY + laneLimit, actualY)));
        }

        private Path2D organicBody(List<BlobSample> samples) {
            Path2D path = new Path2D.Double();
            BlobSample first = samples.get(0);
            BlobSample last = samples.get(samples.size() - 1);
            path.moveTo(first.x(), first.bodyY());
            path.curveTo(first.x(), first.bodyY() - first.halfHeight(),
                    first.x() + 1.5, first.bodyY() - first.halfHeight(),
                    first.x() + 2.5, first.bodyY() - first.halfHeight());
            for (int index = 1; index < samples.size(); index++) {
                BlobSample sample = samples.get(index);
                path.lineTo(sample.x(), sample.bodyY() - sample.halfHeight());
            }
            path.curveTo(last.x() + 2.0, last.bodyY() - last.halfHeight(),
                    last.x() + 2.0, last.bodyY() + last.halfHeight(),
                    last.x(), last.bodyY() + last.halfHeight());
            for (int index = samples.size() - 2; index >= 0; index--) {
                BlobSample sample = samples.get(index);
                path.lineTo(sample.x(), sample.bodyY() + sample.halfHeight());
            }
            path.curveTo(first.x() - 2.0, first.bodyY() + first.halfHeight(),
                    first.x() - 2.0, first.bodyY() - first.halfHeight(),
                    first.x(), first.bodyY());
            path.closePath();
            return path;
        }

        private int confidenceAlpha(PitchBlob blob, boolean targetPitch) {
            double confidence = Math.max(0.0, Math.min(1.0, blob.confidence()));
            int base = blob.frameCount() <= 1 ? 62 : targetPitch ? 135 : 94;
            return Math.min(224, base + (int) Math.round(confidence * (targetPitch ? 80 : 92)));
        }

        private boolean matchesSelected(PitchBlob blob) {
            if (selected == null || blob.occurrence() == null) return false;
            if (selected.equals(blob.occurrence())) return true;
            return Math.abs(selected.startSeconds() - blob.startSeconds()) < 0.011
                    && Math.abs(selected.endSeconds() - blob.endSeconds()) < 0.011
                    && Math.abs(selected.observedCent() - blob.observedCent()) < 0.75;
        }

        private void addBlobHit(PitchBlob blob, Rectangle bounds) {
            if (blob.occurrence() != null) hits.add(new Hit(bounds, blob.occurrence()));
        }

        private void drawVibratoMarker(Graphics2D g, int x, int y, int width, int height) {
            if (width < 22) return;
            String marker = width >= 54 ? "≈ vib." : "≈";
            Font previous = g.getFont();
            g.setFont(previous.deriveFont(Font.BOLD, width >= 54 ? 10.5f : 13f));
            int markerWidth = g.getFontMetrics().stringWidth(marker);
            g.setColor(new Color(255, 255, 255, 235));
            g.drawString(marker, x + Math.max(4, (width - markerWidth) / 2), y + 4);
            g.setFont(previous);
        }

        private void drawPitchDensity(Graphics2D g, List<PitchBlob> blobs, boolean rawEvidence) {
            int columns = Math.max(1, right() - left() + 1);
            int rows = Math.max(1, visibleLaneCount());
            int[][] stable = new int[columns][rows];
            int[][] transientOnly = new int[columns][rows];
            int[][] target = new int[columns][rows];
            int laneOffset = pitchScroll.getValue();
            for (PitchBlob blob : blobs) {
                if ((!rawEvidence && !displayBlob(blob))
                        || blob.endSeconds() < viewStart || blob.startSeconds() > viewEnd) continue;
                double position = lanePosition(displayCent(rawEvidence ? blob.observedCent() : blob.laneCent()));
                if (!Double.isFinite(position)) continue;
                int row = (int) Math.floor(position - laneOffset);
                if (row < 0 || row >= rows) continue;
                int from = Math.max(0, Math.min(columns - 1, timeToX(blob.startSeconds()) - left()));
                int to = Math.max(from, Math.min(columns - 1, timeToX(blob.endSeconds()) - left()));
                for (int column = from; column <= to; column++) {
                    if (blob.frameCount() == 1) transientOnly[column][row]++;
                    else stable[column][row]++;
                    if (!rawEvidence && belongsToSelectedPitch(blob)) target[column][row]++;
                }
            }
            int height = Math.max(3, (int) Math.round(laneHeight() * 0.48));
            for (int column = 0; column < columns; column++) {
                for (int row = 0; row < rows; row++) {
                    int count = stable[column][row] + transientOnly[column][row];
                    if (count == 0) continue;
                    boolean onlyTransient = stable[column][row] == 0;
                    Color base = rawEvidence ? new Color(71, 85, 105)
                            : target[column][row] > 0 ? UiTheme.TARGET : UiTheme.ASCENDING;
                    int alpha = onlyTransient ? 58
                            : Math.min(210, 70 + (int) Math.round(Math.log1p(count) * 52.0));
                    int x = left() + column;
                    int y = top() + (int) Math.round((row + 0.5) * laneHeight());
                    g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
                    g.fillRect(x, y - height / 2, 2, height);
                }
            }
        }

        private void drawOccurrence(Graphics2D g, PitchOccurrence occurrence) {
            if (!visible(occurrence) || occurrence.endSeconds() < viewStart || occurrence.startSeconds() > viewEnd) return;
            int x1 = Math.max(left(), timeToX(occurrence.startSeconds()));
            int x2 = Math.min(right(), timeToX(occurrence.endSeconds()));
            int width = Math.max(8, x2 - x1);
            PitchBlob matched = matchingBlob(occurrence);
            int y = matched == null ? pitchToY(displayCent(occurrence.expectedCent()))
                    : pitchToY(displayCent(matched.laneCent()));
            if (y < top() - 20 || y > bottom() + 20) return;
            boolean active = currentlyPlaying(occurrence.startSeconds(), occurrence.endSeconds());
            int height = (occurrence.eventType().holdLike() ? 27 : 21) + (active ? 5 : 0);
            Rectangle bounds = new Rectangle(x1, y - height / 2, width, height);
            Color direction = directionColor(occurrence);
            String arrow = switch (directionOf(occurrence)) {
                case ASCENDING -> "↑";
                case DESCENDING -> "↓";
                case LEVEL -> "↔";
            };
            if (width >= 19) {
                int badge = active ? 17 : 15;
                g.setColor(new Color(255, 255, 255, active ? 245 : 215));
                g.fillOval(bounds.x + 3, y - badge / 2, badge, badge);
                g.setColor(withAlpha(direction, active ? 255 : 225));
                g.setStroke(new BasicStroke(active ? 1.8f : 1.1f));
                g.drawOval(bounds.x + 3, y - badge / 2, badge, badge);
                g.setFont(getFont().deriveFont(Font.BOLD, active ? 12.5f : 11.5f));
                g.drawString(arrow, bounds.x + 6, y + 4);
            }
            hits.add(new Hit(bounds, occurrence));
        }

        private PitchBlob matchingBlob(PitchOccurrence occurrence) {
            PitchBlob nearest = null;
            double score = Double.POSITIVE_INFINITY;
            for (PitchBlob blob : allPitchBlobs) {
                if (blob.occurrence() == null) continue;
                double candidate = Math.abs(blob.startSeconds() - occurrence.startSeconds())
                        + Math.abs(blob.endSeconds() - occurrence.endSeconds())
                        + Math.abs(blob.observedCent() - occurrence.observedCent()) / 100.0;
                if (candidate < score) { score = candidate; nearest = blob; }
            }
            return score <= 0.08 ? nearest : null;
        }

        private boolean currentlyPlaying(double startSeconds, double endSeconds) {
            if (!playbackActive) return false;
            // The transport refreshes every 100 ms; this margin also catches very short one-frame detections.
            double margin = 0.055;
            return playbackSeconds >= startSeconds - margin && playbackSeconds <= endSeconds + margin;
        }

        private Color darken(Color color) {
            return new Color(
                    Math.max(0, (int) Math.round(color.getRed() * 0.62)),
                    Math.max(0, (int) Math.round(color.getGreen() * 0.62)),
                    Math.max(0, (int) Math.round(color.getBlue() * 0.62)));
        }

        private Color withAlpha(Color color, int alpha) {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
        }

        private Color lighten(Color color, double amount) {
            double value = Math.max(0.0, Math.min(1.0, amount));
            return new Color(
                    (int) Math.round(color.getRed() + (255 - color.getRed()) * value),
                    (int) Math.round(color.getGreen() + (255 - color.getGreen()) * value),
                    (int) Math.round(color.getBlue() + (255 - color.getBlue()) * value));
        }

        private void drawGlobalVisualGlides(Graphics2D g, boolean cleanStyle) {
            for (VisualGlide glide : globalVisualGlides) {
                if (glide.endSeconds() < viewStart || glide.startSeconds() > viewEnd) continue;
                int x1 = timeToX(glide.startSeconds()), x2 = timeToX(glide.endSeconds());
                int y1 = pitchToY(displayCent(glide.startCent()));
                int y2 = pitchToY(displayCent(glide.endCent()));
                if (y1 == Integer.MIN_VALUE || y2 == Integer.MIN_VALUE) continue;
                if (!cleanStyle) {
                    g.setColor(new Color(255, 255, 255, 175));
                    g.setStroke(new BasicStroke(6.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawLine(x1, y1, x2, y2);
                }
                int alpha = (int) Math.round(125 + Math.max(0.0, Math.min(1.0, glide.confidence())) * 100);
                g.setColor(new Color(220, 38, 38, alpha));
                g.setStroke(new BasicStroke(cleanStyle ? 1.8f : 3.2f,
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(x1, y1, x2, y2);
            }
        }

        private void drawGlissandi(Graphics2D g) {
            for (GlissandoEvent event : report.glissandi()) {
                if (event.endSeconds() < viewStart || event.startSeconds() > viewEnd) continue;
                int x1 = timeToX(event.startSeconds()), x2 = timeToX(event.endSeconds());
                int y1 = pitchToY(displayCent(event.startCent()));
                int y2 = pitchToY(displayCent(event.endCent()));
                if (y1 == Integer.MIN_VALUE || y2 == Integer.MIN_VALUE) continue;
                if ((y1 < top() && y2 < top()) || (y1 > bottom() && y2 > bottom())) continue;
                g.setColor(new Color(255, 255, 255, 225));
                g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(x1, y1, x2, y2);
                g.setColor(UiTheme.GLISSANDO);
                g.setStroke(new BasicStroke(5.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(x1, y1, x2, y2);
                if (Math.abs(x2 - x1) > 46) {
                    g.setFont(getFont().deriveFont(Font.BOLD, 11.5f));
                    g.drawString(String.format(Locale.US, "%.2f sn", event.durationSeconds()), (x1 + x2) / 2 + 4, (y1 + y2) / 2 - 4);
                }
            }
        }

        private void drawPlayback(Graphics2D g) {
            if (playbackSeconds < viewStart || playbackSeconds > viewEnd) return;
            int x = timeToX(playbackSeconds);
            g.setColor(new Color(225, 29, 72));
            g.setStroke(new BasicStroke(1.7f));
            g.drawLine(x, top() - 7, x, bottom());
            Path2D marker = new Path2D.Double();
            marker.moveTo(x - 5, top() - 8); marker.lineTo(x + 5, top() - 8); marker.lineTo(x, top() - 1); marker.closePath();
            g.fill(marker);
        }

        private void drawTimeRuler(Graphics2D g) {
            g.setFont(getFont().deriveFont(10.5f));
            g.setColor(UiTheme.MUTED);
            for (int i = 0; i <= 8; i++) {
                double time = viewStart + i / 8.0 * (viewEnd - viewStart);
                int x = left() + i * (right() - left()) / 8;
                String label = String.format(Locale.US, "%d:%05.2f", (int) time / 60, time % 60);
                int width = g.getFontMetrics().stringWidth(label);
                int offset = i == 0 ? 0 : i == 8 ? width : width / 2;
                g.drawString(label, x - offset, bottom() + 19);
            }
            g.drawString("İki parmak ↕: perdelerde gezin • ↔: zamanda ilerle • Sıkıştır/aç: yatay yakınlaştır • Çift tık: dinle", left(), getHeight() - 5);
        }
    }

    private final class Inspector extends JPanel {
        private final JLabel heading = new JLabel("Basış ayrıntısı");
        private final JLabel subheading = new JLabel("Bir nota bloğu seçin");
        private final JPanel values = new JPanel();

        Inspector() {
            setLayout(new BorderLayout());
            setBackground(UiTheme.SURFACE);
            setPreferredSize(new Dimension(305, 520));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 1, 0, 0, UiTheme.BORDER),
                    BorderFactory.createEmptyBorder(18, 18, 18, 18)));
            JPanel header = new JPanel(new BorderLayout(8, 0));
            header.setOpaque(false);
            JPanel titles = new JPanel();
            titles.setOpaque(false);
            titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, 19f));
            heading.setForeground(UiTheme.INK);
            subheading.setForeground(UiTheme.MUTED);
            titles.add(heading); titles.add(Box.createVerticalStrut(4)); titles.add(subheading);
            JButton close = new AutoFitButton("Kapat");
            close.setFocusable(false);
            close.addActionListener(event -> select(null, false));
            header.add(titles, BorderLayout.CENTER);
            header.add(close, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);
            values.setOpaque(false);
            values.setLayout(new BoxLayout(values, BoxLayout.Y_AXIS));
            values.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
            add(values, BorderLayout.CENTER);
        }

        void showOccurrence(PitchOccurrence occurrence) {
            values.removeAll();
            if (occurrence == null || report == null) {
                heading.setText("Basış ayrıntısı");
                subheading.setText("Bir nota bloğu seçin");
                addMessage("Seçilen basışın frekansı, teorik perdeden farkı, yönü, süresi ve kararlılığı burada görünür.");
            } else {
                heading.setText("Basış #" + occurrence.index());
                subheading.setText(occurrence.eventType().label() + " • " + directionOf(occurrence).label());
                double expectedHz = PitchTracker.centToHertz(occurrence.expectedCent());
                double observedHz = PitchTracker.centToHertz(occurrence.observedCent());
                addValue("Zaman", String.format(Locale.US, "%.2f–%.2f sn", occurrence.startSeconds(), occurrence.endSeconds()));
                addValue("Süre", String.format(Locale.US, "%.3f sn", occurrence.durationSeconds()));
                addValue("Teorik frekans", String.format(Locale.US, "%.3f Hz", expectedHz));
                addValue("Gözlenen frekans", String.format(Locale.US, "%.3f Hz", observedHz));
                addValue("Frekans farkı", String.format(Locale.US, "%+.3f Hz", observedHz - expectedHz));
                if (Math.abs(displayOctaveShiftCent) >= 0.5) {
                    addValue("Görsel yerleşim", String.format(Locale.US,
                            "%+d oktav (yalnız görünüm)",
                            (int) Math.round(displayOctaveShiftCent / OCTAVE_CENT)));
                }
                addValue("Cent farkı", String.format(Locale.US, "%+.2f cent", occurrence.deviationCent()));
                addValue("Koma farkı", String.format(Locale.US, "%+.3f koma", occurrence.deviationComma()));
                addDivider();
                addValue("Perdeye geliş", occurrence.entryDirection().label());
                addValue("Net yerel yön", occurrence.direction().label());
                addValue("Perdeden ayrılış", occurrence.exitDirection().label());
                addDivider();
                addValue("Giriş farkı", String.format(Locale.US, "%+.2f cent", occurrence.entryDeviationCent()));
                addValue("Yerleşik fark", String.format(Locale.US, "%+.2f cent", occurrence.settledDeviationCent()));
                addValue("Çıkış farkı", String.format(Locale.US, "%+.2f cent", occurrence.exitDeviationCent()));
                addValue("Kararlılık", String.format(Locale.US, "%.2f cent", occurrence.stabilityCent()));
                addValue("Vibrato genişliği", String.format(Locale.US, "%.2f cent", occurrence.vibratoSpanCent()));
                addValue("Tespit güveni", String.format(Locale.US, "%%%d", Math.round(occurrence.confidence() * 100)));
            }
            values.revalidate(); values.repaint();
        }

        private void addMessage(String message) {
            JLabel label = new JLabel("<html>" + message + "</html>");
            label.setForeground(UiTheme.MUTED);
            label.setFont(label.getFont().deriveFont(13f));
            label.setAlignmentX(LEFT_ALIGNMENT);
            values.add(label);
        }

        private void addValue(String name, String value) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 29));
            JLabel key = new JLabel(name); key.setForeground(UiTheme.MUTED);
            JLabel data = new JLabel(value, SwingConstants.RIGHT); data.setForeground(UiTheme.INK);
            data.setFont(data.getFont().deriveFont(Font.BOLD));
            row.add(key, BorderLayout.WEST); row.add(data, BorderLayout.EAST);
            values.add(row);
        }

        private void addDivider() {
            JPanel divider = new JPanel();
            divider.setBackground(UiTheme.BORDER);
            divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            values.add(Box.createVerticalStrut(7)); values.add(divider); values.add(Box.createVerticalStrut(7));
        }
    }

    private final class ComparisonView extends JPanel {
        ComparisonView() {
            setBackground(UiTheme.SURFACE);
            setPreferredSize(new Dimension(800, 190));
            setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER));
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(UiTheme.INK);
            g.setFont(getFont().deriveFont(Font.BOLD, 15f));
            g.drawString("İnici–çıkıcı karşılaştırma • " + basis(), 18, 25);
            if (report == null) {
                g.setColor(UiTheme.MUTED); g.setFont(getFont().deriveFont(13f));
                g.drawString("Analizden sonra grup ortalamaları ve her basışın dağılımı burada görünür.", 18, 55);
                g.dispose(); return;
            }
            List<PitchOccurrence> ascending = measured(MelodicDirection.ASCENDING);
            List<PitchOccurrence> descending = measured(MelodicDirection.DESCENDING);
            int gap = 12, margin = 18;
            int width = Math.max(220, (getWidth() - margin * 2 - gap * 2) / 3);
            int y = 38, height = getHeight() - 52;
            drawGroup(g, margin, y, width, height, "Çıkıcı", ascending, UiTheme.ASCENDING);
            drawGroup(g, margin + width + gap, y, width, height, "İnici", descending, UiTheme.DESCENDING);
            drawDifference(g, margin + (width + gap) * 2, y, width, height, ascending, descending);
            g.dispose();
        }

        private List<PitchOccurrence> measured(MelodicDirection direction) {
            return report.occurrences().stream()
                    .filter(value -> value.eventType().measurable() && directionOf(value) == direction).toList();
        }

        private void drawGroup(Graphics2D g, int x, int y, int width, int height, String name,
                               List<PitchOccurrence> occurrences, Color color) {
            drawCard(g, x, y, width, height);
            GroupStatistics statistics = GroupStatistics.from(occurrences,
                    report.config().similarityToleranceComma() * AnalysisConfig.COMMA_CENT);
            g.setColor(color); g.setFont(getFont().deriveFont(Font.BOLD, 14f)); g.drawString(name, x + 13, y + 22);
            g.setColor(UiTheme.MUTED); g.setFont(getFont().deriveFont(11.5f));
            g.drawString("n = " + statistics.count(), x + width - 53, y + 22);
            if (occurrences.isEmpty()) { g.drawString("Ölçülebilir basış yok", x + 13, y + 50); return; }
            double observedHz = PitchTracker.centToHertz(report.config().expectedAbsoluteCent() + statistics.meanDeviationCent());
            g.setColor(UiTheme.INK); g.setFont(getFont().deriveFont(Font.BOLD, 18f));
            g.drawString(String.format(Locale.US, "%+.2f cent", statistics.meanDeviationCent()), x + 13, y + 48);
            g.setFont(getFont().deriveFont(Font.PLAIN, 11.5f)); g.setColor(UiTheme.MUTED);
            g.drawString(String.format(Locale.US, "%+.3f koma • %.3f Hz", statistics.meanDeviationCent() / AnalysisConfig.COMMA_CENT, observedHz), x + 13, y + 67);
            g.drawString(String.format(Locale.US, "Medyan %+.2f • SS %.2f cent", statistics.medianDeviationCent(), statistics.standardDeviationCent()), x + 13, y + 84);
            drawDots(g, x + 13, y + height - 27, width - 26, occurrences, color);
        }

        private void drawDifference(Graphics2D g, int x, int y, int width, int height,
                                    List<PitchOccurrence> ascending, List<PitchOccurrence> descending) {
            drawCard(g, x, y, width, height);
            g.setColor(UiTheme.INK); g.setFont(getFont().deriveFont(Font.BOLD, 14f));
            g.drawString("Çıkıcı − inici", x + 13, y + 22);
            if (ascending.isEmpty() || descending.isEmpty()) {
                g.setColor(UiTheme.MUTED); g.setFont(getFont().deriveFont(11.5f));
                g.drawString("İki grupta da veri gerekli", x + 13, y + 50); return;
            }
            GroupStatistics a = GroupStatistics.from(ascending, Double.POSITIVE_INFINITY);
            GroupStatistics d = GroupStatistics.from(descending, Double.POSITIVE_INFINITY);
            double difference = a.meanDeviationCent() - d.meanDeviationCent();
            double aHz = PitchTracker.centToHertz(report.config().expectedAbsoluteCent() + a.meanDeviationCent());
            double dHz = PitchTracker.centToHertz(report.config().expectedAbsoluteCent() + d.meanDeviationCent());
            g.setColor(UiTheme.ACCENT); g.setFont(getFont().deriveFont(Font.BOLD, 20f));
            g.drawString(String.format(Locale.US, "%+.2f cent", difference), x + 13, y + 51);
            g.setColor(UiTheme.INK); g.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g.drawString(String.format(Locale.US, "%+.3f koma", difference / AnalysisConfig.COMMA_CENT), x + 13, y + 75);
            g.drawString(String.format(Locale.US, "%+.3f Hz", aHz - dHz), x + 13, y + 96);
            g.setColor(UiTheme.MUTED); g.setFont(getFont().deriveFont(11f));
            g.drawString("Pozitif değer: çıkıcı kullanım daha tiz", x + 13, y + height - 15);
        }

        private void drawCard(Graphics2D g, int x, int y, int width, int height) {
            g.setColor(UiTheme.SURFACE_SUBTLE); g.fillRoundRect(x, y, width, height, 12, 12);
            g.setColor(UiTheme.BORDER); g.drawRoundRect(x, y, width, height, 12, 12);
        }

        private void drawDots(Graphics2D g, int x, int y, int width, List<PitchOccurrence> occurrences, Color color) {
            g.setColor(UiTheme.GRID); g.drawLine(x, y, x + width, y);
            int zero = x + width / 2; g.setColor(UiTheme.TARGET); g.drawLine(zero, y - 6, zero, y + 6);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 180));
            int lane = 0;
            for (PitchOccurrence occurrence : occurrences) {
                double value = Math.max(-100, Math.min(100, occurrence.deviationCent()));
                int px = x + (int) Math.round((value + 100) / 200.0 * width);
                int py = y - 3 - (lane++ % 3) * 6;
                g.fillOval(px - 3, py - 3, 6, 6);
            }
            g.setFont(getFont().deriveFont(9.5f)); g.setColor(UiTheme.MUTED);
            g.drawString("−100", x, y + 14); g.drawString("0", zero - 3, y + 14);
            String end = "+100"; g.drawString(end, x + width - g.getFontMetrics().stringWidth(end), y + 14);
        }
    }

    private record PitchLane(String key, String name, double relativeCent, double absoluteCent,
                             boolean target, boolean tonic) {}
    private record PitchBlob(double startSeconds, double endSeconds, double observedCent,
                             double laneCent, double confidence, int frameCount, String noteKey,
                             boolean vibrato, PitchOccurrence occurrence, List<PitchFrame> frames) {}
    private record VisualGlide(double startSeconds, double endSeconds,
                               double startCent, double endCent, double confidence) {}
    private record BlobSample(int x, int bodyY, int actualY, double halfHeight) {}
    private record WeightedCent(double cent, double weight) {}
    private record DisplayRegister(double octaveShiftCent, double anchorCent) {}
    private record Hit(Rectangle bounds, PitchOccurrence occurrence) {}
}
