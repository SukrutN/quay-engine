package dev.quay.slotting;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** The whole input of one slotting run. The solver does not keep a building of its own. */
public final class PickFace {

    private final String id;
    private final String name;
    private final List<Slot> slots;
    private final List<Sku> skus;
    private final List<Affinity> affinities;
    private final Policy policy;
    private final Mode mode;
    private final Map<String, String> current;
    private final Map<String, String> locked;
    private final Map<String, Slot> slotsById;
    private final Map<String, Sku> skusById;

    public PickFace(
            String id,
            String name,
            List<Slot> slots,
            List<Sku> skus,
            List<Affinity> affinities,
            Policy policy,
            Mode mode,
            Map<String, String> current,
            Map<String, String> locked) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.slots = List.copyOf(slots);
        this.skus = List.copyOf(skus);
        this.affinities = List.copyOf(affinities);
        this.policy = Objects.requireNonNull(policy, "policy");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.current = Map.copyOf(current);
        this.locked = Map.copyOf(locked);
        this.slotsById = indexSlots(this.slots);
        this.skusById = indexSkus(this.skus);
    }

    private static Map<String, Slot> indexSlots(Collection<Slot> slots) {
        Map<String, Slot> indexed = new LinkedHashMap<>();
        for (Slot slot : slots) {
            indexed.putIfAbsent(slot.id(), slot);
        }
        return Map.copyOf(indexed);
    }

    private static Map<String, Sku> indexSkus(Collection<Sku> skus) {
        Map<String, Sku> indexed = new LinkedHashMap<>();
        for (Sku sku : skus) {
            indexed.putIfAbsent(sku.id(), sku);
        }
        return Map.copyOf(indexed);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<Slot> slots() {
        return slots;
    }

    public List<Sku> skus() {
        return skus;
    }

    public List<Affinity> affinities() {
        return affinities;
    }

    public Policy policy() {
        return policy;
    }

    public Mode mode() {
        return mode;
    }

    /** Slot id to SKU id. Empty when the caller did not send an incumbent layout. */
    public Map<String, String> current() {
        return current;
    }

    /** SKU id to slot id. Those SKUs stay where they are. */
    public Map<String, String> locked() {
        return locked;
    }

    public boolean hasCurrent() {
        return !current.isEmpty();
    }

    public Optional<Slot> slot(String id) {
        return Optional.ofNullable(slotsById.get(id));
    }

    public Optional<Sku> sku(String id) {
        return Optional.ofNullable(skusById.get(id));
    }

    public Map<String, Slot> slotsById() {
        return slotsById;
    }

    public Map<String, Sku> skusById() {
        return skusById;
    }
}
