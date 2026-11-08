package dev.quay.slotting.app;

import java.util.List;
import java.util.Locale;

/** The reading a planner checks on the dock door. Rounding lives here; the API keeps full precision. */
public final class PutawayReading {

    private PutawayReading() {}

    public static String text(PutawayReport report) {
        StringBuilder out = new StringBuilder();
        out.append(report.dayName()).append(" (").append(report.dayId()).append(")\n");
        out.append(report.elapsedMillis()).append(" ms\n");
        for (PutawayReport.Outcome outcome : report.outcomes()) {
            out.append('\n');
            section(out, outcome);
        }
        comparison(out, report);
        return out.toString();
    }

    private static void section(StringBuilder out, PutawayReport.Outcome outcome) {
        PutawayReport.Totals totals = outcome.totals();
        out.append(outcome.policy()).append('\n');
        out.append(String.format(Locale.US,
                "%d manifest lines, %d refused · %d cartons to the face, %d to reserve across %d bays%n",
                totals.receipts(), totals.refused(), totals.cartonsForward(),
                totals.cartonsReserve(), totals.baysTouched()));
        out.append(String.format(Locale.US,
                "putaway walk %.1f m · golden-band share %.2f%n",
                totals.putawayMeters(), totals.goldenShare()));
        for (PutawayReport.SignalRow signal : outcome.signals()) {
            if (signal.action().equals("raise")) {
                out.append(String.format(Locale.US,
                        "%s raise at %.0f on hand, asks %d cartons%n",
                        signal.skuId(), signal.onHand(), signal.cartons()));
            } else {
                out.append(String.format(Locale.US, "%s quiet at %.0f on hand%n", signal.skuId(), signal.onHand()));
            }
        }
        for (PutawayReport.DirectiveRow row : outcome.directives()) {
            if (row.destination().equals("forward")) {
                out.append(String.format(Locale.US, "%s %s %s %d %s -> the face, %d cartons%n",
                        row.receiptId(), row.skuId(), row.skuName(), row.units(), row.uom(), row.cartons()));
            } else {
                out.append(String.format(Locale.US, "%s %s %s %d %s -> %s, %d cartons, %.1f m%n",
                        row.receiptId(), row.skuId(), row.skuName(), row.units(), row.uom(),
                        row.slotId(), row.cartons(), row.putawayMeters()));
            }
        }
        for (PutawayReport.RefusalRow refusal : outcome.refusals()) {
            out.append("refused ").append(refusal.receiptId()).append(": ").append(refusal.reason()).append('\n');
        }
    }

    private static void comparison(StringBuilder out, PutawayReport report) {
        List<PutawayReport.Outcome> outcomes = report.outcomes();
        if (outcomes.size() < 2) {
            return;
        }
        PutawayReport.Totals velocity = report.outcome("velocity").totals();
        PutawayReport.Totals affinity = report.outcome("affinity").totals();
        PutawayReport.Totals random = report.outcome("random").totals();
        double saving = random.putawayMeters() - velocity.putawayMeters();
        double percent = random.putawayMeters() > 0 ? 100 * saving / random.putawayMeters() : 0;
        out.append('\n');
        out.append(String.format(Locale.US,
                "directed against the fork: walk %.1f m against %.1f m - %.1f m less (%.1f%%); "
                        + "golden-band share %.2f against %.2f%n",
                velocity.putawayMeters(), random.putawayMeters(), saving, percent,
                velocity.goldenShare(), random.goldenShare()));
        out.append(String.format(Locale.US,
                "affinity against velocity: %+.1f m to walk its partners together; "
                        + "golden-band share %.2f against %.2f%n",
                affinity.putawayMeters() - velocity.putawayMeters(),
                affinity.goldenShare(), velocity.goldenShare()));
    }
}
