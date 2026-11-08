package dev.quay.slotting;

import java.util.Objects;

/** The cartons one SKU holds in one bay. One SKU owns a bay; more of the same may top it off. */
public record SlotFill(String skuId, int cartons) {

    public SlotFill {
        Objects.requireNonNull(skuId, "skuId");
    }
}
