package com.makambox.studio.ui;

import java.nio.file.Path;

/** Headless-safe guard for the best-effort DOCX auto-open path. */
public final class MakamDocxAutoOpenRegressionTest {
    public static void main(String[] args) {
        if (MakamDocxReportHandler.openSavedReport(null)) {
            throw new AssertionError("A missing report must not be opened");
        }
        Path missing = Path.of(System.getProperty("java.io.tmpdir"),
                "makamlab-report-that-does-not-exist.docx");
        if (MakamDocxReportHandler.openSavedReport(missing)) {
            throw new AssertionError("A non-existent report must not be opened");
        }
        System.out.println("MakamDocxAutoOpenRegressionTest passed");
    }
}
