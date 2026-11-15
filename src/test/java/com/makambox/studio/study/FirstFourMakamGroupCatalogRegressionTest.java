package com.makambox.studio.study;

import java.util.List;

/** Regression coverage for the named first-four interval families used in reports. */
public final class FirstFourMakamGroupCatalogRegressionTest {
    public static void main(String[] args) {
        checkSignature(List.of(0.0, 8.0, 13.0, 22.0), "Uşşak");
        checkSignature(List.of(0.0, 9.0, 13.0, 22.0), "Bûselik");
        checkSignature(List.of(0.0, 4.0, 13.0, 22.0), "Kürdî");
        checkSignature(List.of(0.0, 5.0, 14.0, 22.0), "Segâh");

        checkProfiles("Uşşak", List.of("acem", "beyati", "isfahan", "ussak", "nuhuft",
                "gulizar", "huseyni", "muhayyer", "neva", "tahir", "tahirbuselik",
                "beyatiaraban", "karcigar"));
        checkProfiles("Bûselik", List.of("bûselik", "nihâvend", "sultaniyegâh"));
        checkProfiles("Kürdî", List.of("kürdilihicazkâr", "ferahfezâ", "kürdî"));
        checkProfiles("Segâh", List.of("bestenigâr", "segâh", "eviç", "ırak"));

        check("Uşşak".equals(FirstFourMakamGroupCatalog.displayLabel(
                        List.of(0.02, 8.08, 12.96, 22.04), List.of("bilinmeyen"))),
                "small floating-point deviations must retain the theoretical family label");
        check("Müsteâr".equals(FirstFourMakamGroupCatalog.displayLabel(
                        List.of(0.0, 9.0, 14.0, 22.0), List.of("Müsteâr"))),
                "an unrequested unique profile must retain its own name");
        check("Uşşak, Kürdî".equals(FirstFourMakamGroupCatalog.displayLabel(
                        List.of(), List.of("Uşşak", "Kürdî"))),
                "mixed profile families must not be assigned one canonical label");

        System.out.println("FIRST_FOUR_MAKAM_GROUP_CATALOG_OK");
    }

    private static void checkSignature(List<Double> signature, String expected) {
        check(expected.equals(FirstFourMakamGroupCatalog.displayLabel(signature, List.of())),
                signature + " must be displayed as " + expected);
    }

    private static void checkProfiles(String expected, List<String> profiles) {
        check(expected.equals(FirstFourMakamGroupCatalog.displayLabel(List.of(), profiles)),
                profiles + " must be grouped as " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
