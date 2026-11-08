package dev.quay.slotting;

/**
 * One velocity band's forward-face thresholds, in cartons. A raise fires below
 * {@code minCartons}. While the signal is outstanding, landings route to the face
 * toward {@code maxCartons}. The signal goes quiet only at {@code minCartons}
 * plus {@code hysteresisCartons}, so a shelf hovering at the line does not flick
 * the queue on and off.
 */
public record Threshold(double minCartons, double maxCartons, double hysteresisCartons) {}
