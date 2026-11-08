package dev.quay.slotting;

import java.util.Objects;

/** A manifest line the day did not place, and the reason. */
public record DirectiveRefusal(String receiptId, String reason) {

    public DirectiveRefusal {
        Objects.requireNonNull(receiptId, "receiptId");
        Objects.requireNonNull(reason, "reason");
    }
}
