package dev.quay.slotting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Places every SKU, then relocates and swaps while the objective falls.
 * Velocity order is picks per day, highest first, and SKU id breaks ties.
 * Equal scores keep the slot id that sorts first.
 */
public final class Slotter {

    private static final double TIE = 1.0e-9;

    public Slotting slot(PickFace face) {
        FaceRules.check(face);
        Placement built = switch (face.mode()) {
            case REPLAN -> construct(face);
            case IMPROVE -> Placement.ofComplete(face.current());
        };
        Placement improved = improve(face, built);
        return new Slotting(improved, Measurements.measure(face, improved), Objective.assess(face, improved));
    }

    private static Placement construct(PickFace face) {
        Placement placement = Placement.empty();
        Set<String> lockedIds = new HashSet<>();
        List<String> lockSkus = new ArrayList<>(face.locked().keySet());
        lockSkus.sort(Comparator.naturalOrder());
        for (String skuId : lockSkus) {
            placement = placement.assign(face.locked().get(skuId), skuId);
            lockedIds.add(skuId);
        }
        List<Sku> order = new ArrayList<>(face.skus());
        order.sort(Comparator.comparingDouble(Sku::picksPerDay).reversed().thenComparing(Sku::id));
        List<Slot> slots = sortedSlots(face);
        for (Sku sku : order) {
            if (lockedIds.contains(sku.id())) {
                continue;
            }
            String bestId = null;
            double best = Double.POSITIVE_INFINITY;
            for (Slot slot : slots) {
                if (placement.skuAt(slot.id()).isPresent() || !FaceRules.fits(face, sku, slot)) {
                    continue;
                }
                double score = Objective.total(face, placement.assign(slot.id(), sku.id()));
                if (bestId == null || score < best - TIE || (Math.abs(score - best) <= TIE && slot.id().compareTo(bestId) < 0)) {
                    best = score;
                    bestId = slot.id();
                }
            }
            if (bestId == null) {
                throw new FaceRejection(List.of(sku.name() + " has nowhere left that can hold it."));
            }
            placement = placement.assign(bestId, sku.id());
        }
        return placement;
    }

    private static Placement improve(PickFace face, Placement start) {
        Placement current = start;
        double score = Objective.total(face, current);
        List<Sku> skus = new ArrayList<>(face.skus());
        skus.sort(Comparator.comparing(Sku::id));
        List<Slot> slots = sortedSlots(face);
        Set<String> locked = face.locked().keySet();
        for (int pass = 0; pass < face.policy().maxPasses(); pass++) {
            boolean changed = false;
            for (Sku sku : skus) {
                if (locked.contains(sku.id())) {
                    continue;
                }
                String fromId = current.slotOf(sku.id()).orElseThrow();
                String bestSlot = fromId;
                double best = score;
                for (Slot candidate : slots) {
                    if (candidate.id().equals(fromId) || !FaceRules.fits(face, sku, candidate)) {
                        continue;
                    }
                    Optional<String> occupant = current.skuAt(candidate.id());
                    Placement trial;
                    if (occupant.isEmpty()) {
                        trial = current.move(sku.id(), candidate.id());
                    } else {
                        if (locked.contains(occupant.get())) {
                            continue;
                        }
                        Sku other = face.sku(occupant.get()).orElseThrow();
                        Slot from = face.slot(fromId).orElseThrow();
                        if (!FaceRules.fits(face, other, from)) {
                            continue;
                        }
                        trial = current.swap(sku.id(), occupant.get());
                    }
                    double next = Objective.total(face, trial);
                    if (next < best - TIE) {
                        best = next;
                        bestSlot = candidate.id();
                    }
                }
                if (!bestSlot.equals(fromId)) {
                    Optional<String> occupant = current.skuAt(bestSlot);
                    current = occupant.isEmpty()
                            ? current.move(sku.id(), bestSlot)
                            : current.swap(sku.id(), occupant.get());
                    score = best;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return current;
    }

    private static List<Slot> sortedSlots(PickFace face) {
        List<Slot> slots = new ArrayList<>(face.slots());
        slots.sort(Comparator.comparing(Slot::id));
        return slots;
    }
}
