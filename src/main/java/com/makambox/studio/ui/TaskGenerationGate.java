package com.makambox.studio.ui;

/**
 * Tiny monotonic gate used to reject asynchronous results that belong to a
 * previously selected audio source.  Swing workers may finish after
 * cancellation, so cancellation alone is not a correctness guarantee.
 */
final class TaskGenerationGate {
    private long generation;

    synchronized long advance() {
        return ++generation;
    }

    synchronized long current() {
        return generation;
    }

    synchronized boolean accepts(long candidate) {
        return candidate == generation;
    }
}
