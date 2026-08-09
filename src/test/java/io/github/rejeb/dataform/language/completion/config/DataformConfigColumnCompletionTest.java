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
package io.github.rejeb.dataform.language.completion.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ThreeState;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import io.github.rejeb.dataform.language.setup.DataformInterpreterManager;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class DataformConfigColumnCompletionTest extends BasePlatformTestCase {

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
        installProto();
    }

    /**
     * Installs the proto the config schema is generated from, so that the schema driven
     * contributors run as they do on a real project rather than bailing out on a missing schema.
     */
    private void installProto() throws Exception {
        var stream = getClass().getResourceAsStream("/dataform/configs.proto");
        assertNotNull("configs.proto test resource is missing", stream);
        Path coreDir = Files.createTempDirectory("dataform-core");
        try (stream) {
            Files.write(coreDir.resolve("configs.proto"), stream.readAllBytes());
        }
        VirtualFile coreVirtualDir =
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(coreDir);
        assertNotNull(coreVirtualDir);
        ServiceContainerUtil.replaceService(getProject(), DataformInterpreterManager.class,
                new StubInterpreterManager(coreVirtualDir), getTestRootDisposable());
    }

    private DataformDasTable table() {
        return new DataformDasTable(PsiManager.getInstance(getProject()), "customers",
                List.of(
                        new ColumnInfo("customer_id", "INTEGER", "REQUIRED", "Unique customer id"),
                        new ColumnInfo("signup_date", "DATE", "NULLABLE", null),
                        new ColumnInfo("address", "RECORD", "NULLABLE", null,
                                List.of(new ColumnInfo("city", "STRING", "NULLABLE", null),
                                        new ColumnInfo("zip_code", "STRING", "NULLABLE", null)))),
                null);
    }

    private List<String> completeIn(String configBlock) {
        configure(configBlock);
        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();
        return lookups == null ? List.of() : lookups;
    }

    /**
     * Each block gets its own directory since a test may complete several of them, while the
     * action file name the compiled graph matches on has to stay the same.
     */
    private void configure(String configBlock) {
        PsiFile file = myFixture.addFileToProject(
                "case" + fileCounter.incrementAndGet() + "/definitions/customers.sqlx",
                configBlock + "\n\nSELECT 1\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    }

    public void testColumnsMapProposesTheActionColumns() {
        List<String> lookups = completeIn("config {\n  type: \"table\",\n  columns: {\n    <caret>\n  }\n}");

        assertTrue("got " + lookups, lookups.contains("customer_id"));
        assertTrue("got " + lookups, lookups.contains("signup_date"));
        assertTrue("got " + lookups, lookups.contains("address"));
    }

    public void testAlreadyDescribedColumnsAreFilteredOut() {
        List<String> lookups = completeIn("config {\n  type: \"table\",\n  columns: {\n"
                + "    customer_id: \"known\",\n    <caret>\n  }\n}");

        assertFalse("got " + lookups, lookups.contains("customer_id"));
        assertTrue("got " + lookups, lookups.contains("signup_date"));
    }

    public void testNestedColumnsMapProposesTheRecordFields() {
        List<String> lookups = completeIn("config {\n  type: \"table\",\n  columns: {\n"
                + "    address: {\n      columns: {\n        <caret>\n      }\n    }\n  }\n}");

        assertTrue("got " + lookups, lookups.contains("city"));
        assertTrue("got " + lookups, lookups.contains("zip_code"));
        assertFalse("top level columns must not leak, got " + lookups,
                lookups.contains("customer_id"));
    }

    public void testClusterByProposesTheActionColumns() {
        List<String> lookups = completeIn(
                "config {\n  type: \"table\",\n  clusterBy: [\"<caret>\"]\n}");

        assertTrue("got " + lookups, lookups.contains("customer_id"));
        assertTrue("got " + lookups, lookups.contains("signup_date"));
    }

    public void testClusterByFiltersOutTheColumnsAlreadyListed() {
        List<String> lookups = completeIn(
                "config {\n  type: \"table\",\n  clusterBy: [\"customer_id\", \"<caret>\"]\n}");

        assertFalse("got " + lookups, lookups.contains("customer_id"));
        assertTrue("got " + lookups, lookups.contains("signup_date"));
    }

    public void testPartitionByProposesTheActionColumns() {
        List<String> lookups = completeIn(
                "config {\n  type: \"table\",\n  partitionBy: \"<caret>\"\n}");

        assertTrue("got " + lookups, lookups.contains("signup_date"));
    }

    public void testStructuredPartitionFieldProposesTheActionColumns() {
        List<String> lookups = completeIn("config {\n  type: \"table\",\n  bigquery: {\n"
                + "    partitionBy: {\n      field: \"<caret>\"\n    }\n  }\n}");

        assertTrue("got " + lookups, lookups.contains("signup_date"));
    }

    public void testAssertionKeysProposeTheActionColumns() {
        List<String> nonNull = completeIn("config {\n  type: \"table\",\n  assertions: {\n"
                + "    nonNull: [\"<caret>\"]\n  }\n}");
        assertTrue("got " + nonNull, nonNull.contains("customer_id"));

        List<String> uniqueKeys = completeIn("config {\n  type: \"table\",\n  assertions: {\n"
                + "    uniqueKeys: [[\"<caret>\"]]\n  }\n}");
        assertTrue("got " + uniqueKeys, uniqueKeys.contains("customer_id"));
    }

    public void testIncrementalUniqueKeyProposesTheActionColumns() {
        List<String> lookups = completeIn(
                "config {\n  type: \"incremental\",\n  uniqueKey: [\"<caret>\"]\n}");

        assertTrue("got " + lookups, lookups.contains("customer_id"));
    }

    public void testColumnNamesTypedInAStringOpenThePopupOnTheirOwn() {
        assertPopsUpAutomatically("config {\n  type: \"table\",\n  clusterBy: [\"<caret>\"]\n}");
        assertPopsUpAutomatically("config {\n  type: \"table\",\n  bigquery: {\n"
                + "    partitionBy: {\n      field: \"<caret>\"\n    }\n  }\n}");
        assertPopsUpAutomatically("config {\n  type: \"table\",\n  assertions: {\n"
                + "    nonNull: [\"<caret>\"]\n  }\n}");
    }

    public void testUnrelatedStringsDoNotOpenThePopupOnTheirOwn() {
        configure("config {\n  type: \"table\",\n  description: \"<caret>\"\n}");

        assertEquals(ThreeState.UNSURE, confidenceAtCaret());
    }

    /**
     * The popup opens on its own only when a confidence tells JavaScript not to skip it, which it
     * skips by default inside a string literal.
     */
    private void assertPopsUpAutomatically(String configBlock) {
        configure(configBlock);

        assertEquals("popup would stay closed for [" + configBlock + "]",
                ThreeState.NO, confidenceAtCaret());
    }

    private ThreeState confidenceAtCaret() {
        int offset = myFixture.getEditor().getCaretModel().getOffset();
        PsiFile file = myFixture.getFile();
        PsiElement context = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(file, offset);
        if (context == null) {
            context = file.findElementAt(offset);
        }
        assertNotNull("no config fragment at the caret", context);
        return new ConfigColumnCompletionConfidence().shouldSkipAutopopup(
                context, context.getContainingFile(), offset);
    }

    public void testColumnNamesAreProposedInAMultilineArray() {
        List<String> lookups = completeIn(
                "config {\n  type: \"table\",\n  clusterBy: [\n    \"<caret>\"\n  ]\n}");

        assertTrue("got " + lookups, lookups.contains("customer_id"));
    }

    public void testAColumnPickedOutsideQuotesIsQuoted() {
        List<String> lookups = completeIn("config {\n  type: \"table\",\n  clusterBy: [<caret>]\n}");

        assertTrue("got " + lookups, lookups.contains("\"customer_id\""));
    }

    public void testColumnsAreNotProposedForKeysThatNameNoColumn() {
        List<String> lookups = completeIn(
                "config {\n  type: \"table\",\n  description: \"<caret>\"\n}");

        assertFalse("got " + lookups, lookups.contains("customer_id"));
    }

    public void testColumnsAreNotProposedInsideAColumnDescriptor() {
        List<String> lookups = completeIn("config {\n  type: \"table\",\n  columns: {\n"
                + "    address: {\n      <caret>\n    }\n  }\n}");

        assertFalse("descriptor properties are not column names, got " + lookups,
                lookups.contains("customer_id"));
    }

    public void testPickingAColumnOpensItsDescription() {
        configure("config {\n  type: \"table\",\n  columns: {\n    customer_i<caret>\n  }\n}");
        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();
        if (lookups != null) {
            int index = lookups.indexOf("customer_id");
            assertTrue("customer_id not proposed, got " + lookups, index >= 0);
            myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[index]);
            myFixture.finishLookup('\n');
        }

        String text = hostEditor().getDocument().getText();
        assertTrue("got [" + text + "]", text.contains("    customer_id: \"\",\n"));
    }

    public void testNoColumnIsProposedWhenTheSchemaIsUnknown() {
        ServiceContainerUtil.replaceService(getProject(), DataformTableSchemaService.class,
                new StubSchemaService(Map.of()), getTestRootDisposable());

        List<String> lookups = completeIn(
                "config {\n  type: \"table\",\n  clusterBy: [\"<caret>\"]\n}");

        assertFalse("got " + lookups, lookups.contains("customer_id"));
    }

    private Editor hostEditor() {
        return myFixture.getEditor() instanceof EditorWindow window
                ? window.getDelegate()
                : myFixture.getEditor();
    }

    private record StubInterpreterManager(VirtualFile coreDir) implements DataformInterpreterManager {

        @Override
        public Optional<VirtualFile> dataformCorePath() {
            return Optional.of(coreDir);
        }

        @Override
        public String currentDataformCoreVersion() {
            return "3.0.0";
        }

        @Override
        public Optional<GeneralCommandLine> buildDataformCompileCommand() {
            return Optional.empty();
        }
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
