package dev.quay.slotting.app;

/** The caller named a fixture this process does not publish. */
public final class UnknownFixture extends RuntimeException {

    private final String fixtureId;

    public UnknownFixture(String fixtureId) {
        super("No fixture named '" + fixtureId + "' is published with this service.");
        this.fixtureId = fixtureId;
    }

    public String fixtureId() {
        return fixtureId;
    }
}
