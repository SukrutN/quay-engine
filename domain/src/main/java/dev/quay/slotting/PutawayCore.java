package dev.quay.slotting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The shared loop behind the policies: the openings, the replenishment signals, and
 * every manifest line either to the forward face or into a reserve bay. The pick
 * decides which bay; everything else is the same for every policy.
 */
final class PutawayCore {

    /** Chooses the bay one reserve landing takes. Null means no bay: the line is refused. */
    interface Pick {
        Slot pick(SlotState state, PutawayDay day, Sku sku, int cartons, Map<String, Slot> landing);
    }

    private PutawayCore() {}

    static PutawayResult run(String policy, PutawayDay day, List<Receipt> ordered, Pick pick) {
        PutawayRules.check(day);
        PutawayWeights weights = day.weights();

        LinkedHashMap<String, Integer> onHand = new LinkedHashMap<>(day.forward().onHand());
        Map<String, Boolean> armed = new LinkedHashMap<>();
        List<ReplenishSignal> signals = new ArrayList<>();

        // The openings run first, in document order; they can trip a raise on their own.
        for (StockMove move : day.forward().openings()) {
            onHand.put(move.skuId(), onHand.getOrDefault(move.skuId(), 0) + move.cartons());
        }
        for (Sku sku : day.catalog()) {
            raise(sku, day, onHand, armed, signals);
        }

        List<PutawayDirective> directives = new ArrayList<>();
        List<DirectiveRefusal> refusals = new ArrayList<>();
        SlotState state = SlotState.of(day.startState());
        Map<String, Slot> landing = new LinkedHashMap<>();
        Set<String> bays = new LinkedHashSet<>();
        int forward = 0;
        int reserve = 0;
        double goldenCartons = 0;
        double meters = 0;

        for (Receipt receipt : ordered) {
            Sku sku = day.sku(receipt.skuId()).orElseThrow();
            VelocityClass band = day.bands().band(sku.picksPerDay());
            Threshold threshold = day.forward().thresholds().get(band);
            int cartons = day.cartonsOf(receipt).orElseThrow();

            // While a raise is outstanding, the landing routes to the face toward the max,
            // and the signal goes quiet only past the band.
            if (armed.getOrDefault(sku.id(), false)) {
                int toMax = toMax(threshold, onHand.getOrDefault(sku.id(), 0));
                int taken = Math.min(cartons, toMax);
                if (taken > 0) {
                    cartons -= taken;
                    forward += taken;
                    int on = onHand.getOrDefault(sku.id(), 0) + taken;
                    onHand.put(sku.id(), on);
                    directives.add(new PutawayDirective(receipt.id(), sku.id(), sku.name(), receipt.units(),
                            receipt.uom(), taken, band, PutawayDirective.FORWARD, "", 0));
                }
                int on = onHand.getOrDefault(sku.id(), 0);
                if (on >= threshold.minCartons() + threshold.hysteresisCartons()) {
                    armed.put(sku.id(), false);
                    signals.add(new ReplenishSignal(sku.id(), on, ReplenishSignal.QUIET, 0));
                }
            }

            if (cartons > 0) {
                Slot slot = pick.pick(state, day, sku, cartons, landing);
                String refusal = slot == null
                        ? null
                        : state.refusal(slot, sku, cartons, weights);
                if (slot == null || refusal != null) {
                    refusals.add(new DirectiveRefusal(receipt.id(), refusal == null
                            ? sku.name() + " has no bay left that can hold it."
                            : refusal));
                } else {
                    double put = slot.returnMeters() / 2.0;
                    directives.add(new PutawayDirective(receipt.id(), sku.id(), sku.name(), receipt.units(),
                            receipt.uom(), cartons, band, PutawayDirective.RESERVE, slot.id(), put));
                    state = state.put(slot.id(), sku.id(), cartons);
                    landing.putIfAbsent(sku.id(), slot);
                    bays.add(slot.id());
                    meters += put;
                    reserve += cartons;
                    if (slot.ergonomics() == Ergonomics.GOLDEN) {
                        goldenCartons += cartons;
                    }
                }
            }
        }

        PutawayTotals totals = new PutawayTotals(day.receipts().size(), refusals.size(), forward + reserve,
                forward, reserve, goldenCartons, meters, bays.size());
        return new PutawayResult(policy, directives, refusals, signals, state.fills(), totals);
    }

    private static void raise(
            Sku sku,
            PutawayDay day,
            Map<String, Integer> onHand,
            Map<String, Boolean> armed,
            List<ReplenishSignal> signals) {
        if (armed.getOrDefault(sku.id(), false)) {
            return;
        }
        Threshold threshold = day.forward().thresholds().get(day.bands().band(sku.picksPerDay()));
        int on = onHand.getOrDefault(sku.id(), 0);
        if (on < threshold.minCartons()) {
            armed.put(sku.id(), true);
            signals.add(new ReplenishSignal(sku.id(), on, ReplenishSignal.RAISE, toMax(threshold, on)));
        }
    }

    /** The cartons the face asked for to climb to its max, in whole cartons. */
    private static int toMax(Threshold threshold, int on) {
        return Math.max(0, (int) Math.ceil(threshold.maxCartons() - on - 1.0e-9));
    }
}
