package dev.quay.slotting.app;

import dev.quay.slotting.PickFace;
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

/** Named faces shipped with the service. A name that is not in the index is unknown. */
public final class FixtureLibrary {

    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private final Map<String, PickFace> faces;

    private FixtureLibrary(Map<String, PickFace> faces) {
        this.faces = Map.copyOf(faces);
    }

    public static FixtureLibrary fromClasspath() {
        ClassLoader loader = FixtureLibrary.class.getClassLoader();
        try (InputStream index = loader.getResourceAsStream("fixtures/index.txt")) {
            if (index == null) {
                throw new IllegalStateException("fixtures/index.txt is missing from the classpath");
            }
            Map<String, PickFace> loaded = new LinkedHashMap<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String id = line.strip();
                    if (id.isEmpty() || id.startsWith("#")) {
                        continue;
                    }
                    if (!ID.matcher(id).matches()) {
                        throw new IllegalStateException("fixture id " + id + " is not a safe name");
                    }
                    try (InputStream face = loader.getResourceAsStream("fixtures/" + id + ".json")) {
                        if (face == null) {
                            throw new IllegalStateException("fixture " + id + " is listed but missing");
                        }
                        loaded.put(id, FaceJson.read(face));
                    }
                }
            }
            return new FixtureLibrary(loaded);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static PickFace read(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            return FaceJson.read(input);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + path, exception);
        }
    }

    public PickFace get(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new UnknownFixture(id == null ? "" : id);
        }
        PickFace face = faces.get(id);
        if (face == null) {
            throw new UnknownFixture(id);
        }
        return face;
    }
}
