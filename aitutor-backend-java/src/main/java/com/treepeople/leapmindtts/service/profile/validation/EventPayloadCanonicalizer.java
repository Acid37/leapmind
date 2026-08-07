package com.treepeople.leapmindtts.service.profile.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EventPayloadCanonicalizer {
    private EventPayloadCanonicalizer() {
    }

    public static byte[] canonical(JsonNode node) {
        return write(node).getBytes(StandardCharsets.UTF_8);
    }

    private static String write(JsonNode node) {
        if (node.isObject()) {
            List<String> keys = new ArrayList<>();
            node.fieldNames().forEachRemaining(key -> {
                if (node.get(key) != null && !node.get(key).isNull()) {
                    keys.add(key);
                }
            });
            Collections.sort(keys);
            StringBuilder result = new StringBuilder("{");
            for (String key : keys) {
                if (result.length() > 1) {
                    result.append(',');
                }
                result.append(new TextNode(Normalizer.normalize(key, Normalizer.Form.NFC)))
                        .append(':')
                        .append(write(node.get(key)));
            }
            return result.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder result = new StringBuilder("[");
            for (JsonNode item : node) {
                if (result.length() > 1) {
                    result.append(',');
                }
                result.append(write(item));
            }
            return result.append(']').toString();
        }
        if (node.isTextual()) {
            return new TextNode(Normalizer.normalize(node.textValue(), Normalizer.Form.NFC)).toString();
        }
        if (node.isNumber()) {
            java.math.BigDecimal value = node.decimalValue().stripTrailingZeros();
            return value.signum() == 0 ? "0" : value.toPlainString();
        }
        return node.toString();
    }
}
