package dev.quay.slotting.app;

import dev.quay.slotting.PickFace;
import dev.quay.slotting.Slotter;
import dev.quay.slotting.Slotting;
import java.util.concurrent.TimeUnit;

/** The one use case shared by the API and the CLI. */
public final class SlotFace {

    public SlottingReport place(PickFace face) {
        long started = System.nanoTime();
        Slotting proposal = new Slotter().slot(face);
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        return Reports.create(face, proposal, elapsed);
    }
}
