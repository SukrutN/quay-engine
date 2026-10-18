package dev.quay.slotting;

/** Physical readings of a layout. These are separate from the weighted objective. */
public record FaceMetrics(
        double travelPickMeters,
        double goldenPickShare,
        double meanCubeFit,
        int heavyAboveLimit,
        double meanAffinitySteps,
        int placed,
        double picksPerDay) {}
