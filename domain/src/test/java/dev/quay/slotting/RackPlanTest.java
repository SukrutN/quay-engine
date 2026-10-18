package dev.quay.slotting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RackPlanTest {

    @Test
    void returnWalkIsOutAndBackFromTheDock() {
        Slot mouth = plan().expand().stream().filter(slot -> slot.id().equals("A-01-L1")).findFirst().orElseThrow();
        assertEquals(13.2, mouth.returnMeters(), 1e-9);
        assertEquals(Ergonomics.FLOOR, mouth.ergonomics());
        assertEquals(46, mouth.cubeLiters(), 0);
    }

    @Test
    void wideGoldenBayRoundsCubeAndShortensNothing() {
        Slot wide = plan().expand().stream().filter(slot -> slot.id().equals("A-02-L3")).findFirst().orElseThrow();
        assertEquals(31, wide.cubeLiters(), 0);
        assertEquals(Ergonomics.GOLDEN, wide.ergonomics());
        Slot beam = plan().expand().stream().filter(slot -> slot.id().equals("B-01-L4")).findFirst().orElseThrow();
        assertEquals(Ergonomics.BEAM, beam.ergonomics());
        assertEquals(2 * (4.2 + 3.6 + 2.4), beam.returnMeters(), 1e-9);
    }

    private static RackPlan plan() {
        return new RackPlan(
                List.of("A", "B"),
                List.of(1, 2),
                List.of(1, 2, 3, 4),
                Set.of(2, 3),
                1,
                Set.of(2),
                1.4,
                Map.of(1, 46.0, 2, 30.0, 3, 22.0, 4, 12.0),
                4.2,
                3.6,
                2.4,
                2.5);
    }
}
