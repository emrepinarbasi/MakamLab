package com.makambox.studio.study;

import com.makambox.studio.legacy.TuningSystemCatalog;
import com.makambox.studio.model.AnalysisConfig;

import java.util.List;

/** Regression gate for the delimited 13-profile first-four comparison set. */
public final class FirstFourProfileReferenceCatalogRegressionTest {
    private static final List<String> CORE_NAMES = List.of(
            "Çargâh", "Kürdî", "Rast", "Uşşak", "Hicaz");
    private static final List<String> EXPECTED_NAMES = List.of(
            "Çargâh", "Kürdî", "Rast", "Uşşak", "Hicaz", "Sabâ",
            "Segâh", "Müstear", "Hüzzam", "Nikriz", "Pençgâh", "Ferahnâk", "Nişabur");

    private static final List<List<Double>> EXPECTED_HC = List.of(
            List.of(0.0, 9.0, 18.0, 22.0),
            List.of(0.0, 4.0, 13.0, 22.0),
            List.of(0.0, 9.0, 17.0, 22.0),
            List.of(0.0, 8.0, 13.0, 22.0),
            List.of(0.0, 5.0, 17.0, 22.0),
            List.of(0.0, 8.0, 13.0, 18.0),
            List.of(0.0, 5.0, 14.0, 22.0),
            List.of(0.0, 9.0, 14.0, 22.0),
            List.of(0.0, 5.0, 14.0, 19.0),
            List.of(0.0, 9.0, 14.0, 26.0),
            List.of(0.0, 9.0, 17.0, 22.0),
            List.of(0.0, 5.0, 14.0, 23.0),
            List.of(0.0, 9.0, 17.0, 22.0));

    public static void main(String[] args) {
        TuningSystemCatalog.SystemData system = new TuningSystemCatalog().loadBuiltIns()
                .get(MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM);
        List<FirstFourProfileReferenceCatalog.ReferenceProfile> references =
                FirstFourProfileReferenceCatalog.references(system);

        check(references.stream().map(
                        FirstFourProfileReferenceCatalog.ReferenceProfile::displayName).toList()
                        .equals(EXPECTED_NAMES),
                "the comparison set and order must be exactly the requested 13 profiles");
        check(FirstFourProfileReferenceCatalog.displayNames().equals(EXPECTED_NAMES),
                "the public ordered name inventory must match the derived references");
        check(FirstFourProfileReferenceCatalog.coreDisplayNames().equals(CORE_NAMES),
                "the immutable core inventory must contain exactly the requested five profiles");
        check(FirstFourProfileReferenceCatalog.optionalDisplayNames().equals(
                        EXPECTED_NAMES.subList(CORE_NAMES.size(), EXPECTED_NAMES.size())),
                "the other eight profiles must remain available as the optional inventory");

        for (int index = 0; index < references.size(); index++) {
            FirstFourProfileReferenceCatalog.ReferenceProfile reference = references.get(index);
            double[] source = system.intervalsFor(reference.sourceProfile());
            check(source != null && source.length >= 4,
                    reference.displayName() + " must have a packaged MakamBox source profile");
            for (int degree = 0; degree < 4; degree++) {
                double sourceRelative = source[degree] - source[0];
                close(reference.centPositions().get(degree), sourceRelative, 1.0e-9,
                        reference.displayName() + " degree " + (degree + 1)
                                + " must be derived from the packaged source");
                close(reference.hcPositions().get(degree), EXPECTED_HC.get(index).get(degree),
                        1.0e-5, reference.displayName() + " Hc degree " + (degree + 1));
                close(reference.centPositions().get(degree),
                        reference.hcPositions().get(degree) * AnalysisConfig.COMMA_CENT,
                        1.0e-9, reference.displayName() + " C/Hc conversion");
            }
        }

        MakamProfileComparison emptyEvidence = new MakamProfileComparator()
                .compare(List.of(), 220.0, "Ussak");
        check(emptyEvidence.candidates().size() == CORE_NAMES.size(),
                "the default comparator must contain exactly the five core profiles");
        check(emptyEvidence.candidates().stream().map(
                        MakamProfileComparison.Candidate::makamName).toList().equals(CORE_NAMES),
                "equal-score default candidates must preserve the core catalogue order");
        check(emptyEvidence.candidates().stream().allMatch(value ->
                        value.degreeMatches().size() == 4 && value.theoreticalDegreeCount() == 4),
                "comparison candidates must contain only their first four degrees");

        FirstFourProfileSelection partial = FirstFourProfileSelection.ofOptionalDisplayNames(
                List.of("Nikriz", "Sabâ"));
        List<String> expectedPartial = List.of(
                "Çargâh", "Kürdî", "Rast", "Uşşak", "Hicaz", "Sabâ", "Nikriz");
        check(partial.includedDisplayNames().equals(expectedPartial),
                "optional settings must be normalized to catalogue order");
        MakamProfileComparison partialEvidence = new MakamProfileComparator().compare(
                List.of(), 220.0, "Ussak", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM, partial);
        check(partialEvidence.candidates().stream()
                        .map(MakamProfileComparison.Candidate::makamName).toList()
                        .equals(expectedPartial),
                "an explicit optional selection must reach the comparator without adding other profiles");
        check(partialEvidence.firstFourProfileSelection().equals(partial),
                "the result must freeze the exact profile selection used for analysis");

        MakamProfileComparison allEvidence = new MakamProfileComparator().compare(
                List.of(), 220.0, "Ussak", MakamProfileComparator.DEFAULT_REFERENCE_SYSTEM,
                FirstFourProfileSelection.allProfiles());
        check(allEvidence.candidates().stream().map(
                        MakamProfileComparison.Candidate::makamName).toList().equals(EXPECTED_NAMES),
                "an explicit all-profile audit must preserve all thirteen catalogue entries");

        System.out.println("FIRST_FOUR_PROFILE_REFERENCE_CATALOG_OK");
    }

    private static void close(double actual, double expected, double tolerance, String message) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
