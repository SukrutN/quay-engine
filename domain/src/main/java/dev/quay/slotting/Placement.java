package dev.quay.slotting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A complete or partial assignment of slots to SKUs. Instances are immutable. */
public final class Placement {

    private final Map<String, String> slotToSku;
    private final Map<String, String> skuToSlot;

    private Placement(Map<String, String> slotToSku, Map<String, String> skuToSlot) {
        this.slotToSku = Map.copyOf(slotToSku);
        this.skuToSlot = Map.copyOf(skuToSlot);
    }

    public static Placement empty() {
        return new Placement(Map.of(), Map.of());
    }

    public static Placement ofComplete(Map<String, String> slotToSku) {
        Placement placement = empty();
        for (Map.Entry<String, String> entry : slotToSku.entrySet()) {
            placement = placement.assign(entry.getKey(), entry.getValue());
        }
        return placement;
    }

    public Map<String, String> slotToSku() {
        return slotToSku;
    }

    public Optional<String> skuAt(String slotId) {
        return Optional.ofNullable(slotToSku.get(slotId));
    }

    public Optional<String> slotOf(String skuId) {
        return Optional.ofNullable(skuToSlot.get(skuId));
    }

    public Placement assign(String slotId, String skuId) {
        if (slotToSku.containsKey(slotId) || skuToSlot.containsKey(skuId)) {
            throw new IllegalStateException("slot or SKU is already assigned");
        }
        Map<String, String> slots = new LinkedHashMap<>(slotToSku);
        Map<String, String> skus = new LinkedHashMap<>(skuToSlot);
        slots.put(slotId, skuId);
        skus.put(skuId, slotId);
        return new Placement(slots, skus);
    }

    public Placement move(String skuId, String toSlotId) {
        String from = skuToSlot.get(skuId);
        if (from == null) {
            throw new IllegalStateException("SKU is not placed");
        }
        if (slotToSku.containsKey(toSlotId)) {
            throw new IllegalStateException("slot is occupied");
        }
        Map<String, String> slots = new LinkedHashMap<>(slotToSku);
        Map<String, String> skus = new LinkedHashMap<>(skuToSlot);
        slots.remove(from);
        slots.put(toSlotId, skuId);
        skus.put(skuId, toSlotId);
        return new Placement(slots, skus);
    }

    public Placement swap(String skuA, String skuB) {
        String slotA = skuToSlot.get(skuA);
        String slotB = skuToSlot.get(skuB);
        if (slotA == null || slotB == null) {
            throw new IllegalStateException("both SKUs must be placed");
        }
        Map<String, String> slots = new LinkedHashMap<>(slotToSku);
        Map<String, String> skus = new LinkedHashMap<>(skuToSlot);
        slots.put(slotA, skuB);
        slots.put(slotB, skuA);
        skus.put(skuA, slotB);
        skus.put(skuB, slotA);
        return new Placement(slots, skus);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Placement placement)) {
            return false;
        }
        return slotToSku.equals(placement.slotToSku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slotToSku);
    }
}
