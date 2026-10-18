package dev.quay.slotting;

/**
 * Replan builds a layout from an empty face.
 * Improve starts from the current assignment and only keeps moves that lower the objective.
 */
public enum Mode {
    REPLAN,
    IMPROVE
}
