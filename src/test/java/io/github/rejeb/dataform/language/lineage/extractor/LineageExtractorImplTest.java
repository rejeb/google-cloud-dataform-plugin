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
package io.github.rejeb.dataform.language.lineage.extractor;

import io.github.rejeb.dataform.language.compilation.model.CompiledAssertion;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledOperation;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Declaration;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineageExtractorImplTest {

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set " + field + " on " + target.getClass(), e);
        }
    }

    private static Target target(String schema, String name, String database) {
        Target t = new Target();
        set(t, "schema", schema);
        set(t, "name", name);
        set(t, "database", database);
        return t;
    }

    @Test
    void extractsTypesSchemaTagsAndOperationNodes() {
        Target declTarget = target("ds", "decl", "proj");
        Target tableTarget = target("ds", "tbl", "proj");
        Target opTarget = target("ds", "op", "proj");
        Target assertTarget = target("ds", "assert", "proj");

        Declaration declaration = new Declaration();
        set(declaration, "target", declTarget);
        set(declaration, "fileName", "definitions/decl.sqlx");

        CompiledTable table = new CompiledTable();
        set(table, "target", tableTarget);
        set(table, "enumType", "incremental");
        set(table, "tags", List.of("daily"));
        set(table, "fileName", "definitions/tbl.sqlx");
        set(table, "dependencyTargets", List.of(declTarget));

        CompiledOperation operation = new CompiledOperation();
        set(operation, "target", opTarget);
        set(operation, "tags", List.of());
        set(operation, "fileName", "definitions/op.sqlx");
        set(operation, "dependencyTargets", List.of());
        set(operation, "hasOutput", true);

        Target noOutTarget = target("ds", "noout", "proj");
        CompiledOperation noOutput = new CompiledOperation();
        set(noOutput, "target", noOutTarget);
        set(noOutput, "tags", List.of());
        set(noOutput, "fileName", "definitions/noout.sqlx");
        set(noOutput, "dependencyTargets", List.of());
        set(noOutput, "hasOutput", false);

        CompiledAssertion assertion = new CompiledAssertion();
        set(assertion, "target", assertTarget);
        set(assertion, "tags", List.of());
        set(assertion, "fileName", "definitions/assert.sqlx");
        set(assertion, "dependencyTargets", List.of(tableTarget));

        CompiledGraph graph = new CompiledGraph();
        set(graph, "declarations", List.of(declaration));
        set(graph, "tables", List.of(table));
        set(graph, "operations", List.of(operation, noOutput));
        set(graph, "assertions", List.of(assertion));

        LineageGraph lg = new LineageExtractorImpl().extract(graph);

        LineageNode tableNode = lg.node(LineageNode.idOf("proj.ds.tbl"));
        assertNotNull(tableNode);
        assertEquals("incremental", tableNode.dataformType());
        assertEquals("ds", tableNode.schema());
        assertEquals(List.of("daily"), tableNode.tags());

        LineageNode opNode = lg.node(LineageNode.idOf("proj.ds.op"));
        assertNotNull(opNode, "operation with output must be registered");
        assertEquals("operation", opNode.dataformType());
        assertNull(lg.node(LineageNode.idOf("proj.ds.noout")),
                "operation without output must be excluded");
        assertEquals("declaration", lg.node(LineageNode.idOf("proj.ds.decl")).dataformType());

        assertTrue(lg.predecessors(LineageNode.idOf("proj.ds.tbl"))
                .contains(LineageNode.idOf("proj.ds.decl")), "decl feeds tbl");
        assertTrue(lg.predecessors(LineageNode.idOf("proj.ds.assert"))
                .contains(LineageNode.idOf("proj.ds.tbl")), "tbl feeds assertion");
    }
}
