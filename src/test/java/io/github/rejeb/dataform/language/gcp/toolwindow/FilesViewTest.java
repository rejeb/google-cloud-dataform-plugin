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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.InlineBanner;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.StatusText;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.gitremote.FakeGitRemoteBranch;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.workspace.MainBranchSource;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange.ChangeState;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.rejeb.dataform.language.gcp.toolwindow.CommitViewTest.all;

public class FilesViewTest extends BasePlatformTestCase {

    private static final DataformRepositoryConfig SALES =
            new DataformRepositoryConfig("sales-id", "Sales", "my-gcp-project", "sales", "europe-west1");
    private static final MainBranchSource GIT_REMOTE =
            new MainBranchSource.GitRemote(new FakeGitRemoteBranch("main", Map.of()));

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

    public void testTheTreeStartsWithTheTopFoldersWithoutARootNode() {
        FilesView view = new FilesView(getProject(), SALES, () -> { }, getTestRootDisposable());

        assertFalse(assertOneElement(all(view, Tree.class)).isRootVisible());
    }

    public void testTheDataformMainBranchIsExplainedByABanner() {
        FilesView view = view();

        publish(MainBranchSource.DATAFORM);

        assertTrue(bannerText(view).contains("Read-only view of the repository main branch."));
    }

    public void testTheGitRemoteBranchIsExplainedAsNotADataformBranch() {
        FilesView view = view();

        publish(GIT_REMOTE);

        assertTrue(bannerText(view), bannerText(view).contains(
                "This is main on the Git remote (origin/main), not a Dataform branch. "
                        + "It is read-only: pick a workspace to pull or push files."));
    }

    public void testTheGitRemoteBranchShowsItsFiles() {
        FilesView view = view();

        publish(GIT_REMOTE);
        getProject().getMessageBus().syncPublisher(DataformGcpEvent.TOPIC).onFilesLoaded(List.of("definitions/a.sqlx"));

        assertFalse(assertOneElement(all(view, Tree.class)).isEmpty());
    }

    public void testNoBannerBeforeTheSourceIsKnown() {
        assertEmpty(all(view(), InlineBanner.class));
    }

    public void testRefreshingKeepsOneBanner() {
        FilesView view = view();

        publish(GIT_REMOTE);
        publish(GIT_REMOTE);

        assertOneElement(all(view, InlineBanner.class));
    }

    public void testNoSourceAsksForAWorkspace() {
        FilesView view = view();

        publish(MainBranchSource.UNAVAILABLE);
        getProject().getMessageBus().syncPublisher(DataformGcpEvent.TOPIC).onFilesLoaded(List.of());

        Tree tree = assertOneElement(all(view, Tree.class));
        assertEmpty(all(view, InlineBanner.class));
        assertTrue(tree.isEmpty());
        assertEquals("No workspace selected\nReading the Git remote needs a local clone.\nSelect a workspace…",
                tree.getEmptyText().toString());
    }

    public void testTheSelectAWorkspaceLinkOpensTheChoices() {
        AtomicInteger opened = new AtomicInteger();
        FilesView view = new FilesView(getProject(), SALES, opened::incrementAndGet, getTestRootDisposable());

        publish(MainBranchSource.UNAVAILABLE);
        clickAcrossTheMiddleOf(assertOneElement(all(view, Tree.class)));

        assertTrue(opened.get() > 0);
    }

    public void testASourceBecomingUnavailableLosesTheBanner() {
        FilesView view = view();

        publish(GIT_REMOTE);
        publish(MainBranchSource.UNAVAILABLE);

        assertEmpty(all(view, InlineBanner.class));
    }

    public void testASourceBecomingAvailableForgetsTheWorkspaceRequest() {
        FilesView view = view();

        publish(MainBranchSource.UNAVAILABLE);
        publish(GIT_REMOTE);

        assertOneElement(all(view, InlineBanner.class));
        assertEquals(StatusText.getDefaultEmptyText(), assertOneElement(all(view, Tree.class)).getEmptyText().getText());
    }

    public void testTheBannerFollowsTheSource() {
        FilesView view = view();

        publish(GIT_REMOTE);
        publish(MainBranchSource.DATAFORM);

        assertTrue(bannerText(view).contains("Read-only view of the repository main branch."));
    }

    public void testAWorkspaceSaysNothingAboutTheMainBranch() {
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
        settings.saveAllConfigs(List.of(SALES));
        settings.setActiveRepositoryId(SALES.repositoryConfigId());
        settings.setSelectedWorkspaceId("dev");
        FilesView view = view();

        for (MainBranchSource source : List.of(MainBranchSource.DATAFORM, GIT_REMOTE, MainBranchSource.UNAVAILABLE)) {
            publish(source);

            assertEmpty(all(view, InlineBanner.class));
            assertEquals(StatusText.getDefaultEmptyText(), assertOneElement(all(view, Tree.class)).getEmptyText().getText());
        }
    }

    public void testAFileMenuIsThePrototypeMenu() {
        FilesView view = new FilesView(getProject(), SALES, () -> { }, getTestRootDisposable());

        List<String> texts = view.fileMenu().stream().map(FilesViewTest::text).toList();

        assertEquals(List.of("Compare with Local", "Fetch to Local", "---", "Open in Google Cloud Console", "Copy Path"), texts);
    }

    public void testAFolderMenuFetchesAndCopiesThePathWithIcons() {
        FilesView view = new FilesView(getProject(), SALES, () -> { }, getTestRootDisposable());

        List<AnAction> menu = view.directoryMenu("definitions");

        assertEquals(List.of("Fetch to Local", "---", "Copy Path"), menu.stream().map(FilesViewTest::text).toList());
        assertSame(DataformIcons.FETCH, menu.get(0).getTemplatePresentation().getIcon());
        assertSame(DataformIcons.COPY, menu.get(2).getTemplatePresentation().getIcon());
    }

    public void testDoubleClickComparesModifiedFilesAndExplainsDeletedOnes() {
        assertEquals(FilesView.OpenMode.COMPARE, FilesView.openModeOf(ChangeState.MODIFIED));
        assertEquals(FilesView.OpenMode.EXPLAIN_DELETED, FilesView.openModeOf(ChangeState.DELETED));
        assertEquals(FilesView.OpenMode.READ_ONLY, FilesView.openModeOf(ChangeState.ADDED));
        assertEquals(FilesView.OpenMode.READ_ONLY, FilesView.openModeOf(null));
    }

    private FilesView view() {
        return new FilesView(getProject(), SALES, () -> { }, getTestRootDisposable());
    }

    private void publish(MainBranchSource source) {
        getProject().getMessageBus().syncPublisher(DataformGcpEvent.TOPIC).onMainBranchLoaded(source);
    }

    private static String bannerText(FilesView view) {
        return assertOneElement(all(view, InlineBanner.class)).getToolTipText();
    }

    private static void clickAcrossTheMiddleOf(Tree tree) {
        tree.setSize(600, 400);
        for (int y = 0; y < tree.getHeight(); y += 4) {
            press(tree, MouseEvent.MOUSE_PRESSED, y);
            press(tree, MouseEvent.MOUSE_RELEASED, y);
        }
    }

    private static void press(Tree tree, int id, int y) {
        tree.dispatchEvent(new MouseEvent(tree, id, 0, InputEvent.BUTTON1_DOWN_MASK, tree.getWidth() / 2, y, 1, false,
                MouseEvent.BUTTON1));
    }

    private static String text(AnAction action) {
        return action instanceof Separator ? "---" : action.getTemplatePresentation().getText();
    }
}
