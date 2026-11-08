package dev.quay.slotting;

/** What one policy did with the day, in counts a planner can audit. */
public record PutawayTotals(
        int receipts,
        int refused,
        int cartons,
        int cartonsForward,
        int cartonsReserve,
        double goldenCartons,
        double putawayMeters,
        int baysTouched) {

    public double goldenShare() {
        return cartonsReserve > 0 ? goldenCartons / cartonsReserve : 0;
    }
}
