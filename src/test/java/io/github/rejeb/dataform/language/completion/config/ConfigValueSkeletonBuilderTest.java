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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValueSkeletonBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConfigValueSkeletonBuilder builder() {
        return new ConfigValueSkeletonBuilder(ConfigSchemaLookup.of(MAPPER.createObjectNode()));
    }

    private ObjectNode schema(String json) {
        try {
            return (ObjectNode) MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void enumPropertyInsertsQuotesAndSchedulesPopup() {
        ConfigValueSkeletonBuilder.Skeleton skeleton = builder()
                .build(schema("{\"type\":\"string\",\"enum\":[\"day\",\"hour\"]}"), "  ");

        assertEquals(": \"\"", skeleton.text());
        assertEquals(3, skeleton.caretOffset());
        assertTrue(skeleton.autoPopup());
    }

    @Test
    void arrayPropertyInsertsBrackets() {
        ConfigValueSkeletonBuilder.Skeleton skeleton = builder()
                .build(schema("{\"type\":\"array\",\"items\":{\"type\":\"string\"}}"), "  ");

        assertEquals(": []", skeleton.text());
        assertEquals(3, skeleton.caretOffset());
        assertFalse(skeleton.autoPopup());
    }

    @Test
    void objectWithoutRequiredPropertiesInsertsEmptyBraces() {
        ConfigValueSkeletonBuilder.Skeleton skeleton = builder()
                .build(schema("{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"}}}"),
                        "  ");

        assertEquals(": {}", skeleton.text());
        assertEquals(3, skeleton.caretOffset());
    }

    @Test
    void objectWithRequiredPropertiesInsertsFormattedSkeleton() {
        ConfigValueSkeletonBuilder.Skeleton skeleton = builder().build(schema("""
                {
                  "type": "object",
                  "required": ["field", "dataType"],
                  "properties": {
                    "field": {"type": "string"},
                    "dataType": {"type": "string", "enum": ["date"]},
                    "granularity": {"type": "string"}
                  }
                }"""), "  ");

        assertEquals(": {\n    field: \"\",\n    dataType: \"\",\n  }", skeleton.text());
        assertEquals(": {\n    field: \"".length(), skeleton.caretOffset());
    }

    @Test
    void scalarPropertiesUseTypedEmptyValues() {
        assertEquals(": false", builder().build(schema("{\"type\":\"boolean\"}"), "").text());
        assertEquals(": 0", builder().build(schema("{\"type\":\"integer\"}"), "").text());
        assertEquals(": \"\"", builder().build(schema("{\"type\":\"string\"}"), "").text());
    }

    @Test
    void oneOfPropertyDefersTheValueToAFollowUpPopup() {
        ConfigValueSkeletonBuilder.Skeleton skeleton = builder().build(schema("""
                {
                  "oneOf": [
                    {"type": "string"},
                    {
                      "type": "object",
                      "required": ["field"],
                      "properties": {"field": {"type": "string"}}
                    }
                  ]
                }"""), "  ");

        assertEquals(": ", skeleton.text());
        assertEquals(2, skeleton.caretOffset());
        assertTrue(skeleton.autoPopup());
        assertTrue(ConfigValueSkeletonBuilder.isValuePending(skeleton));
    }

    @Test
    void variantsExposeEveryAcceptedShape() {
        ObjectNode schema = schema("""
                {
                  "oneOf": [
                    {"type": "string"},
                    {
                      "type": "object",
                      "required": ["field"],
                      "properties": {"field": {"type": "string"}}
                    }
                  ]
                }""");
        ConfigSchemaLookup lookup = ConfigSchemaLookup.of(MAPPER.createObjectNode());
        ConfigValueSkeletonBuilder builder = new ConfigValueSkeletonBuilder(lookup);

        assertEquals(2, builder.variants(schema).size());
        assertEquals("string", builder.variantLabel(builder.variants(schema).get(0)));
        assertEquals("object", builder.variantLabel(builder.variants(schema).get(1)));
        assertEquals("{\n    field: \"\",\n  }",
                builder.buildValue(builder.variants(schema).get(1), "  ").text());
    }
}
