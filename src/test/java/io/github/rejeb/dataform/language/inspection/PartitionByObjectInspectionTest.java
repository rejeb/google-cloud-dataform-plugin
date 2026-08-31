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
package io.github.rejeb.dataform.language.inspection;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class PartitionByObjectInspectionTest extends BasePlatformTestCase {

    private static final String TARGET = "proj.mart.customers";

    private static final String GRAPH_JSON = """
            {
              "tables": [
                {
                  "target": {"database": "proj", "schema": "mart", "name": "customers"},
                  "fileName": "definitions/customers.sqlx",
                  "tags": []
                }
              ],
              "assertions": [],
              "operations": [],
              "declarations": []
            }""";

    private final AtomicInteger fileCounter = new AtomicInteger();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CompiledGraph graph = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(GRAPH_JSON, CompiledGraph.class);
        ServiceContainerUtil.replaceService(getProject(), DataformCompilationService.class,
                new StubCompilationService(graph), getTestRootDisposable());
        ServiceContainerUtil.replaceService(getProject(), DataformTableSchemaService.class,
                new StubSchemaService(Map.of(TARGET, table())), getTestRootDisposable());
        myFixture.enableInspections(new PartitionByObjectInspection());
    }

    private DataformDasTable table() {
        return new DataformDasTable(PsiManager.getInstance(getProject()), "customers",
                List.of(
                        new ColumnInfo("customer_id", "INTEGER", "REQUIRED", null),
                        new ColumnInfo("signup_date", "DATE", "NULLABLE", null),
                        new ColumnInfo("order_ts", "TIMESTAMP", "NULLABLE", null)),
                null);
    }

    private void configure(String configBlock) {
        PsiFile file = myFixture.addFileToProject(
                "case" + fileCounter.incrementAndGet() + "/definitions/customers.sqlx",
                configBlock + "\n\nSELECT 1\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    }

    /**
     * Applies the replacement offered on the object and returns the SQLX file it produced.
     */
    private String applyFix(String configBlock) {
        configure(configBlock);
        IntentionAction fix = myFixture.findSingleIntention("Replace with");
        myFixture.launchAction(fix);
        return hostEditor().getDocument().getText();
    }

    private Editor hostEditor() {
        return myFixture.getEditor() instanceof EditorWindow window
                ? window.getDelegate()
                : myFixture.getEditor();
    }

    private long warningsIn(String configBlock) {
        configure(configBlock);
        return myFixture.doHighlighting().stream()
                .filter(info -> info.getDescription() != null
                        && info.getDescription().contains("not an object"))
                .count();
    }

    public void testAnObjectPartitionByIsReported() {
        assertEquals(1, warningsIn("config {\n  type: \"table\",\n  bigquery: {\n"
                + "    partitionBy: {\n      field: \"order_ts\",\n"
                + "      dataType: \"timestamp\",\n      granularity: \"day\"\n    }\n  }\n}"));
    }

    public void testAStringPartitionByIsLeftAlone() {
        assertEquals(0, warningsIn("config {\n  type: \"table\",\n"
                + "  partitionBy: \"TIMESTAMP_TRUNC(order_ts, DAY)\"\n}"));
    }

    public void testAnObjectUnderAnotherKeyIsLeftAlone() {
        assertEquals(0, warningsIn("config {\n  type: \"table\",\n"
                + "  assertions: {\n    nonNull: [\"customer_id\"]\n  }\n}"));
    }

    public void testTheFixWritesTheTruncationTheObjectStandsFor() {
        String text = applyFix("config {\n  type: \"table\",\n  bigquery: {\n"
                + "    partitionBy: {\n      field: \"order_ts\",<caret>\n"
                + "      dataType: \"timestamp\",\n      granularity: \"hour\"\n    }\n  }\n}");

        assertTrue("got [" + text + "]",
                text.contains("partitionBy: \"TIMESTAMP_TRUNC(order_ts, HOUR)\""));
    }

    public void testTheFixWritesADateColumnOnItsOwn() {
        String text = applyFix("config {\n  type: \"table\",\n"
                + "  partitionBy: {\n    field: \"signup_date\",<caret>\n"
                + "    dataType: \"date\",\n    granularity: \"day\"\n  }\n}");

        assertTrue("got [" + text + "]", text.contains("partitionBy: \"signup_date\""));
    }

    public void testTheFixReadsTheColumnTypeFromTheSchemaWhenTheObjectLeavesItOut() {
        String text = applyFix("config {\n  type: \"table\",\n"
                + "  partitionBy: {\n    field: \"order_ts\",<caret>\n    granularity: \"month\"\n  }\n}");

        assertTrue("got [" + text + "]",
                text.contains("partitionBy: \"TIMESTAMP_TRUNC(order_ts, MONTH)\""));
    }

    public void testTheFixWritesTheBoundsARangeCarries() {
        String text = applyFix("config {\n  type: \"table\",\n"
                + "  partitionBy: {\n    field: \"customer_id\",<caret>\n    dataType: \"int64\",\n"
                + "    range: {\n      start: 1,\n      end: 500,\n      interval: 25\n    }\n  }\n}");

        assertTrue("got [" + text + "]", text.contains(
                "partitionBy: \"RANGE_BUCKET(customer_id, GENERATE_ARRAY(1, 500, 25))\""));
    }

    /**
     * A column of a type no partition accepts leaves nothing to write, so the problem is reported
     * without a replacement rather than with a wrong one.
     */
    public void testAnObjectThatCannotBeWrittenIsReportedWithoutAFix() {
        configure("config {\n  type: \"table\",\n"
                + "  partitionBy: {\n    field: \"nickname\",<caret>\n    dataType: \"string\"\n  }\n}");

        assertTrue(myFixture.filterAvailableIntentions("Replace with").isEmpty());
    }

    private record StubCompilationService(CompiledGraph graph) implements DataformCompilationService {

        @Override
        public CompiledGraph compile(boolean forceRefresh) {
            return graph;
        }

        @Override
        public CompiledGraph getCompiledGraph() {
            return graph;
        }

        @Override
        public State getState() {
            return new State();
        }

        @Override
        public void loadState(@NotNull State state) {
        }

        @Override
        public void dispose() {
        }
    }

    private record StubSchemaService(Map<String, DataformDasTable> tables)
            implements DataformTableSchemaService {

        @Override
        public void refreshAsync(@NotNull CompiledGraph graph, boolean forceRefresh) {
        }

        @Override
        public void refreshAsync(@NotNull CompiledGraph graph,
                                 boolean forceRefresh,
                                 @NotNull Set<String> failedFileNames) {
        }

        @Override
        public @NotNull Map<String, DataformDasTable> getAllTables() {
            return tables;
        }

        @Override
        public void renameColumn(@NotNull Set<ColumnRef> columns, @NotNull String newName,
                                 @NotNull java.util.Collection<
                                         com.intellij.openapi.vfs.VirtualFile> written) {
        }

        @Override
        public State getState() {
            return new State();
        }

        @Override
        public void loadState(@NotNull State state) {
        }

        @Override
        public long getModificationCount() {
            return 0;
        }
    }
}
