package com.makambox.studio.ui;

import java.util.Random;
import java.util.concurrent.CancellationException;

public final class WaveformCacheRegressionTest {
    private WaveformCacheRegressionTest() { }

    public static void main(String[] arguments) {
        float[] samples = new float[131_173];
        Random random = new Random(53L);
        for (int index = 0; index < samples.length; index++) {
            samples[index] = random.nextFloat() * 2.0f - 1.0f;
        }
        samples[777] = -1.25f;
        samples[97_531] = 1.30f;

        WaveformCache cache = new WaveformCache(samples);
        if (cache.levelCount() < 2) throw new AssertionError("Waveform pyramid was not built");
        int[][] ranges = {
                {0, 1}, {0, 255}, {0, 256}, {3, 8_117}, {777, 97_532},
                {12_345, 12_678}, {40_001, 130_999}, {0, samples.length}
        };
        for (int[] range : ranges) assertExact(samples, cache, range[0], range[1]);
        for (int iteration = 0; iteration < 250; iteration++) {
            int start = random.nextInt(samples.length);
            int end = start + random.nextInt(samples.length - start + 1);
            assertExact(samples, cache, start, end);
        }
        assertInterruptedBuildCancels(samples);
        System.out.println("Waveform cache regression checks passed.");
    }

    private static void assertInterruptedBuildCancels(float[] samples) {
        Thread.currentThread().interrupt();
        try {
            new WaveformCache(samples);
            throw new AssertionError("Interrupted waveform build should be cancelled");
        } catch (CancellationException expected) {
            // Expected: stale long-audio jobs must stop before publishing a cache.
        } finally {
            Thread.interrupted();
        }
    }

    private static void assertExact(float[] samples, WaveformCache cache, int start, int end) {
        float minimum = 0f;
        float maximum = 0f;
        if (end > start) {
            minimum = Float.POSITIVE_INFINITY;
            maximum = Float.NEGATIVE_INFINITY;
            for (int index = start; index < end; index++) {
                minimum = Math.min(minimum, samples[index]);
                maximum = Math.max(maximum, samples[index]);
            }
        }
        WaveformCache.Range actual = cache.range(start, end);
        if (Float.compare(minimum, actual.minimum()) != 0
                || Float.compare(maximum, actual.maximum()) != 0) {
            throw new AssertionError("Waveform range mismatch at " + start + "–" + end
                    + ": expected " + minimum + "/" + maximum
                    + ", actual " + actual.minimum() + "/" + actual.maximum());
        }
    }
}
