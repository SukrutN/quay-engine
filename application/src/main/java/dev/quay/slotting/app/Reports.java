package dev.quay.slotting.app;

import dev.quay.slotting.CostBreakdown;
import dev.quay.slotting.FaceMetrics;
import dev.quay.slotting.Measurements;
import dev.quay.slotting.Objective;
import dev.quay.slotting.PickFace;
import dev.quay.slotting.Placement;
import dev.quay.slotting.Slot;
import dev.quay.slotting.Sku;
import dev.quay.slotting.Slotting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class Reports {

    private Reports() {}

    static SlottingReport create(PickFace face, Slotting proposal, long elapsedMillis) {
        SlottingReport.Outcome current = null;
        SlottingReport.Comparison comparison = null;
        if (face.hasCurrent()) {
            Placement incumbent = Placement.ofComplete(face.current());
            FaceMetrics metrics = Measurements.measure(face, incumbent);
            CostBreakdown cost = Objective.assess(face, incumbent);
            current = new SlottingReport.Outcome(rows(face, incumbent), metrics, cost);
            double before = metrics.travelPickMeters();
            double after = proposal.metrics().travelPickMeters();
            Double reduction = before > 0 ? (before - after) / before : null;
            comparison = new SlottingReport.Comparison(
                    before,
                    after,
                    after - before,
                    reduction,
                    cost.total(),
                    proposal.cost().total(),
                    proposal.cost().total() - cost.total());
        }
        return new SlottingReport(
                face.id(),
                face.name(),
                face.mode().name().toLowerCase(Locale.US),
                elapsedMillis,
                new SlottingReport.Outcome(rows(face, proposal.placement()), proposal.metrics(), proposal.cost()),
                current,
                comparison);
    }

    private static List<SlottingReport.AssignmentRow> rows(PickFace face, Placement placement) {
        List<SlottingReport.AssignmentRow> rows = new ArrayList<>();
        for (var entry : placement.slotToSku().entrySet()) {
            Slot slot = face.slot(entry.getKey()).orElseThrow();
            Sku sku = face.sku(entry.getValue()).orElseThrow();
            rows.add(new SlottingReport.AssignmentRow(
                    slot.id(),
                    sku.id(),
                    sku.name(),
                    slot.level(),
                    slot.ergonomics().name().toLowerCase(Locale.US),
                    slot.zone().orElse(null),
                    slot.returnMeters(),
                    sku.cubeLiters() / slot.cubeLiters()));
        }
        rows.sort(Comparator.comparing(SlottingReport.AssignmentRow::slotId));
        return List.copyOf(rows);
    }
}
