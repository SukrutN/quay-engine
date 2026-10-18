package dev.quay.slotting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SlotterTest {

    @Test
    void replanPutsTheFastSkuOnTheShortGoldenSlot() {
        PickFace face = twoSlotFace(Mode.REPLAN, Map.of(), 5);
        Slotting result = new Slotter().slot(face);
        assertEquals("near", result.placement().slotOf("fast").orElseThrow());
        assertEquals("far", result.placement().slotOf("slow").orElseThrow());
    }

    @Test
    void improveSwapsAWorseCurrentLayoutAndNeverRaisesTheObjective() {
        Map<String, String> current = Map.of("far", "fast", "near", "slow");
        PickFace face = twoSlotFace(Mode.IMPROVE, current, 5);
        double before = Objective.total(face, Placement.ofComplete(current));
        Slotting result = new Slotter().slot(face);
        assertEquals("near", result.placement().slotOf("fast").orElseThrow());
        assertTrue(result.cost().total() < before - 1e-6);
        assertTrue(result.cost().total() <= before);
    }

    @Test
    void zeroPassesLeavesTheCurrentLayoutUntouched() {
        Map<String, String> current = Map.of("far", "fast", "near", "slow");
        PickFace face = twoSlotFace(Mode.IMPROVE, current, 0);
        Slotting result = new Slotter().slot(face);
        assertEquals("far", result.placement().slotOf("fast").orElseThrow());
        assertEquals(Objective.total(face, Placement.ofComplete(current)), result.cost().total(), 1e-9);
    }

    @Test
    void equalScoresKeepTheLowerSlotId() {
        Slot first = slot("a-slot", 0, 0, 1, Ergonomics.GOLDEN, 10, 8, Optional.empty());
        Slot second = slot("b-slot", 0, 1, 1, Ergonomics.GOLDEN, 10, 8, Optional.empty());
        Sku only = new Sku("only", "Only carton", 10, 1, 1, Optional.empty());
        PickFace face = new PickFace(
                "ties",
                "Ties",
                List.of(second, first),
                List.of(only),
                List.of(),
                weights(1, 0, 3),
                Mode.REPLAN,
                Map.of(),
                Map.of());
        assertEquals("a-slot", new Slotter().slot(face).placement().slotOf("only").orElseThrow());
    }

    @Test
    void heavySkuStaysAtOrBelowTheCap() {
        Slot floor = slot("floor", 0, 0, 1, Ergonomics.FLOOR, 40, 12, Optional.empty());
        Slot beam = slot("beam", 0, 1, 4, Ergonomics.BEAM, 40, 4, Optional.empty());
        Sku heavy = new Sku("pan", "Iron pan", 8, 10, 14, Optional.empty());
        PickFace face = new PickFace(
                "weight",
                "Weight",
                List.of(floor, beam),
                List.of(heavy),
                List.of(),
                weights(1, 0, 3),
                Mode.REPLAN,
                Map.of(),
                Map.of());
        Slotting result = new Slotter().slot(face);
        assertEquals("floor", result.placement().slotOf("pan").orElseThrow());
        assertEquals(0, result.metrics().heavyAboveLimit());
    }

    @Test
    void lockedSkuStaysWhileTheOtherMoves() {
        Map<String, String> current = Map.of("far", "fast", "near", "slow");
        PickFace face = new PickFace(
                "locked",
                "Locked",
                twoSlots(),
                twoSkus(),
                List.of(),
                weights(1, 0, 5),
                Mode.IMPROVE,
                current,
                Map.of("slow", "near"));
        Slotting result = new Slotter().slot(face);
        assertEquals("near", result.placement().slotOf("slow").orElseThrow());
        assertEquals("far", result.placement().slotOf("fast").orElseThrow());
    }

    @Test
    void aSwapCannotLeaveACartonInASlotThatIsTooSmall() {
        Slot small = slot("small", 0, 0, 1, Ergonomics.FLOOR, 10, 4, Optional.empty());
        Slot big = slot("big", 0, 1, 1, Ergonomics.FLOOR, 40, 30, Optional.empty());
        Sku large = new Sku("large", "Crate", 80, 28, 4, Optional.empty());
        Sku tiny = new Sku("tiny", "Tin", 4, 1, 0.2, Optional.empty());
        PickFace face = new PickFace(
                "pack",
                "Pack",
                List.of(small, big),
                List.of(large, tiny),
                List.of(),
                weights(1, 0, 4),
                Mode.REPLAN,
                Map.of(),
                Map.of());
        Slotting result = new Slotter().slot(face);
        assertEquals("big", result.placement().slotOf("large").orElseThrow());
        assertEquals("small", result.placement().slotOf("tiny").orElseThrow());
        assertTrue(result.metrics().meanCubeFit() <= 1.0);
    }

    @Test
    void twoRunsMatch() {
        PickFace face = twoSlotFace(Mode.REPLAN, Map.of(), 4);
        assertEquals(new Slotter().slot(face).placement(), new Slotter().slot(face).placement());
    }

    @Test
    void travelWeightScalesOnlyTheTravelTerm() {
        PickFace face = twoSlotFace(Mode.REPLAN, Map.of(), 0);
        Placement placement = new Slotter().slot(face).placement();
        CostBreakdown light = Objective.assess(face, placement);
        PickFace heavier = new PickFace(
                face.id(),
                face.name(),
                face.slots(),
                face.skus(),
                face.affinities(),
                new Policy(2, 0, 0, 0, 0, 0, 2, 12, 1, 1, 1.1, 1.3, 2, 0),
                face.mode(),
                Map.of(),
                Map.of());
        CostBreakdown doubled = Objective.assess(heavier, placement);
        assertEquals(light.travel() * 2, doubled.travel(), 1e-9);
        assertEquals(doubled.travel(), doubled.total(), 1e-9);
    }

    @Test
    void emptyFaceIsRejected() {
        PickFace face = new PickFace("empty", "Empty", twoSlots(), List.of(), List.of(), weights(1, 0, 1), Mode.REPLAN, Map.of(), Map.of());
        FaceRejection rejection = assertThrows(FaceRejection.class, () -> new Slotter().slot(face));
        assertTrue(rejection.getMessage().contains("no SKUs"));
    }

    @Test
    void negativePicksAreRejected() {
        Sku broken = new Sku("bad", "Torn tin", -4, 1, 1, Optional.empty());
        PickFace face = new PickFace("torn", "Torn", twoSlots(), List.of(broken), List.of(), weights(1, 0, 1), Mode.REPLAN, Map.of(), Map.of());
        FaceRejection rejection = assertThrows(FaceRejection.class, () -> new Slotter().slot(face));
        assertTrue(rejection.getMessage().contains("unreadable pick count"));
    }

    @Test
    void impossibleCubeIsRejected() {
        Sku huge = new Sku("huge", "Bolt of cloth", 3, 900, 2, Optional.empty());
        PickFace face = new PickFace("huge", "Huge", twoSlots(), List.of(huge), List.of(), weights(1, 0, 1), Mode.REPLAN, Map.of(), Map.of());
        FaceRejection rejection = assertThrows(FaceRejection.class, () -> new Slotter().slot(face));
        assertTrue(rejection.getMessage().contains("no slot"));
    }

    @Test
    void zoneMismatchIsRejected() {
        Slot ambient = slot("amb", 0, 0, 1, Ergonomics.FLOOR, 20, 5, Optional.of("ambient"));
        Sku chill = new Sku("yog", "Yogurt", 10, 2, 1, Optional.of("chill"));
        PickFace face = new PickFace("zones", "Zones", List.of(ambient), List.of(chill), List.of(), weights(1, 0, 1), Mode.REPLAN, Map.of(), Map.of());
        FaceRejection rejection = assertThrows(FaceRejection.class, () -> new Slotter().slot(face));
        assertTrue(rejection.getMessage().contains("zone"));
    }

    private static PickFace twoSlotFace(Mode mode, Map<String, String> current, int passes) {
        return new PickFace("pair", "Pair", twoSlots(), twoSkus(), List.of(), weights(1, 0, passes), mode, current, Map.of());
    }

    private static List<Slot> twoSlots() {
        return List.of(
                slot("near", 0, 0, 2, Ergonomics.GOLDEN, 20, 5, Optional.empty()),
                slot("far", 1, 0, 1, Ergonomics.FLOOR, 20, 50, Optional.empty()));
    }

    private static List<Sku> twoSkus() {
        return List.of(
                new Sku("fast", "Fast tin", 100, 2, 1, Optional.empty()),
                new Sku("slow", "Slow tin", 1, 2, 1, Optional.empty()));
    }

    private static Policy weights(double travel, double affinity, int passes) {
        return new Policy(travel, 0, affinity, 0, 0, 0, 2, 12, 1, 1, 1.1, 1.3, 2, passes);
    }

    private static Slot slot(
            String id,
            int aisleIndex,
            int bayIndex,
            int level,
            Ergonomics ergonomics,
            double cube,
            double returns,
            Optional<String> zone) {
        return new Slot(id, "A", aisleIndex, bayIndex + 1, bayIndex, level, cube, returns, ergonomics, zone);
    }
}
