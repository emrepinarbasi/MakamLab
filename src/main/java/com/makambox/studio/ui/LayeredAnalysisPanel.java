package com.makambox.studio.ui;

import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.AnalysisReport;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.EventType;
import com.makambox.studio.model.GlissandoEvent;
import com.makambox.studio.model.PitchFrame;

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.SwingWorker;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.DoubleConsumer;

public final class LayeredAnalysisPanel extends JPanel {
    private static final int SYNCHRONOUS_WAVEFORM_LIMIT = 500_000;
    private static final int SCROLL_SCALE = 1_000_000;
    private static final Color WAVEFORM_SHADOW = new Color(0, 0, 0, 92);
    private static final Color WAVEFORM_CRIMSON = new Color(205, 25, 55);

    private AudioData audio;
    private WaveformCache waveformCache = new WaveformCache(null);
    private SwingWorker<WaveformCache, Void> waveformCacheWorker;
    private AudioData waveformCacheSource;
    private boolean waveformCacheBuilding;
    private AnalysisReport report;
    private BufferedImage spectrogram;
    private double viewStart;
    private double viewEnd = 1.0;
    private double pitchCenterCent;
    private double pitchSpanCent = 1040.0;
    private double selectionStart = Double.NaN;
    private double selectionEnd = Double.NaN;
    private int dragX = -1;
    private double dragStartTime = Double.NaN;
    private BiConsumer<Double, Double> selectionListener;
    private DoubleConsumer seekListener;
    private DoubleConsumer tonicPickListener;
    private boolean tonicPickMode;
    private boolean showWaveform = true;
    private boolean showSpectrogram = true;
    private boolean showPitch = true;
    private boolean showEvents = true;
    /** This instance is a dedicated waveform canvas rather than a layered melogram. */
    private boolean waveformOnly;
    private double exclusiveRangeLowerCent = Double.NaN;
    private double exclusiveRangeUpperCent = Double.NaN;
    private double playbackSeconds = Double.NaN;
    private boolean playbackActive;
    private boolean playbackFollowing;
    private String emptyAudioMessage = "Bir ses dosyası açın veya örnek melodiyi yükleyin";
    private String tonicPickInstruction = "Karar seçimi: karar kabul edeceğiniz perde izine tıklayın";
    private String excludedBoundaryLabel = "sınır hariç";
    private String navigationHint = "İki parmak ↕: perdede gezin • ↔: zamanda ilerle • Sıkıştır/aç: yatay yakınlaştır • Option+↕: dikey yakınlaştır";
    private String waveformBuildingMessage = "Dalga biçimi arka planda hazırlanıyor…";
    private final JScrollBar timeScrollBar = new JScrollBar(JScrollBar.HORIZONTAL);
    private boolean updatingScrollBar;

    public LayeredAnalysisPanel() {
        setBackground(UiTheme.CANVAS);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (tonicPickMode) return;
                dragX = event.getX();
                dragStartTime = xToTime(event.getX());
            }
            @Override public void mouseDragged(MouseEvent event) {
                if (tonicPickMode) return;
                if (dragX < 0 || Math.abs(event.getX() - dragX) < 4) return;
                selectionStart = dragStartTime;
                selectionEnd = xToTime(event.getX()); repaint();
            }
            @Override public void mouseReleased(MouseEvent event) {
                if (tonicPickMode) {
                    pickTonicAt(event.getX(), event.getY());
                    return;
                }
                boolean click = Math.abs(event.getX() - dragX) < 4;
                double clickedTime = xToTime(event.getX());
                if (click) {
                    // A click is a playback/position gesture, never a tonic
                    // selection. Preserve any existing dragged selection.
                    if (seekListener != null) seekListener.accept(clickedTime);
                    dragX = -1;
                    dragStartTime = Double.NaN;
                    repaint();
                    return;
                }
                selectionStart = dragStartTime;
                selectionEnd = clickedTime;
                if (selectionStart > selectionEnd) { double swap = selectionStart; selectionStart = selectionEnd; selectionEnd = swap; }
                if (selectionListener != null) selectionListener.accept(selectionStart, selectionEnd);
                dragX = -1;
                dragStartTime = Double.NaN;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        TrackpadNavigationSupport.install(this, gesture -> {
            switch (gesture.action()) {
                case TIME_ZOOM -> zoomTime(Math.pow(1.16, gesture.amount()), gesture.x());
                case TIME_PAN -> panTime(gesture.amount() * Math.max(0.05, (viewEnd - viewStart) * 0.035));
                case PITCH_ZOOM -> zoomPitch(Math.pow(1.16, gesture.amount()));
                case PITCH_PAN -> panPitch(gesture.amount() * pitchSpanCent * 0.035);
            }
        });
        TrackpadZoomSupport.install(this,
                magnification -> zoomTime(Math.exp(-magnification * 1.8), getWidth() / 2));
        timeScrollBar.setUnitIncrement(6_000);
        timeScrollBar.setBlockIncrement(100_000);
        timeScrollBar.setName("makam-main-wave-timeline-scroll");
        timeScrollBar.setPreferredSize(new Dimension(120, 18));
        timeScrollBar.setMinimumSize(new Dimension(36, 18));
        timeScrollBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        timeScrollBar.addAdjustmentListener(event -> {
            if (updatingScrollBar || audio == null) return;
            playbackFollowing = false;
            double span = viewEnd - viewStart;
            double maximumStart = Math.max(0.0, audio.durationSeconds() - span);
            int maximumValue = Math.max(1, timeScrollBar.getMaximum() - timeScrollBar.getVisibleAmount());
            viewStart = maximumStart * timeScrollBar.getValue() / maximumValue;
            viewEnd = Math.min(audio.durationSeconds(), viewStart + span);
            repaint();
        });
    }

    /**
     * Supplies the four short canvas messages without coupling this reusable
     * component to a particular application or resource bundle.  MakamLab
     * refreshes them when its language changes; Studio keeps its established
     * defaults unless it explicitly opts in.
     */
    public void setLocalizedCanvasText(String emptyAudioMessage,
                                       String tonicPickInstruction,
                                       String excludedBoundaryLabel,
                                       String navigationHint,
                                       String waveformBuildingMessage) {
        this.emptyAudioMessage = nonBlankOr(emptyAudioMessage, this.emptyAudioMessage);
        this.tonicPickInstruction = nonBlankOr(tonicPickInstruction, this.tonicPickInstruction);
        this.excludedBoundaryLabel = nonBlankOr(excludedBoundaryLabel, this.excludedBoundaryLabel);
        this.navigationHint = nonBlankOr(navigationHint, this.navigationHint);
        this.waveformBuildingMessage = nonBlankOr(waveformBuildingMessage,
                this.waveformBuildingMessage);
        repaint();
    }

    private static String nonBlankOr(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate;
    }

    public void setData(AudioData audio, AnalysisReport report, BufferedImage spectrogram) {
        if (this.audio != audio) prepareWaveformCache(audio);
        this.audio = audio;
        this.report = report;
        this.spectrogram = spectrogram;
        this.viewStart = 0.0;
        this.viewEnd = audio == null ? 1.0 : audio.durationSeconds();
        this.pitchCenterCent = report == null ? 0.0 : report.config().expectedAbsoluteCent();
        this.pitchSpanCent = 1040.0;
        this.selectionStart = this.selectionEnd = Double.NaN;
        this.playbackFollowing = false;
        updateTimeScrollBar();
        repaint();
    }

    private void prepareWaveformCache(AudioData requestedAudio) {
        if (waveformCacheWorker != null) {
            waveformCacheWorker.cancel(true);
            waveformCacheWorker = null;
        }
        waveformCacheSource = requestedAudio;
        waveformCacheBuilding = false;
        if (requestedAudio == null) {
            waveformCache = new WaveformCache(null);
            return;
        }
        float[] samples = requestedAudio.samples();
        if (samples.length <= SYNCHRONOUS_WAVEFORM_LIMIT) {
            waveformCache = new WaveformCache(samples);
            return;
        }

        waveformCache = new WaveformCache(null);
        waveformCacheBuilding = true;
        SwingWorker<WaveformCache, Void> worker = new SwingWorker<>() {
            @Override protected WaveformCache doInBackground() {
                return new WaveformCache(samples);
            }

            @Override protected void done() {
                if (waveformCacheWorker != this || waveformCacheSource != requestedAudio) return;
                try {
                    if (!isCancelled()) waveformCache = get();
                } catch (CancellationException ignored) {
                    // Superseded audio source; no UI update is needed.
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ignored) {
                    // Keep the empty cache. The audio and every other analysis layer remain usable.
                } finally {
                    if (waveformCacheWorker == this) {
                        waveformCacheWorker = null;
                        waveformCacheBuilding = false;
                        repaint();
                    }
                }
            }
        };
        waveformCacheWorker = worker;
        worker.execute();
    }

    public void setSelectionListener(BiConsumer<Double, Double> listener) { selectionListener = listener; }
    public void setSeekListener(DoubleConsumer listener) { seekListener = listener; }
    public JScrollBar timeScrollBar() { return timeScrollBar; }
    public void setSelection(double startSeconds, double endSeconds) {
        selectionStart = startSeconds;
        selectionEnd = endSeconds;
        repaint();
    }
    public void setPlaybackPosition(double seconds, boolean active) {
        playbackSeconds = seconds;
        playbackActive = active;
        if (active && playbackFollowing) centerPlayback(seconds);
        repaint();
    }
    public void beginPlaybackFollow(double seconds) {
        playbackFollowing = true;
        if (audio != null && audio.durationSeconds() > 45.0 && viewEnd - viewStart > 30.0) {
            double span = Math.min(30.0, audio.durationSeconds());
            viewStart = Math.max(0.0,
                    Math.min(audio.durationSeconds() - span, seconds - span / 2.0));
            viewEnd = viewStart + span;
        }
        centerPlayback(seconds);
        repaint();
    }
    public void stopPlaybackFollow() { playbackFollowing = false; }
    public void setTonicPickListener(DoubleConsumer listener) { tonicPickListener = listener; }
    public void setTonicPickMode(boolean enabled) {
        tonicPickMode = enabled;
        setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.CROSSHAIR_CURSOR));
        repaint();
    }
    public double selectionStart() { return selectionStart; }
    public double selectionEnd() { return selectionEnd; }
    public boolean hasSelection() { return Double.isFinite(selectionStart) && selectionEnd - selectionStart > 0.02; }
    public void zoomToFit() {
        if (audio != null) {
            boolean keepPlaybackFollow = playbackFollowing && playbackActive
                    && Double.isFinite(playbackSeconds);
            playbackFollowing = keepPlaybackFollow;
            viewStart = 0; viewEnd = audio.durationSeconds();
            if (report != null) pitchCenterCent = report.config().expectedAbsoluteCent();
            pitchSpanCent = 1040.0;
            updateTimeScrollBar();
            repaint();
        }
    }
    public void zoomTime(double factor) { zoomTime(factor, getWidth() / 2); }
    public void zoomPitch(double factor) {
        pitchSpanCent = Math.max(90.0, Math.min(3600.0, pitchSpanCent * factor)); repaint();
    }
    public void panPitch(double deltaCent) { pitchCenterCent += deltaCent; repaint(); }
    public void panTime(double deltaSeconds) {
        if (audio == null) return;
        playbackFollowing = false;
        double duration = viewEnd - viewStart;
        viewStart = Math.max(0.0, Math.min(audio.durationSeconds() - duration, viewStart + deltaSeconds));
        viewEnd = viewStart + duration;
        updateTimeScrollBar();
        repaint();
    }
    public void setLayers(boolean waveform, boolean spectrum, boolean pitch, boolean events) {
        showWaveform = waveform; showSpectrogram = spectrum; showPitch = pitch; showEvents = events; repaint();
    }
    public void setWaveformOnly(boolean waveformOnly) {
        this.waveformOnly = waveformOnly;
        repaint();
    }
    public void setExclusivePitchRange(double lowerAbsoluteCent, double upperAbsoluteCent) {
        exclusiveRangeLowerCent = Math.min(lowerAbsoluteCent, upperAbsoluteCent);
        exclusiveRangeUpperCent = Math.max(lowerAbsoluteCent, upperAbsoluteCent);
        repaint();
    }
    public void clearExclusivePitchRange() {
        exclusiveRangeLowerCent = exclusiveRangeUpperCent = Double.NaN;
        repaint();
    }

    private void zoomTime(double factor, int anchorX) {
        if (audio == null) return;
        // Zooming must not make the red playback cursor escape from the wave
        // window.  A deliberate horizontal pan/scroll still suspends follow,
        // but magnification during playback keeps the cursor centred.
        boolean keepPlaybackFollow = playbackFollowing && playbackActive
                && Double.isFinite(playbackSeconds);
        if (!keepPlaybackFollow) playbackFollowing = false;
        double duration = viewEnd - viewStart;
        double next = Math.max(0.25, Math.min(audio.durationSeconds(), duration * factor));
        double anchor = xToTime(anchorX);
        double ratio = (anchor - viewStart) / duration;
        viewStart = Math.max(0.0, Math.min(audio.durationSeconds() - next, anchor - next * ratio));
        viewEnd = viewStart + next;
        if (keepPlaybackFollow) centerPlayback(playbackSeconds);
        else updateTimeScrollBar();
        repaint();
    }

    private void centerPlayback(double seconds) {
        if (audio == null || !Double.isFinite(seconds)) return;
        double span = Math.max(0.25, Math.min(audio.durationSeconds(), viewEnd - viewStart));
        if (span >= audio.durationSeconds() - 0.001) return;
        viewStart = Math.max(0.0,
                Math.min(audio.durationSeconds() - span, seconds - span / 2.0));
        viewEnd = viewStart + span;
        updateTimeScrollBar();
    }

    private void updateTimeScrollBar() {
        updatingScrollBar = true;
        try {
            if (audio == null || audio.durationSeconds() <= 0.0) {
                timeScrollBar.setValues(0, SCROLL_SCALE, 0, SCROLL_SCALE);
                timeScrollBar.setEnabled(false);
                return;
            }
            double duration = audio.durationSeconds();
            double span = Math.max(0.0, Math.min(duration, viewEnd - viewStart));
            int extent = Math.max(1, Math.min(SCROLL_SCALE,
                    (int) Math.round(span / duration * SCROLL_SCALE)));
            int maximumValue = Math.max(0, SCROLL_SCALE - extent);
            double maximumStart = Math.max(0.0, duration - span);
            int value = maximumStart <= 0.0 ? 0 : (int) Math.round(
                    Math.max(0.0, Math.min(maximumStart, viewStart)) / maximumStart * maximumValue);
            timeScrollBar.setValues(value, extent, 0, SCROLL_SCALE);
            timeScrollBar.setBlockIncrement(Math.max(1, extent * 4 / 5));
            timeScrollBar.setUnitIncrement(Math.max(1, extent / 20));
            timeScrollBar.setEnabled(extent < SCROLL_SCALE);
        } finally {
            updatingScrollBar = false;
        }
    }

    double diagnosticViewStart() { return viewStart; }
    double diagnosticViewEnd() { return viewEnd; }
    boolean diagnosticPlaybackFollowing() { return playbackFollowing; }
    boolean diagnosticWaveformOnly() { return waveformOnly; }
    String diagnosticEmptyAudioMessage() { return emptyAudioMessage; }
    String diagnosticTonicPickInstruction() { return tonicPickInstruction; }

    private double xToTime(int x) {
        int left = 54, right = Math.max(left + 1, getWidth() - 20);
        double ratio = Math.max(0, Math.min(1, (x - left) / (double) (right - left)));
        return viewStart + ratio * (viewEnd - viewStart);
    }

    private int timeToX(double time) {
        int left = 54, right = Math.max(left + 1, getWidth() - 20);
        return left + (int) Math.round((time - viewStart) / Math.max(0.001, viewEnd - viewStart) * (right - left));
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int left = 54, right = getWidth() - 20, top = 22, bottom = getHeight() - 42;
        if (audio == null) {
            g.setColor(UiTheme.MUTED);
            g.setFont(getFont().deriveFont(Font.BOLD, 17f));
            String message = emptyAudioMessage;
            g.drawString(message, (getWidth() - g.getFontMetrics().stringWidth(message)) / 2, getHeight() / 2);
            g.dispose(); return;
        }

        if (waveformOnly) {
            // MakamLab already has a separate analysis/result window below.
            // Give the waveform the full canvas instead of repeating a melogram.
            drawWaveform(g, left, right, top, bottom);
            drawSelection(g, top, bottom);
            drawPlayback(g, top, bottom);
            drawRuler(g, left, right, bottom);
            g.dispose();
            return;
        }

        int waveBottom = top + Math.max(85, (bottom - top) / 4);
        int spectrumTop = showWaveform ? waveBottom + 12 : top;
        if (showWaveform) drawWaveform(g, left, right, top, waveBottom);
        if (showSpectrogram && spectrogram != null) drawSpectrogram(g, left, right, spectrumTop, bottom);
        else { g.setColor(UiTheme.SURFACE); g.fillRoundRect(left, spectrumTop, right - left, bottom - spectrumTop, 12, 12); }
        if (report != null && showPitch) drawPitch(g, left, right, spectrumTop, bottom);
        if (report != null && showEvents) drawEvents(g, spectrumTop, bottom);
        drawSelection(g, top, bottom);
        drawPlayback(g, top, bottom);
        drawTonicPickHint(g, left, spectrumTop);
        drawRuler(g, left, right, bottom);
        g.dispose();
    }

    private void pickTonicAt(int mouseX, int mouseY) {
        if (report == null || tonicPickListener == null) return;
        int top = 22, bottom = getHeight() - 42;
        int waveBottom = top + Math.max(85, (bottom - top) / 4);
        int pitchTop = showWaveform ? waveBottom + 12 : top;
        if (mouseY < pitchTop || mouseY > bottom) return;
        double clickedTime = xToTime(mouseX);
        double maximumTimeDistance = Math.max(0.10,
                (viewEnd - viewStart) * 18.0 / Math.max(1.0, getWidth() - 74.0));
        double minimumPitch = pitchCenterCent - pitchSpanCent / 2.0;
        double maximumPitch = pitchCenterCent + pitchSpanCent / 2.0;
        double clickedCent = maximumPitch - (mouseY - pitchTop) / (double) Math.max(1, bottom - pitchTop)
                * (maximumPitch - minimumPitch);
        PitchFrame best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (PitchFrame frame : report.frames()) {
            if (!frame.voiced()) continue;
            double timeDistance = Math.abs(frame.timeSeconds() - clickedTime);
            double pitchDistance = Math.abs(frame.absoluteCent() - clickedCent);
            if (timeDistance > maximumTimeDistance || pitchDistance > 125.0) continue;
            double score = timeDistance / maximumTimeDistance + pitchDistance / 125.0;
            if (score < bestScore) { best = frame; bestScore = score; }
        }
        if (best != null) tonicPickListener.accept(best.hertz());
    }

    private void drawTonicPickHint(Graphics2D g, int left, int top) {
        if (!tonicPickMode || report == null) return;
        String text = tonicPickInstruction;
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        int width = g.getFontMetrics().stringWidth(text) + 24;
        g.setColor(new Color(15, 118, 110, 225));
        g.fillRoundRect(left + 10, top + 10, width, 30, 12, 12);
        g.setColor(Color.WHITE);
        g.drawString(text, left + 22, top + 30);
    }

    private void drawWaveform(Graphics2D g, int left, int right, int top, int bottom) {
        g.setColor(UiTheme.SURFACE);
        g.fillRoundRect(left, top, right - left, bottom - top, 12, 12);
        if (waveformCacheBuilding) {
            String message = waveformBuildingMessage;
            g.setColor(UiTheme.MUTED);
            g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            g.drawString(message, left + 14, top + 22);
            return;
        }
        int center = (top + bottom) / 2;
        double samplesPerPixel = audio.sampleRate() * (viewEnd - viewStart) / Math.max(1, right - left);
        for (int x = left; x <= right; x++) {
            int start = (int) ((viewStart * audio.sampleRate()) + (x - left) * samplesPerPixel);
            int end = (int) Math.min(audio.samples().length, start + Math.max(1, samplesPerPixel));
            WaveformCache.Range range = waveformCache.range(start, end);
            int y1 = center - (int) Math.round(range.maximum() * (bottom - top) * 0.46);
            int y2 = center - (int) Math.round(range.minimum() * (bottom - top) * 0.46);
            g.setColor(WAVEFORM_SHADOW);
            g.drawLine(x + 1, Math.min(y1, y2) + 1, x + 1, Math.max(y1, y2) + 1);
            g.setColor(WAVEFORM_CRIMSON);
            g.drawLine(x, Math.min(y1, y2), x, Math.max(y1, y2));
        }
    }

    private void drawSpectrogram(Graphics2D g, int left, int right, int top, int bottom) {
        double duration = audio.durationSeconds();
        int sx1 = (int) Math.max(0, Math.floor(viewStart / duration * spectrogram.getWidth()));
        int sx2 = (int) Math.min(spectrogram.getWidth(), Math.ceil(viewEnd / duration * spectrogram.getWidth()));
        g.drawImage(spectrogram, left, top, right, bottom, sx1, 0, Math.max(sx1 + 1, sx2), spectrogram.getHeight(), null);
        g.setColor(new Color(255, 255, 255, 85));
        g.drawRoundRect(left, top, right - left, bottom - top, 12, 12);
    }

    private void drawPitch(Graphics2D g, int left, int right, int top, int bottom) {
        AnalysisConfig config = report.config();
        double target = config.expectedAbsoluteCent();
        double min = pitchCenterCent - pitchSpanCent / 2.0, max = pitchCenterCent + pitchSpanCent / 2.0;
        boolean interval = Double.isFinite(exclusiveRangeLowerCent)
                && Double.isFinite(exclusiveRangeUpperCent);
        double lower = interval ? exclusiveRangeLowerCent : config.targetWindowLowerAbsoluteCent();
        double upper = interval ? exclusiveRangeUpperCent : config.targetWindowUpperAbsoluteCent();
        int bandTop = Math.max(top, pitchY(upper, min, max, top, bottom));
        int bandBottom = Math.min(bottom, pitchY(lower, min, max, top, bottom));
        if (bandBottom >= bandTop) {
            g.setColor(new Color(13, 148, 136, 28));
            g.fillRect(left, bandTop, right - left, Math.max(1, bandBottom - bandTop));
            g.setColor(interval ? new Color(177, 18, 48, 190) : new Color(13, 148, 136, 135));
            g.setStroke(interval
                    ? new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10.0f, new float[]{6.0f, 5.0f}, 0.0f)
                    : new BasicStroke(1.2f));
            g.drawLine(left, bandTop, right, bandTop);
            g.drawLine(left, bandBottom, right, bandBottom);
            if (interval) {
                g.setFont(getFont().deriveFont(Font.BOLD, 10f));
                String excluded = excludedBoundaryLabel;
                g.drawString(excluded, left + 7, Math.min(bottom - 3, bandTop + 12));
                g.drawString(excluded, left + 7, Math.max(top + 10, bandBottom - 4));
            }
        }
        if (!interval) {
            int targetY = pitchY(target, min, max, top, bottom);
            g.setColor(UiTheme.TARGET);
            g.setStroke(new BasicStroke(1.8f));
            g.drawLine(left, targetY, right, targetY);
        }
        Path2D path = new Path2D.Double();
        boolean drawing = false;
        for (PitchFrame frame : report.frames()) {
            if (frame.timeSeconds() < viewStart || frame.timeSeconds() > viewEnd || !frame.voiced()
                    || frame.absoluteCent() < min || frame.absoluteCent() > max) { drawing = false; continue; }
            int x = timeToX(frame.timeSeconds());
            int y = pitchY(frame.absoluteCent(), min, max, top, bottom);
            if (!drawing) { path.moveTo(x, y); drawing = true; } else path.lineTo(x, y);
        }
        g.setColor(UiTheme.INK);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
    }

    private static int pitchY(double pitch, double min, double max, int top, int bottom) {
        return bottom - (int) Math.round((pitch - min) / (max - min) * (bottom - top));
    }

    private void drawEvents(Graphics2D g, int top, int bottom) {
        for (var occurrence : report.occurrences()) {
            if (occurrence.endSeconds() < viewStart || occurrence.startSeconds() > viewEnd) continue;
            int x1 = timeToX(occurrence.startSeconds()), x2 = timeToX(occurrence.endSeconds());
            Color colour = occurrence.eventType() == EventType.CADENTIAL_HOLD ? new Color(139, 92, 246, 60)
                    : occurrence.eventType() == EventType.SUSTAINED ? new Color(13, 148, 136, 48)
                    : occurrence.eventType() == EventType.SHORT_CONTACT ? new Color(245, 158, 11, 38)
                    : new Color(59, 130, 246, 45);
            g.setColor(colour); g.fillRect(x1, top, Math.max(3, x2 - x1), bottom - top);
        }
        for (GlissandoEvent event : report.glissandi()) {
            if (event.endSeconds() < viewStart || event.startSeconds() > viewEnd) continue;
            int x1 = timeToX(event.startSeconds()), x2 = timeToX(event.endSeconds());
            g.setColor(UiTheme.GLISSANDO_FILL);
            g.fillRect(x1, top, Math.max(4, x2 - x1), bottom - top);
            g.setColor(new Color(255, 255, 255, 225));
            g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(x1, top + 8, x2, top + 8);
            g.setColor(UiTheme.GLISSANDO);
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(x1, top + 8, x2, top + 8);
        }
    }

    private void drawSelection(Graphics2D g, int top, int bottom) {
        if (!Double.isFinite(selectionStart) || !Double.isFinite(selectionEnd)) return;
        int x1 = timeToX(Math.min(selectionStart, selectionEnd));
        int x2 = timeToX(Math.max(selectionStart, selectionEnd));
        g.setColor(new Color(96, 165, 250, 52));
        g.fillRect(x1, top, Math.max(2, x2 - x1), bottom - top);
        g.setColor(UiTheme.ASCENDING);
        g.drawLine(x1, top, x1, bottom); g.drawLine(x2, top, x2, bottom);
    }

    private void drawPlayback(Graphics2D g, int top, int bottom) {
        if (!Double.isFinite(playbackSeconds) || audio == null
                || playbackSeconds < viewStart || playbackSeconds > viewEnd) return;
        int x = timeToX(playbackSeconds);
        g.setColor(new Color(190, 18, 60, playbackActive ? 245 : 135));
        g.setStroke(new BasicStroke(playbackActive ? 2.4f : 1.4f));
        g.drawLine(x, top, x, bottom);
        if (playbackActive) g.fillPolygon(new int[]{x - 5, x + 5, x},
                new int[]{top, top, top + 8}, 3);
    }

    private void drawRuler(Graphics2D g, int left, int right, int bottom) {
        g.setFont(getFont().deriveFont(11f));
        g.setColor(UiTheme.MUTED);
        for (int i = 0; i <= 6; i++) {
            double time = viewStart + i / 6.0 * (viewEnd - viewStart);
            int x = left + i * (right - left) / 6;
            String label = String.format("%d:%05.2f", (int) time / 60, time % 60);
            g.drawString(label, x - (i == 6 ? g.getFontMetrics().stringWidth(label) : 0), bottom + 24);
        }
        g.drawString(navigationHint, left, getHeight() - 7);
    }
}
