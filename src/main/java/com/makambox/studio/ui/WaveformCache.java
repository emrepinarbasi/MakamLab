package com.makambox.studio.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Exact min/max waveform pyramid used by long-recording views. */
final class WaveformCache {
    private static final int BASE_BLOCK_SAMPLES = 256;

    private final float[] samples;
    private final List<Level> levels;

    WaveformCache(float[] samples) {
        this.samples = samples == null ? new float[0] : samples;
        this.levels = buildLevels(this.samples);
    }

    Range range(int requestedStart, int requestedEnd) {
        int start = Math.max(0, Math.min(samples.length, requestedStart));
        int end = Math.max(start, Math.min(samples.length, requestedEnd));
        if (start >= end) return new Range(0f, 0f);

        Level level = chooseLevel(end - start);
        if (level == null) return scan(start, end);

        int firstFullBlock = (start + level.blockSamples() - 1) / level.blockSamples();
        int afterLastFullBlock = end / level.blockSamples();
        int firstFullSample = Math.min(end, firstFullBlock * level.blockSamples());
        int afterLastFullSample = Math.max(firstFullSample,
                Math.min(end, afterLastFullBlock * level.blockSamples()));

        Range head = scan(start, firstFullSample);
        float minimum = head.minimum();
        float maximum = head.maximum();
        boolean populated = firstFullSample > start;
        for (int block = firstFullBlock; block < afterLastFullBlock; block++) {
            minimum = populated ? Math.min(minimum, level.minimum()[block]) : level.minimum()[block];
            maximum = populated ? Math.max(maximum, level.maximum()[block]) : level.maximum()[block];
            populated = true;
        }
        Range tail = scan(afterLastFullSample, end);
        if (end > afterLastFullSample) {
            minimum = populated ? Math.min(minimum, tail.minimum()) : tail.minimum();
            maximum = populated ? Math.max(maximum, tail.maximum()) : tail.maximum();
            populated = true;
        }
        return populated ? new Range(minimum, maximum) : scan(start, end);
    }

    int levelCount() { return levels.size(); }

    private Level chooseLevel(int rangeSamples) {
        int target = Math.max(BASE_BLOCK_SAMPLES, rangeSamples / 4);
        Level selected = null;
        for (Level level : levels) {
            if (level.blockSamples() > target) break;
            selected = level;
        }
        return selected;
    }

    private Range scan(int start, int end) {
        if (start >= end) return new Range(0f, 0f);
        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;
        for (int index = start; index < end; index++) {
            float value = samples[index];
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        return new Range(minimum, maximum);
    }

    private static List<Level> buildLevels(float[] samples) {
        if (samples.length < BASE_BLOCK_SAMPLES) return List.of();
        List<Level> result = new ArrayList<>();
        int blocks = (samples.length + BASE_BLOCK_SAMPLES - 1) / BASE_BLOCK_SAMPLES;
        float[] minimum = new float[blocks];
        float[] maximum = new float[blocks];
        for (int block = 0; block < blocks; block++) {
            checkCancelled(block);
            int start = block * BASE_BLOCK_SAMPLES;
            int end = Math.min(samples.length, start + BASE_BLOCK_SAMPLES);
            float low = Float.POSITIVE_INFINITY;
            float high = Float.NEGATIVE_INFINITY;
            for (int index = start; index < end; index++) {
                low = Math.min(low, samples[index]);
                high = Math.max(high, samples[index]);
            }
            minimum[block] = low;
            maximum[block] = high;
        }
        int blockSamples = BASE_BLOCK_SAMPLES;
        result.add(new Level(blockSamples, minimum, maximum));
        while (minimum.length > 1) {
            int nextBlocks = (minimum.length + 1) / 2;
            float[] nextMinimum = new float[nextBlocks];
            float[] nextMaximum = new float[nextBlocks];
            for (int block = 0; block < nextBlocks; block++) {
                checkCancelled(block);
                int first = block * 2;
                int second = Math.min(minimum.length - 1, first + 1);
                nextMinimum[block] = Math.min(minimum[first], minimum[second]);
                nextMaximum[block] = Math.max(maximum[first], maximum[second]);
            }
            blockSamples = Math.multiplyExact(blockSamples, 2);
            minimum = nextMinimum;
            maximum = nextMaximum;
            result.add(new Level(blockSamples, minimum, maximum));
        }
        return List.copyOf(result);
    }

    private static void checkCancelled(int block) {
        if ((block & 0x3fff) == 0 && Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Waveform cache construction cancelled");
        }
    }

    record Range(float minimum, float maximum) { }
    private record Level(int blockSamples, float[] minimum, float[] maximum) { }
}
