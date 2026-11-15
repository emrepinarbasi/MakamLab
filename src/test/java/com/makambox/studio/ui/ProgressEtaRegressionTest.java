package com.makambox.studio.ui;

/** Deterministic regression for the analysis percentage and ETA text. */
public final class ProgressEtaRegressionTest {
    public static void main(String[] args) {
        assertEquals("Hazırlanıyor…", ProgressEta.describe(0, 8.0));
        assertEquals("%25 • geçen 0:10 • kalan ~0:30", ProgressEta.describe(25, 10.0));
        assertEquals("%50 • geçen 1:00 • kalan ~1:00", ProgressEta.describe(50, 60.0));
        assertEquals("%100 • 1:01:01", ProgressEta.describe(100, 3661.0));
        System.out.println("ProgressEtaRegressionTest passed");
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected '" + expected + "' but got '" + actual + "'");
    }
}
