package dev.quay.slotting;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The forward face, its thresholds, and the openings that start the day. */
public record ForwardPlan(
        Map<String, Integer> onHand,
        Map<VelocityClass, Threshold> thresholds,
        List<StockMove> openings) {

    public ForwardPlan {
        Objects.requireNonNull(onHand, "onHand");
        Objects.requireNonNull(thresholds, "thresholds");
        openings = List.copyOf(openings);
        onHand = Map.copyOf(onHand);
        thresholds = Map.copyOf(thresholds);
    }
}
