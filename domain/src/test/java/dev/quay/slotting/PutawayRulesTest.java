package dev.quay.slotting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PutawayRulesTest {

    static final PutawayWeights WEIGHTS = new PutawayWeights(0.5, 0.05, 0.2, 3.0, 1.0, 1.15, 1.35, 12.0, 1);
    static final Banding BANDS = new Banding(90, 30);
    static final List<Threshold> THRESHOLDS =
            List.of(new Threshold(10, 24, 6), new Threshold(6, 12, 3), new Threshold(3, 8, 2));

    @Test
    void anUnknownSkuOnTheManifestStopsTheDay() {
        PutawayDay day = simpleDay(List.of(new Receipt("R-01", "MG-99", 2, "carton")));
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayRules.check(day));
        assertTrue(rejection.reasons().stream()
                .anyMatch(reason -> reason.contains("MG-99") && reason.contains("not in the catalog")),
                rejection.reasons().toString());
    }

    @Test
    void aZeroCartonLineStopsTheDay() {
        PutawayDay day = simpleDay(List.of(new Receipt("R-01", "FAST", 0, "carton")));
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayRules.check(day));
        assertTrue(rejection.reasons().stream()
                .anyMatch(reason -> reason.contains("R-01") && reason.contains("zero cartons")),
                rejection.reasons().toString());
    }

    @Test
    void anUnlistedUomStopsTheDay() {
        PutawayDay day = simpleDay(List.of(new Receipt("R-01", "FAST", 1, "pallet")));
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayRules.check(day));
        assertTrue(rejection.reasons().stream()
                .anyMatch(reason -> reason.contains("pallet") && reason.contains("does not publish")),
                rejection.reasons().toString());
    }

    @Test
    void aFractionalCartonStopsTheDay() {
        PutawayDay day = uomedDay(Map.of("FAST", Map.of("layer", 2.5)),
                List.of(new Receipt("R-01", "FAST", 3, "layer")));
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayRules.check(day));
        assertTrue(rejection.reasons().stream().anyMatch(reason -> reason.contains("fraction of a carton")),
                rejection.reasons().toString());
    }

    @Test
    void aReceiptIdMayNotAppearTwice() {
        PutawayDay day = simpleDay(
                List.of(new Receipt("R-01", "FAST", 1, "carton"), new Receipt("R-01", "SLOW", 1, "carton")));
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayRules.check(day));
        assertTrue(rejection.reasons().stream().anyMatch(reason -> reason.contains("R-01 is listed twice")),
                rejection.reasons().toString());
    }

    @Test
    void anOpeningMayNotLeaveTheFaceBelowZero() {
        PutawayDay day = openingDay(Map.of("FAST", 2),
                List.of(new StockMove("FAST", -5)),
                List.of(new Receipt("R-01", "FAST", 1, "carton")));
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayRules.check(day));
        assertTrue(rejection.reasons().stream().anyMatch(reason -> reason.contains("leaves fast tin below zero")),
                rejection.reasons().toString());
    }

    @Test
    void aStartStateMayNotExceedItsBay() {
        PutawayDay day = startedDay(List.of(new Receipt("R-01", "SLOW", 1, "carton")),
                Map.of("F-01-L2", new SlotFill("MID", 20)));
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayRules.check(day));
        assertTrue(rejection.reasons().stream().anyMatch(reason -> reason.contains("F-01-L2 starts over its cube")),
                rejection.reasons().toString());
    }

    @Test
    void everyBandNeedsAThreshold() {
        PutawayDay day = thresholdsMapDay(Map.of(VelocityClass.A, new Threshold(10, 24, 6)),
                List.of(new Receipt("R-01", "FAST", 1, "carton")));
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayRules.check(day));
        assertTrue(rejection.reasons().stream()
                        .anyMatch(reason -> reason.contains("band B") && reason.contains("threshold"))
                && rejection.reasons().stream()
                        .anyMatch(reason -> reason.contains("band C") && reason.contains("threshold")),
                rejection.reasons().toString());
    }

    @Test
    void theMaxMayNotSitBelowMinPlusTheHysteresis() {
        PutawayDay day = thresholdDay(Map.of(),
                List.of(new Threshold(30, 32, 12), new Threshold(6, 12, 3), new Threshold(3, 8, 2)),
                List.of(new Receipt("R-01", "FAST", 1, "carton")));
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayRules.check(day));
        assertTrue(rejection.reasons().stream().anyMatch(
                reason -> reason.contains("A threshold needs maxCartons at or above minCartons plus its hysteresis")),
                rejection.reasons().toString());
    }

    @Test
    void aDayWithoutBaysCannotRun() {
        PutawayDay day = slottedDay(List.of(),
                List.of(new Receipt("R-01", "FAST", 1, "carton")));
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayRules.check(day));
        assertTrue(rejection.reasons().stream().anyMatch(reason -> reason.contains("no reserve bays")),
                rejection.reasons().toString());
    }

    @Test
    void aCartonNoBayCanHoldAloneStopsTheDay() {
        PutawayDay day = slottedDay(
                List.of(slot("S-01-L1", "S", 0, 1, 0, 1, 4, Ergonomics.FLOOR)),
                List.of(new Receipt("R-01", "SLOW", 1, "carton")));
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayRules.check(day));
        assertTrue(rejection.reasons().stream().anyMatch(reason -> reason.contains("no bay that holds even one carton")),
                rejection.reasons().toString());
    }

    @Test
    void aBayCarriesOneSkuAndTopsOffOnlyItsOwn() {
        SlotState state = SlotState.of(Map.of("F-01-L2", new SlotFill("FAST", 4)));
        Slot slot = slot("F-01-L2", "F", 0, 1, 0, 2, 96, Ergonomics.GOLDEN);
        Sku other = sku("MID", "mid carton", 60, 8, 1.5);
        Sku same = fastSku();

        String refusal = state.refusal(slot, other, 2, WEIGHTS);
        assertTrue(refusal.contains("already holds FAST"), refusal);

        assertNull(state.refusal(slot, same, 4, WEIGHTS));
        SlotState topped = state.put(slot.id(), "FAST", 4);
        assertEquals(8, topped.fills().get(slot.id()).cartons());
    }

    @Test
    void anOverCapacityTopOffNamesTheMissingLiters() {
        SlotState state = SlotState.of(Map.of("F-01-L2", new SlotFill("FAST", 30)));
        Slot slot = slot("F-01-L2", "F", 0, 1, 0, 2, 96, Ergonomics.GOLDEN);
        String refusal = state.refusal(slot, fastSku(), 4, WEIGHTS);
        assertTrue(refusal.contains("liters free") && refusal.contains("fast tin"), refusal);
    }

    @Test
    void theHeavyCrateCannotRideTheBeam() {
        SlotState state = SlotState.empty();
        Slot beam = slot("F-01-L3", "F", 0, 1, 0, 3, 96, Ergonomics.GOLDEN);
        String refusal = state.refusal(beam, heavySku(), 2, WEIGHTS);
        assertTrue(refusal.contains("cannot claim a level above 1"), refusal);
        assertNull(state.refusal(slot("F-01-L1", "F", 0, 1, 0, 1, 120, Ergonomics.FLOOR), heavySku(), 2, WEIGHTS));
    }

    @Test
    void zonedStockStaysInItsZone() {
        SlotState state = SlotState.empty();
        Sku chill = sku("CHILL", "chill vial", 40, 2, 0.5, "chill");
        Slot ambient = slot("F-01-L2", "F", 0, 1, 0, 2, 96, Ergonomics.GOLDEN, "ambient");
        String refusal = state.refusal(ambient, chill, 2, WEIGHTS);
        assertTrue(refusal.contains("zoned"), refusal);
        assertNull(state.refusal(slot("B-01-L2", "B", 1, 1, 0, 2, 96, Ergonomics.GOLDEN, "chill"), chill, 2, WEIGHTS));
    }

    @Test
    void anEmptyStateHoldsNothing() {
        assertEquals(0, SlotState.empty().fills().size());
    }

    static Sku sku(String id, String name, double picks, double cube, double weight) {
        return new Sku(id, name, picks, cube, weight, Optional.empty());
    }

    static Sku sku(String id, String name, double picks, double cube, double weight, String zone) {
        return new Sku(id, name, picks, cube, weight, Optional.of(zone));
    }

    static Sku fastSku() {
        return sku("FAST", "fast tin", 120, 3, 2.0);
    }

    static Sku midSku() {
        return sku("MID", "mid carton", 60, 8, 1.5);
    }

    static Sku slowSku() {
        return sku("SLOW", "slow mortar", 20, 6, 3.0);
    }

    static Sku heavySku() {
        return sku("HEAVY", "iron crate", 100, 10, 14.5);
    }

    static Slot slot(String id, String aisle, int aisleIndex, int bay, int bayIndex, int level, double cube,
            Ergonomics ergonomics) {
        return slot(id, aisle, aisleIndex, bay, bayIndex, level, cube, ergonomics, null);
    }

    static Slot slot(String id, String aisle, int aisleIndex, int bay, int bayIndex, int level, double cube,
            Ergonomics ergonomics, String zone) {
        return new Slot(id, aisle, aisleIndex, bay, bayIndex, level, cube, 8.0, ergonomics,
                zone == null ? Optional.empty() : Optional.of(zone));
    }

    static List<Slot> rack() {
        return List.of(
                slot("F-01-L1", "F", 0, 1, 0, 1, 120, Ergonomics.FLOOR),
                slot("F-01-L2", "F", 0, 1, 0, 2, 96, Ergonomics.GOLDEN),
                slot("F-01-L3", "F", 0, 1, 0, 3, 10, Ergonomics.GOLDEN),
                slot("F-02-L1", "F", 0, 2, 1, 1, 120, Ergonomics.FLOOR),
                slot("F-03-L2", "F", 0, 3, 2, 2, 16, Ergonomics.GOLDEN),
                slot("G-01-L2", "G", 1, 1, 0, 2, 24, Ergonomics.GOLDEN),
                slot("G-01-L4", "G", 1, 1, 0, 4, 6, Ergonomics.BEAM));
    }

    static PutawayDay simpleDay(List<Receipt> receipts) {
        return day(receipts, Map.of(), List.of(), THRESHOLDS, Map.of(), Map.of(), rack());
    }

    static PutawayDay uomedDay(Map<String, Map<String, Double>> uoms, List<Receipt> receipts) {
        return day(receipts, Map.of(), List.of(), THRESHOLDS, uoms, Map.of(), rack());
    }

    static PutawayDay openingDay(Map<String, Integer> onHand, List<StockMove> openings, List<Receipt> receipts) {
        return day(receipts, onHand, openings, THRESHOLDS, Map.of(), Map.of(), rack());
    }

    static PutawayDay startedDay(List<Receipt> receipts, Map<String, SlotFill> startState) {
        return day(receipts, Map.of(), List.of(), THRESHOLDS, Map.of(), startState, rack());
    }

    static PutawayDay thresholdDay(Map<String, Integer> onHand, List<Threshold> thresholds, List<Receipt> receipts) {
        return day(receipts, onHand, List.of(), thresholds, Map.of(), Map.of(), rack());
    }

    static PutawayDay thresholdsMapDay(Map<VelocityClass, Threshold> thresholds, List<Receipt> receipts) {
        ForwardPlan forward = new ForwardPlan(Map.of(), thresholds, List.of());
        return new PutawayDay("test-day", "Test day", rack(),
                List.of(fastSku(), midSku(), slowSku(), heavySku()),
                List.of(), Map.of(), receipts, BANDS, WEIGHTS, forward, Map.of(), 1998);
    }

    static PutawayDay slottedDay(List<Slot> slots, List<Receipt> receipts) {
        return day(receipts, Map.of(), List.of(), THRESHOLDS, Map.of(), Map.of(), slots);
    }

    static PutawayDay day(
            List<Receipt> receipts,
            Map<String, Integer> onHand,
            List<StockMove> openings,
            List<Threshold> thresholds,
            Map<String, Map<String, Double>> uoms,
            Map<String, SlotFill> startState,
            List<Slot> slots) {
        ForwardPlan forward = new ForwardPlan(onHand, Map.of(
                VelocityClass.A, thresholds.get(0),
                VelocityClass.B, thresholds.get(1),
                VelocityClass.C, thresholds.get(2)), openings);
        return new PutawayDay("test-day", "Test day", slots,
                List.of(fastSku(), midSku(), slowSku(), heavySku()),
                List.of(), uoms, receipts, BANDS, WEIGHTS, forward, startState, 1998);
    }
}
