package dev.quay.slotting;

import java.util.Objects;

/**
 * One directive row: a manifest line either lands in a named bay, or goes to the
 * forward face because the shelf there asked for it. The meters are one-way
 * minutes of fork travel for the reserve leg; the forward leg is the dock mouth.
 */
public record PutawayDirective(
        String receiptId,
        String skuId,
        String skuName,
        int units,
        String uom,
        int cartons,
        VelocityClass band,
        String destination,
        String slotId,
        double putawayMeters) {

    public static final String FORWARD = "forward";
    public static final String RESERVE = "reserve";

    public PutawayDirective {
        Objects.requireNonNull(receiptId, "receiptId");
        Objects.requireNonNull(skuId, "skuId");
        Objects.requireNonNull(skuName, "skuName");
        Objects.requireNonNull(uom, "uom");
        Objects.requireNonNull(band, "band");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(slotId, "slotId");
    }
}
