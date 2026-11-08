package dev.quay.slotting.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.quay.slotting.Banding;
import dev.quay.slotting.ForwardPlan;
import dev.quay.slotting.PutawayDay;
import dev.quay.slotting.PutawayRejection;
import dev.quay.slotting.PutawayRules;
import dev.quay.slotting.PutawayWeights;
import dev.quay.slotting.Receipt;
import dev.quay.slotting.Slot;
import dev.quay.slotting.SlotFill;
import dev.quay.slotting.StockMove;
import dev.quay.slotting.Threshold;
import dev.quay.slotting.VelocityClass;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads a putaway day. Unknown fields are rejected so a typo cannot drop a constraint. */
public final class PutawayJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ROOT = Set.of(
            "id", "name", "rack", "slots", "skus", "affinities", "uoms", "receipts", "bands", "weights",
            "forward", "startState", "seed");
    private static final Set<String> RECEIPT = Set.of("id", "skuId", "units", "uom");
    private static final Set<String> BANDS = Set.of("aCutoff", "bCutoff");
    private static final Set<String> WEIGHTS = Set.of(
            "goldenWeight", "lowWeight", "affinityWeight", "affinityDecayBays", "ergoGolden", "ergoFloor",
            "ergoBeam", "heavyKg", "maxLevelForHeavy");
    private static final Set<String> FORWARD = Set.of("onHand", "thresholds", "openings");
    private static final Set<String> THRESHOLD = Set.of("minCartons", "maxCartons", "hysteresisCartons");
    private static final Set<String> OPENING = Set.of("skuId", "cartons");
    private static final Set<String> FILL = Set.of("skuId", "cartons");

    private PutawayJson() {}

    public static PutawayDay read(InputStream input) throws IOException {
        try {
            return parse(MAPPER.readTree(input));
        } catch (JsonProcessingException exception) {
            throw new PutawayRejection(List.of("The putaway day is not valid JSON."));
        }
    }

    public static PutawayDay read(String json) throws IOException {
        try {
            return parse(MAPPER.readTree(json));
        } catch (JsonProcessingException exception) {
            throw new PutawayRejection(List.of("The putaway day is not valid JSON."));
        }
    }

    public static PutawayDay parse(JsonNode root) {
        DocRead read = new DocRead();
        if (root == null || !root.isObject()) {
            throw new PutawayRejection(List.of("The putaway day must be a JSON object."));
        }
        read.unknown(root, ROOT, "putaway day");
        String id = read.text(root, "id", "putaway day");
        String name = read.text(root, "name", "putaway day");
        boolean hasRack = root.has("rack");
        boolean hasSlots = root.has("slots");
        if (hasRack && hasSlots) {
            read.problems.add("Send a rack or a slot list, not both.");
        }
        if (!hasRack && !hasSlots) {
            read.problems.add("The day needs a rack or a slot list.");
        }

        List<Slot> slots = List.of();
        if (hasRack && !hasSlots && root.get("rack").isObject()) {
            read.unknown(root.get("rack"), FaceJson.RACK, "rack");
            slots = FaceJson.readRack(read, root.get("rack"));
        } else if (hasRack && !root.get("rack").isObject()) {
            read.problems.add("rack must be an object.");
        }
        if (hasSlots && !hasRack) {
            slots = FaceJson.readSlots(read, root.get("slots"));
        }

        List<dev.quay.slotting.Sku> skus = FaceJson.readSkus(read, root.get("skus"));
        List<dev.quay.slotting.Affinity> affinities = FaceJson.readAffinities(read, root.get("affinities"));
        Map<String, Map<String, Double>> uoms = readUoms(read, root.get("uoms"));
        List<Receipt> receipts = readReceipts(read, root.get("receipts"));
        Banding bands = readBands(read, root.get("bands"));
        PutawayWeights weights = readWeights(read, root.get("weights"));
        ForwardPlan forward = readForward(read, root.get("forward"));
        Map<String, SlotFill> startState = readStartState(read, root.get("startState"));
        int seed = read.integer(root, "seed", "putaway day");
        if (!read.problems.isEmpty()) {
            throw new PutawayRejection(read.problems);
        }

        PutawayDay day = new PutawayDay(
                id,
                name,
                slots,
                skus,
                affinities,
                uoms,
                receipts,
                bands,
                weights,
                forward,
                startState,
                seed);
        PutawayRules.check(day);
        return day;
    }

    private static Map<String, Map<String, Double>> readUoms(DocRead read, JsonNode node) {
        Map<String, Map<String, Double>> uoms = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return uoms;
        }
        if (!node.isObject()) {
            read.problems.add("uoms must be an object.");
            return uoms;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String skuId = field.getKey();
            JsonNode row = field.getValue();
            if (!row.isObject()) {
                read.problems.add("uoms." + skuId + " must be an object.");
                continue;
            }
            Map<String, Double> units = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> rows = row.fields();
            while (rows.hasNext()) {
                Map.Entry<String, JsonNode> unit = rows.next();
                if (!unit.getValue().isNumber()) {
                    read.problems.add("uoms." + skuId + "." + unit.getKey() + " must be a number.");
                } else {
                    units.put(unit.getKey(), unit.getValue().doubleValue());
                }
            }
            uoms.put(skuId, units);
        }
        return uoms;
    }

    private static List<Receipt> readReceipts(DocRead read, JsonNode node) {
        if (node == null || !node.isArray()) {
            read.problems.add("receipts must be an array.");
            return List.of();
        }
        List<Receipt> receipts = new ArrayList<>();
        int index = 0;
        for (JsonNode item : node) {
            String path = "receipts[" + index + "]";
            index++;
            if (!item.isObject()) {
                read.problems.add(path + " must be an object.");
                continue;
            }
            read.unknown(item, RECEIPT, path);
            receipts.add(new Receipt(
                    read.text(item, "id", path),
                    read.text(item, "skuId", path),
                    read.integer(item, "units", path),
                    read.text(item, "uom", path)));
        }
        return receipts;
    }

    private static Banding readBands(DocRead read, JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            read.problems.add("bands is required.");
            return new Banding(0, 0);
        }
        read.unknown(node, BANDS, "bands");
        return new Banding(
                read.number(node, "aCutoff", "bands"),
                read.number(node, "bCutoff", "bands"));
    }

    private static PutawayWeights readWeights(DocRead read, JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            read.problems.add("weights is required.");
            return new PutawayWeights(1, 0, 0, 1, 1, 1, 1, 1, 1);
        }
        read.unknown(node, WEIGHTS, "weights");
        return new PutawayWeights(
                read.number(node, "goldenWeight", "weights"),
                read.number(node, "lowWeight", "weights"),
                read.number(node, "affinityWeight", "weights"),
                read.number(node, "affinityDecayBays", "weights"),
                read.number(node, "ergoGolden", "weights"),
                read.number(node, "ergoFloor", "weights"),
                read.number(node, "ergoBeam", "weights"),
                read.number(node, "heavyKg", "weights"),
                read.integer(node, "maxLevelForHeavy", "weights"));
    }

    private static ForwardPlan readForward(DocRead read, JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            read.problems.add("forward is required.");
            return new ForwardPlan(Map.of(), Map.of(), List.of());
        }
        read.unknown(node, FORWARD, "forward");
        Map<String, Integer> onHand = readIntegerMap(read, node.get("onHand"), "forward.onHand");
        Map<VelocityClass, Threshold> thresholds = readThresholds(read, node.get("thresholds"));
        List<StockMove> openings = readOpenings(read, node.get("openings"));
        return new ForwardPlan(onHand, thresholds, openings);
    }

    private static Map<Integer, Integer> dummyUnused() {
        return Map.of();
    }

    private static Map<VelocityClass, Threshold> readThresholds(DocRead read, JsonNode node) {
        Map<VelocityClass, Threshold> thresholds = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            read.problems.add("forward.thresholds is required.");
            return thresholds;
        }
        if (!node.isObject()) {
            read.problems.add("forward.thresholds must be an object.");
            return thresholds;
        }
        read.unknown(node, Set.of("A", "B", "C"), "forward.thresholds");
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            VelocityClass band = switch (field.getKey()) {
                case "A" -> VelocityClass.A;
                case "B" -> VelocityClass.B;
                case "C" -> VelocityClass.C;
                default -> null;
            };
            JsonNode row = field.getValue();
            if (band == null || !row.isObject()) {
                read.problems.add("forward.thresholds must carry a row for each of A, B, and C.");
                continue;
            }
            read.unknown(row, THRESHOLD, "forward.thresholds." + band);
            thresholds.put(band, new Threshold(
                    read.number(row, "minCartons", "forward.thresholds." + band),
                    read.number(row, "maxCartons", "forward.thresholds." + band),
                    read.number(row, "hysteresisCartons", "forward.thresholds." + band)));
        }
        return thresholds;
    }

    private static List<StockMove> readOpenings(DocRead read, JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            read.problems.add("forward.openings must be an array.");
            return List.of();
        }
        List<StockMove> openings = new ArrayList<>();
        int index = 0;
        for (JsonNode item : node) {
            String path = "forward.openings[" + index + "]";
            index++;
            if (!item.isObject()) {
                read.problems.add(path + " must be an object.");
                continue;
            }
            read.unknown(item, OPENING, path);
            openings.add(new StockMove(
                    read.text(item, "skuId", path),
                    read.integer(item, "cartons", path)));
        }
        return openings;
    }

    private static Map<String, Integer> readIntegerMap(DocRead read, JsonNode node, String path) {
        Map<String, Integer> values = new LinkedHashMap<>();
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
            if (!field.getValue().isIntegralNumber()) {
                read.problems.add(path + "." + field.getKey() + " must be a whole number.");
            } else {
                values.put(field.getKey(), field.getValue().intValue());
            }
        }
        return values;
    }

    private static Map<String, SlotFill> readStartState(DocRead read, JsonNode node) {
        Map<String, SlotFill> fills = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return fills;
        }
        if (!node.isObject()) {
            read.problems.add("startState must be an object.");
            return fills;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String slotId = field.getKey();
            JsonNode row = field.getValue();
            if (!row.isObject()) {
                read.problems.add("startState." + slotId + " must be an object.");
                continue;
            }
            read.unknown(row, FILL, "startState." + slotId);
            fills.put(slotId, new SlotFill(
                    read.text(row, "skuId", "startState." + slotId),
                    read.integer(row, "cartons", "startState." + slotId)));
        }
        return fills;
    }
}
