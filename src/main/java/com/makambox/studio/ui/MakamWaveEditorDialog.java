package com.makambox.studio.ui;

import com.makambox.studio.audio.AudioPlayback;
import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.i18n.I18n;
import com.makambox.studio.i18n.LocalizationService;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.AudioChannelMode;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.PitchFrame;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Audacity-like, non-destructive mono editor used before academic analysis. */
final class MakamWaveEditorDialog extends JDialog {
    private static final Color CRIMSON = new Color(177, 18, 48);
    private static final Color CANVAS = new Color(248, 249, 251);
    private static final Color GRID = new Color(220, 224, 231);
    private static final Color WAVE = new Color(224, 31, 54);
    private static final Color WAVE_SHADOW = new Color(0, 0, 0, 105);
    /** Playback blocks bound memory; they do not limit the listening duration. */
    private static final int PLAYBACK_CHUNK_SECONDS = 30;

    private final LocalizationService i18n = I18n.get();
    private final WaveformEditModel model;
    private final List<PitchFrame> pitchFrames;
    private final double tonicHertz;
    private final AudioPlayback playback = new AudioPlayback();
    private final WaveCanvas canvas = new WaveCanvas();
    private final JScrollBar timelineScroll = new JScrollBar(JScrollBar.HORIZONTAL);
    private final JLabel selectionLabel = new JLabel(tr("waveEditor.selectionHint",
            "Bir noktaya tıklayın veya aralık seçmek için sürükleyin"));
    private final JLabel pitchLabel = new JLabel(tr("waveEditor.instantPitch.empty", "Anlık perde: —"));
    private final JLabel positionLabel = new JLabel("0:00.00 / 0:00.00", SwingConstants.RIGHT);
    private final JButton playButton = button("▶ " + tr("transport.play", "Oynat"));
    private final JButton pauseButton = button("⏸ " + tr("transport.pause", "Duraklat"));
    private final JButton stopButton = button("■ " + tr("transport.stop", "Durdur"));
    private final JButton previewButton = button("▶ " + tr("waveEditor.preview", "2 sn dinle"));
    private final JButton cutButton = button(tr("waveEditor.cut", "Kes"));
    private final JButton splitButton = button(tr("waveEditor.split", "Böl"));
    private final JButton deleteButton = button(tr("waveEditor.delete", "Sil"));
    private final JButton trimButton = button(tr("waveEditor.trim", "Seçime kırp"));
    private final JButton pasteButton = button(tr("waveEditor.paste", "Yapıştır"));
    private final JButton undoButton = button(tr("waveEditor.undo", "Geri al"));
    private final JButton applyButton = button(tr("waveEditor.apply", "Kaydet ve ana sayfaya aktar"));
    private final Timer timer = new Timer(60, event -> refreshPlayback());
    private EditResult savedResult;
    private int cursorSample;
    private int selectionStart;
    private int selectionEnd;
    private volatile double playRangeStart;
    private volatile Thread playbackPreparationThread;
    private long playbackSessionGeneration;
    private boolean continuousPlayback;
    private boolean playbackChunkStarting;
    private int playbackSessionEndSample;
    private int playbackChunkEndSample;
    private PitchFrame cachedSelectionPitch;
    private long lastSelectionPitchUpdateNanos;
    private boolean saving;
    private boolean updatingTimelineScroll;

    MakamWaveEditorDialog(Window owner, AudioData audio, List<PitchFrame> frames,
                          double tonicHertz, double initialStart, double initialEnd) {
        super(owner instanceof Frame frame ? frame : null,
                I18n.get().textOrDefault("waveEditor.windowTitle", "MakamLab — Dalga Düzenleyici"),
                ModalityType.APPLICATION_MODAL);
        model = new WaveformEditModel(audio);
        pitchFrames = frames == null ? List.of() : frames.stream()
                .sorted(Comparator.comparingDouble(PitchFrame::timeSeconds)).toList();
        this.tonicHertz = tonicHertz;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(980, 620));
        setSize(1240, 760);
        setLocationRelativeTo(owner);
        buildUi();
        configureActions();
        i18n.applyOrientation(this);
        canvas.fit();
        if (Double.isFinite(initialStart)) {
            selectionStart = model.secondsToSample(initialStart);
            selectionEnd = model.secondsToSample(Double.isFinite(initialEnd) ? initialEnd : initialStart);
            cursorSample = selectionStart;
            canvas.reveal(cursorSample);
        }
        updateInformation();
        timer.start();
    }

    static EditResult open(Window owner, AudioData audio, List<PitchFrame> frames,
                           double tonicHertz, double initialStart, double initialEnd) {
        MakamWaveEditorDialog dialog = new MakamWaveEditorDialog(owner, audio, frames,
                tonicHertz, initialStart, initialEnd);
        dialog.setVisible(true);
        return dialog.savedResult;
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(new Color(246, 247, 249));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        setContentPane(root);

        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        JLabel title = new JLabel(tr("waveEditor.title", "Dalga Düzenleyici"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        heading.add(title, BorderLayout.WEST);
        JLabel source = new JLabel(model.source().name());
        source.setForeground(new Color(95, 104, 120));
        heading.add(source, BorderLayout.EAST);
        root.add(heading, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        canvas.setPreferredSize(new Dimension(1120, 480));
        canvas.setBorder(BorderFactory.createLineBorder(GRID));
        timelineScroll.setUnitIncrement(Math.max(1, Math.round(model.sampleRate() * .20f)));
        timelineScroll.setBlockIncrement(Math.max(1, Math.round(model.sampleRate() * 2f)));
        timelineScroll.setPreferredSize(new Dimension(120, 17));
        timelineScroll.setMinimumSize(new Dimension(36, 17));
        timelineScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 17));
        timelineScroll.setName("makam-wave-timeline-scroll");
        timelineScroll.setToolTipText(tr("waveEditor.timeline.tooltip",
                "Zaman penceresinde sağa/sola gezin"));
        timelineScroll.addAdjustmentListener(event -> {
            if (updatingTimelineScroll) return;
            canvas.scrollTo(event.getValue(), true);
        });
        JPanel waveformArea = new JPanel(new BorderLayout(0, 3));
        waveformArea.setOpaque(false);
        waveformArea.add(canvas, BorderLayout.CENTER);
        JPanel timelineStrip = new JPanel(new BorderLayout());
        timelineStrip.setOpaque(false);
        timelineStrip.setMinimumSize(new Dimension(0, 17));
        timelineStrip.setPreferredSize(new Dimension(120, 17));
        timelineStrip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 17));
        timelineStrip.add(timelineScroll, BorderLayout.CENTER);
        waveformArea.add(timelineStrip, BorderLayout.SOUTH);
        center.add(waveformArea, BorderLayout.CENTER);

        JPanel details = new JPanel(new BorderLayout(12, 0));
        details.setOpaque(false);
        selectionLabel.setForeground(new Color(77, 86, 101));
        pitchLabel.setForeground(CRIMSON);
        pitchLabel.setFont(pitchLabel.getFont().deriveFont(Font.BOLD));
        details.add(selectionLabel, BorderLayout.WEST);
        details.add(pitchLabel, BorderLayout.CENTER);
        JPanel rightDetails = new JPanel(new BorderLayout(0, 2));
        rightDetails.setOpaque(false);
        rightDetails.add(positionLabel, BorderLayout.NORTH);
        JPanel zoomTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        zoomTools.setOpaque(false);
        JButton zoomOut = button("−");
        JButton zoomIn = button("+");
        JButton fit = button(tr("waveEditor.fit", "Tümünü göster"));
        zoomOut.setToolTipText(tr("waveEditor.zoomOut.tooltip", "Zaman düzleminde uzaklaştır"));
        zoomIn.setToolTipText(tr("waveEditor.zoomIn.tooltip", "Zaman düzleminde yakınlaştır"));
        zoomOut.addActionListener(event -> canvas.zoom(1.8));
        zoomIn.addActionListener(event -> canvas.zoom(.55));
        fit.addActionListener(event -> canvas.fit());
        zoomTools.add(zoomOut); zoomTools.add(zoomIn); zoomTools.add(fit);
        rightDetails.add(zoomTools, BorderLayout.SOUTH);
        details.add(rightDetails, BorderLayout.EAST);
        center.add(details, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);

        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new javax.swing.BoxLayout(controls, javax.swing.BoxLayout.Y_AXIS));
        JPanel transport = row();
        transport.add(playButton); transport.add(pauseButton); transport.add(stopButton);
        transport.add(previewButton);
        controls.add(transport);

        JPanel edit = row();
        edit.add(cutButton); edit.add(splitButton); edit.add(deleteButton); edit.add(trimButton);
        edit.add(pasteButton); edit.add(undoButton);
        JButton cancel = button(tr("waveEditor.cancel", "Vazgeç"));
        // Aqua paints the default action blue; white keeps this long action
        // label readable while the surrounding editor retains the red theme.
        applyButton.setForeground(Color.WHITE);
        applyButton.setFont(applyButton.getFont().deriveFont(Font.BOLD));
        applyButton.setToolTipText(tr("waveEditor.apply.tooltip",
                "Düzenlenmiş WAV'ı kaydeder, MakamLab ana sayfasına yükler ve perde izini yeniden çıkarır"));
        getRootPane().setDefaultButton(applyButton);
        cancel.addActionListener(event -> dispose());
        applyButton.addActionListener(event -> saveAndUse());
        JPanel transfer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        transfer.setOpaque(false);
        transfer.add(cancel); transfer.add(applyButton);
        controls.add(Box.createVerticalStrut(6)); controls.add(edit);
        controls.add(Box.createVerticalStrut(6)); controls.add(transfer);
        root.add(controls, BorderLayout.SOUTH);
    }

    private void configureActions() {
        playButton.addActionListener(event -> play(false));
        previewButton.addActionListener(event -> play(true));
        pauseButton.addActionListener(event -> {
            if (playback.isPaused()) {
                playback.resume();
                canvas.beginPlaybackFollow(model.secondsToSample(
                        absolutePlaybackSeconds(playRangeStart, playback.positionSeconds())));
            } else playback.pause();
            updateButtons();
        });
        stopButton.addActionListener(event -> stopPlayback());
        cutButton.addActionListener(event -> editSelection(Edit.CUT));
        deleteButton.addActionListener(event -> editSelection(Edit.DELETE));
        trimButton.addActionListener(event -> editSelection(Edit.TRIM));
        splitButton.addActionListener(event -> {
            model.splitAt(cursorSample);
            canvas.repaint(); updateInformation();
        });
        pasteButton.addActionListener(event -> {
            stopPlayback(); model.pasteAt(cursorSample); clearSelectionAt(cursorSample);
            canvas.fitIfOutside(); updateInformation();
        });
        undoButton.addActionListener(event -> {
            stopPlayback(); model.undo(); cursorSample = Math.min(cursorSample, model.sampleCount());
            clearSelectionAt(cursorSample); canvas.fitIfOutside(); updateInformation();
        });
        cutButton.setToolTipText(tr("waveEditor.cut.tooltip",
                "Seçimi çıkarır ve düzenleyici panosuna alır"));
        splitButton.setToolTipText(tr("waveEditor.split.tooltip",
                "İmleçte bölüm sınırı oluşturur; bölümü seçmek için çift tıklayın"));
        deleteButton.setToolTipText(tr("waveEditor.delete.tooltip", "Seçimi panoya almadan siler"));
        trimButton.setToolTipText(tr("waveEditor.trim.tooltip", "Yalnız seçili aralığı bırakır"));
        pasteButton.setToolTipText(tr("waveEditor.paste.tooltip", "Kesilen sesi imleç konumuna yapıştırır"));

        JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "play-pause");
        root.getActionMap().put("play-pause", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                if (playback.isPlaying()) playback.pause();
                else if (playback.isPaused()) {
                    playback.resume();
                    canvas.beginPlaybackFollow(model.secondsToSample(
                            absolutePlaybackSeconds(playRangeStart, playback.positionSeconds())));
                }
                else play(false);
                updateButtons();
            }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        root.getActionMap().put("delete", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { editSelection(Edit.DELETE); }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.META_DOWN_MASK), "undo");
        root.getActionMap().put("undo", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { undoButton.doClick(); }
        });
    }

    private void play(boolean shortPreview) {
        if (model.sampleCount() == 0) return;
        int start = hasSelection() ? selectionStart : cursorSample;
        int end = requestedPlaybackEnd(start, shortPreview, hasSelection(), selectionEnd,
                model.sampleCount(), model.sampleRate());
        if (end <= start) return;
        startContinuousPlayback(start, end);
    }

    private void startContinuousPlayback(int start, int end) {
        int safeStart = Math.max(0, Math.min(model.sampleCount(), start));
        int safeEnd = Math.max(safeStart, Math.min(model.sampleCount(), end));
        if (safeEnd <= safeStart) return;
        long ticket = ++playbackSessionGeneration;
        Thread previous = playbackPreparationThread;
        if (previous != null) previous.interrupt();
        playback.stop();
        continuousPlayback = true;
        playbackSessionEndSample = safeEnd;
        playbackChunkEndSample = safeStart;
        canvas.beginPlaybackFollow(safeStart);
        startPlaybackChunk(safeStart, ticket);
        updateButtons();
    }

    private void startPlaybackChunk(int start, long ticket) {
        if (ticket != playbackSessionGeneration || !continuousPlayback) return;
        int end = nextPlaybackChunkEnd(start, playbackSessionEndSample, model.sampleRate());
        if (end <= start) {
            continuousPlayback = false;
            playbackChunkStarting = false;
            canvas.stopPlaybackFollow();
            return;
        }
        playbackChunkEndSample = end;
        playbackChunkStarting = true;
        // Only one bounded block is copied, so a long edited recording never
        // needs a second full-length sample array merely for listening.
        float[] samples = model.materialize(start, end);
        AudioData preview = new AudioData(model.source().name(), model.source().source(),
                model.sampleRate(), samples, AudioChannelMode.MIX, 1);
        Thread worker = new Thread(() -> {
            try {
                if (ticket != playbackSessionGeneration || Thread.currentThread().isInterrupted()) return;
                playRangeStart = model.sampleToSeconds(start);
                playback.play(preview);
            }
            catch (Exception exception) {
                if (exception instanceof InterruptedException || Thread.currentThread().isInterrupted()) return;
                SwingUtilities.invokeLater(() -> {
                    if (ticket != playbackSessionGeneration) return;
                    continuousPlayback = false;
                    canvas.stopPlaybackFollow();
                    String message = tr("dialog.error.playback", "Ses çalınamadı");
                    showLocalizedMessage(message, javax.swing.JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                Thread completedWorker = Thread.currentThread();
                SwingUtilities.invokeLater(() -> {
                    if (ticket == playbackSessionGeneration) {
                        playbackChunkStarting = false;
                        if (playbackPreparationThread == completedWorker) {
                            playbackPreparationThread = null;
                        }
                        updateButtons();
                    }
                });
            }
        }, "makam-wave-editor-playback-" + ticket);
        worker.setDaemon(true);
        playbackPreparationThread = worker;
        worker.start();
    }

    private void stopPlayback() {
        playbackSessionGeneration++;
        Thread worker = playbackPreparationThread;
        playbackPreparationThread = null;
        if (worker != null) worker.interrupt();
        continuousPlayback = false;
        playbackChunkStarting = false;
        playbackSessionEndSample = 0;
        playbackChunkEndSample = 0;
        playback.stop();
        canvas.stopPlaybackFollow();
        canvas.playbackSeconds = Double.NaN;
        updateButtons();
        canvas.repaint();
    }

    static int requestedPlaybackEnd(int start, boolean shortPreview, boolean hasSelection,
                                    int selectionEnd, int sampleCount, float sampleRate) {
        if (shortPreview) {
            return Math.min(sampleCount, start + Math.max(1, Math.round(sampleRate * 2f)));
        }
        return Math.max(start, Math.min(sampleCount, hasSelection ? selectionEnd : sampleCount));
    }

    static int nextPlaybackChunkEnd(int start, int sessionEnd, float sampleRate) {
        long chunk = Math.max(1L, Math.round(sampleRate * PLAYBACK_CHUNK_SECONDS));
        return (int) Math.min(sessionEnd, Math.min(Integer.MAX_VALUE, start + chunk));
    }

    static double absolutePlaybackSeconds(double chunkStartSeconds, double clipPositionSeconds) {
        return chunkStartSeconds + clipPositionSeconds;
    }

    private void editSelection(Edit edit) {
        if (!hasSelection()) return;
        stopPlayback();
        int anchor = selectionStart;
        boolean selectedWholeEditedRecording = false;
        switch (edit) {
            case CUT -> model.cut(selectionStart, selectionEnd);
            case DELETE -> model.delete(selectionStart, selectionEnd);
            case TRIM -> {
                model.trimTo(selectionStart, selectionEnd);
                anchor = 0;
                selectedWholeEditedRecording = true;
            }
        }
        cursorSample = Math.min(anchor, model.sampleCount());
        if (selectedWholeEditedRecording && model.sampleCount() > 1) {
            // "Seçime kırp" makes the chosen sound the complete edited
            // timeline.  Keep that range selected so "Kaydet ve ana sayfaya
            // aktar" can restore it after the new WAV has been analysed.
            selectionStart = 0;
            selectionEnd = model.sampleCount();
            cachedSelectionPitch = null;
            lastSelectionPitchUpdateNanos = 0L;
            canvas.repaint();
        } else {
            clearSelectionAt(cursorSample);
        }
        canvas.fitIfOutside(); updateInformation();
    }

    private void clearSelectionAt(int sample) {
        selectionStart = selectionEnd = Math.max(0, Math.min(model.sampleCount(), sample));
        cachedSelectionPitch = null;
        lastSelectionPitchUpdateNanos = 0L;
        canvas.repaint();
    }

    private void updateInformation() {
        double duration = model.durationSeconds();
        double cursor = model.sampleToSeconds(cursorSample);
        if (hasSelection()) {
            selectionLabel.setText(i18n.textOrDefault("waveEditor.selection",
                    "Seçim: {0}–{1} • {2} sn",
                    formatTime(model.sampleToSeconds(selectionStart)),
                    formatTime(model.sampleToSeconds(selectionEnd)),
                    i18n.formatNumber(model.sampleToSeconds(selectionEnd - selectionStart), 3, 3)));
        } else selectionLabel.setText(i18n.textOrDefault("waveEditor.cursor",
                "İmleç: {0} • sürükleyerek aralık seçin", formatTime(cursor)));
        positionLabel.setText(formatTime(cursor) + " / " + formatTime(duration));
        PitchFrame nearest;
        if (hasSelection()) {
            long now = System.nanoTime();
            if (cachedSelectionPitch == null || now - lastSelectionPitchUpdateNanos >= 80_000_000L) {
                cachedSelectionPitch = robustSelectionFrame();
                lastSelectionPitchUpdateNanos = now;
            }
            nearest = cachedSelectionPitch;
        } else {
            cachedSelectionPitch = null;
            lastSelectionPitchUpdateNanos = 0L;
            nearest = nearestFrame(model.sourceSecondsAt(cursorSample));
        }
        if (nearest == null) pitchLabel.setText(tr("waveEditor.pitch.unreliable",
                "Anlık perde: güvenilir F0 yok"));
        else {
            String relative = "";
            if (Double.isFinite(tonicHertz) && tonicHertz > 0) {
                double cent = 1200.0 * Math.log(nearest.hertz() / tonicHertz) / Math.log(2.0);
                relative = " • " + i18n.formatSignedNumber(cent, 2, 2) + " C / "
                        + i18n.formatSignedNumber(cent / AnalysisConfig.COMMA_CENT, 3, 3) + " Hc";
            }
            pitchLabel.setText(i18n.textOrDefault("waveEditor.pitch.value",
                    "{0}: {1} Hz{2} • güven {3}",
                    hasSelection() ? tr("waveEditor.selectionMedian", "Seçim medyanı")
                            : tr("waveEditor.instantPitch", "Anlık perde"),
                    i18n.formatNumber(nearest.hertz(), 3, 3), relative,
                    i18n.formatPercent(nearest.confidence(), 0)));
        }
        updateButtons(); canvas.repaint();
    }

    private PitchFrame nearestFrame(double sourceSeconds) {
        PitchFrame best = null;
        double distance = .12;
        int first = lowerBoundFrame(sourceSeconds - distance);
        for (int index = first; index < pitchFrames.size(); index++) {
            PitchFrame frame = pitchFrames.get(index);
            if (frame.timeSeconds() > sourceSeconds + distance) break;
            PitchFrame displayed = selectionEvidenceFrame(frame);
            if (displayed == null) continue;
            double current = Math.abs(frame.timeSeconds() - sourceSeconds);
            if (current < distance) { distance = current; best = displayed; }
        }
        return best;
    }

    private PitchFrame robustSelectionFrame() {
        List<WaveformEditModel.SourceRange> ranges = model.sourceRanges(selectionStart, selectionEnd);
        TonicSelectionEstimator.Estimate estimate = TonicSelectionEstimator.estimate(pitchFrames,
                time -> includedInRanges(time, ranges));
        if (estimate == null) return null;
        return new PitchFrame(model.sampleToSeconds(selectionStart), estimate.hertz(),
                estimate.absoluteCent(), estimate.confidence(), 0.0);
    }

    private static boolean includedInRanges(double seconds,
                                            List<WaveformEditModel.SourceRange> ranges) {
        for (WaveformEditModel.SourceRange range : ranges) {
            if (seconds >= range.startSeconds() && seconds <= range.endSeconds()) return true;
        }
        return false;
    }

    private static PitchFrame selectionEvidenceFrame(PitchFrame frame) {
        if (frame.voiced() && frame.confidence() >= PitchTracker.RELIABLE_CONFIDENCE) return frame;
        if (!frame.candidateVoiced()
                || frame.confidence() < PitchTracker.VISUAL_CANDIDATE_CONFIDENCE) return null;
        return new PitchFrame(frame.timeSeconds(), frame.candidateHertz(),
                frame.candidateAbsoluteCent(), frame.confidence(), frame.rms());
    }

    private int lowerBoundFrame(double seconds) {
        int low = 0, high = pitchFrames.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (pitchFrames.get(middle).timeSeconds() < seconds) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static double median(double[] values) {
        if (values.length == 0) return Double.NaN;
        int middle = values.length / 2;
        return values.length % 2 == 0 ? (values[middle - 1] + values[middle]) / 2.0 : values[middle];
    }

    private void updateButtons() {
        boolean selection = hasSelection();
        playButton.setEnabled(!saving && model.sampleCount() > 0);
        previewButton.setEnabled(!saving && model.sampleCount() > 0);
        cutButton.setEnabled(!saving && selection);
        deleteButton.setEnabled(!saving && selection);
        trimButton.setEnabled(!saving && selection);
        splitButton.setEnabled(!saving && cursorSample > 0 && cursorSample < model.sampleCount());
        pasteButton.setEnabled(!saving && model.hasClipboard());
        undoButton.setEnabled(!saving && model.canUndo());
        pauseButton.setEnabled(!saving && (playback.isPlaying() || playback.isPaused()));
        stopButton.setEnabled(!saving && (playback.hasClip() || continuousPlayback));
        applyButton.setEnabled(!saving && model.sampleCount() > 0);
        canvas.setEnabled(!saving);
    }

    private void refreshPlayback() {
        double editorPosition = playback.hasClip()
                ? absolutePlaybackSeconds(playRangeStart, playback.positionSeconds()) : playRangeStart;
        boolean playing = playback.isPlaying();
        if (continuousPlayback && !playing && !playback.isPaused()
                && playback.hasClip() && !playbackChunkStarting
                && editorPosition >= model.sampleToSeconds(playbackChunkEndSample) - .08) {
            if (playbackChunkEndSample < playbackSessionEndSample) {
                startPlaybackChunk(playbackChunkEndSample, playbackSessionGeneration);
            } else {
                continuousPlayback = false;
                canvas.stopPlaybackFollow();
            }
        }
        if (playback.hasClip() || (continuousPlayback && playbackChunkStarting)) {
            positionLabel.setText(formatTime(editorPosition) + " / " + formatTime(model.durationSeconds()));
            canvas.playbackSeconds = editorPosition;
            if (playing) canvas.followPlayback(editorPosition);
        } else canvas.playbackSeconds = Double.NaN;
        canvas.repaint(); updateButtons();
    }

    private void saveAndUse() {
        if (saving) return;
        String base = model.source().name().replaceFirst("(?i)\\.[^.]+$", "");
        Path target = NativeFileDialogs.save(this,
                tr("waveEditor.save.title", "Düzenlenmiş WAV dosyasını kaydet"),
                base + tr("waveEditor.fileSuffix", "-duzenlenmis") + ".wav");
        if (target == null) return;
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav")) {
            target = target.resolveSibling(target.getFileName() + ".wav");
        }
        stopPlayback();
        Path finalTarget = target;
        double transferredSelectionStart = hasSelection()
                ? model.sampleToSeconds(selectionStart) : Double.NaN;
        double transferredSelectionEnd = hasSelection()
                ? model.sampleToSeconds(selectionEnd) : Double.NaN;
        saving = true;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        ProgressEta eta = new ProgressEta();
        eta.start();
        updateButtons();
        SwingWorker<EditResult, Integer> worker = new SwingWorker<>() {
            @Override protected EditResult doInBackground() throws Exception {
                writeWave(finalTarget, model, value -> publish(value));
                return new EditResult(finalTarget, transferredSelectionStart,
                        transferredSelectionEnd);
            }

            @Override protected void process(List<Integer> chunks) {
                int value = chunks.get(chunks.size() - 1);
                selectionLabel.setText(i18n.textOrDefault("waveEditor.saving",
                        "Düzenlenmiş WAV kaydediliyor • {0}", eta.describe(value)));
            }

            @Override protected void done() {
                try {
                    savedResult = get();
                    saving = false;
                    MakamWaveEditorDialog.this.dispose();
                } catch (Exception exception) {
                    try { Files.deleteIfExists(finalTarget); } catch (IOException ignored) { }
                    saving = false;
                    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                    setCursor(Cursor.getDefaultCursor());
                    updateInformation();
                    String message = tr("waveEditor.save.error",
                            "Düzenlenmiş ses kaydedilemedi");
                    showLocalizedMessage(message, javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    @Override public void dispose() {
        if (saving) return;
        stopPlayback();
        timer.stop(); playback.close(); super.dispose();
    }

    private boolean hasSelection() { return selectionEnd - selectionStart > 1; }

    static void writeWave(Path target, WaveformEditModel model) throws IOException {
        writeWave(target, model, ignored -> { });
    }

    static void writeWave(Path target, WaveformEditModel model,
                          IntConsumer progress) throws IOException {
        long dataBytes = model.sampleCount() * 2L;
        if (dataBytes > 0xfffffff0L) throw new IOException(I18n.get().textOrDefault(
                "waveEditor.save.tooLarge", "Düzenlenmiş kayıt WAV sınırını aşıyor"));
        try (OutputStream raw = new BufferedOutputStream(Files.newOutputStream(target), 256 * 1024);
             DataOutputStream output = new DataOutputStream(raw)) {
            output.writeBytes("RIFF"); writeLeInt(output, 36 + (int) dataBytes);
            output.writeBytes("WAVEfmt "); writeLeInt(output, 16); writeLeShort(output, 1);
            writeLeShort(output, 1); int rate = Math.round(model.sampleRate()); writeLeInt(output, rate);
            writeLeInt(output, rate * 2); writeLeShort(output, 2); writeLeShort(output, 16);
            output.writeBytes("data"); writeLeInt(output, (int) dataBytes);
            model.writePcm16(output, progress);
        }
    }

    private static void writeLeShort(DataOutputStream output, int value) throws IOException {
        output.writeByte(value & 0xff); output.writeByte((value >>> 8) & 0xff);
    }

    private static void writeLeInt(DataOutputStream output, int value) throws IOException {
        output.writeByte(value & 0xff); output.writeByte((value >>> 8) & 0xff);
        output.writeByte((value >>> 16) & 0xff); output.writeByte((value >>> 24) & 0xff);
    }

    private static JPanel row() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        panel.setOpaque(false); panel.setAlignmentX(Component.LEFT_ALIGNMENT); return panel;
    }

    private static JButton button(String text) {
        JButton button = new AutoFitButton(text); button.setFocusPainted(false); return button;
    }

    private String tr(String key, String fallback) {
        return i18n.textOrDefault(key, fallback);
    }

    private void showLocalizedMessage(String message, int messageType) {
        String close = tr("common.close", "Kapat");
        javax.swing.JOptionPane.showOptionDialog(this, message, "MakamLab",
                javax.swing.JOptionPane.DEFAULT_OPTION, messageType, null,
                new Object[]{close}, close);
    }

    private void syncTimelineScroll(int viewStart, int viewEnd) {
        int maximum = Math.max(1, model.sampleCount());
        int extent = Math.max(1, Math.min(maximum, viewEnd - viewStart));
        int value = Math.max(0, Math.min(maximum - extent, viewStart));
        updatingTimelineScroll = true;
        try {
            timelineScroll.setValues(value, extent, 0, maximum);
            timelineScroll.setUnitIncrement(Math.max(1, extent / 20));
            timelineScroll.setBlockIncrement(Math.max(1, extent * 4 / 5));
        } finally {
            updatingTimelineScroll = false;
        }
    }

    static int centeredViewStart(int sample, int span, int length) {
        int safeLength = Math.max(1, length);
        int safeSpan = Math.max(1, Math.min(safeLength, span));
        return Math.max(0, Math.min(safeLength - safeSpan, sample - safeSpan / 2));
    }

    private String formatTime(double seconds) {
        seconds = Math.max(0.0, seconds);
        return String.format(i18n.locale(), "%d:%05.2f", (int) seconds / 60, seconds % 60.0);
    }

    private enum Edit { CUT, DELETE, TRIM }

    record EditResult(Path path, double selectionStartSeconds, double selectionEndSeconds) { }

    private final class WaveCanvas extends JPanel {
        private int viewStart;
        private int viewEnd = 1;
        private int pressSample;
        private int pressX;
        private double playbackSeconds = Double.NaN;
        private boolean playbackFollow;
        private boolean resumePlaybackAfterClick;

        WaveCanvas() {
            setOpaque(true); setBackground(CANVAS); setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    resumePlaybackAfterClick = playback.isPlaying() || continuousPlayback;
                    playbackFollow = false;
                    pressX = event.getX(); pressSample = xToSample(event.getX());
                    cursorSample = pressSample; selectionStart = selectionEnd = pressSample;
                    cachedSelectionPitch = null; lastSelectionPitchUpdateNanos = 0L;
                    updateInformation();
                }
                @Override public void mouseDragged(MouseEvent event) {
                    cursorSample = xToSample(event.getX());
                    selectionStart = Math.min(pressSample, cursorSample);
                    selectionEnd = Math.max(pressSample, cursorSample);
                    updateInformation();
                }
                @Override public void mouseReleased(MouseEvent event) {
                    cursorSample = xToSample(event.getX());
                    if (Math.abs(event.getX() - pressX) < 4) selectionStart = selectionEnd = cursorSample;
                    else {
                        selectionStart = Math.min(pressSample, cursorSample);
                        selectionEnd = Math.max(pressSample, cursorSample);
                    }
                    if (event.getClickCount() >= 2) selectSplitSegment(cursorSample);
                    cachedSelectionPitch = null; lastSelectionPitchUpdateNanos = 0L;
                    updateInformation();
                    if (resumePlaybackAfterClick && event.getClickCount() == 1
                            && Math.abs(event.getX() - pressX) < 4) {
                        play(false);
                    }
                    resumePlaybackAfterClick = false;
                }
                @Override public void mouseWheelMoved(MouseWheelEvent event) {
                    if (event.isShiftDown()) pan(event.getPreciseWheelRotation() * .08);
                    else zoom(Math.pow(1.18, event.getPreciseWheelRotation()), event.getX());
                }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse); addMouseWheelListener(mouse);
            TrackpadNavigationSupport.install(this, gesture -> {
                switch (gesture.action()) {
                    case TIME_ZOOM -> zoom(Math.pow(1.16, gesture.amount()), gesture.x());
                    case TIME_PAN -> pan(gesture.amount() * .035);
                    case PITCH_ZOOM, PITCH_PAN -> { }
                }
            });
            TrackpadZoomSupport.install(this, amount -> zoom(Math.exp(-amount * 1.8), getWidth() / 2));
        }

        void fit() {
            boolean keepPlaybackFollow = playbackFollow
                    && (playback.isPlaying() || continuousPlayback);
            playbackFollow = keepPlaybackFollow;
            viewStart = 0;
            viewEnd = Math.max(1, model.sampleCount());
            syncTimelineScroll(viewStart, viewEnd);
            repaint();
        }
        void fitIfOutside() {
            int length = Math.max(1, model.sampleCount());
            viewStart = Math.min(viewStart, Math.max(0, length - 1));
            viewEnd = Math.max(viewStart + 1, Math.min(viewEnd, length));
            if (viewEnd - viewStart < 2 || viewStart >= length) fit();
            else { syncTimelineScroll(viewStart, viewEnd); repaint(); }
        }
        void reveal(int sample) {
            if (sample >= viewStart && sample <= viewEnd) return;
            int span = Math.max(1, viewEnd - viewStart);
            viewStart = Math.max(0, Math.min(Math.max(0, model.sampleCount() - span), sample - span / 2));
            viewEnd = Math.min(model.sampleCount(), viewStart + span);
            syncTimelineScroll(viewStart, viewEnd);
            repaint();
        }
        void zoom(double factor) { zoom(factor, getWidth() / 2); }
        void zoom(double factor, int anchorX) {
            // Keep the playhead inside the editor after pinch or +/- zoom while
            // audio is running. Explicit pan/scroll remains a manual override.
            boolean keepPlaybackFollow = playbackFollow
                    && (playback.isPlaying() || continuousPlayback)
                    && Double.isFinite(playbackSeconds);
            if (!keepPlaybackFollow) playbackFollow = false;
            int length = Math.max(1, model.sampleCount());
            int span = Math.max(1, viewEnd - viewStart);
            int next = Math.max(Math.round(model.sampleRate() * .12f),
                    Math.min(length, (int) Math.round(span * factor)));
            int anchor = xToSample(anchorX);
            double ratio = (anchor - viewStart) / (double) span;
            viewStart = Math.max(0, Math.min(length - next, anchor - (int) Math.round(next * ratio)));
            viewEnd = viewStart + next;
            if (keepPlaybackFollow) centerOn(model.secondsToSample(playbackSeconds));
            else syncTimelineScroll(viewStart, viewEnd);
            repaint();
        }
        void pan(double fraction) {
            playbackFollow = false;
            int span = Math.max(1, viewEnd - viewStart);
            int delta = (int) Math.round(span * fraction);
            viewStart = Math.max(0, Math.min(Math.max(0, model.sampleCount() - span), viewStart + delta));
            viewEnd = Math.min(model.sampleCount(), viewStart + span);
            syncTimelineScroll(viewStart, viewEnd);
            repaint();
        }

        void scrollTo(int requestedStart, boolean manual) {
            if (manual) playbackFollow = false;
            int length = Math.max(1, model.sampleCount());
            int span = Math.max(1, Math.min(length, viewEnd - viewStart));
            viewStart = Math.max(0, Math.min(length - span, requestedStart));
            viewEnd = viewStart + span;
            syncTimelineScroll(viewStart, viewEnd);
            repaint();
        }

        void beginPlaybackFollow(int sample) {
            playbackFollow = true;
            centerOn(sample);
        }

        void stopPlaybackFollow() { playbackFollow = false; }

        void followPlayback(double seconds) {
            if (!playbackFollow) return;
            centerOn(model.secondsToSample(seconds));
        }

        private void centerOn(int sample) {
            int length = Math.max(1, model.sampleCount());
            int span = Math.max(1, Math.min(length, viewEnd - viewStart));
            int nextStart = centeredViewStart(sample, span, length);
            if (nextStart == viewStart && viewEnd == nextStart + span) return;
            viewStart = nextStart;
            viewEnd = viewStart + span;
            syncTimelineScroll(viewStart, viewEnd);
            repaint();
        }

        private void selectSplitSegment(int sample) {
            int start = 0, end = model.sampleCount();
            for (int point : model.splitPoints()) {
                if (point <= sample) start = point;
                else { end = point; break; }
            }
            selectionStart = start; selectionEnd = end; cursorSample = start;
        }

        private int xToSample(int x) {
            int left = 22, right = Math.max(left + 1, getWidth() - 22);
            double ratio = Math.max(0.0, Math.min(1.0, (x - left) / (double) (right - left)));
            return Math.max(0, Math.min(model.sampleCount(), viewStart
                    + (int) Math.round(ratio * Math.max(1, viewEnd - viewStart))));
        }
        private int sampleToX(int sample) {
            int left = 22, right = Math.max(left + 1, getWidth() - 22);
            return left + (int) Math.round((sample - viewStart) / (double) Math.max(1, viewEnd - viewStart)
                    * (right - left));
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int left = 22, right = getWidth() - 22, top = 22, bottom = getHeight() - 42;
            int center = (top + bottom) / 2;
            g.setColor(GRID); g.drawLine(left, center, right, center);
            int pixels = Math.max(1, right - left);
            double samplesPerPixel = Math.max(1.0, (viewEnd - viewStart) / (double) pixels);
            int[] upper = new int[pixels + 1];
            int[] lower = new int[pixels + 1];
            for (int x = left; x <= right; x++) {
                int start = viewStart + (int) Math.floor((x - left) * samplesPerPixel);
                int end = Math.min(viewEnd, start + Math.max(1, (int) Math.ceil(samplesPerPixel)));
                WaveformCache.Range range = model.range(start, end);
                int y1 = center - (int) Math.round(range.maximum() * (bottom - top) * .46);
                int y2 = center - (int) Math.round(range.minimum() * (bottom - top) * .46);
                upper[x - left] = Math.min(y1, y2);
                lower[x - left] = Math.max(y1, y2);
            }
            g.setStroke(new BasicStroke(1f));
            g.setColor(WAVE_SHADOW);
            for (int x = left; x <= right; x++) {
                int index = x - left;
                g.drawLine(x + 1, upper[index] + 1, x + 1, lower[index] + 1);
            }
            g.setColor(WAVE);
            for (int x = left; x <= right; x++) {
                int index = x - left;
                g.drawLine(x, upper[index], x, lower[index]);
            }

            if (hasSelection()) {
                int x1 = sampleToX(selectionStart), x2 = sampleToX(selectionEnd);
                g.setColor(new Color(59, 130, 246, 52)); g.fillRect(x1, top, Math.max(2, x2 - x1), bottom - top);
                g.setColor(new Color(37, 99, 235)); g.drawLine(x1, top, x1, bottom); g.drawLine(x2, top, x2, bottom);
            }
            for (int split : model.splitPoints()) {
                if (split <= viewStart || split >= viewEnd) continue;
                int x = sampleToX(split); g.setColor(CRIMSON); g.setStroke(new BasicStroke(2f));
                g.drawLine(x, top, x, bottom); g.fillPolygon(new int[]{x - 5, x + 5, x},
                        new int[]{top, top, top + 7}, 3);
            }
            int cursorX = sampleToX(cursorSample);
            g.setColor(new Color(17, 24, 39)); g.setStroke(new BasicStroke(1.5f));
            g.drawLine(cursorX, top, cursorX, bottom);
            if (Double.isFinite(playbackSeconds)) {
                int playX = sampleToX(model.secondsToSample(playbackSeconds));
                g.setColor(CRIMSON); g.setStroke(new BasicStroke(2.4f)); g.drawLine(playX, top, playX, bottom);
            }
            g.setFont(getFont().deriveFont(11f)); g.setColor(new Color(95, 104, 120));
            for (int tick = 0; tick <= 6; tick++) {
                int x = left + tick * (right - left) / 6;
                double seconds = model.sampleToSeconds(viewStart + tick * (viewEnd - viewStart) / 6);
                String label = formatTime(seconds);
                if (tick == 6) x -= g.getFontMetrics().stringWidth(label);
                g.drawString(label, x, bottom + 24);
            }
            g.dispose();
        }
    }
}
