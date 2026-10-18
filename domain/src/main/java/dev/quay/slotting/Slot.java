package dev.quay.slotting;

import java.util.Objects;
import java.util.Optional;

/** One storage position on a pick face. Travel and ergonomics are properties of the position. */
public record Slot(
        String id,
        String aisle,
        int aisleIndex,
        int bay,
        int bayIndex,
        int level,
        double cubeLiters,
        double returnMeters,
        Ergonomics ergonomics,
        Optional<String> zone) {

    public Slot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(aisle, "aisle");
        Objects.requireNonNull(ergonomics, "ergonomics");
        zone = zone == null ? Optional.empty() : zone;
    }
}
