package com.makambox.studio.study.hicaz;

import com.makambox.studio.model.AnalysisConfig;

/** Canonical 53-Hc positions of the four degrees of the Hicaz tetrachord. */
public enum HicazTetrachordDegree {
    DEGREE_1(1, "1. derece (karar)", 0.0),
    DEGREE_2(2, "2. derece", 5.0),
    DEGREE_3(3, "3. derece", 17.0),
    DEGREE_4(4, "4. derece", 22.0);

    private final int number;
    private final String label;
    private final double theoreticalHc;

    HicazTetrachordDegree(int number, String label, double theoreticalHc) {
        this.number = number;
        this.label = label;
        this.theoreticalHc = theoreticalHc;
    }

    public int number() { return number; }
    public String label() { return label; }
    public double theoreticalHc() { return theoreticalHc; }
    public double theoreticalCent() { return theoreticalHc * AnalysisConfig.COMMA_CENT; }

    public double theoreticalHertz(double tonicHertz) {
        if (!Double.isFinite(tonicHertz) || tonicHertz <= 0.0) {
            throw new IllegalArgumentException("tonicHertz must be finite and positive");
        }
        return tonicHertz * Math.pow(2.0, theoreticalCent() / 1200.0);
    }

    public static HicazTetrachordDegree fromNumber(int number) {
        for (HicazTetrachordDegree value : values()) {
            if (value.number == number) return value;
        }
        throw new IllegalArgumentException("Hicaz degree must be between 1 and 4: " + number);
    }

    @Override public String toString() { return label; }
}
