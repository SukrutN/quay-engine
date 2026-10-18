package dev.quay.slotting;

/**
 * The weights and hard limits for one run. Nothing in the solver substitutes its own numbers
 * when a caller leaves a field out; the document has to carry them.
 */
public record Policy(
        double travelWeight,
        double wasteWeight,
        double affinityWeight,
        double goldenWeight,
        double lightFloorWeight,
        double slowPickCutoff,
        double lightKg,
        double heavyKg,
        int maxLevelForHeavy,
        double ergoGolden,
        double ergoFloor,
        double ergoBeam,
        double affinityDecayBays,
        int maxPasses) {

    public double ergo(Ergonomics ergonomics) {
        return switch (ergonomics) {
            case GOLDEN -> ergoGolden;
            case FLOOR -> ergoFloor;
            case BEAM -> ergoBeam;
        };
    }
}
