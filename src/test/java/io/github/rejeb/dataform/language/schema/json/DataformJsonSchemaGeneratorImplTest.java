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
package io.github.rejeb.dataform.language.schema.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.setup.DataformInterpreterManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Generates the SQLX config schema from the real Dataform {@code configs.proto} and checks the
 * shape the completion and validation rely on.
 */
public class DataformJsonSchemaGeneratorImplTest {

    @TempDir
    Path coreDir;

    private ObjectNode schema;

    @BeforeEach
    public void setUp() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/dataform/configs.proto")) {
            assertNotNull(stream, "configs.proto test resource is missing");
            Files.write(coreDir.resolve("configs.proto"), stream.readAllBytes());
        }
        schema = new DataformJsonSchemaGeneratorImpl(project(true)).generateSqlxConfigSchema().orElseThrow();
    }

    @Test
    public void noProtoMeansNoSchema() {
        ProtoParser parser = mock(ProtoParser.class);
        when(parser.configProtoFileExists()).thenReturn(false);
        when(parser.parse()).thenReturn(Optional.empty());
        Project project = mock(Project.class);
        when(project.getService(ProtoParser.class)).thenReturn(parser);
        assertTrue(new DataformJsonSchemaGeneratorImpl(project).generateSqlxConfigSchema().isEmpty());
    }

    @Test
    public void schemaIsBuiltOnceAndReused() {
        DataformJsonSchemaGeneratorImpl generator = new DataformJsonSchemaGeneratorImpl(project(true));
        assertSame(generator.generateSqlxConfigSchema().orElseThrow(),
                generator.generateSqlxConfigSchema().orElseThrow());
    }

    @Test
    public void rootRequiresTheTypeAndOffersOneBranchPerActionType() {
        assertEquals("http://json-schema.org/draft-07/schema#", schema.get("$schema").asText());
        assertEquals("type", schema.get("required").get(0).asText());
        List<String> types = new ArrayList<>();
        for (JsonNode branch : schema.get("oneOf")) {
            types.add(branch.path("properties").path("type").path("enum").get(0).asText());
            assertFalse(branch.get("additionalProperties").asBoolean());
        }
        assertEquals(List.of("table", "view", "incremental", "assertion", "operations", "declaration", "test"), types);
    }

    @Test
    public void tableBranchMapsProtoFieldsToJsonTypes() {
        JsonNode props = branch("table").get("properties");
        assertEquals("string", props.get("name").get("type").asText());
        assertEquals("boolean", props.get("disabled").get("type").asText());
        assertEquals("integer", props.get("partitionExpirationDays").get("type").asText());
        assertEquals("array", props.get("clusterBy").get("type").asText());
        assertEquals("string", props.get("clusterBy").get("items").get("type").asText());
        assertEquals("object", props.get("labels").get("type").asText());
        assertEquals("string", props.get("labels").get("additionalProperties").get("type").asText());
        assertTrue(props.get("description").get("description").asText().length() > 0);
    }

    @Test
    public void compilerSetFieldsAreLeftOut() {
        JsonNode props = branch("table").get("properties");
        assertFalse(props.has("filename"));
    }

    @Test
    public void preAndPostOperationsAreAcceptedByDatasetActions() {
        for (String type : List.of("table", "view", "incremental")) {
            JsonNode props = branch(type).get("properties");
            assertEquals("array", props.get("preOperations").get("type").asText(), type);
            assertEquals("string", props.get("preOperations").get("items").get("type").asText(), type);
            assertEquals("array", props.get("postOperations").get("type").asText(), type);
        }
        assertFalse(branch("operations").get("properties").has("postOperations"));
        assertFalse(branch("assertion").get("properties").has("postOperations"));
    }

    @Test
    public void datasetProjectAndDependenciesHaveTheirSqlxAliases() {
        JsonNode props = branch("table").get("properties");
        assertEquals("string", props.get("schema").get("type").asText());
        assertTrue(props.get("schema").get("description").asText().startsWith("Alias for dataset"));
        assertEquals("string", props.get("database").get("type").asText());
        assertTrue(props.get("database").get("description").asText().startsWith("Alias for project"));
        assertEquals("array", props.get("dependencies").get("type").asText());
        JsonNode dependencyShapes = props.get("dependencies").get("items").get("oneOf");
        assertEquals("string", dependencyShapes.get(0).get("type").asText());
        assertEquals("#/$defs/ActionConfig_Target", dependencyShapes.get(1).get("$ref").asText());
        assertEquals("#/$defs/ActionConfig_Target",
                props.get("dependencyTargets").get("items").get("$ref").asText());
    }

    @Test
    public void columnsAreAMapOfDescriptors() {
        JsonNode columns = branch("table").get("properties").get("columns");
        assertEquals("object", columns.get("type").asText());
        assertEquals("#/$defs/SqlxColumnDescriptor", columns.get("additionalProperties").get("$ref").asText());

        JsonNode descriptor = schema.get("$defs").get("SqlxColumnDescriptor");
        assertEquals("string", descriptor.get("oneOf").get(0).get("type").asText());
        JsonNode record = descriptor.get("oneOf").get(1).get("properties");
        assertEquals("#/$defs/SqlxColumnDescriptor",
                record.get("columns").get("additionalProperties").get("$ref").asText());
        assertEquals("array", record.get("bigqueryPolicyTags").get("type").asText());
        assertEquals("array", record.get("tags").get("type").asText());
    }

    @Test
    public void nestedMessagesBecomeDefinitions() {
        JsonNode target = schema.get("$defs").get("ActionConfig_Target");
        assertEquals("object", target.get("type").asText());
        assertFalse(target.get("additionalProperties").asBoolean());
        assertEquals("boolean", target.get("properties").get("includeDependentAssertions").get("type").asText());

        JsonNode assertions = branch("table").get("properties").get("assertions");
        assertEquals("#/$defs/ActionConfig_TableAssertionsConfig", assertions.get("$ref").asText());
        assertTrue(schema.get("$defs").has("ActionConfig_TableAssertionsConfig"));
    }

    @Test
    public void assertionsAcceptTheLegacySqlxShapes() {
        JsonNode props = schema.get("$defs").get("ActionConfig_TableAssertionsConfig").get("properties");

        for (String name : List.of("uniqueKey", "nonNull")) {
            JsonNode shapes = props.get(name).get("oneOf");
            assertEquals("array", shapes.get(0).get("type").asText(), name);
            assertEquals("string", shapes.get(0).get("items").get("type").asText(), name);
            assertEquals("string", shapes.get(1).get("type").asText(), name);
            assertTrue(props.get(name).get("description").asText().length() > 0, name);
        }

        JsonNode uniqueKeys = props.get("uniqueKeys");
        assertEquals("array", uniqueKeys.get("type").asText());
        JsonNode itemShapes = uniqueKeys.get("items").get("oneOf");
        assertEquals("#/$defs/ActionConfig_TableAssertionsConfig_UniqueKey",
                itemShapes.get(0).get("$ref").asText());
        assertEquals("array", itemShapes.get(1).get("type").asText());
        assertEquals("string", itemShapes.get(1).get("items").get("type").asText());

        assertEquals("array", props.get("rowConditions").get("type").asText());
    }

    @Test
    public void enumsListTheirValues() {
        JsonNode onSchemaChange = branch("incremental").get("properties").get("onSchemaChange");
        assertEquals("string", onSchemaChange.get("type").asText());
        List<String> values = new ArrayList<>();
        onSchemaChange.get("enum").forEach(v -> values.add(v.asText()));
        assertEquals(List.of("IGNORE", "FAIL", "EXTEND", "SYNCHRONIZE"), values);
    }

    @Test
    public void bigQueryWrapperOnlyExistsForBigQueryActions() {
        assertTrue(branch("table").get("properties").has("bigquery"));
        assertTrue(branch("view").get("properties").has("bigquery"));
        assertTrue(branch("incremental").get("properties").has("bigquery"));
        assertFalse(branch("assertion").get("properties").has("bigquery"));
        assertFalse(branch("operations").get("properties").has("bigquery"));
        assertFalse(branch("declaration").get("properties").has("bigquery"));
    }

    @Test
    public void bigQueryWrapperDescribesPartitioningAndIceberg() {
        JsonNode table = branch("table").get("properties").get("bigquery").get("properties");
        JsonNode partitionBy = table.get("partitionBy").get("oneOf");
        assertEquals("string", partitionBy.get(0).get("type").asText());
        assertEquals("object", partitionBy.get(1).get("type").asText());
        assertEquals("field", partitionBy.get(1).get("required").get(0).asText());
        assertEquals("PARQUET", table.get("iceberg").get("properties").get("fileFormat").get("enum").get(0).asText());
        assertFalse(table.has("updatePartitionFilter"));

        JsonNode view = branch("view").get("properties").get("bigquery").get("properties");
        assertFalse(view.has("iceberg"));

        JsonNode incremental = branch("incremental").get("properties").get("bigquery").get("properties");
        assertTrue(incremental.has("updatePartitionFilter"));
        assertTrue(incremental.has("iceberg"));
    }

    @Test
    public void unitTestBranchHasOnlyTheTestConfigKeys() {
        JsonNode test = branch("test");
        List<String> keys = new ArrayList<>();
        test.get("properties").fieldNames().forEachRemaining(keys::add);

        assertEquals(List.of("type", "dataset", "name", "tags"), keys);
        assertEquals("dataset", test.get("required").get(1).asText());
        assertFalse(test.get("additionalProperties").asBoolean());
        assertEquals(2, test.get("properties").get("dataset").get("oneOf").size());
    }

    private JsonNode branch(String type) {
        for (JsonNode branch : schema.get("oneOf")) {
            if (type.equals(branch.path("properties").path("type").path("enum").get(0).asText())) {
                return branch;
            }
        }
        throw new AssertionError("No branch for " + type);
    }

    private Project project(boolean withCore) {
        DataformInterpreterManager manager = mock(DataformInterpreterManager.class);
        if (withCore) {
            VirtualFile core = mock(VirtualFile.class);
            when(core.toNioPath()).thenReturn(coreDir);
            when(manager.dataformCorePath()).thenReturn(Optional.of(core));
        } else {
            when(manager.dataformCorePath()).thenReturn(Optional.empty());
        }
        Project project = mock(Project.class);
        when(project.getService(DataformInterpreterManager.class)).thenReturn(manager);
        ProtoParserImpl parser = new ProtoParserImpl(project);
        when(project.getService(ProtoParser.class)).thenReturn(parser);
        return project;
    }
}
