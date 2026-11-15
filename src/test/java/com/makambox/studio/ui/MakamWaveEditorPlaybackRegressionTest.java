package com.makambox.studio.ui;

/** Guards full-length editor playback while keeping each prepared block bounded. */
public final class MakamWaveEditorPlaybackRegressionTest {
    public static void main(String[] args) {
        float rate = 44_100f;
        int ninetySeconds = Math.round(rate * 90f);
        int start = Math.round(rate * 5f);

        int fullEnd = MakamWaveEditorDialog.requestedPlaybackEnd(
                start, false, false, start, ninetySeconds, rate);
        if (fullEnd != ninetySeconds) {
            throw new AssertionError("Normal playback was still capped: " + fullEnd);
        }

        int selectedEnd = Math.round(rate * 72f);
        int selectionEnd = MakamWaveEditorDialog.requestedPlaybackEnd(
                start, false, true, selectedEnd, ninetySeconds, rate);
        if (selectionEnd != selectedEnd) {
            throw new AssertionError("Long selection was still capped: " + selectionEnd);
        }

        int previewEnd = MakamWaveEditorDialog.requestedPlaybackEnd(
                start, true, false, start, ninetySeconds, rate);
        if (previewEnd != start + Math.round(rate * 2f)) {
            throw new AssertionError("Two-second preview changed: " + previewEnd);
        }

        int firstChunk = MakamWaveEditorDialog.nextPlaybackChunkEnd(start, fullEnd, rate);
        int expectedChunk = start + Math.round(rate * 30f);
        if (firstChunk != expectedChunk) {
            throw new AssertionError("Playback chunk is not memory-bounded: " + firstChunk);
        }
        int finalChunk = MakamWaveEditorDialog.nextPlaybackChunkEnd(
                Math.round(rate * 80f), fullEnd, rate);
        if (finalChunk != fullEnd) {
            throw new AssertionError("Final playback chunk does not reach session end: " + finalChunk);
        }

        double resumed = MakamWaveEditorDialog.absolutePlaybackSeconds(30.0, 7.25);
        if (Math.abs(resumed - 37.25) > 1e-9) {
            throw new AssertionError("Pause/resume lost the edited-timeline offset: " + resumed);
        }

        System.out.println("MakamWaveEditorPlaybackRegressionTest passed");
    }
}
