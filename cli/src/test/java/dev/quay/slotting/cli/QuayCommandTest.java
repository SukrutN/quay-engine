package dev.quay.slotting.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;

class QuayCommandTest {

    @Test
    void placePrintsTheLaneReading() throws Exception {
        Path lane = Files.createTempFile("lane", ".json");
        try (InputStream input = QuayCommandTest.class.getResourceAsStream("/fixtures/lane.json")) {
            Files.copy(input, lane, StandardCopyOption.REPLACE_EXISTING);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = QuayCommand.run(
                new String[] {"place", lane.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(0, code);
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Lane chill corner"));
        assertTrue(text.contains("LN-02"));
        assertTrue(text.contains("B-01-L2"));
        assertTrue(text.contains("Travel is"));
        assertTrue(text.contains("lower"));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void putawayPrintsTheDayReading() throws Exception {
        Path day = Files.createTempFile("putaway-day", ".json");
        try (InputStream input = QuayCommandTest.class.getResourceAsStream("/fixtures/putaway-day.json")) {
            Files.copy(input, day, StandardCopyOption.REPLACE_EXISTING);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = QuayCommand.run(
                new String[] {"putaway", day.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(0, code);
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Harbor & Lane day 07 (putaway-day)"));
        assertTrue(text.contains("directed against the fork: walk 531.2 m against 740.0 m - 208.8 m less (28.2%)"));
        assertTrue(text.contains("affinity against velocity: +4.8 m to walk its partners together"));
        assertTrue(text.contains("MG-15 raise at 11 on hand, asks 19 cartons"));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void missingFileAndBadUsageFail() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int missing = QuayCommand.run(
                new String[] {"place", "missing-face.json"},
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(2, missing);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Could not read"));
        int usage = QuayCommand.run(new String[] {}, new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));
        assertEquals(2, usage);
    }
}
