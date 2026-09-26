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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The file-name lookups of the compiled graph go through an index by last path segment. They must
 * answer exactly what matching every action one by one answered.
 */
public class CompiledGraphFileNameIndexTest {

    private static final String ORDERS = "/work/project/definitions/orders.sqlx";

    private static void set(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Target target(String name) {
        Target target = new Target();
        set(target, "database", "p");
        set(target, "schema", "d");
        set(target, "name", name);
        return target;
    }

    private static CompiledTable table(String name, String fileName) {
        CompiledTable table = new CompiledTable();
        set(table, "target", target(name));
        set(table, "fileName", fileName);
        return table;
    }

    private static CompiledGraph graph(List<CompiledTable> tables) {
        CompiledGraph graph = new CompiledGraph();
        set(graph, "tables", tables);
        return graph;
    }

    @Test
    void findsOnlyTheActionsOfTheFileAmongThoseSharingItsName() {
        CompiledTable orders = table("orders", "definitions/orders.sqlx");
        CompiledTable nestedOrders = table("staging_orders", "definitions/staging/orders.sqlx");
        CompiledTable oldOrders = table("old_orders", "definitions/old_orders.sqlx");
        CompiledGraph graph = graph(List.of(orders, nestedOrders, oldOrders));

        assertEquals(List.of(orders), graph.findTableByFileName(ORDERS));
        assertEquals(List.of(nestedOrders),
                graph.findTableByFileName("/work/project/definitions/staging/orders.sqlx"));
        assertEquals(List.of(orders), graph.findTableByFileName("definitions/orders.sqlx"));
        assertTrue(graph.findTableByFileName("orders.sqlx").isEmpty());
    }

    @Test
    void matchesWindowsFileNamesAndWindowsPaths() {
        CompiledTable orders = table("orders", "definitions\\orders.sqlx");
        CompiledGraph graph = graph(List.of(orders));

        assertEquals(List.of(orders), graph.findTableByFileName(ORDERS));
        assertEquals(List.of(orders), graph.findTableByFileName("C:\\work\\project\\definitions\\orders.sqlx"));
    }

    @Test
    void keepsTheCompilerOrderOfSeveralActionsInOneFile() {
        CompiledTable first = table("first", "definitions/orders.sqlx");
        CompiledTable second = table("second", "definitions/orders.sqlx");
        CompiledGraph graph = graph(List.of(first, second));

        assertEquals(List.of(first, second), graph.findTableByFileName(ORDERS));
    }

    @Test
    void answersNothingForAnUnknownOrMissingFileName() {
        CompiledGraph graph = graph(List.of(table("orders", "definitions/orders.sqlx"), new CompiledTable()));

        assertTrue(graph.findTableByFileName("/work/project/definitions/customers.sqlx").isEmpty());
        assertTrue(graph.findTableByFileName(null).isEmpty());
        assertTrue(graph.findDeclarationByFileName(ORDERS).isEmpty());
    }

    @Test
    void seesActionsAddedOrListsReplacedAfterAFirstLookup() {
        CompiledTable orders = table("orders", "definitions/orders.sqlx");
        List<CompiledTable> tables = new ArrayList<>(List.of(orders));
        CompiledGraph graph = graph(tables);
        assertEquals(List.of(orders), graph.findTableByFileName(ORDERS));

        CompiledTable more = table("more_orders", "definitions/orders.sqlx");
        tables.add(more);
        assertEquals(List.of(orders, more), graph.findTableByFileName(ORDERS));

        CompiledTable replaced = table("replaced", "definitions/orders.sqlx");
        set(graph, "tables", List.of(replaced));
        assertEquals(List.of(replaced), graph.findTableByFileName(ORDERS));
    }

    @Test
    void findsATargetByItsNameThenByItsCanonicalName() {
        CompiledTable orders = table("orders", "definitions/orders.sqlx");
        CompiledTable prefixed = table("dev_customers", "definitions/customers.sqlx");
        set(prefixed, "canonicalTarget", target("customers"));
        CompiledGraph graph = graph(List.of(orders, prefixed));

        assertEquals("p.d.orders", graph.findTargetByRefName("orders").orElseThrow().getFullName());
        assertEquals("p.d.dev_customers", graph.findTargetByRefName("customers").orElseThrow().getFullName());
        assertTrue(graph.findTargetByRefName("missing").isEmpty());
    }
}
