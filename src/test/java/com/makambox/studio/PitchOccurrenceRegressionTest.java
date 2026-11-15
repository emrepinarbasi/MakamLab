package com.makambox.studio;

import com.makambox.studio.analysis.DirectionalIntonationAnalyzer;
import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.AnalysisReport;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.EventType;
import com.makambox.studio.model.GroupStatistics;
import com.makambox.studio.model.MelodicDirection;
import com.makambox.studio.model.PitchFrame;
import com.makambox.studio.model.PitchOccurrence;

import java.util.ArrayList;
import java.util.List;

/** Verifies per-press direction labels, holds, and event/duration-weighted averages. */
public final class PitchOccurrenceRegressionTest {
    private static final double STEP = 0.020;
    private static final double TONIC_HZ = 220.0;
    private static final double TARGET = PitchTracker.hertzToCent(TONIC_HZ);

    public static void main(String[] args) {
        List<PitchFrame> frames = new ArrayList<>();
        addVoiced(frames, TARGET - 180, 10);
        addVoiced(frames, TARGET - 10, 12);
        addVoiced(frames, TARGET + 180, 10);
        addSilence(frames, 18);

        addVoiced(frames, TARGET + 180, 10);
        addVoiced(frames, TARGET + 15, 12);
        addVoiced(frames, TARGET - 180, 10);
        addSilence(frames, 18);

        addVoiced(frames, TARGET - 180, 10);
        addVoiced(frames, TARGET + 5, 46);
        addSilence(frames, 20);

        addVoiced(frames, TARGET - 180, 10);
        addVoiced(frames, TARGET + 20, 12);
        addVoiced(frames, TARGET - 180, 10);
        addSilence(frames, 18);

        addVoiced(frames, TARGET + 8, 2);
        addSilence(frames, 18);

        AudioData audio = new AudioData("occurrence-regression", null, 44_100f,
                new float[(int) Math.ceil((frames.size() * STEP + 0.2) * 44_100)]);
        AnalysisReport report = new DirectionalIntonationAnalyzer().analyze(audio, frames, config());
        check(report.occurrences().size() == 5, "dört basış ve bir kısa temas korunmalı");
        check(report.measuredOccurrences().size() == 4, "dört ölçülebilir basış olmalı");
        check(report.shortContacts().size() == 1
                        && report.shortContacts().get(0).eventType() == EventType.SHORT_CONTACT,
                "alt süreli temas kaybolmadan ayrı işaretlenmeli");

        PitchOccurrence ascending = report.occurrences().get(0);
        check(ascending.entryDirection() == MelodicDirection.ASCENDING
                        && ascending.exitDirection() == MelodicDirection.ASCENDING
                        && ascending.direction() == MelodicDirection.ASCENDING,
                "çıkıcı kullanımın giriş, çıkış ve net yönü");

        PitchOccurrence descending = report.occurrences().get(1);
        check(descending.entryDirection() == MelodicDirection.DESCENDING
                        && descending.exitDirection() == MelodicDirection.DESCENDING
                        && descending.direction() == MelodicDirection.DESCENDING,
                "inici kullanımın giriş, çıkış ve net yönü");

        PitchOccurrence hold = report.occurrences().get(2);
        check(hold.eventType() == EventType.CADENTIAL_HOLD, "uzun ve kararlı kullanım kalış olmalı");

        PitchOccurrence turning = report.occurrences().get(3);
        check(turning.entryDirection() == MelodicDirection.ASCENDING
                        && turning.exitDirection() == MelodicDirection.DESCENDING
                        && turning.direction() == MelodicDirection.LEVEL,
                "dönüşlü kullanım zorla çıkıcı veya inici yapılmamalı");

        GroupStatistics all = report.statistics(value -> true);
        check(all.count() == 4, "ortalama yalnız dört ölçülebilir olayı içermeli");
        check(Math.abs(all.meanDeviationCent() - 7.5) < 0.01, "olay ağırlıklı ortalama doğru olmalı");
        check(Math.abs(all.durationWeightedDeviationCent() - all.meanDeviationCent()) > 0.5,
                "süre ağırlıklı ortalama ayrı hesaplanmalı");
        check(report.statistics(value -> value.eventType() != EventType.PASSING).count() == 1,
                "kalış ortalaması ayrı üretilebilmeli");
        verifyShortDropoutIsBridged();
        verifyAsymmetricTargetWindow();
        verifyUserWindowIsAnExactBoundary();
        System.out.printf("PITCH_OCCURRENCE_REGRESSION_OK events=%d mean=%+.2f weighted=%+.2f%n",
                all.count(), all.meanDeviationCent(), all.durationWeightedDeviationCent());
    }

    private static void verifyShortDropoutIsBridged() {
        List<PitchFrame> frames = new ArrayList<>();
        addVoiced(frames, TARGET + 4, 4);
        addSilence(frames, 2);
        addVoiced(frames, TARGET + 4, 4);
        addSilence(frames, 12);
        AudioData audio = new AudioData("dropout-regression", null, 44_100f,
                new float[(int) Math.ceil((frames.size() * STEP + 0.2) * 44_100)]);
        AnalysisReport report = new DirectionalIntonationAnalyzer().analyze(audio, frames, config());
        check(report.rawTargetContactRegions() == 2, "ham temaslar birleştirme öncesinde sayılmalı");
        check(report.occurrences().size() == 1, "40 ms güven kaybı tek basışı bölmemeli");
        check(report.measuredOccurrences().size() == 1, "birleştirilen basış ölçülebilir olmalı");
    }

    private static void verifyAsymmetricTargetWindow() {
        List<PitchFrame> frames = new ArrayList<>();
        addVoiced(frames, TARGET - 70, 8);
        addSilence(frames, 12);
        addVoiced(frames, TARGET + 70, 8);
        addSilence(frames, 12);
        AudioData audio = new AudioData("window-regression", null, 44_100f,
                new float[(int) Math.ceil((frames.size() * STEP + 0.2) * 44_100)]);
        AnalysisConfig asymmetric = new AnalysisConfig("Test", "Karar", "Hedef", TONIC_HZ, 0.0,
                4.0, 2.5, 1.0, 0.10, 0.35, 0.70, 0.28, 0.75, 0.55,
                0.12, 1.10, 0.72, 100.0, 700.0, 1.5, 0.02, 32.0, false);
        AnalysisReport report = new DirectionalIntonationAnalyzer().analyze(audio, frames, asymmetric);
        check(report.measuredOccurrences().size() == 1, "asimetrik pencere üst komşu perdeyi dışlamalı");
        check(report.measuredOccurrences().get(0).deviationCent() < 0,
                "alt taraftaki hedef kullanımı korunmalı");
    }

    private static void verifyUserWindowIsAnExactBoundary() {
        double upper = TARGET + 5.0 * AnalysisConfig.COMMA_CENT;
        List<PitchFrame> frames = new ArrayList<>();
        addVoiced(frames, upper - 0.1, 8);
        addVoiced(frames, upper + 5.0, 8);
        addSilence(frames, 12);
        AudioData audio = new AudioData("exact-window-regression", null, 44_100f,
                new float[(int) Math.ceil((frames.size() * STEP + 0.2) * 44_100)]);
        AnalysisConfig asymmetric = new AnalysisConfig("Test", "Karar", "Hedef", TONIC_HZ, 0.0,
                3.0, 5.0, 1.0, 0.10, 0.35, 0.70, 0.28, 0.75, 0.55,
                0.12, 1.10, 0.72, 100.0, 700.0, 1.5, 0.02, 32.0, false);
        AnalysisReport report = new DirectionalIntonationAnalyzer().analyze(audio, frames, asymmetric);
        check(report.occurrences().size() == 1, "kesin hedef alanı içindeki tek olay korunmalı");
        check(report.occurrences().get(0).endSeconds() <= 8 * STEP + 0.001,
                "kullanıcının üst sınırı dışındaki kareler olaya eklenmemeli");
        check(report.occurrences().get(0).observedCent() <= upper,
                "sınır dışı perde hedef olayın ortalamasına girmemeli");
    }

    private static void addVoiced(List<PitchFrame> frames, double cent, int count) {
        for (int i = 0; i < count; i++) {
            frames.add(new PitchFrame(frames.size() * STEP, PitchTracker.centToHertz(cent), cent, 0.96, 0.12));
        }
    }

    private static void addSilence(List<PitchFrame> frames, int count) {
        for (int i = 0; i < count; i++) frames.add(new PitchFrame(frames.size() * STEP, 0.0, Double.NaN, 0.0, 0.0));
    }

    private static AnalysisConfig config() {
        return new AnalysisConfig("Test", "Karar", "Hedef", TONIC_HZ, 0.0,
                4.0, 4.0, 1.0, 0.10, 0.35, 0.70, 0.28, 0.75, 0.55,
                0.12, 1.10, 0.72, 100.0, 700.0, 1.5, 0.02, 32.0, false);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
