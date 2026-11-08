package dev.quay.slotting.app;

import dev.quay.slotting.PutawayDay;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Named putaway days shipped with the service. A name that is not in the index is unknown. */
public final class PutawayLibrary {

    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private final Map<String, PutawayDay> days;

    private PutawayLibrary(Map<String, PutawayDay> days) {
        this.days = Map.copyOf(days);
    }

    public static PutawayLibrary fromClasspath() {
        ClassLoader loader = PutawayLibrary.class.getClassLoader();
        try (InputStream index = loader.getResourceAsStream("fixtures/putaway.txt")) {
            if (index == null) {
                return new PutawayLibrary(Map.of());
            }
            Map<String, PutawayDay> loaded = new LinkedHashMap<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String id = line.strip();
                    if (id.isEmpty() || id.startsWith("#")) {
                        continue;
                    }
                    if (!ID.matcher(id).matches()) {
                        throw new IllegalStateException("putaway day id " + id + " is not a safe name");
                    }
                    try (InputStream day = loader.getResourceAsStream("fixtures/" + id + ".json")) {
                        if (day == null) {
                            throw new IllegalStateException("putaway day " + id + " is listed but missing");
                        }
                        loaded.put(id, PutawayJson.read(day));
                    }
                }
            }
            return new PutawayLibrary(loaded);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static PutawayDay read(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            return PutawayJson.read(input);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + path, exception);
        }
    }

    public PutawayDay get(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new UnknownDay(id == null ? "" : id);
        }
        PutawayDay day = days.get(id);
        if (day == null) {
            throw new UnknownDay(id);
        }
        return day;
    }
}
