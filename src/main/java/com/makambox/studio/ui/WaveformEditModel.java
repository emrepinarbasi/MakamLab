package com.makambox.studio.ui;

import com.makambox.studio.model.AudioData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.TreeSet;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.IntConsumer;

/**
 * Non-destructive mono waveform edit list.
 *
 * <p>Edits retain ranges of the decoded source instead of copying a 40 minute
 * recording for every undo step.  A new sample array is created only when a
 * preview range or the final edited WAV is requested.</p>
 */
final class WaveformEditModel {
    private static final int MAXIMUM_UNDO_STATES = 40;

    private final AudioData source;
    private final WaveformCache sourceCache;
    private final Deque<State> undo = new ArrayDeque<>();
    private List<Piece> pieces;
    private List<Piece> clipboard = List.of();
    private TreeSet<Integer> splitPoints = new TreeSet<>();

    WaveformEditModel(AudioData source) {
        if (source == null) throw new IllegalArgumentException("Ses verisi gereklidir");
        this.source = source;
        this.sourceCache = new WaveformCache(source.samples());
        this.pieces = source.samples().length == 0 ? List.of()
                : List.of(new Piece(0, source.samples().length));
    }

    AudioData source() { return source; }
    float sampleRate() { return source.sampleRate(); }
    int sampleCount() { return pieces.stream().mapToInt(Piece::length).sum(); }
    double durationSeconds() { return sampleCount() / (double) sampleRate(); }
    boolean canUndo() { return !undo.isEmpty(); }
    boolean hasClipboard() { return !clipboard.isEmpty(); }
    List<Integer> splitPoints() { return List.copyOf(splitPoints); }

    int secondsToSample(double seconds) {
        return clampSample((int) Math.round(seconds * sampleRate()));
    }

    double sampleToSeconds(int sample) {
        return clampSample(sample) / (double) sampleRate();
    }

    /** Source time corresponding to a point in the edited timeline. */
    double sourceSecondsAt(int logicalSample) {
        if (pieces.isEmpty()) return 0.0;
        int position = Math.max(0, Math.min(Math.max(0, sampleCount() - 1), logicalSample));
        int logical = 0;
        for (Piece piece : pieces) {
            if (position < logical + piece.length()) {
                return (piece.sourceStart() + position - logical) / (double) sampleRate();
            }
            logical += piece.length();
        }
        Piece last = pieces.get(pieces.size() - 1);
        return Math.max(0, last.sourceEnd() - 1) / (double) sampleRate();
    }

    List<SourceRange> sourceRanges(int requestedStart, int requestedEnd) {
        ArrayList<SourceRange> result = new ArrayList<>();
        for (Piece piece : slice(requestedStart, requestedEnd)) {
            result.add(new SourceRange(piece.sourceStart() / (double) sampleRate(),
                    piece.sourceEnd() / (double) sampleRate()));
        }
        return List.copyOf(result);
    }

    void cut(int requestedStart, int requestedEnd) {
        remove(requestedStart, requestedEnd, true);
    }

    void delete(int requestedStart, int requestedEnd) {
        remove(requestedStart, requestedEnd, false);
    }

    void trimTo(int requestedStart, int requestedEnd) {
        Range range = normalizedRange(requestedStart, requestedEnd);
        if (range.length() <= 0 || range.length() == sampleCount()) return;
        pushUndo();
        pieces = slice(range.start(), range.end());
        TreeSet<Integer> adjusted = new TreeSet<>();
        for (int point : splitPoints) {
            if (point > range.start() && point < range.end()) adjusted.add(point - range.start());
        }
        splitPoints = adjusted;
    }

    void splitAt(int requestedSample) {
        int sample = clampSample(requestedSample);
        if (sample <= 0 || sample >= sampleCount() || splitPoints.contains(sample)) return;
        pushUndo();
        splitPoints.add(sample);
    }

    void pasteAt(int requestedSample) {
        if (clipboard.isEmpty()) return;
        int sample = clampSample(requestedSample);
        pushUndo();
        List<Piece> before = slice(0, sample);
        List<Piece> after = slice(sample, sampleCount());
        ArrayList<Piece> combined = new ArrayList<>(before.size() + clipboard.size() + after.size());
        combined.addAll(before);
        combined.addAll(clipboard);
        combined.addAll(after);
        pieces = mergeAdjacent(combined);
        int inserted = clipboard.stream().mapToInt(Piece::length).sum();
        TreeSet<Integer> adjusted = new TreeSet<>();
        for (int point : splitPoints) adjusted.add(point <= sample ? point : point + inserted);
        adjusted.add(sample);
        adjusted.add(sample + inserted);
        adjusted.remove(0);
        adjusted.remove(sampleCount());
        splitPoints = adjusted;
    }

    void undo() {
        if (undo.isEmpty()) return;
        State state = undo.removeLast();
        pieces = state.pieces();
        splitPoints = new TreeSet<>(state.splitPoints());
        clipboard = state.clipboard();
    }

    float[] materialize() { return materialize(0, sampleCount()); }

    /** Streams 16-bit little-endian PCM without allocating another full recording. */
    void writePcm16(DataOutputStream output) throws IOException {
        writePcm16(output, ignored -> { });
    }

    /** Streams PCM in buffered blocks and reports a coarse 0..100 completion percentage. */
    void writePcm16(DataOutputStream output, IntConsumer progress) throws IOException {
        IntConsumer reporter = progress == null ? ignored -> { } : progress;
        byte[] buffer = new byte[256 * 1024];
        int bufferPosition = 0;
        long writtenSamples = 0L;
        long totalSamples = Math.max(1L, sampleCount());
        int lastPercent = -1;
        for (Piece piece : pieces) {
            for (int index = piece.sourceStart(); index < piece.sourceEnd(); index++) {
                int value = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE,
                        Math.round(source.samples()[index] * Short.MAX_VALUE)));
                buffer[bufferPosition++] = (byte) (value & 0xff);
                buffer[bufferPosition++] = (byte) ((value >>> 8) & 0xff);
                if (bufferPosition == buffer.length) {
                    output.write(buffer, 0, bufferPosition);
                    bufferPosition = 0;
                }
                writtenSamples++;
                if ((writtenSamples & 0xffffL) == 0L) {
                    int percent = (int) Math.min(99L, writtenSamples * 100L / totalSamples);
                    if (percent != lastPercent) { reporter.accept(percent); lastPercent = percent; }
                }
            }
        }
        if (bufferPosition > 0) output.write(buffer, 0, bufferPosition);
        reporter.accept(100);
    }

    float[] materialize(int requestedStart, int requestedEnd) {
        Range range = normalizedRange(requestedStart, requestedEnd);
        float[] result = new float[range.length()];
        int target = 0;
        for (Piece piece : slice(range.start(), range.end())) {
            int count = piece.length();
            System.arraycopy(source.samples(), piece.sourceStart(), result, target, count);
            target += count;
        }
        return result;
    }

    WaveformCache.Range range(int requestedStart, int requestedEnd) {
        Range range = normalizedRange(requestedStart, requestedEnd);
        if (range.length() <= 0) return new WaveformCache.Range(0f, 0f);
        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;
        for (Piece piece : slice(range.start(), range.end())) {
            WaveformCache.Range value = sourceCache.range(piece.sourceStart(), piece.sourceEnd());
            minimum = Math.min(minimum, value.minimum());
            maximum = Math.max(maximum, value.maximum());
        }
        return minimum == Float.POSITIVE_INFINITY ? new WaveformCache.Range(0f, 0f)
                : new WaveformCache.Range(minimum, maximum);
    }

    private void remove(int requestedStart, int requestedEnd, boolean copy) {
        Range range = normalizedRange(requestedStart, requestedEnd);
        if (range.length() <= 0 || range.length() >= sampleCount()) return;
        pushUndo();
        if (copy) clipboard = slice(range.start(), range.end());
        ArrayList<Piece> retained = new ArrayList<>();
        retained.addAll(slice(0, range.start()));
        retained.addAll(slice(range.end(), sampleCount()));
        pieces = mergeAdjacent(retained);
        TreeSet<Integer> adjusted = new TreeSet<>();
        for (int point : splitPoints) {
            if (point < range.start()) adjusted.add(point);
            else if (point > range.end()) adjusted.add(point - range.length());
        }
        adjusted.add(range.start());
        adjusted.remove(0);
        adjusted.remove(sampleCount());
        splitPoints = adjusted;
    }

    private List<Piece> slice(int requestedStart, int requestedEnd) {
        Range range = normalizedRange(requestedStart, requestedEnd);
        if (range.length() <= 0) return List.of();
        ArrayList<Piece> result = new ArrayList<>();
        int logical = 0;
        for (Piece piece : pieces) {
            int pieceStart = logical;
            int pieceEnd = logical + piece.length();
            int overlapStart = Math.max(range.start(), pieceStart);
            int overlapEnd = Math.min(range.end(), pieceEnd);
            if (overlapStart < overlapEnd) {
                result.add(new Piece(piece.sourceStart() + overlapStart - pieceStart,
                        piece.sourceStart() + overlapEnd - pieceStart));
            }
            logical = pieceEnd;
            if (logical >= range.end()) break;
        }
        return mergeAdjacent(result);
    }

    private void pushUndo() {
        if (undo.size() >= MAXIMUM_UNDO_STATES) undo.removeFirst();
        undo.addLast(new State(List.copyOf(pieces), new TreeSet<>(splitPoints), List.copyOf(clipboard)));
    }

    private Range normalizedRange(int first, int second) {
        int start = clampSample(Math.min(first, second));
        int end = clampSample(Math.max(first, second));
        return new Range(start, end);
    }

    private int clampSample(int value) { return Math.max(0, Math.min(sampleCount(), value)); }

    private static List<Piece> mergeAdjacent(List<Piece> input) {
        if (input.isEmpty()) return List.of();
        ArrayList<Piece> result = new ArrayList<>();
        for (Piece piece : input) {
            if (piece.length() <= 0) continue;
            if (!result.isEmpty() && result.get(result.size() - 1).sourceEnd() == piece.sourceStart()) {
                Piece previous = result.remove(result.size() - 1);
                result.add(new Piece(previous.sourceStart(), piece.sourceEnd()));
            } else result.add(piece);
        }
        return List.copyOf(result);
    }

    private record Piece(int sourceStart, int sourceEnd) {
        int length() { return sourceEnd - sourceStart; }
    }
    record SourceRange(double startSeconds, double endSeconds) { }
    private record Range(int start, int end) { int length() { return end - start; } }
    private record State(List<Piece> pieces, TreeSet<Integer> splitPoints, List<Piece> clipboard) { }
}
