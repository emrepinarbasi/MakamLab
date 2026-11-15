package com.makambox.studio.ui;

import com.makambox.studio.analysis.AllPitchEventAnalyzer;
import com.makambox.studio.analysis.DirectionalIntonationAnalyzer;
import com.makambox.studio.analysis.TonicEstimator;
import com.makambox.studio.audio.AudioLoader;
import com.makambox.studio.audio.AudioPlayback;
import com.makambox.studio.dsp.AnalysisAudioResampler;
import com.makambox.studio.dsp.ManualTonicSelectionEstimator;
import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.i18n.AppLanguage;
import com.makambox.studio.i18n.I18n;
import com.makambox.studio.i18n.LocalizationService;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.AnalysisReport;
import com.makambox.studio.model.AudioChannelMode;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.GroupStatistics;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.model.PitchOccurrence;
import com.makambox.studio.study.IntervalEventTier;
import com.makambox.studio.study.IntervalPitchNaming;
import com.makambox.studio.study.PitchClassInterval;
import com.makambox.studio.study.FirstFourProfilePreferences;
import com.makambox.studio.study.FirstFourProfileSelection;
import com.makambox.studio.study.SinglePitchStudyService;
import com.makambox.studio.study.StudyStatistics;
import com.makambox.studio.study.UssakSegahDirectionalAnalyzer;
import com.makambox.studio.study.UssakSegahDirectionalStudyResult;
import com.makambox.studio.study.UssakSegahEvent;
import com.makambox.studio.study.UssakSegahUsageClass;
import com.makambox.studio.study.glissando.GlissandoScopeMode;
import com.makambox.studio.study.glissando.PitchPairGlissandoAnalyzer;
import com.makambox.studio.study.glissando.PitchPairGlissandoRequest;
import com.makambox.studio.study.glissando.PitchPairGlissandoStudyResult;
import com.makambox.studio.study.hicaz.HicazAugmentedIntervalAnalyzer;
import com.makambox.studio.study.hicaz.HicazAugmentedIntervalStudyResult;
import com.makambox.studio.theory.TheoryCatalog;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Guided, macOS-first single-pitch academic workflow for the standalone Makam app.
 *
 * <p>F0 extraction is deliberately independent from makam, target and tonic.
 * Changing any of those choices therefore reuses the same cached evidence.</p>
 */
public final class MakamFrame extends JFrame {
    private static final Color BACKGROUND = new Color(0xF5F3F0);
    private static final Color SURFACE = new Color(0xFCFBF9);
    private static final Color INK = new Color(0x1D2735);
    private static final Color MUTED = new Color(99, 108, 123);
    private static final Color BORDER = new Color(220, 216, 210);
    private static final Color CRIMSON = new Color(0xA91632);
    private static final Color FREQUENCY_BLUE = new Color(0x2368A8);
    private static final int CONTROL_CONTENT_WIDTH = 278;
    /**
     * The theory summary contains up to three explicit lines (open-interval
     * mode) and can wrap once more in translated interfaces. The former
     * 170-pixel banner forced the final line below the card's clipping edge.
     */
    private static final int WORKSPACE_BANNER_HEIGHT = 194;
    private static final int THEORY_SUMMARY_HEIGHT = 58;
    private static final double MAXIMUM_SELECTION_PREVIEW_SECONDS = 30.0;
    private static final double HC_CENT = AnalysisConfig.COMMA_CENT;
    private static final double STABLE_SPREAD_HC = 0.55;
    private static final String HOME_CARD = "HOME";
    private static final String WORKSPACE_CARD = "WORKSPACE";

    private final TheoryCatalog theory = TheoryCatalog.loadDefault();
    private final AudioLoader audioLoader = new AudioLoader();
    private final AudioPlayback playback = new AudioPlayback();
    private final LayeredAnalysisPanel evidencePanel = new LayeredAnalysisPanel();
    private final PitchEditorPanel editorPanel = new PitchEditorPanel();
    private final JComboBox<AudioChannelMode> channelBox = new JComboBox<>(AudioChannelMode.values());
    private final JComboBox<TheoryCatalog.Makam> makamBox = new JComboBox<>();
    private final JComboBox<MakamAnalysisMode> analysisModeBox = new JComboBox<>(MakamAnalysisMode.values());
    private final JComboBox<TheoryCatalog.Note> targetBox = new JComboBox<>();
    private final JComboBox<TheoryCatalog.Note> lowerBoundaryBox = new JComboBox<>();
    private final JComboBox<TheoryCatalog.Note> upperBoundaryBox = new JComboBox<>();
    private final JComboBox<TheoryCatalog.Note> directionalLowerBoundaryBox = new JComboBox<>();
    private final JComboBox<TheoryCatalog.Note> directionalUpperBoundaryBox = new JComboBox<>();
    private final JComboBox<TheoryCatalog.Note> glissandoLowerPitchBox = new JComboBox<>();
    private final JComboBox<TheoryCatalog.Note> glissandoUpperPitchBox = new JComboBox<>();
    private final CardLayout analysisSelectorLayout = new CardLayout();
    private final JPanel analysisSelectorCards = new JPanel(analysisSelectorLayout);
    private JPanel theoryFields;
    private JPanel makamField;
    private JPanel analysisModeField;
    private JPanel workspaceCards;
    private JComponent sourceControlCard;
    private JComponent theoryControlCard;
    private JComponent tonicControlCard;
    private final CardLayout rootLayout = new CardLayout();
    private final JPanel rootCards = new JPanel(rootLayout);
    private final CardLayout resultLayout = new CardLayout();
    private final JPanel resultCards = new JPanel(resultLayout);
    private final LocalizationService i18n = I18n.get();
    private final Map<JComponent, LocalizedBinding> localizedTextBindings = new LinkedHashMap<>();
    private final Map<JPanel, LocalizedBinding> localizedBorderBindings = new LinkedHashMap<>();
    private final Map<JButton, MenuCardBinding> localizedMenuCards = new LinkedHashMap<>();
    private final Map<String, JLabel> directionalSummaryHeadings = new LinkedHashMap<>();
    private final CardLayout headerActionsLayout = new CardLayout();
    private final JPanel headerActions = new JPanel(headerActionsLayout);
    private final JPanel headerWorkActions = new JPanel(new AdaptiveButtonRowLayout(8));
    private final JPanel headerHomeUtilities = new JPanel(new AdaptiveButtonRowLayout(8));
    private final JPanel activeModuleBadge = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    private final JLabel activeModuleIcon = new JLabel();
    private final JLabel activeModuleLabel = new JLabel();
    private final JButton homeButton = button(localized("action.home", "‹ Ana menü"));
    private final JButton openAudioButton = button(localized("action.openAudio", "Ses dosyasını aç…"));
    private final LanguageSelectorButton<AppLanguage> languageSelectorButton =
            new LanguageSelectorButton<>(AppLanguage::flag, i18n::languageDisplayName);
    private final JButton settingsButton = button("⚙");
    private final JButton aboutButton = button("ⓘ");
    private final FirstFourProfilePreferences firstFourProfilePreferences =
            new FirstFourProfilePreferences();
    private FirstFourProfileSelection analysisProfileSelection =
            firstFourProfilePreferences.selectionSnapshot();
    private final Runnable languageChangeListener = this::refreshLocalizedInterface;
    private final JTextPane sourceName = centeredTextPane(localized("source.none", "Henüz kayıt açılmadı"));
    private final JLabel sourceDetails = new JLabel("WAV • AIFF • AU • MP3 • M4A");
    private final JTextField tonicField = new JTextField("—", 10);
    private final JTextPane tonicEvidence = centeredTextPane(localized("tonic.evidence.noAudio",
            "Önce ses dosyasını açın"));
    private final JTextPane selectionEvidence = centeredTextPane(localized("tonic.evidence.selectRegion",
            "Grafikte bir ses bölgesi seçebilirsiniz"));
    private final JButton useAutomaticTonicButton = button(localized("action.useLastTonic",
            "Son sesi karar olarak kullan"));
    private final JButton useSelectionTonicButton = button(localized("action.useSelectedTonic",
            "Seçili bölgeyi karar olarak kullan"));
    private final JButton listenSelectionButton = button(localized("transport.playSelection",
            "Seçili bölgeyi oynat"));
    private final JButton pauseButton = button(localized("transport.pause", "Duraklat"));
    private final JButton waveEditorButton = button(localized("action.waveEditor", "Dalga düzenleyici…"));
    private final JButton analyzeButton = primaryButton(localized("action.analyze.single", "Analiz et"));
    private final JButton wordButton = primaryButton(localized("action.createReport", "Rapor oluştur…"));
    private final JButton stopButton = button(localized("transport.stop", "Durdur"));
    private final JTextPane theoreticalTarget = centeredTextPane(localized("theory.target.empty",
            "Kuramsal hedef: —"));
    private final JLabel summary = new JLabel(localized("result.waiting", "Analiz sonucu bekleniyor"));
    private final JLabel resultSourceTitle = new JLabel(localized("source.none", "Henüz kayıt açılmadı"));
    private final JLabel status = new JLabel(localized("status.ready", "Hazır"));
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final ProgressEta progressEta = new ProgressEta();
    private final TargetEventTableModel eventModel = new TargetEventTableModel();
    private final JTable eventTable = new JTable(eventModel);
    private final DirectionalSegahTableModel directionalEventModel = new DirectionalSegahTableModel();
    private final JTable directionalEventTable = new JTable(directionalEventModel);
    private final HicazAugmentedIntervalResultsPanel hicazResultsPanel =
            new HicazAugmentedIntervalResultsPanel();
    private final PitchPairGlissandoResultsPanel glissandoResultsPanel =
            new PitchPairGlissandoResultsPanel();
    private final HicazAugmentedIntervalAnalyzer hicazAnalyzer =
            new HicazAugmentedIntervalAnalyzer();
    private final PitchPairGlissandoAnalyzer glissandoAnalyzer =
            new PitchPairGlissandoAnalyzer();
    private final Map<String, JLabel> directionalSummaryCards = new LinkedHashMap<>();
    private final Map<PitchCacheKey, List<PitchFrame>> pitchCache = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(4, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<PitchCacheKey, List<PitchFrame>> eldest) {
            return size() > 3;
        }
    });
    private final Timer playbackTimer = new Timer(100, event -> refreshPlayback());
    private Thread playbackPreparationThread;
    private long playbackSessionGeneration;
    private double playbackSessionEnd = Double.NaN;
    private double playbackChunkEnd = Double.NaN;
    private boolean continuousPlayback;
    private boolean playbackChunkStarting;

    private Path sourcePath;
    private AudioData audio;
    private List<PitchFrame> frames = List.of();
    private List<PitchEvent> allPitchEvents = List.of();
    private List<PitchEvent> targetPitchEvents = List.of();
    private AnalysisReport report;
    private UssakSegahDirectionalStudyResult directionalSegahStudy;
    private HicazAugmentedIntervalStudyResult hicazStudy;
    private PitchPairGlissandoStudyResult glissandoStudy;
    private TonicEstimator.Estimate automaticTonic;
    private double confirmedTonicHertz = Double.NaN;
    private double tonicStartSeconds = Double.NaN;
    private double tonicEndSeconds = Double.NaN;
    private double tonicConfidence;
    private String tonicMethodKey = "tonic.method.unspecified";
    private String tonicMethodFallback = "Belirtilmedi";
    private String currentStatusKey;
    private String currentStatusFallback;
    private Object[] currentStatusArguments = new Object[0];
    private double selectedTonicHertz = Double.NaN;
    private double selectedTonicConfidence;
    private boolean selectedTonicManualFallback;
    private double selectionStart = Double.NaN;
    private double selectionEnd = Double.NaN;
    private SwingWorker<?, ?> activeWorker;
    private MakamWordReportHandler wordReportHandler;
    private boolean exportAfterAnalysis;
    private boolean resolvingMakamEntry;
    private TheoryCatalog.Makam lastSelectedMakam;

    public MakamFrame() {
        // Use a language-neutral construction title; refreshLocalizedInterface()
        // installs the exact selected-locale title before the frame is shown.
        super("MakamLab");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        // Keep the full workspace inside the usable desktop on compact Windows
        // laptops (for example 1366x768 with a taskbar) while retaining the
        // spacious default on larger macOS and Windows displays.
        java.awt.Rectangle usableBounds = java.awt.GraphicsEnvironment
                .getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int minimumWidth = Math.min(1180, Math.max(980, usableBounds.width));
        int minimumHeight = Math.min(760, Math.max(620, usableBounds.height));
        setMinimumSize(new Dimension(minimumWidth, minimumHeight));
        setSize(Math.min(1480, usableBounds.width), Math.min(920, usableBounds.height));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BACKGROUND);
        configureModels();
        configureLanguageSelector();
        add(buildHeader(), BorderLayout.NORTH);
        rootCards.setOpaque(false);
        rootCards.add(buildHomeMenu(), HOME_CARD);
        rootCards.add(buildWorkspace(), WORKSPACE_CARD);
        add(rootCards, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        evidencePanel.setName("makam-main-waveform");
        evidencePanel.setWaveformOnly(true);
        evidencePanel.setLayers(true, false, false, false);
        evidencePanel.setSelectionListener(this::selectionChanged);
        evidencePanel.setSeekListener(this::seekFromWave);
        editorPanel.setPlayRangeListener(this::playRange);
        editorPanel.setSeekListener(seconds -> playRange(seconds, Math.min(audio == null ? seconds : audio.durationSeconds(), seconds + 2.0)));
        hicazResultsPanel.setTimeRangeListener(this::focusResultTimeRange);
        glissandoResultsPanel.setTimeRangeListener(this::focusResultTimeRange);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { close(); }
        });
        configureShortcuts();
        progress.setVisible(false);
        playbackTimer.start();
        showHomeMenu();
        i18n.addChangeListener(languageChangeListener);
        refreshLocalizedInterface();
        updateButtons();
    }

    public void setWordReportHandler(MakamWordReportHandler handler) {
        wordReportHandler = handler;
        updateButtons();
    }

    public MakamStudyContext studyContext() {
        if (report == null || audio == null || sourcePath == null) return null;
        // The completed analysis is the immutable source of truth for the
        // tonic used by this run.  A live preview value must never give the
        // DOCX exporter a different anchor from the analysed pitch events.
        double analyzedTonicHertz = report.config().tonicHz();
        return new MakamStudyContext(sourcePath, audio, frames, report, allPitchEvents,
                targetPitchEvents, selectedMakam(), selectedAnalysisMode(), selectedTarget(),
                selectedLowerBoundary(), selectedUpperBoundary(), analyzedTonicHertz,
                localized(tonicMethodKey, tonicMethodFallback),
                tonicStartSeconds, tonicEndSeconds, tonicConfidence,
                directionalSegahStudy, hicazStudy, glissandoStudy,
                analysisProfileSelection);
    }

    private void configureModels() {
        analysisModeBox.setName("makam-analysis-mode");
        makamBox.setName("makam-searchable-makam");
        targetBox.setName("makam-single-pitch");
        lowerBoundaryBox.setName("makam-lower-boundary");
        upperBoundaryBox.setName("makam-upper-boundary");
        directionalLowerBoundaryBox.setName("makam-directional-lower-boundary");
        directionalUpperBoundaryBox.setName("makam-directional-upper-boundary");
        glissandoLowerPitchBox.setName("makam-glissando-lower-pitch");
        glissandoUpperPitchBox.setName("makam-glissando-upper-pitch");
        for (TheoryCatalog.Makam makam : theory.makams()) makamBox.addItem(makam);
        makamBox.setSelectedItem(theory.makams().stream()
                .filter(value -> value.name().equalsIgnoreCase("Ussak")).findFirst().orElse(theory.makams().get(0)));
        lastSelectedMakam = (TheoryCatalog.Makam) makamBox.getSelectedItem();
        makamBox.setEditable(true);
        makamBox.setToolTipText(localized("field.makam.searchHint",
                "Makam adını yazıp Enter'a basın veya listeden seçin"));
        if (makamBox.getEditor().getEditorComponent() instanceof JTextField editor) {
            editor.setToolTipText(localized("field.makam.searchHint",
                    "Makam adını yazıp Enter'a basın veya listeden seçin"));
        }
        refreshTargetModel();
        makamBox.addActionListener(event -> {
            if (resolvingMakamEntry) return;
            TheoryCatalog.Makam resolved = resolveMakam(makamBox.getSelectedItem());
            if (resolved == null) {
                if (lastSelectedMakam != null) {
                    resolvingMakamEntry = true;
                    makamBox.setSelectedItem(lastSelectedMakam);
                    resolvingMakamEntry = false;
                }
                return;
            }
            lastSelectedMakam = resolved;
            if (!(makamBox.getSelectedItem() instanceof TheoryCatalog.Makam)) {
                resolvingMakamEntry = true;
                makamBox.setSelectedItem(resolved);
                resolvingMakamEntry = false;
            }
            refreshTargetModel();
            invalidateTargetAnalysis();
            refreshTheoryText();
        });
        analysisModeBox.addActionListener(event -> {
            refreshAnalysisModeControls();
            invalidateTargetAnalysis();
            refreshTheoryText();
            if (audio != null && Double.isFinite(confirmedTonicHertz)) refreshPreview(confirmedTonicHertz);
        });
        targetBox.addActionListener(event -> {
            invalidateTargetAnalysis();
            refreshTheoryText();
        });
        lowerBoundaryBox.addActionListener(event -> intervalBoundaryChanged());
        upperBoundaryBox.addActionListener(event -> intervalBoundaryChanged());
        directionalLowerBoundaryBox.addActionListener(event -> directionalBoundaryChanged());
        directionalUpperBoundaryBox.addActionListener(event -> directionalBoundaryChanged());
        glissandoLowerPitchBox.addActionListener(event -> placeholderSelectionChanged());
        glissandoUpperPitchBox.addActionListener(event -> placeholderSelectionChanged());
        channelBox.addActionListener(event -> {
            if (sourcePath != null && activeWorker == null) loadAudio(sourcePath);
        });
        useAutomaticTonicButton.addActionListener(event -> confirmAutomaticTonic());
        useSelectionTonicButton.addActionListener(event -> confirmSelectedTonic());
        listenSelectionButton.addActionListener(event -> listenSelection());
        pauseButton.addActionListener(event -> togglePause());
        waveEditorButton.addActionListener(event -> openWaveEditor());
        analyzeButton.addActionListener(event -> analyzeTarget());
        wordButton.addActionListener(event -> exportWord());
        settingsButton.addActionListener(event -> openProfileSettings());
        aboutButton.addActionListener(event -> MakamAboutDialog.show(this));
        stopButton.addActionListener(event -> stopPlayback());
        homeButton.addActionListener(event -> showHomeMenu());
        refreshAnalysisModeControls();
        eventTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        eventTable.setAutoCreateRowSorter(true);
        eventTable.setRowHeight(25);
        eventTable.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || eventTable.getSelectedRow() < 0) return;
            int modelRow = eventTable.convertRowIndexToModel(eventTable.getSelectedRow());
            PitchEvent pitchEvent = eventModel.row(modelRow);
            editorPanel.selectOccurrence(pitchEvent.occurrence().index());
            editorPanel.setTimeWindow(Math.max(0.0, pitchEvent.occurrence().startSeconds() - 1.0),
                    Math.min(audio.durationSeconds(), pitchEvent.occurrence().endSeconds() + 1.0));
        });
        directionalEventTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        directionalEventTable.setAutoCreateRowSorter(true);
        directionalEventTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        directionalEventTable.setRowHeight(25);
        configureDirectionalTableColumns();
        directionalEventTable.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || directionalEventTable.getSelectedRow() < 0
                    || audio == null) return;
            int modelRow = directionalEventTable.convertRowIndexToModel(
                    directionalEventTable.getSelectedRow());
            UssakSegahEvent value = directionalEventModel.row(modelRow);
            editorPanel.setTimeWindow(Math.max(0.0, value.measurement().startSeconds() - 1.0),
                    Math.min(audio.durationSeconds(), value.measurement().endSeconds() + 1.0));
        });
    }

    private void configureDirectionalTableColumns() {
        int[] widths = {44, 118, 220, 108, 72, 84, 98, 98,
                102, 102, 88, 88, 82, 92, 98, 240};
        for (int index = 0; index < Math.min(widths.length,
                directionalEventTable.getColumnModel().getColumnCount()); index++) {
            directionalEventTable.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
        }
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new GridBagLayout());
        header.setBackground(SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        JComponent title = new MakamWordmark();

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        brand.setOpaque(false);
        // Reserve the complete brand/badge strip even while the badge is
        // hidden on the landing page. Otherwise GridBagLayout can cache the
        // short home-page minimum and later clip the active module outside the
        // brand panel when entering a workspace.
        brand.setMinimumSize(new Dimension(285, 48));
        brand.setPreferredSize(new Dimension(285, 48));
        JLabel icon = new JLabel(loadHeaderIcon(44));
        icon.setName("makam-header-icon");
        icon.setPreferredSize(new Dimension(44, 44));
        icon.setMinimumSize(new Dimension(44, 44));
        icon.setToolTipText("MakamLab");
        brand.add(icon);
        brand.add(title);
        activeModuleBadge.setName("makam-active-module-badge");
        activeModuleBadge.setOpaque(false);
        activeModuleBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER),
                BorderFactory.createEmptyBorder(0, 9, 0, 0)));
        activeModuleIcon.setPreferredSize(new Dimension(24, 24));
        activeModuleLabel.setForeground(INK);
        activeModuleLabel.setFont(activeModuleLabel.getFont().deriveFont(Font.BOLD, 10f));
        activeModuleBadge.add(activeModuleIcon);
        activeModuleBadge.add(activeModuleLabel);
        activeModuleBadge.setVisible(false);
        brand.add(activeModuleBadge);
        GridBagConstraints brandConstraints = new GridBagConstraints();
        brandConstraints.gridx = 0;
        brandConstraints.gridy = 0;
        brandConstraints.weightx = 0.0;
        brandConstraints.anchor = GridBagConstraints.LINE_START;
        // Keep only a compact visual separation from the action row.  The
        // released width is used by long translated labels so no locale is
        // forced into an ellipsis at normal window size.
        brandConstraints.insets = new Insets(0, 0, 0, 4);
        header.add(brand, brandConstraints);

        headerActions.setOpaque(false);
        headerActions.setName("makam-header-actions");
        // GridBag must be allowed to hand the row less than its calculated
        // button minimum on compact desktops. AdaptiveButtonRowLayout then
        // contracts every action proportionally instead of letting the final
        // Analyse/Report actions escape past the right edge.
        headerActions.setMinimumSize(new Dimension(0, 38));
        headerWorkActions.setOpaque(false);
        headerWorkActions.setName("makam-header-work-actions");
        headerWorkActions.setMinimumSize(new Dimension(0, 38));
        homeButton.setToolTipText(localized("action.home.tooltip",
                "Analiz türü seçim ekranına dön"));
        homeButton.setName("makam-header-home");
        configureHeaderActionButton(homeButton, 118);
        headerWorkActions.add(homeButton);
        openAudioButton.addActionListener(event -> chooseAudio());
        openAudioButton.setName("makam-header-open-audio");
        configureHeaderActionButton(openAudioButton, 166);
        headerWorkActions.add(openAudioButton);
        waveEditorButton.setName("makam-header-wave-editor");
        configureHeaderActionButton(waveEditorButton, 166);
        headerWorkActions.add(waveEditorButton);
        analyzeButton.setName("makam-header-analyze");
        configureHeaderActionButton(analyzeButton, 178);
        headerWorkActions.add(analyzeButton);
        wordButton.setName("makam-header-report");
        configureHeaderActionButton(wordButton, 164);
        headerWorkActions.add(wordButton);

        headerHomeUtilities.setOpaque(false);
        headerHomeUtilities.setName("makam-header-home-utilities");
        configureHomeUtilityButton(settingsButton, "makamlab-settings-button", 25f);
        headerHomeUtilities.add(settingsButton);
        languageSelectorButton.setName("makam-language-selector");
        languageSelectorButton.setPreferredSize(new Dimension(80, 46));
        languageSelectorButton.setMinimumSize(new Dimension(72, 42));
        headerHomeUtilities.add(languageSelectorButton);
        configureHomeUtilityButton(aboutButton, "makamlab-about-button", 27f);
        headerHomeUtilities.add(aboutButton);

        headerActions.add(headerHomeUtilities, HOME_CARD);
        headerActions.add(headerWorkActions, WORKSPACE_CARD);
        GridBagConstraints actionConstraints = new GridBagConstraints();
        actionConstraints.gridx = 1;
        actionConstraints.gridy = 0;
        actionConstraints.weightx = 1.0;
        actionConstraints.fill = GridBagConstraints.HORIZONTAL;
        actionConstraints.anchor = GridBagConstraints.LINE_END;
        header.add(headerActions, actionConstraints);
        return header;
    }

    private void configureLanguageSelector() {
        languageSelectorButton.setSortLocaleProvider(i18n::locale);
        languageSelectorButton.setSelectionListener(i18n::setLanguage);
        languageSelectorButton.setOptions(i18n.availableLanguages(), i18n.currentLanguage());
    }

    private static void configureHomeUtilityButton(JButton button, String name,
                                                   float fontSize) {
        button.setName(name);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(205, 208, 214), 1, true),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        if (button instanceof AutoFitButton adaptive) {
            adaptive.setMinimumAutoFitFontSize(22f);
        }
        button.setFont(button.getFont().deriveFont(Font.BOLD, fontSize));
        button.setPreferredSize(new Dimension(64, 46));
        button.setMinimumSize(new Dimension(58, 42));
    }

    private void openProfileSettings() {
        boolean changed = MakamProfileSettingsDialog.show(this, firstFourProfilePreferences);
        if (!changed) return;
        // A report must describe the exact reference inventory used by its
        // analysis.  Discard stale results and freeze a new snapshot only when
        // the user starts the next analysis.
        invalidateTargetAnalysis();
        setStatus("status.profileSettingsUpdated",
                "İlk dört perde karşılaştırma ayarları güncellendi; analizi yeniden çalıştırın.");
    }

    /** Refreshes the critical actions immediately after a language change. */
    private void refreshLocalizedInterface() {
        languageSelectorButton.setOptions(i18n.availableLanguages(), i18n.currentLanguage());
        String languageLabel = localized("language.selector.tooltip", "Dil seçimi");
        languageSelectorButton.setAccessibilityText(languageLabel, languageLabel);
        languageSelectorButton.setToolTipText(languageLabel);
        String aboutLabel = localized("menu.about", "MakamLab Hakkında");
        aboutButton.setToolTipText(aboutLabel);
        aboutButton.getAccessibleContext().setAccessibleName(aboutLabel);
        String settingsLabel = localized("menu.settings", "Ayarlar");
        settingsButton.setToolTipText(settingsLabel);
        settingsButton.getAccessibleContext().setAccessibleName(settingsLabel);
        homeButton.setText(localized("action.home", "‹ Ana menü"));
        homeButton.setToolTipText(localized("action.home.tooltip",
                "Analiz türü seçim ekranına dön"));
        openAudioButton.setText(localized("action.openAudio", "Ses dosyasını aç…"));
        waveEditorButton.setText(localized("action.waveEditor", "Dalga düzenleyici…"));
        wordButton.setText(localized("action.createReport", "Rapor oluştur…"));
        useAutomaticTonicButton.setText(localized("action.useLastTonic",
                "Son sesi karar olarak kullan"));
        useSelectionTonicButton.setText(localized("action.useSelectedTonic",
                "Seçili bölgeyi karar olarak kullan"));
        listenSelectionButton.setToolTipText(localized("transport.playSelection",
                "Seçili bölgeyi oynat"));
        listenSelectionButton.getAccessibleContext().setAccessibleName(
                localized("transport.play", "Oynat"));
        pauseButton.setToolTipText(playback.isPaused()
                ? localized("transport.resume", "Oynatmayı sürdür")
                : localized("transport.pause", "Oynatmayı duraklat"));
        pauseButton.getAccessibleContext().setAccessibleName(
                localized("transport.pause", "Duraklat"));
        stopButton.setToolTipText(localized("transport.stop", "Oynatmayı durdur"));
        stopButton.getAccessibleContext().setAccessibleName(
                localized("transport.stop", "Durdur"));
        makamBox.setToolTipText(localized("field.makam.searchHint",
                "Makam adını yazıp Enter'a basın veya listeden seçin"));
        if (makamBox.getEditor().getEditorComponent() instanceof JTextField editor) {
            editor.setToolTipText(localized("field.makam.searchHint",
                    "Makam adını yazıp Enter'a basın veya listeden seçin"));
        }
        if (audio == null) {
            sourceName.setText(localized("source.none", "Henüz kayıt açılmadı"));
            resultSourceTitle.setText(localized("source.none", "Henüz kayıt açılmadı"));
        } else {
            refreshSourceDetailsLocalization();
        }
        refreshTonicEvidenceLocalization();
        if (report == null) summary.setText(localized("result.waiting", "Analiz sonucu bekleniyor"));
        evidencePanel.setLocalizedCanvasText(
                localized("canvas.empty", "Bir ses dosyası açın veya örnek melodiyi yükleyin"),
                localized("canvas.tonicPick",
                        "Karar seçimi: karar kabul edeceğiniz perde izine tıklayın"),
                localized("canvas.boundaryExcluded", "sınır hariç"),
                localized("canvas.navigationHint",
                        "İki parmak ↕: perdede gezin • ↔: zamanda ilerle • Sıkıştır/aç: yatay yakınlaştır • Option+↕: dikey yakınlaştır"),
                localized("canvas.waveformBuilding", "Dalga biçimi arka planda hazırlanıyor…"));
        analysisModeBox.repaint();
        channelBox.repaint();
        for (Map.Entry<JComponent, LocalizedBinding> entry : localizedTextBindings.entrySet()) {
            applyLocalizedText(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<JPanel, LocalizedBinding> entry : localizedBorderBindings.entrySet()) {
            applyWorkspaceCardBorder(entry.getKey(), localized(
                    entry.getValue().key(), entry.getValue().fallback()));
        }
        for (Map.Entry<JButton, MenuCardBinding> entry : localizedMenuCards.entrySet()) {
            MenuCardBinding binding = entry.getValue();
            setMenuCardText(entry.getKey(), localized(binding.titleKey(), binding.titleFallback()),
                    localized(binding.descriptionKey(), binding.descriptionFallback()));
        }
        for (Map.Entry<String, JLabel> entry : directionalSummaryHeadings.entrySet()) {
            entry.getValue().setText(switch (entry.getKey()) {
                case "ASCENDING" -> localized("report.ascending", "Çıkıcı");
                case "DESCENDING" -> localized("report.descending", "İnici");
                case "HOLD" -> localized("report.sustained", "Segâh kalışı");
                default -> localized("report.all", "Tümü");
            });
        }
        if (currentStatusKey != null) {
            status.setText(i18n.textOrDefault(currentStatusKey, currentStatusFallback,
                    currentStatusArguments));
        }
        eventModel.fireTableStructureChanged();
        directionalEventModel.fireTableStructureChanged();
        configureDirectionalTableColumns();
        updateDirectionalSummaryCards(directionalSegahStudy);
        hicazResultsPanel.refreshLocalization();
        glissandoResultsPanel.refreshLocalization();
        refreshTheoryText();
        if (report != null) refreshSummary();
        MakamAnalysisMode selected = selectedAnalysisMode();
        // A live language change must not make a hidden workspace badge
        // reappear on the landing page.  Only refresh the badge while the
        // workspace action strip itself is active.
        if (selected != null && headerWorkActions.isVisible()) {
            updateActiveModuleBadge(selected);
        } else {
            activeModuleBadge.setVisible(false);
        }
        if (selected != null) refreshAnalysisModeControls();
        setTitle(localized("app.title", "MakamLab — Akademik İcra Analizi"));
        i18n.applyOrientation(this);
        revalidate();
        repaint();
    }

    private String localized(String key, String fallback) {
        String value = i18n.text(key);
        return value.equals("[" + key + "]") ? fallback : value;
    }

    private <T extends JComponent> T localizedText(T component, String key, String fallback) {
        LocalizedBinding binding = new LocalizedBinding(key, fallback);
        localizedTextBindings.put(component, binding);
        applyLocalizedText(component, binding);
        return component;
    }

    private void applyLocalizedText(JComponent component, LocalizedBinding binding) {
        String value = localized(binding.key(), binding.fallback());
        if (component instanceof AbstractButton button) button.setText(value);
        else if (component instanceof JLabel label) label.setText(value);
        else if (component instanceof javax.swing.text.JTextComponent text) text.setText(value);
    }

    private static void setMenuCardText(JButton card, String title, String description) {
        card.setText("<html><div style='text-align:center'>"
                + "<span style='font-size:16pt;color:#b11230'><b>" + title + "</b></span>"
                + "<br><br><span style='font-size:10pt;color:#636c7b'>" + description
                + "</span></div></html>");
    }

    /** Application landing page with the four academic analysis modules. */
    private JComponent buildHomeMenu() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setName("makam-home-menu");
        page.setBackground(BACKGROUND);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel title = localizedText(new JLabel("", SwingConstants.CENTER),
                "home.selectStudy", "Çalışma türünü seçin");
        title.setForeground(INK);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel explanation = localizedText(new JLabel("", SwingConstants.CENTER),
                "home.explanation",
                "Kaydı hangi akademik soruyla incelemek istediğinizi seçin.");
        explanation.setForeground(MUTED);
        explanation.setFont(explanation.getFont().deriveFont(14f));
        explanation.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(8));
        content.add(explanation);
        content.add(Box.createVerticalStrut(28));

        JPanel menuGrid = new JPanel(new GridLayout(0, 2, 26, 26));
        menuGrid.setOpaque(false);
        menuGrid.add(menuCard("MenuIcon-PerdeAnalizi-v1.png", "module.pitch", "Perde Analizi",
                "home.pitch.description",
                "Tek bir perdeyi veya seçtiğiniz iki perde arasındaki açık alanı inceleyin.",
                MakamAnalysisMode.SINGLE_PITCH));
        menuGrid.add(menuCard("MenuIcon-IniciCikici-v1.png", "module.directional",
                "İnici-Çıkıcı Perde Analizi", "home.directional.description",
                "Segâh'ı çıkıcı, inici ve doğrudan kalış bağlamlarında ayrı ayrı karşılaştırın.",
                MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL));
        menuGrid.add(menuCard("MenuIcon-ArtikAralik-v1-preview.png", "module.augmented",
                "Artık Aralık Analizi", "home.augmented.description",
                "Hicaz dörtlüsünün çıkıcı ve inici icrasında 2., 3. ve 4. dereceyi inceleyin.",
                MakamAnalysisMode.HICAZ_AUGMENTED_INTERVAL));
        menuGrid.add(menuCard("MenuIcon-GlissandoAnalizi-v1-preview.png", "module.glissando",
                "Glissando Analizi", "home.glissando.description",
                "Seçtiğiniz iki perde arasındaki kesintisiz kaymaları iki yönde ayrı ölçün.",
                MakamAnalysisMode.GLISSANDO_ANALYSIS));
        menuGrid.setAlignmentX(Component.CENTER_ALIGNMENT);
        menuGrid.setMaximumSize(new Dimension(930, 470));
        menuGrid.setPreferredSize(new Dimension(930, 470));
        content.add(menuGrid);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(24, 30, 30, 30);
        constraints.fill = GridBagConstraints.NONE;
        page.add(content, constraints);
        return page;
    }

    private JComponent menuCard(String iconName, String titleKey, String name,
                                String descriptionKey, String description,
                                MakamAnalysisMode mode) {
        JButton card = new AutoFitButton();
        card.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        card.setBackground(SURFACE);
        card.setOpaque(true);
        card.setContentAreaFilled(true);
        card.setFocusPainted(false);
        card.setName("makam-home-" + mode.name().toLowerCase(Locale.ROOT));
        card.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(206, 164, 173), 2, true),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)));
        card.setIcon(loadMenuIcon(iconName, 126));
        card.setVerticalTextPosition(SwingConstants.BOTTOM);
        card.setHorizontalTextPosition(SwingConstants.CENTER);
        card.setIconTextGap(12);
        localizedMenuCards.put(card, new MenuCardBinding(titleKey, name,
                descriptionKey, description));
        setMenuCardText(card, localized(titleKey, name), localized(descriptionKey, description));
        card.addActionListener(event -> showWorkspace(mode));
        return card;
    }

    private void showHomeMenu() {
        stopPlayback();
        rootLayout.show(rootCards, HOME_CARD);
        headerActionsLayout.show(headerActions, HOME_CARD);
        activeModuleBadge.setVisible(false);
        if (activeModuleBadge.getParent() != null) activeModuleBadge.getParent().revalidate();
        headerActions.revalidate();
        headerActions.repaint();
        setStatus("status.selectStudy", "Bir çalışma türü seçin");
    }

    private void showWorkspace(MakamAnalysisMode mode) {
        MakamAnalysisMode requested = mode == null ? MakamAnalysisMode.SINGLE_PITCH : mode;
        configureWorkspaceAnalysisModes(requested);
        updateActiveModuleBadge(requested);
        if (mode == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL) selectTargetKey("segah");
        refreshAnalysisModeControls();
        rootLayout.show(rootCards, WORKSPACE_CARD);
        headerActionsLayout.show(headerActions, WORKSPACE_CARD);
        if (activeModuleBadge.getParent() != null) activeModuleBadge.getParent().revalidate();
        headerActions.revalidate();
        headerActions.repaint();
        if (audio == null) setStatus("status.openAudio", "Ses dosyasını açarak başlayın");
        else setStatus("status.ready", "Hazır");
        updateButtons();
    }

    private void updateActiveModuleBadge(MakamAnalysisMode mode) {
        String iconName;
        String label;
        switch (mode) {
            case SINGLE_PITCH, BETWEEN_PITCHES -> {
                iconName = "MenuIcon-PerdeAnalizi-v1.png";
                label = localized("module.pitch", "Perde Analizi");
            }
            case USSAK_SEGAH_DIRECTIONAL -> {
                iconName = "MenuIcon-IniciCikici-v1.png";
                label = localized("module.directional", "İnici–Çıkıcı Perde Analizi");
            }
            case HICAZ_AUGMENTED_INTERVAL -> {
                iconName = "MenuIcon-ArtikAralik-v1-preview.png";
                label = localized("module.augmented", "Artık Aralık Analizi");
            }
            case GLISSANDO_ANALYSIS -> {
                iconName = "MenuIcon-GlissandoAnalizi-v1-preview.png";
                label = localized("module.glissando", "Glissando Analizi");
            }
            default -> throw new IllegalStateException("Desteklenmeyen analiz türü: " + mode);
        }
        activeModuleIcon.setIcon(loadMenuIcon(iconName, 24));
        activeModuleLabel.setText(label);
        activeModuleBadge.setToolTipText(localized("module.active", "Etkin bölüm") + ": " + label);
        activeModuleBadge.setVisible(true);
        if (activeModuleBadge.getParent() != null) activeModuleBadge.getParent().revalidate();
    }

    private void configureWorkspaceAnalysisModes(MakamAnalysisMode requested) {
        boolean generalPitchAnalysis = requested == MakamAnalysisMode.SINGLE_PITCH
                || requested == MakamAnalysisMode.BETWEEN_PITCHES;
        DefaultComboBoxModel<MakamAnalysisMode> model = new DefaultComboBoxModel<>();
        if (generalPitchAnalysis) {
            model.addElement(MakamAnalysisMode.SINGLE_PITCH);
            model.addElement(MakamAnalysisMode.BETWEEN_PITCHES);
        } else {
            // The three specialist studies are entered from their own home
            // cards and do not leak into the general pitch-analysis menu.
            model.addElement(requested);
        }
        analysisModeBox.setModel(model);
        analysisModeBox.setSelectedItem(requested);
        if (theoryFields != null && makamField != null && analysisModeField != null) {
            theoryFields.removeAll();
            theoryFields.setLayout(new GridLayout(1, generalPitchAnalysis ? 2 : 1, 8, 0));
            theoryFields.add(makamField);
            if (generalPitchAnalysis) theoryFields.add(analysisModeField);
            theoryFields.revalidate();
            theoryFields.repaint();
        }
        if (workspaceCards != null && sourceControlCard != null
                && theoryControlCard != null && tonicControlCard != null) {
            boolean tonicOnlyTheory = requested == MakamAnalysisMode.HICAZ_AUGMENTED_INTERVAL;
            layoutWorkspaceCards(tonicOnlyTheory);
        }
    }

    private JComponent buildWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout(0, 8));
        workspace.setBackground(BACKGROUND);
        workspace.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        workspace.add(buildWorkspaceBanner(), BorderLayout.NORTH);

        JPanel evidenceCard = card();
        evidenceCard.add(evidencePanel, BorderLayout.CENTER);
        JPanel evidenceTimeline = new JPanel(new BorderLayout());
        evidenceTimeline.setOpaque(false);
        evidenceTimeline.setMinimumSize(new Dimension(0, 18));
        evidenceTimeline.setPreferredSize(new Dimension(120, 18));
        evidenceTimeline.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        evidenceTimeline.add(evidencePanel.timeScrollBar(), BorderLayout.CENTER);
        evidenceCard.add(evidenceTimeline, BorderLayout.SOUTH);
        // Keep the waveform as a compact inspection strip.  The detailed
        // analysis table below needs the larger share of the vertical space.
        evidenceCard.setMinimumSize(new Dimension(500, 112));

        JPanel results = new JPanel(new BorderLayout(0, 8));
        results.setBackground(SURFACE);
        results.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        resultSourceTitle.setName("makam-result-source-title");
        resultSourceTitle.setForeground(INK);
        resultSourceTitle.setFont(resultSourceTitle.getFont().deriveFont(Font.BOLD, 16f));
        summary.setFont(summary.getFont().deriveFont(Font.BOLD, 15f));
        summary.setForeground(FREQUENCY_BLUE);
        JPanel resultHeading = new JPanel();
        resultHeading.setOpaque(false);
        resultHeading.setLayout(new BoxLayout(resultHeading, BoxLayout.Y_AXIS));
        resultHeading.add(resultSourceTitle);
        resultHeading.add(Box.createVerticalStrut(3));
        resultHeading.add(summary);
        results.add(resultHeading, BorderLayout.NORTH);
        resultCards.setOpaque(false);
        resultCards.add(new JScrollPane(eventTable), "STANDARD");
        resultCards.add(buildDirectionalResults(), "DIRECTIONAL");
        resultCards.add(hicazResultsPanel, "HICAZ");
        resultCards.add(glissandoResultsPanel, "GLISSANDO");
        results.add(resultCards, BorderLayout.CENTER);

        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, evidenceCard, results);
        vertical.setResizeWeight(.22); vertical.setContinuousLayout(true);
        vertical.setDividerLocation(145); vertical.setBorder(BorderFactory.createEmptyBorder());
        workspace.add(vertical, BorderLayout.CENTER);
        return workspace;
    }

    private JComponent buildWorkspaceBanner() {
        JPanel banner = new JPanel(new BorderLayout(10, 0));
        banner.setName("makam-workspace-banner");
        banner.setBackground(BACKGROUND);

        workspaceCards = new JPanel(new GridBagLayout());
        workspaceCards.setOpaque(false);
        sourceControlCard = buildSourceCard();
        theoryControlCard = buildTheoryCard();
        tonicControlCard = buildTonicCard();
        layoutWorkspaceCards(false);
        banner.add(workspaceCards, BorderLayout.CENTER);
        banner.add(buildTransportCard(), BorderLayout.EAST);
        banner.setMinimumSize(new Dimension(720, WORKSPACE_BANNER_HEIGHT));
        banner.setPreferredSize(new Dimension(1000, WORKSPACE_BANNER_HEIGHT));
        return banner;
    }

    /**
     * Gives the control cards content-aware widths.  The tonic card receives
     * the largest share because its translated confirmation actions are the
     * longest.  Unlike an equal-cell GridLayout, this lets one card expand
     * without wasting the same width in every other card.
     */
    private void layoutWorkspaceCards(boolean tonicOnlyTheory) {
        workspaceCards.removeAll();
        workspaceCards.setLayout(new GridBagLayout());
        int column = 0;
        addWorkspaceCard(sourceControlCard, column++, tonicOnlyTheory ? .30 : .20, 215);
        if (!tonicOnlyTheory) {
            addWorkspaceCard(theoryControlCard, column++, .33, 285);
        }
        addWorkspaceCard(tonicControlCard, column, tonicOnlyTheory ? .70 : .47, 490);
        workspaceCards.revalidate();
        workspaceCards.repaint();
    }

    private void addWorkspaceCard(JComponent card, int column, double weight, int minimumWidth) {
        card.setMinimumSize(new Dimension(minimumWidth, 0));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = 0;
        constraints.weightx = weight;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, column == 0 ? 0 : 5, 0, 0);
        workspaceCards.add(card, constraints);
    }

    private JComponent buildSourceCard() {
        JPanel panel = workspaceControlCard("card.recording", "1. Kayıt");
        sourceName.setName("makam-source-name");
        sourceName.setFont(sourceName.getFont().deriveFont(Font.BOLD, 14f));
        sourceName.setForeground(INK);
        sourceName.setAlignmentX(Component.CENTER_ALIGNMENT);
        sourceName.setMinimumSize(new Dimension(120, 28));
        sourceName.setPreferredSize(new Dimension(CONTROL_CONTENT_WIDTH, 34));
        sourceName.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        sourceDetails.setHorizontalAlignment(SwingConstants.CENTER);
        sourceDetails.setName("makam-source-details");
        sourceDetails.setForeground(MUTED);
        sourceDetails.setAlignmentX(Component.CENTER_ALIGNMENT);
        sourceDetails.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        panel.add(sourceName);
        panel.add(sourceDetails);
        panel.add(Box.createVerticalStrut(3));
        panel.add(labeled("field.audioChannel", "Ses kanalı", channelBox));
        return panel;
    }

    private JComponent buildTheoryCard() {
        JPanel panel = workspaceControlCard("card.theoryTarget", "2. Kuram ve hedef");
        theoryFields = new JPanel(new GridLayout(1, 2, 8, 0));
        theoryFields.setOpaque(false);
        theoryFields.setAlignmentX(Component.CENTER_ALIGNMENT);
        theoryFields.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        makamField = compactLabeled("field.makam", "Makam", makamBox);
        analysisModeField = compactLabeled("field.analysisType", "Analiz türü", analysisModeBox);
        theoryFields.add(makamField);
        theoryFields.add(analysisModeField);
        panel.add(theoryFields);
        panel.add(Box.createVerticalStrut(3));
        analysisSelectorCards.setOpaque(false);

        JPanel singlePitchSelector = selectorPanel();
        singlePitchSelector.add(compactLabeled("field.targetPitch", "İncelenecek perde", targetBox));
        JPanel intervalSelector = selectorPanel();
        JPanel intervalFields = new JPanel(new GridLayout(1, 2, 8, 0));
        intervalFields.setOpaque(false);
        intervalFields.add(compactLabeled("field.lowerBoundary", "Alt sınır (hariç)", lowerBoundaryBox));
        intervalFields.add(compactLabeled("field.upperBoundary", "Üst sınır (hariç)", upperBoundaryBox));
        intervalSelector.add(intervalFields);
        JPanel directionalSelector = selectorPanel();
        JPanel directionalFields = new JPanel(new GridLayout(1, 2, 8, 0));
        directionalFields.setOpaque(false);
        directionalFields.add(compactLabeled("field.lowerBoundary", "Alt sınır (hariç)",
                directionalLowerBoundaryBox));
        directionalFields.add(compactLabeled("field.upperBoundary", "Üst sınır (hariç)",
                directionalUpperBoundaryBox));
        directionalSelector.add(directionalFields);
        JPanel hicazSelector = selectorPanel();
        hicazSelector.add(modeExplanation("analysis.augmented.explanation",
                "Hicaz dörtlüsünde 1→2→3→4 ve 4→3→2→1 bağlamları ayrı incelenecek."));
        JPanel glissandoSelector = selectorPanel();
        JPanel glissandoFields = new JPanel(new GridLayout(1, 2, 8, 0));
        glissandoFields.setOpaque(false);
        glissandoFields.add(compactLabeled("field.lowerPitch", "Alt perde", glissandoLowerPitchBox));
        glissandoFields.add(compactLabeled("field.upperPitch", "Üst perde", glissandoUpperPitchBox));
        glissandoSelector.add(glissandoFields);

        analysisSelectorCards.add(singlePitchSelector, MakamAnalysisMode.SINGLE_PITCH.name());
        analysisSelectorCards.add(intervalSelector, MakamAnalysisMode.BETWEEN_PITCHES.name());
        analysisSelectorCards.add(directionalSelector,
                MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL.name());
        analysisSelectorCards.add(hicazSelector,
                MakamAnalysisMode.HICAZ_AUGMENTED_INTERVAL.name());
        analysisSelectorCards.add(glissandoSelector,
                MakamAnalysisMode.GLISSANDO_ANALYSIS.name());
        analysisSelectorCards.setAlignmentX(Component.CENTER_ALIGNMENT);
        analysisSelectorCards.setMinimumSize(new Dimension(120, 42));
        analysisSelectorCards.setPreferredSize(new Dimension(CONTROL_CONTENT_WIDTH, 47));
        analysisSelectorCards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        panel.add(analysisSelectorCards);

        theoreticalTarget.setForeground(FREQUENCY_BLUE);
        theoreticalTarget.setName("makam-theoretical-target");
        theoreticalTarget.setFont(theoreticalTarget.getFont().deriveFont(10.5f));
        theoreticalTarget.setAlignmentX(Component.CENTER_ALIGNMENT);
        theoreticalTarget.setMinimumSize(new Dimension(120, THEORY_SUMMARY_HEIGHT));
        theoreticalTarget.setPreferredSize(new Dimension(CONTROL_CONTENT_WIDTH,
                THEORY_SUMMARY_HEIGHT));
        theoreticalTarget.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                THEORY_SUMMARY_HEIGHT + 8));
        panel.add(theoreticalTarget);
        return panel;
    }

    private JComponent buildTonicCard() {
        JPanel panel = workspaceControlCard("card.tonic", "3. Karar seçimi");
        tonicField.setEditable(false);
        panel.add(compactLabeled("field.confirmedTonic", "Onaylı karar (Hz)", tonicField));
        tonicEvidence.setForeground(MUTED);
        tonicEvidence.setFont(tonicEvidence.getFont().deriveFont(10.5f));
        tonicEvidence.setAlignmentX(Component.CENTER_ALIGNMENT);
        selectionEvidence.setForeground(MUTED);
        selectionEvidence.setFont(selectionEvidence.getFont().deriveFont(10.5f));
        selectionEvidence.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(wrapLabel(tonicEvidence));
        panel.add(wrapLabel(selectionEvidence));
        JPanel choices = new JPanel(new GridBagLayout());
        choices.setOpaque(false);
        choices.setAlignmentX(Component.CENTER_ALIGNMENT);
        choices.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        GridBagConstraints automatic = new GridBagConstraints();
        automatic.gridx = 0;
        automatic.gridy = 0;
        automatic.weightx = 1.0;
        automatic.fill = GridBagConstraints.BOTH;
        automatic.insets = new Insets(0, 0, 2, 0);
        choices.add(useAutomaticTonicButton, automatic);
        GridBagConstraints selected = new GridBagConstraints();
        selected.gridx = 0;
        selected.gridy = 1;
        selected.weightx = 1.0;
        selected.fill = GridBagConstraints.BOTH;
        selected.insets = new Insets(2, 0, 0, 0);
        choices.add(useSelectionTonicButton, selected);
        panel.add(Box.createVerticalStrut(2));
        panel.add(choices);
        return panel;
    }

    private JComponent buildTransportCard() {
        JPanel panel = new JPanel();
        panel.setName("makam-transport-controls");
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(72, WORKSPACE_BANNER_HEIGHT));
        JLabel title = localizedText(new JLabel("", SwingConstants.CENTER),
                "transport.title", "Dinleme");
        title.setForeground(MUTED);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalGlue());
        listenSelectionButton.setText("▶");
        listenSelectionButton.setToolTipText(localized("transport.playSelection", "Seçili bölgeyi oynat"));
        listenSelectionButton.getAccessibleContext().setAccessibleName(localized("transport.play", "Oynat"));
        pauseButton.setText("⏸");
        pauseButton.setToolTipText(localized("transport.pause", "Oynatmayı duraklat"));
        pauseButton.getAccessibleContext().setAccessibleName(localized("transport.pause", "Duraklat"));
        stopButton.setText("■");
        stopButton.setToolTipText(localized("transport.stop", "Oynatmayı durdur"));
        stopButton.getAccessibleContext().setAccessibleName(localized("transport.stop", "Durdur"));
        for (AbstractButton button : List.of(listenSelectionButton, pauseButton, stopButton)) {
            button.setFont(button.getFont().deriveFont(Font.BOLD, 17f));
            button.setAlignmentX(Component.CENTER_ALIGNMENT);
            button.setMaximumSize(new Dimension(44, 34));
            button.setPreferredSize(new Dimension(44, 34));
            panel.add(button);
            panel.add(Box.createVerticalStrut(4));
        }
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel workspaceControlCard(String key, String fallback) {
        JPanel panel = new JPanel();
        panel.setBackground(SURFACE);
        localizedBorderBindings.put(panel, new LocalizedBinding(key, fallback));
        applyWorkspaceCardBorder(panel, localized(key, fallback));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private static void applyWorkspaceCardBorder(JPanel panel, String title) {
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER, 1, true),
                        title, 0, 0, new Font(Font.SANS_SERIF, Font.BOLD, 13), INK),
                BorderFactory.createEmptyBorder(3, 8, 4, 8)));
    }

    private static JPanel selectorPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private JTextPane modeExplanation(String key, String fallback) {
        JTextPane pane = localizedText(centeredTextPane(""), key, fallback);
        pane.setForeground(MUTED);
        pane.setFont(pane.getFont().deriveFont(10.5f));
        pane.setPreferredSize(new Dimension(CONTROL_CONTENT_WIDTH, 35));
        pane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        return pane;
    }

    private JPanel compactLabeled(String key, String fallback, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        JLabel title = localizedText(new JLabel("", SwingConstants.CENTER), key, fallback);
        title.setForeground(MUTED);
        title.setFont(title.getFont().deriveFont(10f));
        component.setPreferredSize(new Dimension(118, 26));
        panel.add(title, BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildDirectionalResults() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        JPanel cards = new JPanel(new GridLayout(1, 4, 10, 0));
        cards.setOpaque(false);
        cards.add(directionalSummaryCard("ASCENDING", "Çıkıcı"));
        cards.add(directionalSummaryCard("DESCENDING", "İnici"));
        cards.add(directionalSummaryCard("HOLD", "Segâh kalışı"));
        cards.add(directionalSummaryCard("ALL", "Tümü"));
        cards.setPreferredSize(new Dimension(760, 116));
        panel.add(cards, BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(directionalEventTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        panel.add(tableScroll, BorderLayout.CENTER);
        return panel;
    }

    private JComponent directionalSummaryCard(String key, String title) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(new Color(253, 248, 249));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 194, 201), 1, true),
                BorderFactory.createEmptyBorder(9, 11, 9, 11)));
        JLabel heading = new JLabel(title, SwingConstants.CENTER);
        heading.setForeground(CRIMSON);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 14f));
        directionalSummaryHeadings.put(key, heading);
        JLabel values = new JLabel("", SwingConstants.CENTER);
        values.setForeground(INK);
        values.setFont(values.getFont().deriveFont(11f));
        directionalSummaryCards.put(key, values);
        card.add(heading, BorderLayout.NORTH);
        card.add(values, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(SURFACE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        status.setForeground(MUTED); bar.add(status, BorderLayout.CENTER);
        progress.setStringPainted(true); progress.setPreferredSize(new Dimension(330, 20));
        bar.add(progress, BorderLayout.EAST);
        return bar;
    }

    private void chooseAudio() {
        Path chosen = NativeFileDialogs.open(this,
                localized("dialog.openAudio.title", "MakamLab için ses dosyası aç"),
                AudioLoader.supportedFileExtensions());
        if (chosen != null) loadAudio(chosen);
    }

    private void loadAudio(Path path) {
        loadAudio(path, Double.NaN, Double.NaN);
    }

    private void loadAudio(Path path, double transferredSelectionStart,
                           double transferredSelectionEnd) {
        cancelWorker();
        sourcePath = path;
        invalidateAllAnalysis();
        AudioChannelMode mode = (AudioChannelMode) channelBox.getSelectedItem();
        setBusy("status.loadingAudio", "Ses yükleniyor…", 0);
        SwingWorker<LoadedPitch, Integer> worker = new SwingWorker<>() {
            @Override protected LoadedPitch doInBackground() throws Exception {
                AudioData loaded = audioLoader.load(path, mode);
                PitchCacheKey key = PitchCacheKey.from(loaded);
                List<PitchFrame> cached = pitchCache.get(key);
                List<PitchFrame> extracted;
                if (cached != null) {
                    extracted = cached;
                    publish(100);
                } else {
                    AudioData working = AnalysisAudioResampler.forPitchTracking(loaded);
                    extracted = new PitchTracker().track(working, value -> {
                        if (isCancelled()) throw new CancellationException();
                        publish((int) Math.round(value * 100));
                    });
                    pitchCache.put(key, List.copyOf(extracted));
                }
                TonicEstimator.Estimate estimate = new TonicEstimator().estimate(extracted);
                return new LoadedPitch(loaded, List.copyOf(extracted), estimate, cached != null);
            }

            @Override protected void process(List<Integer> chunks) {
                int value = chunks.get(chunks.size() - 1);
                setBusy("status.trackingPitch", "Perde izi çıkarılıyor… %{0}", value, value);
            }

            @Override protected void done() {
                if (activeWorker != this) return;
                try {
                    LoadedPitch loaded = get();
                    audio = loaded.audio(); frames = loaded.frames(); automaticTonic = loaded.tonic();
                    sourceName.setText(audio.name()); sourceName.setCaretPosition(0);
                    resultSourceTitle.setText(audio.name());
                    resultSourceTitle.setToolTipText(audio.name());
                    refreshSourceDetailsLocalization();
                    refreshTonicEvidenceLocalization();
                    refreshPreview(automaticTonic.hertz());
                    if (Double.isFinite(transferredSelectionStart)
                            && Double.isFinite(transferredSelectionEnd)) {
                        double safeStart = Math.max(0.0, Math.min(audio.durationSeconds(),
                                Math.min(transferredSelectionStart, transferredSelectionEnd)));
                        double safeEnd = Math.max(safeStart, Math.min(audio.durationSeconds(),
                                Math.max(transferredSelectionStart, transferredSelectionEnd)));
                        if (safeEnd - safeStart >= 0.02) selectionChanged(safeStart, safeEnd);
                    }
                    if (loaded.cached()) setStatus("status.pitchReadyCached",
                            "Perde izi hazır • önbellekten");
                    else setStatus("status.pitchReady", "Perde izi hazır");
                } catch (CancellationException ignored) {
                    return;
                } catch (Exception exception) {
                    showError("dialog.error.audioDecode", "Ses çözümlenemedi", exception);
                } finally {
                    if (activeWorker == this) activeWorker = null;
                    setBusy(null, null, 0); updateButtons();
                }
            }
        };
        activeWorker = worker;
        updateButtons();
        worker.execute();
    }

    private void selectionChanged(double start, double end) {
        if (audio == null) return;
        if (!Double.isFinite(start) || !Double.isFinite(end) || Math.abs(end - start) < 0.02) return;
        Candidate candidate = robustCandidate(frames, start, end);
        // A short decay can be audible and compact although the global pitch
        // track has no retained frame in that exact interval.  In that case,
        // re-examine only the PCM that the user deliberately dragged over.  The
        // result stays below the report-quality confidence threshold and never
        // becomes the tonic until the user explicitly presses the confirmation
        // button.  A NaN reference is intentional: the automatic candidate must
        // not silently fold the user's selected physical frequency by an octave.
        if (candidate == null) {
            ManualTonicSelectionEstimator.Estimate manual =
                    new ManualTonicSelectionEstimator().estimate(audio, start, end, Double.NaN);
            if (manual != null) {
                candidate = new Candidate(manual.hertz(), manual.confidence(),
                        manual.frameCount(), true);
            }
        }
        selectionStart = start; selectionEnd = end;
        evidencePanel.setSelection(start, end);
        if (candidate == null) {
            selectedTonicHertz = Double.NaN;
            selectedTonicManualFallback = false;
            selectionEvidence.setText(localized("tonic.evidence.insufficient",
                    "Seçimde yeterli güvenilir perde bulunamadı"));
        } else {
            selectedTonicHertz = candidate.hertz();
            selectedTonicConfidence = candidate.confidence();
            selectedTonicManualFallback = candidate.manualFallback();
            String interval = "";
            if (Double.isFinite(confirmedTonicHertz) && confirmedTonicHertz > 0.0) {
                double cent = 1200.0 * Math.log(candidate.hertz() / confirmedTonicHertz) / Math.log(2.0);
                interval = " • " + i18n.formatSignedNumber(cent, 2, 2) + " C / "
                        + i18n.formatSignedNumber(cent / HC_CENT, 3, 3) + " Hc";
            }
            String description = i18n.textOrDefault("tonic.evidence.selection",
                    "Seçim {0}–{1} sn: {2} Hz{3} • güven {4} • {5} çerçeve",
                    i18n.formatNumber(start, 2, 2), i18n.formatNumber(end, 2, 2),
                    i18n.formatNumber(candidate.hertz(), 3, 3), interval,
                    i18n.formatPercent(candidate.confidence(), 0),
                    i18n.formatInteger(candidate.count()));
            if (candidate.manualFallback()) {
                description += " • " + localized("result.hicaz.audit.lowConfidence",
                        "Düşük güven");
            }
            selectionEvidence.setText(description);
        }
        updateButtons();
    }

    private void refreshTonicEvidenceLocalization() {
        if (Double.isFinite(confirmedTonicHertz)) {
            tonicField.setText(i18n.formatNumber(confirmedTonicHertz, 3, 3));
        }
        if (audio == null || automaticTonic == null) {
            tonicEvidence.setText(localized("tonic.evidence.noAudio", "Önce ses dosyasını açın"));
        } else {
            tonicEvidence.setText(i18n.textOrDefault("tonic.evidence.finalCandidate",
                    "Son kararlı ses adayı: {0} Hz • güven {1}",
                    i18n.formatNumber(automaticTonic.hertz(), 3, 3),
                    i18n.formatPercent(automaticTonic.confidence(), 0)));
        }
        if (audio != null && Double.isFinite(selectionStart) && Double.isFinite(selectionEnd)
                && selectionEnd - selectionStart >= .02) {
            selectionChanged(selectionStart, selectionEnd);
        } else {
            selectionEvidence.setText(localized("tonic.evidence.selectRegion",
                    "Grafikte bir ses bölgesi seçebilirsiniz"));
        }
    }

    private void refreshSourceDetailsLocalization() {
        if (audio == null) return;
        sourceDetails.setText(i18n.textOrDefault("source.details", "{0} sn • {1} kHz • {2}",
                i18n.formatNumber(audio.durationSeconds(), 1, 1),
                i18n.formatNumber(audio.sampleRate() / 1000.0, 1, 1),
                audio.channelDescription()));
    }

    private void confirmAutomaticTonic() {
        if (automaticTonic == null) return;
        confirmedTonicHertz = automaticTonic.hertz();
        tonicConfidence = automaticTonic.confidence();
        tonicStartSeconds = Double.NaN; tonicEndSeconds = Double.NaN;
        tonicMethodKey = "tonic.method.finalCandidate";
        tonicMethodFallback = "Son kararlı sesten otomatik öneri, kullanıcı onaylı";
        tonicConfirmed();
    }

    private void confirmSelectedTonic() {
        if (!Double.isFinite(selectedTonicHertz)) return;
        confirmedTonicHertz = selectedTonicHertz;
        tonicConfidence = selectedTonicConfidence;
        tonicStartSeconds = selectionStart; tonicEndSeconds = selectionEnd;
        tonicMethodKey = "tonic.method.selectionMedian";
        tonicMethodFallback = selectedTonicManualFallback
                ? "Grafikte seçilen bölgenin düşük güvenli medyanı, kullanıcı onaylı"
                : "Grafikte seçilen bölgenin sağlam medyanı, kullanıcı onaylı";
        tonicConfirmed();
    }

    private void tonicConfirmed() {
        tonicField.setText(i18n.formatNumber(confirmedTonicHertz, 3, 3));
        invalidateTargetAnalysis();
        refreshTheoryText();
        refreshPreview(confirmedTonicHertz);
        setStatus("status.tonicConfirmed", "Karar onaylandı");
        updateButtons();
    }

    private void analyzeTarget() {
        if (audio == null) {
            showWorkflowMessage("validation.openAudio",
                    "Önce bir ses dosyası açın veya düzenlenmiş kaydı ana sayfaya aktarın.");
            return;
        }
        if (frames.isEmpty()) {
            showWorkflowMessage("validation.waitForPitch",
                    "Perde izi henüz hazır değil. Ses çözümlemesinin tamamlanmasını bekleyin.");
            return;
        }
        if (!Double.isFinite(confirmedTonicHertz)) {
            showWorkflowMessage("validation.selectTonic",
                    "Önce dalga/F0 alanından karar sesini seçip ‘Seçili bölgeyi karar olarak kullan’ düğmesine basın.");
            return;
        }
        // Freeze the exact user-confirmed physical frequency for the complete
        // run. Automatic suggestions and later UI changes must never replace,
        // snap or octave-fold this anchor while a calculation is in progress.
        final double tonicForRun = confirmedTonicHertz;
        TheoryCatalog.Makam makam = selectedMakam();
        MakamAnalysisMode mode = selectedAnalysisMode();
        TheoryCatalog.Note target = mode == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL
                ? theory.note("segah") : selectedTarget();
        IntervalBounds interval = selectedIntervalBounds();
        IntervalBounds directionalInterval = selectedDirectionalBounds();
        GlissandoPair glissandoPair = selectedGlissandoPair();
        if (makam == null || (mode == MakamAnalysisMode.SINGLE_PITCH && target == null)) {
            showWorkflowMessage("validation.selectTarget",
                    "Önce makamı ve incelenecek perdeyi seçin.");
            return;
        }
        if (mode == MakamAnalysisMode.BETWEEN_PITCHES && interval == null) {
            showWorkflowMessage("validation.distinctBoundaries",
                    "Perdeler arası analiz için birbirinden farklı iki sınır perdesi seçin.");
            return;
        }
        if (mode == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL
                && directionalInterval == null) {
            showWorkflowMessage("validation.distinctBoundaries",
                    "İnici-çıkıcı analiz için birbirinden farklı iki sınır perdesi seçin.");
            return;
        }
        if (mode == MakamAnalysisMode.GLISSANDO_ANALYSIS && glissandoPair == null) {
            showWorkflowMessage("validation.distinctGlissandoPitches",
                    "Glissando analizi için birbirinden farklı iki perde seçin.");
            return;
        }
        // Freeze the selected reference inventory for this run. Reports use
        // this immutable snapshot rather than rereading mutable preferences.
        final FirstFourProfileSelection profileSelectionForRun =
                firstFourProfilePreferences.selectionSnapshot();
        setBusy("status.analysis.inProgress", switch (mode) {
            case SINGLE_PITCH -> "Kanonik 53-Hc olayları hazırlanıyor…";
            case BETWEEN_PITCHES -> "Sınırlar dışındaki perde olayları hazırlanıyor…";
            case USSAK_SEGAH_DIRECTIONAL -> directionalInterval.lower() + "–"
                    + directionalInterval.upper() + " arasındaki Segâh adayları hazırlanıyor…";
            case HICAZ_AUGMENTED_INTERVAL -> "Hicaz dörtlüsü derece olayları hazırlanıyor…";
            case GLISSANDO_ANALYSIS -> "Seçili iki perde arasında glissandolar aranıyor…";
        }, 0);
        SwingWorker<AnalyzedTarget, Integer> worker = new SwingWorker<>() {
            @Override protected AnalyzedTarget doInBackground() {
                publish(5);
                AnalysisConfig config = switch (mode) {
                    case SINGLE_PITCH, USSAK_SEGAH_DIRECTIONAL ->
                            config(makam, target, tonicForRun, false);
                    case BETWEEN_PITCHES ->
                            intervalConfig(makam, interval, tonicForRun, false);
                    case HICAZ_AUGMENTED_INTERVAL ->
                            hicazConfig(makam, tonicForRun, false);
                    case GLISSANDO_ANALYSIS ->
                            glissandoConfig(makam, glissandoPair, tonicForRun, false);
                };
                AnalysisReport broad = mode == MakamAnalysisMode.SINGLE_PITCH
                        ? new DirectionalIntonationAnalyzer().analyze(audio, frames, config)
                        : null;
                publish(35);
                List<PitchEvent> all = new AllPitchEventAnalyzer().analyze(frames, config, theory, makam);
                List<PitchClassInterval.RegisterSelection> intervalRegisters =
                        mode == MakamAnalysisMode.BETWEEN_PITCHES
                                ? PitchClassInterval.selectViableRegisters(all,
                                hertzToCent(tonicForRun),
                                interval.lowerCent(), interval.upperCent(), config)
                                : List.of();
                publish(62);
                UssakSegahDirectionalStudyResult directional =
                        mode == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL
                                ? new UssakSegahDirectionalAnalyzer().analyze(frames, all, config,
                                theory, makam, tonicForRun,
                                directionalInterval.lower(), directionalInterval.upper()) : null;
                HicazAugmentedIntervalStudyResult hicaz =
                        mode == MakamAnalysisMode.HICAZ_AUGMENTED_INTERVAL
                                ? hicazAnalyzer.analyze(all, tonicForRun) : null;
                PitchPairGlissandoStudyResult glissando = null;
                if (mode == MakamAnalysisMode.GLISSANDO_ANALYSIS) {
                    PitchPairGlissandoRequest request = PitchPairGlissandoRequest.academicDefaults(
                            makam.toString(), tonicForRun,
                            glissandoPair.lower().toString(), glissandoPair.lowerCent(),
                            glissandoPair.upper().toString(), glissandoPair.upperCent(),
                            GlissandoScopeMode.OPEN_INTERVAL);
                    glissando = glissandoAnalyzer.analyze(frames, request);
                }
                publish(88);
                final HicazAugmentedIntervalStudyResult finalHicaz = hicaz;
                List<PitchEvent> targetEvents = switch (mode) {
                    case SINGLE_PITCH, USSAK_SEGAH_DIRECTIONAL -> all.stream()
                            .filter(value -> PitchClassInterval.samePitchClass(
                                    value.theoreticalRelativeCent(),
                                    theory.relativeCent(makam, target))).toList();
                    case BETWEEN_PITCHES -> {
                        double tonicAbsoluteCent = hertzToCent(tonicForRun);
                        java.util.Set<Integer> selectedOffsets = intervalRegisters.stream()
                                .map(PitchClassInterval.RegisterSelection::octaveOffset)
                                .collect(java.util.stream.Collectors.toCollection(
                                        java.util.LinkedHashSet::new));
                        yield all.stream().filter(value -> {
                                    int offset = PitchClassInterval.registerOffset(value,
                                            tonicAbsoluteCent, interval.lowerCent(), interval.upperCent());
                                    return selectedOffsets.contains(offset)
                                            && PitchClassInterval.containsInRegister(value,
                                            tonicAbsoluteCent, interval.lowerCent(), interval.upperCent(),
                                            offset, config);
                                })
                                .map(value -> {
                                    int offset = PitchClassInterval.registerOffset(value,
                                            tonicAbsoluteCent, interval.lowerCent(), interval.upperCent());
                                    return PitchClassInterval.normalizeEvent(value,
                                            offset, theory, makam);
                                })
                                .map(value -> IntervalPitchNaming.relabelForDisplay(value, config))
                                .toList();
                    }
                    case HICAZ_AUGMENTED_INTERVAL -> {
                        java.util.Set<Integer> sourceIndexes = finalHicaz.events().stream()
                                .map(value -> value.sourceEventIndex())
                                .collect(java.util.stream.Collectors.toSet());
                        yield all.stream().filter(value -> sourceIndexes.contains(
                                value.occurrence().index())).toList();
                    }
                    case GLISSANDO_ANALYSIS -> all.stream()
                            .filter(value -> glissandoPair.containsObserved(value,
                                    tonicForRun)).toList();
                };
                List<PitchOccurrence> exactOccurrences = targetEvents.stream()
                        .map(PitchEvent::occurrence).toList();
                AnalysisReport exact = new AnalysisReport(audio, config, List.copyOf(frames),
                        exactOccurrences, broad == null ? List.of() : broad.glissandi(), ZonedDateTime.now());
                publish(100);
                return new AnalyzedTarget(exact, all, targetEvents, directional, hicaz, glissando,
                        intervalRegisters.stream()
                                .map(PitchClassInterval.RegisterSelection::octaveOffset).toList());
            }

            @Override protected void process(List<Integer> chunks) {
                int value = chunks.get(chunks.size() - 1);
                setBusy("status.analysis.inProgress", switch (mode) {
                    case SINGLE_PITCH -> "Hedef perde analiz ediliyor…";
                    case BETWEEN_PITCHES -> "Perdeler arası açık alan analiz ediliyor…";
                    case USSAK_SEGAH_DIRECTIONAL ->
                            "Çıkıcı, inici ve doğrudan Segâh kalışları sınıflandırılıyor…";
                    case HICAZ_AUGMENTED_INTERVAL ->
                            "Hicaz dereceleri ve gerçek 2↔3 artık aralıkları ölçülüyor…";
                    case GLISSANDO_ANALYSIS ->
                            "Çıkıcı, inici ve geri dönüşlü glissandolar ölçülüyor…";
                }, value);
            }

            @Override protected void done() {
                if (activeWorker != this) return;
                try {
                    AnalyzedTarget value = get();
                    report = value.report(); allPitchEvents = value.all();
                    analysisProfileSelection = profileSelectionForRun;
                    targetPitchEvents = value.target();
                    directionalSegahStudy = value.directionalStudy();
                    hicazStudy = value.hicazStudy();
                    glissandoStudy = value.glissandoStudy();
                    evidencePanel.setData(audio, report, null);
                    if (mode == MakamAnalysisMode.BETWEEN_PITCHES) {
                        if (value.intervalOctaveOffsets().size() == 1) {
                            double tonicAbsoluteCent = hertzToCent(tonicForRun);
                            int offset = value.intervalOctaveOffsets().get(0);
                            evidencePanel.setExclusivePitchRange(
                                    tonicAbsoluteCent + interval.lowerCent()
                                            + offset * PitchClassInterval.OCTAVE_CENT,
                                    tonicAbsoluteCent + interval.upperCent()
                                            + offset * PitchClassInterval.OCTAVE_CENT);
                        } else {
                            // A single highlighted physical band would hide or
                            // misrepresent a second coherent performance register.
                            evidencePanel.clearExclusivePitchRange();
                        }
                    } else if (mode == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL) {
                        double tonicAbsoluteCent = hertzToCent(tonicForRun);
                        evidencePanel.setExclusivePitchRange(
                                tonicAbsoluteCent + directionalInterval.lowerCent(),
                                tonicAbsoluteCent + directionalInterval.upperCent());
                    } else if (mode == MakamAnalysisMode.HICAZ_AUGMENTED_INTERVAL) {
                        double tonicAbsoluteCent = hertzToCent(tonicForRun);
                        evidencePanel.setExclusivePitchRange(tonicAbsoluteCent,
                                tonicAbsoluteCent + 22.0 * HC_CENT);
                    } else if (mode == MakamAnalysisMode.GLISSANDO_ANALYSIS) {
                        double tonicAbsoluteCent = hertzToCent(tonicForRun);
                        evidencePanel.setExclusivePitchRange(
                                tonicAbsoluteCent + glissandoPair.lowerCent(),
                                tonicAbsoluteCent + glissandoPair.upperCent());
                    } else {
                        evidencePanel.clearExclusivePitchRange();
                    }
                    evidencePanel.setLayers(true, false, false, false);
                    editorPanel.setData(report, allPitchEvents);
                    eventModel.setRows(targetPitchEvents);
                    directionalEventModel.setRows(directionalSegahStudy == null
                            ? List.of() : directionalSegahStudy.events());
                    hicazResultsPanel.setResult(hicazStudy);
                    glissandoResultsPanel.setResult(glissandoStudy);
                    refreshSummary();
                    switch (mode) {
                        case SINGLE_PITCH -> setPitchCountStatus(false);
                        case BETWEEN_PITCHES -> setPitchCountStatus(true);
                        case USSAK_SEGAH_DIRECTIONAL -> setStatus("status.result.directional",
                                "Segâh: {0} güvenilir yönsel/kalış olayı",
                                i18n.formatInteger(directionalSegahStudy == null ? 0
                                        : directionalSegahStudy.includedEvents().size()));
                        case HICAZ_AUGMENTED_INTERVAL -> setStatus("status.result.hicaz",
                                "Hicaz dörtlüsü: {0} güvenilir yönsel cümle • {1} doğrudan 2↔3 ölçümü",
                                i18n.formatInteger(hicazStudy == null ? 0
                                        : hicazStudy.includedPhrases().size()),
                                i18n.formatInteger(hicazStudy == null ? 0
                                        : hicazStudy.directIntervals().stream()
                                        .filter(value1 -> value1.included()).count()));
                        case GLISSANDO_ANALYSIS -> setStatus("status.result.glissando",
                                "{0}–{1}: {2} güvenilir glissando • {3} denetim adayı",
                                glissandoPair.lower(), glissandoPair.upper(),
                                i18n.formatInteger(glissandoStudy == null ? 0
                                        : glissandoStudy.primaryEvents().size()),
                                i18n.formatInteger(glissandoStudy == null ? 0
                                        : glissandoStudy.auditEvents().size()));
                    }
                } catch (Exception exception) {
                    exportAfterAnalysis = false;
                    showError("dialog.error.analysis", "Hedef perde analizi tamamlanamadı", exception);
                } finally {
                    if (activeWorker == this) activeWorker = null;
                    boolean shouldExport = exportAfterAnalysis && report != null;
                    exportAfterAnalysis = false;
                    setBusy(null, null, 0); updateButtons();
                    if (shouldExport) SwingUtilities.invokeLater(MakamFrame.this::exportWord);
                }
            }
        };
        activeWorker = worker;
        updateButtons();
        worker.execute();
    }

    private void refreshSummary() {
        MakamAnalysisMode mode = selectedAnalysisMode();
        boolean directionalMode = mode == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL;
        resultLayout.show(resultCards, resultCard(mode));
        if (report == null) {
            summary.setText(localized("result.waiting", "Analiz sonucu bekleniyor"));
            if (directionalMode) updateDirectionalSummaryCards(null);
            return;
        }
        if (directionalMode) {
            updateDirectionalSummaryCards(directionalSegahStudy);
            int all = directionalSegahStudy == null ? 0 : directionalSegahStudy.events().size();
            int included = directionalSegahStudy == null ? 0
                    : directionalSegahStudy.includedEvents().size();
            IntervalBounds directionalInterval = selectedDirectionalBounds();
            Object lowerLabel = directionalInterval == null ? "—" : directionalInterval.lower();
            Object upperLabel = directionalInterval == null ? "—" : directionalInterval.upper();
            summary.setText(i18n.textOrDefault("result.summary.directional",
                    "{0} referansı: {3} Hc / {4} C / {5} Hz • {1} < F0 < {2} • {6} aday, {7} ana istatistikte",
                    "Segâh", lowerLabel, upperLabel,
                    i18n.formatSignedNumber(directionalSegahStudy == null ? 0.0
                            : directionalSegahStudy.theoreticalSegahHc(), 3, 3),
                    i18n.formatSignedNumber(directionalSegahStudy == null ? 0.0
                            : directionalSegahStudy.theoreticalSegahCent(), 2, 2),
                    i18n.formatNumber(directionalSegahStudy == null ? 0.0
                            : directionalSegahStudy.theoreticalSegahHertz(), 3, 3),
                    i18n.formatInteger(all), i18n.formatInteger(included)));
            return;
        }
        if (mode == MakamAnalysisMode.HICAZ_AUGMENTED_INTERVAL) {
            long directIncluded = hicazStudy == null ? 0 : hicazStudy.directIntervals().stream()
                    .filter(value -> value.included()).count();
            int ascending = hicazStudy == null ? 0 : (int) hicazStudy.includedPhrases().stream()
                    .filter(value -> value.direction()
                            == com.makambox.studio.study.hicaz.HicazPhraseDirection.ASCENDING)
                    .count();
            int descending = hicazStudy == null ? 0 : (int) hicazStudy.includedPhrases().stream()
                    .filter(value -> value.direction()
                            == com.makambox.studio.study.hicaz.HicazPhraseDirection.DESCENDING)
                    .count();
            summary.setText(i18n.textOrDefault("result.summary.augmented",
                    "{0} dörtlüsü 0–5–17–22 Hc • kuramsal artık aralık 12 Hc / {1} C "
                            + "• {2} çıkıcı, {3} inici cümle • {4} doğrudan 2↔3 ölçümü",
                    "Hicaz", i18n.formatNumber(12.0 * HC_CENT, 2, 2),
                    i18n.formatInteger(ascending), i18n.formatInteger(descending),
                    i18n.formatInteger(directIncluded)));
            return;
        }
        if (mode == MakamAnalysisMode.GLISSANDO_ANALYSIS) {
            GlissandoPair pair = selectedGlissandoPair();
            int ascending = glissandoStudy == null ? 0
                    : glissandoStudy.ascendingStatistics().count();
            int descending = glissandoStudy == null ? 0
                    : glissandoStudy.descendingStatistics().count();
            int audit = glissandoStudy == null ? 0 : glissandoStudy.auditEvents().size();
            summary.setText(i18n.textOrDefault("result.summary.glissando",
                    "{0}–{1} • kuramsal {2} C / {3} Hc • {4} çıkıcı, "
                            + "{5} inici glissando • {6} denetim adayı",
                    pair == null ? "—" : pair.lower(), pair == null ? "—" : pair.upper(),
                    i18n.formatNumber(pair == null ? 0.0 : pair.spanCent(), 2, 2),
                    i18n.formatNumber(pair == null ? 0.0 : pair.spanCent() / HC_CENT, 3, 3),
                    i18n.formatInteger(ascending), i18n.formatInteger(descending),
                    i18n.formatInteger(audit)));
            return;
        }
        List<PitchOccurrence> measured = measuredOccurrencesForSelectedMode();
        List<PitchOccurrence> stable = stableMeasuredOccurrences(measured);
        if (selectedAnalysisMode() == MakamAnalysisMode.BETWEEN_PITCHES) {
            IntervalBounds interval = selectedIntervalBounds();
            double totalDuration = measured.stream().mapToDouble(PitchOccurrence::durationSeconds).sum();
            String distribution = targetPitchEvents.stream()
                    .collect(java.util.stream.Collectors.groupingBy(PitchEvent::noteName,
                            LinkedHashMap::new, java.util.stream.Collectors.counting()))
                    .entrySet().stream().limit(5)
                    .map(value -> value.getKey() + " " + value.getValue())
                    .collect(java.util.stream.Collectors.joining(" • "));
            summary.setText(i18n.textOrDefault("result.summary.between",
                    "{0}–{1} açık aralığı • sınırlar hariç • {2} temas, {3} ölçülebilir, "
                            + "{4} kararlı • {5} sn{6}",
                    interval == null ? "—" : interval.lower(),
                    interval == null ? "—" : interval.upper(),
                    i18n.formatInteger(targetPitchEvents.size()), i18n.formatInteger(measured.size()),
                    i18n.formatInteger(stable.size()), i18n.formatNumber(totalDuration, 2, 2),
                    distribution.isBlank() ? "" : " • " + distribution));
            return;
        }
        GroupStatistics main = GroupStatistics.from(measured, HC_CENT);
        GroupStatistics robust = GroupStatistics.from(stable, HC_CENT);
        long pest = measured.stream().filter(value -> value.deviationCent() < 0).count();
        long dik = measured.size() - pest;
        summary.setText(i18n.textOrDefault("result.summary.single",
                "Kuramsal {0} Hc / {1} C • {2} temas, {3} ölçülebilir, {4} kararlı • "
                        + "medyan {5} C ({6} Hc) • kararlı medyan {7} C • pest/dik {8}/{9}",
                i18n.formatNumber(report.config().targetRelativeCent() / HC_CENT, 3, 3),
                i18n.formatNumber(report.config().targetRelativeCent(), 2, 2),
                i18n.formatInteger(report.occurrences().size()), i18n.formatInteger(measured.size()),
                i18n.formatInteger(stable.size()), i18n.formatSignedNumber(main.medianDeviationCent(), 2, 2),
                i18n.formatSignedNumber(main.medianDeviationCent() / HC_CENT, 3, 3),
                i18n.formatSignedNumber(robust.medianDeviationCent(), 2, 2),
                i18n.formatInteger(pest), i18n.formatInteger(dik)));
    }

    /**
     * Shows the same three evidence layers as the result banner in the status area.
     * No event is reclassified here: the method only reports the already-produced
     * canonical contacts, measurable subset and stable subset.
     */
    private void setPitchCountStatus(boolean openInterval) {
        if (report == null) return;
        List<PitchOccurrence> measured = measuredOccurrencesForSelectedMode();
        List<PitchOccurrence> stable = stableMeasuredOccurrences(measured);
        if (openInterval) {
            IntervalBounds interval = selectedIntervalBounds();
            double totalDuration = measured.stream()
                    .mapToDouble(PitchOccurrence::durationSeconds).sum();
            String distribution = targetPitchEvents.stream()
                    .collect(java.util.stream.Collectors.groupingBy(PitchEvent::noteName,
                            LinkedHashMap::new, java.util.stream.Collectors.counting()))
                    .entrySet().stream().limit(5)
                    .map(value -> value.getKey() + " " + value.getValue())
                    .collect(java.util.stream.Collectors.joining(" • "));
            setStatus("result.summary.between",
                    "{0}–{1} açık aralığı • sınırlar hariç • {2} temas, {3} ölçülebilir, "
                            + "{4} kararlı • {5} sn{6}",
                    interval == null ? "—" : interval.lower(),
                    interval == null ? "—" : interval.upper(),
                    i18n.formatInteger(targetPitchEvents.size()),
                    i18n.formatInteger(measured.size()), i18n.formatInteger(stable.size()),
                    i18n.formatNumber(totalDuration, 2, 2),
                    distribution.isBlank() ? "" : " • " + distribution);
            return;
        }
        GroupStatistics main = GroupStatistics.from(measured, HC_CENT);
        GroupStatistics robust = GroupStatistics.from(stable, HC_CENT);
        long pest = measured.stream().filter(value -> value.deviationCent() < 0).count();
        long dik = measured.size() - pest;
        setStatus("result.summary.single",
                "Kuramsal {0} Hc / {1} C • {2} temas, {3} ölçülebilir, {4} kararlı • "
                        + "medyan {5} C ({6} Hc) • kararlı medyan {7} C • pest/dik {8}/{9}",
                i18n.formatNumber(report.config().targetRelativeCent() / HC_CENT, 3, 3),
                i18n.formatNumber(report.config().targetRelativeCent(), 2, 2),
                i18n.formatInteger(targetPitchEvents.size()), i18n.formatInteger(measured.size()),
                i18n.formatInteger(stable.size()),
                i18n.formatSignedNumber(main.medianDeviationCent(), 2, 2),
                i18n.formatSignedNumber(main.medianDeviationCent() / HC_CENT, 3, 3),
                i18n.formatSignedNumber(robust.medianDeviationCent(), 2, 2),
                i18n.formatInteger(pest), i18n.formatInteger(dik));
    }

    private static List<PitchOccurrence> stableMeasuredOccurrences(
            List<PitchOccurrence> measured) {
        return measured.stream()
                .filter(value -> value.stabilityCent() <= STABLE_SPREAD_HC * HC_CENT + 1.0e-9)
                .filter(value -> value.confidence() + 1.0e-9
                        >= SinglePitchStudyService.STABLE_EVENT_MINIMUM_CONFIDENCE)
                .toList();
    }

    private List<PitchOccurrence> measuredOccurrencesForSelectedMode() {
        if (report == null) return List.of();
        if (selectedAnalysisMode() == MakamAnalysisMode.BETWEEN_PITCHES) {
            return report.occurrences().stream()
                    .filter(IntervalEventTier::statisticallyMeasurable).toList();
        }
        return report.measuredOccurrences();
    }

    private static String resultCard(MakamAnalysisMode mode) {
        return switch (mode) {
            case USSAK_SEGAH_DIRECTIONAL -> "DIRECTIONAL";
            case HICAZ_AUGMENTED_INTERVAL -> "HICAZ";
            case GLISSANDO_ANALYSIS -> "GLISSANDO";
            default -> "STANDARD";
        };
    }

    private void updateDirectionalSummaryCards(UssakSegahDirectionalStudyResult study) {
        updateDirectionalSummaryCard("ASCENDING", study == null ? null
                : study.statistics(UssakSegahUsageClass.ASCENDING_PHRASE));
        updateDirectionalSummaryCard("DESCENDING", study == null ? null
                : study.statistics(UssakSegahUsageClass.DESCENDING_PHRASE));
        updateDirectionalSummaryCard("HOLD", study == null ? null
                : study.statistics(UssakSegahUsageClass.DIRECT_HOLD));
        updateDirectionalSummaryCard("ALL", study == null ? null : study.overallStatistics());
    }

    private void updateDirectionalSummaryCard(String key, StudyStatistics statistics) {
        JLabel label = directionalSummaryCards.get(key);
        if (label == null) return;
        if (statistics == null || statistics.count() == 0) {
            label.setText("<html><div style='text-align:center'>n: 0<br>"
                    + localized("common.average", "Ortalama") + ": —<br>"
                    + localized("common.median", "Medyan") + ": —<br>"
                    + localized("report.weightedMean", "Süre ağırlıklı")
                    + ": —</div></html>");
            return;
        }
        label.setText("<html><div style='text-align:center'>n: "
                + i18n.formatInteger(statistics.count()) + "<br>"
                + localized("common.average", "Ortalama") + ": "
                + i18n.formatSignedNumber(statistics.meanDeviationCent(), 2, 2)
                + " C / " + i18n.formatSignedNumber(statistics.meanDeviationHc(), 3, 3)
                + " Hc<br>" + localized("common.median", "Medyan") + ": "
                + i18n.formatSignedNumber(statistics.medianDeviationCent(), 2, 2)
                + " C / " + i18n.formatSignedNumber(statistics.medianDeviationHc(), 3, 3)
                + " Hc<br>" + localized("report.weightedMean", "Süre ağırlıklı") + ": "
                + i18n.formatSignedNumber(statistics.durationWeightedDeviationCent(), 2, 2)
                + " C / " + i18n.formatSignedNumber(statistics.durationWeightedDeviationHc(), 3, 3)
                + " Hc</div></html>");
    }

    private void refreshPreview(double tonic) {
        if (audio == null || frames.isEmpty() || !Double.isFinite(tonic)) return;
        TheoryCatalog.Makam makam = selectedMakam();
        MakamAnalysisMode mode = selectedAnalysisMode();
        TheoryCatalog.Note target = mode == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL
                ? theory.note("segah") : selectedTarget();
        IntervalBounds interval = selectedIntervalBounds();
        IntervalBounds directionalInterval = selectedDirectionalBounds();
        GlissandoPair glissandoPair = selectedGlissandoPair();
        if (makam == null || (mode == MakamAnalysisMode.SINGLE_PITCH && target == null)
                || (mode == MakamAnalysisMode.BETWEEN_PITCHES && interval == null)
                || (mode == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL
                && directionalInterval == null)
                || (mode == MakamAnalysisMode.GLISSANDO_ANALYSIS
                && glissandoPair == null)) return;
        AnalysisConfig config = switch (mode) {
            case SINGLE_PITCH, USSAK_SEGAH_DIRECTIONAL -> config(makam, target, tonic, true);
            case BETWEEN_PITCHES -> intervalConfig(makam, interval, tonic, true);
            case HICAZ_AUGMENTED_INTERVAL -> hicazConfig(makam, tonic, true);
            case GLISSANDO_ANALYSIS -> glissandoConfig(makam, glissandoPair, tonic, true);
        };
        AnalysisReport preview = new AnalysisReport(audio, config, frames, List.of(), List.of(), ZonedDateTime.now());
        evidencePanel.setData(audio, preview, null);
        if (mode == MakamAnalysisMode.BETWEEN_PITCHES) {
            double tonicAbsoluteCent = hertzToCent(tonic);
            evidencePanel.setExclusivePitchRange(tonicAbsoluteCent + interval.lowerCent(),
                    tonicAbsoluteCent + interval.upperCent());
        } else if (mode == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL) {
            double tonicAbsoluteCent = hertzToCent(tonic);
            evidencePanel.setExclusivePitchRange(
                    tonicAbsoluteCent + directionalInterval.lowerCent(),
                    tonicAbsoluteCent + directionalInterval.upperCent());
        } else if (mode == MakamAnalysisMode.HICAZ_AUGMENTED_INTERVAL) {
            double tonicAbsoluteCent = hertzToCent(tonic);
            evidencePanel.setExclusivePitchRange(tonicAbsoluteCent,
                    tonicAbsoluteCent + 22.0 * HC_CENT);
        } else if (mode == MakamAnalysisMode.GLISSANDO_ANALYSIS) {
            double tonicAbsoluteCent = hertzToCent(tonic);
            evidencePanel.setExclusivePitchRange(tonicAbsoluteCent + glissandoPair.lowerCent(),
                    tonicAbsoluteCent + glissandoPair.upperCent());
        } else {
            evidencePanel.clearExclusivePitchRange();
        }
        evidencePanel.setLayers(true, false, false, false);
        refreshTheoryText();
    }

    private void refreshTheoryText() {
        TheoryCatalog.Makam makam = selectedMakam();
        TheoryCatalog.Note target = selectedTarget();
        if (makam == null) {
            theoreticalTarget.setText(localized("theory.target.empty", "Kuramsal hedef: —"));
            return;
        }
        if (selectedAnalysisMode() == MakamAnalysisMode.HICAZ_AUGMENTED_INTERVAL) {
            theoreticalTarget.setText(i18n.textOrDefault("theory.augmented.reference",
                    "{0} dörtlüsü referansı: 0–5–17–22 Hc • artık aralık 2↔3",
                    "Hicaz"));
            return;
        }
        if (selectedAnalysisMode() == MakamAnalysisMode.GLISSANDO_ANALYSIS) {
            TheoryCatalog.Note first = (TheoryCatalog.Note) glissandoLowerPitchBox.getSelectedItem();
            TheoryCatalog.Note second = (TheoryCatalog.Note) glissandoUpperPitchBox.getSelectedItem();
            if (first == null || second == null || first.key().equals(second.key())) {
                theoreticalTarget.setText(localized("theory.glissando.distinct",
                        "Glissando için birbirinden farklı iki perde seçin"));
                return;
            }
            double firstCent = theory.relativeCent(makam, first);
            double secondCent = theory.relativeCent(makam, second);
            TheoryCatalog.Note lower = firstCent <= secondCent ? first : second;
            TheoryCatalog.Note upper = firstCent <= secondCent ? second : first;
            double spanCent = Math.abs(secondCent - firstCent);
            theoreticalTarget.setText(i18n.textOrDefault("theory.glissando.span",
                    "{0} ↔ {1} • kuramsal genişlik {2} C / {3} Hc",
                    lower, upper, i18n.formatNumber(spanCent, 2, 2),
                    i18n.formatNumber(spanCent / HC_CENT, 3, 3)));
            return;
        }
        if (selectedAnalysisMode() == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL) {
            IntervalBounds directionalInterval = selectedDirectionalBounds();
            if (directionalInterval == null) {
                theoreticalTarget.setText(localized("theory.boundaries.distinct",
                        "İki farklı sınır perdesi seçin"));
                return;
            }
            TheoryCatalog.Note segah = theory.note("segah");
            double segahCent = theory.relativeCent(makam, segah);
            String hz = Double.isFinite(confirmedTonicHertz)
                    ? " / " + i18n.formatNumber(confirmedTonicHertz
                    * Math.pow(2.0, segahCent / 1200.0), 3, 3) + " Hz" : "";
            theoreticalTarget.setText(i18n.textOrDefault("theory.directional.reference",
                    "{0} referansı: {3} C / {4} Hc{5}\n"
                            + "Aday alanı: {1} < F0 < {2} (sınırlar hariç)",
                    "Segâh", directionalInterval.lower(), directionalInterval.upper(),
                    i18n.formatSignedNumber(segahCent, 2, 2),
                    i18n.formatSignedNumber(segahCent / HC_CENT, 3, 3), hz));
            return;
        }
        IntervalBounds interval = selectedIntervalBounds();
        if (selectedAnalysisMode() == MakamAnalysisMode.BETWEEN_PITCHES) {
            if (interval == null) {
                theoreticalTarget.setText(localized("theory.boundaries.distinct",
                        "İki farklı sınır perdesi seçin"));
                return;
            }
            String lowerHz = Double.isFinite(confirmedTonicHertz)
                    ? " / " + i18n.formatNumber(confirmedTonicHertz
                    * Math.pow(2.0, interval.lowerCent() / 1200.0), 3, 3) + " Hz" : "";
            String upperHz = Double.isFinite(confirmedTonicHertz)
                    ? " / " + i18n.formatNumber(confirmedTonicHertz
                    * Math.pow(2.0, interval.upperCent() / 1200.0), 3, 3) + " Hz" : "";
            theoreticalTarget.setText(i18n.textOrDefault("theory.openInterval",
                    "Açık aralık • sınırlar hariç\n{0}: {1} C{2}\n{3}: {4} C{5}",
                    interval.lower(), i18n.formatSignedNumber(interval.lowerCent(), 2, 2), lowerHz,
                    interval.upper(), i18n.formatSignedNumber(interval.upperCent(), 2, 2), upperHz));
            return;
        }
        if (target == null) {
            theoreticalTarget.setText(localized("theory.target.empty", "Kuramsal hedef: —"));
            return;
        }
        double cent = theory.relativeCent(makam, target);
        String hz = Double.isFinite(confirmedTonicHertz)
                ? " • " + i18n.formatNumber(confirmedTonicHertz
                * Math.pow(2.0, cent / 1200.0), 3, 3) + " Hz" : "";
        theoreticalTarget.setText(i18n.textOrDefault("theory.target",
                "Kuramsal hedef: {0} C / {1} Hc{2}",
                i18n.formatSignedNumber(cent, 2, 2),
                i18n.formatSignedNumber(cent / HC_CENT, 3, 3), hz));
    }

    private AnalysisConfig config(TheoryCatalog.Makam makam, TheoryCatalog.Note target,
                                  double tonic, boolean estimated) {
        return new AnalysisConfig(makam.toString(), theory.tonicFor(makam).toString(), target.toString(),
                tonic, theory.relativeCent(makam, target), 2.0, 2.0, 1.0,
                .10, .35, .70, .28, .75, STABLE_SPREAD_HC,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_SECONDS,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_SPAN_COMMA,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_MONOTONICITY,
                AnalysisConfig.DEFAULT_MAXIMUM_GLISSANDO_FRAME_STEP_CENT,
                700.0, 1.5, .02, 32.0, estimated);
    }

    private AnalysisConfig intervalConfig(TheoryCatalog.Makam makam, IntervalBounds interval,
                                          double tonic, boolean estimated) {
        double midpoint = (interval.lowerCent() + interval.upperCent()) / 2.0;
        return new AnalysisConfig(makam.toString(), theory.tonicFor(makam).toString(),
                interval.lower() + "–" + interval.upper(),
                tonic, midpoint,
                (midpoint - interval.lowerCent()) / HC_CENT,
                (interval.upperCent() - midpoint) / HC_CENT, 1.0,
                .10, .35, .70, .28, .75, STABLE_SPREAD_HC,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_SECONDS,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_SPAN_COMMA,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_MONOTONICITY,
                AnalysisConfig.DEFAULT_MAXIMUM_GLISSANDO_FRAME_STEP_CENT,
                700.0, 1.5, .02, 32.0, estimated);
    }

    private AnalysisConfig hicazConfig(TheoryCatalog.Makam makam, double tonic,
                                       boolean estimated) {
        double midpoint = 11.0 * HC_CENT;
        return new AnalysisConfig(makam.toString(), theory.tonicFor(makam).toString(),
                "Hicaz 0–5–17–22 Hc", tonic, midpoint,
                11.0, 11.0, 1.0,
                .10, .35, .70, .28, .75, STABLE_SPREAD_HC,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_SECONDS,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_SPAN_COMMA,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_MONOTONICITY,
                AnalysisConfig.DEFAULT_MAXIMUM_GLISSANDO_FRAME_STEP_CENT,
                700.0, 1.5, .02, 32.0, estimated);
    }

    private AnalysisConfig glissandoConfig(TheoryCatalog.Makam makam, GlissandoPair pair,
                                            double tonic, boolean estimated) {
        double midpoint = (pair.lowerCent() + pair.upperCent()) / 2.0;
        return new AnalysisConfig(makam.toString(), theory.tonicFor(makam).toString(),
                pair.lower() + "–" + pair.upper(), tonic, midpoint,
                Math.max(.5, (midpoint - pair.lowerCent()) / HC_CENT),
                Math.max(.5, (pair.upperCent() - midpoint) / HC_CENT), 1.0,
                .10, .35, .70, .28, .75, STABLE_SPREAD_HC,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_SECONDS,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_SPAN_COMMA,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_MONOTONICITY,
                AnalysisConfig.DEFAULT_MAXIMUM_GLISSANDO_FRAME_STEP_CENT,
                700.0, 1.5, .02, 32.0, estimated);
    }

    private void refreshTargetModel() {
        TheoryCatalog.Makam makam = selectedMakam();
        Object previous = targetBox.getSelectedItem();
        Object previousLower = lowerBoundaryBox.getSelectedItem();
        Object previousUpper = upperBoundaryBox.getSelectedItem();
        Object previousDirectionalLower = directionalLowerBoundaryBox.getSelectedItem();
        Object previousDirectionalUpper = directionalUpperBoundaryBox.getSelectedItem();
        Object previousGlissandoLower = glissandoLowerPitchBox.getSelectedItem();
        Object previousGlissandoUpper = glissandoUpperPitchBox.getSelectedItem();
        DefaultComboBoxModel<TheoryCatalog.Note> model = new DefaultComboBoxModel<>();
        DefaultComboBoxModel<TheoryCatalog.Note> lowerModel = new DefaultComboBoxModel<>();
        DefaultComboBoxModel<TheoryCatalog.Note> upperModel = new DefaultComboBoxModel<>();
        DefaultComboBoxModel<TheoryCatalog.Note> directionalLowerModel = new DefaultComboBoxModel<>();
        DefaultComboBoxModel<TheoryCatalog.Note> directionalUpperModel = new DefaultComboBoxModel<>();
        DefaultComboBoxModel<TheoryCatalog.Note> glissandoLowerModel = new DefaultComboBoxModel<>();
        DefaultComboBoxModel<TheoryCatalog.Note> glissandoUpperModel = new DefaultComboBoxModel<>();
        List<TheoryCatalog.Note> notes = makam == null ? List.of() : theory.notesAround(makam);
        notes.forEach(value -> {
            model.addElement(value); lowerModel.addElement(value); upperModel.addElement(value);
            directionalLowerModel.addElement(value); directionalUpperModel.addElement(value);
            glissandoLowerModel.addElement(value); glissandoUpperModel.addElement(value);
        });
        targetBox.setModel(model);
        lowerBoundaryBox.setModel(lowerModel);
        upperBoundaryBox.setModel(upperModel);
        directionalLowerBoundaryBox.setModel(directionalLowerModel);
        directionalUpperBoundaryBox.setModel(directionalUpperModel);
        glissandoLowerPitchBox.setModel(glissandoLowerModel);
        glissandoUpperPitchBox.setModel(glissandoUpperModel);
        if (previous instanceof TheoryCatalog.Note note && indexOfKey(model, note.key()) >= 0) {
            targetBox.setSelectedIndex(indexOfKey(model, note.key()));
        } else if (model.getSize() > 0) {
            int tonicIndex = 0;
            for (int i = 0; i < model.getSize(); i++) {
                if (model.getElementAt(i).key().equals(makam.tonicKey())) { tonicIndex = i; break; }
            }
            targetBox.setSelectedIndex(Math.min(model.getSize() - 1, tonicIndex + 1));
        }
        if (lowerModel.getSize() == 0) return;
        int tonicIndex = Math.max(0, indexOfKey(lowerModel, makam.tonicKey()));
        boolean restoredLower = previousLower instanceof TheoryCatalog.Note note
                && indexOfKey(lowerModel, note.key()) >= 0;
        boolean restoredUpper = previousUpper instanceof TheoryCatalog.Note note
                && indexOfKey(upperModel, note.key()) >= 0;
        if (restoredLower) {
            lowerBoundaryBox.setSelectedIndex(indexOfKey(lowerModel,
                    ((TheoryCatalog.Note) previousLower).key()));
        } else {
            lowerBoundaryBox.setSelectedIndex(tonicIndex);
        }
        if (restoredUpper) {
            upperBoundaryBox.setSelectedIndex(indexOfKey(upperModel,
                    ((TheoryCatalog.Note) previousUpper).key()));
        } else {
            int dugahIndex = -1;
            for (int i = tonicIndex + 1; i < upperModel.getSize(); i++) {
                if (upperModel.getElementAt(i).key().equals("dugah")) { dugahIndex = i; break; }
            }
            upperBoundaryBox.setSelectedIndex(dugahIndex >= 0 ? dugahIndex
                    : Math.min(upperModel.getSize() - 1, tonicIndex + 1));
        }
        int directionalLowerIndex = previousDirectionalLower instanceof TheoryCatalog.Note note
                ? indexOfKey(directionalLowerModel, note.key()) : -1;
        int directionalUpperIndex = previousDirectionalUpper instanceof TheoryCatalog.Note note
                ? indexOfKey(directionalUpperModel, note.key()) : -1;
        if (directionalLowerIndex < 0) {
            directionalLowerIndex = indexOfKey(directionalLowerModel, "dugah");
        }
        if (directionalUpperIndex < 0) {
            directionalUpperIndex = indexOfKey(directionalUpperModel, "buselik");
        }
        directionalLowerBoundaryBox.setSelectedIndex(Math.max(0, directionalLowerIndex));
        directionalUpperBoundaryBox.setSelectedIndex(Math.max(0, directionalUpperIndex));
        int glissandoLowerIndex = previousGlissandoLower instanceof TheoryCatalog.Note note
                ? indexOfKey(glissandoLowerModel, note.key()) : -1;
        int glissandoUpperIndex = previousGlissandoUpper instanceof TheoryCatalog.Note note
                ? indexOfKey(glissandoUpperModel, note.key()) : -1;
        if (glissandoLowerIndex < 0) glissandoLowerIndex = indexOfKey(glissandoLowerModel, "dugah");
        if (glissandoUpperIndex < 0) glissandoUpperIndex = indexOfKey(glissandoUpperModel, "buselik");
        glissandoLowerPitchBox.setSelectedIndex(Math.max(0, glissandoLowerIndex));
        glissandoUpperPitchBox.setSelectedIndex(Math.max(0, glissandoUpperIndex));
    }

    private void placeholderSelectionChanged() {
        if (selectedAnalysisMode() != MakamAnalysisMode.GLISSANDO_ANALYSIS) return;
        invalidateTargetAnalysis();
        refreshTheoryText();
        if (audio != null && Double.isFinite(confirmedTonicHertz)) {
            refreshPreview(confirmedTonicHertz);
        }
    }

    private void focusResultTimeRange(double start, double end) {
        if (audio == null || !Double.isFinite(start) || !Double.isFinite(end)) return;
        double safeStart = Math.max(0.0, Math.min(audio.durationSeconds(), start));
        double safeEnd = Math.max(safeStart, Math.min(audio.durationSeconds(), end));
        evidencePanel.setSelection(safeStart, safeEnd);
        selectionChanged(safeStart, safeEnd);
        editorPanel.setTimeWindow(Math.max(0.0, safeStart - .75),
                Math.min(audio.durationSeconds(), safeEnd + .75));
    }

    private static int indexOfKey(DefaultComboBoxModel<TheoryCatalog.Note> model, String key) {
        for (int index = 0; index < model.getSize(); index++) {
            if (model.getElementAt(index).key().equals(key)) return index;
        }
        return -1;
    }

    private void listenSelection() {
        AudioData currentAudio = audio;
        if (currentAudio == null) return;
        if (hasPlayableSelection()) {
            startContinuousPlayback(selectionStart, selectionEnd);
        } else {
            // The transport is shared by all four analysis workspaces.  A user
            // must be able to listen immediately after opening a recording;
            // requiring a waveform selection first made the common Play button
            // appear broken in every module.
            startContinuousPlayback(0.0, currentAudio.durationSeconds());
        }
    }

    private boolean hasPlayableSelection() {
        return Double.isFinite(selectionStart) && Double.isFinite(selectionEnd)
                && selectionEnd - selectionStart >= 0.001;
    }

    private void togglePause() {
        if (playback.isPaused()) {
            playback.resume();
            evidencePanel.beginPlaybackFollow(playback.positionSeconds());
        }
        else if (playback.isPlaying()) playback.pause();
        else if (audio != null) listenSelection();
        updateButtons();
    }

    private void openWaveEditor() {
        if (audio == null || activeWorker != null) return;
        stopPlayback();
        MakamWaveEditorDialog.EditResult edited = MakamWaveEditorDialog.open(
                this, audio, frames, confirmedTonicHertz,
                selectionStart, selectionEnd);
        if (edited != null) {
            setStatus("status.loadingEditedAudio", "Düzenlenmiş çalışma kopyası yükleniyor…");
            loadAudio(edited.path(), edited.selectionStartSeconds(), edited.selectionEndSeconds());
        }
    }

    private void playRange(double start, double end) {
        startContinuousPlayback(start, end);
    }

    private void seekFromWave(double seconds) {
        AudioData currentAudio = audio;
        if (currentAudio == null || (!playback.isPlaying() && !continuousPlayback)) return;
        startContinuousPlayback(seconds, currentAudio.durationSeconds());
    }

    private void startContinuousPlayback(double start, double end) {
        AudioData currentAudio = audio;
        if (currentAudio == null) return;
        double safeStart = Math.max(0.0, Math.min(currentAudio.durationSeconds(), start));
        double safeEnd = Math.max(safeStart, Math.min(currentAudio.durationSeconds(), end));
        if (safeEnd - safeStart < 0.001) return;
        long ticket = ++playbackSessionGeneration;
        Thread previous = playbackPreparationThread;
        if (previous != null) previous.interrupt();
        continuousPlayback = true;
        playbackSessionEnd = safeEnd;
        playbackChunkEnd = Double.NaN;
        evidencePanel.beginPlaybackFollow(safeStart);
        startPlaybackChunk(currentAudio, safeStart, ticket);
        updateButtons();
    }

    private void startPlaybackChunk(AudioData currentAudio, double start, long ticket) {
        if (ticket != playbackSessionGeneration || !continuousPlayback || currentAudio != audio) return;
        double chunkEnd = Math.min(playbackSessionEnd,
                start + MAXIMUM_SELECTION_PREVIEW_SECONDS);
        if (chunkEnd - start < 0.001) {
            continuousPlayback = false;
            playbackChunkStarting = false;
            return;
        }
        playbackChunkEnd = chunkEnd;
        playbackChunkStarting = true;
        Thread thread = new Thread(() -> {
            try {
                playback.playRange(currentAudio, start, chunkEnd, false);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> {
                    if (ticket == playbackSessionGeneration) {
                        continuousPlayback = false;
                        evidencePanel.stopPlaybackFollow();
                        showError("dialog.error.playback", "Ses çalınamadı", exception);
                    }
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    if (ticket == playbackSessionGeneration) {
                        playbackChunkStarting = false;
                        updateButtons();
                    }
                });
            }
        }, "makam-playback-" + ticket);
        thread.setDaemon(true);
        playbackPreparationThread = thread;
        thread.start();
    }

    private void stopPlayback() {
        playbackSessionGeneration++;
        Thread preparing = playbackPreparationThread;
        playbackPreparationThread = null;
        if (preparing != null) preparing.interrupt();
        continuousPlayback = false;
        playbackChunkStarting = false;
        playbackSessionEnd = Double.NaN;
        playbackChunkEnd = Double.NaN;
        playback.stop();
        evidencePanel.stopPlaybackFollow();
        evidencePanel.setPlaybackPosition(Double.NaN, false);
        if (report != null) editorPanel.setPlaybackPosition(Double.NaN, false);
        updateButtons();
    }

    private void refreshPlayback() {
        double position = playback.hasClip() ? playback.positionSeconds() : Double.NaN;
        boolean playing = playback.isPlaying();
        if (continuousPlayback && !playing && !playback.isPaused()
                && playback.hasClip() && !playbackChunkStarting
                && Double.isFinite(playbackChunkEnd)
                && position >= playbackChunkEnd - 0.08) {
            if (playbackChunkEnd < playbackSessionEnd - 0.001) {
                startPlaybackChunk(audio, playbackChunkEnd, playbackSessionGeneration);
            } else {
                continuousPlayback = false;
                evidencePanel.stopPlaybackFollow();
            }
        }
        // Keep the main waveform logically active while the next 30-second block is
        // being prepared. A click during that brief hand-off can therefore still seek.
        boolean waveformPlaybackActive = playing || (continuousPlayback && playbackChunkStarting);
        evidencePanel.setPlaybackPosition(position, waveformPlaybackActive);
        if (report != null) editorPanel.setPlaybackPosition(position, playing);
        pauseButton.setText(playback.isPaused() ? "▶" : "⏸");
        pauseButton.setToolTipText(playback.isPaused()
                ? localized("transport.resume", "Oynatmayı sürdür")
                : localized("transport.pause", "Oynatmayı duraklat"));
        updateButtons();
    }

    private void exportWord() {
        if (wordReportHandler == null) {
            showWorkflowMessage("validation.reportHandler",
                    "Word raporu bileşeni yüklenemedi. Uygulamayı yeniden başlatın.");
            return;
        }
        MakamStudyContext context = studyContext();
        if (context == null) {
            if (activeWorker != null) {
                showWorkflowMessage("validation.analysisInProgress",
                        "Devam eden çözümlemenin tamamlanmasını bekleyin.");
                return;
            }
            exportAfterAnalysis = true;
            analyzeTarget();
            return;
        }
        try { wordReportHandler.export(this, context); }
        catch (Exception exception) {
            showError("dialog.error.report", "Word raporu oluşturulamadı", exception);
        }
    }

    private void showWorkflowMessage(String key, String fallback) {
        showLocalizedMessage(localized(key, fallback), JOptionPane.INFORMATION_MESSAGE);
    }

    private void showLocalizedMessage(String message, int messageType) {
        String close = localized("common.close", "Kapat");
        JOptionPane.showOptionDialog(this, message, "MakamLab", JOptionPane.DEFAULT_OPTION,
                messageType, null, new Object[]{close}, close);
    }

    private void invalidateAllAnalysis() {
        stopPlayback();
        audio = null; frames = List.of(); automaticTonic = null; confirmedTonicHertz = Double.NaN;
        selectedTonicHertz = Double.NaN; selectionStart = selectionEnd = Double.NaN;
        selectedTonicManualFallback = false;
        tonicField.setText("—");
        tonicEvidence.setText(localized("tonic.evidence.noAudio", "Ses ve F0 hazırlanıyor…"));
        selectionEvidence.setText(localized("tonic.evidence.selectRegion",
                "Grafikte bir ses bölgesi seçebilirsiniz"));
        invalidateTargetAnalysis();
    }

    private void invalidateTargetAnalysis() {
        report = null; directionalSegahStudy = null; hicazStudy = null; glissandoStudy = null;
        allPitchEvents = List.of(); targetPitchEvents = List.of();
        eventModel.setRows(List.of()); directionalEventModel.setRows(List.of());
        hicazResultsPanel.setResult(null); glissandoResultsPanel.setResult(null);
        summary.setText(localized("result.waiting", "Analiz sonucu bekleniyor"));
        resultLayout.show(resultCards, resultCard(selectedAnalysisMode()));
        updateDirectionalSummaryCards(null);
        updateButtons();
    }

    private void setBusy(String key, String fallback, int value, Object... arguments) {
        boolean busy = key != null;
        if (busy && !progress.isVisible()) progressEta.start();
        if (busy) setStatus(key, fallback, arguments);
        progress.setVisible(busy);
        progress.setIndeterminate(busy && value <= 0);
        progress.setValue(Math.max(0, Math.min(100, value)));
        progress.setString(busy ? progressEta.describe(value) : "");
        updateButtons();
    }

    private void setStatus(String key, String fallback, Object... arguments) {
        currentStatusKey = key;
        currentStatusFallback = fallback;
        currentStatusArguments = arguments == null ? new Object[0] : arguments.clone();
        status.setText(i18n.textOrDefault(key, fallback, currentStatusArguments));
    }

    private void updateButtons() {
        boolean busy = activeWorker != null;
        useAutomaticTonicButton.setEnabled(!busy && automaticTonic != null);
        useSelectionTonicButton.setEnabled(!busy && Double.isFinite(selectedTonicHertz));
        // Play is a recording-level transport, not a selection-only action.
        // When no selection exists listenSelection() starts at the beginning.
        listenSelectionButton.setEnabled(!busy && audio != null);
        pauseButton.setEnabled(!busy && (playback.isPlaying() || playback.isPaused()));
        stopButton.setEnabled(!busy && (playback.hasClip() || continuousPlayback || playbackChunkStarting));
        waveEditorButton.setEnabled(!busy && audio != null);
        boolean analysisActionVisible = !busy && audio != null;
        setPrimaryEnabled(analyzeButton, analysisActionVisible);
        boolean reportActionVisible = !busy && audio != null && wordReportHandler != null;
        setPrimaryEnabled(wordButton, reportActionVisible);
        channelBox.setEnabled(!busy);
        makamBox.setEnabled(!busy);
        analysisModeBox.setEnabled(!busy);
        targetBox.setEnabled(!busy && selectedAnalysisMode() == MakamAnalysisMode.SINGLE_PITCH);
        lowerBoundaryBox.setEnabled(!busy && selectedAnalysisMode() == MakamAnalysisMode.BETWEEN_PITCHES);
        upperBoundaryBox.setEnabled(!busy && selectedAnalysisMode() == MakamAnalysisMode.BETWEEN_PITCHES);
        directionalLowerBoundaryBox.setEnabled(!busy
                && selectedAnalysisMode() == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL);
        directionalUpperBoundaryBox.setEnabled(!busy
                && selectedAnalysisMode() == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL);
        glissandoLowerPitchBox.setEnabled(!busy
                && selectedAnalysisMode() == MakamAnalysisMode.GLISSANDO_ANALYSIS);
        glissandoUpperPitchBox.setEnabled(!busy
                && selectedAnalysisMode() == MakamAnalysisMode.GLISSANDO_ANALYSIS);
    }

    private void configureShortcuts() {
        JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_O,
                InputEvent.META_DOWN_MASK), "open-audio");
        root.getActionMap().put("open-audio", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { chooseAudio(); }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "play-stop");
        root.getActionMap().put("play-stop", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                togglePause();
            }
        });
    }

    private void close() {
        cancelWorker();
        i18n.removeChangeListener(languageChangeListener);
        playbackTimer.stop();
        playbackSessionGeneration++;
        Thread preparing = playbackPreparationThread;
        if (preparing != null) preparing.interrupt();
        playback.close();
        dispose();
        if (Frame.getFrames().length <= 1) System.exit(0);
    }

    private void cancelWorker() {
        if (activeWorker != null) activeWorker.cancel(true);
        activeWorker = null;
    }

    private TheoryCatalog.Makam selectedMakam() {
        TheoryCatalog.Makam resolved = resolveMakam(makamBox.getSelectedItem());
        return resolved == null ? lastSelectedMakam : resolved;
    }

    private TheoryCatalog.Makam resolveMakam(Object value) {
        if (value instanceof TheoryCatalog.Makam makam) return makam;
        String query = searchKey(Objects.toString(value, ""));
        if (query.isBlank()) return null;
        TheoryCatalog.Makam prefix = null;
        TheoryCatalog.Makam contains = null;
        for (TheoryCatalog.Makam makam : theory.makams()) {
            String raw = searchKey(makam.name());
            String display = searchKey(makam.toString());
            if (raw.equals(query) || display.equals(query)) return makam;
            if (prefix == null && (raw.startsWith(query) || display.startsWith(query))) prefix = makam;
            if (contains == null && (raw.contains(query) || display.contains(query))) contains = makam;
        }
        return prefix != null ? prefix : contains;
    }

    private static String searchKey(String value) {
        String lower = value.toLowerCase(Locale.forLanguageTag("tr-TR"))
                .replace('ı', 'i');
        return java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private MakamAnalysisMode selectedAnalysisMode() {
        MakamAnalysisMode value = (MakamAnalysisMode) analysisModeBox.getSelectedItem();
        return value == null ? MakamAnalysisMode.SINGLE_PITCH : value;
    }

    private TheoryCatalog.Note selectedTarget() {
        return (TheoryCatalog.Note) targetBox.getSelectedItem();
    }

    private TheoryCatalog.Note selectedLowerBoundary() {
        IntervalBounds value = selectedAnalysisMode() == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL
                ? selectedDirectionalBounds() : selectedIntervalBounds();
        return value == null ? null : value.lower();
    }

    private TheoryCatalog.Note selectedUpperBoundary() {
        IntervalBounds value = selectedAnalysisMode() == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL
                ? selectedDirectionalBounds() : selectedIntervalBounds();
        return value == null ? null : value.upper();
    }

    private IntervalBounds selectedIntervalBounds() {
        TheoryCatalog.Makam makam = selectedMakam();
        TheoryCatalog.Note first = (TheoryCatalog.Note) lowerBoundaryBox.getSelectedItem();
        TheoryCatalog.Note second = (TheoryCatalog.Note) upperBoundaryBox.getSelectedItem();
        if (makam == null || first == null || second == null || first.key().equals(second.key())) return null;
        double firstCent = theory.relativeCent(makam, first);
        double secondCent = theory.relativeCent(makam, second);
        return firstCent < secondCent
                ? new IntervalBounds(first, second, firstCent, secondCent)
                : new IntervalBounds(second, first, secondCent, firstCent);
    }

    private IntervalBounds selectedDirectionalBounds() {
        TheoryCatalog.Makam makam = selectedMakam();
        TheoryCatalog.Note first = (TheoryCatalog.Note) directionalLowerBoundaryBox.getSelectedItem();
        TheoryCatalog.Note second = (TheoryCatalog.Note) directionalUpperBoundaryBox.getSelectedItem();
        if (makam == null || first == null || second == null
                || first.key().equals(second.key())) return null;
        double firstCent = theory.relativeCent(makam, first);
        double secondCent = theory.relativeCent(makam, second);
        return firstCent < secondCent
                ? new IntervalBounds(first, second, firstCent, secondCent)
                : new IntervalBounds(second, first, secondCent, firstCent);
    }

    private GlissandoPair selectedGlissandoPair() {
        TheoryCatalog.Makam makam = selectedMakam();
        TheoryCatalog.Note first = (TheoryCatalog.Note) glissandoLowerPitchBox.getSelectedItem();
        TheoryCatalog.Note second = (TheoryCatalog.Note) glissandoUpperPitchBox.getSelectedItem();
        if (makam == null || first == null || second == null
                || first.key().equals(second.key())) return null;
        double firstCent = theory.relativeCent(makam, first);
        double secondCent = theory.relativeCent(makam, second);
        return firstCent < secondCent
                ? new GlissandoPair(first, second, firstCent, secondCent)
                : new GlissandoPair(second, first, secondCent, firstCent);
    }

    private void intervalBoundaryChanged() {
        invalidateTargetAnalysis();
        refreshTheoryText();
        if (audio != null && Double.isFinite(confirmedTonicHertz)) refreshPreview(confirmedTonicHertz);
    }

    private void directionalBoundaryChanged() {
        if (selectedAnalysisMode() != MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL) return;
        invalidateTargetAnalysis();
        refreshTheoryText();
        if (audio != null && Double.isFinite(confirmedTonicHertz)) {
            refreshPreview(confirmedTonicHertz);
        }
    }

    private void refreshAnalysisModeControls() {
        MakamAnalysisMode mode = selectedAnalysisMode();
        if (mode == MakamAnalysisMode.USSAK_SEGAH_DIRECTIONAL) selectTargetKey("segah");
        analysisSelectorLayout.show(analysisSelectorCards, mode.name());
        analyzeButton.setText(switch (mode) {
            case SINGLE_PITCH -> localized("action.analyze.single", "Tek perdeyi analiz et");
            case BETWEEN_PITCHES -> localized("action.analyze.between", "Perdeler arasını analiz et");
            case USSAK_SEGAH_DIRECTIONAL -> localized("action.analyze.directional", "İnici-çıkıcı Segâh analizi");
            case HICAZ_AUGMENTED_INTERVAL -> localized("action.analyze.hicaz", "Artık aralığı analiz et");
            case GLISSANDO_ANALYSIS -> localized("action.analyze.glissando", "Glissandoları analiz et");
        });
        resultLayout.show(resultCards, resultCard(mode));
        analysisSelectorCards.revalidate();
        analysisSelectorCards.repaint();
    }

    private void selectTargetKey(String key) {
        if (key == null) return;
        for (int index = 0; index < targetBox.getItemCount(); index++) {
            TheoryCatalog.Note note = targetBox.getItemAt(index);
            if (note != null && key.equals(note.key())) {
                if (targetBox.getSelectedIndex() != index) targetBox.setSelectedIndex(index);
                return;
            }
        }
    }

    private static double hertzToCent(double hertz) {
        return 1200.0 * Math.log(hertz / 16.351597831287414) / Math.log(2.0);
    }

    private static Candidate robustCandidate(List<PitchFrame> frames, double start, double end) {
        TonicSelectionEstimator.Estimate estimate = TonicSelectionEstimator.estimate(frames,
                time -> time >= start && time <= end);
        return estimate == null ? null
                : new Candidate(estimate.hertz(), estimate.confidence(), estimate.count(), false);
    }

    private static double median(double[] values) {
        if (values.length == 0) return Double.NaN;
        int middle = values.length / 2;
        return values.length % 2 == 0 ? (values[middle - 1] + values[middle]) / 2.0 : values[middle];
    }

    private static JPanel card() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SURFACE); panel.setBorder(BorderFactory.createLineBorder(BORDER));
        return panel;
    }

    private static JLabel section(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER); label.setForeground(INK);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 18f));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setMaximumSize(new Dimension(CONTROL_CONTENT_WIDTH, 32));
        return label;
    }

    private JPanel labeled(String key, String fallback, JComponent component) {
        JPanel panel = new JPanel(); panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel title = localizedText(new JLabel("", SwingConstants.CENTER), key, fallback);
        title.setForeground(MUTED);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setMaximumSize(new Dimension(CONTROL_CONTENT_WIDTH, 18));
        component.setAlignmentX(Component.CENTER_ALIGNMENT);
        component.setMaximumSize(new Dimension(CONTROL_CONTENT_WIDTH, 28));
        component.setPreferredSize(new Dimension(CONTROL_CONTENT_WIDTH, 26));
        panel.add(title); panel.add(Box.createVerticalStrut(1)); panel.add(component); panel.add(Box.createVerticalStrut(3));
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.setMaximumSize(new Dimension(CONTROL_CONTENT_WIDTH, 50));
        return panel;
    }

    private static JPanel wrapLabel(JComponent label) {
        JPanel panel = new JPanel(new BorderLayout()); panel.setOpaque(false);
        panel.add(label); panel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.setMaximumSize(new Dimension(CONTROL_CONTENT_WIDTH, 28));
        return panel;
    }

    private static JTextPane centeredTextPane(String text) {
        return new CenteredTextPane(text);
    }

    private static Icon loadHeaderIcon(int size) {
        BufferedImage image = null;
        try (InputStream input = MakamFrame.class.getResourceAsStream("/ui/MakamIcon-1024.png")) {
            if (input != null) image = ImageIO.read(input);
        } catch (Exception ignored) {
            // Development builds fall back to the project asset below.
        }
        if (image == null) {
            try {
                Path asset = Path.of("assets", "MakamIcon-1024.png");
                if (Files.isRegularFile(asset)) image = ImageIO.read(asset.toFile());
            } catch (Exception ignored) {
                // The title remains usable even when a development asset is unavailable.
            }
        }
        if (image == null) {
            BufferedImage fallback = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            var graphics = fallback.createGraphics();
            graphics.setColor(CRIMSON);
            graphics.fillRoundRect(0, 0, size, size, size / 4, size / 4);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(18, size / 2)));
            graphics.drawString("M", size / 5, size * 7 / 10);
            graphics.dispose();
            image = fallback;
        }
        return new HighResolutionImageIcon(image, size, size);
    }

    private static Icon loadMenuIcon(String fileName, int size) {
        BufferedImage image = null;
        try (InputStream input = MakamFrame.class.getResourceAsStream("/ui/" + fileName)) {
            if (input != null) image = ImageIO.read(input);
        } catch (Exception ignored) {
            // Development builds fall back to the project asset below.
        }
        if (image == null) {
            try {
                Path asset = Path.of("assets", fileName);
                if (Files.isRegularFile(asset)) image = ImageIO.read(asset.toFile());
            } catch (Exception ignored) {
                // A clear crimson fallback keeps the menu usable.
            }
        }
        if (image == null) {
            BufferedImage fallback = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = fallback.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(CRIMSON);
            graphics.fillOval(3, 3, size - 6, size - 6);
            graphics.setColor(new Color(255, 236, 200));
            graphics.setStroke(new java.awt.BasicStroke(Math.max(3f, size / 30f),
                    java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            graphics.drawLine(size / 5, size / 2, size * 2 / 5, size / 3);
            graphics.drawLine(size * 2 / 5, size / 3, size * 3 / 5, size * 2 / 3);
            graphics.drawLine(size * 3 / 5, size * 2 / 3, size * 4 / 5, size / 2);
            graphics.dispose();
            image = fallback;
        }
        return new HighResolutionImageIcon(image, size, size);
    }

    private static JButton button(String text) {
        AutoFitButton button = new AutoFitButton(text);
        // Some supported translations are substantially longer than Turkish.
        // Preserve the complete wording at compact-window widths; a small
        // readable font is preferable to Swing replacing the final word with
        // an ellipsis.
        button.setMinimumAutoFitFontSize(7.0f);
        button.setFocusPainted(false);
        return button;
    }

    private static JButton primaryButton(String text) {
        JButton button = button(text);
        if (button instanceof AutoFitButton adaptive) {
            // Header actions share a compact single row; a slightly lower
            // floor is preferable to truncating the final translated word.
            adaptive.setMinimumAutoFitFontSize(7.0f);
        }
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(CRIMSON);
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CRIMSON.darker(), 1, true),
                BorderFactory.createEmptyBorder(7, 8, 7, 8)));
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        return button;
    }

    private static void configureHeaderActionButton(JButton button, int preferredWidth) {
        Dimension size = new Dimension(preferredWidth, 38);
        button.setPreferredSize(size);
        // AutoFitButton computes a translation-safe minimum width itself; the
        // height below keeps every action visually stable and readable.
        button.setMinimumSize(new Dimension(Math.min(preferredWidth, 92), 34));
        button.setFont(button.getFont().deriveFont(12.5f));
    }

    private static void setPrimaryEnabled(JButton button, boolean enabled) {
        button.setEnabled(enabled);
        button.setBackground(enabled ? CRIMSON : new Color(232, 233, 236));
        button.setForeground(enabled ? Color.WHITE : new Color(82, 87, 96));
    }

    /** Logical-size icon that retains the complete source bitmap for HiDPI painting. */
    private static final class HighResolutionImageIcon implements Icon {
        private final BufferedImage source;
        private final int width;
        private final int height;

        private HighResolutionImageIcon(BufferedImage source, int width, int height) {
            this.source = Objects.requireNonNull(source, "source");
            this.width = width;
            this.height = height;
        }

        @Override public int getIconWidth() { return width; }
        @Override public int getIconHeight() { return height; }

        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, x, y, width, height, null);
            g.dispose();
        }
    }

    private void showError(String titleKey, String titleFallback, Exception exception) {
        String message = localized(titleKey, titleFallback);
        showLocalizedMessage(message, JOptionPane.ERROR_MESSAGE);
    }

    static double diagnosticRobustCandidateHertz(List<PitchFrame> frames, double start, double end) {
        Candidate candidate = robustCandidate(frames, start, end);
        return candidate == null ? Double.NaN : candidate.hertz();
    }

    static double diagnosticSelectedTonicCandidateHertz(AudioData audio,
                                                        List<PitchFrame> frames,
                                                        double start, double end,
                                                        double registerReferenceHertz) {
        Candidate candidate = robustCandidate(frames, start, end);
        if (candidate == null && audio != null) {
            ManualTonicSelectionEstimator.Estimate manual =
                    new ManualTonicSelectionEstimator().estimate(audio, start, end, Double.NaN);
            if (manual != null) {
                candidate = new Candidate(manual.hertz(), manual.confidence(),
                        manual.frameCount(), true);
            }
        }
        return candidate == null ? Double.NaN : candidate.hertz();
    }

    private record Candidate(double hertz, double confidence, int count,
                             boolean manualFallback) { }
    private record IntervalBounds(TheoryCatalog.Note lower, TheoryCatalog.Note upper,
                                  double lowerCent, double upperCent) { }
    private record GlissandoPair(TheoryCatalog.Note lower, TheoryCatalog.Note upper,
                                 double lowerCent, double upperCent) {
        double spanCent() { return upperCent - lowerCent; }

        boolean containsObserved(PitchEvent event, double tonicHertz) {
            double observedRelative = PitchClassInterval.alignToRegister(
                    event.occurrence().observedCent() - MakamFrame.hertzToCent(tonicHertz),
                    lowerCent, upperCent);
            return observedRelative > lowerCent && observedRelative < upperCent;
        }
    }
    private record LoadedPitch(AudioData audio, List<PitchFrame> frames,
                               TonicEstimator.Estimate tonic, boolean cached) { }
    private record AnalyzedTarget(AnalysisReport report, List<PitchEvent> all,
                                  List<PitchEvent> target,
                                  UssakSegahDirectionalStudyResult directionalStudy,
                                  HicazAugmentedIntervalStudyResult hicazStudy,
                                  PitchPairGlissandoStudyResult glissandoStudy,
                                  List<Integer> intervalOctaveOffsets) {
        private AnalyzedTarget {
            intervalOctaveOffsets = List.copyOf(intervalOctaveOffsets);
        }
    }

    private static final class CenteredTextPane extends JTextPane {
        private CenteredTextPane(String text) {
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder());
            setHighlighter(null);
            setText(text);
        }

        @Override public void setText(String text) {
            super.setText(text == null ? "" : text);
            StyledDocument document = getStyledDocument();
            SimpleAttributeSet centered = new SimpleAttributeSet();
            StyleConstants.setAlignment(centered, StyleConstants.ALIGN_CENTER);
            document.setParagraphAttributes(0, document.getLength(), centered, false);
            setCaretPosition(0);
        }
    }

    private record PitchCacheKey(Path source, long size, long modifiedMillis,
                                 AudioChannelMode channelMode, int sourceChannels) {
        static PitchCacheKey from(AudioData audio) {
            try {
                return new PitchCacheKey(audio.source(), Files.size(audio.source()),
                        Files.getLastModifiedTime(audio.source()).toMillis(),
                        audio.channelMode(), audio.sourceChannelCount());
            } catch (Exception ignored) {
                return new PitchCacheKey(audio.source(), audio.samples().length, 0L,
                        audio.channelMode(), audio.sourceChannelCount());
            }
        }
    }

    private final class TargetEventTableModel extends AbstractTableModel {
        private final String[] columnKeys = {"table.number", "table.pitch", "table.time",
                "table.duration", "table.hertz", "table.relativeCent", "table.relativeHc",
                "table.deviationCent", "table.deviationHc", "table.direction", "table.type",
                "table.confidence", "table.comment"};
        private final String[] columnFallbacks = {"#", "Perde", "Zaman", "Süre", "Hz",
                "Karara göre C", "Karara göre Hc", "Sapma C", "Sapma Hc", "Yön", "Tür",
                "Güven", "Yorum"};
        private List<PitchEvent> rows = List.of();

        void setRows(List<PitchEvent> values) { rows = List.copyOf(values); fireTableDataChanged(); }
        PitchEvent row(int index) { return rows.get(index); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columnKeys.length; }
        @Override public String getColumnName(int column) {
            return localized(columnKeys[column], columnFallbacks[column]);
        }
        @Override public Object getValueAt(int row, int column) {
            PitchEvent event = rows.get(row);
            PitchOccurrence value = event.occurrence();
            double relativeCent = event.theoreticalRelativeCent() + value.deviationCent();
            return switch (column) {
                case 0 -> value.index();
                case 1 -> event.noteName();
                case 2 -> range(value.startSeconds(), value.endSeconds(), 2);
                case 3 -> i18n.formatNumber(value.durationSeconds(), 2, 2) + " "
                        + localized("common.seconds", "sn");
                case 4 -> i18n.formatNumber(PitchTracker.centToHertz(value.observedCent()), 3, 3);
                case 5 -> i18n.formatSignedNumber(relativeCent, 2, 2);
                case 6 -> i18n.formatSignedNumber(relativeCent / HC_CENT, 3, 3);
                case 7 -> i18n.formatSignedNumber(value.deviationCent(), 2, 2);
                case 8 -> i18n.formatSignedNumber(value.deviationComma(), 3, 3);
                case 9 -> melodicDirectionLabel(value.direction().name());
                case 10 -> eventTypeLabel(value.eventType().name());
                case 11 -> i18n.formatPercent(value.confidence(), 0);
                default -> event.noteName().startsWith(IntervalPitchNaming.UNNAMED_LABEL)
                        ? localized("table.nearestCenter", "En yakın merkeze") + " "
                        + i18n.formatSignedNumber(value.deviationCent(), 2, 2) + " C / "
                        + i18n.formatSignedNumber(value.deviationComma(), 3, 3) + " Hc"
                        : Math.abs(value.deviationCent()) < .01
                        ? localized("intonation.theoreticalCenter", "Kuramsal merkez")
                        : value.deviationCent() < 0
                        ? localized("intonation.flat", "Pest")
                        : localized("intonation.sharp", "Dik");
            };
        }
    }

    private final class DirectionalSegahTableModel extends AbstractTableModel {
        private final String[] columnKeys = {"table.number", "table.usage", "table.path",
                "table.time", "table.duration", "table.hertz", "table.observedCent",
                "table.observedHc", "table.referenceDeviationCent", "table.referenceDeviationHc",
                "table.localCent", "table.localHc", "table.intonation", "table.f0Confidence",
                "table.classConfidence", "table.status"};
        private final String[] columnFallbacks = {"#", "Kullanım", "İzlenen yol", "Zaman", "Süre",
                "Hz", "Gözlenen C", "Gözlenen Hc", "Referanstan C", "Referanstan Hc",
                "Yerel C", "Yerel Hc", "Pest/Dik", "F0 güveni", "Sınıf güveni", "Durum"};
        private List<UssakSegahEvent> rows = List.of();

        void setRows(List<UssakSegahEvent> values) {
            rows = List.copyOf(values == null ? List.of() : values);
            fireTableDataChanged();
        }

        UssakSegahEvent row(int index) { return rows.get(index); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columnKeys.length; }
        @Override public String getColumnName(int column) {
            return localized(columnKeys[column], columnFallbacks[column]);
        }

        @Override public Object getValueAt(int row, int column) {
            UssakSegahEvent event = rows.get(row);
            var value = event.measurement();
            return switch (column) {
                case 0 -> event.index();
                case 1 -> usageClassLabel(event.usageClass());
                case 2 -> event.pathLabel();
                case 3 -> range(value.startSeconds(), value.endSeconds(), 2);
                case 4 -> i18n.formatNumber(value.durationSeconds(), 2, 2) + " "
                        + localized("common.seconds", "sn");
                case 5 -> i18n.formatNumber(value.observedHertz(), 3, 3);
                case 6 -> i18n.formatSignedNumber(value.observedRelativeCent(), 2, 2);
                case 7 -> i18n.formatSignedNumber(value.observedRelativeHc(), 3, 3);
                case 8 -> i18n.formatSignedNumber(event.globalDeviationCent(), 2, 2);
                case 9 -> i18n.formatSignedNumber(event.globalDeviationHc(), 3, 3);
                case 10 -> finite(event.localDeviationCent(), 2);
                case 11 -> finite(event.localDeviationHc(), 3);
                case 12 -> intonationLabel(event.globalDeviationCent());
                case 13 -> i18n.formatPercent(value.confidence(), 0);
                case 14 -> i18n.formatPercent(event.classificationConfidence(), 0);
                default -> event.included() ? localized("table.included", "Ortalamaya dâhil")
                        // The analyser stores audit reasons as internal Turkish
                        // data.  Do not expose that implementation language in
                        // a differently localised table.
                        : localized("common.auditNote", "Denetim notu");
            };
        }

        private String finite(double value, int fractionDigits) {
            return Double.isFinite(value)
                    ? i18n.formatSignedNumber(value, fractionDigits, fractionDigits) : "—";
        }
    }

    private String range(double start, double end, int digits) {
        return i18n.formatNumber(start, digits, digits) + "–"
                + i18n.formatNumber(end, digits, digits);
    }

    private String melodicDirectionLabel(String name) {
        return switch (name) {
            case "ASCENDING" -> localized("direction.ascending", "Çıkıcı");
            case "DESCENDING" -> localized("direction.descending", "İnici");
            default -> localized("direction.level", "Yatay/Karma");
        };
    }

    private String eventTypeLabel(String name) {
        return switch (name) {
            case "SHORT_CONTACT" -> localized("event.shortContact", "Kısa temas");
            case "PASSING" -> localized("event.transition", "Geçiş");
            case "SUSTAINED" -> localized("event.prolongation", "Uzatma");
            default -> localized("event.hold", "Kalış");
        };
    }

    private String usageClassLabel(UssakSegahUsageClass usageClass) {
        return switch (usageClass) {
            case ASCENDING_PHRASE -> localized("usage.ascendingPhrase", "Çıkıcı cümle");
            case DESCENDING_PHRASE -> localized("usage.descendingPhrase", "İnici cümle");
            case DIRECT_HOLD -> localized("usage.directHold", "Doğrudan kalış");
            case OTHER -> localized("usage.other", "Belirsiz/diğer");
        };
    }

    private String intonationLabel(double deviationCent) {
        if (Math.abs(deviationCent) < .01) {
            return localized("intonation.theoreticalCenter", "Kuramsal merkez");
        }
        return deviationCent < 0 ? localized("intonation.flat", "Pest")
                : localized("intonation.sharp", "Dik");
    }

    private record LocalizedBinding(String key, String fallback) { }
    private record MenuCardBinding(String titleKey, String titleFallback,
                                   String descriptionKey, String descriptionFallback) { }
}
