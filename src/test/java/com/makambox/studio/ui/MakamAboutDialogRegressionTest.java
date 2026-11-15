package com.makambox.studio.ui;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Keeps MakamLab's focused licence notice separate from MakamBox Studio. */
public final class MakamAboutDialogRegressionTest {
    private MakamAboutDialogRegressionTest() { }

    public static void main(String[] args) {
        String[][] rows = MakamAboutDialog.diagnosticLicenseRows();
        Set<String> components = Arrays.stream(rows).map(row -> row[0])
                .collect(Collectors.toSet());
        require(components.contains("MakamLab V1.1"), "MakamLab licence is missing");
        require(components.contains("MakamBox v1.0"), "MakamBox attribution is missing");
        require(components.stream().anyMatch(value -> value.startsWith("Apache POI")),
                "DOCX dependency licence is missing");
        require(components.stream().anyMatch(value -> value.startsWith("JLayer")),
                "MP3 decoder licence is missing");
        require(components.contains("FFmpeg 9.0.2 (Windows audio component)"),
                "Windows M4A decoder licence is missing");
        require(components.stream().anyMatch(value -> value.startsWith("OpenJDK")),
                "Bundled Java runtime licence is missing");
        require(components.stream().noneMatch(value -> value.contains("NeuralNote")
                        || value.contains("MuScriptor") || value.contains("LilyPond")
                        || value.contains("Hacklily") || value.contains("Node.js")),
                "MakamBox Studio transcription licences leaked into MakamLab About");
        for (String[] row : rows) {
            require(row.length == 2 && !row[0].isBlank() && !row[1].isBlank(),
                    "Every component must display its licence beside it");
        }
        System.out.println("MakamAboutDialogRegressionTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
