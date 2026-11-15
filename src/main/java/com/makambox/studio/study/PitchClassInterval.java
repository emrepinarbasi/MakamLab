package com.makambox.studio.study;

import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.PitchEvent;
import com.makambox.studio.theory.TheoryCatalog;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Register-independent helpers for an open interval selected by pitch class.
 *
 * <p>A confirmed tonic is a physical frequency, but a makam pitch and a
 * selected interval may be performed in more than one octave.  For example,
 * a Rast--Dik Kürdî study anchored at a low Rast must also retain the same
 * pitch classes performed one octave above.  These helpers align both the
 * observed F0 and its nearest theory centre to the register of the selected
 * interval without duplicating an event.</p>
 */
public final class PitchClassInterval {
    public static final double OCTAVE_CENT = 1200.0;
    private static final double EPSILON_CENT = 1.0e-7;
    private static final double MINIMUM_AUDIT_DURATION_SECONDS = 0.16;
    private static final double MINIMUM_REGISTER_CONFIDENCE = 0.65;
    private static final double MAXIMUM_REGISTER_SPREAD_HC = 0.55;

    private PitchClassInterval() { }

    public static double alignToRegister(double relativeCent,
                                         double lowerCent, double upperCent) {
        if (!Double.isFinite(relativeCent)
                || !Double.isFinite(lowerCent) || !Double.isFinite(upperCent)
                || !(upperCent > lowerCent)
                || upperCent - lowerCent >= OCTAVE_CENT) {
            throw new IllegalArgumentException("A finite sub-octave interval is required");
        }
        double center = (lowerCent + upperCent) / 2.0;
        return relativeCent
                + Math.rint((center - relativeCent) / OCTAVE_CENT) * OCTAVE_CENT;
    }

    public static boolean contains(PitchEvent event, double tonicAbsoluteCent,
                                   double lowerCent, double upperCent,
                                   AnalysisConfig config) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(config, "config");
        double observed = alignToRegister(
                event.occurrence().observedCent() - tonicAbsoluteCent,
                lowerCent, upperCent);
        if (!(observed > lowerCent + EPSILON_CENT
                && observed < upperCent - EPSILON_CENT)) return false;

        // An event farther than the naming tolerance from the nearest centre
        // remains an unnamed, measurable in-between sound.  A confidently
        // named octave-equivalent boundary pitch is excluded just like the
        // boundary in the tonic's physical register.
        if (!IntervalPitchNaming.isCanonicallyNamed(event, config)) return true;
        double theoretical = alignToRegister(event.theoreticalRelativeCent(),
                lowerCent, upperCent);
        return theoretical > lowerCent + EPSILON_CENT
                && theoretical < upperCent - EPSILON_CENT;
    }

    /**
     * Returns every physical register that carries a coherent version of the
     * selected pitch-class interval.
     *
     * <p>A recording can legitimately alternate between, for example, a low
     * instrumental line and a choral line one octave higher.  Selecting only
     * the nearest register silently discarded the latter even though both
     * lines contained the requested makam pitch classes.  Conversely, folding
     * every isolated octave estimate would reintroduce brief octave-tracking
     * errors.  This method therefore applies the existing auditable register
     * evidence threshold independently to every register represented in the
     * canonical event stream.  If no register reaches the threshold, the
     * strongest one is retained as an explicit audit fallback.</p>
     */
    public static List<RegisterSelection> selectViableRegisters(
            List<PitchEvent> events, double tonicAbsoluteCent,
            double lowerCent, double upperCent, AnalysisConfig config) {
        Objects.requireNonNull(events, "events");
        validateInterval(lowerCent, upperCent);
        Set<Integer> offsets = new LinkedHashSet<>();
        for (PitchEvent event : events) {
            int offset = registerOffset(event, tonicAbsoluteCent, lowerCent, upperCent);
            if (containsInRegister(event, tonicAbsoluteCent, lowerCent, upperCent,
                    offset, config)) {
                offsets.add(offset);
            }
        }
        if (offsets.isEmpty()) return List.of();

        List<RegisterSelection> candidates = offsets.stream()
                .map(offset -> evidence(events, tonicAbsoluteCent,
                        lowerCent, upperCent, offset, config))
                .toList();
        List<RegisterSelection> viable = candidates.stream()
                .filter(RegisterSelection::viable)
                .sorted(Comparator.comparingInt(RegisterSelection::octaveOffset))
                .toList();
        if (!viable.isEmpty()) return viable;
        return candidates.stream().max(FALLBACK_EVIDENCE_COMPARATOR)
                .map(List::of).orElseGet(List::of);
    }

    /** Returns the unique physical-octave offset whose centre is nearest the event. */
    public static int registerOffset(PitchEvent event, double tonicAbsoluteCent,
                                     double lowerCent, double upperCent) {
        Objects.requireNonNull(event, "event");
        validateInterval(lowerCent, upperCent);
        double relative = event.occurrence().observedCent() - tonicAbsoluteCent;
        double aligned = alignToRegister(relative, lowerCent, upperCent);
        return (int) Math.rint((relative - aligned) / OCTAVE_CENT);
    }

    /**
     * Chooses one physical register for an open interval.  The tonic register is
     * always tried first.  If it has too little evidence, only its immediate
     * neighbours are considered; more distant octaves are deliberately excluded.
     */
    public static RegisterSelection selectNearestViableRegister(
            List<PitchEvent> events, double tonicAbsoluteCent,
            double lowerCent, double upperCent, AnalysisConfig config) {
        Objects.requireNonNull(events, "events");
        RegisterSelection base = evidence(events, tonicAbsoluteCent,
                lowerCent, upperCent, 0, config);
        if (base.viable()) return base;

        RegisterSelection upper = evidence(events, tonicAbsoluteCent,
                lowerCent, upperCent, 1, config);
        RegisterSelection lower = evidence(events, tonicAbsoluteCent,
                lowerCent, upperCent, -1, config);
        List<RegisterSelection> viable = List.of(upper, lower).stream()
                .filter(RegisterSelection::viable).toList();
        if (!viable.isEmpty()) return viable.stream().max(EVIDENCE_COMPARATOR).orElseThrow();

        // No register reached the analysis threshold.  Keep the strongest nearby
        // evidence for transparent audit output, but never jump to a second octave.
        return List.of(base, upper, lower).stream()
                .max(EVIDENCE_COMPARATOR).orElse(base);
    }

    public static boolean containsInRegister(PitchEvent event,
                                             double tonicAbsoluteCent,
                                             double lowerCent, double upperCent,
                                             int octaveOffset,
                                             AnalysisConfig config) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(config, "config");
        validateInterval(lowerCent, upperCent);
        double shift = octaveOffset * OCTAVE_CENT;
        double physicalLower = lowerCent + shift;
        double physicalUpper = upperCent + shift;
        double observed = event.occurrence().observedCent() - tonicAbsoluteCent;
        if (!(observed > physicalLower + EPSILON_CENT
                && observed < physicalUpper - EPSILON_CENT)) return false;

        if (!IntervalPitchNaming.isCanonicallyNamed(event, config)) return true;
        double theoretical = event.theoreticalRelativeCent();
        return theoretical > physicalLower + EPSILON_CENT
                && theoretical < physicalUpper - EPSILON_CENT;
    }

    /**
     * Returns a report/UI copy in the reference register.  Its observed physical
     * frequency remains unchanged in the occurrence; only theory identity and
     * relative C/Hc geometry are brought back to the selected base interval.
     */
    public static PitchEvent normalizeEvent(PitchEvent event, int octaveOffset,
                                            TheoryCatalog theory,
                                            TheoryCatalog.Makam makam) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(theory, "theory");
        Objects.requireNonNull(makam, "makam");
        if (octaveOffset == 0) return event;
        double normalized = event.theoreticalRelativeCent()
                - octaveOffset * OCTAVE_CENT;
        TheoryCatalog.Note note = theory.notesForPitchAnalysis(makam).stream()
                .min(Comparator.comparingDouble(value -> Math.abs(
                        theory.relativeCent(makam, value) - normalized)))
                .filter(value -> Math.abs(theory.relativeCent(makam, value)
                        - normalized) <= 0.05)
                .orElse(null);
        if (note == null) {
            return new PitchEvent(event.noteKey(), event.noteName(),
                    normalized, event.occurrence());
        }
        return new PitchEvent(note.key(), note.toString(),
                theory.relativeCent(makam, note), event.occurrence());
    }

    public static boolean samePitchClass(double firstRelativeCent,
                                         double secondRelativeCent) {
        if (!Double.isFinite(firstRelativeCent) || !Double.isFinite(secondRelativeCent)) {
            return false;
        }
        double difference = firstRelativeCent - secondRelativeCent;
        difference -= Math.rint(difference / OCTAVE_CENT) * OCTAVE_CENT;
        return Math.abs(difference) <= EPSILON_CENT;
    }

    private static RegisterSelection evidence(List<PitchEvent> events,
                                              double tonicAbsoluteCent,
                                              double lowerCent, double upperCent,
                                              int octaveOffset,
                                              AnalysisConfig config) {
        List<PitchEvent> contacts = events.stream()
                .filter(value -> containsInRegister(value, tonicAbsoluteCent,
                        lowerCent, upperCent, octaveOffset, config))
                .toList();
        int measurableCount = (int) contacts.stream()
                .filter(IntervalEventTier::statisticallyMeasurable).count();
        double contactDuration = contacts.stream()
                .mapToDouble(value -> value.occurrence().durationSeconds()).sum();
        double measurableDuration = contacts.stream()
                .filter(IntervalEventTier::statisticallyMeasurable)
                .mapToDouble(value -> value.occurrence().durationSeconds()).sum();
        List<PitchEvent> reliable = contacts.stream()
                .filter(value -> value.occurrence().confidence() + 1.0e-9
                        >= MINIMUM_REGISTER_CONFIDENCE)
                .filter(value -> value.occurrence().stabilityCent()
                        <= MAXIMUM_REGISTER_SPREAD_HC * AnalysisConfig.COMMA_CENT + 1.0e-9)
                .toList();
        int reliableMeasurableCount = (int) reliable.stream()
                // A single 60--99 ms conditional event is statistically usable
                // inside an already coherent register, but must not activate a
                // new physical register on its own.
                .filter(value -> IntervalEventTier.classify(value)
                        == IntervalEventTier.Tier.STRONG_MEASURABLE)
                .count();
        double reliableDuration = reliable.stream()
                .mapToDouble(value -> value.occurrence().durationSeconds()).sum();
        boolean viable = reliableMeasurableCount >= 1
                || (reliable.size() >= 3
                && reliableDuration + 1.0e-9 >= MINIMUM_AUDIT_DURATION_SECONDS);
        return new RegisterSelection(octaveOffset, contacts.size(), measurableCount,
                contactDuration, measurableDuration, viable);
    }

    private static void validateInterval(double lowerCent, double upperCent) {
        if (!Double.isFinite(lowerCent) || !Double.isFinite(upperCent)
                || !(upperCent > lowerCent)
                || upperCent - lowerCent >= OCTAVE_CENT) {
            throw new IllegalArgumentException("A finite sub-octave interval is required");
        }
    }

    private static final Comparator<RegisterSelection> EVIDENCE_COMPARATOR =
            Comparator.comparingInt(RegisterSelection::measurableCount)
                    .thenComparingDouble(RegisterSelection::measurableDurationSeconds)
                    .thenComparingDouble(RegisterSelection::contactDurationSeconds)
                    .thenComparingInt(RegisterSelection::contactCount)
                    // At equal evidence prefer the upper neighbour over the lower.
                    .thenComparingInt(value -> value.octaveOffset() == 1 ? 1
                            : value.octaveOffset() == 0 ? 0 : -1);

    private static final Comparator<RegisterSelection> FALLBACK_EVIDENCE_COMPARATOR =
            EVIDENCE_COMPARATOR.thenComparingInt(value -> -Math.abs(value.octaveOffset()));

    public record RegisterSelection(int octaveOffset, int contactCount,
                                    int measurableCount,
                                    double contactDurationSeconds,
                                    double measurableDurationSeconds,
                                    boolean viable) {
        public double physicalLowerCent(double lowerCent) {
            return lowerCent + octaveOffset * OCTAVE_CENT;
        }

        public double physicalUpperCent(double upperCent) {
            return upperCent + octaveOffset * OCTAVE_CENT;
        }
    }
}
