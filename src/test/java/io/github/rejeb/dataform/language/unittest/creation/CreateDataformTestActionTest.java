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
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.DataformAutoCompileService;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.unittest.UnitTestGraphFixture;
import io.github.rejeb.dataform.language.unittest.UnitTestSchemaFixture;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

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

    private Presentation presentationOn(String path) {
        VirtualFile file = myFixture.addFileToProject(path, "").getVirtualFile();
        AnActionEvent event = eventOn(file);
        action().update(event);
        return event.getPresentation();
    }

    private void assertEnabledOn(String path) {
        Presentation presentation = presentationOn(path);
        assertTrue(path, presentation.isVisible());
        assertTrue(path, presentation.isEnabled());
        assertEquals(path, "Create a unit test for a Dataform table or view", presentation.getDescription());
    }

    private void assertDisabledOn(String path, String reason) {
        Presentation presentation = presentationOn(path);
        assertTrue(path, presentation.isVisible());
        assertFalse(path, presentation.isEnabled());
        assertEquals(path, reason, presentation.getDescription());
    }

    private void assertHiddenOn(String path) {
        assertFalse(path, presentationOn(path).isVisible());
    }

    private static void drain() {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    public void testEnabledOnTablesViewsAndJsFilesHoldingThem() {
        assertEnabledOn("definitions/orders.sqlx");
        assertEnabledOn("definitions/customers.sqlx");
        assertEnabledOn("definitions/multi.js");
    }

    public void testDisabledOnActionsThatCannotBeTested() {
        assertDisabledOn("definitions/events.sqlx", "Dataform tests are only supported for tables and views");
        assertDisabledOn("definitions/cleanup.sqlx", "Dataform tests are only supported for tables and views");
        assertDisabledOn("definitions/sources.js", "Dataform tests are only supported for tables and views");
    }

    public void testDisabledOnSqlxFilesDeclaringNoTableOrView() {
        assertDisabledOn("definitions/tests/test_orders.sqlx", "Dataform tests are only supported for tables and views");
        assertDisabledOn("definitions/not_compiled_yet.sqlx", "Dataform tests are only supported for tables and views");
    }

    public void testHiddenOnJsFilesWithoutActionsAndOtherFiles() {
        assertHiddenOn("includes/helpers.js");
        assertHiddenOn("definitions/helpers.js");
        assertHiddenOn("definitions/notes.txt");
    }

    public void testHiddenOnFilesWithoutExtension() {
        assertHiddenOn("definitions/Makefile");
    }

    public void testFilesWithoutExtensionHoldNoTestableAction() {
        VirtualFile file = myFixture.addFileToProject("definitions/Dockerfile", "").getVirtualFile();
        assertEmpty(CreateDataformTestAction.testableActions(getProject(), file));
    }

    public void testWithoutGraphSqlxFilesAreDisabledAndJsFilesHidden() {
        UnitTestGraphFixture.clear(getProject());
        assertDisabledOn("definitions/orders.sqlx", "Compile the Dataform project to create a test");
        assertHiddenOn("definitions/multi.js");
    }

    public void testTheDescriptionComesBackOnceTheFileBecomesTestable() {
        VirtualFile orders = myFixture.addFileToProject("definitions/orders.sqlx", "").getVirtualFile();
        AnActionEvent event = eventOn(orders);
        UnitTestGraphFixture.clear(getProject());
        action().update(event);
        UnitTestGraphFixture.install(getProject(), getTestRootDisposable());
        action().update(event);
        assertTrue(event.getPresentation().isEnabled());
        assertEquals("Create a unit test for a Dataform table or view", event.getPresentation().getDescription());
    }

    public void testShowsTheTestFileIcon() {
        assertSame(DataformIcons.TEST_FILE, action().getTemplatePresentation().getIcon());
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

    private AtomicInteger countingCompileRequests() {
        AtomicInteger requests = new AtomicInteger();
        ServiceContainerUtil.replaceService(getProject(), DataformAutoCompileService.class,
                new DataformAutoCompileService() {
                    @Override
                    public void scheduleCompile() {
                        requests.incrementAndGet();
                    }

                    @Override
                    public void scheduleCompileAfterEdit() {
                    }
                }, getTestRootDisposable());
        return requests;
    }

    public void testCreatingATestRecompilesTheProject() {
        AtomicInteger requests = countingCompileRequests();
        VirtualFile orders = myFixture.addFileToProject("definitions/orders.sqlx", "").getVirtualFile();
        action().actionPerformed(eventOn(orders));
        drain();
        assertEquals(1, requests.get());
    }

    public void testNothingIsRecompiledWhenTheTestAlreadyExists() {
        myFixture.addFileToProject("definitions/tests/test_orders.sqlx", "keep me");
        AtomicInteger requests = countingCompileRequests();
        VirtualFile orders = myFixture.addFileToProject("definitions/orders.sqlx", "").getVirtualFile();
        action().actionPerformed(eventOn(orders));
        drain();
        assertEquals(0, requests.get());
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
