package com.makambox.studio.dsp;

import com.makambox.studio.analysis.TonicEstimator;
import com.makambox.studio.audio.AudioLoader;
import com.makambox.studio.model.AudioData;
import com.makambox.studio.model.PitchFrame;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Regression coverage for f/2 and f/3 pitch-tracker errors. */
public final class PitchTrackerSubharmonicRegressionTest {
    private static final float SAMPLE_RATE = 44_100f;

    public static void main(String[] args) throws Exception {
        harmonicFundamentalIsRetained();
        secondCommonPeriodAliasIsRejected();
        thirdCommonPeriodAliasIsRejected();
        legitimateLowRegisterIsRetained();
        shortHarmonicIslandIsCorrected();
        commonPeriodTransitionIsCorrected();
        octaveRelatedCommonPeriodIsCorrected();
        incompatibleShortLowNoteIsRetained();
        oneSidedLowOnsetIsRetained();
        sustainedRegisterChangeIsRetained();
        continuousGlissandoIsRetained();
        if (args.length > 0) verifyUssakTaksim(Path.of(args[0]));
        System.out.println("PITCH_TRACKER_SUBHARMONIC_OK");
    }

    private static void harmonicFundamentalIsRetained() {
        float[] frame = harmonicFrame(160.0, new double[]{1.0, 0.62, 0.33, 0.18, 0.10});
        YinPitchDetector.Result result = detector(frame.length).detect(frame);
        check(cents(result.hertz(), 160.0) < 25.0,
                "160 Hz harmonic frame was folded to the wrong register: " + result.hertz());
    }

    private static void legitimateLowRegisterIsRetained() {
        float[] frame = harmonicFrame(80.0, new double[]{0.42, 1.0, 0.48, 0.34, 0.21, 0.16});
        YinPitchDetector.Result result = detector(frame.length).detect(frame);
        check(cents(result.hertz(), 80.0) < 25.0,
                "A legitimate 80 Hz harmonic series was incorrectly doubled: " + result.hertz());
    }

    private static void secondCommonPeriodAliasIsRejected() {
        // 160 and 240 Hz share an 80 Hz mathematical period, but no 80 Hz
        // fundamental is present and the 160 Hz melody component dominates.
        float[] frame = mixedFrame(new double[]{160.0, 240.0}, new double[]{1.0, 0.42});
        YinPitchDetector.Result result = detector(frame.length).detect(frame);
        check(cents(result.hertz(), 160.0) < 30.0,
                "A spectral 80 Hz common period survived the f/2 guard: " + result.hertz());
    }

    private static void thirdCommonPeriodAliasIsRejected() {
        // Likewise 108 and 162 Hz share a 54 Hz period; the audible 162 Hz
        // component must not be reported in the sub-contra register.
        float[] frame = mixedFrame(new double[]{108.0, 162.0}, new double[]{0.48, 1.0});
        YinPitchDetector.Result result = detector(frame.length).detect(frame);
        check(cents(result.hertz(), 162.0) < 30.0,
                "A spectral 54 Hz common period survived the f/3 guard: " + result.hertz());
    }

    private static void verifyUssakTaksim(Path path) throws Exception {
        AudioData audio = new AudioLoader().load(path);
        List<PitchFrame> frames = new PitchTracker().track(audio, ignored -> { });
        long below90 = frames.stream().filter(PitchFrame::voiced).filter(frame -> frame.hertz() < 90.0).count();
        int longestBelow120 = 0;
        int currentBelow120 = 0;
        for (PitchFrame frame : frames) {
            if (frame.voiced() && frame.hertz() < 120.0) {
                currentBelow120++;
                longestBelow120 = Math.max(longestBelow120, currentBelow120);
            } else {
                currentBelow120 = 0;
            }
        }
        long largeJumps = 0;
        for (int index = 1; index < frames.size(); index++) {
            PitchFrame before = frames.get(index - 1);
            PitchFrame after = frames.get(index);
            if (before.voiced() && after.voiced()
                    && Math.abs(after.absoluteCent() - before.absoluteCent()) >= 900.0) largeJumps++;
        }
        TonicEstimator.Estimate tonic = new TonicEstimator().estimate(frames);
        printLowRuns(frames);
        check(below90 <= 30,
                "Too many residual sub-90 Hz frames in the verified Ussak recording: " + below90);
        check(largeJumps <= 40,
                "Too many residual >=900 C jumps in the verified Ussak recording: " + largeJumps);
        check(tonic.hertz() > 158.0 && tonic.hertz() < 164.0,
                "Ussak tonic moved outside the verified register: " + tonic.hertz());
        double[] verifiedSubharmonics = {3.50, 29.80, 133.90, 286.70, 307.84, 309.10, 309.90};
        for (double second : verifiedSubharmonics) {
            double hertz = valueNear(frames, second);
            check(hertz > 145.0 && hertz < 175.0,
                    "Verified tonic frame remains in a subharmonic register at " + second + " s: " + hertz);
        }
        double high333 = median(frames, 159.30, 159.90);
        double high381 = median(frames, 107.56, 107.90);
        double high571 = median(frames, 244.74, 244.94);
        verifyNoSubharmonic(frames, 2.68, 4.48);
        verifyNoSubharmonic(frames, 14.44, 14.60);
        verifyNoSubharmonic(frames, 31.54, 41.92);
        verifyNoSubharmonic(frames, 133.12, 134.10);
        verifyNoSubharmonic(frames, 188.36, 188.76);
        verifyNoSubharmonic(frames, 283.28, 293.08, 283.90, 283.92);
        check(high333 > 325.0 && high333 < 345.0,
                "Verified ~333 Hz high-register performance was lost: " + high333);
        check(high381 > 370.0 && high381 < 392.0,
                "Verified ~381 Hz high-register performance was lost: " + high381);
        check(high571 > 555.0 && high571 < 588.0,
                "Verified ~571 Hz high-register performance was lost: " + high571);
        System.out.printf(Locale.US,
                "REAL_USSAK_SUBHARMONIC_METRICS voiced=%d below90=%d longestBelow120=%d jumps900=%d tonic=%.6fHz "
                        + "f307=%.3fHz f309=%.3fHz high=%.1f/%.1f/%.1fHz%n",
                frames.stream().filter(PitchFrame::voiced).count(), below90, longestBelow120, largeJumps, tonic.hertz(),
                valueNear(frames, 307.84), valueNear(frames, 309.90), high333, high381, high571);
    }

    private static void printLowRuns(List<PitchFrame> frames) {
        int index = 0;
        while (index < frames.size()) {
            if (!frames.get(index).voiced() || frames.get(index).hertz() >= 90.0) {
                index++;
                continue;
            }
            int start = index;
            while (index + 1 < frames.size() && frames.get(index + 1).voiced()
                    && frames.get(index + 1).hertz() < 90.0) index++;
            int end = index;
            PitchFrame before = start > 0 ? frames.get(start - 1) : frames.get(start);
            PitchFrame after = end + 1 < frames.size() ? frames.get(end + 1) : frames.get(end);
            int left = start - 1;
            while (left >= 0 && start - left <= 25
                    && (!frames.get(left).voiced() || frames.get(left).hertz() < 90.0)) left--;
            int right = end + 1;
            while (right < frames.size() && right - end <= 25
                    && (!frames.get(right).voiced() || frames.get(right).hertz() < 90.0)) right++;
            double[] values = frames.subList(start, end + 1).stream()
                    .mapToDouble(PitchFrame::hertz).sorted().toArray();
            System.out.printf(Locale.US,
                    "LOW_RUN %.2f-%.2f n=%d med=%.3fHz context=%.3f/%.3fHz nearest=%s/%s confidence=%.3f..%.3f rms=%.5f..%.5f%n",
                    frames.get(start).timeSeconds(), frames.get(end).timeSeconds(), end - start + 1,
                    values[values.length / 2], before.hertz(), after.hertz(),
                    left >= 0 ? String.format(Locale.US, "%.3f@%.2f", frames.get(left).hertz(), frames.get(left).timeSeconds()) : "-",
                    right < frames.size() ? String.format(Locale.US, "%.3f@%.2f", frames.get(right).hertz(), frames.get(right).timeSeconds()) : "-",
                    frames.subList(start, end + 1).stream().mapToDouble(PitchFrame::confidence).min().orElse(0.0),
                    frames.subList(start, end + 1).stream().mapToDouble(PitchFrame::confidence).max().orElse(0.0),
                    frames.subList(start, end + 1).stream().mapToDouble(PitchFrame::rms).min().orElse(0.0),
                    frames.subList(start, end + 1).stream().mapToDouble(PitchFrame::rms).max().orElse(0.0));
            index++;
        }
    }

    private static void verifyNoSubharmonic(List<PitchFrame> frames, double start, double end,
                                            double... excludedWindow) {
        long count = frames.stream().filter(PitchFrame::voiced)
                .filter(frame -> frame.timeSeconds() >= start && frame.timeSeconds() <= end)
                .filter(frame -> excludedWindow.length != 2
                        || frame.timeSeconds() < excludedWindow[0] || frame.timeSeconds() > excludedWindow[1])
                .filter(frame -> frame.hertz() < 90.0).count();
        check(count == 0, String.format(Locale.US,
                "A verified common-period alias remains in %.2f-%.2f s", start, end));
    }

    private static void shortHarmonicIslandIsCorrected() {
        java.util.ArrayList<PitchFrame> frames = new java.util.ArrayList<>();
        for (int index = 0; index < 12; index++) {
            double hertz = index >= 5 && index <= 7 ? 80.0 : 160.0;
            frames.add(frame(index * 0.02, hertz));
        }
        List<PitchFrame> corrected = PitchTracker.suppressShortHarmonicAliases(frames);
        check(corrected.subList(5, 8).stream().allMatch(frame -> cents(frame.hertz(), 160.0) < 1.0),
                "A three-frame f/2 island was not repaired");
    }

    private static void sustainedRegisterChangeIsRetained() {
        java.util.ArrayList<PitchFrame> frames = new java.util.ArrayList<>();
        for (int index = 0; index < 40; index++) {
            double hertz = index >= 10 && index < 30 ? 80.0 : 160.0;
            frames.add(frame(index * 0.02, hertz));
        }
        List<PitchFrame> corrected = PitchTracker.suppressShortHarmonicAliases(frames);
        check(cents(corrected.get(20).hertz(), 80.0) < 1.0,
                "A sustained legitimate register change was folded upwards");
    }

    private static void commonPeriodTransitionIsCorrected() {
        java.util.ArrayList<PitchFrame> frames = new java.util.ArrayList<>();
        for (int index = 0; index < 5; index++) frames.add(frame(index * 0.02, 160.0));
        for (int index = 5; index < 14; index++) frames.add(frame(index * 0.02, 80.0));
        for (int index = 14; index < 19; index++) frames.add(frame(index * 0.02, 240.0));
        List<PitchFrame> corrected = PitchTracker.suppressShortHarmonicAliases(frames);
        check(corrected.subList(5, 14).stream().allMatch(value -> value.hertz() >= 150.0),
                "A short 80 Hz common-period transition between 160/240 Hz was not repaired");
        check(cents(corrected.get(5).hertz(), 160.0) < 1.0
                        && cents(corrected.get(13).hertz(), 240.0) < 1.0,
                "The common-period repair did not follow its two audible harmonic anchors");
    }

    private static void octaveRelatedCommonPeriodIsCorrected() {
        java.util.ArrayList<PitchFrame> frames = new java.util.ArrayList<>();
        for (int index = 0; index < 5; index++) frames.add(frame(index * 0.02, 107.5));
        frames.add(frame(0.10, 53.75));
        for (int index = 6; index < 11; index++) frames.add(frame(index * 0.02, 215.0));
        List<PitchFrame> corrected = PitchTracker.suppressShortHarmonicAliases(frames);
        check(cents(corrected.get(5).hertz(), 107.5) < 1.0,
                "A 54 Hz common period between octave-related 107/215 Hz context was not repaired");
    }

    private static void incompatibleShortLowNoteIsRetained() {
        java.util.ArrayList<PitchFrame> frames = new java.util.ArrayList<>();
        for (int index = 0; index < 5; index++) frames.add(frame(index * 0.02, 145.0));
        for (int index = 5; index < 10; index++) frames.add(frame(index * 0.02, 80.0));
        for (int index = 10; index < 15; index++) frames.add(frame(index * 0.02, 190.0));
        List<PitchFrame> corrected = PitchTracker.suppressShortHarmonicAliases(frames);
        check(corrected.subList(5, 10).stream().allMatch(value -> cents(value.hertz(), 80.0) < 1.0),
                "A short low note without coherent 2f/3f context was folded upwards");
    }

    private static void oneSidedLowOnsetIsRetained() {
        java.util.ArrayList<PitchFrame> frames = new java.util.ArrayList<>();
        for (int index = 0; index < 5; index++) frames.add(frame(index * 0.02, 80.0));
        for (int index = 5; index < 10; index++) frames.add(frame(index * 0.02, 160.0));
        List<PitchFrame> corrected = PitchTracker.suppressShortHarmonicAliases(frames);
        check(corrected.subList(0, 5).stream().allMatch(value -> cents(value.hertz(), 80.0) < 1.0),
                "A one-sided low-register onset was folded without two-sided evidence");
    }

    private static void continuousGlissandoIsRetained() {
        java.util.ArrayList<PitchFrame> frames = new java.util.ArrayList<>();
        for (int index = 0; index < 24; index++) {
            double hertz = 160.0 * Math.pow(2.0, index / 23.0);
            frames.add(frame(index * 0.02, hertz));
        }
        List<PitchFrame> corrected = PitchTracker.suppressShortHarmonicAliases(frames);
        for (int index = 0; index < frames.size(); index++) {
            check(cents(corrected.get(index).hertz(), frames.get(index).hertz()) < 0.01,
                    "Continuous glissando was mistaken for an octave alias at frame " + index);
        }
    }

    private static PitchFrame frame(double second, double hertz) {
        return new PitchFrame(second, hertz, PitchTracker.hertzToCent(hertz), 0.95, 0.10);
    }

    private static double median(List<PitchFrame> frames, double start, double end) {
        double[] values = frames.stream().filter(PitchFrame::voiced)
                .filter(frame -> frame.timeSeconds() >= start && frame.timeSeconds() <= end)
                .mapToDouble(PitchFrame::hertz).sorted().toArray();
        check(values.length > 0, "No voiced frames in verified high-register window");
        int middle = values.length / 2;
        return values.length % 2 == 0 ? (values[middle - 1] + values[middle]) / 2.0 : values[middle];
    }

    private static double valueNear(List<PitchFrame> frames, double second) {
        return frames.stream().filter(PitchFrame::voiced)
                .min((left, right) -> Double.compare(Math.abs(left.timeSeconds() - second),
                        Math.abs(right.timeSeconds() - second)))
                .map(PitchFrame::hertz).orElse(0.0);
    }

    private static YinPitchDetector detector(int frameSize) {
        return new YinPitchDetector(SAMPLE_RATE, frameSize, 0.20, 45.0, 1600.0);
    }

    private static float[] harmonicFrame(double fundamental, double[] amplitudes) {
        int length = (int) Math.round(SAMPLE_RATE * 0.040);
        float[] result = new float[length];
        for (int index = 0; index < length; index++) {
            double second = index / SAMPLE_RATE;
            double sample = 0.0;
            for (int harmonic = 0; harmonic < amplitudes.length; harmonic++) {
                sample += amplitudes[harmonic]
                        * Math.sin(2.0 * Math.PI * fundamental * (harmonic + 1) * second + harmonic * 0.17);
            }
            result[index] = (float) (sample * 0.20);
        }
        return result;
    }

    private static float[] mixedFrame(double[] frequencies, double[] amplitudes) {
        int length = (int) Math.round(SAMPLE_RATE * 0.040);
        float[] result = new float[length];
        for (int index = 0; index < length; index++) {
            double second = index / SAMPLE_RATE;
            double sample = 0.0;
            for (int component = 0; component < frequencies.length; component++) {
                sample += amplitudes[component]
                        * Math.sin(2.0 * Math.PI * frequencies[component] * second + component * 0.23);
            }
            result[index] = (float) (sample * 0.20);
        }
        return result;
    }

    private static double cents(double first, double second) {
        return Math.abs(1200.0 * Math.log(first / second) / Math.log(2.0));
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
