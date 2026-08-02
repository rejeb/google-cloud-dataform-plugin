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
package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.SortableAction;
import io.github.rejeb.dataform.language.compilation.model.Target;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/**
 * The planner decides what a refresh re-reads. Its inputs are the compiled actions and what the
 * previous run recorded, so it is exercised without touching BigQuery.
 */
public class SchemaRefreshPlannerTest extends BasePlatformTestCase {

    private SchemaCacheStore cache;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        cache = new SchemaCacheStore(getProject());
    }

    private SchemaRefreshPlanner planner() {
        return new SchemaRefreshPlanner(getProject().getBasePath(), cache);
    }

    private static List<String> namesOf(List<List<SortableAction>> waves) {
        return waves.stream()
                .flatMap(List::stream)
                .map(a -> a.target().getFullName())
                .toList();
    }

    public void testAForcedRefreshPlansEveryAction() {
        List<SortableAction> actions = List.of(action("orders", "definitions/orders.sqlx"),
                action("users", "definitions/users.sqlx"));

        List<List<SortableAction>> waves = planner().planWaves(actions, true, Set.of());

        assertEquals(List.of("p.d.orders", "p.d.users"), namesOf(waves));
    }

    public void testAnActionWhoseSourceIsUnknownIsPlanned() {
        List<SortableAction> actions = List.of(action("orders", "definitions/orders.sqlx"));

        List<List<SortableAction>> waves = planner().planWaves(actions, false, Set.of());

        assertEquals("an action never extracted must be dry-run",
                List.of("p.d.orders"), namesOf(waves));
    }

    public void testAnActionOfAFileThatFailedToCompileIsNotPlanned() {
        List<SortableAction> actions = List.of(action("orders", "definitions/orders.sqlx"),
                action("users", "definitions/users.sqlx"));

        List<List<SortableAction>> waves =
                planner().planWaves(actions, true, Set.of("definitions/orders.sqlx"));

        assertEquals(List.of("p.d.users"), namesOf(waves));
    }

    public void testAFailedFileIsMatchedAcrossPathSeparators() {
        List<SortableAction> actions = List.of(action("orders", "definitions\\orders.sqlx"));

        List<List<SortableAction>> waves =
                planner().planWaves(actions, true, Set.of("definitions/orders.sqlx"));

        assertTrue("a Windows file name must match the compiler's path", namesOf(waves).isEmpty());
    }

    public void testDependenciesAreDryRunBeforeTheActionsReadingThem() {
        SortableAction upstream = action("orders", "definitions/orders.sqlx");
        SortableAction downstream = action("mart", "definitions/mart.sqlx",
                upstream.target());

        List<List<SortableAction>> waves =
                planner().planWaves(List.of(downstream, upstream), true, Set.of());

        assertEquals("a dependency belongs to an earlier wave than its reader", 2, waves.size());
        assertEquals(List.of("p.d.orders"), namesOf(List.of(waves.get(0))));
        assertEquals(List.of("p.d.mart"), namesOf(List.of(waves.get(1))));
    }

    public void testIndependentActionsShareOneWave() {
        List<SortableAction> actions = List.of(action("orders", "definitions/orders.sqlx"),
                action("users", "definitions/users.sqlx"));

        List<List<SortableAction>> waves = planner().planWaves(actions, true, Set.of());

        assertEquals("independent actions can be dry-run together", 1, waves.size());
    }

    public void testNothingToRefreshPlansNoWave() {
        assertTrue(planner().planWaves(List.of(), true, Set.of()).isEmpty());
    }

    private static SortableAction action(String name, String fileName, Target... dependencies) {
        CompiledTable table = new CompiledTable();
        set(table, "target", target(name));
        set(table, "fileName", fileName);
        set(table, "dependencyTargets", List.of(dependencies));
        return SortableAction.of(table);
    }

    private static Target target(String name) {
        Target t = new Target();
        set(t, "schema", "d");
        set(t, "name", name);
        set(t, "database", "p");
        return t;
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set " + field + " on " + target.getClass(), e);
        }
    }
}
