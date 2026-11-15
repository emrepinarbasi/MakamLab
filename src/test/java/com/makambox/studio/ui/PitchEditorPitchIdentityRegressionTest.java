package com.makambox.studio.ui;

import com.makambox.studio.AnalyzerSelfTest;
import com.makambox.studio.analysis.AllPitchEventAnalyzer;
import com.makambox.studio.model.AnalysisReport;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.theory.TheoryCatalog;

import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** The table and academic editor must render the very same stable pitch identity. */
public final class PitchEditorPitchIdentityRegressionTest {
    public static void main(String[] args) throws Exception {
        AnalysisReport report = AnalyzerSelfTest.createReport();
        TheoryCatalog theory = TheoryCatalog.loadDefault();
        TheoryCatalog.Makam makam = theory.makams().stream()
                .filter(value -> value.toString().equals(report.config().makamName()))
                .findFirst().orElseThrow();
        List<PitchEvent> events = new AllPitchEventAnalyzer().analyze(
                report.frames(), report.config(), theory, makam);
        if (events.isEmpty()) throw new AssertionError("test report should contain pitch events");

        PitchEditorPanel[] holder = new PitchEditorPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            PitchEditorPanel editor = new PitchEditorPanel();
            editor.setData(report, events);
            holder[0] = editor;
        });

        Field blobsField = PitchEditorPanel.class.getDeclaredField("allPitchBlobs");
        blobsField.setAccessible(true);
        List<?> blobs = (List<?>) blobsField.get(holder[0]);
        if (blobs.size() != events.size()) {
            throw new AssertionError("editor must preserve every canonical pitch event");
        }

        Method noteKey = blobs.get(0).getClass().getDeclaredMethod("noteKey");
        Method laneCent = blobs.get(0).getClass().getDeclaredMethod("laneCent");
        noteKey.setAccessible(true);
        laneCent.setAccessible(true);
        for (int index = 0; index < events.size(); index++) {
            PitchEvent event = events.get(index);
            Object blob = blobs.get(index);
            String actualKey = (String) noteKey.invoke(blob);
            double actualLaneCent = (double) laneCent.invoke(blob);
            if (!event.noteKey().equals(actualKey)) {
                throw new AssertionError("pitch identity changed in editor: "
                        + event.noteKey() + " -> " + actualKey);
            }
            if (Math.abs(event.occurrence().expectedCent() - actualLaneCent) > 1.0e-8) {
                throw new AssertionError("editor block left its classified theory lane: " + actualKey);
            }
        }
        System.out.println("PITCH_EDITOR_IDENTITY_OK events=" + events.size());
    }
}
