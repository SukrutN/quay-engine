package dev.quay.slotting;

/** Physical readings. Travel here is pick-meters of out-and-back walk, before ergonomic weights. */
public final class Measurements {

    private Measurements() {}

    public static FaceMetrics measure(PickFace face, Placement placement) {
        double travel = 0;
        double picks = 0;
        double goldenPicks = 0;
        double fitSum = 0;
        int placed = 0;
        int heavyAbove = 0;
        Policy policy = face.policy();
        for (var entry : placement.slotToSku().entrySet()) {
            Slot slot = face.slot(entry.getKey()).orElseThrow();
            Sku sku = face.sku(entry.getValue()).orElseThrow();
            travel += sku.picksPerDay() * slot.returnMeters();
            picks += sku.picksPerDay();
            if (slot.ergonomics() == Ergonomics.GOLDEN) {
                goldenPicks += sku.picksPerDay();
            }
            fitSum += sku.cubeLiters() / slot.cubeLiters();
            if (sku.weightKg() >= policy.heavyKg() && slot.level() > policy.maxLevelForHeavy()) {
                heavyAbove++;
            }
            placed++;
        }
        double affinitySteps = meanAffinitySteps(face, placement);
        double share = picks > 0 ? goldenPicks / picks : 0;
        double fit = placed > 0 ? fitSum / placed : 0;
        return new FaceMetrics(travel, share, fit, heavyAbove, affinitySteps, placed, picks);
    }

    private static double meanAffinitySteps(PickFace face, Placement placement) {
        double weighted = 0;
        double weight = 0;
        for (Affinity affinity : face.affinities()) {
            String slotA = placement.slotOf(affinity.skuA()).orElse(null);
            String slotB = placement.slotOf(affinity.skuB()).orElse(null);
            if (slotA == null || slotB == null) {
                continue;
            }
            Slot left = face.slot(slotA).orElseThrow();
            Slot right = face.slot(slotB).orElseThrow();
            int steps = Math.abs(left.aisleIndex() - right.aisleIndex()) + Math.abs(left.bayIndex() - right.bayIndex());
            weighted += steps * affinity.strength();
            weight += affinity.strength();
        }
        return weight > 0 ? weighted / weight : 0;
    }
}
