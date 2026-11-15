package com.makambox.studio.ui;

import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.AnalysisReport;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.study.FirstFourProfileSelection;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

/** Ensures a report can never silently use a tonic other than the analysed tonic. */
public final class MakamStudyContextTonicInvariantRegressionTest {
    private MakamStudyContextTonicInvariantRegressionTest() { }

    public static void main(String[] arguments) {
        double manuallyConfirmedTonic = 221.148706;
        AudioData audio = new AudioData("manual-tonic.wav", Path.of("manual-tonic.wav"),
                44_100, new float[4_410]);
        AnalysisConfig config = config(manuallyConfirmedTonic);
        AnalysisReport report = new AnalysisReport(audio, config, List.of(), List.of(),
                List.of(), ZonedDateTime.now());

        MakamStudyContext exact = context(audio, report, manuallyConfirmedTonic);
        if (Double.doubleToLongBits(exact.tonicHertz())
                != Double.doubleToLongBits(manuallyConfirmedTonic)) {
            throw new AssertionError("The exact manual tonic was not preserved");
        }

        boolean rejected = false;
        try {
            context(audio, report, 218.280434);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        if (!rejected) {
            throw new AssertionError("A report/context tonic mismatch must be rejected");
        }
        System.out.println("MAKAM_STUDY_CONTEXT_TONIC_INVARIANT_OK");
    }

    private static MakamStudyContext context(AudioData audio, AnalysisReport report,
                                              double tonicHertz) {
        return new MakamStudyContext(Path.of("manual-tonic.wav"), audio, List.of(), report,
                List.of(), List.of(), null, MakamAnalysisMode.SINGLE_PITCH,
                null, null, null, tonicHertz, "Grafikten seçilen bölge",
                1.0, 1.4, 0.92, null, null, null,
                FirstFourProfileSelection.defaults());
    }

    private static AnalysisConfig config(double tonicHertz) {
        return new AnalysisConfig("Kurdilihicazkar", "Rast", "Kürdi", tonicHertz,
                90.566, 4.0, 4.0, 1.0, 0.06, 0.22, 0.35, 0.45,
                0.45, 0.55, 0.08, 0.80, 0.68, 90.0, 800.0,
                1.0, 0.04, 25.0, false);
    }
}
