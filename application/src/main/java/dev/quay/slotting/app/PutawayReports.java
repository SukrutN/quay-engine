package dev.quay.slotting.app;

import dev.quay.slotting.PutawayDay;
import dev.quay.slotting.PutawayResult;
import java.util.ArrayList;
import java.util.List;

final class PutawayReports {

    private PutawayReports() {}

    static PutawayReport create(PutawayDay day, List<PutawayResult> results, long elapsedMillis) {
        List<PutawayReport.Outcome> outcomes = new ArrayList<>();
        for (PutawayResult result : results) {
            outcomes.add(new PutawayReport.Outcome(
                    result.policy(),
                    rows(result),
                    refusals(result),
                    signals(result),
                    totals(result)));
        }
        return new PutawayReport(day.id(), day.name(), elapsedMillis, List.copyOf(outcomes));
    }

    private static List<PutawayReport.DirectiveRow> rows(PutawayResult result) {
        return result.directives().stream()
                .map(directive -> new PutawayReport.DirectiveRow(
                        directive.receiptId(),
                        directive.skuId(),
                        directive.skuName(),
                        directive.band().name(),
                        directive.destination(),
                        directive.units(),
                        directive.uom(),
                        directive.cartons(),
                        directive.slotId(),
                        directive.putawayMeters()))
                .toList();
    }

    private static List<PutawayReport.RefusalRow> refusals(PutawayResult result) {
        return result.refusals().stream()
                .map(refusal -> new PutawayReport.RefusalRow(refusal.receiptId(), refusal.reason()))
                .toList();
    }

    private static List<PutawayReport.SignalRow> signals(PutawayResult result) {
        return result.signals().stream()
                .map(signal -> new PutawayReport.SignalRow(
                        signal.skuId(), signal.onHand(), signal.action(), signal.cartons()))
                .toList();
    }

    private static PutawayReport.Totals totals(PutawayResult result) {
        return new PutawayReport.Totals(
                result.totals().receipts(),
                result.totals().refused(),
                result.totals().cartons(),
                result.totals().cartonsForward(),
                result.totals().cartonsReserve(),
                result.totals().goldenShare(),
                result.totals().putawayMeters(),
                result.totals().baysTouched());
    }
}
