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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.setup.DataformInterpreterManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProtoParserImplTest {

    @TempDir
    Path coreDir;

    @Test
    public void noProtoFileMeansNothingToParse() {
        ProtoParserImpl parser = new ProtoParserImpl(project(false));
        assertFalse(parser.configProtoFileExists());
        assertTrue(parser.parse().isEmpty());
    }

    @Test
    public void parsesMessagesEnumsAndTheirComments() throws Exception {
        ProtoModel.ProtoFile file = parse("""
                syntax = "proto3";
                package dataform;
                option java_package = "com.dataform.protos";
                import "google/protobuf/struct.proto";

                // Top level
                // message.
                message Outer {
                  // The name.
                  string name = 1;
                  repeated string tags = 2;
                  map<string, string> labels = 3;
                  google.protobuf.Struct extra = 4;

                  message Inner {
                    Kind kind = 1;
                  }

                  enum Kind {
                    // first
                    A = 0;
                    B = 1;
                  }

                  oneof choice {
                    Inner inner = 5;
                    int32 number = 6;
                  }
                }

                enum Level {
                  LOW = 0;
                  HIGH = 1;
                }
                """);

        assertEquals(1, file.messages.size());
        ProtoModel.ProtoMessage outer = file.messages.get(0);
        assertEquals("Outer", outer.name);
        assertEquals("Outer", outer.qualifiedName);
        assertEquals("Top level message.", outer.description);

        List<String> fields = outer.fields.stream().map(f -> f.name).toList();
        assertEquals(List.of("name", "tags", "labels", "extra", "inner", "number"), fields);

        ProtoModel.ProtoField name = outer.fields.get(0);
        assertEquals("string", name.type);
        assertEquals("The name.", name.description);
        assertFalse(name.repeated);
        assertFalse(name.isOneof);

        ProtoModel.ProtoField tags = outer.fields.get(1);
        assertTrue(tags.repeated);
        assertEquals("string", tags.type);

        ProtoModel.ProtoField labels = outer.fields.get(2);
        assertTrue(labels.isMap);
        assertEquals("string", labels.mapKeyType);
        assertEquals("string", labels.mapValueType);

        assertEquals("google.protobuf.Struct", outer.fields.get(3).type);

        ProtoModel.ProtoField inner = outer.fields.get(4);
        assertTrue(inner.isOneof);
        assertEquals("Inner", inner.type);
        assertTrue(outer.fields.get(5).isOneof);

        assertEquals(1, outer.nestedMessages.size());
        assertEquals("Outer.Inner", outer.nestedMessages.get(0).qualifiedName);
        assertEquals("Kind", outer.nestedMessages.get(0).fields.get(0).type);

        assertEquals(1, outer.nestedEnums.size());
        assertEquals("Outer.Kind", outer.nestedEnums.get(0).qualifiedName);
        assertEquals(List.of("A", "B"), outer.nestedEnums.get(0).values);

        assertEquals(1, file.enums.size());
        assertEquals("Level", file.enums.get(0).qualifiedName);
        assertEquals(List.of("LOW", "HIGH"), file.enums.get(0).values);
    }

    @Test
    public void snakeCaseFieldNamesGetACamelCaseName() throws Exception {
        ProtoModel.ProtoFile file = parse("""
                message M {
                  string default_assertion_dataset = 1;
                  repeated string unique_key = 2;
                  map<string, string> additional_options = 3;
                }
                """);
        List<String> camel = file.messages.get(0).fields.stream().map(f -> f.camelName).toList();
        assertEquals(List.of("defaultAssertionDataset", "uniqueKey", "additionalOptions"), camel);
    }

    @Test
    public void commentsBelongToTheNextDeclarationOnly() throws Exception {
        ProtoModel.ProtoFile file = parse("""
                // On the message.
                message M {
                  // On a.
                  string a = 1;
                  string b = 2;
                }
                """);
        ProtoModel.ProtoMessage m = file.messages.get(0);
        assertEquals("On the message.", m.description);
        assertEquals("On a.", m.fields.get(0).description);
        assertEquals("", m.fields.get(1).description);
    }

    @Test
    public void parsesTheRealDataformProto() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/dataform/configs.proto")) {
            assertNotNull(stream, "configs.proto test resource is missing");
            Files.write(coreDir.resolve("configs.proto"), stream.readAllBytes());
        }
        ProtoParserImpl parser = new ProtoParserImpl(project(true));
        assertTrue(parser.configProtoFileExists());
        ProtoModel.ProtoFile file = parser.parse().orElseThrow();

        ProtoModel.ProtoMessage actionConfig = file.messages.stream()
                .filter(m -> m.name.equals("ActionConfig"))
                .findFirst().orElseThrow();
        List<String> nested = actionConfig.nestedMessages.stream().map(m -> m.qualifiedName).toList();
        assertTrue(nested.containsAll(List.of(
                "ActionConfig.TableConfig", "ActionConfig.ViewConfig",
                "ActionConfig.IncrementalTableConfig", "ActionConfig.AssertionConfig",
                "ActionConfig.OperationConfig", "ActionConfig.DeclarationConfig")), nested.toString());

        ProtoModel.ProtoEnum onSchemaChange = actionConfig.nestedEnums.stream()
                .filter(e -> e.name.equals("OnSchemaChange"))
                .findFirst().orElseThrow();
        assertEquals(List.of("IGNORE", "FAIL", "EXTEND", "SYNCHRONIZE"), onSchemaChange.values);

        List<String> oneofFields = actionConfig.fields.stream()
                .filter(f -> f.isOneof).map(f -> f.camelName).toList();
        assertTrue(oneofFields.containsAll(List.of("table", "view", "incrementalTable", "assertion")),
                oneofFields.toString());

        ProtoModel.ProtoMessage workflow = file.messages.stream()
                .filter(m -> m.name.equals("WorkflowSettings"))
                .findFirst().orElseThrow();
        ProtoModel.ProtoField vars = workflow.fields.stream()
                .filter(f -> f.name.equals("vars")).findFirst().orElseThrow();
        assertTrue(vars.isMap);
        assertTrue(workflow.fields.stream()
                .filter(f -> f.name.equals("default_project"))
                .findFirst().orElseThrow().description.startsWith("Required."));
    }

    private ProtoModel.ProtoFile parse(String proto) throws Exception {
        Files.writeString(coreDir.resolve("configs.proto"), proto, StandardCharsets.UTF_8);
        return new ProtoParserImpl(project(true)).parse().orElseThrow();
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
        return project;
    }
}
