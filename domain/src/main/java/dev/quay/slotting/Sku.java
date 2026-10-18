package dev.quay.slotting;

import java.util.Objects;
import java.util.Optional;

/** A carton that needs one slot. Picks are the daily forecast the objective cares about. */
public record Sku(
        String id,
        String name,
        double picksPerDay,
        double cubeLiters,
        double weightKg,
        Optional<String> zone) {

    public Sku {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        zone = zone == null ? Optional.empty() : zone;
    }
}
