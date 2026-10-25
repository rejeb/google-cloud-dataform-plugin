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
package io.github.rejeb.dataform.language.compilation.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Large projects hold tables of the same name in several datasets, and often prefix or suffix the
 * names they compile to. A reference must reach the action it designates in both cases.
 */
public class CompiledGraphReferenceTest {

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException("set " + field, e);
        }
    }

    private static Target target(String database, String schema, String name) {
        Target target = new Target();
        set(target, "database", database);
        set(target, "schema", schema);
        set(target, "name", name);
        return target;
    }

    private static CompiledTable table(Target target, Target canonical) {
        CompiledTable table = new CompiledTable();
        set(table, "target", target);
        set(table, "canonicalTarget", canonical);
        return table;
    }

    private static Declaration declaration(Target target) {
        Declaration declaration = new Declaration();
        set(declaration, "target", target);
        set(declaration, "canonicalTarget", target);
        return declaration;
    }

    private static CompiledGraph graph(List<CompiledTable> tables, List<Declaration> declarations) {
        CompiledGraph graph = new CompiledGraph();
        set(graph, "tables", tables);
        set(graph, "declarations", declarations);
        return graph;
    }

    @Test
    public void aSchemaQualifiedReferencePicksTheTableOfThatDataset() {
        CompiledGraph graph = graph(List.of(
                table(target("p", "staging", "customers"), target("p", "staging", "customers")),
                table(target("p", "mart", "customers"), target("p", "mart", "customers"))), List.of());

        Optional<Target> found = graph.findTargetByReference(new ActionReference(null, "mart", "customers"));

        assertEquals("p.mart.customers", found.orElseThrow().getFullName());
    }

    @Test
    public void aFullyQualifiedReferencePicksTheTableOfThatDatabase() {
        CompiledGraph graph = graph(List.of(), List.of(
                declaration(target("a", "raw", "events")),
                declaration(target("b", "raw", "events"))));

        Optional<Target> found = graph.findTargetByReference(new ActionReference("b", "raw", "events"));

        assertEquals("b.raw.events", found.orElseThrow().getFullName());
    }

    @Test
    public void aReferenceByTheNameBeforeTheTablePrefixReachesThePrefixedTable() {
        CompiledGraph graph = graph(List.of(
                table(target("p", "mart", "dev_customers"), target("p", "mart", "customers"))), List.of());

        assertEquals("p.mart.dev_customers",
                graph.findTargetByRefName("customers").orElseThrow().getFullName());
        assertEquals("p.mart.dev_customers",
                graph.findTableByName("customers").orElseThrow().getTarget().getFullName());
    }

    @Test
    public void aReferenceByTheSchemaBeforeTheSuffixReachesTheSuffixedTable() {
        CompiledGraph graph = graph(List.of(
                table(target("p", "mart_dev", "customers"), target("p", "mart", "customers"))), List.of());

        Optional<Target> found = graph.findTargetByReference(new ActionReference(null, "mart", "customers"));

        assertEquals("p.mart_dev.customers", found.orElseThrow().getFullName());
    }

    @Test
    public void theCompiledNameWinsOverAnotherActionsCanonicalName() {
        CompiledGraph graph = graph(List.of(
                table(target("p", "mart", "dev_customers"), target("p", "mart", "customers")),
                table(target("p", "mart", "customers"), target("p", "mart", "customers_src"))), List.of());

        assertEquals("p.mart.customers",
                graph.findTargetByRefName("customers").orElseThrow().getFullName());
    }

    @Test
    public void aReferenceToAnotherDatasetFindsNothing() {
        CompiledGraph graph = graph(List.of(
                table(target("p", "mart", "customers"), target("p", "mart", "customers"))), List.of());

        assertTrue(graph.findTargetByReference(new ActionReference(null, "staging", "customers")).isEmpty());
    }

    /**
     * Ctrl+Click on {@code ref()} and on a unit test {@code input} both open the file of the action
     * a name designates, so both must agree: an action compiled to the name wins over a table whose
     * name before its prefix is that name.
     */
    @Test
    public void theFileOfAReferenceIsThatOfTheActionCompiledToTheNameFirst() {
        CompiledTable prefixed = table(target("p", "d", "dev_orders"), target("p", "d", "orders"));
        set(prefixed, "fileName", "definitions/orders.sqlx");
        Declaration declared = declaration(target("p", "d", "orders"));
        set(declared, "fileName", "definitions/sources.js");
        CompiledGraph graph = graph(List.of(prefixed), List.of(declared));

        assertEquals(Optional.of("definitions/sources.js"), graph.fileOf(ActionReference.named("orders")));
        assertEquals(Optional.of("definitions/orders.sqlx"), graph.fileOf(ActionReference.named("dev_orders")));
    }

    @Test
    public void theFileOfAnOperationIsFoundByItsName() {
        CompiledOperation operation = new CompiledOperation();
        set(operation, "target", target("p", "d", "ops"));
        set(operation, "fileName", "definitions/ops.sqlx");
        CompiledGraph graph = graph(List.of(), List.of());
        set(graph, "operations", List.of(operation));

        assertEquals(Optional.of("definitions/ops.sqlx"), graph.fileOf(ActionReference.named("ops")));
    }
}
