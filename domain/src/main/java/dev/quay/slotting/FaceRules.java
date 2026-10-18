package dev.quay.slotting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Document rules. A face that fails here is not slotted. */
public final class FaceRules {

    private FaceRules() {}

    public static void check(PickFace face) {
        List<String> reasons = new ArrayList<>();
        checkPolicy(face.policy(), reasons);
        checkSlots(face, reasons);
        checkSkus(face, reasons);
        checkAffinities(face, reasons);
        checkLocks(face, reasons);
        checkCurrent(face, reasons);
        if (face.mode() == Mode.IMPROVE && !face.hasCurrent()) {
            reasons.add("Improve mode needs a current assignment of every SKU.");
        }
        if (!reasons.isEmpty()) {
            throw new FaceRejection(reasons);
        }
    }

    public static boolean fits(PickFace face, Sku sku, Slot slot) {
        if (!(sku.cubeLiters() > 0) || sku.cubeLiters() > slot.cubeLiters()) {
            return false;
        }
        if (sku.weightKg() >= face.policy().heavyKg() && slot.level() > face.policy().maxLevelForHeavy()) {
            return false;
        }
        return zonesMatch(sku, slot);
    }

    static boolean zonesMatch(Sku sku, Slot slot) {
        if (sku.zone().isPresent() != slot.zone().isPresent()) {
            return false;
        }
        return sku.zone().equals(slot.zone());
    }

    private static void checkPolicy(Policy policy, List<String> reasons) {
        requireWeight(policy.travelWeight(), "travelWeight", reasons);
        requireWeight(policy.wasteWeight(), "wasteWeight", reasons);
        requireWeight(policy.affinityWeight(), "affinityWeight", reasons);
        requireWeight(policy.goldenWeight(), "goldenWeight", reasons);
        requireWeight(policy.lightFloorWeight(), "lightFloorWeight", reasons);
        if (!Double.isFinite(policy.slowPickCutoff()) || policy.slowPickCutoff() < 0) {
            reasons.add("slowPickCutoff must be zero or greater.");
        }
        if (!Double.isFinite(policy.lightKg()) || policy.lightKg() < 0) {
            reasons.add("lightKg must be zero or greater.");
        }
        if (!Double.isFinite(policy.heavyKg()) || policy.heavyKg() <= policy.lightKg()) {
            reasons.add("heavyKg must be greater than lightKg.");
        }
        requirePositive(policy.ergoGolden(), "ergoGolden", reasons);
        requirePositive(policy.ergoFloor(), "ergoFloor", reasons);
        requirePositive(policy.ergoBeam(), "ergoBeam", reasons);
        if (!Double.isFinite(policy.affinityDecayBays()) || policy.affinityDecayBays() <= 0) {
            reasons.add("affinityDecayBays must be greater than zero.");
        }
        if (policy.maxPasses() < 0) {
            reasons.add("maxPasses must be zero or greater.");
        }
    }

    private static void checkSlots(PickFace face, List<String> reasons) {
        if (face.slots().isEmpty()) {
            reasons.add("The face has no slots.");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (Slot slot : face.slots()) {
            if (slot.id().isBlank()) {
                reasons.add("A slot is missing an id.");
            } else if (!seen.add(slot.id())) {
                reasons.add("Slot " + slot.id() + " is listed twice.");
            }
            if (slot.aisle().isBlank()) {
                reasons.add("Slot " + slot.id() + " is missing an aisle.");
            }
            if (slot.aisleIndex() < 0 || slot.bayIndex() < 0) {
                reasons.add("Slot " + slot.id() + " has a negative aisle or bay index.");
            }
            if (!Double.isFinite(slot.cubeLiters()) || slot.cubeLiters() <= 0) {
                reasons.add("Slot " + slot.id() + " needs a positive cube.");
            }
            if (!Double.isFinite(slot.returnMeters()) || slot.returnMeters() < 0) {
                reasons.add("Slot " + slot.id() + " needs a return distance of zero or greater.");
            }
            if (slot.zone().filter(String::isBlank).isPresent()) {
                reasons.add("Slot " + slot.id() + " has a blank zone. Omit the zone or name one.");
            }
        }
    }

    private static void checkSkus(PickFace face, List<String> reasons) {
        if (face.skus().isEmpty()) {
            reasons.add("The face has no SKUs.");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (Sku sku : face.skus()) {
            if (sku.id().isBlank()) {
                reasons.add("A SKU is missing an id.");
                continue;
            }
            if (!seen.add(sku.id())) {
                reasons.add("SKU " + sku.id() + " is listed twice.");
            }
            if (sku.name().isBlank()) {
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
            } else if (face.slots().stream().noneMatch(slot -> fits(face, sku, slot))) {
                reasons.add(sku.name() + " has no slot that accepts its cube, weight, and zone.");
            }
            if (sku.zone().filter(String::isBlank).isPresent()) {
                reasons.add(sku.name() + " has a blank zone. Omit the zone or name one.");
            }
        }
    }

    private static void checkAffinities(PickFace face, List<String> reasons) {
        Set<String> seen = new HashSet<>();
        for (Affinity affinity : face.affinities()) {
            if (face.sku(affinity.skuA()).isEmpty() || face.sku(affinity.skuB()).isEmpty()) {
                reasons.add("Affinity " + affinity.skuA() + " / " + affinity.skuB() + " names a SKU that is not on the face.");
            }
            if (affinity.skuA().equals(affinity.skuB())) {
                reasons.add("Affinity " + affinity.skuA() + " points at itself.");
            }
            if (!Double.isFinite(affinity.strength()) || affinity.strength() <= 0 || affinity.strength() > 1) {
                reasons.add("Affinity " + affinity.skuA() + " / " + affinity.skuB() + " needs a strength between 0 and 1.");
            }
            String key = pairKey(affinity.skuA(), affinity.skuB());
            if (!seen.add(key)) {
                reasons.add("Affinity " + affinity.skuA() + " / " + affinity.skuB() + " is listed twice.");
            }
        }
    }

    private static void checkLocks(PickFace face, List<String> reasons) {
        Set<String> slots = new HashSet<>();
        for (Map.Entry<String, String> entry : face.locked().entrySet()) {
            Sku sku = face.sku(entry.getKey()).orElse(null);
            Slot slot = face.slot(entry.getValue()).orElse(null);
            if (sku == null) {
                reasons.add("Lock names unknown SKU " + entry.getKey() + ".");
                continue;
            }
            if (slot == null) {
                reasons.add(sku.name() + " is locked to unknown slot " + entry.getValue() + ".");
                continue;
            }
            if (!slots.add(slot.id())) {
                reasons.add("Slot " + slot.id() + " is locked to more than one SKU.");
            }
            if (!fits(face, sku, slot)) {
                reasons.add(sku.name() + " is locked to " + slot.id() + ", which cannot hold it.");
            }
        }
    }

    private static void checkCurrent(PickFace face, List<String> reasons) {
        if (!face.hasCurrent()) {
            return;
        }
        Set<String> seenSkus = new HashSet<>();
        for (Map.Entry<String, String> entry : face.current().entrySet()) {
            Slot slot = face.slot(entry.getKey()).orElse(null);
            Sku sku = face.sku(entry.getValue()).orElse(null);
            if (slot == null) {
                reasons.add("Current layout names unknown slot " + entry.getKey() + ".");
                continue;
            }
            if (sku == null) {
                reasons.add("Current layout names unknown SKU " + entry.getValue() + ".");
                continue;
            }
            if (!seenSkus.add(sku.id())) {
                reasons.add(sku.name() + " is assigned twice in the current layout.");
            }
            if (!fits(face, sku, slot)) {
                reasons.add(sku.name() + " cannot stand in current slot " + slot.id() + ".");
            }
            String lockedSlot = face.locked().get(sku.id());
            if (lockedSlot != null && !lockedSlot.equals(slot.id())) {
                reasons.add(sku.name() + " is locked to " + lockedSlot + " but the current layout has it in " + slot.id() + ".");
            }
        }
        if (seenSkus.size() != face.skus().size()) {
            for (Sku sku : face.skus()) {
                if (!seenSkus.contains(sku.id())) {
                    reasons.add("Current layout is missing " + sku.name() + ".");
                }
            }
        }
    }

    private static String pairKey(String left, String right) {
        if (left.compareTo(right) <= 0) {
            return left + "\0" + right;
        }
        return right + "\0" + left;
    }

    private static void requireWeight(double value, String name, List<String> reasons) {
        if (!Double.isFinite(value) || value < 0) {
            reasons.add(name + " must be zero or greater.");
        }
    }

    private static void requirePositive(double value, String name, List<String> reasons) {
        if (!Double.isFinite(value) || value <= 0) {
            reasons.add(name + " must be greater than zero.");
        }
    }
}
