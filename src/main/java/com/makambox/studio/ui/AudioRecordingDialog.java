package com.makambox.studio.ui;

import com.makambox.studio.audio.AudioRecorder;
import com.makambox.studio.audio.AudioPlayback;
import com.makambox.studio.model.AudioData;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.ActionMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Records microphone audio without replacing the recording in the main window
 * until the user explicitly accepts the result.
 */
final class AudioRecordingDialog extends JDialog {
    private static final int LIVE_REFRESH_MILLIS = 75;
    private static final int LIVE_WINDOW_SECONDS = 30;
    private static final int LIVE_WAVEFORM_POINTS = 6_000;

    enum State { READY, RECORDING, STOPPING, REVIEW }

    private final RecordingSource source;
    private final Consumer<AudioData> useRecording;
    private final AudioPlayback previewPlayback = new AudioPlayback();
    private final WaveformCanvas waveform = new WaveformCanvas();
    private final JLabel stateLabel = new JLabel();
    private final JLabel durationLabel = new JLabel("00:00.0", SwingConstants.RIGHT);
    private final JLabel guidanceLabel = new JLabel();
    private final JProgressBar activity = new JProgressBar();
    private final JButton startButton = new AutoFitButton("Kaydı başlat");
    private final JButton stopButton = new AutoFitButton("Kaydı durdur");
    private final JButton useButton = new AutoFitButton("Bu kaydı kullan");
    private final JButton deleteButton = new AutoFitButton("Bu kaydı sil");
    private final JButton listenButton = new AutoFitButton("Kaydı dinle");
    private final JButton stopListeningButton = new AutoFitButton("Dinlemeyi durdur");
    private final JButton cancelButton = new AutoFitButton("Vazgeç");
    private final Timer liveTimer;

    private State state = State.READY;
    private AudioData recordedAudio;
    private SwingWorker<Void, Void> previewWorker;
    private boolean closed;

    AudioRecordingDialog(Window owner, AudioRecorder recorder, Consumer<AudioData> useRecording) {
        this(owner, new AudioRecorderSource(recorder), useRecording);
    }

    AudioRecordingDialog(Window owner, RecordingSource source, Consumer<AudioData> useRecording) {
        super(owner, "Ses kaydı", ModalityType.APPLICATION_MODAL);
        this.source = Objects.requireNonNull(source, "source");
        this.useRecording = Objects.requireNonNull(useRecording, "useRecording");
        liveTimer = new Timer(LIVE_REFRESH_MILLIS, event -> refreshLiveWaveform());
        liveTimer.setCoalesce(true);
        buildInterface();
        bindActions();
        applyState(State.READY);
    }

    private void buildInterface() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(true);
        getContentPane().setBackground(UiTheme.APP_BACKGROUND);
        setLayout(new BorderLayout(0, 0));

        JPanel header = new JPanel(new BorderLayout(16, 5));
        header.setBackground(UiTheme.SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
                BorderFactory.createEmptyBorder(20, 24, 18, 24)));
        JLabel title = new JLabel("Mikrofondan ses kaydı");
        title.setForeground(UiTheme.INK);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel subtitle = new JLabel("Kaydı dinleyip dalga biçimini kontrol edin; siz onaylayana kadar ana kayıt değişmez.");
        UiTheme.applyText(subtitle, true, false);
        JPanel heading = new JPanel(new BorderLayout(0, 5));
        heading.setOpaque(false);
        heading.add(title, BorderLayout.NORTH);
        heading.add(subtitle, BorderLayout.CENTER);
        header.add(heading, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setBackground(UiTheme.APP_BACKGROUND);
        center.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        JPanel waveformCard = UiTheme.card();
        JPanel waveformHeader = new JPanel(new BorderLayout(12, 0));
        waveformHeader.setBackground(UiTheme.SURFACE);
        waveformHeader.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        stateLabel.setFont(stateLabel.getFont().deriveFont(Font.BOLD));
        waveformHeader.add(stateLabel, BorderLayout.WEST);
        durationLabel.setForeground(UiTheme.INK);
        durationLabel.setFont(durationLabel.getFont().deriveFont(Font.BOLD, 18f));
        waveformHeader.add(durationLabel, BorderLayout.EAST);
        waveformCard.add(waveformHeader, BorderLayout.NORTH);
        waveform.setPreferredSize(new Dimension(780, 280));
        waveformCard.add(waveform, BorderLayout.CENTER);

        JPanel waveformFooter = new JPanel(new BorderLayout(12, 0));
        waveformFooter.setBackground(UiTheme.SURFACE);
        waveformFooter.setBorder(BorderFactory.createEmptyBorder(9, 14, 11, 14));
        UiTheme.applyText(guidanceLabel, true, false);
        waveformFooter.add(guidanceLabel, BorderLayout.CENTER);
        activity.setIndeterminate(true);
        activity.setPreferredSize(new Dimension(95, 6));
        activity.setBorderPainted(false);
        waveformFooter.add(activity, BorderLayout.EAST);
        waveformCard.add(waveformFooter, BorderLayout.SOUTH);
        center.add(waveformCard, BorderLayout.CENTER);

        JLabel privacy = new JLabel("Geçici kayıt yalnızca bu pencerede tutulur. “Bu kaydı kullan” seçilmeden analize gönderilmez.");
        UiTheme.applyText(privacy, true, false);
        center.add(privacy, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setBackground(UiTheme.SURFACE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER),
                BorderFactory.createEmptyBorder(12, 18, 12, 18)));
        JPanel recordingActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        recordingActions.setOpaque(false);
        recordingActions.add(startButton);
        recordingActions.add(stopButton);
        recordingActions.add(listenButton);
        recordingActions.add(stopListeningButton);
        footer.add(recordingActions, BorderLayout.NORTH);
        JPanel decisionActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        decisionActions.setOpaque(false);
        decisionActions.add(cancelButton);
        decisionActions.add(deleteButton);
        decisionActions.add(useButton);
        footer.add(decisionActions, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        useButton.setBackground(UiTheme.ACCENT);
        useButton.setForeground(Color.WHITE);
        useButton.setFont(useButton.getFont().deriveFont(Font.BOLD));
        getRootPane().setDefaultButton(startButton);

        setSize(880, 540);
        setMinimumSize(new Dimension(680, 430));
        setLocationRelativeTo(getOwner());
    }

    private void bindActions() {
        startButton.addActionListener(event -> startRecording());
        stopButton.addActionListener(event -> stopRecording());
        deleteButton.addActionListener(event -> deleteRecording());
        useButton.addActionListener(event -> acceptRecording());
        listenButton.addActionListener(event -> listenToRecording());
        stopListeningButton.addActionListener(event -> stopListening());
        cancelButton.addActionListener(event -> discardAndClose());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { discardAndClose(); }
        });

        InputMap input = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actions = getRootPane().getActionMap();
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel-recording");
        actions.put("cancel-recording", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { discardAndClose(); }
        });
    }

    private void startRecording() {
        if (closed || state != State.READY) return;
        recordedAudio = null;
        waveform.clear();
        durationLabel.setText(formatDuration(0.0));
        try {
            source.start();
            applyState(State.RECORDING);
            refreshLiveWaveform();
            liveTimer.start();
        } catch (Exception exception) {
            source.close();
            applyState(State.READY);
            showError("Mikrofon kaydı başlatılamadı", exception);
        }
    }

    private void refreshLiveWaveform() {
        if (closed || state != State.RECORDING) return;
        updateLiveWaveform();
        if (!source.isRecording()) beginStopRecording();
    }

    private void updateLiveWaveform() {
        double seconds = Math.max(0.0, source.recordedSeconds());
        float sampleRate = Math.max(1f, source.sampleRate());
        waveform.setSamples(source.recentSamples(LIVE_WAVEFORM_POINTS), sampleRate, seconds, true);
        durationLabel.setText(formatDuration(seconds));
    }

    private void stopRecording() {
        if (closed || state != State.RECORDING) return;
        updateLiveWaveform();
        beginStopRecording();
    }

    private void beginStopRecording() {
        if (closed || state != State.RECORDING) return;
        liveTimer.stop();
        applyState(State.STOPPING);
        new SwingWorker<AudioData, Void>() {
            @Override protected AudioData doInBackground() throws Exception {
                return source.stopAndBuild();
            }

            @Override protected void done() {
                if (closed) return;
                try {
                    AudioData result = get();
                    if (result == null || result.samples() == null || result.samples().length == 0) {
                        throw new IllegalStateException("Kayda ses alınamadı.");
                    }
                    showReview(result);
                } catch (CancellationException ignored) {
                    // Deleting, accepting, or closing deliberately cancels preparation.
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    recoverFromStopFailure("Kayıt tamamlanamadı", interrupted);
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    recoverFromStopFailure("Kayıt tamamlanamadı", cause);
                } catch (RuntimeException exception) {
                    recoverFromStopFailure("Kayıt tamamlanamadı", exception);
                }
            }
        }.execute();
    }

    private void recoverFromStopFailure(String title, Throwable failure) {
        stopListening();
        source.close();
        recordedAudio = null;
        waveform.clear();
        durationLabel.setText(formatDuration(0.0));
        applyState(State.READY);
        showError(title, failure);
    }

    private void showReview(AudioData audio) {
        recordedAudio = audio;
        waveform.setSamples(audio.samples(), audio.sampleRate(), audio.durationSeconds(), false);
        durationLabel.setText(formatDuration(audio.durationSeconds()));
        applyState(State.REVIEW);
    }

    private void deleteRecording() {
        if (closed || state != State.REVIEW) return;
        stopListening();
        source.close();
        recordedAudio = null;
        waveform.clear();
        durationLabel.setText(formatDuration(0.0));
        applyState(State.READY);
    }

    private void acceptRecording() {
        if (closed || state != State.REVIEW || recordedAudio == null) return;
        AudioData accepted = recordedAudio;
        recordedAudio = null;
        closed = true;
        liveTimer.stop();
        stopListening();
        source.close();
        dispose();
        useRecording.accept(accepted);
    }

    private void discardAndClose() {
        if (closed) return;
        closed = true;
        liveTimer.stop();
        stopListening();
        recordedAudio = null;
        dispose();
        Thread cleanup = new Thread(source::close, "makambox-recording-discard");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    private void applyState(State next) {
        state = next;
        boolean ready = next == State.READY;
        boolean recording = next == State.RECORDING;
        boolean review = next == State.REVIEW;
        startButton.setEnabled(ready);
        stopButton.setEnabled(recording);
        useButton.setEnabled(review);
        deleteButton.setEnabled(review);
        listenButton.setEnabled(review);
        stopListeningButton.setEnabled(false);
        activity.setVisible(recording || next == State.STOPPING);
        activity.setIndeterminate(recording || next == State.STOPPING);
        cancelButton.setEnabled(true);

        switch (next) {
            case READY -> {
                stateLabel.setText("●  Kayıt için hazır");
                stateLabel.setForeground(UiTheme.MUTED);
                guidanceLabel.setText("Kaydı başlatınca canlı ses dalgası burada görünecek.");
                getRootPane().setDefaultButton(startButton);
            }
            case RECORDING -> {
                stateLabel.setText("●  Kayıt yapılıyor");
                stateLabel.setForeground(new Color(220, 38, 38));
                guidanceLabel.setText("Canlı dalga biçimi • “Kaydı durdur” ile kaydı incelemeye geçin.");
                getRootPane().setDefaultButton(stopButton);
            }
            case STOPPING -> {
                stateLabel.setText("●  Kayıt hazırlanıyor");
                stateLabel.setForeground(UiTheme.ACCENT);
                guidanceLabel.setText("Ses dalgası inceleme için hazırlanıyor…");
                getRootPane().setDefaultButton(null);
            }
            case REVIEW -> {
                stateLabel.setText("●  Kayıt tamamlandı");
                stateLabel.setForeground(UiTheme.ACCENT);
                guidanceLabel.setText("Dalga biçimini kontrol edin; kaydı kullanın veya silip yeniden başlayın.");
                getRootPane().setDefaultButton(useButton);
            }
        }
    }

    private void showError(String title, Throwable failure) {
        String detail = failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
                ? "Bilinmeyen hata" : failure.getMessage();
        if (GraphicsEnvironment.isHeadless()) return;
        JOptionPane.showMessageDialog(this, detail, title, JOptionPane.ERROR_MESSAGE);
    }

    private void listenToRecording() {
        if (closed || state != State.REVIEW || recordedAudio == null || previewWorker != null) return;
        AudioData preview = recordedAudio;
        listenButton.setEnabled(false);
        stopListeningButton.setEnabled(false);
        guidanceLabel.setText("Kayıt önizlemesi hazırlanıyor…");
        previewWorker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                previewPlayback.play(preview);
                return null;
            }

            @Override protected void done() {
                previewWorker = null;
                if (closed || state != AudioRecordingDialog.State.REVIEW) return;
                try {
                    get();
                    guidanceLabel.setText("Kayıt dinleniyor; kaydı kullanın veya silip yeniden başlayın.");
                    stopListeningButton.setEnabled(true);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    showError("Kayıt dinletilemedi", cause);
                } finally {
                    listenButton.setEnabled(state == AudioRecordingDialog.State.REVIEW && !closed);
                }
            }
        };
        previewWorker.execute();
    }

    private void stopListening() {
        SwingWorker<Void, Void> worker = previewWorker;
        previewWorker = null;
        if (worker != null) worker.cancel(true);
        previewPlayback.close();
        stopListeningButton.setEnabled(false);
        listenButton.setEnabled(state == State.REVIEW && !closed);
        if (state == State.REVIEW && !closed) {
            guidanceLabel.setText("Dalga biçimini ve kaydı kontrol edin; kaydı kullanın veya silip yeniden başlayın.");
        }
    }

    private static String formatDuration(double seconds) {
        double safe = Math.max(0.0, seconds);
        return String.format(Locale.US, "%02d:%04.1f", (int) safe / 60, safe % 60.0);
    }

    JButton diagnosticStartButton() { return startButton; }
    JButton diagnosticStopButton() { return stopButton; }
    JButton diagnosticUseButton() { return useButton; }
    JButton diagnosticDeleteButton() { return deleteButton; }
    JButton diagnosticListenButton() { return listenButton; }
    JButton diagnosticCancelButton() { return cancelButton; }
    State diagnosticState() { return state; }
    AudioData diagnosticRecordedAudio() { return recordedAudio; }
    int diagnosticWaveformPointCount() { return waveform.pointCount(); }
    String diagnosticDurationText() { return durationLabel.getText(); }
    void diagnosticShowReview(AudioData audio) { showReview(audio); }

    private static final class WaveformCanvas extends JPanel {
        private static final int MAX_ENVELOPE_POINTS = 6_000;
        private float[] minimum = new float[0];
        private float[] maximum = new float[0];
        private double startSeconds;
        private double endSeconds;
        private boolean live;

        private WaveformCanvas() {
            setOpaque(true);
            setBackground(UiTheme.CANVAS);
            setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, UiTheme.BORDER));
        }

        private void clear() {
            minimum = new float[0];
            maximum = new float[0];
            startSeconds = 0.0;
            endSeconds = 0.0;
            live = false;
            repaint();
        }

        private void setSamples(float[] samples, float sampleRate, double totalSeconds, boolean live) {
            if (samples == null || samples.length == 0) {
                clear();
                return;
            }
            int points = Math.min(MAX_ENVELOPE_POINTS, samples.length);
            float[] nextMinimum = new float[points];
            float[] nextMaximum = new float[points];
            for (int point = 0; point < points; point++) {
                int from = (int) ((long) point * samples.length / points);
                int to = (int) ((long) (point + 1) * samples.length / points);
                if (to <= from) to = Math.min(samples.length, from + 1);
                float low = 1f;
                float high = -1f;
                for (int sample = from; sample < to; sample++) {
                    float value = Math.max(-1f, Math.min(1f, samples[sample]));
                    low = Math.min(low, value);
                    high = Math.max(high, value);
                }
                nextMinimum[point] = low;
                nextMaximum[point] = high;
            }
            minimum = nextMinimum;
            maximum = nextMaximum;
            this.live = live;
            double sampleSeconds = samples.length / (double) Math.max(1f, sampleRate);
            endSeconds = live ? Math.max(0.0, totalSeconds) : Math.max(sampleSeconds, totalSeconds);
            double windowSeconds = live ? Math.min(LIVE_WINDOW_SECONDS, endSeconds) : sampleSeconds;
            startSeconds = live ? Math.max(0.0, endSeconds - windowSeconds) : 0.0;
            repaint();
        }

        private int pointCount() { return minimum.length; }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int left = 18;
                int right = Math.max(left + 1, getWidth() - 18);
                int top = 16;
                int bottom = Math.max(top + 1, getHeight() - 27);
                int width = Math.max(1, right - left);
                int height = Math.max(1, bottom - top);
                int center = top + height / 2;

                g.setStroke(new BasicStroke(1f));
                g.setColor(UiTheme.GRID);
                for (int line = 0; line <= 8; line++) {
                    int x = left + (int) Math.round(line / 8.0 * width);
                    g.drawLine(x, top, x, bottom);
                }
                g.drawLine(left, top + height / 4, right, top + height / 4);
                g.drawLine(left, center, right, center);
                g.drawLine(left, top + height * 3 / 4, right, top + height * 3 / 4);

                if (minimum.length == 0) {
                    String empty = "Canlı ses dalgası";
                    g.setColor(UiTheme.MUTED);
                    g.setFont(getFont().deriveFont(Font.BOLD, 16f));
                    int textWidth = g.getFontMetrics().stringWidth(empty);
                    g.drawString(empty, left + (width - textWidth) / 2, center - 4);
                    String help = "Kaydı başlatınca mikrofon sinyali burada görünür.";
                    g.setFont(getFont().deriveFont(Font.PLAIN, 13f));
                    int helpWidth = g.getFontMetrics().stringWidth(help);
                    g.drawString(help, left + (width - helpWidth) / 2, center + 19);
                    return;
                }

                g.setColor(new Color(UiTheme.ACCENT.getRed(), UiTheme.ACCENT.getGreen(), UiTheme.ACCENT.getBlue(), 48));
                for (int pixel = 0; pixel < width; pixel++) {
                    int from = (int) ((long) pixel * minimum.length / width);
                    int to = (int) ((long) (pixel + 1) * minimum.length / width);
                    if (to <= from) to = Math.min(minimum.length, from + 1);
                    float low = 1f;
                    float high = -1f;
                    for (int point = from; point < to; point++) {
                        low = Math.min(low, minimum[point]);
                        high = Math.max(high, maximum[point]);
                    }
                    int yHigh = center - Math.round(high * height * .45f);
                    int yLow = center - Math.round(low * height * .45f);
                    g.drawLine(left + pixel, yHigh, left + pixel, yLow);
                }
                g.setColor(UiTheme.ACCENT);
                g.setStroke(new BasicStroke(1.35f));
                for (int pixel = 0; pixel < width; pixel++) {
                    int point = Math.min(minimum.length - 1,
                            (int) ((long) pixel * minimum.length / width));
                    int yHigh = center - Math.round(maximum[point] * height * .45f);
                    int yLow = center - Math.round(minimum[point] * height * .45f);
                    g.drawLine(left + pixel, yHigh, left + pixel, yLow);
                }

                g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
                g.setColor(UiTheme.MUTED);
                String start = clock(startSeconds);
                String end = clock(endSeconds);
                g.drawString(start, left, getHeight() - 8);
                int endWidth = g.getFontMetrics().stringWidth(end);
                g.drawString(end, right - endWidth, getHeight() - 8);
                if (live) {
                    String liveLabel = "CANLI";
                    g.setColor(new Color(220, 38, 38));
                    g.setFont(getFont().deriveFont(Font.BOLD, 11f));
                    g.drawString(liveLabel, right - g.getFontMetrics().stringWidth(liveLabel), top + 12);
                }
            } finally {
                g.dispose();
            }
        }

        private static String clock(double seconds) {
            double safe = Math.max(0.0, seconds);
            return String.format(Locale.US, "%d:%04.1f", (int) safe / 60, safe % 60.0);
        }
    }
}

/** Injectable recording surface used by the dialog and its no-microphone UI tests. */
interface RecordingSource extends AutoCloseable {
    void start() throws Exception;
    AudioData stopAndBuild() throws Exception;
    boolean isRecording();
    float[] recentSamples(int maximumSamples);
    double recordedSeconds();
    float sampleRate();
    @Override void close();
}

/** Production adapter that keeps AudioRecorder out of the dialog state machine. */
final class AudioRecorderSource implements RecordingSource {
    private final AudioRecorder recorder;

    AudioRecorderSource(AudioRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    @Override public void start() throws Exception { recorder.start(); }
    @Override public AudioData stopAndBuild() throws Exception { return recorder.stopAndBuild(); }
    @Override public boolean isRecording() { return recorder.isRecording(); }
    @Override public float[] recentSamples(int maximumSamples) { return recorder.recentSamples(maximumSamples); }
    @Override public double recordedSeconds() { return recorder.recordedSeconds(); }
    @Override public float sampleRate() { return recorder.sampleRate(); }
    @Override public void close() { recorder.close(); }
}
