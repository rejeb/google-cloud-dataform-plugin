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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.treeStructure.Tree;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.common.DataformConsoleUrls;
import io.github.rejeb.dataform.language.gcp.gitremote.FakeGitRemoteBranch;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.OpenInConsoleAction;
import io.github.rejeb.dataform.language.gcp.workspace.MainBranchSource;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange.ChangeState;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorkspaceFileActionsTest extends BasePlatformTestCase {

    private static final DataformRepositoryConfig SALES =
            new DataformRepositoryConfig("sales-id", "Sales", "my-gcp-project", "sales", "europe-west1");

    private WorkspaceFileActions.Target target;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        WorkspaceSelectionTest.resetSettings(GcpRepositorySettings.getInstance(getProject()));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            WorkspaceSelectionTest.resetSettings(GcpRepositorySettings.getInstance(getProject()));
        } finally {
            super.tearDown();
        }
    }

    public void testTheFileMenuIsThePrototypeMenu() {
        List<AnAction> menu = actions(null).fileMenu();

        assertEquals(List.of("Compare with Local", "Fetch to Local", "---", "Open in Google Cloud Console", "Copy Path"),
                menu.stream().map(WorkspaceFileActionsTest::text).toList());
        assertSame(DataformIcons.DIFF, menu.get(0).getTemplatePresentation().getIcon());
        assertSame(DataformIcons.FETCH, menu.get(1).getTemplatePresentation().getIcon());
        assertInstanceOf(menu.get(3), OpenInConsoleAction.class);
        assertSame(DataformIcons.COPY, menu.get(4).getTemplatePresentation().getIcon());
    }

    public void testCompareShowsTheDiffShortcut() {
        AnAction compare = actions(null).fileMenu().get(0);

        String diffShortcut = KeymapUtil.getFirstKeyboardShortcutText(
                ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_DIFF_COMMON));
        assertFalse(diffShortcut.isEmpty());
        assertEquals(diffShortcut, KeymapUtil.getFirstKeyboardShortcutText(compare));
    }

    public void testTheShortcutWorksInTheView() {
        WorkspaceFileActions actions = actions(null);
        Tree tree = new Tree();

        actions.installOn(tree, getTestRootDisposable());

        assertContainsElements(ActionUtil.getActions(tree), actions.fileMenu().get(0));
    }

    public void testCompareNeedsTheLocalFile() {
        WorkspaceFileActions actions = actions(new WorkspaceFileActions.Target("definitions/a.sqlx", ChangeState.MODIFIED));
        assertFalse(enabled(actions.fileMenu().get(0)));

        myFixture.addFileToProject("definitions/a.sqlx", "SELECT 1");

        assertTrue(enabled(actions.fileMenu().get(0)));
        assertTrue(enabled(actions.fileMenu().get(1)));
    }

    public void testADeletedFileCanNeitherBeComparedNorFetched() {
        myFixture.addFileToProject("definitions/a.sqlx", "SELECT 1");
        WorkspaceFileActions actions = actions(new WorkspaceFileActions.Target("definitions/a.sqlx", ChangeState.DELETED));

        assertFalse(enabled(actions.fileMenu().get(0)));
        assertFalse(enabled(actions.fileMenu().get(1)));
    }

    public void testWithoutSelectionNothingIsEnabled() {
        WorkspaceFileActions actions = actions(null);

        assertFalse(enabled(actions.fileMenu().get(0)));
        assertFalse(enabled(actions.fileMenu().get(1)));
        assertFalse(enabled(actions.fileMenu().get(4)));
    }

    public void testTheConsoleItemOpensTheSelectedFileInItsWorkspace() {
        selectWorkspace("dev");
        OpenInConsoleAction console = (OpenInConsoleAction) actions(
                new WorkspaceFileActions.Target("definitions/a.sqlx", ChangeState.MODIFIED)).fileMenu().get(3);

        assertEquals(DataformConsoleUrls.ofFile(SALES, "dev", "definitions/a.sqlx"), console.url());
    }

    public void testTheConsoleItemOfADeletedFileOpensItsWorkspace() {
        selectWorkspace("dev");
        OpenInConsoleAction console = (OpenInConsoleAction) actions(
                new WorkspaceFileActions.Target("definitions/a.sqlx", ChangeState.DELETED)).fileMenu().get(3);

        assertEquals(DataformConsoleUrls.of(SALES, "dev"), console.url());
    }

    public void testCopyPathCopiesTheWorkspacePath() throws Exception {
        AnAction copy = actions(new WorkspaceFileActions.Target("definitions/a.sqlx", null)).fileMenu().get(4);

        copy.actionPerformed(TestActionEvent.createTestEvent(copy));

        assertEquals("definitions/a.sqlx", CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));
    }

    public void testOpenLocalOpensTheProjectFile() {
        PsiFile file = myFixture.addFileToProject("definitions/a.sqlx", "SELECT 1");

        assertTrue(actions(null).openLocal("definitions/a.sqlx"));

        assertTrue(FileEditorManager.getInstance(getProject()).isFileOpen(file.getVirtualFile()));
    }

    public void testOpenLocalWithoutTheFileSaysSo() {
        assertFalse(actions(null).openLocal("definitions/missing.sqlx"));
    }

    private void selectWorkspace(String workspace) {
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
        settings.saveAllConfigs(List.of(SALES));
        settings.setActiveRepositoryId(SALES.repositoryConfigId());
        settings.setSelectedWorkspaceId(workspace);
    }

    private WorkspaceFileActions actions(@Nullable WorkspaceFileActions.Target selected) {
        target = selected;
        return new WorkspaceFileActions(getProject(), () -> target);
    }

    private static boolean enabled(AnAction action) {
        AnActionEvent event = TestActionEvent.createTestEvent(action);
        action.update(event);
        return event.getPresentation().isEnabled();
    }

    private static String text(AnAction action) {
        return action instanceof Separator ? "---" : action.getTemplatePresentation().getText();
    }

    private DataformGcpService serviceReading(MainBranchSource source) {
        DataformGcpService service = mock(DataformGcpService.class);
        when(service.mainBranchSource()).thenReturn(source);
        ServiceContainerUtil.replaceService(getProject(), DataformGcpService.class, service, getTestRootDisposable());
        return service;
    }

    public void testComparingWithTheGitRemoteBranchNamesIt() {
        serviceReading(new MainBranchSource.GitRemote(new FakeGitRemoteBranch("main", Map.of())));

        assertEquals("origin/main (Git remote)", actions(null).remoteLabel());
    }

    public void testComparingWithTheDataformMainBranchNamesGcp() {
        serviceReading(MainBranchSource.DATAFORM);

        assertEquals("Remote (GCP)", actions(null).remoteLabel());
    }

    public void testComparingWithAWorkspaceNamesGcpWithoutLookingUpTheMainBranch() {
        DataformGcpService service = serviceReading(new MainBranchSource.GitRemote(new FakeGitRemoteBranch("main", Map.of())));
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
        settings.saveAllConfigs(List.of(SALES));
        settings.setActiveRepositoryId(SALES.repositoryConfigId());
        settings.setSelectedWorkspaceId("dev");

        assertEquals("Remote (GCP)", actions(null).remoteLabel());
        verify(service, never()).mainBranchSource();
    }
}
