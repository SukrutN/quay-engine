package dev.quay.slotting;

import java.util.Optional;

/**
 * One way to direct a day's receipts. The engine answers for three: velocity, affinity,
 * and the seeded random fork. Every policy answers for every manifest line; the slot
 * state refuses what does not fit.
 */
public interface PutawayPolicy {

    String name();

    PutawayResult run(PutawayDay day);

    /** The policy a caller named, if the engine ships it. */
    static Optional<PutawayPolicy> named(String name) {
        return switch (name) {
            case "velocity" -> Optional.of(new VelocityPutaway());
            case "affinity" -> Optional.of(new AffinityPutaway());
            case "random" -> Optional.of(new RandomPutaway());
            default -> Optional.empty();
        };
    }
}
