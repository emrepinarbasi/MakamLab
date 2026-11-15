package com.makambox.studio.audio;

import com.makambox.studio.model.AudioData;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.ByteArrayInputStream;

/** Audio transport with pitch-preserving slow playback for detailed listening. */
public final class AudioPlayback implements AutoCloseable {
    private final Object lock = new Object();
    private Clip clip;
    private double rangeStartSeconds;
    private double rangeEndSeconds;
    private boolean paused;
    private double playbackRate = 1.0;
    private long generation;

    public void setPlaybackRate(double value) {
        if (!Double.isFinite(value) || value < 0.5 || value > 1.25) {
            throw new IllegalArgumentException("Çalma hızı 0.50× ile 1.25× arasında olmalıdır");
        }
        synchronized (lock) { playbackRate = value; }
    }

    public double playbackRate() { synchronized (lock) { return playbackRate; } }

    public void play(AudioData audio) throws Exception {
        playRange(audio, 0.0, audio.durationSeconds(), false);
    }

    public void playFrom(AudioData audio, double startSeconds) throws Exception {
        playRange(audio, startSeconds, audio.durationSeconds(), false);
    }

    public void playRange(AudioData audio, double startSeconds, double endSeconds, boolean loop) throws Exception {
        int start = Math.max(0, Math.min(audio.samples().length - 1,
                (int) Math.floor(startSeconds * audio.sampleRate())));
        int end = Math.max(start + 1, Math.min(audio.samples().length,
                (int) Math.ceil(endSeconds * audio.sampleRate())));
        final long ticket;
        final double rate;
        synchronized (lock) {
            ticket = ++generation;
            closeClipLocked();
            rate = playbackRate;
            rangeStartSeconds = start / (double) audio.sampleRate();
            rangeEndSeconds = end / (double) audio.sampleRate();
            paused = false;
        }

        float[] stretched = Math.abs(rate - 1.0) < 0.0001 ? null
                : timeStretchPitchPreserving(audio.samples(), start, end, rate, audio.sampleRate());
        int sampleCount = stretched == null ? end - start : stretched.length;
        byte[] bytes = new byte[sampleCount * 2];
        for (int i = 0; i < sampleCount; i++) {
            if ((i & 0x7fff) == 0 && Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Ses hazırlama iptal edildi");
            }
            float sample = stretched == null ? audio.samples()[start + i] : stretched[i];
            int value = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE,
                    Math.round(sample * Short.MAX_VALUE)));
            int target = i * 2;
            bytes[target] = (byte) (value & 0xff);
            bytes[target + 1] = (byte) ((value >>> 8) & 0xff);
        }
        stretched = null;
        AudioFormat format = new AudioFormat(audio.sampleRate(), 16, 1, true, false);
        AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(bytes), format, sampleCount);
        Clip prepared = AudioSystem.getClip();
        try {
            prepared.open(stream);
            synchronized (lock) {
                if (ticket != generation || Thread.currentThread().isInterrupted()) {
                    prepared.close();
                    return;
                }
                clip = prepared;
                if (loop) clip.loop(Clip.LOOP_CONTINUOUSLY); else clip.start();
            }
        } catch (Exception exception) {
            prepared.close();
            throw exception;
        }
    }

    public void pause() {
        synchronized (lock) {
            if (clip != null && clip.isRunning()) {
                clip.stop();
                paused = true;
            }
        }
    }

    public void resume() {
        synchronized (lock) {
            if (clip != null && paused && clip.getMicrosecondPosition() < clip.getMicrosecondLength()) {
                paused = false;
                clip.start();
            }
        }
    }

    public void seek(AudioData audio, double seconds, boolean continuePlaying) throws Exception {
        playFrom(audio, seconds);
        if (!continuePlaying) pause();
    }

    public void stop() {
        synchronized (lock) {
            generation++;
            if (clip != null) {
                clip.stop();
                clip.setMicrosecondPosition(0L);
            }
            rangeStartSeconds = 0.0;
            paused = false;
        }
    }

    public boolean isPlaying() { synchronized (lock) { return clip != null && clip.isRunning(); } }
    public boolean isPaused() { synchronized (lock) { return clip != null && paused; } }
    public boolean hasClip() { synchronized (lock) { return clip != null; } }

    public double positionSeconds() {
        synchronized (lock) {
            if (clip == null) return rangeStartSeconds;
            long length = Math.max(1L, clip.getMicrosecondLength());
            double fraction = Math.max(0.0, Math.min(1.0, clip.getMicrosecondPosition() / (double) length));
            return rangeStartSeconds + fraction * (rangeEndSeconds - rangeStartSeconds);
        }
    }

    @Override public void close() {
        synchronized (lock) {
            generation++;
            closeClipLocked();
            paused = false;
        }
    }

    private void closeClipLocked() {
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
    }

    /** Waveform-similarity overlap/add: duration changes while perceived pitch stays stable. */
    static float[] timeStretchPitchPreserving(float[] source, int start, int end,
                                               double rate, float sampleRate) throws InterruptedException {
        int inputLength = Math.max(1, end - start);
        if (inputLength < 512) return linearStretch(source, start, end, rate);
        int frameSize = 1024;
        while (frameSize < sampleRate * 0.045 && frameSize < 4096) frameSize <<= 1;
        frameSize = Math.min(frameSize, Math.max(512, Integer.highestOneBit(inputLength)));
        int synthesisHop = Math.max(64, frameSize / 4);
        int analysisHop = Math.max(1, (int) Math.round(synthesisHop * rate));
        int frameCount = Math.max(1, (int) Math.ceil(Math.max(0, inputLength - frameSize)
                / (double) analysisHop) + 1);
        int targetLength = Math.max(1, (int) Math.round(inputLength / rate));
        int workingLength = Math.max(targetLength, (frameCount - 1) * synthesisHop + frameSize);
        float[] output = new float[workingLength];
        float[] window = new float[frameSize];
        for (int i = 0; i < frameSize; i++) {
            window[i] = (float) (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / Math.max(1, frameSize - 1)));
        }

        int previous = 0;
        int maxFrameStart = Math.max(0, inputLength - frameSize);
        for (int frame = 0; frame < frameCount; frame++) {
            if ((frame & 0x3f) == 0 && Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Ses yavaşlatma iptal edildi");
            }
            int expected = Math.min(maxFrameStart, frame * analysisHop);
            int inputPosition = frame == 0 ? 0
                    : bestMatchingPosition(source, start, inputLength, expected, previous,
                    synthesisHop, frameSize);
            int outputPosition = frame * synthesisHop;
            int count = Math.min(frameSize, Math.min(inputLength - inputPosition, workingLength - outputPosition));
            for (int i = 0; i < count; i++) {
                output[outputPosition + i] += source[start + inputPosition + i] * window[i];
            }
            previous = inputPosition;
        }

        // Four Hann windows overlap in the steady-state region, producing a gain of two.
        int fade = Math.min(frameSize, targetLength / 2);
        for (int i = 0; i < targetLength; i++) {
            double gain = 2.0;
            if (i < fade) gain = Math.max(0.05, 2.0 * i / Math.max(1, fade));
            else if (i >= targetLength - fade) gain = Math.max(0.05,
                    2.0 * (targetLength - 1 - i) / Math.max(1, fade));
            output[i] = (float) Math.max(-1.0, Math.min(1.0, output[i] / gain));
        }
        if (output.length == targetLength) return output;
        float[] trimmed = new float[targetLength];
        System.arraycopy(output, 0, trimmed, 0, targetLength);
        return trimmed;
    }

    private static int bestMatchingPosition(float[] source, int start, int inputLength, int expected,
                                            int previous, int synthesisHop, int frameSize) {
        int maxFrameStart = Math.max(0, inputLength - frameSize);
        int radius = Math.min(96, Math.max(16, frameSize / 16));
        int from = Math.max(0, expected - radius);
        int to = Math.min(maxFrameStart, expected + radius);
        int reference = Math.min(maxFrameStart, previous + synthesisHop);
        int compareLength = Math.min(frameSize - synthesisHop,
                Math.min(inputLength - reference, inputLength - to));
        if (compareLength < 64) return expected;
        double best = -Double.MAX_VALUE;
        int bestPosition = expected;
        for (int candidate = from; candidate <= to; candidate += 8) {
            double dot = 0.0, leftEnergy = 0.0, rightEnergy = 0.0;
            for (int i = 0; i < compareLength; i += 16) {
                double left = source[start + reference + i];
                double right = source[start + candidate + i];
                dot += left * right;
                leftEnergy += left * left;
                rightEnergy += right * right;
            }
            double score = dot / Math.sqrt(Math.max(1e-12, leftEnergy * rightEnergy));
            if (score > best) { best = score; bestPosition = candidate; }
        }
        return bestPosition;
    }

    private static float[] linearStretch(float[] source, int start, int end, double rate) {
        int inputLength = Math.max(1, end - start);
        int outputLength = Math.max(1, (int) Math.round(inputLength / rate));
        float[] output = new float[outputLength];
        for (int i = 0; i < outputLength; i++) {
            double position = Math.min(inputLength - 1.0, i * rate);
            int left = (int) position;
            int right = Math.min(inputLength - 1, left + 1);
            double fraction = position - left;
            output[i] = (float) (source[start + left] * (1.0 - fraction) + source[start + right] * fraction);
        }
        return output;
    }
}
