package com.makambox.studio.study;

import com.makambox.studio.model.AnalysisConfig;
import com.makambox.studio.model.PitchEvent;

import java.util.Locale;
import java.util.Objects;

/**
 * Naming policy for an event whose observed F0 lies inside an open pitch interval.
 *
 * <p>The canonical event engine always retains the nearest 53-Hc theory centre so
 * that a signed C/Hc distance can be calculated.  That nearest centre is not,
 * however, automatically a defensible pitch name.  An event is displayed with a
 * pitch name only when its robust centre is within the configured similarity
 * tolerance (normally 1 Hc).  More distant events remain in the analysis as
 * {@code Adlandırılamayan}; their nearest pitch and signed distance are still
 * reported.  This distinction prevents an in-between realization from silently
 * becoming a boundary pitch merely because the midpoint classifier must choose
 * some nearest centre.</p>
 */
public final class IntervalPitchNaming {
    public static final String UNNAMED_LABEL = "Adlandırılamayan";
    private static final double EPSILON_CENT = 1.0e-7;

    private IntervalPitchNaming() { }

    public static double toleranceCent(AnalysisConfig config) {
        Objects.requireNonNull(config, "config");
        double toleranceHc = config.similarityToleranceComma();
        if (!Double.isFinite(toleranceHc) || toleranceHc <= 0.0) toleranceHc = 1.0;
        return toleranceHc * AnalysisConfig.COMMA_CENT;
    }

    public static boolean isCanonicallyNamed(double deviationCent, AnalysisConfig config) {
        return Double.isFinite(deviationCent)
                && Math.abs(deviationCent) <= toleranceCent(config) + EPSILON_CENT;
    }

    public static boolean isCanonicallyNamed(StudyPitchEvent event, AnalysisConfig config) {
        Objects.requireNonNull(event, "event");
        return isCanonicallyNamed(event.deviationCent(), config);
    }

    public static boolean isCanonicallyNamed(PitchEvent event, AnalysisConfig config) {
        Objects.requireNonNull(event, "event");
        return isCanonicallyNamed(event.occurrence().deviationCent(), config);
    }

    public static String displayName(StudyPitchEvent event, AnalysisConfig config) {
        Objects.requireNonNull(event, "event");
        return displayName(event.noteName(), event.deviationCent(), config);
    }

    public static String displayName(PitchEvent event, AnalysisConfig config) {
        Objects.requireNonNull(event, "event");
        return displayName(event.noteName(), event.occurrence().deviationCent(), config);
    }

    public static String displayName(String nearestPitchName, double deviationCent,
                                     AnalysisConfig config) {
        Objects.requireNonNull(nearestPitchName, "nearestPitchName");
        if (isCanonicallyNamed(deviationCent, config)) return nearestPitchName;
        return UNNAMED_LABEL + " (en yakın: " + nearestPitchName + ")";
    }

    public static String nearestDistanceLabel(String nearestPitchName, double deviationCent) {
        Objects.requireNonNull(nearestPitchName, "nearestPitchName");
        return String.format(Locale.US, "En yakın %s: %+.2f C / %+.3f Hc",
                nearestPitchName, deviationCent, deviationCent / AnalysisConfig.COMMA_CENT);
    }

    /** Returns a UI-only copy; the machine-readable nearest-theory key is retained. */
    public static PitchEvent relabelForDisplay(PitchEvent event, AnalysisConfig config) {
        String displayName = displayName(event, config);
        if (displayName.equals(event.noteName())) return event;
        return new PitchEvent(event.noteKey(), displayName,
                event.theoreticalRelativeCent(), event.occurrence());
    }
}
