package dev.quay.slotting.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.quay.slotting.Affinity;
import dev.quay.slotting.Ergonomics;
import dev.quay.slotting.FaceRejection;
import dev.quay.slotting.FaceRules;
import dev.quay.slotting.Mode;
import dev.quay.slotting.PickFace;
import dev.quay.slotting.Policy;
import dev.quay.slotting.RackPlan;
import dev.quay.slotting.Sku;
import dev.quay.slotting.Slot;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reads a face document. Unknown fields are rejected so a typo cannot drop a constraint. */
public final class FaceJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ROOT = Set.of(
            "id", "name", "rack", "slots", "skus", "affinities", "current", "locked", "policy", "mode");
    private static final Set<String> RACK = Set.of(
            "aisles", "bays", "levels", "goldenLevels", "floorLevel", "wideBays", "wideMultiplier",
            "cubeLitersByLevel", "dockToFirstAisleMeters", "aislePitchMeters", "mouthToFirstBayMeters",
            "bayPitchMeters");
    private static final Set<String> SLOT = Set.of(
            "id", "aisle", "aisleIndex", "bay", "bayIndex", "level", "cubeLiters", "returnMeters",
            "ergonomics", "zone");
    private static final Set<String> SKU = Set.of("id", "name", "picksPerDay", "cubeLiters", "weightKg", "zone");
    private static final Set<String> AFFINITY = Set.of("skuA", "skuB", "strength");
    private static final Set<String> POLICY = Set.of(
            "travelWeight", "wasteWeight", "affinityWeight", "goldenWeight", "lightFloorWeight",
            "slowPickCutoff", "lightKg", "heavyKg", "maxLevelForHeavy", "ergoGolden", "ergoFloor",
            "ergoBeam", "affinityDecayBays", "maxPasses");

    private FaceJson() {}

    public static PickFace read(InputStream input) throws IOException {
        try {
            return parse(MAPPER.readTree(input));
        } catch (JsonProcessingException exception) {
            throw new FaceRejection(List.of("The face document is not valid JSON."));
        }
    }

    public static PickFace read(String json) throws IOException {
        try {
            return parse(MAPPER.readTree(json));
        } catch (JsonProcessingException exception) {
            throw new FaceRejection(List.of("The face document is not valid JSON."));
        }
    }

    public static PickFace parse(JsonNode root) {
        Read read = new Read();
        if (root == null || !root.isObject()) {
            throw new FaceRejection(List.of("The face document must be a JSON object."));
        }
        read.unknown(root, ROOT, "face");
        String id = read.text(root, "id", "face");
        String name = read.text(root, "name", "face");
        boolean hasRack = root.has("rack");
        boolean hasSlots = root.has("slots");
        if (hasRack && hasSlots) {
            read.problems.add("Send a rack or a slot list, not both.");
        }
        if (!hasRack && !hasSlots) {
            read.problems.add("The face needs a rack or a slot list.");
        }

        List<Slot> slots = List.of();
        if (hasRack && !hasSlots && root.get("rack").isObject()) {
            read.unknown(root.get("rack"), RACK, "rack");
            slots = readRack(read, root.get("rack"));
        } else if (hasRack && !root.get("rack").isObject()) {
            read.problems.add("rack must be an object.");
        }
        if (hasSlots && !hasRack) {
            slots = readSlots(read, root.get("slots"));
        }

        List<Sku> skus = readSkus(read, root.get("skus"));
        List<Affinity> affinities = readAffinities(read, root.get("affinities"));
        Map<String, String> current = readStringMap(read, root.get("current"), "current");
        Map<String, String> locked = readStringMap(read, root.get("locked"), "locked");
        Policy policy = readPolicy(read, root.get("policy"));
        Mode mode = readMode(read, root.get("mode"));
        if (root.has("current") && current.isEmpty() && (root.get("current") == null || root.get("current").isObject())) {
            read.problems.add("current must assign every SKU.");
        }
        read.finish();

        PickFace face = new PickFace(
                id,
                name,
                slots,
                skus,
                affinities,
                policy,
                mode,
                root.has("current") ? current : Map.of(),
                locked);
        FaceRules.check(face);
        return face;
    }

    private static List<Slot> readRack(Read read, JsonNode rack) {
        List<String> aisles = read.strings(rack, "aisles", "rack");
        List<Integer> bays = read.integers(rack, "bays", "rack");
        List<Integer> levels = read.integers(rack, "levels", "rack");
        Set<Integer> golden = new LinkedHashSet<>(read.integers(rack, "goldenLevels", "rack"));
        int floor = read.integer(rack, "floorLevel", "rack");
        Set<Integer> wide = new LinkedHashSet<>(read.integers(rack, "wideBays", "rack"));
        double multiplier = read.number(rack, "wideMultiplier", "rack");
        Map<Integer, Double> cubes = readLevelCubes(read, rack.get("cubeLitersByLevel"));
        double dock = read.number(rack, "dockToFirstAisleMeters", "rack");
        double aislePitch = read.number(rack, "aislePitchMeters", "rack");
        double mouth = read.number(rack, "mouthToFirstBayMeters", "rack");
        double bayPitch = read.number(rack, "bayPitchMeters", "rack");
        if (read.failed()) {
            return List.of();
        }
        try {
            return new RackPlan(aisles, bays, levels, golden, floor, wide, multiplier, cubes, dock, aislePitch, mouth, bayPitch)
                    .expand();
        } catch (FaceRejection rejection) {
            read.problems.addAll(rejection.reasons());
            return List.of();
        }
    }

    private static Map<Integer, Double> readLevelCubes(Read read, JsonNode node) {
        Map<Integer, Double> cubes = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            read.problems.add("rack.cubeLitersByLevel is required.");
            return cubes;
        }
        if (!node.isObject()) {
            read.problems.add("rack.cubeLitersByLevel must be an object.");
            return cubes;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            try {
                int level = Integer.parseInt(field.getKey());
                if (!field.getValue().isNumber()) {
                    read.problems.add("cubeLitersByLevel." + field.getKey() + " must be a number.");
                } else {
                    cubes.put(level, field.getValue().doubleValue());
                }
            } catch (NumberFormatException exception) {
                read.problems.add("cubeLitersByLevel key " + field.getKey() + " must be a level number.");
            }
        }
        return cubes;
    }

    private static List<Slot> readSlots(Read read, JsonNode node) {
        if (node == null || !node.isArray()) {
            read.problems.add("slots must be an array.");
            return List.of();
        }
        List<Slot> slots = new ArrayList<>();
        int index = 0;
        for (JsonNode item : node) {
            String path = "slots[" + index + "]";
            index++;
            if (!item.isObject()) {
                read.problems.add(path + " must be an object.");
                continue;
            }
            read.unknown(item, SLOT, path);
            String id = read.text(item, "id", path);
            String aisle = read.text(item, "aisle", path);
            int aisleIndex = read.integer(item, "aisleIndex", path);
            int bay = read.integer(item, "bay", path);
            int bayIndex = read.integer(item, "bayIndex", path);
            int level = read.integer(item, "level", path);
            double cube = read.number(item, "cubeLiters", path);
            double returns = read.number(item, "returnMeters", path);
            Ergonomics ergonomics = readErgonomics(read, item.get("ergonomics"), path);
            Optional<String> zone = read.optionalText(item, "zone", path);
            if (ergonomics == null) {
                continue;
            }
            slots.add(new Slot(id, aisle, aisleIndex, bay, bayIndex, level, cube, returns, ergonomics, zone));
        }
        return slots;
    }

    private static List<Sku> readSkus(Read read, JsonNode node) {
        if (node == null || node.isNull()) {
            read.problems.add("skus is required.");
            return List.of();
        }
        if (!node.isArray()) {
            read.problems.add("skus must be an array.");
            return List.of();
        }
        List<Sku> skus = new ArrayList<>();
        int index = 0;
        for (JsonNode item : node) {
            String path = "skus[" + index + "]";
            index++;
            if (!item.isObject()) {
                read.problems.add(path + " must be an object.");
                continue;
            }
            read.unknown(item, SKU, path);
            skus.add(new Sku(
                    read.text(item, "id", path),
                    read.text(item, "name", path),
                    read.number(item, "picksPerDay", path),
                    read.number(item, "cubeLiters", path),
                    read.number(item, "weightKg", path),
                    read.optionalText(item, "zone", path)));
        }
        return skus;
    }

    private static List<Affinity> readAffinities(Read read, JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            read.problems.add("affinities must be an array.");
            return List.of();
        }
        List<Affinity> affinities = new ArrayList<>();
        int index = 0;
        for (JsonNode item : node) {
            String path = "affinities[" + index + "]";
            index++;
            if (!item.isObject()) {
                read.problems.add(path + " must be an object.");
                continue;
            }
            read.unknown(item, AFFINITY, path);
            affinities.add(new Affinity(
                    read.text(item, "skuA", path),
                    read.text(item, "skuB", path),
                    read.number(item, "strength", path)));
        }
        return affinities;
    }

    private static Map<String, String> readStringMap(Read read, JsonNode node, String path) {
        Map<String, String> values = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return values;
        }
        if (!node.isObject()) {
            read.problems.add(path + " must be an object.");
            return values;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!field.getValue().isTextual()) {
                read.problems.add(path + "." + field.getKey() + " must be a string.");
            } else {
                values.put(field.getKey(), field.getValue().asText());
            }
        }
        return values;
    }

    private static Policy readPolicy(Read read, JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            read.problems.add("policy is required.");
            return new Policy(0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0);
        }
        read.unknown(node, POLICY, "policy");
        return new Policy(
                read.number(node, "travelWeight", "policy"),
                read.number(node, "wasteWeight", "policy"),
                read.number(node, "affinityWeight", "policy"),
                read.number(node, "goldenWeight", "policy"),
                read.number(node, "lightFloorWeight", "policy"),
                read.number(node, "slowPickCutoff", "policy"),
                read.number(node, "lightKg", "policy"),
                read.number(node, "heavyKg", "policy"),
                read.integer(node, "maxLevelForHeavy", "policy"),
                read.number(node, "ergoGolden", "policy"),
                read.number(node, "ergoFloor", "policy"),
                read.number(node, "ergoBeam", "policy"),
                read.number(node, "affinityDecayBays", "policy"),
                read.integer(node, "maxPasses", "policy"));
    }

    private static Mode readMode(Read read, JsonNode node) {
        if (node == null || node.isNull()) {
            return Mode.REPLAN;
        }
        if (!node.isTextual()) {
            read.problems.add("mode must be replan or improve.");
            return Mode.REPLAN;
        }
        return switch (node.asText()) {
            case "replan" -> Mode.REPLAN;
            case "improve" -> Mode.IMPROVE;
            default -> {
                read.problems.add("mode must be replan or improve.");
                yield Mode.REPLAN;
            }
        };
    }

    private static Ergonomics readErgonomics(Read read, JsonNode node, String path) {
        if (node == null || node.isNull()) {
            read.problems.add(path + ".ergonomics is required.");
            return null;
        }
        if (!node.isTextual()) {
            read.problems.add(path + ".ergonomics must be golden, floor, or beam.");
            return null;
        }
        return switch (node.asText()) {
            case "golden" -> Ergonomics.GOLDEN;
            case "floor" -> Ergonomics.FLOOR;
            case "beam" -> Ergonomics.BEAM;
            default -> {
                read.problems.add(path + ".ergonomics must be golden, floor, or beam.");
                yield null;
            }
        };
    }

    private static final class Read {
        private final List<String> problems = new ArrayList<>();

        private void finish() {
            if (!problems.isEmpty()) {
                throw new FaceRejection(problems);
            }
        }

        private boolean failed() {
            return !problems.isEmpty();
        }

        private void unknown(JsonNode node, Set<String> allowed, String path) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!allowed.contains(name)) {
                    problems.add(path + " has unknown field " + name + ".");
                }
            }
        }

        private String text(JsonNode node, String field, String path) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
                problems.add(path + "." + field + " is required.");
                return "";
            }
            return value.asText();
        }

        private Optional<String> optionalText(JsonNode node, String field, String path) {
            if (!node.has(field) || node.get(field).isNull()) {
                return Optional.empty();
            }
            JsonNode value = node.get(field);
            if (!value.isTextual()) {
                problems.add(path + "." + field + " must be a string.");
                return Optional.empty();
            }
            return Optional.of(value.asText());
        }

        private double number(JsonNode node, String field, String path) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                problems.add(path + "." + field + " is required.");
                return 0;
            }
            if (!value.isNumber()) {
                problems.add(path + "." + field + " must be a number.");
                return 0;
            }
            return value.doubleValue();
        }

        private int integer(JsonNode node, String field, String path) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                problems.add(path + "." + field + " is required.");
                return 0;
            }
            if (!value.isIntegralNumber()) {
                problems.add(path + "." + field + " must be a whole number.");
                return 0;
            }
            return value.intValue();
        }

        private List<String> strings(JsonNode node, String field, String path) {
            JsonNode value = node.get(field);
            if (value == null || !value.isArray()) {
                problems.add(path + "." + field + " must be an array.");
                return List.of();
            }
            List<String> items = new ArrayList<>();
            for (JsonNode item : value) {
                if (!item.isTextual()) {
                    problems.add(path + "." + field + " must contain strings.");
                } else {
                    items.add(item.asText());
                }
            }
            return items;
        }

        private List<Integer> integers(JsonNode node, String field, String path) {
            JsonNode value = node.get(field);
            if (value == null || !value.isArray()) {
                problems.add(path + "." + field + " must be an array.");
                return List.of();
            }
            List<Integer> items = new ArrayList<>();
            for (JsonNode item : value) {
                if (!item.isIntegralNumber()) {
                    problems.add(path + "." + field + " must contain whole numbers.");
                } else {
                    items.add(item.intValue());
                }
            }
            return items;
        }
    }
}
