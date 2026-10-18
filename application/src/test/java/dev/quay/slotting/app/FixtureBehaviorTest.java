package dev.quay.slotting.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quay.slotting.FaceRejection;
import dev.quay.slotting.PickFace;
import dev.quay.slotting.Sku;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FixtureBehaviorTest {

    private static FixtureLibrary library;

    @BeforeAll
    static void load() {
        library = FixtureLibrary.fromClasspath();
    }

    @Test
    void laneIsADifferentFaceFromTheForwardPick() {
        PickFace lane = library.get("lane");
        PickFace forward = library.get("forward-pick");
        assertEquals(6, lane.slots().size());
        assertEquals(4, lane.skus().size());
        assertEquals(160, forward.slots().size());
        assertEquals(30, forward.skus().size());
        assertTrue(lane.skus().stream().noneMatch(sku -> sku.id().startsWith("MG-")));
        assertTrue(forward.skus().stream().noneMatch(sku -> sku.id().startsWith("LN-")));
        assertEquals("chill", lane.sku("LN-02").orElseThrow().zone().orElseThrow());
        assertTrue(forward.skus().stream().allMatch(sku -> sku.zone().isEmpty()));
    }

    @Test
    void laneReplanBeatsTheCurrentLayoutAndRespectsZoneWeightAndLock() {
        SlottingReport report = new SlotFace().place(library.get("lane"));
        Map<String, String> slots = report.proposal().assignment().stream()
                .collect(Collectors.toMap(SlottingReport.AssignmentRow::skuId, SlottingReport.AssignmentRow::slotId));
        assertEquals("A-01-L2", slots.get("LN-01"));
        assertEquals("B-01-L2", slots.get("LN-02"));
        assertEquals("A-01-L1", slots.get("LN-03"));
        assertEquals("A-03-L2", slots.get("LN-04"));
        assertEquals("chill", zone(report, "LN-02"));
        assertEquals(1, level(report, "LN-03"));
        assertEquals(0, report.proposal().metrics().heavyAboveLimit());
        assertTrue(report.comparison().objectiveAfter() < report.comparison().objectiveBefore());
        assertTrue(report.comparison().travelReduction() > 0);
        assertTrue(Reading.text(report).contains("Travel is"));
        assertTrue(Reading.text(report).contains("lower"));
    }

    @Test
    void forwardPickPlacesEverySkuAndKeepsHeavyCartonsAtOrBelowTheCap() {
        PickFace face = library.get("forward-pick");
        SlottingReport report = new SlotFace().place(face);
        assertEquals(face.skus().size(), report.proposal().metrics().placed());
        assertEquals(0, report.proposal().metrics().heavyAboveLimit());
        Map<String, Integer> levels = report.proposal().assignment().stream()
                .collect(Collectors.toMap(SlottingReport.AssignmentRow::skuId, SlottingReport.AssignmentRow::level));
        for (Sku sku : face.skus()) {
            assertTrue(levels.containsKey(sku.id()));
            if (sku.weightKg() >= face.policy().heavyKg()) {
                assertTrue(levels.get(sku.id()) <= face.policy().maxLevelForHeavy(), sku.id());
            }
            assertTrue(report.proposal().assignment().stream()
                    .filter(row -> row.skuId().equals(sku.id()))
                    .allMatch(row -> row.cubeFit() <= 1.0 + 1e-9));
        }
        SlottingReport again = new SlotFace().place(face);
        assertEquals(report.proposal().assignment(), again.proposal().assignment());
    }

    @Test
    void badDocumentsFailClearly() {
        assertTrue(message("{\"id\":\"x\",\"name\":\"X\"}").contains("rack"));
        assertTrue(message(laneWith("LN-02", "picksPerDay", -4)).contains("unreadable pick count"));
        assertTrue(message(laneWith("LN-03", "cubeLiters", 900)).contains("no slot"));
        FaceRejection empty = assertThrows(FaceRejection.class, () -> FaceJson.read("[]"));
        assertTrue(empty.getMessage().contains("JSON object"));
    }

    @Test
    void unknownFixtureIsNotInvented() {
        UnknownFixture unknown = assertThrows(UnknownFixture.class, () -> library.get("no-such-face"));
        assertEquals("no-such-face", unknown.fixtureId());
        assertFalse(unknown.getMessage().contains("MG-01"));
    }

    private static String message(String json) {
        return assertThrows(FaceRejection.class, () -> FaceJson.read(json)).getMessage();
    }

    private static String laneWith(String skuId, String field, double value) {
        PickFace lane = library.get("lane");
        StringBuilder skus = new StringBuilder();
        skus.append('[');
        boolean first = true;
        for (Sku sku : lane.skus()) {
            if (!first) {
                skus.append(',');
            }
            first = false;
            double picks = sku.id().equals(skuId) && field.equals("picksPerDay") ? value : sku.picksPerDay();
            double cube = sku.id().equals(skuId) && field.equals("cubeLiters") ? value : sku.cubeLiters();
            skus.append("{\"id\":\"").append(sku.id())
                    .append("\",\"name\":\"").append(sku.name())
                    .append("\",\"picksPerDay\":").append(picks)
                    .append(",\"cubeLiters\":").append(cube)
                    .append(",\"weightKg\":").append(sku.weightKg())
                    .append(",\"zone\":\"").append(sku.zone().orElseThrow()).append("\"}");
        }
        skus.append(']');
        return """
                {"id":"lane-bad","name":"Lane bad","slots":[
                  {"id":"A-01-L1","aisle":"A","aisleIndex":0,"bay":1,"bayIndex":0,"level":1,"cubeLiters":40,"returnMeters":8,"ergonomics":"floor","zone":"ambient"},
                  {"id":"B-01-L2","aisle":"B","aisleIndex":1,"bay":1,"bayIndex":0,"level":2,"cubeLiters":16,"returnMeters":28,"ergonomics":"golden","zone":"chill"}
                ],"skus":%s,"policy":%s}
                """.formatted(skus, policyJson());
    }

    private static String policyJson() {
        return """
                {"travelWeight":1,"wasteWeight":1,"affinityWeight":1,"goldenWeight":1,"lightFloorWeight":1,
                "slowPickCutoff":15,"lightKg":1,"heavyKg":12,"maxLevelForHeavy":1,"ergoGolden":1,"ergoFloor":1.1,
                "ergoBeam":1.3,"affinityDecayBays":2,"maxPasses":1}""";
    }

    private static String zone(SlottingReport report, String skuId) {
        return report.proposal().assignment().stream()
                .filter(row -> row.skuId().equals(skuId))
                .findFirst()
                .orElseThrow()
                .zone();
    }

    private static int level(SlottingReport report, String skuId) {
        return report.proposal().assignment().stream()
                .filter(row -> row.skuId().equals(skuId))
                .findFirst()
                .orElseThrow()
                .level();
    }
}
