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
import java.util.Set;

/**
 * Builds the value skeleton inserted after a config property name, according to
 * the property schema type.
 */
public class ConfigValueSkeletonBuilder {

    private static final String INDENT_UNIT = "  ";
    private static final String COLON = ": ";

    /**
     * Properties Dataform declares as a string, whatever other shape the schema also describes for
     * them. Only the string shape is offered, so that adding one opens an expression rather than a
     * choice of shapes; the other shape stays completable once its braces have been typed.
     */
    private static final Set<String> STRING_ONLY_PROPERTIES = Set.of("partitionBy");

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
        return build(null, propertySchema, indent);
    }

    /**
     * Builds the text inserted after the given property name, the caret offset inside that text and
     * whether another completion popup should follow. A property accepting several shapes only gets
     * the separator, so the shape can be picked in a new popup, unless Dataform declares it as a
     * string, in which case the string is opened straight away.
     */
    public Skeleton build(@Nullable String propertyName,
                          @NotNull ObjectNode propertySchema,
                          @NotNull String indent) {
        List<ObjectNode> variants = variantsOf(propertyName, propertySchema);
        if (variants.size() > 1) {
            return new Skeleton(COLON, COLON.length(), true);
        }
        ObjectNode chosen = variants.size() == 1 ? variants.getFirst() : preferredVariant(propertySchema);
        Skeleton value = buildValue(chosen, indent);
        boolean popup = value.autoPopup() || isStringOnly(propertyName);
        return new Skeleton(COLON + value.text(), COLON.length() + value.caretOffset(), popup);
    }

    /**
     * Returns the shapes offered for the value of the given property, which are the ones its schema
     * accepts but for a property Dataform declares as a string.
     */
    public List<ObjectNode> variantsOf(@Nullable String propertyName, @NotNull ObjectNode schema) {
        List<ObjectNode> variants = variants(schema);
        if (!isStringOnly(propertyName) || variants.isEmpty()) {
            return variants;
        }
        List<ObjectNode> strings = variants.stream().filter(variant -> !isObject(variant)).toList();
        return strings.isEmpty() ? variants : strings;
    }

    private static boolean isStringOnly(@Nullable String propertyName) {
        return propertyName != null && STRING_ONLY_PROPERTIES.contains(propertyName);
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
