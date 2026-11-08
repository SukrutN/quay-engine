package dev.quay.slotting.app;

import com.fasterxml.jackson.databind.JsonNode;
import dev.quay.slotting.FaceRejection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Problem collector for the document parsers. Unknown fields are rejected so a
 * typo cannot drop a constraint. Package-private: the parsers are the only readers.
 */
final class DocRead {

    final List<String> problems = new ArrayList<>();

    boolean failed() {
        return !problems.isEmpty();
    }

    void unknown(JsonNode node, Set<String> allowed, String path) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                problems.add(path + " has unknown field " + name + ".");
            }
        }
    }

    String text(JsonNode node, String field, String path) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            problems.add(path + "." + field + " is required.");
            return "";
        }
        return value.asText();
    }

    Optional<String> optionalText(JsonNode node, String field, String path) {
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

    double number(JsonNode node, String field, String path) {
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

    int integer(JsonNode node, String field, String path) {
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

    List<String> strings(JsonNode node, String field, String path) {
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

    List<Integer> integers(JsonNode node, String field, String path) {
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
