package dev.quay.slotting;

import java.util.Objects;

/**
 * One entry in the replenishment queue the putaway emits to the work side.
 * A raise carries the cartons the face asked for; a quiet releases the signal.
 */
public record ReplenishSignal(String skuId, double onHand, String action, int cartons) {

    public static final String RAISE = "raise";
    public static final String QUIET = "quiet";

    public ReplenishSignal {
        Objects.requireNonNull(skuId, "skuId");
        Objects.requireNonNull(action, "action");
    }
}
