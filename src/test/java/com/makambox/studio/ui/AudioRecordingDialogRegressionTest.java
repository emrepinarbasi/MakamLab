package com.makambox.studio.ui;

import com.makambox.studio.model.AudioData;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the complete recording dialog workflow without requiring microphone hardware. */
public final class AudioRecordingDialogRegressionTest {
    public static void main(String[] args) throws Exception {
        FakeRecordingSource source = new FakeRecordingSource();
        AtomicInteger acceptedCount = new AtomicInteger();
        AtomicReference<AudioData> accepted = new AtomicReference<>();
        AudioRecordingDialog[] holder = new AudioRecordingDialog[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new AudioRecordingDialog(null, source, audio -> {
            accepted.set(audio);
            acceptedCount.incrementAndGet();
        }));
        AudioRecordingDialog dialog = holder[0];

        onEdt(() -> assertState(dialog, AudioRecordingDialog.State.READY,
                true, false, false, false));
        onEdt(() -> {
            if (!"Kaydı başlat".equals(dialog.diagnosticStartButton().getText())
                    || !"Kaydı durdur".equals(dialog.diagnosticStopButton().getText())
                    || !"Bu kaydı kullan".equals(dialog.diagnosticUseButton().getText())
                    || !"Bu kaydı sil".equals(dialog.diagnosticDeleteButton().getText())) {
                throw new AssertionError("recording decisions are not labelled clearly");
            }
        });
        onEdt(() -> dialog.diagnosticStartButton().doClick());
        onEdt(() -> {
            assertState(dialog, AudioRecordingDialog.State.RECORDING,
                    false, true, false, false);
            if (dialog.diagnosticWaveformPointCount() == 0
                    || !"00:01.5".equals(dialog.diagnosticDurationText())) {
                throw new AssertionError("live waveform or elapsed time was not updated");
            }
        });

        onEdt(() -> dialog.diagnosticStopButton().doClick());
        awaitState(dialog, AudioRecordingDialog.State.REVIEW);
        onEdt(() -> {
            assertState(dialog, AudioRecordingDialog.State.REVIEW,
                    false, false, true, true);
            if (dialog.diagnosticRecordedAudio() != source.result
                    || dialog.diagnosticWaveformPointCount() == 0
                    || !dialog.diagnosticListenButton().isEnabled()) {
                throw new AssertionError("completed take is not staged for review");
            }
        });

        onEdt(() -> dialog.diagnosticDeleteButton().doClick());
        onEdt(() -> {
            assertState(dialog, AudioRecordingDialog.State.READY,
                    true, false, false, false);
            if (dialog.diagnosticRecordedAudio() != null
                    || dialog.diagnosticWaveformPointCount() != 0
                    || !"00:00.0".equals(dialog.diagnosticDurationText())
                    || acceptedCount.get() != 0 || source.closeCount != 1) {
                throw new AssertionError("delete did not discard the staged take cleanly");
            }
        });

        onEdt(() -> dialog.diagnosticStartButton().doClick());
        onEdt(() -> dialog.diagnosticStopButton().doClick());
        awaitState(dialog, AudioRecordingDialog.State.REVIEW);
        onEdt(() -> dialog.diagnosticUseButton().doClick());
        if (acceptedCount.get() != 1 || accepted.get() != source.result || source.closeCount != 2) {
            throw new AssertionError("use did not deliver exactly one take and close the source");
        }

        FakeRecordingSource cancelledSource = new FakeRecordingSource();
        AtomicInteger cancelledAccepts = new AtomicInteger();
        AudioRecordingDialog[] cancelledHolder = new AudioRecordingDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            cancelledHolder[0] = new AudioRecordingDialog(null, cancelledSource,
                    ignored -> cancelledAccepts.incrementAndGet());
            cancelledHolder[0].diagnosticStartButton().doClick();
            cancelledHolder[0].diagnosticCancelButton().doClick();
        });
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (cancelledSource.closeCount == 0 && System.nanoTime() < deadline) Thread.sleep(10);
        if (cancelledSource.closeCount != 1 || cancelledAccepts.get() != 0) {
            throw new AssertionError("cancel did not stop and discard the temporary recording");
        }

        System.out.println("AUDIO_RECORDING_DIALOG_OK live=true delete=true useOnce=true cancel=true");
        System.exit(0);
    }

    private static void awaitState(AudioRecordingDialog dialog, AudioRecordingDialog.State expected)
            throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < deadline) {
            AtomicReference<AudioRecordingDialog.State> actual = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> actual.set(dialog.diagnosticState()));
            if (actual.get() == expected) return;
            Thread.sleep(10);
        }
        throw new AssertionError("dialog did not reach state " + expected);
    }

    private static void assertState(AudioRecordingDialog dialog, AudioRecordingDialog.State expected,
                                    boolean start, boolean stop, boolean use, boolean delete) {
        if (dialog.diagnosticState() != expected
                || dialog.diagnosticStartButton().isEnabled() != start
                || dialog.diagnosticStopButton().isEnabled() != stop
                || dialog.diagnosticUseButton().isEnabled() != use
                || dialog.diagnosticDeleteButton().isEnabled() != delete
                || dialog.diagnosticListenButton().isEnabled() != use) {
            throw new AssertionError("unexpected control state for " + expected);
        }
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static final class FakeRecordingSource implements RecordingSource {
        private final AudioData result;
        private final float[] live;
        private volatile boolean recording;
        private volatile int closeCount;

        private FakeRecordingSource() {
            live = wave(4_410, 44_100f);
            result = new AudioData("Deneme mikrofon kaydı", null, 44_100f,
                    wave(22_050, 44_100f));
        }

        @Override public void start() { recording = true; }
        @Override public AudioData stopAndBuild() { recording = false; return result; }
        @Override public boolean isRecording() { return recording; }
        @Override public float[] recentSamples(int maximumSamples) { return live.clone(); }
        @Override public double recordedSeconds() { return 1.5; }
        @Override public float sampleRate() { return 44_100f; }
        @Override public void close() { recording = false; closeCount++; }

        private static float[] wave(int length, float sampleRate) {
            float[] samples = new float[length];
            for (int index = 0; index < length; index++) {
                double envelope = .25 + .7 * Math.sin(Math.PI * index / Math.max(1, length - 1));
                samples[index] = (float) (.72 * envelope * Math.sin(2.0 * Math.PI * 220.0 * index / sampleRate));
            }
            return samples;
        }
    }
}
