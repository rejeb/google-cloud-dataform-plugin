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

import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Two datasets holding a table of the same name: a reference qualified by its dataset must see the
 * columns of that table only, whichever of the two the schema cache happens to list first.
 */
public class DuplicateTableNameResolveTest extends BasePlatformTestCase {

    /**
     * Ignores the platform's complaint that the SQL plugin's own lookup element holds PSI. It is
     * raised by the SQL completion itself and has nothing to do with what is asserted here.
     */
    private static final LoggedErrorProcessor IGNORING_SQL_LOOKUP_LEAK = new LoggedErrorProcessor() {
        @Override
        public @NotNull Set<Action> processError(@NotNull String category,
                                                 @NotNull String message,
                                                 String @NotNull [] details,
                                                 @Nullable Throwable throwable) {
            return message.contains("is retaining PSI")
                    ? EnumSet.noneOf(Action.class)
                    : EnumSet.allOf(Action.class);
        }
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        installCompiledGraph();
        installSchema();
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

    private static Target target(String schema, String name) {
        Target target = new Target();
        set(target, "database", "proj");
        set(target, "schema", schema);
        set(target, "name", name);
        return target;
    }

    private static CompiledTable table(String schema) {
        CompiledTable table = new CompiledTable();
        set(table, "type", "table");
        set(table, "target", target(schema, "customers"));
        set(table, "canonicalTarget", target(schema, "customers"));
        set(table, "query", "SELECT 1");
        set(table, "fileName", "definitions/" + schema + "/customers.sqlx");
        set(table, "tags", List.of());
        set(table, "dependencyTargets", List.of());
        return table;
    }

    private void installCompiledGraph() {
        CompiledGraph graph = new CompiledGraph();
        set(graph, "tables", List.of(table("staging"), table("mart")));
        set(graph, "declarations", List.of());
        set(graph, "operations", List.of());
        set(graph, "assertions", List.of());
        set(getProject().getService(DataformCompilationService.class), "compiledGraph", graph);
    }

    private void installSchema() {
        DataformTableSchemaService.State state = new DataformTableSchemaService.State();
        state.schemaCacheJson = "{" + entry("staging", "staging_only") + "," + entry("mart", "mart_only") + "}";
        DataformTableSchemaService.getInstance(getProject()).loadState(state);
    }

    private static String entry(String schema, String column) {
        return "\"proj." + schema + ".customers\":{\"columns\":[{\"name\":\"" + column
                + "\",\"type\":\"STRING\",\"mode\":\"NULLABLE\",\"subFields\":[]}],"
                + "\"lastModified\":0,\"fileName\":\"definitions/" + schema + "/customers.sqlx\"}";
    }

    private List<String> selectListCompletion(String from) {
        myFixture.configureByText("probe.sqlx", "config { type: \"table\" }\nSELECT <caret> FROM " + from);
        LoggedErrorProcessor.executeWith(IGNORING_SQL_LOOKUP_LEAK, () -> myFixture.completeBasic());
        List<String> lookups = myFixture.getLookupElementStrings();
        assertNotNull("completing a select list must offer columns", lookups);
        return lookups;
    }

    public void testATwoArgumentRefSeesTheColumnsOfItsDatasetOnly() {
        List<String> lookups = selectListCompletion("${ref(\"mart\", \"customers\")}");
        assertTrue("the columns of mart.customers must be offered, got " + lookups,
                lookups.contains("mart_only"));
        assertFalse("staging.customers is another table, got " + lookups,
                lookups.contains("staging_only"));
    }

    public void testAnObjectRefSeesTheColumnsOfItsDatasetOnly() {
        List<String> lookups = selectListCompletion("${ref({schema: \"staging\", name: \"customers\"})}");
        assertTrue("the columns of staging.customers must be offered, got " + lookups,
                lookups.contains("staging_only"));
        assertFalse("mart.customers is another table, got " + lookups,
                lookups.contains("mart_only"));
    }

    public void testAHandWrittenQualifiedNameSeesTheColumnsOfItsDatasetOnly() {
        List<String> lookups = selectListCompletion("`proj.staging.customers`");
        assertTrue("the columns of staging.customers must be offered, got " + lookups,
                lookups.contains("staging_only"));
        assertFalse("mart.customers is another table, got " + lookups,
                lookups.contains("mart_only"));
    }

    public void testTablesOfTheSameNameInTwoDatasetsAreNotEquivalent() {
        List<ColumnInfo> columns = List.of();
        DataformDasTable staging = new DataformDasTable(getPsiManager(), "proj.staging.customers",
                "customers", columns, null);
        DataformDasTable mart = new DataformDasTable(getPsiManager(), "proj.mart.customers",
                "customers", columns, null);

        assertFalse(staging.isEquivalentTo(mart));
        assertTrue(staging.isEquivalentTo(new DataformDasTable(getPsiManager(), "proj.staging.customers",
                "customers", columns, null)));
    }
}
