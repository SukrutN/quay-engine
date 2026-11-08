package dev.quay.slotting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Directs by velocity: the manifest is read fastest first, and each landing takes
 * the feasible bay with the lowest cost — one-way meters times the ergonomic
 * factor, plus the band penalties. A slow carton into the golden band pays the
 * golden weight; a fast carton away from it pays the low weight. Ties keep the
 * lower bay id.
 */
public final class VelocityPutaway implements PutawayPolicy {

    public static final String NAME = "velocity";

    private static final double TIE = 1.0e-9;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PutawayResult run(PutawayDay day) {
        return PutawayCore.run(NAME, day, velocityOrder(day), VelocityPutaway::pick);
    }

    /** The manifest reordered by picks per day, highest first; SKU id breaks ties, then receipt id. */
    static List<Receipt> velocityOrder(PutawayDay day) {
        List<Receipt> ordered = new ArrayList<>(day.receipts());
        ordered.sort(Comparator
                .comparing((Receipt receipt) -> day.sku(receipt.skuId()).orElseThrow().picksPerDay())
                .reversed()
                .thenComparing(receipt -> day.sku(receipt.skuId()).orElseThrow().id())
                .thenComparing(Receipt::id));
        return ordered;
    }

    static double cost(PutawayDay day, Slot slot, Sku sku, VelocityClass band) {
        PutawayWeights weights = day.weights();
        double cost = slot.returnMeters() / 2.0 * weights.ergo(slot.ergonomics());
        if (slot.ergonomics() == Ergonomics.GOLDEN) {
            if (band == VelocityClass.C) {
                cost += weights.goldenWeight() * (day.bands().bCutoff() - sku.picksPerDay());
            }
        } else if (band == VelocityClass.A) {
            cost += weights.lowWeight() * sku.picksPerDay();
        }
        return cost;
    }

    private static Slot pick(SlotState state, PutawayDay day, Sku sku, int cartons, Map<String, Slot> landing) {
        VelocityClass band = day.bands().band(sku.picksPerDay());
        Slot best = null;
        double bestCost = 0;
        for (Slot slot : day.slots()) {
            if (!PutawayRules.fits(state, slot, sku, cartons, day.weights())) {
                continue;
            }
            double cost = cost(day, slot, sku, band);
            if (best == null || cost < bestCost - TIE
                    || (cost <= bestCost + TIE && slot.id().compareTo(best.id()) < 0)) {
                best = slot;
                bestCost = cost;
            }
        }
        return best;
    }
}
