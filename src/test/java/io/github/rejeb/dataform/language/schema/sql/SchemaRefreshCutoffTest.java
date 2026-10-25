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

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.SortableAction;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A refresh dry-runs an action planned for its dependencies only when one of them came out with
 * other columns than the cached ones.
 */
public class SchemaRefreshCutoffTest extends BasePlatformTestCase {

    private static final List<ColumnInfo> ORDERS = List.of(new ColumnInfo("order_id", "STRING", "NULLABLE", null));
    private static final List<ColumnInfo> ORDERS_WIDER = List.of(
            new ColumnInfo("order_id", "STRING", "NULLABLE", null),
            new ColumnInfo("amount", "NUMERIC", "NULLABLE", null));
    private static final List<ColumnInfo> MART = List.of(new ColumnInfo("total", "NUMERIC", "NULLABLE", null));

    private final List<String> sent = new CopyOnWriteArrayList<>();
    private volatile List<ColumnInfo> ordersColumns = ORDERS;
    private SortableAction orders;
    private SortableAction mart;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        orders = action("orders", "SELECT 'o' AS order_id");
        mart = action("mart", "SELECT 1 AS total FROM `p.d.orders`", orders.target());
        run(Set.of("p.d.orders", "p.d.mart"));
        sent.clear();
    }

    public void testADependentIsSkippedWhenItsDependencyKeptItsColumns() {
        run(Set.of("p.d.orders"));

        assertEquals(List.of("orders"), sent);
    }

    public void testADependentIsDryRunWhenItsDependencyChangedItsColumns() {
        ordersColumns = ORDERS_WIDER;

        run(Set.of("p.d.orders"));

        assertEquals(List.of("orders", "mart"), sent);
    }

    public void testAModifiedActionIsDryRunWhateverItsDependenciesReturn() {
        run(Set.of("p.d.orders", "p.d.mart"));

        assertEquals(List.of("orders", "mart"), sent);
    }

    private void run(Set<String> modified) {
        ExtractionContext ctx = new ExtractionContext("p", null, (projectId, location, query) -> {
            boolean isMart = query.contains("total");
            sent.add(isMart ? "mart" : "orders");
            return DryRunResult.success(isMart ? MART : ordersColumns);
        });
        DataformTableSchemaServiceImpl service =
                (DataformTableSchemaServiceImpl) DataformTableSchemaService.getInstance(getProject());
        service.processAllWaves(new SchemaRefreshPlan(List.of(List.of(orders), List.of(mart)), modified),
                ctx, new EmptyProgressIndicator());
    }

    private static SortableAction action(String name, String query, Target... dependencies) {
        CompiledTable table = new CompiledTable();
        set(table, "target", target(name));
        set(table, "query", query);
        set(table, "dependencyTargets", List.of(dependencies));
        return SortableAction.of(table);
    }

    private static Target target(String name) {
        Target target = new Target();
        set(target, "database", "p");
        set(target, "schema", "d");
        set(target, "name", name);
        return target;
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException("set " + field, e);
        }
    }
}
