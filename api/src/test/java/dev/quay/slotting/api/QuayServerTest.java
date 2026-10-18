package dev.quay.slotting.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.quay.slotting.app.FixtureLibrary;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuayServerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private Javalin app;
    private String base;

    @BeforeEach
    void start() {
        app = QuayServer.create(FixtureLibrary.fromClasspath());
        app.start(0);
        base = "http://127.0.0.1:" + app.port();
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    @Test
    void healthAndOpenApi() throws Exception {
        HttpResponse<String> health = send("GET", "/health", null);
        assertEquals(200, health.statusCode());
        assertEquals("up", mapper.readTree(health.body()).get("status").asText());
        HttpResponse<String> spec = send("GET", "/v1/openapi.yaml", null);
        assertEquals(200, spec.statusCode());
        assertTrue(spec.body().contains("/v1/slotting"));
    }

    @Test
    void laneFixtureReturnsTheChillSlotAndALowerWalk() throws Exception {
        HttpResponse<String> response = send("POST", "/v1/slotting", "{\"fixture\":\"lane\"}");
        assertEquals(200, response.statusCode());
        JsonNode body = mapper.readTree(response.body());
        assertEquals("lane", body.get("faceId").asText());
        JsonNode yogurt = row(body.get("proposal").get("assignment"), "LN-02");
        assertEquals("B-01-L2", yogurt.get("slotId").asText());
        assertEquals("chill", yogurt.get("zone").asText());
        assertTrue(body.get("comparison").get("travelReduction").asDouble() > 0);
    }

    @Test
    void unknownFixtureIsAProblemAndNotALayout() throws Exception {
        HttpResponse<String> response = send("POST", "/v1/slotting", "{\"fixture\":\"no-such-face\"}");
        assertEquals(400, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/problem+json"));
        JsonNode body = mapper.readTree(response.body());
        assertEquals("Unknown fixture", body.get("title").asText());
        assertTrue(body.get("detail").asText().contains("no-such-face"));
        assertFalse(body.has("assignment"));
        assertFalse(body.has("proposal"));
    }

    @Test
    void impossibleInlineFaceIsRejected() throws Exception {
        String body = """
                {"face":{"id":"bad","name":"Bad","slots":[
                  {"id":"only","aisle":"A","aisleIndex":0,"bay":1,"bayIndex":0,"level":1,"cubeLiters":10,"returnMeters":4,"ergonomics":"floor"}
                ],"skus":[
                  {"id":"huge","name":"Bolt of cloth","picksPerDay":3,"cubeLiters":900,"weightKg":2}
                ],"policy":{"travelWeight":1,"wasteWeight":1,"affinityWeight":0,"goldenWeight":0,"lightFloorWeight":0,
                "slowPickCutoff":10,"lightKg":1,"heavyKg":12,"maxLevelForHeavy":1,"ergoGolden":1,"ergoFloor":1,"ergoBeam":1.2,
                "affinityDecayBays":2,"maxPasses":1}}}
                """;
        HttpResponse<String> response = send("POST", "/v1/slotting", body);
        assertEquals(400, response.statusCode());
        JsonNode problem = mapper.readTree(response.body());
        assertEquals(400, problem.get("status").asInt());
        assertTrue(problem.get("detail").asText().contains("Bolt of cloth"));
        assertFalse(problem.has("proposal"));
    }

    private static JsonNode row(JsonNode assignment, String skuId) {
        for (JsonNode row : assignment) {
            if (skuId.equals(row.get("skuId").asText())) {
                return row;
            }
        }
        throw new AssertionError(skuId);
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path));
        if ("POST".equals(method)) {
            builder.header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.GET();
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
