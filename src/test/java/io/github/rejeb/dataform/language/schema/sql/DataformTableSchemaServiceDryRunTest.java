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
import io.github.rejeb.dataform.language.compilation.model.CompiledOperation;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataformTableSchemaServiceDryRunTest extends BasePlatformTestCase {

    public void testAFailedTableDryRunKeepsTheQueryItSent() {
        CompiledTable table = new CompiledTable();
        set(table, "query", "SELECT custmer_id FROM t");
        set(table, "preOps", List.of("DECLARE x INT64 DEFAULT 1"));
        List<String> sent = new ArrayList<>();
        ExtractionContext ctx = new ExtractionContext("proj", null, (projectId, location, query) -> {
            sent.add(query);
            return DryRunResult.failure("Unrecognized name: custmer_id at [2:8]");
        });
        DataformTableSchemaServiceImpl service =
                (DataformTableSchemaServiceImpl) DataformTableSchemaService.getInstance(getProject());

        DryRunResult result = service.extractTableSchema(table, ctx, new HashMap<>());

        assertNotNull(result.query());
        assertEquals(sent.getFirst(), result.query().text());
        assertEquals("SELECT custmer_id FROM t", result.query().sourceText(DryRunQueryText.MAIN_QUERY));
    }

    public void testADeclaredSourceIsReadFromBigQueryRatherThanFromACachedCopy() {
        CompiledTable table = new CompiledTable();
        set(table, "query", "SELECT e.id, o.order_id FROM `p.d.raw_events` AS e JOIN `p.d.orders` AS o ON true");
        set(table, "dependencyTargets", List.of(target("raw_events"), target("orders")));
        List<String> sent = new ArrayList<>();
        ExtractionContext ctx = new ExtractionContext("proj", null, (projectId, location, query) -> {
            sent.add(query);
            return DryRunResult.failure("stop");
        }, Set.of("p.d.raw_events"));
        Map<String, List<ColumnInfo>> resolved = new HashMap<>(Map.of(
                "p.d.raw_events", List.of(new ColumnInfo("id", "STRING", "NULLABLE", null)),
                "p.d.orders", List.of(new ColumnInfo("order_id", "STRING", "NULLABLE", null))));
        DataformTableSchemaServiceImpl service =
                (DataformTableSchemaServiceImpl) DataformTableSchemaService.getInstance(getProject());

        service.extractTableSchema(table, ctx, resolved);

        assertTrue(sent.getFirst(), sent.getFirst().contains("FROM `p.d.raw_events` AS e"));
        assertTrue(sent.getFirst(), sent.getFirst().contains("JOIN _df_orders AS o"));
    }

    public void testAnOperationEndingInASelectIsDryRunFromItsSqlWithStubs() {
        CompiledOperation operation = new CompiledOperation();
        set(operation, "target", target("silver_customers"));
        set(operation, "queries", List.of("SELECT customer_id, emaisl FROM `p.d.orders` AS o"));
        set(operation, "dependencyTargets", List.of(target("orders")));
        List<String> sent = new ArrayList<>();
        ExtractionContext ctx = new ExtractionContext("proj", null, (projectId, location, query) -> {
            sent.add(query);
            return DryRunResult.failure("Unrecognized name: emaisl at [1:20]");
        });
        Map<String, List<ColumnInfo>> resolved = new HashMap<>(Map.of(
                "p.d.orders", List.of(new ColumnInfo("customer_id", "INT64", "NULLABLE", null))));
        DataformTableSchemaServiceImpl service =
                (DataformTableSchemaServiceImpl) DataformTableSchemaService.getInstance(getProject());

        DryRunResult result = service.extractOperationSchema(operation, ctx, resolved);

        assertEquals(1, sent.size());
        assertTrue(sent.getFirst(), sent.getFirst().contains("FROM _df_orders AS o"));
        assertNotNull(result.query());
        assertEquals("SELECT customer_id, emaisl FROM `p.d.orders` AS o",
                result.query().sourceText(DryRunQueryText.MAIN_QUERY));
        assertTrue(result.hasError());
    }

    public void testAnOperationWritingTablesStillReadsTheTableItCreates() {
        CompiledOperation operation = new CompiledOperation();
        set(operation, "target", target("history"));
        set(operation, "queries", List.of("CREATE TABLE IF NOT EXISTS `p.d.history` (a INT64);\n"
                + "MERGE `p.d.history` AS t USING (SELECT 1 AS a) AS s ON t.a = s.a WHEN NOT MATCHED THEN INSERT (a) VALUES (s.a)"));
        set(operation, "dependencyTargets", List.of());
        List<String> sent = new ArrayList<>();
        ExtractionContext ctx = new ExtractionContext("proj", null, (projectId, location, query) -> {
            sent.add(query);
            return DryRunResult.success(List.of(new ColumnInfo("a", "INT64", "NULLABLE", null)));
        });
        DataformTableSchemaServiceImpl service =
                (DataformTableSchemaServiceImpl) DataformTableSchemaService.getInstance(getProject());

        service.extractOperationSchema(operation, ctx, new HashMap<>());

        assertEquals(List.of("SELECT * FROM `p.d.history` LIMIT 0"), sent);
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
