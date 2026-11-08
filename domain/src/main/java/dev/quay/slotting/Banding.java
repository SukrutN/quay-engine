package dev.quay.slotting;

import java.util.Objects;

/** The two cutoffs that cut the catalog into three bands. A needs more picks than B; B needs more than zero. */
public record Banding(double aCutoff, double bCutoff) {

    public Banding {
        Objects.requireNonNull(bCutoff, "bCutoff");
    }

    public VelocityClass band(double picksPerDay) {
        if (picksPerDay >= aCutoff) {
            return VelocityClass.A;
        }
        if (picksPerDay >= bCutoff) {
            return VelocityClass.B;
        }
        return VelocityClass.C;
    }
}
