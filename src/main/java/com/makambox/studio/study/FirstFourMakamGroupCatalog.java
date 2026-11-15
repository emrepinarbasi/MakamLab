package com.makambox.studio.study;

import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Canonical display names for makam profiles that share the same first four
 * Arel–Ezgi–Uzdilek degrees.  A label describes an interval family; it is not
 * by itself a makam diagnosis.
 */
public final class FirstFourMakamGroupCatalog {
    public static final String USSAK = "Uşşak";
    public static final String BUSELIK = "Bûselik";
    public static final String KURDI = "Kürdî";
    public static final String SEGAH = "Segâh";

    private static final double SIGNATURE_TOLERANCE_HC = 0.35;
    private static final List<Group> GROUPS = List.of(
            new Group(USSAK, List.of(0, 8, 13, 22), List.of(
                    "acem", "beyati", "isfahan", "ussak", "nuhuft", "gulizar",
                    "huseyni", "muhayyer", "neva", "tahir", "tahirbuselik",
                    "beyatiaraban", "karcigar")),
            new Group(BUSELIK, List.of(0, 9, 13, 22), List.of(
                    "buselik", "nihavend", "sultaniyegah")),
            new Group(KURDI, List.of(0, 4, 13, 22), List.of(
                    "kurdilihicazkar", "ferahfeza", "kurdi")),
            new Group(SEGAH, List.of(0, 5, 14, 22), List.of(
                    "bestenigar", "segah", "evic", "irak"))
    );
    private static final Map<String, String> LABEL_BY_PROFILE = profileLabels();

    private FirstFourMakamGroupCatalog() { }

    /**
     * Returns the requested canonical family name when either the theoretical
     * first-four signature or every supplied profile name identifies one group.
     */
    public static Optional<String> canonicalLabel(List<Double> theoreticalHc,
                                                   Collection<String> makamNames) {
        Optional<String> bySignature = labelBySignature(theoreticalHc);
        if (bySignature.isPresent()) return bySignature;
        if (makamNames == null || makamNames.isEmpty()) return Optional.empty();

        LinkedHashSet<String> labels = new LinkedHashSet<>();
        int namedProfiles = 0;
        for (String makamName : makamNames) {
            if (makamName == null || makamName.isBlank()) continue;
            namedProfiles++;
            String label = LABEL_BY_PROFILE.get(normalize(makamName));
            if (label == null) return Optional.empty();
            labels.add(label);
        }
        return namedProfiles > 0 && labels.size() == 1
                ? Optional.of(labels.iterator().next()) : Optional.empty();
    }

    /** Returns a canonical family name, or the original profile list for an unknown family. */
    public static String displayLabel(List<Double> theoreticalHc,
                                      Collection<String> makamNames) {
        Optional<String> canonical = canonicalLabel(theoreticalHc, makamNames);
        if (canonical.isPresent()) return canonical.get();
        if (makamNames == null) return "Belirtilmeyen profil";
        String fallback = makamNames.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct().reduce((left, right) -> left + ", " + right).orElse("");
        return fallback.isBlank() ? "Belirtilmeyen profil" : fallback;
    }

    private static Optional<String> labelBySignature(List<Double> theoreticalHc) {
        if (theoreticalHc == null || theoreticalHc.size() < 4) return Optional.empty();
        for (Group group : GROUPS) {
            boolean matches = true;
            for (int index = 0; index < 4; index++) {
                Double actual = theoreticalHc.get(index);
                if (actual == null || !Double.isFinite(actual)
                        || Math.abs(actual - group.signatureHc().get(index))
                        > SIGNATURE_TOLERANCE_HC) {
                    matches = false;
                    break;
                }
            }
            if (matches) return Optional.of(group.label());
        }
        return Optional.empty();
    }

    private static Map<String, String> profileLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (Group group : GROUPS) {
            for (String profile : group.normalizedProfiles()) labels.put(profile, group.label());
        }
        return Map.copyOf(labels);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('ı', 'i')
                .replaceAll("[^a-z0-9]", "");
    }

    private record Group(String label, List<Integer> signatureHc,
                         List<String> normalizedProfiles) { }
}
