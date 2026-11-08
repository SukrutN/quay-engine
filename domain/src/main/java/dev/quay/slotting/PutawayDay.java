package dev.quay.slotting;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The whole input of one putaway day: the carrier manifest, the reserve rack, the
 * forward face it feeds, and the weights the policies score. The solver does not
 * keep a warehouse of its own.
 */
public final class PutawayDay {

    private final String id;
    private final String name;
    private final List<Slot> slots;
    private final List<Sku> catalog;
    private final List<Affinity> affinities;
    private final Map<String, Map<String, Double>> uoms;
    private final List<Receipt> receipts;
    private final Banding bands;
    private final PutawayWeights weights;
    private final ForwardPlan forward;
    private final Map<String, SlotFill> startState;
    private final int seed;
    private final Map<String, Slot> slotsById;
    private final Map<String, Sku> catalogById;

    public PutawayDay(
            String id,
            String name,
            List<Slot> slots,
            List<Sku> catalog,
            List<Affinity> affinities,
            Map<String, Map<String, Double>> uoms,
            List<Receipt> receipts,
            Banding bands,
            PutawayWeights weights,
            ForwardPlan forward,
            Map<String, SlotFill> startState,
            int seed) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.slots = List.copyOf(slots);
        this.catalog = List.copyOf(catalog);
        this.affinities = List.copyOf(affinities);
        this.uoms = copyUoms(uoms);
        this.receipts = List.copyOf(receipts);
        this.bands = Objects.requireNonNull(bands, "bands");
        this.weights = Objects.requireNonNull(weights, "weights");
        this.forward = Objects.requireNonNull(forward, "forward");
        this.startState = Map.copyOf(startState);
        this.seed = seed;
        this.slotsById = index(slots, Slot::id);
        this.catalogById = index(catalog, Sku::id);
    }

    private static Map<String, Map<String, Double>> copyUoms(Map<String, Map<String, Double>> uoms) {
        Objects.requireNonNull(uoms, "uoms");
        LinkedHashMap<String, Map<String, Double>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Double>> entry : uoms.entrySet()) {
            copied.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Map.copyOf(copied);
    }

    private static <T> Map<String, T> index(Collection<T> values, java.util.function.Function<T, String> key) {
        LinkedHashMap<String, T> indexed = new LinkedHashMap<>();
        for (T value : values) {
            indexed.putIfAbsent(key.apply(value), value);
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

    public List<Sku> catalog() {
        return catalog;
    }

    public List<Affinity> affinities() {
        return affinities;
    }

    public Map<String, Map<String, Double>> uoms() {
        return uoms;
    }

    public List<Receipt> receipts() {
        return receipts;
    }

    public Banding bands() {
        return bands;
    }

    public PutawayWeights weights() {
        return weights;
    }

    public ForwardPlan forward() {
        return forward;
    }

    public Map<String, SlotFill> startState() {
        return startState;
    }

    public int seed() {
        return seed;
    }

    public Map<String, Slot> slotsById() {
        return slotsById;
    }

    public Map<String, Sku> catalogById() {
        return catalogById;
    }

    public Optional<Slot> slot(String id) {
        return Optional.ofNullable(slotsById.get(id));
    }

    public Optional<Sku> sku(String id) {
        return Optional.ofNullable(catalogById.get(id));
    }

    /**
     * The cartons one receipt is asking for. "carton" is the base unit everywhere;
     * every other unit of measure has to be listed on the SKU with the cartons one
     * of them contains.
     */
    public Optional<Integer> cartonsOf(Receipt receipt) {
        double conversion = "carton".equals(receipt.uom())
                ? 1.0
                : uoms.getOrDefault(receipt.skuId(), Map.of()).getOrDefault(receipt.uom(), Double.NaN);
        if (!Double.isFinite(conversion) || conversion <= 0) {
            return Optional.empty();
        }
        double cartons = receipt.units() * conversion;
        if (Math.abs(cartons - Math.rint(cartons)) > 1.0e-9) {
            return Optional.empty();
        }
        return Optional.of((int) Math.rint(cartons));
    }
}
