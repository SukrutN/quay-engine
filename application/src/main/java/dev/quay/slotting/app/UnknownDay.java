package dev.quay.slotting.app;

/** The caller named a putaway day this process does not publish. */
public final class UnknownDay extends RuntimeException {

    private final String dayId;

    public UnknownDay(String dayId) {
        super("No putaway day named '" + dayId + "' is published with this service.");
        this.dayId = dayId;
    }

    public String dayId() {
        return dayId;
    }
}
