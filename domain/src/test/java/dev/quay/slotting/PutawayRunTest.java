package dev.quay.slotting;

import static dev.quay.slotting.PutawayRulesTest.BANDS;
import static dev.quay.slotting.PutawayRulesTest.WEIGHTS;
import static dev.quay.slotting.PutawayRulesTest.fastSku;
import static dev.quay.slotting.PutawayRulesTest.heavySku;
import static dev.quay.slotting.PutawayRulesTest.midSku;
import static dev.quay.slotting.PutawayRulesTest.openingDay;
import static dev.quay.slotting.PutawayRulesTest.rack;
import static dev.quay.slotting.PutawayRulesTest.slot;
import static dev.quay.slotting.PutawayRulesTest.slowSku;
import static dev.quay.slotting.PutawayRulesTest.thresholdDay;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PutawayRunTest {

    private static final Map<VelocityClass, Threshold> THRESHOLDS = Map.of(
            VelocityClass.A, new Threshold(10, 24, 6),
            VelocityClass.B, new Threshold(6, 12, 3),
            VelocityClass.C, new Threshold(3, 8, 2));

    /** Every SKU holds above its min, so nothing raises and every reserve line stays a reserve line. */
    private static PutawayDay stockedDay(List<Receipt> receipts) {
        return stockedDay(receipts, rack());
    }

    private static PutawayDay stockedDay(List<Receipt> receipts, List<Slot> slots) {
        return new PutawayDay("test-day", "Test day", slots,
                List.of(fastSku(), midSku(), slowSku(), heavySku()),
                List.of(), Map.of(), receipts, BANDS, WEIGHTS,
                new ForwardPlan(Map.of("FAST", 24, "MID", 6, "SLOW", 3, "HEAVY", 10), THRESHOLDS, List.of()),
                Map.of(), 1998);
    }

    @Test
    void velocityLandsTheFastCartonInTheNearestGoldenBay() {
        PutawayResult result = new VelocityPutaway()
                .run(stockedDay(List.of(new Receipt("R-01", "FAST", 2, "carton"))));
        PutawayDirective row = result.directives().get(0);
        assertEquals(PutawayDirective.RESERVE, row.destination());
        assertEquals("F-01-L2", row.slotId());
        assertEquals(2, row.cartons());
        assertEquals(4.0, row.putawayMeters(), 1.0e-9);
    }

    @Test
    void velocityReadsTheFastestLineFirst() {
        PutawayResult result = new VelocityPutaway()
                .run(stockedDay(List.of(new Receipt("R-01", "SLOW", 1, "carton"),
                        new Receipt("R-02", "FAST", 1, "carton"))));
        assertEquals("R-02", result.directives().get(0).receiptId());
        assertEquals("R-01", result.directives().get(1).receiptId());
    }

    @Test
    void theHeavyCrateStaysOnTheFloor() {
        PutawayResult result = new VelocityPutaway()
                .run(stockedDay(List.of(new Receipt("R-01", "HEAVY", 2, "carton"))));
        assertEquals("F-01-L1", result.directives().get(0).slotId());
    }

    @Test
    void theSlowCartonKeepsOutOfTheGoldenBand() {
        PutawayResult result = new VelocityPutaway()
                .run(stockedDay(List.of(new Receipt("R-01", "SLOW", 1, "carton"))));
        assertEquals("F-01-L1", result.directives().get(0).slotId());
    }

    @Test
    void replenishRaisesAtTheOpeningAndQuietsPastTheBand() {
        PutawayDay day = openingDay(Map.of("FAST", 24, "MID", 6, "SLOW", 2, "HEAVY", 10),
                List.of(new StockMove("SLOW", -2)),
                List.of(new Receipt("R-01", "SLOW", 10, "carton")));
        PutawayResult result = new VelocityPutaway().run(day);
        assertEquals(List.of(
                new ReplenishSignal("SLOW", 0, ReplenishSignal.RAISE, 8),
                new ReplenishSignal("SLOW", 8, ReplenishSignal.QUIET, 0)), result.signals());
        PutawayDirective forward = result.directives().get(0);
        assertEquals(PutawayDirective.FORWARD, forward.destination());
        assertEquals(8, forward.cartons());
        PutawayDirective reserve = result.directives().get(1);
        assertEquals(PutawayDirective.RESERVE, reserve.destination());
        assertEquals(2, reserve.cartons());
        assertEquals(0, result.totals().refused());
        assertEquals(8, result.totals().cartonsForward());
        assertEquals(2, result.totals().cartonsReserve());
    }

    @Test
    void theQueueDoesNotFlickerInsideTheBand() {
        PutawayDay day = thresholdDay(Map.of("FAST", 24, "MID", 6, "SLOW", 2, "HEAVY", 10),
                PutawayRulesTest.THRESHOLDS, List.of(
                new Receipt("R-01", "SLOW", 1, "carton"),
                new Receipt("R-02", "SLOW", 1, "carton"),
                new Receipt("R-03", "SLOW", 1, "carton"),
                new Receipt("R-04", "SLOW", 5, "carton")));
        PutawayResult result = new VelocityPutaway().run(day);
        assertEquals(List.of(
                new ReplenishSignal("SLOW", 2, ReplenishSignal.RAISE, 6),
                new ReplenishSignal("SLOW", 5, ReplenishSignal.QUIET, 0)), result.signals());
        assertEquals(3, result.totals().cartonsForward());
        PutawayDirective last = result.directives().get(3);
        assertEquals(PutawayDirective.RESERVE, last.destination());
        assertEquals(5, last.cartons());
    }

    @Test
    void aLineWithNoBayLeftIsRefused() {
        Map<String, SlotFill> occupied = Map.of(
                "F-01-L1", new SlotFill("SLOW", 10),
                "F-01-L2", new SlotFill("MID", 10),
                "F-01-L3", new SlotFill("SLOW", 1),
                "F-02-L1", new SlotFill("HEAVY", 10),
                "F-03-L2", new SlotFill("MID", 1),
                "G-01-L2", new SlotFill("SLOW", 4),
                "G-01-L4", new SlotFill("SLOW", 1));
        PutawayDay day = new PutawayDay("test-day", "Test day", rack(),
                List.of(fastSku(), midSku(), slowSku(), heavySku()),
                List.of(), Map.of(), List.of(new Receipt("R-01", "FAST", 5, "carton")),
                BANDS, WEIGHTS,
                new ForwardPlan(Map.of("FAST", 24, "MID", 6, "SLOW", 3, "HEAVY", 10), THRESHOLDS, List.of()),
                occupied, 1998);
        PutawayResult result = new VelocityPutaway().run(day);
        assertEquals(1, result.refusals().size());
        assertTrue(result.refusals().get(0).reason().contains("no bay left"), result.refusals().toString());
        assertEquals(0, result.totals().cartonsReserve());
        assertEquals(1, result.totals().refused());
    }

    @Test
    void everyPolicyAnswersForTheSameManifestWithThePutTotalsItEarned() {
        List<Receipt> receipts = List.of(
                new Receipt("R-01", "FAST", 2, "carton"),
                new Receipt("R-02", "MID", 1, "carton"));
        PutawayResult velocity = new VelocityPutaway().run(stockedDay(receipts));
        assertEquals(2, velocity.totals().receipts());
        assertEquals(0, velocity.totals().refused());
        assertEquals(3, velocity.totals().cartons());
        assertEquals(3, velocity.totals().cartonsReserve());
        assertEquals(1.0, velocity.totals().goldenShare(), 1.0e-9);
        assertEquals(8.0, velocity.totals().putawayMeters(), 1.0e-9);
        assertEquals(2, velocity.totals().baysTouched());
        assertEquals(Map.of("F-01-L2", new SlotFill("FAST", 2), "F-01-L3", new SlotFill("MID", 1)),
                velocity.endState());
    }

    @Test
    void affinityWithoutPartnersMatchesVelocity() {
        List<Receipt> receipts = List.of(
                new Receipt("R-01", "SLOW", 2, "carton"),
                new Receipt("R-02", "FAST", 2, "carton"),
                new Receipt("R-03", "MID", 2, "carton"));
        PutawayResult velocity = new VelocityPutaway().run(stockedDay(receipts));
        PutawayResult affinity = new AffinityPutaway().run(stockedDay(receipts));
        assertEquals(velocity.directives(), affinity.directives());
        assertEquals(velocity.signals(), affinity.signals());
    }

    @Test
    void affinityPullsAReceiptToItsPartnerAndAgainstTheVelocityChoice() {
        List<Slot> slots = List.of(
                slot("K-03-L2", "K", 2, 3, 2, 2, 24, Ergonomics.GOLDEN),
                slot("K-03-L3", "K", 2, 3, 2, 3, 6, Ergonomics.GOLDEN),
                slot("G-01-L2", "G", 1, 1, 0, 4, 6, Ergonomics.BEAM));
        PutawayWeights affinityWeights = new PutawayWeights(0.5, 0.05, 1.0, 3.0, 1.0, 1.15, 1.35, 12.0, 1);
        PutawayDay day = new PutawayDay("test-day", "Test day", slots,
                List.of(fastSku(), midSku(), slowSku()),
                List.of(new Affinity("MID", "SLOW", 1.0)), Map.of(),
                List.of(new Receipt("R-01", "MID", 1, "carton"), new Receipt("R-02", "SLOW", 1, "carton")),
                BANDS, affinityWeights,
                new ForwardPlan(Map.of("FAST", 24, "MID", 6, "SLOW", 3), THRESHOLDS, List.of()),
                Map.of(), 1998);

        PutawayResult velocity = new VelocityPutaway().run(day);
        assertEquals("G-01-L2", velocity.directives().get(1).slotId());

        PutawayResult affinity = new AffinityPutaway().run(day);
        assertEquals("K-03-L3", affinity.directives().get(1).slotId());
    }

    @Test
    void randomIsDeterministicAndReadsTheManifestInOrder() {
        List<Receipt> receipts = List.of(
                new Receipt("R-01", "SLOW", 2, "carton"),
                new Receipt("R-02", "FAST", 2, "carton"),
                new Receipt("R-03", "MID", 1, "carton"));
        PutawayResult first = new RandomPutaway().run(stockedDay(receipts));
        PutawayResult again = new RandomPutaway().run(stockedDay(receipts));
        assertEquals(first, again);
        assertEquals(RandomPutaway.NAME, first.policy());
        assertEquals("R-01", first.directives().get(0).receiptId());
        assertEquals("R-03", first.directives().get(2).receiptId());
    }

    @Test
    void theThreePoliciesComeFromOneFactory() {
        assertEquals("velocity", PutawayPolicy.named("velocity").orElseThrow().name());
        assertEquals("affinity", PutawayPolicy.named("affinity").orElseThrow().name());
        assertEquals("random", PutawayPolicy.named("random").orElseThrow().name());
        assertTrue(PutawayPolicy.named("greedy").isEmpty());
    }
}
