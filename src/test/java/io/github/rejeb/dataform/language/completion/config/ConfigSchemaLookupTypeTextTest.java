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

/**
 * The type shown next to a config property lists every shape it accepts.
 */
public class ConfigSchemaLookupTypeTextTest {

    private final ConfigSchemaLookup lookup = ConfigSchemaLookup.of(schema());

    @Test
    public void aPropertyWithSeveralShapesListsThem() {
        assertEquals("string[] | string", lookup.typeText(property("uniqueKey")));
    }

    @Test
    public void anArrayOfSeveralShapesListsThemInParentheses() {
        assertEquals("(string | object)[]", lookup.typeText(property("dependencies")));
    }

    @Test
    public void singleShapesKeepTheirType() {
        assertEquals("string[]", lookup.typeText(property("tags")));
        assertEquals("object", lookup.typeText(property("target")));
    }

    private ObjectNode property(String name) {
        return (ObjectNode) schema().path("properties").get(name);
    }

    private static ObjectNode schema() {
        try {
            return (ObjectNode) new ObjectMapper().readTree("""
                    {
                      "$defs": {
                        "Target": {"type": "object", "properties": {"name": {"type": "string"}}}
                      },
                      "properties": {
                        "uniqueKey": {"oneOf": [
                          {"type": "array", "items": {"type": "string"}},
                          {"type": "string"}]},
                        "dependencies": {"type": "array", "items": {"oneOf": [
                          {"type": "string"},
                          {"$ref": "#/$defs/Target"}]}},
                        "tags": {"type": "array", "items": {"type": "string"}},
                        "target": {"$ref": "#/$defs/Target"}
                      }
                    }
                    """);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
