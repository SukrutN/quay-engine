package dev.quay.slotting.app;

import dev.quay.slotting.AffinityPutaway;
import dev.quay.slotting.PutawayDay;
import dev.quay.slotting.PutawayPolicy;
import dev.quay.slotting.PutawayResult;
import dev.quay.slotting.RandomPutaway;
import dev.quay.slotting.VelocityPutaway;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** The one putaway use case shared by the API and the CLI: every shipped policy over one day. */
public final class RunPutaway {

    public PutawayReport day(PutawayDay day) {
        long started = System.nanoTime();
        List<PutawayResult> results = new ArrayList<>();
        for (String name : List.of(VelocityPutaway.NAME, AffinityPutaway.NAME, RandomPutaway.NAME)) {
            results.add(PutawayPolicy.named(name).orElseThrow().run(day));
        }
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        return PutawayReports.create(day, results, elapsed);
    }
}
