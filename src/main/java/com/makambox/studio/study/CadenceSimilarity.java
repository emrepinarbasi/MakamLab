package com.makambox.studio.study;

import com.makambox.studio.legacy.TuningSystemCatalog;
import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.AudioData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Compares every profile against the same descending, tonic-anchored F0 paths. */
public final class CadenceSimilarity {
    private static final double HC = AnalysisConfig.COMMA_CENT;
    private static final double MAX_GAP = 0.80;
    private static final List<String> PATTERNS = List.of(
            "4→3→2→1", "4→3→1", "4→2→1", "4→1", "3→2→1", "3→1", "2→1");

    private CadenceSimilarity() { }

    public record Variant(String path, int candidates, int confirmed, int phraseFinals) { }
    public record Score(String profile, double percent, double meanAbsoluteHc) { }
    public record Result(List<Variant> variants, List<Score> scores, int coveredDegrees) {
        public Result {
            variants = List.copyOf(variants);
            scores = List.copyOf(scores);
        }

        public int confirmed() {
            return variants.stream().mapToInt(Variant::confirmed).sum();
        }
    }

    private record Note(int degree, int octave, double hc, StudyPitchEvent event) { }
    private record Observation(int degree, double hc, double weight) { }

    public static Result analyze(AudioData audio, List<StudyPitchEvent> events, double tonicHz,
                                 String selectedMakam, MakamProfileComparison comparison) {
        var systems = new TuningSystemCatalog().loadBuiltIns();
        var system = systems.get(comparison.referenceSystem());
        // Detect degrees in a fixed 53-Hc domain, independent of the profiles being scored.
        double[] centers = systems.get(MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM)
                .intervalsFor(selectedMakam);
        if (centers == null || centers.length < 4) {
            return new Result(List.of(), List.of(), 0);
        }

        Map<String, Integer> candidates = new TreeMap<>();
        Map<String, Integer> confirmed = new TreeMap<>();
        Map<String, Integer> finals = new TreeMap<>();
        List<Observation> observations = new ArrayList<>();
        List<List<Note>> run = new ArrayList<>();
        List<StudyPitchEvent> ordered = events.stream()
                .sorted(Comparator.comparingDouble(StudyPitchEvent::startSeconds)
                        .thenComparingDouble(StudyPitchEvent::endSeconds)).toList();
        for (StudyPitchEvent e : ordered) {
            if (!MakamProfileComparator.reliableDirectionalFirstFourEvidence(
                    e.durationSeconds(), e.confidence(), e.stabilityCent())) {
                if (e.durationSeconds() >= .08 - 1e-9 || e.phraseFinal()) run.clear();
                continue;
            }
            Note next = classify(e, centers);
            if (next == null) {
                run.clear();
                continue;
            }
            if (!run.isEmpty()) {
                List<Note> lastGroup = run.getLast();
                Note previous = lastGroup.getLast();
                if (next.octave() != previous.octave()
                        || e.startSeconds() - previous.event().endSeconds() > MAX_GAP + 1e-9
                        || next.degree() > previous.degree()) {
                    run.clear();
                } else if (next.degree() == previous.degree()) {
                    lastGroup.add(next);
                    if (e.phraseFinal()) run.clear();
                    continue;
                }
            }
            if (!run.isEmpty() || next.degree() > 1) {
                run.add(new ArrayList<>(List.of(next)));
            }
            if (next.degree() == 1 && run.size() > 1) {
                String path = String.join("→", run.stream()
                        .map(group -> Integer.toString(group.getFirst().degree())).toList());
                candidates.merge(path, 1, Integer::sum);
                if (waveformSupports(audio, tonicHz, run)) {
                    confirmed.merge(path, 1, Integer::sum);
                    if (e.phraseFinal()) finals.merge(path, 1, Integer::sum);
                    for (List<Note> group : run.subList(0, run.size() - 1)) {
                        double weight = group.stream().mapToDouble(CadenceSimilarity::weight).sum();
                        for (Note note : group) {
                            observations.add(new Observation(note.degree(), note.hc(), weight(note) / weight));
                        }
                    }
                }
            }
            if (next.degree() == 1 || e.phraseFinal()) run.clear();
        }

        List<Variant> variants = PATTERNS.stream().map(path -> new Variant(path,
                candidates.getOrDefault(path, 0), confirmed.getOrDefault(path, 0),
                finals.getOrDefault(path, 0))).toList();
        int coverage = (int) observations.stream().map(Observation::degree).distinct().count();
        if (coverage == 0) return new Result(variants, List.of(), 0);

        double total = observations.stream().mapToDouble(Observation::weight).sum();
        List<Score> scores = new ArrayList<>();
        for (var reference : FirstFourProfileReferenceCatalog.references(
                system, comparison.firstFourProfileSelection())) {
            double similarity = 0;
            double error = 0;
            for (Observation observed : observations) {
                double distance = Math.abs(observed.hc() - reference.hcPositions().get(observed.degree() - 1));
                similarity += observed.weight() * Math.exp(-distance);
                error += observed.weight() * distance;
            }
            scores.add(new Score(reference.displayName(),
                    100 * similarity / total * (.55 + .45 * coverage / 3), error / total));
        }
        scores.sort(Comparator.comparingDouble(Score::percent).reversed().thenComparing(Score::profile));
        return new Result(variants, scores, coverage);
    }

    private static Note classify(StudyPitchEvent event, double[] centers) {
        double relative = event.observedRelativeCent();
        int octave = (int) Math.floor((relative + 600) / 1200);
        double cent = relative - 1200 * octave;
        int nearest = 0;
        for (int i = 1; i < 4; i++) {
            if (Math.abs(cent - centers[i]) < Math.abs(cent - centers[nearest])) nearest = i;
        }
        return Math.abs(cent - centers[nearest]) <= HC + 1e-7
                ? new Note(nearest + 1, octave, cent / HC, event) : null;
    }

    private static double weight(Note note) {
        double duration = note.event().durationSeconds();
        return duration * Math.min(1, duration / .08) * note.event().confidence();
    }

    private static boolean waveformSupports(AudioData audio, double tonicHz, List<List<Note>> run) {
        for (List<Note> group : run) {
            StudyPitchEvent event = group.stream()
                    .max(Comparator.comparingDouble(value -> value.event().durationSeconds()))
                    .orElseThrow().event();
            double expectedHz = tonicHz * Math.pow(2, event.observedRelativeCent() / 1200);
            if (!supportsPeriod(audio, (event.startSeconds() + event.endSeconds()) / 2, expectedHz)) {
                return false;
            }
        }
        return true;
    }

    private static boolean supportsPeriod(AudioData audio, double time, double expectedHz) {
        double rate = audio.sampleRate();
        int start = Math.max(0, (int) ((time - .025) * rate));
        int stop = Math.min(audio.samples().length, start + (int) (.055 * rate));
        if (stop <= start) return false;
        double[] samples = new double[(stop - start + 2) / 3];
        double mean = 0;
        for (int i = 0; i < samples.length; i++) {
            samples[i] = audio.samples()[start + i * 3];
            mean += samples[i];
        }
        mean /= samples.length;
        for (int i = 0; i < samples.length; i++) samples[i] -= mean;
        double expectedLag = rate / (3 * expectedHz);
        double ratio = Math.pow(2, 90.0 / 1200);
        int low = Math.max(2, (int) Math.floor(expectedLag / ratio));
        int high = Math.min(samples.length - 3, (int) Math.ceil(expectedLag * ratio));
        if (low > high) return false;
        double[] correlations = new double[high - low + 3];
        int best = 1;
        for (int i = 0; i < correlations.length; i++) {
            int lag = low - 1 + i;
            double cross = 0;
            double left = 0;
            double right = 0;
            for (int j = 0; j + lag < samples.length; j++) {
                double a = samples[j];
                double b = samples[j + lag];
                cross += a * b;
                left += a * a;
                right += b * b;
            }
            correlations[i] = left * right > 0 ? cross / Math.sqrt(left * right) : 0;
            if (i > 0 && i < correlations.length - 1 && correlations[i] > correlations[best]) best = i;
        }
        double y = correlations[best];
        double a = correlations[best - 1];
        double b = correlations[best + 1];
        if (y < .5 || y <= a || y <= b) return false;
        double lag = low - 1 + best + .5 * (a - b) / (a - 2 * y + b);
        return Math.abs(1200 * Math.log(expectedLag / lag) / Math.log(2)) <= HC;
    }
}
