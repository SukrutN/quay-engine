package dev.quay.slotting;

import java.util.Objects;

/**
 * One manifest line. The units are whole counts of the named unit of measure; the
 * document's uom table says how many cartons one of those is.
 */
public record Receipt(String id, String skuId, int units, String uom) {

    public Receipt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(skuId, "skuId");
        Objects.requireNonNull(uom, "uom");
    }
}
