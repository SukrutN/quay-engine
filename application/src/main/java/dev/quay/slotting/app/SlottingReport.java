package dev.quay.slotting.app;

import dev.quay.slotting.CostBreakdown;
import dev.quay.slotting.FaceMetrics;
import java.util.List;

/** The response a caller can store. Raw fractions stay unrounded; the CLI does the rounding. */
public record SlottingReport(
        String faceId,
        String faceName,
        String mode,
        long elapsedMillis,
        Outcome proposal,
        Outcome current,
        Comparison comparison) {

    public record Outcome(List<AssignmentRow> assignment, FaceMetrics metrics, CostBreakdown cost) {}

    public record AssignmentRow(
            String slotId,
            String skuId,
            String skuName,
            int level,
            String ergonomics,
            String zone,
            double returnMeters,
            double cubeFit) {}

    public record Comparison(
            double travelPickMetersBefore,
            double travelPickMetersAfter,
            double travelChange,
            Double travelReduction,
            double objectiveBefore,
            double objectiveAfter,
            double objectiveChange) {}
}
