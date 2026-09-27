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
package io.github.rejeb.dataform.language.unittest.generation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.diagnostics.compile.ConfigSchemaFixture;
import io.github.rejeb.dataform.language.unittest.UnitTestGraphFixture;
import io.github.rejeb.dataform.language.unittest.UnitTestSchemaFixture;

import java.util.List;

public class GenerateTestSelectIntentionTest extends BasePlatformTestCase {

    private static final String CONFIG = "config {\n  type: \"test\",\n  dataset: \"orders\"\n}\n\n";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ConfigSchemaFixture.install(getProject(), getTestRootDisposable());
        UnitTestGraphFixture.install(getProject(), getTestRootDisposable());
        UnitTestSchemaFixture.install(getProject(), getTestRootDisposable());
    }

    private List<IntentionAction> intentionsIn(String path, String text) {
        PsiFile file = myFixture.addFileToProject(path, text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.filterAvailableIntentions("Generate SELECT from schema of");
    }

    private String hostText() {
        Editor editor = myFixture.getEditor() instanceof EditorWindow window ? window.getDelegate() : myFixture.getEditor();
        return editor.getDocument().getText();
    }

    public void testOnAnInputLabelTheInputBodyIsGenerated() {
        List<IntentionAction> actions = intentionsIn("definitions/tests/test_orders.sqlx",
                CONFIG + "input \"raw_<caret>orders\" {\n  SELECT 1 AS id\n}\n\nSELECT 1\n");
        assertEquals(1, actions.size());
        assertEquals("Generate SELECT from schema of raw_orders", actions.get(0).getText());
        myFixture.launchAction(actions.get(0));
        assertTrue(hostText(), hostText().contains(
                "input \"raw_orders\" {\n  SELECT\n    '' AS id,\n    0.0 AS amount\n}"));
    }

    public void testInsideTheExpectedQueryTheExpectedOutputIsGenerated() {
        List<IntentionAction> actions = intentionsIn("definitions/tests/test_orders.sqlx",
                CONFIG + "-- Expected output\nSELECT 1 AS <caret>n\n");
        assertEquals(1, actions.size());
        myFixture.launchAction(actions.get(0));
        assertTrue(hostText(), hostText().contains("-- Expected output\nSELECT\n  '' AS order_id,\n  NUMERIC '0' AS total,\n"
                + "  STRUCT('' AS city, STRUCT(0.0 AS lat) AS geo) AS address,\n"
                + "  [STRUCT('' AS sku, 0 AS qty)] AS items\n"));
    }

    public void testNotAvailableWithoutSchema() {
        assertEquals(List.of(), intentionsIn("definitions/tests/test_orders.sqlx",
                CONFIG + "input \"stats\" {\n  SELECT 1<caret> AS id\n}\n\nSELECT 1\n"));
    }

    public void testNotAvailableOutsideATest() {
        assertEquals(List.of(), intentionsIn("definitions/orders.sqlx",
                "config { type: \"table\" }\n\nSELECT 1<caret> AS id\n"));
    }
}
