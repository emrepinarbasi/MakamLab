package com.makambox.studio.ui;

import java.awt.Window;

/** Pluggable Word-report action; keeps the UI independent from the DOCX implementation. */
@FunctionalInterface
public interface MakamWordReportHandler {
    void export(Window owner, MakamStudyContext context) throws Exception;
}
