package com.makambox.studio.audio;

import com.makambox.studio.model.AudioData;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AudioRecorder implements AutoCloseable {
    private static final float PRIMARY_SAMPLE_RATE = 44_100f;
    private static final float FALLBACK_SAMPLE_RATE = 48_000f;
    private static final int LIVE_WINDOW_SECONDS = 30;
    private static final int MAXIMUM_RECORDING_SECONDS = 45 * 60;
    private static final long STOP_JOIN_MILLIS = 1_500;
    private static final DateTimeFormatter NAME_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss");

    private volatile Session activeSession;
    private volatile CaptureState visibleState;

    public synchronized void start() throws Exception {
        Session existing = activeSession;
        if (existing != null) {
            if (existing.state.acceptingSamples.get()) return;
            activeSession = null;
            stopSession(existing);
        }

        CaptureState state = openCaptureState();
        Session next = null;
        boolean started = false;
        try {
            Thread thread = new Thread(() -> capture(state), "makambox-audio-capture");
            thread.setDaemon(true);
            next = new Session(state, thread);
            state.line.start();
            thread.start();
            started = true;
            activeSession = next;
            visibleState = state;
        } finally {
            if (!started) cleanupFailedStart(next, state);
        }
    }

    private static CaptureState openCaptureState() throws Exception {
        Exception failure = null;
        for (float sampleRate : new float[]{PRIMARY_SAMPLE_RATE, FALLBACK_SAMPLE_RATE}) {
            TargetDataLine candidate = null;
            try {
                AudioFormat requested = captureFormat(sampleRate);
                candidate = (TargetDataLine) AudioSystem.getLine(
                        new DataLine.Info(TargetDataLine.class, requested));
                candidate.open(requested);
                AudioFormat actual = candidate.getFormat();
                validateCaptureFormat(actual);
                return new CaptureState(candidate, actual);
            } catch (LineUnavailableException | IllegalArgumentException | SecurityException exception) {
                closeLineQuietly(candidate);
                if (failure != null) exception.addSuppressed(failure);
                failure = exception;
            }
        }
        if (failure != null) throw failure;
        throw new LineUnavailableException("No supported microphone capture format was found");
    }

    private static AudioFormat captureFormat(float sampleRate) {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, 1,
                2, sampleRate, false);
    }

    private static void validateCaptureFormat(AudioFormat format) throws LineUnavailableException {
        if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                || format.getSampleSizeInBits() != 16
                || format.getChannels() != 1
                || format.getFrameSize() != 2
                || format.isBigEndian()
                || !Float.isFinite(format.getSampleRate())
                || format.getSampleRate() <= 0f) {
            throw new LineUnavailableException("Microphone did not provide 16-bit mono little-endian PCM");
        }
    }

    private static void capture(CaptureState state) {
        byte[] buffer = new byte[4096];
        try {
            while (state.acceptingSamples.get()) {
                int count = state.line.read(buffer, 0, buffer.length);
                if (count < 0) break;
                if (count > 0) appendCapturedBytes(state, buffer, count);
            }
        } catch (RuntimeException failure) {
            // A stop/close can release a blocking read with a runtime exception; only a failure
            // raised while the session was still active should invalidate the recording.
            if (state.acceptingSamples.get()) state.captureFailure = failure;
        } finally {
            state.acceptingSamples.set(false);
            closeLineQuietly(state.line);
        }
    }

    private static void appendCapturedBytes(CaptureState state, byte[] bytes, int count) {
        synchronized (state.dataLock) {
            if (state.writesFrozen) return;
            int usable = count - count % 2;
            long remainingSamples = Math.max(0L, state.maximumSamples - state.capturedSamples);
            int acceptedBytes = (int) Math.min(usable, remainingSamples * 2L);
            if (acceptedBytes <= 0) {
                state.acceptingSamples.set(false);
                return;
            }
            state.output.write(bytes, 0, acceptedBytes);
            for (int offset = 0; offset < acceptedBytes; offset += 2) {
                int value = (bytes[offset] & 0xff) | (bytes[offset + 1] << 8);
                state.waveformRing[state.waveformWriteIndex] = (short) value / 32768f;
                state.waveformWriteIndex = (state.waveformWriteIndex + 1) % state.waveformRing.length;
                state.waveformSampleCount = Math.min(
                        state.waveformRing.length, state.waveformSampleCount + 1);
                state.capturedSamples++;
            }
            if (state.capturedSamples >= state.maximumSamples) {
                state.acceptingSamples.set(false);
            }
        }
    }

    public float[] recentSamples(int maximumSamples) {
        CaptureState state = visibleState;
        if (state == null || maximumSamples <= 0) return new float[0];
        synchronized (state.dataLock) {
            int count = state.waveformSampleCount;
            if (count == 0) return new float[0];
            int start = (state.waveformWriteIndex - count + state.waveformRing.length)
                    % state.waveformRing.length;

            if (count <= maximumSamples) {
                float[] result = new float[count];
                for (int index = 0; index < count; index++) {
                    result[index] = state.waveformRing[(start + index) % state.waveformRing.length];
                }
                return result;
            }

            if (maximumSamples == 1) {
                return new float[]{state.waveformRing[
                        (state.waveformWriteIndex - 1 + state.waveformRing.length)
                                % state.waveformRing.length]};
            }

            int bucketCount = maximumSamples / 2;
            float[] result = new float[bucketCount * 2];
            for (int bucket = 0; bucket < bucketCount; bucket++) {
                int from = (int) ((long) bucket * count / bucketCount);
                int to = (int) ((long) (bucket + 1) * count / bucketCount);
                float minimum = Float.POSITIVE_INFINITY;
                float maximum = Float.NEGATIVE_INFINITY;
                for (int offset = from; offset < to; offset++) {
                    float sample = state.waveformRing[(start + offset) % state.waveformRing.length];
                    minimum = Math.min(minimum, sample);
                    maximum = Math.max(maximum, sample);
                }
                result[bucket * 2] = minimum;
                result[bucket * 2 + 1] = maximum;
            }
            return result;
        }
    }

    public double recordedSeconds() {
        CaptureState state = visibleState;
        if (state == null) return 0.0;
        synchronized (state.dataLock) {
            return state.capturedSamples / state.sampleRate;
        }
    }

    public float sampleRate() {
        CaptureState state = visibleState;
        return state == null ? PRIMARY_SAMPLE_RATE : state.sampleRate;
    }

    public synchronized AudioData stopAndBuild() throws InterruptedException {
        Session stopped = activeSession;
        if (stopped == null) return null;
        activeSession = null;
        stopSession(stopped);

        byte[] bytes = null;
        RuntimeException captureFailure = stopped.state.captureFailure;
        synchronized (stopped.state.dataLock) {
            if (captureFailure == null) bytes = stopped.state.output.toByteArray();
            stopped.state.output = new ByteArrayOutputStream(0);
        }
        if (captureFailure != null) {
            throw new IllegalStateException("Mikrofon bağlantısı kayıt sırasında kesildi.", captureFailure);
        }
        float[] samples = decodePcm16LittleEndian(bytes);
        String timestamp = LocalDateTime.now().format(NAME_TIMESTAMP);
        return new AudioData("Mikrofon kaydı " + timestamp, null,
                stopped.state.sampleRate, samples);
    }

    static float[] decodePcm16LittleEndian(byte[] bytes) {
        float[] samples = new float[bytes.length / 2];
        for (int index = 0; index < samples.length; index++) {
            int offset = index * 2;
            int value = (bytes[offset] & 0xff) | (bytes[offset + 1] << 8);
            samples[index] = (short) value / 32768f;
        }
        return samples;
    }

    public boolean isRecording() {
        Session session = activeSession;
        return session != null && session.state.acceptingSamples.get();
    }

    @Override public synchronized void close() {
        Session stopped = activeSession;
        activeSession = null;
        visibleState = null;
        if (stopped == null) return;
        try {
            stopSession(stopped);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void stopSession(Session session) throws InterruptedException {
        session.state.acceptingSamples.set(false);
        stopLineQuietly(session.state.line);
        closeLineQuietly(session.state.line);
        try {
            if (session.thread != Thread.currentThread()
                    && !awaitTermination(session.thread, STOP_JOIN_MILLIS)) {
                session.thread.interrupt();
            }
        } finally {
            synchronized (session.state.dataLock) {
                session.state.writesFrozen = true;
            }
        }
    }

    private static boolean awaitTermination(Thread thread, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (thread.isAlive()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) return false;
            long millis = TimeUnit.NANOSECONDS.toMillis(remaining);
            int nanos = (int) (remaining - TimeUnit.MILLISECONDS.toNanos(millis));
            thread.join(millis, nanos);
        }
        return true;
    }

    private static void cleanupFailedStart(Session session, CaptureState state) {
        state.acceptingSamples.set(false);
        stopLineQuietly(state.line);
        closeLineQuietly(state.line);
        if (session == null || session.thread == Thread.currentThread()) return;
        try {
            if (!awaitTermination(session.thread, STOP_JOIN_MILLIS)) session.thread.interrupt();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            synchronized (state.dataLock) {
                state.writesFrozen = true;
            }
        }
    }

    private static void stopLineQuietly(TargetDataLine line) {
        if (line == null) return;
        try {
            line.stop();
        } catch (RuntimeException ignored) {
        }
    }

    private static void closeLineQuietly(TargetDataLine line) {
        if (line == null) return;
        try {
            line.close();
        } catch (RuntimeException ignored) {
        }
    }

    private static final class Session {
        final CaptureState state;
        final Thread thread;

        Session(CaptureState state, Thread thread) {
            this.state = state;
            this.thread = thread;
        }
    }

    private static final class CaptureState {
        final TargetDataLine line;
        final AudioFormat format;
        final float sampleRate;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        final AtomicBoolean acceptingSamples = new AtomicBoolean(true);
        final Object dataLock = new Object();
        final float[] waveformRing;
        final long maximumSamples;
        int waveformWriteIndex;
        int waveformSampleCount;
        long capturedSamples;
        boolean writesFrozen;
        volatile RuntimeException captureFailure;

        CaptureState(TargetDataLine line, AudioFormat format) {
            this.line = line;
            this.format = format;
            sampleRate = format.getSampleRate();
            maximumSamples = Math.max(1L, Math.round(sampleRate * MAXIMUM_RECORDING_SECONDS));
            waveformRing = new float[Math.max(1,
                    (int) Math.ceil(sampleRate * LIVE_WINDOW_SECONDS))];
        }
    }
}
