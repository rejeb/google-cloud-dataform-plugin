/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.completion.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the value skeleton inserted after a config property name, according to
 * the property schema type.
 */
public class ConfigValueSkeletonBuilder {

    private static final String INDENT_UNIT = "  ";
    private static final String COLON = ": ";

    private final ConfigSchemaLookup lookup;

    public ConfigValueSkeletonBuilder(@NotNull ConfigSchemaLookup lookup) {
        this.lookup = lookup;
    }

    /**
     * Builds the text inserted after the property name, the caret offset inside that
     * text and whether another completion popup should follow. Properties accepting
     * several shapes only get the separator, so the shape can be picked in a new popup.
     */
    public Skeleton build(@NotNull ObjectNode propertySchema, @NotNull String indent) {
        if (variants(propertySchema).size() > 1) {
            return new Skeleton(COLON, COLON.length(), true);
        }
        Skeleton value = buildValue(preferredVariant(propertySchema), indent);
        return new Skeleton(COLON + value.text(),
                COLON.length() + value.caretOffset(), value.autoPopup());
    }

    /**
     * Tells whether the skeleton only opened the property, its value being chosen in
     * a follow-up popup.
     */
    public static boolean isValuePending(@NotNull Skeleton skeleton) {
        return COLON.equals(skeleton.text());
    }

    /**
     * Builds the value text alone, without the property separator.
     */
    public Skeleton buildValue(@Nullable ObjectNode schema, @NotNull String indent) {
        if (schema == null) {
            return new Skeleton("\"\"", 1, false);
        }
        if (!lookup.enumValues(schema).isEmpty()) {
            return new Skeleton("\"\"", 1, true);
        }
        String type = schema.path("type").asText("");
        if ("array".equals(type)) {
            return new Skeleton("[]", 1, false);
        }
        if (isObject(schema)) {
            return objectSkeleton(schema, indent);
        }
        if ("boolean".equals(type)) {
            return new Skeleton("false", "false".length(), false);
        }
        if ("integer".equals(type) || "number".equals(type)) {
            return new Skeleton("0", 1, false);
        }
        return new Skeleton("\"\"", 1, false);
    }

    /**
     * Returns the alternative shapes accepted by the given schema, empty when it
     * accepts a single one.
     */
    public List<ObjectNode> variants(@NotNull ObjectNode schema) {
        JsonNode oneOf = schema.get("oneOf");
        if (oneOf == null || !oneOf.isArray()) {
            return List.of();
        }
        List<ObjectNode> variants = new ArrayList<>();
        for (JsonNode node : oneOf) {
            ObjectNode variant = lookup.deref(node);
            if (variant != null) {
                variants.add(variant);
            }
        }
        return variants;
    }

    /**
     * Returns a readable name for the shape described by the given schema.
     */
    public String variantLabel(@NotNull ObjectNode schema) {
        if (!lookup.enumValues(schema).isEmpty()) {
            return String.join(" | ", lookup.enumValues(schema));
        }
        if (isObject(schema)) {
            return "object";
        }
        String type = schema.path("type").asText("");
        return type.isEmpty() ? "value" : type;
    }

    private Skeleton objectSkeleton(@NotNull ObjectNode schema, @NotNull String indent) {
        List<String> required = requiredProperties(schema);
        if (required.isEmpty()) {
            return new Skeleton("{}", 1, false);
        }
        String inner = indent + INDENT_UNIT;
        StringBuilder text = new StringBuilder("{\n");
        int caretOffset = -1;
        for (String name : required) {
            ObjectNode childSchema =
                    preferredVariant(lookup.deref(schema.path("properties").get(name)));
            String emptyValue = emptyValue(childSchema);
            text.append(inner).append(name).append(COLON).append(emptyValue).append(",\n");
            if (caretOffset < 0) {
                caretOffset = text.length() - emptyValue.length() - 2
                        + caretOffsetInValue(emptyValue);
            }
        }
        text.append(indent).append("}");
        return new Skeleton(text.toString(), caretOffset, false);
    }

    private List<String> requiredProperties(@NotNull ObjectNode schema) {
        JsonNode required = schema.get("required");
        if (required == null || !required.isArray()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        required.forEach(name -> {
            if (schema.path("properties").has(name.asText())) {
                names.add(name.asText());
            }
        });
        return names;
    }

    private String emptyValue(@Nullable ObjectNode schema) {
        if (schema == null || !lookup.enumValues(schema).isEmpty()) {
            return "\"\"";
        }
        return switch (schema.path("type").asText("")) {
            case "array" -> "[]";
            case "object" -> "{}";
            case "boolean" -> "false";
            case "integer", "number" -> "0";
            default -> isObject(schema) ? "{}" : "\"\"";
        };
    }

    @Nullable
    private ObjectNode preferredVariant(@Nullable ObjectNode schema) {
        if (schema == null) {
            return null;
        }
        List<ObjectNode> variants = variants(schema);
        if (variants.isEmpty()) {
            return schema;
        }
        for (ObjectNode variant : variants) {
            if (isObject(variant)) {
                return variant;
            }
        }
        return variants.get(0);
    }

    private static boolean isObject(@NotNull ObjectNode schema) {
        return "object".equals(schema.path("type").asText("")) || schema.has("properties");
    }

    private static int caretOffsetInValue(@NotNull String emptyValue) {
        return switch (emptyValue) {
            case "\"\"", "[]", "{}" -> 1;
            default -> emptyValue.length();
        };
    }

    /**
     * Inserted text, caret offset within it and whether another completion popup
     * should be scheduled.
     */
    public record Skeleton(String text, int caretOffset, boolean autoPopup) {
    }
}
