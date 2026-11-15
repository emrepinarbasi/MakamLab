package com.makambox.studio.study;

import com.makambox.studio.legacy.TuningSystemCatalog;
import com.makambox.studio.model.AnalysisConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The academically delimited first-four-pitch reference set used by MakamLab.
 *
 * <p>The labels and their order are product requirements, while every interval
 * position is read from the bundled MakamBox tuning-system resource.  No C/Hc
 * position is duplicated as a user-entered or hard-coded numeric table.</p>
 */
public final class FirstFourProfileReferenceCatalog {
    private static final int CORE_PROFILE_COUNT = 5;
    private static final List<Specification> SPECIFICATIONS = List.of(
            // The bundled AEU catalogue calls this complete scale Mâhur; its
            // first four degrees are the Çargâh çeşni reference.
            new Specification("Çargâh", "Mahur"),
            new Specification("Kürdî", "Kurdi"),
            new Specification("Rast", "Rast"),
            new Specification("Uşşak", "Ussak"),
            new Specification("Hicaz", "Hicaz"),
            new Specification("Sabâ", "Saba"),
            new Specification("Segâh", "Segah"),
            new Specification("Müstear", "Mustear"),
            new Specification("Hüzzam", "Huzzam"),
            new Specification("Nikriz", "Nikriz"),
            new Specification("Pençgâh", "Pencgah"),
            new Specification("Ferahnâk", "Ferahnak"),
            // The packaged MakamBox resource names the source profile
            // Nişabürek; its first-four structure supplies Nişabur here.
            new Specification("Nişabur", "Nisaburek")
    );

    private FirstFourProfileReferenceCatalog() { }

    public static List<ReferenceProfile> references(TuningSystemCatalog.SystemData system) {
        Objects.requireNonNull(system, "system");
        List<ReferenceProfile> result = new ArrayList<>(SPECIFICATIONS.size());
        for (int index = 0; index < SPECIFICATIONS.size(); index++) {
            Specification specification = SPECIFICATIONS.get(index);
            double[] completeProfile = system.intervalsFor(specification.sourceProfile());
            if (completeProfile == null || completeProfile.length < 4) {
                throw new IllegalStateException("Eksik ilk-dört-ses kuram profili: "
                        + specification.sourceProfile() + " / " + system.name());
            }
            double tonic = completeProfile[0];
            List<Double> cent = new ArrayList<>(4);
            List<Double> hc = new ArrayList<>(4);
            double previous = Double.NEGATIVE_INFINITY;
            for (int degree = 0; degree < 4; degree++) {
                double relativeCent = completeProfile[degree] - tonic;
                if (!Double.isFinite(relativeCent) || relativeCent < -1.0e-7
                        || relativeCent <= previous && degree > 0) {
                    throw new IllegalStateException("Geçersiz ilk-dört-ses sırası: "
                            + specification.sourceProfile() + " / " + system.name());
                }
                if (Math.abs(relativeCent) < 1.0e-7) relativeCent = 0.0;
                cent.add(relativeCent);
                hc.add(relativeCent / AnalysisConfig.COMMA_CENT);
                previous = relativeCent;
            }
            result.add(new ReferenceProfile(index, specification.displayName(),
                    specification.sourceProfile(), List.copyOf(cent), List.copyOf(hc)));
        }
        return List.copyOf(result);
    }

    /**
     * Returns the immutable catalogue subset selected for one comparison.
     * The complete thirteen-profile catalogue remains available through
     * {@link #references(TuningSystemCatalog.SystemData)}.
     */
    public static List<ReferenceProfile> references(TuningSystemCatalog.SystemData system,
                                                    FirstFourProfileSelection selection) {
        Objects.requireNonNull(selection, "selection");
        return references(system).stream()
                .filter(value -> selection.includes(value.displayName()))
                .toList();
    }

    public static List<String> displayNames() {
        return SPECIFICATIONS.stream().map(Specification::displayName).toList();
    }

    public static List<String> coreDisplayNames() {
        return displayNames().subList(0, CORE_PROFILE_COUNT);
    }

    public static List<String> optionalDisplayNames() {
        return displayNames().subList(CORE_PROFILE_COUNT, SPECIFICATIONS.size());
    }

    /**
     * Upper degree of the five primary four-pitch profiles.
     *
     * <p>The primary comparison is deliberately delimited to the common
     * tonic-to-fourth domain.  Optional rows may be displayed, but they must
     * never enlarge the observation set used by an already visible primary
     * profile.</p>
     */
    public static double coreFourthDegreeCent(TuningSystemCatalog.SystemData system) {
        return references(system).stream().limit(CORE_PROFILE_COUNT)
                .mapToDouble(value -> value.centPositions().get(3))
                .max().orElseThrow(() -> new IllegalStateException(
                        "Temel ilk-dört-ses kuram kataloğu boş olamaz"));
    }

    /** Resolves {@link #coreFourthDegreeCent(TuningSystemCatalog.SystemData)} by system name. */
    public static double coreFourthDegreeCent(String referenceSystem) {
        Map<String, TuningSystemCatalog.SystemData> systems =
                new TuningSystemCatalog().loadBuiltIns();
        TuningSystemCatalog.SystemData system = Objects.requireNonNull(
                systems.get(referenceSystem), "Unknown reference system: " + referenceSystem);
        return coreFourthDegreeCent(system);
    }

    /**
     * Upper boundary of the common first-four evidence domain.
     *
     * <p>The boundary is deliberately derived from the complete immutable
     * catalogue rather than the profiles currently selected for display.  A
     * user who enables an optional row therefore changes only the rows shown,
     * never the evidence or the score of an already visible core row.</p>
     */
    public static double maximumFourthDegreeCent(TuningSystemCatalog.SystemData system) {
        return references(system).stream()
                .mapToDouble(value -> value.centPositions().get(3))
                .max().orElseThrow(() -> new IllegalStateException(
                        "İlk-dört-ses kuram kataloğu boş olamaz"));
    }

    /** Resolves {@link #maximumFourthDegreeCent(TuningSystemCatalog.SystemData)} by system name. */
    public static double maximumFourthDegreeCent(String referenceSystem) {
        Map<String, TuningSystemCatalog.SystemData> systems =
                new TuningSystemCatalog().loadBuiltIns();
        TuningSystemCatalog.SystemData system = Objects.requireNonNull(
                systems.get(referenceSystem), "Unknown reference system: " + referenceSystem);
        return maximumFourthDegreeCent(system);
    }

    private record Specification(String displayName, String sourceProfile) { }

    public record ReferenceProfile(int order, String displayName, String sourceProfile,
                                   List<Double> centPositions, List<Double> hcPositions) {
        public ReferenceProfile {
            centPositions = List.copyOf(centPositions);
            hcPositions = List.copyOf(hcPositions);
            if (centPositions.size() != 4 || hcPositions.size() != 4) {
                throw new IllegalArgumentException("İlk-dört-ses profili dört derece içermelidir");
            }
        }

        public double[] centArray() {
            return centPositions.stream().mapToDouble(Double::doubleValue).toArray();
        }
    }
}
