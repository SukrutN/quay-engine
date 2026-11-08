package dev.quay.slotting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Document rules. A day that fails here is not run. */
public final class PutawayRules {

    private PutawayRules() {}

    public static void check(PutawayDay day) {
        List<String> reasons = new ArrayList<>();
        text(day.id(), "day.id", reasons);
        text(day.name(), "day.name", reasons);
        checkSlots(day, reasons);
        checkCatalog(day, reasons);
        checkReceipts(day, reasons);
        checkUoms(day, reasons);
        checkBands(day.bands(), reasons);
        checkWeights(day.weights(), reasons);
        checkForward(day, reasons);
        checkStartState(day, reasons);
        if (!reasons.isEmpty()) {
            throw new PutawayRejection(reasons);
        }
    }

    /** Whether a bay can hold this landing. Zone, heavy cap, and what is already in the bay. */
    public static boolean fits(SlotState state, Slot slot, Sku sku, int cartons, PutawayWeights weights) {
        return state.refusal(slot, sku, cartons, weights) == null;
    }

    static boolean zonesMatch(Sku sku, Slot slot) {
        if (sku.zone().isPresent() != slot.zone().isPresent()) {
            return false;
        }
        return sku.zone().equals(slot.zone());
    }

    private static void text(String value, String field, List<String> reasons) {
        if (value == null || value.isBlank()) {
            reasons.add(field + " is required.");
        }
    }

    private static void checkSlots(PutawayDay day, List<String> reasons) {
        if (day.slots().isEmpty()) {
            reasons.add("The day has no reserve bays.");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (Slot slot : day.slots()) {
            if (slot.id() == null || slot.id().isBlank()) {
                reasons.add("A bay is missing an id.");
            } else if (!seen.add(slot.id())) {
                reasons.add("Bay " + slot.id() + " is listed twice.");
            }
            if (!Double.isFinite(slot.cubeLiters()) || slot.cubeLiters() <= 0) {
                reasons.add("Bay " + slot.id() + " needs a positive cube.");
            }
            if (!Double.isFinite(slot.returnMeters()) || slot.returnMeters() < 0) {
                reasons.add("Bay " + slot.id() + " needs a return distance of zero or greater.");
            }
            if (slot.zone().filter(String::isBlank).isPresent()) {
                reasons.add("Bay " + slot.id() + " has a blank zone. Omit the zone or name one.");
            }
        }
    }

    private static void checkCatalog(PutawayDay day, List<String> reasons) {
        if (day.catalog().isEmpty()) {
            reasons.add("The catalog is empty.");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (Sku sku : day.catalog()) {
            if (sku.id() == null || sku.id().isBlank()) {
                reasons.add("A SKU is missing an id.");
                continue;
            }
            if (!seen.add(sku.id())) {
                reasons.add("SKU " + sku.id() + " is listed twice in the catalog.");
            }
            if (sku.name() == null || sku.name().isBlank()) {
                reasons.add(sku.id() + " is missing a name.");
            }
            if (!Double.isFinite(sku.picksPerDay()) || sku.picksPerDay() < 0) {
                reasons.add(sku.name() + " has an unreadable pick count.");
            }
            if (!Double.isFinite(sku.weightKg()) || sku.weightKg() < 0) {
                reasons.add(sku.name() + " has an unreadable weight.");
            }
            if (!Double.isFinite(sku.cubeLiters()) || sku.cubeLiters() <= 0) {
                reasons.add(sku.name() + " has no usable cube.");
            } else if (day.slots().stream().noneMatch(slot -> fits(SlotState.of(Map.of()), slot, sku, 1, day.weights()))) {
                reasons.add(sku.name() + " has no bay that holds even one carton.");
            }
            if (sku.zone().filter(String::isBlank).isPresent()) {
                reasons.add(sku.name() + " has a blank zone. Omit the zone or name one.");
            }
        }
        for (Affinity affinity : day.affinities()) {
            if (day.sku(affinity.skuA()).isEmpty() || day.sku(affinity.skuB()).isEmpty()) {
                reasons.add("Affinity " + affinity.skuA() + " / " + affinity.skuB() + " names a SKU that is not in the catalog.");
            }
            if (affinity.skuA().equals(affinity.skuB())) {
                reasons.add("Affinity " + affinity.skuA() + " / " + affinity.skuB() + " points at itself.");
            }
            if (!Double.isFinite(affinity.strength()) || affinity.strength() <= 0 || affinity.strength() > 1) {
                reasons.add("Affinity " + affinity.skuA() + " / " + affinity.skuB() + " needs a strength between 0 and 1.");
            }
        }
    }

    private static void checkReceipts(PutawayDay day, List<String> reasons) {
        if (day.receipts().isEmpty()) {
            reasons.add("The manifest has no lines.");
            return;
        }
        Set<String> seenIds = new HashSet<>();
        for (Receipt receipt : day.receipts()) {
            if (receipt.id() == null || receipt.id().isBlank()) {
                reasons.add("A receipt is missing an id.");
                continue;
            }
            if (!seenIds.add(receipt.id())) {
                reasons.add("Receipt " + receipt.id() + " is listed twice.");
            }
            Sku sku = receipt.skuId() == null ? null : day.sku(receipt.skuId()).orElse(null);
            if (sku == null) {
                reasons.add("Receipt " + receipt.id() + " names " + receipt.skuId()
                        + ", which is not in the catalog.");
                continue;
            }
            if (receipt.units() < 1) {
                reasons.add("Receipt " + receipt.id() + " asks for zero cartons of " + sku.name() + ".");
                continue;
            }
            if (receipt.uom() == null || receipt.uom().isBlank()) {
                reasons.add("Receipt " + receipt.id() + " does not say its unit of measure.");
                continue;
            }
            if (day.cartonsOf(receipt).isEmpty()) {
                Double conversion = day.uoms().getOrDefault(receipt.skuId(), Map.of()).get(receipt.uom());
                boolean listed = conversion != null && Double.isFinite(conversion) && conversion > 0;
                if (listed && Math.abs(receipt.units() * conversion - Math.rint(receipt.units() * conversion)) > 1.0e-9) {
                    reasons.add("Receipt " + receipt.id() + " asks for a fraction of a carton.");
                } else {
                    reasons.add("Receipt " + receipt.id() + " lists '" + receipt.uom()
                            + "', which " + sku.name() + " does not publish.");
                }
            }
        }
    }

    private static void checkUoms(PutawayDay day, List<String> reasons) {
        for (Map.Entry<String, Map<String, Double>> entry : day.uoms().entrySet()) {
            if (day.sku(entry.getKey()).isEmpty()) {
                reasons.add("The uom table names SKU " + entry.getKey() + ", which is not in the catalog.");
                continue;
            }
            for (Map.Entry<String, Double> unit : entry.getValue().entrySet()) {
                if (!Double.isFinite(unit.getValue()) || unit.getValue() <= 0) {
                    reasons.add("The uom table needs a positive count of cartons for " + entry.getKey() + " / "
                            + unit.getKey() + ".");
                }
                if ("carton".equals(unit.getKey()) && Math.abs(unit.getValue() - 1.0) > 1.0e-9) {
                    reasons.add("A carton is one carton. Leave carton off the uom table for " + entry.getKey() + ".");
                }
            }
        }
    }

    private static void checkBands(Banding bands, List<String> reasons) {
        if (bands == null) {
            reasons.add("bands is required.");
            return;
        }
        if (!Double.isFinite(bands.aCutoff()) || !Double.isFinite(bands.bCutoff())
                || bands.bCutoff() <= 0 || bands.aCutoff() <= bands.bCutoff()) {
            reasons.add("Bands need two positive cutoffs, the A cutoff above the B cutoff.");
        }
    }

    private static void checkWeights(PutawayWeights weights, List<String> reasons) {
        if (weights == null) {
            reasons.add("weights is required.");
            return;
        }
        checkNonNegative(weights.goldenWeight(), "goldenWeight", reasons);
        checkNonNegative(weights.lowWeight(), "lowWeight", reasons);
        checkNonNegative(weights.affinityWeight(), "affinityWeight", reasons);
        checkNonNegative(weights.ergoGolden(), "ergoGolden", reasons);
        checkNonNegative(weights.ergoFloor(), "ergoFloor", reasons);
        checkNonNegative(weights.ergoBeam(), "ergoBeam", reasons);
        if (!Double.isFinite(weights.affinityDecayBays()) || weights.affinityDecayBays() <= 0) {
            reasons.add("affinityDecayBays must be greater than zero.");
        }
        if (!Double.isFinite(weights.heavyKg()) || weights.heavyKg() <= 0) {
            reasons.add("heavyKg must be greater than zero.");
        }
        if (weights.maxLevelForHeavy() < 1) {
            reasons.add("maxLevelForHeavy must be at least one.");
        }
    }

    private static void checkForward(PutawayDay day, List<String> reasons) {
        ForwardPlan forward = day.forward();
        if (forward == null) {
            reasons.add("forward is required.");
            return;
        }
        for (VelocityClass band : VelocityClass.values()) {
            Threshold threshold = forward.thresholds().get(band);
            if (threshold == null) {
                reasons.add("The day needs a threshold for band " + band + ".");
                continue;
            }
            if (!Double.isFinite(threshold.minCartons()) || threshold.minCartons() <= 0) {
                reasons.add("The " + band + " threshold needs a minCartons greater than zero.");
            }
            if (!Double.isFinite(threshold.maxCartons()) || threshold.maxCartons() <= 0) {
                reasons.add("The " + band + " threshold needs a maxCartons greater than zero.");
            }
            if (!Double.isFinite(threshold.hysteresisCartons()) || threshold.hysteresisCartons() < 0) {
                reasons.add("The " + band + " threshold needs a hysteresisCartons of zero or greater.");
            }
            if (Double.isFinite(threshold.minCartons()) && Double.isFinite(threshold.maxCartons())
                    && Double.isFinite(threshold.hysteresisCartons())
                    && threshold.maxCartons() < threshold.minCartons() + threshold.hysteresisCartons()) {
                reasons.add("The " + band + " threshold needs maxCartons at or above minCartons plus its hysteresis.");
            }
        }
        for (Map.Entry<String, Integer> entry : forward.onHand().entrySet()) {
            Sku sku = day.sku(entry.getKey()).orElse(null);
            if (sku == null) {
                reasons.add("The forward plan names SKU " + entry.getKey() + ", which is not in the catalog.");
                continue;
            }
            if (entry.getValue() == null || entry.getValue() < 0) {
                reasons.add(sku.name() + " cannot start the day below zero on the face.");
            }
        }
        LinkedHashMap<String, Integer> projected = new LinkedHashMap<>(forward.onHand());
        int index = 0;
        for (StockMove move : forward.openings()) {
            index++;
            Sku sku = day.sku(move.skuId()).orElse(null);
            if (sku == null) {
                reasons.add("Opening " + index + " names SKU " + move.skuId() + ", which is not in the catalog.");
                continue;
            }
            if (move.cartons() == 0) {
                reasons.add("Opening " + index + " moves zero cartons of " + sku.name() + ".");
                continue;
            }
            int before = projected.getOrDefault(move.skuId(), 0);
            int after = before + move.cartons();
            if (after < 0) {
                reasons.add("Opening " + index + " leaves " + sku.name() + " below zero.");
                continue;
            }
            projected.put(move.skuId(), after);
        }
    }

    private static void checkStartState(PutawayDay day, List<String> reasons) {
        for (Map.Entry<String, SlotFill> entry : day.startState().entrySet()) {
            Slot slot = day.slot(entry.getKey()).orElse(null);
            if (slot == null) {
                reasons.add("Bay " + entry.getKey() + " is not on the reserve rack.");
                continue;
            }
            SlotFill fill = entry.getValue();
            if (fill == null || fill.skuId() == null || day.sku(fill.skuId()).isEmpty()) {
                reasons.add("Bay " + entry.getKey() + " starts with a SKU that is not in the catalog.");
                continue;
            }
            Sku sku = day.sku(fill.skuId()).orElseThrow();
            if (fill.cartons() < 1) {
                reasons.add("Bay " + entry.getKey() + " starts with " + fill.cartons() + " cartons of " + sku.name() + ".");
                continue;
            }
            if (!zonesMatch(sku, slot)) {
                reasons.add("Bay " + entry.getKey() + " starts with " + sku.name() + ", whose zone does not match.");
                continue;
            }
            double liters = fill.cartons() * sku.cubeLiters();
            if (liters > slot.cubeLiters() + 1.0e-9) {
                reasons.add("Bay " + entry.getKey() + " starts over its cube: " + Math.round(liters * 10) / 10.0
                        + " liters of " + sku.name() + " in a " + slot.cubeLiters() + " liter bay.");
            }
        }
    }

    private static void checkNonNegative(double value, String name, List<String> reasons) {
        if (!Double.isFinite(value) || value < 0) {
            reasons.add(name + " must be zero or greater.");
        }
    }
}
