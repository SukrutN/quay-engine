package dev.quay.slotting.app;

import java.util.List;

/** The response a caller can store. Raw fractions stay unrounded; the CLI does the rounding. */
public record PutawayReport(
        String dayId,
        String dayName,
        long elapsedMillis,
        List<Outcome> outcomes) {

    public record Outcome(
            String policy,
            List<DirectiveRow> directives,
            List<RefusalRow> refusals,
            List<SignalRow> signals,
            Totals totals) {}

    public record DirectiveRow(
            String receiptId,
            String skuId,
            String skuName,
            String band,
            String destination,
            int units,
            String uom,
            int cartons,
            String slotId,
            double putawayMeters) {}

    public record RefusalRow(String receiptId, String reason) {}

    public record SignalRow(String skuId, double onHand, String action, int cartons) {}

    public record Totals(
            int receipts,
            int refused,
            int cartons,
            int cartonsForward,
            int cartonsReserve,
            double goldenShare,
            double putawayMeters,
            int baysTouched) {}

    public Outcome outcome(String policy) {
        return outcomes.stream()
                .filter(outcome -> outcome.policy().equals(policy))
                .findFirst()
                .orElseThrow();
    }
}
