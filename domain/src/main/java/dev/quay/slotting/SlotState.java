package dev.quay.slotting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The bays that are carrying stock. Immutable: every accepted directive returns a new
 * state. The refusals the state names are the reason a directive does not land.
 */
public record SlotState(Map<String, SlotFill> fills) {

    public SlotState {
        Objects.requireNonNull(fills, "fills");
        fills = Map.copyOf(fills);
    }

    public static SlotState of(Map<String, SlotFill> fills) {
        return new SlotState(new LinkedHashMap<>(fills));
    }

    public static SlotState empty() {
        return new SlotState(new LinkedHashMap<>());
    }

    public Optional<SlotFill> fill(String slotId) {
        return Optional.ofNullable(fills.get(slotId));
    }

    /**
     * Why this landing cannot go into this bay, or {@code null} when it can.
     * The message is written for the fork: the bay is spoken to by id, the
     * cartons by name, and the liters are what is actually missing.
     */
    public String refusal(Slot slot, Sku sku, int cartons, PutawayWeights weights) {
        if (cartons <= 0) {
            return sku.name() + " has no cartons to put away.";
        }
        if (!PutawayRules.zonesMatch(sku, slot)) {
            return slot.id() + " is zoned " + slot.zone().orElse("open") + ", and " + sku.name()
                    + " ships to " + sku.zone().orElse("no zone") + ".";
        }
        SlotFill fill = fills.get(slot.id());
        if (fill != null && !fill.skuId().equals(sku.id())) {
            return slot.id() + " already holds " + fill.skuId() + "; a bay carries one SKU.";
        }
        int holding = fill == null ? 0 : fill.cartons();
        double needed = cartons * sku.cubeLiters();
        double free = slot.cubeLiters() - holding * sku.cubeLiters();
        if (needed > free + 1.0e-9) {
            return slot.id() + " has " + Math.round(free * 10) / 10.0
                    + " liters free, and " + cartons + " cartons of " + sku.name()
                    + " take " + Math.round(needed * 10) / 10.0 + ".";
        }
        if (sku.weightKg() >= weights.heavyKg() && slot.level() > weights.maxLevelForHeavy()) {
            return sku.name() + " cannot claim a level above " + weights.maxLevelForHeavy() + ".";
        }
        return null;
    }

    public SlotState put(String slotId, String skuId, int cartons) {
        SlotFill fill = fills.get(slotId);
        LinkedHashMap<String, SlotFill> next = new LinkedHashMap<>(fills);
        int holding = fill == null ? 0 : fill.cartons();
        next.put(slotId, new SlotFill(skuId, holding + cartons));
        return new SlotState(next);
    }
}
