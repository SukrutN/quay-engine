package dev.quay.slotting;

/** The objective split into the terms a planner can audit. Total subtracts affinity. */
public record CostBreakdown(
        double travel,
        double waste,
        double golden,
        double lightFloor,
        double affinity,
        double total) {}
