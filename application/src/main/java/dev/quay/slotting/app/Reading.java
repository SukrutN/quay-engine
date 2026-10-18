package dev.quay.slotting.app;

import dev.quay.slotting.FaceMetrics;
import java.util.Locale;

/** The reading a planner checks. Rounding lives here; the API keeps full precision. */
public final class Reading {

    private Reading() {}

    public static String text(SlottingReport report) {
        StringBuilder out = new StringBuilder();
        out.append(report.faceName()).append(" (").append(report.faceId()).append(")\n");
        out.append("mode ").append(report.mode()).append("\n");
        out.append(report.elapsedMillis()).append(" ms\n\n");
        section(out, "Proposal", report.proposal());
        if (report.current() != null) {
            out.append('\n');
            section(out, "Current", report.current());
        }
        out.append('\n');
        for (var row : report.proposal().assignment()) {
            out.append(String.format(Locale.US, "%-12s %-8s %s%n", row.slotId(), row.skuId(), row.skuName()));
        }
        if (report.comparison() != null) {
            out.append('\n').append(travelSentence(report.comparison())).append('\n');
        }
        return out.toString();
    }

    private static void section(StringBuilder out, String title, SlottingReport.Outcome outcome) {
        FaceMetrics metrics = outcome.metrics();
        out.append(title).append('\n');
        line(out, "travel", String.format(Locale.US, "%.1f pick-m", metrics.travelPickMeters()));
        line(out, "golden", String.format(Locale.US, "%d%%", Math.round(metrics.goldenPickShare() * 100)));
        line(out, "cube fit", String.format(Locale.US, "%d%%", Math.round(metrics.meanCubeFit() * 100)));
        line(out, "heavy above", Integer.toString(metrics.heavyAboveLimit()));
        line(out, "affinity", String.format(Locale.US, "%.1f steps", metrics.meanAffinitySteps()));
        line(out, "objective", String.format(Locale.US, "%.1f", outcome.cost().total()));
    }

    private static void line(StringBuilder out, String label, String value) {
        out.append(String.format(Locale.US, "  %-12s %s%n", label, value));
    }

    private static String travelSentence(SlottingReport.Comparison comparison) {
        if (comparison.travelReduction() == null) {
            return "The current layout has no travel to compare.";
        }
        double reduction = comparison.travelReduction();
        if (Math.abs(reduction) < 1e-9) {
            return "Travel matches the current layout.";
        }
        int percent = (int) Math.round(Math.abs(reduction) * 100);
        String direction = reduction > 0 ? "lower" : "higher";
        return "Travel is " + percent + "% " + direction + " than the current layout.";
    }
}
