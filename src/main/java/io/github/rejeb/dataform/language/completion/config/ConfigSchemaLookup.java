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
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSArrayLiteralExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;
import io.github.rejeb.dataform.language.schema.json.DataformJsonSchemaGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Navigates the generated SQLX config JSON schema from a PSI position inside the
 * injected {@code config {}} object literal.
 */
public final class ConfigSchemaLookup {

    private static final String TYPE_PROPERTY = "type";
    private static final String PROPERTIES = "properties";
    private static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String DEFS_PREFIX = "#/$defs/";

    private final ObjectNode root;

    private ConfigSchemaLookup(@NotNull ObjectNode root) {
        this.root = root;
    }

    /**
     * Creates a lookup over an already resolved config schema.
     */
    public static ConfigSchemaLookup of(@NotNull ObjectNode schema) {
        return new ConfigSchemaLookup(schema);
    }

    /**
     * Creates a lookup bound to the config schema of the position's project,
     * provided the position lies inside a SQLX config block.
     */
    public static Optional<ConfigSchemaLookup> create(@NotNull PsiElement position) {
        if (!isInConfigBlock(position)) {
            return Optional.empty();
        }
        return position.getProject()
                .getService(DataformJsonSchemaGenerator.class)
                .generateSqlxConfigSchema()
                .map(ConfigSchemaLookup::new);
    }

    /**
     * Tells whether the position lies inside the injected config block of a SQLX file.
     */
    public static boolean isInConfigBlock(@NotNull PsiElement position) {
        return InjectedLanguageManager.getInstance(position.getProject())
                .getInjectionHost(position) instanceof SqlxConfigBlock;
    }

    /**
     * Returns the schema of the object whose keys can be completed at this position.
     */
    public Optional<ObjectNode> enclosingObjectSchema(@NotNull PsiElement position) {
        JSObjectLiteralExpression object =
                PsiTreeUtil.getParentOfType(position, JSObjectLiteralExpression.class, false);
        return object == null ? Optional.empty() : objectSchema(object);
    }

    /**
     * Returns the schema of the value being edited at this position.
     */
    public Optional<ObjectNode> valueSchema(@NotNull PsiElement position) {
        JSProperty property = PsiTreeUtil.getParentOfType(position, JSProperty.class, false);
        if (property == null || !isValuePosition(property, position)) {
            return Optional.empty();
        }
        return propertySchema(property);
    }

    /**
     * Returns the property names declared by the given object schema.
     */
    public Map<String, ObjectNode> properties(@NotNull ObjectNode objectSchema) {
        JsonNode props = objectSchema.get(PROPERTIES);
        if (props == null || !props.isObject()) {
            return Map.of();
        }
        Map<String, ObjectNode> result = new LinkedHashMap<>();
        props.properties().forEach(entry -> {
            if (entry.getValue().isObject()) {
                result.put(entry.getKey(), (ObjectNode) entry.getValue());
            }
        });
        return result;
    }

    /**
     * Returns the enumerated values allowed by the given schema, across oneOf variants.
     */
    public List<String> enumValues(@NotNull ObjectNode schema) {
        List<String> values = new ArrayList<>();
        collectEnumValues(schema, values);
        return values;
    }

    /**
     * Resolves {@code $ref} indirections against the schema definitions.
     */
    public ObjectNode deref(@Nullable JsonNode node) {
        JsonNode current = node;
        int guard = 0;
        while (current != null && current.isObject() && guard++ < 10) {
            String ref = current.path("$ref").asText("");
            if (ref.isEmpty()) {
                return (ObjectNode) current;
            }
            current = root.path("$defs").get(ref.replace(DEFS_PREFIX, ""));
        }
        return current != null && current.isObject() ? (ObjectNode) current : null;
    }

    /**
     * Tells whether the position sits in the value part of the given property.
     */
    public static boolean isValuePosition(@NotNull JSProperty property,
                                          @NotNull PsiElement position) {
        if (property.isShorthanded()) {
            return false;
        }
        PsiElement value = property.getValue();
        return value != null && PsiTreeUtil.isAncestor(value, position, false);
    }

    private void collectEnumValues(@Nullable JsonNode schema, List<String> values) {
        if (schema == null || !schema.isObject()) {
            return;
        }
        JsonNode enumNode = schema.get("enum");
        if (enumNode != null && enumNode.isArray()) {
            enumNode.forEach(value -> values.add(value.asText()));
        }
        JsonNode oneOf = schema.get("oneOf");
        if (oneOf != null && oneOf.isArray()) {
            oneOf.forEach(variant -> collectEnumValues(deref(variant), values));
        }
    }

    private Optional<ObjectNode> objectSchema(@NotNull JSObjectLiteralExpression object) {
        JSProperty owner = PsiTreeUtil.getParentOfType(object, JSProperty.class, true);
        if (owner == null) {
            return Optional.ofNullable(rootBranch(object));
        }
        Optional<ObjectNode> schema = propertySchema(owner);
        if (PsiTreeUtil.getParentOfType(object, JSArrayLiteralExpression.class, true,
                JSProperty.class) != null) {
            schema = schema.map(node -> deref(node.get("items")));
        }
        return schema.map(this::objectVariant).filter(node -> node != null);
    }

    private Optional<ObjectNode> propertySchema(@NotNull JSProperty property) {
        JSObjectLiteralExpression owner =
                PsiTreeUtil.getParentOfType(property, JSObjectLiteralExpression.class, true);
        String name = property.getName();
        if (owner == null || name == null) {
            return Optional.empty();
        }
        return objectSchema(owner).map(ownerSchema -> {
            JsonNode declared = ownerSchema.path(PROPERTIES).get(name);
            if (declared == null) {
                declared = ownerSchema.get(ADDITIONAL_PROPERTIES);
            }
            return declared != null && declared.isObject() ? deref(declared) : null;
        }).filter(node -> node != null);
    }

    private ObjectNode objectVariant(@NotNull ObjectNode schema) {
        if (schema.has(PROPERTIES) || schema.path(ADDITIONAL_PROPERTIES).isObject()) {
            return schema;
        }
        JsonNode oneOf = schema.get("oneOf");
        if (oneOf != null && oneOf.isArray()) {
            for (JsonNode variant : oneOf) {
                ObjectNode resolved = deref(variant);
                if (resolved != null && (resolved.has(PROPERTIES)
                        || resolved.path(ADDITIONAL_PROPERTIES).isObject())) {
                    return resolved;
                }
            }
        }
        return schema;
    }

    @Nullable
    private ObjectNode rootBranch(@NotNull JSObjectLiteralExpression configObject) {
        JsonNode oneOf = root.get("oneOf");
        if (oneOf == null || !oneOf.isArray()) {
            return null;
        }
        String actionType = declaredType(configObject);
        if (actionType != null) {
            for (JsonNode branch : oneOf) {
                if (actionType.equals(branch.path(PROPERTIES).path(TYPE_PROPERTY)
                        .path("enum").path(0).asText(""))) {
                    return (ObjectNode) branch;
                }
            }
        }
        return mergeBranches(oneOf);
    }

    private ObjectNode mergeBranches(@NotNull JsonNode oneOf) {
        ObjectNode merged = root.objectNode();
        merged.put(TYPE_PROPERTY, "object");
        ObjectNode props = merged.putObject(PROPERTIES);
        ObjectNode typeProperty = props.putObject(TYPE_PROPERTY);
        typeProperty.put(TYPE_PROPERTY, "string");
        typeProperty.put("description", "Action type.");
        var typeValues = typeProperty.putArray("enum");
        for (JsonNode branch : oneOf) {
            String actionType = branch.path(PROPERTIES).path(TYPE_PROPERTY)
                    .path("enum").path(0).asText("");
            if (!actionType.isEmpty()) {
                typeValues.add(actionType);
            }
            branch.path(PROPERTIES).properties().forEach(entry -> {
                if (!props.has(entry.getKey())) {
                    props.set(entry.getKey(), entry.getValue());
                }
            });
        }
        return merged;
    }

    @Nullable
    private static String declaredType(@NotNull JSObjectLiteralExpression configObject) {
        JSProperty typeProperty = configObject.findProperty(TYPE_PROPERTY);
        if (typeProperty == null
                || !(typeProperty.getValue() instanceof JSLiteralExpression literal)) {
            return null;
        }
        Object value = literal.getValue();
        return value instanceof String text && !text.isEmpty() ? text : null;
    }
}
