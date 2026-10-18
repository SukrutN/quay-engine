package dev.quay.slotting.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.quay.slotting.FaceRejection;
import dev.quay.slotting.PickFace;
import dev.quay.slotting.app.FaceJson;
import dev.quay.slotting.app.FixtureLibrary;
import dev.quay.slotting.app.SlotFace;
import dev.quay.slotting.app.UnknownFixture;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.logstash.logback.marker.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** HTTP adapter. The slotting rules live in the domain, not in these routes. */
public final class QuayServer {

    private static final Logger log = LoggerFactory.getLogger(QuayServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> REQUEST_FIELDS = Set.of("fixture", "face");

    private QuayServer() {}

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8091"));
        create(FixtureLibrary.fromClasspath()).start("0.0.0.0", port);
    }

    public static Javalin create(FixtureLibrary library) {
        String openApi = loadOpenApi();
        SlotFace slotFace = new SlotFace();
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        app.before(ctx -> ctx.attribute("startedNanos", System.nanoTime()));
        app.after(ctx -> {
            Long started = ctx.attribute("startedNanos");
            long elapsedMs = started == null ? 0 : (System.nanoTime() - started) / 1_000_000L;
            log.info(
                    Markers.append("method", ctx.method().name())
                            .and(Markers.append("path", ctx.path()))
                            .and(Markers.append("status", ctx.statusCode()))
                            .and(Markers.append("elapsedMs", elapsedMs)),
                    "request");
        });
        app.exception(FaceRejection.class, (exception, ctx) -> problem(
                ctx, 400, "urn:quay:slotting:invalid-face", "Invalid face", exception.getMessage(), exception.reasons()));
        app.exception(UnknownFixture.class, (exception, ctx) -> problem(
                ctx,
                400,
                "urn:quay:slotting:unknown-fixture",
                "Unknown fixture",
                exception.getMessage(),
                List.of()));
        app.exception(Exception.class, (exception, ctx) -> {
            log.error(Markers.append("path", ctx.path()), "request failed", exception);
            problem(ctx, 500, "urn:quay:slotting:internal", "Internal error", "The face could not be slotted.", List.of());
        });
        app.get("/health", ctx -> ctx.json(Map.of("status", "up")));
        app.get("/v1/openapi.yaml", ctx -> ctx.contentType("application/yaml").result(openApi));
        app.post("/v1/slotting", ctx -> ctx.json(slotFace.place(readRequest(ctx, library))));
        return app;
    }

    private static PickFace readRequest(Context ctx, FixtureLibrary library) {
        if (ctx.body().isBlank()) {
            throw new FaceRejection(List.of("Send a fixture name or a face document."));
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(ctx.body());
        } catch (JsonProcessingException exception) {
            throw new FaceRejection(List.of("The request is not valid JSON."));
        }
        if (!node.isObject()) {
            throw new FaceRejection(List.of("The request must be a JSON object."));
        }
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!REQUEST_FIELDS.contains(name)) {
                throw new FaceRejection(List.of("Request has unknown field " + name + "."));
            }
        }
        boolean named = node.hasNonNull("fixture");
        boolean inline = node.has("face");
        if (named && inline) {
            throw new FaceRejection(List.of("Send a fixture name or a face document, not both."));
        }
        if (!named && !inline) {
            throw new FaceRejection(List.of("Send a fixture name or a face document."));
        }
        if (named) {
            JsonNode fixture = node.get("fixture");
            if (!fixture.isTextual()) {
                throw new FaceRejection(List.of("fixture must be a string."));
            }
            return library.get(fixture.asText());
        }
        return FaceJson.parse(node.get("face"));
    }

    private static void problem(Context ctx, int status, String type, String title, String detail, List<String> reasons) {
        ctx.status(status).json(Map.of(
                "type", type,
                "title", title,
                "status", status,
                "detail", detail,
                "instance", ctx.path(),
                "reasons", reasons));
        ctx.contentType("application/problem+json");
    }

    private static String loadOpenApi() {
        try (InputStream input = QuayServer.class.getResourceAsStream("/openapi.yaml")) {
            if (input == null) {
                throw new IllegalStateException("openapi.yaml is missing");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("openapi.yaml could not be read", exception);
        }
    }
}
