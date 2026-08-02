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
package io.github.rejeb.dataform.language.documentation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class DataformConfigKeyDocumentationTest extends BasePlatformTestCase {

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
    private final DataformConfigKeyDocumentationTargetProvider provider =
            new DataformConfigKeyDocumentationTargetProvider();

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
    }

    private DataformDasTable table() {
        return new DataformDasTable(PsiManager.getInstance(getProject()), "customers",
                List.of(
                        new ColumnInfo("customer_id", "INTEGER", "REQUIRED", "Unique customer id"),
                        new ColumnInfo("signup_date", "DATE", "NULLABLE", null),
                        new ColumnInfo("address", "RECORD", "NULLABLE", null,
                                List.of(new ColumnInfo("city", "STRING", "NULLABLE", null)))),
                null);
    }

    private List<? extends DocumentationTarget> targetsAt(String configBlock) {
        PsiFile file = myFixture.addFileToProject(
                "case" + fileCounter.incrementAndGet() + "/definitions/customers.sqlx",
                configBlock + "\n\nSELECT 1\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return provider.documentationTargets(myFixture.getFile(),
                myFixture.getEditor().getCaretModel().getOffset());
    }

    private String htmlAt(String configBlock) {
        List<? extends DocumentationTarget> targets = targetsAt(configBlock);
        assertEquals("expected one target, got " + targets, 1, targets.size());
        assertInstanceOf(targets.getFirst(), ConfigKeyDocumentationTarget.class);
        return ((ConfigKeyDocumentationTarget) targets.getFirst()).html();
    }

    public void testColumnsKeyExplainsHowColumnsAreAdded() {
        String html = htmlAt("config {\n  type: \"table\",\n  col<caret>umns: {\n  }\n}");

        assertTrue(html, html.contains("Usage"));
        assertTrue(html, html.contains("customer_id: &quot;Unique customer identifier&quot;"));
        assertTrue("the descriptor form must be shown, " + html,
                html.contains("bigqueryPolicyTags"));
    }

    public void testColumnsKeyListsTheColumnsOfTheAction() {
        String html = htmlAt("config {\n  type: \"table\",\n  col<caret>umns: {\n  }\n}");

        assertTrue(html, html.contains(listed("customer_id")));
        assertTrue(html, html.contains(listed("signup_date")));
        assertTrue(html, html.contains("INTEGER"));
    }

    public void testNestedColumnsKeyListsTheRecordFields() {
        String html = htmlAt("config {\n  type: \"table\",\n  columns: {\n"
                + "    address: {\n      col<caret>umns: {\n      }\n    }\n  }\n}");

        assertTrue(html, html.contains(listed("city")));
        assertFalse("top level columns must not leak, " + html,
                html.contains(listed("signup_date")));
    }

    public void testColumnNameKeysExplainWhatTheyTakeAndListTheColumns() {
        String html = htmlAt("config {\n  type: \"table\",\n  clus<caret>terBy: []\n}");

        assertTrue(html, html.contains("clusterBy"));
        assertTrue(html, html.contains("at most four"));
        assertTrue(html, html.contains(listed("customer_id")));
    }

    /**
     * How a column reads in the rendered column table, as opposed to in the usage example.
     */
    private static String listed(String column) {
        return "<code>" + column + "</code>";
    }

    public void testAssertionUniqueKeyIsDocumentedAsAnAssertion() {
        String html = htmlAt("config {\n  type: \"table\",\n  assertions: {\n"
                + "    uniq<caret>ueKey: []\n  }\n}");

        assertTrue("the merge key wording must not be used, " + html,
                html.contains("must be unique across the output"));
    }

    public void testIncrementalUniqueKeyIsDocumentedAsAMergeKey() {
        String html = htmlAt("config {\n  type: \"incremental\",\n  uniq<caret>ueKey: []\n}");

        assertTrue(html, html.contains("incremental merge"));
    }

    public void testKnownColumnEntryIsDocumentedWithItsColumn() {
        List<? extends DocumentationTarget> targets = targetsAt("config {\n  type: \"table\",\n"
                + "  columns: {\n    customer<caret>_id: \"described\"\n  }\n}");

        assertEquals("expected one target, got " + targets, 1, targets.size());
        assertInstanceOf(targets.getFirst(), DataformColumnDocumentationTarget.class);
    }

    public void testUnknownColumnEntryFallsBackToTheColumnsUsage() {
        String html = htmlAt("config {\n  type: \"table\",\n"
                + "  columns: {\n    unkn<caret>own_column: \"described\"\n  }\n}");

        assertTrue("the syntax must still be explained, " + html, html.contains("Usage"));
        assertTrue(html, html.contains(listed("customer_id")));
    }

    public void testNothingIsDocumentedOutsideAConfigBlock() {
        PsiFile file = myFixture.addFileToProject("definitions/other.sqlx",
                "config {\n  type: \"table\"\n}\n\nSELECT 1 AS <caret>x\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

        assertEmpty(provider.documentationTargets(myFixture.getFile(),
                myFixture.getEditor().getCaretModel().getOffset()));
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
