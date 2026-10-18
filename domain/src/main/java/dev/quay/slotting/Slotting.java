package dev.quay.slotting;

/** One finished layout: where each SKU went, what it costs, and the physical reading. */
public record Slotting(Placement placement, FaceMetrics metrics, CostBreakdown cost) {}
