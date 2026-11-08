package dev.quay.slotting.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.quay.slotting.PutawayDay;
import dev.quay.slotting.PutawayPolicy;
import dev.quay.slotting.PutawayRejection;
import dev.quay.slotting.PutawayResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class PutawayBehaviorTest {

    private final PutawayLibrary library = PutawayLibrary.fromClasspath();

    @Test
    void theLibraryPublishesTheDay() {
        PutawayDay day = library.get("putaway-day");
        assertEquals("Harbor & Lane day 07", day.name());
        assertEquals(1998, day.seed());
        assertEquals(30, day.receipts().size());
    }

    @Test
    void everyPolicyAnswersAllThirtyLinesWithoutRefusal() {
        PutawayReport report = new RunPutaway().day(library.get("putaway-day"));
        for (PutawayReport.Outcome outcome : report.outcomes()) {
            assertEquals(30, outcome.totals().receipts(), outcome.policy());
            assertEquals(0, outcome.totals().refused(), outcome.policy());
            assertEquals(226, outcome.totals().cartons(), outcome.policy());
            assertEquals(52, outcome.totals().cartonsForward(), outcome.policy());
            assertEquals(174, outcome.totals().cartonsReserve(), outcome.policy());
            assertEquals(26, outcome.totals().baysTouched(), outcome.policy());
        }
    }

    @Test
    void theVelocityPolicyWalks531_2Meters() {
        PutawayReport report = new RunPutaway().day(library.get("putaway-day"));
        PutawayReport.Totals totals = report.outcome("velocity").totals();
        assertEquals(531.2, totals.putawayMeters(), 1.0e-6);
        assertEquals(0.5287356321839081, totals.goldenShare(), 1.0e-9);

        List<String> ids = report.outcome("velocity").directives().stream()
                .filter(row -> row.destination().equals("reserve"))
                .map(PutawayReport.DirectiveRow::slotId)
                .toList();
        assertEquals("F-01-L3", ids.get(0));
        assertTrue(ids.contains("F-01-L2"), ids.toString());
        assertTrue(ids.contains("F-04-L2"), ids.toString());

        assertRow(report, "velocity", 0, "R-01", "MG-01", "reserve", "F-01-L3", 8, 14.8);
        assertRow(report, "velocity", 5, "R-06", "MG-06", "forward", "", 6, 0.0);
        assertRow(report, "velocity", 14, "R-15", "MG-15", "forward", "", 19, 0.0);
        assertRow(report, "velocity", 15, "R-15", "MG-15", "reserve", "F-04-L2", 17, 23.8);
        assertRow(report, "velocity", 10, "R-11", "MG-11", "reserve", "F-02-L1", 2, 17.8);
        assertRow(report, "velocity", 24, "R-23", "MG-23", "reserve", "G-02-L4", 36, 21.4);
        assertRow(report, "velocity", 26, "R-25", "MG-25", "reserve", "H-02-L1", 2, 25.0);
    }

    @Test
    void theAffinityPolicyPaysFourPointEightMetersForProximity() {
        PutawayReport report = new RunPutaway().day(library.get("putaway-day"));
        PutawayReport.Totals totals = report.outcome("affinity").totals();
        assertEquals(536.0, totals.putawayMeters(), 1.0e-6);
        assertEquals(0.43103448275862066, totals.goldenShare(), 1.0e-9);
        assertRow(report, "affinity", 9, "R-10", "MG-10", "reserve", "F-03-L1", 4, 20.8);
        assertRow(report, "affinity", 1, "R-02", "MG-02", "reserve", "F-01-L1", 12, 14.8);

        PutawayReport.Totals velocity = report.outcome("velocity").totals();
        assertEquals(4.8, totals.putawayMeters() - velocity.putawayMeters(), 1.0e-6);
    }

    @Test
    void theForkWalks208_8MetersFurther() {
        PutawayReport report = new RunPutaway().day(library.get("putaway-day"));
        PutawayReport.Totals totals = report.outcome("random").totals();
        assertEquals(740.0, totals.putawayMeters(), 1.0e-6);
        assertEquals(0.6839080459770115, totals.goldenShare(), 1.0e-9);
        assertRow(report, "random", 0, "R-01", "MG-01", "reserve", "F-03-L4", 8, 20.8);
        assertRow(report, "random", 1, "R-02", "MG-02", "reserve", "H-06-L2", 12, 37.0);
        assertRow(report, "random", 15, "R-15", "MG-15", "reserve", "G-06-L4", 17, 33.4);

        PutawayReport.Totals velocity = report.outcome("velocity").totals();
        assertEquals(208.8, totals.putawayMeters() - velocity.putawayMeters(), 1.0e-6);
    }

    @Test
    void theRandomForkReplaysTheSameDay() {
        PutawayDay day = library.get("putaway-day");
        PutawayResult first = PutawayPolicy.named("random").orElseThrow().run(day);
        PutawayResult again = PutawayPolicy.named("random").orElseThrow().run(day);
        assertEquals(first, again);
    }

    @Test
    void theSignalTraceIsTheSameUnderEveryPolicy() {
        List<PutawayReport.SignalRow> expected = List.of(
                new PutawayReport.SignalRow("MG-06", 22.0, "raise", 50),
                new PutawayReport.SignalRow("MG-09", 9.0, "raise", 21),
                new PutawayReport.SignalRow("MG-15", 11.0, "raise", 19),
                new PutawayReport.SignalRow("MG-19", 5.0, "raise", 10),
                new PutawayReport.SignalRow("MG-22", 4.0, "raise", 11),
                new PutawayReport.SignalRow("MG-30", 2.0, "raise", 13),
                new PutawayReport.SignalRow("MG-09", 18.0, "quiet", 0),
                new PutawayReport.SignalRow("MG-15", 30.0, "quiet", 0),
                new PutawayReport.SignalRow("MG-19", 9.0, "quiet", 0),
                new PutawayReport.SignalRow("MG-22", 15.0, "quiet", 0));
        PutawayReport report = new RunPutaway().day(library.get("putaway-day"));
        for (PutawayReport.Outcome outcome : report.outcomes()) {
            assertEquals(expected, outcome.signals(), outcome.policy());
        }
    }

    @Test
    void theReadingTellsTheComparisonStory() {
        String text = PutawayReading.text(new RunPutaway().day(library.get("putaway-day")));
        assertTrue(text.contains("Harbor & Lane day 07 (putaway-day)"), text);
        assertTrue(text.contains("directed against the fork: walk 531.2 m against 740.0 m - 208.8 m less (28.2%)"), text);
        assertTrue(text.contains("golden-band share 0.53 against 0.68"), text);
        assertTrue(text.contains("affinity against velocity: +4.8 m to walk its partners together"), text);
    }

    @Test
    void anUnknownDayIdRefusesTheFetch() {
        UnknownDay unknown = assertThrows(UnknownDay.class, () -> library.get("no-such-day"));
        assertEquals("no-such-day", unknown.dayId());
    }

    @Test
    void anUnknownRootFieldStopsTheParse() throws IOException {
        String json = fixture().replace("\"seed\": 1998", "\"seed\": 1998, \"extra\": true");
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayJson.read(json));
        assertTrue(rejection.reasons().stream().anyMatch(reason -> reason.contains("unknown field extra")),
                rejection.reasons().toString());
    }

    @Test
    void anUnknownSkuOnTheManifestStopsTheParse() throws IOException {
        String json = fixture().replace("\"id\": \"R-30\", \"skuId\": \"MG-30\"", "\"id\": \"R-30\", \"skuId\": \"MG-31\"");
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayJson.read(json));
        assertTrue(rejection.reasons().stream().anyMatch(reason -> reason.contains("MG-31") && reason.contains("not in the catalog")),
                rejection.reasons().toString());
    }

    @Test
    void anUnlistedUomStopsTheParse() throws IOException {
        String json = fixture().replace("\"skuId\": \"MG-30\", \"units\": 3, \"uom\": \"carton\"",
                "\"skuId\": \"MG-30\", \"units\": 3, \"uom\": \"pallet\"");
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayJson.read(json));
        assertTrue(rejection.reasons().stream().anyMatch(reason -> reason.contains("pallet") && reason.contains("does not publish")),
                rejection.reasons().toString());
    }

    @Test
    void aFractionalCartonStopsTheParse() throws IOException {
        String json = fixture().replace("\"case\": 4", "\"case\": 4.5");
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayJson.read(json));
        assertTrue(rejection.reasons().stream().anyMatch(reason -> reason.contains("fraction of a carton")),
                rejection.reasons().toString());
    }

    @Test
    void aUomRowWithoutNumbersStopsTheParse() throws IOException {
        String json = fixture().replace("\"case\": 4 }", "\"case\": \"four\" }");
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayJson.read(json));
        assertTrue(rejection.reasons().stream().anyMatch(reason -> reason.contains("uoms.MG-15.case must be a number")),
                rejection.reasons().toString());
    }

    @Test
    void aStartStateOverItsBayStopsTheParse() throws IOException {
        String json = fixture().replace("\"G-01-L3\": { \"skuId\": \"MG-02\", \"cartons\": 40 }",
                "\"G-01-L3\": { \"skuId\": \"MG-02\", \"cartons\": 70 }");
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayJson.read(json));
        assertTrue(rejection.reasons().stream().anyMatch(reason -> reason.contains("G-01-L3 starts over its cube")),
                rejection.reasons().toString());
    }

    @Test
    void anOpeningBelowTheFaceStopsTheParse() throws IOException {
        String json = fixture().replace("\"MG-06\": 30", "\"MG-06\": 6");
        PutawayRejection rejection = assertThrows(PutawayRejection.class, () -> PutawayJson.read(json));
        assertTrue(rejection.reasons().stream().anyMatch(reason -> reason.contains("Peppercorn drums") && reason.contains("below zero")),
                rejection.reasons().toString());
    }

    private static void assertRow(
            PutawayReport report,
            String policy,
            int index,
            String receiptId,
            String skuId,
            String destination,
            String slotId,
            int cartons,
            double meters) {
        PutawayReport.DirectiveRow row = report.outcome(policy).directives().get(index);
        assertEquals(receiptId, row.receiptId(), policy + " " + index);
        assertEquals(skuId, row.skuId(), policy + " " + index);
        assertEquals(destination, row.destination(), policy + " " + index);
        assertEquals(slotId, row.slotId(), policy + " " + index);
        assertEquals(cartons, row.cartons(), policy + " " + index);
        assertEquals(meters, row.putawayMeters(), 1.0e-9, policy + " " + index);
    }

    private static String fixture() throws IOException {
        try (InputStream input = PutawayBehaviorTest.class.getResourceAsStream("/fixtures/putaway-day.json")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
