package dev.quay.slotting;

import java.util.Objects;

/**
 * One opening move on the forward face, in cartons, signed. A negative move is the
 * pick run that emptied the shelf; a positive move is stock that came back.
 * A move that would leave a face below zero is refused.
 */
public record StockMove(String skuId, int cartons) {

    public StockMove {
        Objects.requireNonNull(skuId, "skuId");
    }
}
