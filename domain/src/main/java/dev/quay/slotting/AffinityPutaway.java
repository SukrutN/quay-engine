package dev.quay.slotting;

import java.util.List;
import java.util.Map;

/**
 * Directs by velocity, then lets the co-pick curves pull: a landing earns a credit
 * for sitting near the bays its partners already took, on the same strength, decay,
 * and pick-weighted curve the outbound objective reads. Above that it is the
 * velocity policy; ties keep the lower bay id.
 */
public final class AffinityPutaway implements PutawayPolicy {

    public static final String NAME = "affinity";

    private static final double TIE = 1.0e-9;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PutawayResult run(PutawayDay day) {
        return PutawayCore.run(NAME, day, VelocityPutaway.velocityOrder(day), AffinityPutaway::pick);
    }

    /** The affinity credit for one candidate bay, against the partners that landed earlier. */
    static double credit(PutawayDay day, Sku sku, Slot slot, Map<String, Slot> landing) {
        if (landing.isEmpty() || day.affinities().isEmpty()) {
            return 0;
        }
        PutawayWeights weights = day.weights();
        double credit = 0;
        for (Affinity affinity : day.affinities()) {
            String partnerId = partnerOf(affinity, sku.id());
            if (partnerId == null) {
                continue;
            }
            Slot placed = landing.get(partnerId);
            if (placed == null) {
                continue;
            }
            int steps = Math.abs(slot.aisleIndex() - placed.aisleIndex())
                    + Math.abs(slot.bayIndex() - placed.bayIndex());
            double picks = sku.picksPerDay() * day.sku(partnerId).orElseThrow().picksPerDay();
            credit += affinity.strength() * Math.sqrt(picks) * Math.exp(-steps / weights.affinityDecayBays());
        }
        return weights.affinityWeight() * credit;
    }

    private static String partnerOf(Affinity affinity, String skuId) {
        if (affinity.skuA().equals(skuId)) {
            return affinity.skuB();
        }
        if (affinity.skuB().equals(skuId)) {
            return affinity.skuA();
        }
        return null;
    }

    private static Slot pick(SlotState state, PutawayDay day, Sku sku, int cartons, Map<String, Slot> landing) {
        VelocityClass band = day.bands().band(sku.picksPerDay());
        Slot best = null;
        double bestScore = 0;
        for (Slot slot : day.slots()) {
            if (!PutawayRules.fits(state, slot, sku, cartons, day.weights())) {
                continue;
            }
            double score = VelocityPutaway.cost(day, slot, sku, band) - credit(day, sku, slot, landing);
            if (best == null || score < bestScore - TIE
                    || (score <= bestScore + TIE && slot.id().compareTo(best.id()) < 0)) {
                best = slot;
                bestScore = score;
            }
        }
        return best;
    }
}
