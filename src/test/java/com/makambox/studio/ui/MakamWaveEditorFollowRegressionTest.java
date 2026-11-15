package com.makambox.studio.ui;

/** Guards the centered playhead-follow math used by the waveform timeline. */
public final class MakamWaveEditorFollowRegressionTest {
    public static void main(String[] args) {
        assertEquals(4_000, MakamWaveEditorDialog.centeredViewStart(5_000, 2_000, 10_000));
        assertEquals(0, MakamWaveEditorDialog.centeredViewStart(200, 2_000, 10_000));
        assertEquals(8_000, MakamWaveEditorDialog.centeredViewStart(9_800, 2_000, 10_000));
        assertEquals(0, MakamWaveEditorDialog.centeredViewStart(5_000, 20_000, 10_000));
        assertEquals(0, MakamWaveEditorDialog.centeredViewStart(0, 0, 0));
        System.out.println("MakamWaveEditorFollowRegressionTest passed");
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
