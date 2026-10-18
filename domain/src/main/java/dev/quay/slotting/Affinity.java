package dev.quay.slotting;

import java.util.Objects;

/** Two SKUs that leave together often enough that nearby slots are worth a longer walk. */
public record Affinity(String skuA, String skuB, double strength) {

    public Affinity {
        Objects.requireNonNull(skuA, "skuA");
        Objects.requireNonNull(skuB, "skuB");
    }
}
