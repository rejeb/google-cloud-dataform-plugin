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
package io.github.rejeb.dataform.language.unittest;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.IconUtil;
import io.github.rejeb.dataform.language.DataformIcons;

import javax.swing.Icon;
import java.util.concurrent.TimeUnit;

public class TestFileIconProviderTest extends BasePlatformTestCase {

    private static final String TEST_FILE = """
            config {
              type: "test",
              dataset: "orders"
            }

            input "raw_orders" {
              SELECT 1 AS id
            }

            SELECT 1 AS id
            """;

    private VirtualFile file(String path, String text) {
        return myFixture.addFileToProject(path, text).getVirtualFile();
    }

    public void testUnitTestFilesShowTheTestFileIcon() {
        VirtualFile test = file("definitions/tests/test_orders.sqlx", TEST_FILE);
        assertSame(DataformIcons.TEST_FILE, new TestFileIconProvider().getIcon(test, 0, getProject()));
    }

    public void testTheProjectViewAndEditorTabsGetTheTestFileIcon() {
        VirtualFile test = file("definitions/tests/test_orders.sqlx", TEST_FILE);
        assertSame(DataformIcons.TEST_FILE, IconUtil.computeFileIcon(test, 0, getProject()));
    }

    public void testOtherSqlxFilesKeepTheSqlxIcon() {
        VirtualFile table = file("definitions/orders.sqlx", "config {\n  type: \"table\"\n}\n\nSELECT 1 AS id\n");
        assertNull(new TestFileIconProvider().getIcon(table, 0, getProject()));
        assertSame(DataformIcons.FILE, IconUtil.computeFileIcon(table, 0, getProject()));
    }

    public void testATestTypeOutsideTheConfigBlockIsNoTest() {
        VirtualFile table = file("definitions/orders.sqlx",
                "config {\n  type: \"table\"\n}\n\njs {\n  const options = { type: \"test\" };\n}\n\nSELECT 1 AS id\n");
        assertNull(new TestFileIconProvider().getIcon(table, 0, getProject()));
    }

    public void testFilesOtherThanSqlxAreLeftAlone() {
        VirtualFile js = file("definitions/tests.js", "const config = { type: \"test\" };\n");
        assertNull(new TestFileIconProvider().getIcon(js, 0, getProject()));
        assertNull(new TestFileIconProvider().getIcon(file("definitions/tests/test_orders.sqlx", TEST_FILE), 0, null));
    }

    public void testTheIconIsDecidedWithoutAReadAction() throws Exception {
        VirtualFile test = file("definitions/tests/test_orders.sqlx", TEST_FILE);
        Icon icon = ApplicationManager.getApplication()
                .executeOnPooledThread(() -> new TestFileIconProvider().getIcon(test, 0, getProject()))
                .get(30, TimeUnit.SECONDS);
        assertSame(DataformIcons.TEST_FILE, icon);
    }

    public void testAnUnsavedConfigBlockDecidesTheIcon() {
        VirtualFile table = file("definitions/orders.sqlx", "config {\n  type: \"table\"\n}\n\nSELECT 1 AS id\n");
        Document document = FileDocumentManager.getInstance().getDocument(table);
        WriteCommandAction.runWriteCommandAction(getProject(),
                () -> document.setText("config {\n  type: \"test\"\n}\n\nSELECT 1 AS id\n"));
        assertSame(DataformIcons.TEST_FILE, new TestFileIconProvider().getIcon(table, 0, getProject()));
    }

    public void testIsRegistered() {
        assertNotNull(FileIconProvider.EP_NAME.findExtension(TestFileIconProvider.class));
    }
}
