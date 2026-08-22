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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.setup.DataformInterpreterManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class DataformConfigCompletionTest extends BasePlatformTestCase {

    private static final String PROTO_RESOURCE = "/dataform/configs.proto";

    private boolean protoAvailable;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        protoAvailable = installProto();
    }

    private boolean installProto() throws Exception {
        var stream = getClass().getResourceAsStream(PROTO_RESOURCE);
        if (stream == null) {
            return false;
        }
        Path coreDir = Files.createTempDirectory("dataform-core");
        try (stream) {
            Files.write(coreDir.resolve("configs.proto"), stream.readAllBytes());
        }
        VirtualFile coreVirtualDir =
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(coreDir);
        assertNotNull(coreVirtualDir);
        ServiceContainerUtil.replaceService(getProject(), DataformInterpreterManager.class,
                new StubInterpreterManager(coreVirtualDir), getTestRootDisposable());
        return true;
    }

    private List<String> completeIn(String configBlock) {
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx",
                configBlock + "\n\nSELECT 1\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();
        return lookups == null ? List.of() : lookups;
    }

    public void testTopLevelPropertiesAreProposed() {
        if (!protoAvailable) return;
        List<String> lookups = completeIn("config {\n  type: \"table\",\n  <caret>\n}");

        assertTrue("got " + lookups, lookups.contains("bigquery"));
        assertTrue("got " + lookups, lookups.contains("partitionBy"));
        assertTrue("got " + lookups, lookups.contains("tags"));
        assertFalse("already declared", lookups.contains("type"));
    }

    public void testBigqueryPropertiesAreProposed() {
        if (!protoAvailable) return;
        List<String> lookups = completeIn(
                "config {\n  type: \"table\",\n  bigquery: {\n    <caret>\n  }\n}");

        assertTrue("got " + lookups, lookups.contains("partitionBy"));
        assertTrue("got " + lookups, lookups.contains("clusterBy"));
        assertTrue("got " + lookups, lookups.contains("iceberg"));
        assertFalse("top level properties must not leak", lookups.contains("tags"));
    }

    public void testPropertiesAreScopedToTheDeclaredActionType() {
        if (!protoAvailable) return;
        List<String> operations = completeIn(
                "config {\n  type: \"operations\",\n  <caret>\n}");

        assertFalse("operations have no BigQuery block, got " + operations,
                operations.contains("bigquery"));
        assertFalse("operations have no partitioning, got " + operations,
                operations.contains("partitionBy"));
        assertTrue("got " + operations, operations.contains("tags"));
    }

    /**
     * Dataform declares {@code partition_by} as a string, so adding one opens a string rather than
     * asking which shape to write it in, under {@code bigquery} as at the top level.
     */
    public void testBigqueryPartitionByInsertsAString() {
        if (!protoAvailable) return;
        String inserted = completeAndInsert(
                "config {\n  type: \"table\",\n  bigquery: {\n    partitionB<caret>\n  }\n}",
                "partitionBy");

        assertTrue("got [" + inserted + "]", inserted.contains("    partitionBy: \"\""));
    }

    public void testTheObjectShapeOfAPartitionByIsNotOffered() {
        if (!protoAvailable) return;
        completeAndInsert(
                "config {\n  type: \"table\",\n  bigquery: {\n    partitionB<caret>\n  }\n}",
                "partitionBy");

        List<String> shapes = completeAgain();
        assertFalse("got " + shapes, shapes.contains("object"));
        assertFalse("got " + shapes, shapes.contains("string"));
    }

    public void testTopLevelPartitionByInsertsAString() {
        if (!protoAvailable) return;
        String inserted = completeAndInsert(
                "config {\n  type: \"table\",\n  partitionB<caret>\n}", "partitionBy");

        assertTrue("got [" + inserted + "]", inserted.contains("  partitionBy: \"\",\n"));
    }

    private List<String> completeAgain() {
        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();
        return lookups == null ? List.of() : lookups;
    }

    private String insertFromCurrentPosition(String lookupString) {
        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();
        assertNotNull("no popup, expected " + lookupString, lookups);
        int index = lookups.indexOf(lookupString);
        assertTrue(lookupString + " not proposed, got " + lookups, index >= 0);
        myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[index]);
        myFixture.finishLookup('\n');
        return hostEditor().getDocument().getText();
    }

    private Editor hostEditor() {
        return myFixture.getEditor() instanceof EditorWindow window
                ? window.getDelegate()
                : myFixture.getEditor();
    }

    private String completeAndInsert(String configBlock, String propertyName) {
        PsiFile file = myFixture.addFileToProject(
                "definitions/insert_" + propertyName + configBlock.length() + ".sqlx",
                configBlock + "\n\nSELECT 1\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();
        if (lookups != null) {
            int index = lookups.indexOf(propertyName);
            assertTrue(propertyName + " not proposed, got " + lookups, index >= 0);
            myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[index]);
            myFixture.finishLookup('\n');
        }

        Editor hostEditor = myFixture.getEditor() instanceof EditorWindow window
                ? window.getDelegate()
                : myFixture.getEditor();
        return hostEditor.getDocument().getText();
    }

    public void testEnumValuesAreProposedForType() {
        if (!protoAvailable) return;
        List<String> lookups = completeIn("config {\n  type: \"<caret>\"\n}");

        assertTrue("got " + lookups, lookups.contains("table"));
        assertTrue("got " + lookups, lookups.contains("incremental"));
        assertTrue("got " + lookups, lookups.contains("declaration"));
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
}
