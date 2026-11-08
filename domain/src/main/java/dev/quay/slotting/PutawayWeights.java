package dev.quay.slotting;

/**
 * The weights and hard limits for one putaway day. Nothing in the solver substitutes
 * its own numbers when a caller leaves a field out; the document has to carry them.
 * The ergonomics multipliers are the slotting ones, read for a put: a landing costs
 * one-way meters times the ergonomic factor.
 */
public record PutawayWeights(
        double goldenWeight,
        double lowWeight,
        double affinityWeight,
        double affinityDecayBays,
        double ergoGolden,
        double ergoFloor,
        double ergoBeam,
        double heavyKg,
        int maxLevelForHeavy) {

    public double ergo(Ergonomics ergonomics) {
        return switch (ergonomics) {
            case GOLDEN -> ergoGolden;
            case FLOOR -> ergoFloor;
            case BEAM -> ergoBeam;
        };
    }
}
