package com.makambox.studio;

import com.makambox.studio.analysis.DirectionalIntonationAnalyzer;
import com.makambox.studio.dsp.PitchTracker;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.AnalysisReport;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.GlissandoEvent;
import com.makambox.studio.model.GlissandoType;
import com.makambox.studio.model.MelodicDirection;
import com.makambox.studio.model.PitchFrame;

import java.util.ArrayList;
import java.util.List;

/** Regression cases for false octave jumps and genuine continuous glides. */
public final class GlissandoRegressionTest {
    private static final double STEP = 0.020;
    private static final double TONIC_HZ = 220.0;
    private static final double TARGET = PitchTracker.hertzToCent(TONIC_HZ);

    public static void main(String[] args) {
        assertGlide(linear(TARGET - 120.0, TARGET + 120.0, 0.40),
                MelodicDirection.ASCENDING, GlissandoType.THROUGH_TARGET,
                "çıkıcı olağan glisendo");
        assertGlide(linear(TARGET + 120.0, TARGET - 120.0, 0.40),
                MelodicDirection.DESCENDING, GlissandoType.THROUGH_TARGET,
                "inici olağan glisendo");
        assertGlide(linear(TARGET - 260.0, TARGET + 260.0, 0.52),
                MelodicDirection.ASCENDING, GlissandoType.THROUGH_TARGET,
                "çıkıcı geniş glisendo");
        assertGlide(linear(TARGET + 260.0, TARGET - 260.0, 0.52),
                MelodicDirection.DESCENDING, GlissandoType.THROUGH_TARGET,
                "inici geniş glisendo");
        assertGlide(linear(TARGET - 11.5, TARGET + 11.5, 0.08),
                MelodicDirection.ASCENDING, GlissandoType.THROUGH_TARGET,
                "çıkıcı kısa ve dar glisendo");
        assertGlide(linear(TARGET + 11.5, TARGET - 11.5, 0.08),
                MelodicDirection.DESCENDING, GlissandoType.THROUGH_TARGET,
                "inici kısa ve dar glisendo");
        assertGlide(linear(TARGET - 15.0, TARGET + 15.0, 0.80),
                MelodicDirection.ASCENDING, GlissandoType.THROUGH_TARGET,
                "çıkıcı yumuşak glisendo");
        assertGlide(linear(TARGET + 15.0, TARGET - 15.0, 0.80),
                MelodicDirection.DESCENDING, GlissandoType.THROUGH_TARGET,
                "inici yumuşak glisendo");

        assertGlide(linear(TARGET - 120.0, TARGET, 0.30),
                MelodicDirection.ASCENDING, GlissandoType.INTO_TARGET,
                "alttan hedefe giriş glisendosu");
        assertGlide(linear(TARGET + 120.0, TARGET, 0.30),
                MelodicDirection.DESCENDING, GlissandoType.INTO_TARGET,
                "üstten hedefe giriş glisendosu");
        assertGlide(linear(TARGET, TARGET + 120.0, 0.30),
                MelodicDirection.ASCENDING, GlissandoType.OUT_OF_TARGET,
                "hedeften yukarı çıkış glisendosu");
        assertGlide(linear(TARGET, TARGET - 120.0, 0.30),
                MelodicDirection.DESCENDING, GlissandoType.OUT_OF_TARGET,
                "hedeften aşağı çıkış glisendosu");

        assertGlide(glideWithVibrato(), MelodicDirection.ASCENDING,
                GlissandoType.THROUGH_TARGET, "hafif vibratolu çıkıcı glisendo");
        assertGlide(reverse(glideWithVibrato()), MelodicDirection.DESCENDING,
                GlissandoType.THROUGH_TARGET, "hafif vibratolu inici glisendo");
        check(count(octaveJump()) == 0,
                "çıkıcı tek oktav sıçraması glisendo sayılmamalı");
        check(count(reverse(octaveJump())) == 0,
                "inici tek oktav sıçraması glisendo sayılmamalı");
        check(count(stepChange()) == 0,
                "çıkıcı ani nota değişimi glisendo sayılmamalı");
        check(count(reverse(stepChange())) == 0,
                "inici ani nota değişimi glisendo sayılmamalı");
        check(count(constantPitch()) == 0,
                "sabit perde glisendo sayılmamalı");
        check(count(vibrato()) == 0,
                "vibrato glisendo sayılmamalı");
        check(count(slowVibrato()) == 0,
                "yavaş vibrato yarım dalgaları glisendo sayılmamalı");
        System.out.println("GLISSANDO_REGRESSION_OK asc=7 desc=7 into=2 out=2 through=10 "
                + "octaveJump=0 step=0 constant=0 vibrato=0");
    }

    private static int count(List<PitchFrame> frames) {
        return events(frames).size();
    }

    private static List<GlissandoEvent> events(List<PitchFrame> frames) {
        int sampleCount = Math.max(1, (int) Math.ceil((frames.size() * STEP + 0.2) * 44_100));
        AudioData audio = new AudioData("glissando-regression", null, 44_100f, new float[sampleCount]);
        AnalysisReport report = new DirectionalIntonationAnalyzer().analyze(audio, frames, config());
        return report.glissandi();
    }

    private static void assertGlide(List<PitchFrame> frames, MelodicDirection direction,
                                    GlissandoType type, String label) {
        List<GlissandoEvent> events = events(frames);
        check(events.size() == 1, label + " tam bir olay olarak bulunmalı; bulunan=" + events.size());
        GlissandoEvent event = events.get(0);
        check(event.direction() == direction,
                label + " yönü " + direction + " olmalı; bulunan=" + event.direction());
        String expectedDirectionLabel = switch (direction) {
            case ASCENDING -> "Düz (çıkıcı)";
            case DESCENDING -> "Ters (inici)";
            case LEVEL -> "Yatay/Karma";
        };
        check(event.directionTechniqueLabel().equals(expectedDirectionLabel),
                label + " kullanıcı etiketi tam olarak '" + expectedDirectionLabel
                        + "' olmalı; bulunan='" + event.directionTechniqueLabel() + "'");
        check(event.type() == type,
                label + " hedef ilişkisi " + type + " olmalı; bulunan=" + event.type());
    }

    private static List<PitchFrame> linear(double first, double last, double duration) {
        int count = (int) Math.round(duration / STEP) + 1;
        List<PitchFrame> frames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double cent = first + (last - first) * i / (count - 1.0);
            frames.add(frame(i, cent));
        }
        return frames;
    }

    private static List<PitchFrame> octaveJump() {
        List<PitchFrame> frames = new ArrayList<>();
        for (int i = 0; i < 10; i++) frames.add(frame(i, TARGET - 30.0));
        for (int i = 10; i < 22; i++) frames.add(frame(i, TARGET + 1170.0));
        return frames;
    }

    private static List<PitchFrame> stepChange() {
        List<PitchFrame> frames = new ArrayList<>();
        for (int i = 0; i < 8; i++) frames.add(frame(i, TARGET - 35.0));
        for (int i = 8; i < 20; i++) frames.add(frame(i, TARGET + 35.0));
        return frames;
    }

    private static List<PitchFrame> constantPitch() {
        List<PitchFrame> frames = new ArrayList<>();
        for (int i = 0; i < 40; i++) frames.add(frame(i, TARGET + 4.0));
        return frames;
    }

    private static List<PitchFrame> vibrato() {
        List<PitchFrame> frames = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            double cent = TARGET + 45.0 * Math.sin(2.0 * Math.PI * i * STEP / 0.16);
            frames.add(frame(i, cent));
        }
        return frames;
    }

    private static List<PitchFrame> slowVibrato() {
        List<PitchFrame> frames = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            double cent = TARGET + 24.0 * Math.sin(2.0 * Math.PI * i * STEP / 0.28);
            frames.add(frame(i, cent));
        }
        return frames;
    }

    private static List<PitchFrame> glideWithVibrato() {
        List<PitchFrame> frames = new ArrayList<>();
        int count = (int) Math.round(0.48 / STEP) + 1;
        for (int i = 0; i < count; i++) {
            double progress = i / (count - 1.0);
            double cent = TARGET - 70.0 + 140.0 * progress
                    + 5.0 * Math.sin(2.0 * Math.PI * i * STEP / 0.14);
            frames.add(frame(i, cent));
        }
        return frames;
    }

    private static List<PitchFrame> reverse(List<PitchFrame> source) {
        List<PitchFrame> frames = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            double cent = source.get(source.size() - 1 - i).absoluteCent();
            frames.add(frame(i, cent));
        }
        return frames;
    }

    private static PitchFrame frame(int index, double cent) {
        return new PitchFrame(index * STEP, PitchTracker.centToHertz(cent), cent, 0.96, 0.12);
    }

    private static AnalysisConfig config() {
        return new AnalysisConfig("Test", "Karar", "Hedef", TONIC_HZ, 0.0,
                1.25, 1.25, 1.0, 0.10, 0.35, 0.70, 0.28, 0.75, 0.55,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_SECONDS,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_SPAN_COMMA,
                AnalysisConfig.DEFAULT_MINIMUM_GLISSANDO_MONOTONICITY,
                AnalysisConfig.DEFAULT_MAXIMUM_GLISSANDO_FRAME_STEP_CENT,
                700.0, 1.5, 0.02, 32.0, false);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
