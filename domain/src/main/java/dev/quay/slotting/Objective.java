package dev.quay.slotting;

/** Weighted cost of a placement. Affinity is a credit, so it is subtracted in the total. */
public final class Objective {

    private Objective() {}

    public static CostBreakdown assess(PickFace face, Placement placement) {
        Policy policy = face.policy();
        double travel = 0;
        double waste = 0;
        double golden = 0;
        double lightFloor = 0;
        for (MapEntry entry : pairs(face, placement)) {
            Sku sku = entry.sku;
            Slot slot = entry.slot;
            double picks = sku.picksPerDay();
            travel += policy.travelWeight() * picks * slot.returnMeters() * policy.ergo(slot.ergonomics());
            double wasteRatio = (slot.cubeLiters() - sku.cubeLiters()) / slot.cubeLiters();
            waste += policy.wasteWeight() * wasteRatio * picks;
            if (slot.ergonomics() == Ergonomics.GOLDEN && picks < policy.slowPickCutoff()) {
                golden += policy.goldenWeight() * (policy.slowPickCutoff() - picks);
            }
            if (slot.ergonomics() == Ergonomics.FLOOR && sku.weightKg() <= policy.lightKg()) {
                lightFloor += policy.lightFloorWeight() * picks;
            }
        }
        double affinity = affinityCredit(face, placement);
        double total = travel + waste + golden + lightFloor - affinity;
        return new CostBreakdown(travel, waste, golden, lightFloor, affinity, total);
    }

    public static double total(PickFace face, Placement placement) {
        return assess(face, placement).total();
    }

    private static double affinityCredit(PickFace face, Placement placement) {
        Policy policy = face.policy();
        double credit = 0;
        for (Affinity affinity : face.affinities()) {
            String slotA = placement.slotOf(affinity.skuA()).orElse(null);
            String slotB = placement.slotOf(affinity.skuB()).orElse(null);
            if (slotA == null || slotB == null) {
                continue;
            }
            Slot left = face.slot(slotA).orElseThrow();
            Slot right = face.slot(slotB).orElseThrow();
            Sku skuA = face.sku(affinity.skuA()).orElseThrow();
            Sku skuB = face.sku(affinity.skuB()).orElseThrow();
            int steps = Math.abs(left.aisleIndex() - right.aisleIndex()) + Math.abs(left.bayIndex() - right.bayIndex());
            double pull = affinity.strength()
                    * Math.sqrt(skuA.picksPerDay() * skuB.picksPerDay())
                    * Math.exp(-steps / policy.affinityDecayBays());
            credit += policy.affinityWeight() * pull;
        }
        return credit;
    }

    private static java.util.List<MapEntry> pairs(PickFace face, Placement placement) {
        java.util.List<MapEntry> pairs = new java.util.ArrayList<>();
        for (var entry : placement.slotToSku().entrySet()) {
            Slot slot = face.slot(entry.getKey()).orElseThrow();
            Sku sku = face.sku(entry.getValue()).orElseThrow();
            pairs.add(new MapEntry(slot, sku));
        }
        return pairs;
    }

    private record MapEntry(Slot slot, Sku sku) {}
}
