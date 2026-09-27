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
package io.github.rejeb.dataform.language.unittest.creation;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.unittest.UnitTestGraphFixture;
import io.github.rejeb.dataform.language.unittest.UnitTestSchemaFixture;

import java.io.IOException;

public class CreateDataformTestActionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        UnitTestGraphFixture.install(getProject(), getTestRootDisposable());
        UnitTestSchemaFixture.install(getProject(), getTestRootDisposable());
    }

    private AnAction action() {
        AnAction action = ActionManager.getInstance().getAction("Dataform.CreateTest");
        assertNotNull("action not registered", action);
        return action;
    }

    private AnActionEvent eventOn(VirtualFile file) {
        return TestActionEvent.createTestEvent(action(), SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, getProject())
                .add(CommonDataKeys.VIRTUAL_FILE, file)
                .build());
    }

    private boolean visibleOn(String path) {
        VirtualFile file = myFixture.addFileToProject(path, "").getVirtualFile();
        AnActionEvent event = eventOn(file);
        action().update(event);
        return event.getPresentation().isEnabledAndVisible();
    }

    private static void drain() {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    public void testShownOnTablesViewsAndJsFilesHoldingThem() {
        assertTrue(visibleOn("definitions/orders.sqlx"));
        assertTrue(visibleOn("definitions/customers.sqlx"));
        assertTrue(visibleOn("definitions/multi.js"));
    }

    public void testHiddenOnActionsThatCannotBeTested() {
        assertFalse(visibleOn("definitions/events.sqlx"));
        assertFalse(visibleOn("definitions/cleanup.sqlx"));
        assertFalse(visibleOn("definitions/sources.js"));
        assertFalse(visibleOn("includes/helpers.js"));
    }

    public void testHiddenWithoutGraph() {
        UnitTestGraphFixture.clear(getProject());
        assertFalse(visibleOn("definitions/orders.sqlx"));
    }

    public void testCreatesTheTestUnderDefinitionsTests() throws IOException {
        VirtualFile orders = myFixture.addFileToProject("definitions/orders.sqlx", "").getVirtualFile();
        action().actionPerformed(eventOn(orders));
        drain();
        VirtualFile created = orders.getParent().findFileByRelativePath("tests/test_orders.sqlx");
        assertNotNull(created);
        String text = VfsUtilCore.loadText(created);
        assertTrue(text, text.startsWith("config {\n  type: \"test\",\n  dataset: {\n    schema: \"d\",\n    name: \"orders\"\n  }\n}"));
        assertTrue(text, text.contains("input \"raw_orders\" {\n  SELECT\n    '' AS id,\n    0.0 AS amount\n}"));
        assertTrue(text, text.contains("-- Expected output\nSELECT\n  '' AS order_id,"));
    }

    public void testAnExistingTestIsNotOverwritten() throws IOException {
        myFixture.addFileToProject("definitions/tests/test_orders.sqlx", "keep me");
        VirtualFile orders = myFixture.addFileToProject("definitions/orders.sqlx", "").getVirtualFile();
        action().actionPerformed(eventOn(orders));
        drain();
        assertEquals("keep me", VfsUtilCore.loadText(
                orders.getParent().findFileByRelativePath("tests/test_orders.sqlx")));
    }
}
