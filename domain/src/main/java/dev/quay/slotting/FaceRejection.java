package dev.quay.slotting;

import java.util.List;

/** The document cannot be slotted. Reasons are safe to show to the caller. */
public final class FaceRejection extends RuntimeException {

    private final List<String> reasons;

    public FaceRejection(List<String> reasons) {
        super(String.join(" ", reasons));
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("a rejection needs at least one reason");
        }
        this.reasons = List.copyOf(reasons);
    }

    public List<String> reasons() {
        return reasons;
    }
}
