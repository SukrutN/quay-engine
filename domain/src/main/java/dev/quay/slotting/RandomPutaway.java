package dev.quay.slotting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The baseline the two directed policies are measured against: the fork reads the
 * manifest in arrival order and takes the next bay a fixed shuffle hands it. The
 * seed is data, so the same day draws the same bays every run. Bays that cannot
 * take a line are skipped, and a line with no bay left is refused.
 */
public final class RandomPutaway implements PutawayPolicy {

    public static final String NAME = "random";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PutawayResult run(PutawayDay day) {
        List<Slot> shuffled = new ArrayList<>(day.slots());
        Collections.shuffle(shuffled, new Random(day.seed()));
        return PutawayCore.run(NAME, day, day.receipts(), (state, d, sku, cartons, landing) -> {
            for (Slot slot : shuffled) {
                if (PutawayRules.fits(state, slot, sku, cartons, d.weights())) {
                    return slot;
                }
            }
            return null;
        });
    }
}
