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

import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.InlineBanner;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.UiInterceptors;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.components.labels.LinkLabel;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher.GcpPanelActionDispatcher;
import io.github.rejeb.dataform.language.gcp.workspace.CommitRoute;
import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange.ChangeState;

import org.jetbrains.annotations.NotNull;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.ActionEvent;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class CommitViewTest extends BasePlatformTestCase {

    private static final DataformRepositoryConfig SALES =
            new DataformRepositoryConfig("sales-id", "Sales", "my-gcp-project", "sales", "europe-west1");

    static GcpPanelActionDispatcher noOpDispatcher() {
        return (GcpPanelActionDispatcher) Proxy.newProxyInstance(GcpPanelActionDispatcher.class.getClassLoader(),
                new Class<?>[]{GcpPanelActionDispatcher.class}, (proxy, method, args) -> null);
    }

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

    public void testTheMessageIsAnEditorWithAPlaceholderBelowTheTree() {
        CommitView view = newView();

        EditorTextField message = assertOneElement(all(view, EditorTextField.class));
        EditorEx editor = message.getEditor(true);

        assertEquals("Commit message", String.valueOf(((EditorImpl) editor).getPlaceholder()));
        assertFalse(editor.isOneLineMode());
        assertEmpty(all(view, OnePixelSplitter.class));
    }

    public void testCommitOffersCommitAndPushNextToAPushButton() {
        CommitView view = newView();

        JBOptionButton commit = assertOneElement(all(view, JBOptionButton.class));
        assertEquals("Commit", commit.getAction().getValue(Action.NAME));
        assertEquals(List.of("Commit and Push…"),
                Arrays.stream(commit.getOptions()).map(option -> String.valueOf(option.getValue(Action.NAME))).toList());
        JButton push = pushButton(view);
        assertSame(DataformIcons.PUSH, push.getIcon());
    }

    public void testWithoutWorkspaceTheTabAsksForOne() {
        CommitView view = newView();

        CheckboxTree tree = assertOneElement(all(view, CheckboxTree.class));
        assertEquals("No workspace selected", tree.getEmptyText().getText());
        assertFalse(pushButton(view).isEnabled());
    }

    public void testWithoutChangesTheTabSaysSo() {
        selectWorkspace("dev");
        CommitView view = newView();

        publish(List.of());

        CheckboxTree tree = assertOneElement(all(view, CheckboxTree.class));
        assertFalse(tree.isRootVisible());
        assertEquals("No changes in dev", tree.getEmptyText().getText());
        assertFalse(assertOneElement(all(view, JBOptionButton.class)).isEnabled());
        assertTrue(pushButton(view).isEnabled());
    }

    public void testEveryChangeStartsChecked() {
        selectWorkspace("dev");
        CommitView view = newView();

        publish(List.of(new UncommittedChange("definitions/analytics/a.sqlx", ChangeState.MODIFIED),
                new UncommittedChange("definitions/b.sqlx", ChangeState.ADDED)));

        CheckboxTree tree = assertOneElement(all(view, CheckboxTree.class));
        assertEquals(2, tree.getCheckedNodes(UncommittedChange.class, null).length);
        assertEquals("Commit 2 files", assertOneElement(all(view, JBOptionButton.class)).getAction().getValue(Action.NAME));
    }

    public void testARightClickOffersThePrototypeFileMenu() {
        selectWorkspace("dev");
        CommitView view = newView();

        List<String> texts = view.fileMenu().stream()
                .map(a -> a instanceof Separator ? "---" : a.getTemplatePresentation().getText()).toList();

        assertEquals(List.of("Compare with Local", "Fetch to Local", "---", "Open in Google Cloud Console", "Copy Path"), texts);
    }

    public void testTheMenuActsOnTheSelectedChange() {
        selectWorkspace("dev");
        CommitView view = newView();
        publish(List.of(new UncommittedChange("definitions/b.sqlx", ChangeState.ADDED)));
        CheckboxTree tree = assertOneElement(all(view, CheckboxTree.class));

        tree.setSelectionRow(1);

        assertEquals(new WorkspaceFileActions.Target("definitions/b.sqlx", ChangeState.ADDED), view.selectedChange());
    }

    public void testDoubleClickingAChangeOpensTheLocalFile() {
        PsiFile file = myFixture.addFileToProject("definitions/b.sqlx", "SELECT 1");
        selectWorkspace("dev");
        CommitView view = newView();
        publish(List.of(new UncommittedChange("definitions/b.sqlx", ChangeState.MODIFIED)));
        CheckboxTree tree = assertOneElement(all(view, CheckboxTree.class));
        tree.setSize(400, 200);
        Rectangle row = tree.getRowBounds(1);

        doubleClick(tree, row.x + row.width - 2, row.y + row.height / 2);

        assertTrue(FileEditorManager.getInstance(getProject()).isFileOpen(file.getVirtualFile()));
    }

    public void testDoubleClickingTheCheckboxDoesNotOpenTheFile() {
        PsiFile file = myFixture.addFileToProject("definitions/b.sqlx", "SELECT 1");
        selectWorkspace("dev");
        CommitView view = newView();
        publish(List.of(new UncommittedChange("definitions/b.sqlx", ChangeState.MODIFIED)));
        CheckboxTree tree = assertOneElement(all(view, CheckboxTree.class));
        tree.setSize(400, 200);
        Rectangle row = tree.getRowBounds(1);

        doubleClick(tree, row.x + 2, row.y + row.height / 2);

        assertFalse(FileEditorManager.getInstance(getProject()).isFileOpen(file.getVirtualFile()));
    }

    private static void doubleClick(Component target, int x, int y) {
        long now = System.currentTimeMillis();
        for (int clicks = 1; clicks <= 2; clicks++) {
            target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, now, 0, x, y, clicks, false, MouseEvent.BUTTON1));
            target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, now, 0, x, y, clicks, false, MouseEvent.BUTTON1));
            target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, now, 0, x, y, clicks, false, MouseEvent.BUTTON1));
        }
    }

    public void testUncheckingAFileUpdatesTheCommitButton() {
        selectWorkspace("dev");
        CommitView view = newView();
        publish(List.of(new UncommittedChange("definitions/a.sqlx", ChangeState.MODIFIED),
                new UncommittedChange("definitions/b.sqlx", ChangeState.ADDED)));
        CheckboxTree tree = assertOneElement(all(view, CheckboxTree.class));
        CheckedTreeNode root = (CheckedTreeNode) tree.getModel().getRoot();

        tree.setNodeState((CheckedTreeNode) root.getChildAt(0), false);

        assertEquals("Commit 1 file", assertOneElement(all(view, JBOptionButton.class)).getAction().getValue(Action.NAME));
    }

    public void testTheRootCountsTheFilesInGrayAndRowsShowTheirFolder() {
        selectWorkspace("dev");
        CommitView view = newView();
        publish(List.of(new UncommittedChange("definitions/analytics/a.sqlx", ChangeState.MODIFIED),
                new UncommittedChange("definitions/b.sqlx", ChangeState.ADDED)));
        CheckboxTree tree = assertOneElement(all(view, CheckboxTree.class));
        CheckedTreeNode root = (CheckedTreeNode) tree.getModel().getRoot();

        assertEquals(List.of("Changes", "2 files"), fragments(tree, root));
        assertEquals(List.of("a.sqlx", "definitions/analytics"), fragments(tree, root.getChildAt(0)));
    }

    public void testCommittingWithoutMessageShowsTheErrorUnderTheField() {
        selectWorkspace("dev");
        CommitView view = newView();
        publish(List.of(new UncommittedChange("definitions/a.sqlx", ChangeState.MODIFIED)));
        JBOptionButton commit = assertOneElement(all(view, JBOptionButton.class));

        commit.getAction().actionPerformed(new ActionEvent(commit, ActionEvent.ACTION_PERFORMED, "commit"));

        EditorTextField message = assertOneElement(all(view, EditorTextField.class));
        ValidationInfo info = ComponentValidator.getInstance(message).orElseThrow().getValidationInfo();
        assertNotNull(info);
        assertEquals("Enter a commit message", info.message);
    }

    private CommitView newView() {
        CommitView view = new CommitView(getProject(), noOpDispatcher(), () -> { }, getTestRootDisposable());
        publishRoute(NO_REMOTE);
        return view;
    }

    private void selectWorkspace(String workspace) {
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
        settings.saveAllConfigs(List.of(SALES));
        settings.setActiveRepositoryId(SALES.repositoryConfigId());
        settings.setSelectedWorkspaceId(workspace);
    }

    private void publish(List<UncommittedChange> changes) {
        getProject().getMessageBus().syncPublisher(DataformGcpEvent.TOPIC).onGitStatusesLoaded(changes);
    }

    private static JButton pushButton(CommitView view) {
        return all(view, JButton.class).stream()
                .filter(button -> !(button instanceof JBOptionButton) && "Push".equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> fragments(CheckboxTree tree, Object node) {
        CheckboxTree.CheckboxTreeCellRenderer renderer = (CheckboxTree.CheckboxTreeCellRenderer) tree.getCellRenderer();
        renderer.getTreeCellRendererComponent(tree, node, false, true, false, 0, false);
        List<String> texts = new ArrayList<>();
        SimpleColoredComponent.ColoredIterator fragments = renderer.getTextRenderer().iterator();
        while (fragments.hasNext()) {
            String text = fragments.next().trim();
            SimpleTextAttributes attributes = fragments.getTextAttributes();
            if (!text.isEmpty()) {
                texts.add(text);
            }
            if (texts.size() == 2) {
                assertEquals(SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor(), attributes.getFgColor());
            }
        }
        return texts;
    }

    static <T extends Component> List<T> all(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                found.add(type.cast(child));
            }
            if (child instanceof Container container) {
                found.addAll(all(container, type));
            }
        }
        return found;
    }

    private static final DataformRepository LINKED =
            new DataformRepository("sales", "https://example.com/sales.git", "main", "");
    private static final DataformRepository HOSTED = new DataformRepository("sales", null, null, "");
    private static final CommitRoute NO_REMOTE = new CommitRoute(HOSTED, CommitRoute.LocalClone.NONE);
    private static final CommitRoute NO_CLONE = new CommitRoute(LINKED, CommitRoute.LocalClone.NONE);
    private static final CommitRoute CLONE = new CommitRoute(LINKED, CommitRoute.LocalClone.WITH_GIT_PLUGIN);
    private static final CommitRoute CLONE_WITHOUT_GIT_PLUGIN =
            new CommitRoute(LINKED, CommitRoute.LocalClone.WITHOUT_GIT_PLUGIN);

    private final GcpPanelActionDispatcher dispatcher = mock(GcpPanelActionDispatcher.class);
    private ListPopup popup;

    private final List<String> linksRun = new ArrayList<>();
    private boolean linksAvailable = true;
    private final GitLinks links = new GitLinks() {
        @Override
        public Runnable newBranch(@NotNull JComponent source) {
            return linksAvailable ? () -> linksRun.add("new branch") : null;
        }

        @Override
        public Runnable cloneRepository() {
            return linksAvailable ? () -> linksRun.add("clone") : null;
        }
    };

    private CommitView recordingView(String workspace) {
        selectWorkspace(workspace);
        return new CommitView(getProject(), dispatcher, () -> { }, getTestRootDisposable(), links);
    }

    private void publishRoute(CommitRoute route) {
        getProject().getMessageBus().syncPublisher(DataformGcpEvent.TOPIC).onCommitRouteLoaded(route);
    }

    private void interceptThePopup() {
        UiInterceptors.register(new UiInterceptors.UiInterceptor<>(ListPopup.class) {
            @Override
            protected void doIntercept(@NotNull ListPopup intercepted) {
                popup = intercepted;
                Disposer.register(getTestRootDisposable(), intercepted);
            }
        });
    }

    private List<String> popupChoices() {
        ListPopupStep<Object> step = popupStep();
        return step.getValues().stream().map(step::getTextFor).toList();
    }

    @SuppressWarnings("unchecked")
    private ListPopupStep<Object> popupStep() {
        assertNotNull("no popup was shown", popup);
        return (ListPopupStep<Object>) popup.getListStep();
    }

    private void choose(String text) {
        ListPopupStep<Object> step = popupStep();
        Object choice = step.getValues().stream().filter(value -> text.equals(step.getTextFor(value))).findFirst().orElseThrow();
        step.onChosen(choice, true);
        Runnable finalStep = step.getFinalRunnable();
        if (finalStep != null) {
            finalStep.run();
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    private void commitAndPush(CommitView view) {
        JBOptionButton commit = assertOneElement(all(view, JBOptionButton.class));
        Action commitAndPush = commit.getOptions()[0];
        commitAndPush.actionPerformed(new ActionEvent(commit, ActionEvent.ACTION_PERFORMED, "push"));
    }

    private void withAChange(CommitView view) {
        publish(List.of(new UncommittedChange("definitions/a.sqlx", ChangeState.MODIFIED)));
        assertOneElement(all(view, EditorTextField.class)).setText("first commit");
    }

    public void testPushWaitsForTheRepositoryToBeKnown() {
        CommitView view = recordingView("dev");
        assertFalse(pushButton(view).isEnabled());

        publishRoute(NO_CLONE);

        assertTrue(pushButton(view).isEnabled());
    }

    public void testPushStaysDisabledWithoutAWorkspace() {
        CommitView view = newView();

        publishRoute(NO_CLONE);

        assertFalse(pushButton(view).isEnabled());
    }

    public void testCommitAndPushWaitsForTheRepositoryToBeKnown() {
        CommitView view = recordingView("dev");
        withAChange(view);
        Action commitAndPush = assertOneElement(all(view, JBOptionButton.class)).getOptions()[0];
        assertFalse(commitAndPush.isEnabled());

        publishRoute(NO_CLONE);

        assertTrue(commitAndPush.isEnabled());
    }

    public void testPushingALinkedRepositoryAsksForTheBranchFirst() {
        CommitView view = recordingView("dev");
        publishRoute(NO_CLONE);
        interceptThePopup();

        pushButton(view).doClick();

        assertEquals(List.of("Push to main", "Push to dev"), popupChoices());
        verify(dispatcher, never()).pushGitCommits(any(), any());
    }

    public void testAWorkspaceNameKeepsItsUnderscoresInTheChoices() {
        CommitView view = recordingView("world_cup");
        publishRoute(NO_CLONE);
        interceptThePopup();

        pushButton(view).doClick();

        assertEquals(List.of("Push to main", "Push to world_cup"), popupChoices());
    }

    public void testChoosingTheDefaultBranchPushesToIt() {
        CommitView view = recordingView("dev");
        publishRoute(NO_CLONE);
        interceptThePopup();
        pushButton(view).doClick();

        choose("Push to main");

        verify(dispatcher).pushGitCommits("dev", "main");
    }

    public void testChoosingTheWorkspaceBranchPushesToIt() {
        CommitView view = recordingView("dev");
        publishRoute(NO_CLONE);
        interceptThePopup();
        pushButton(view).doClick();

        choose("Push to dev");

        verify(dispatcher).pushGitCommits("dev", "dev");
    }

    public void testAHostedRepositoryPushesWithoutAsking() {
        CommitView view = recordingView("dev");
        publishRoute(NO_REMOTE);

        pushButton(view).doClick();

        verify(dispatcher).pushGitCommits("dev", null);
    }

    public void testAnUnreadableRepositoryKeepsPushDisabledAndSaysWhy() {
        CommitView view = recordingView("dev");

        publishRoute(null);

        assertFalse(pushButton(view).isEnabled());
        assertEquals("The repository settings could not be read. Refresh to try again.",
                pushButton(view).getToolTipText());
    }

    public void testARepositoryReadOnALaterRefreshEnablesPushAgain() {
        CommitView view = recordingView("dev");
        publishRoute(null);

        publishRoute(NO_CLONE);

        assertTrue(pushButton(view).isEnabled());
        assertEquals("Push the workspace commits to the Git remote", pushButton(view).getToolTipText());
    }

    public void testCommitAndPushCommitsNothingBeforeTheBranchIsChosen() {
        CommitView view = recordingView("dev");
        publishRoute(NO_CLONE);
        withAChange(view);
        interceptThePopup();

        commitAndPush(view);

        assertEquals(List.of("Push to main", "Push to dev"), popupChoices());
        verify(dispatcher, never()).commitAndPush(any(), any(), any(), any());
        verify(dispatcher, never()).commitChanges(any(), any(), any());
    }

    public void testCommitAndPushGoesToTheChosenBranch() {
        CommitView view = recordingView("dev");
        publishRoute(NO_CLONE);
        withAChange(view);
        interceptThePopup();
        commitAndPush(view);

        choose("Push to dev");

        verify(dispatcher).commitAndPush("dev", List.of("definitions/a.sqlx"), "first commit", "dev");
    }

    public void testCommitAndPushOfAHostedRepositoryDoesNotAsk() {
        CommitView view = recordingView("dev");
        publishRoute(NO_REMOTE);
        withAChange(view);

        commitAndPush(view);

        verify(dispatcher).commitAndPush("dev", List.of("definitions/a.sqlx"), "first commit", null);
    }

    private String bannerText(CommitView view) {
        return assertOneElement(all(view, InlineBanner.class)).getToolTipText();
    }

    private List<LinkLabel> bannerLinks(CommitView view) {
        return all(view, LinkLabel.class).stream().filter(Component::isVisible).toList();
    }

    private void assertCommitAndPushDisabled(CommitView view) {
        JBOptionButton commit = assertOneElement(all(view, JBOptionButton.class));
        assertFalse(commit.isEnabled());
        assertFalse(commit.getOptions()[0].isEnabled());
        assertFalse(pushButton(view).isEnabled());
        assertFalse(assertOneElement(all(view, EditorTextField.class)).isEnabled());
    }

    private void assertCommitAndPushEnabled(CommitView view) {
        JBOptionButton commit = assertOneElement(all(view, JBOptionButton.class));
        assertTrue(commit.isEnabled());
        assertTrue(commit.getOptions()[0].isEnabled());
        assertTrue(pushButton(view).isEnabled());
        assertTrue(assertOneElement(all(view, EditorTextField.class)).isEnabled());
    }

    public void testNoGitRemoteKeepsCommitAndPushAndSaysNothing() {
        CommitView view = recordingView("dev");
        publishRoute(NO_REMOTE);
        withAChange(view);

        assertCommitAndPushEnabled(view);
        assertEmpty(all(view, InlineBanner.class));
    }

    public void testACloneDisablesCommitAndPush() {
        CommitView view = recordingView("dev");
        publishRoute(CLONE);
        withAChange(view);

        assertCommitAndPushDisabled(view);
    }

    public void testACloneExplainsToUseGitAndOffersANewBranch() {
        CommitView view = recordingView("dev");

        publishRoute(CLONE);

        assertTrue(bannerText(view), bannerText(view).contains("Commit and push with Git"));
        LinkLabel<?> link = assertOneElement(bannerLinks(view));
        assertEquals("New Branch…", link.getText());
        link.doClick();
        assertEquals(List.of("new branch"), linksRun);
    }

    public void testACloneWithoutANewBranchActionStillExplains() {
        linksAvailable = false;
        CommitView view = recordingView("dev");

        publishRoute(CLONE);

        assertTrue(bannerText(view), bannerText(view).contains("Commit and push with Git"));
        assertEmpty(bannerLinks(view));
    }

    public void testACloneWithoutTheGitPluginDisablesCommitAndPushSilently() {
        CommitView view = recordingView("dev");
        publishRoute(CLONE_WITHOUT_GIT_PLUGIN);
        withAChange(view);

        assertCommitAndPushDisabled(view);
        assertEmpty(all(view, InlineBanner.class));
    }

    public void testNoCloneKeepsCommitAndPushAndSuggestsCloning() {
        CommitView view = recordingView("dev");
        publishRoute(NO_CLONE);
        withAChange(view);

        assertCommitAndPushEnabled(view);
        assertTrue(bannerText(view), bannerText(view).contains("Clone the repository"));
        LinkLabel<?> link = assertOneElement(bannerLinks(view));
        assertEquals("Clone Repository…", link.getText());
        link.doClick();
        assertEquals(List.of("clone"), linksRun);
    }

    public void testNoCloneDialogLeavesTheSuggestionAsText() {
        linksAvailable = false;
        CommitView view = recordingView("dev");

        publishRoute(NO_CLONE);

        assertTrue(bannerText(view), bannerText(view).contains("Clone the repository"));
        assertEmpty(bannerLinks(view));
    }

    public void testNoWorkspaceStillSuggestsCloning() {
        CommitView view = recordingView(null);

        publishRoute(NO_CLONE);

        assertTrue(bannerText(view), bannerText(view).contains("Clone the repository"));
    }

    public void testAddingACloneDisablesCommitAndPushAndReplacesTheBanner() {
        CommitView view = recordingView("dev");
        publishRoute(NO_CLONE);
        withAChange(view);
        assertCommitAndPushEnabled(view);

        publishRoute(CLONE);

        assertCommitAndPushDisabled(view);
        assertTrue(bannerText(view), bannerText(view).contains("Commit and push with Git"));
    }

    public void testRemovingTheCloneBringsCommitAndPushBack() {
        CommitView view = recordingView("dev");
        publishRoute(CLONE);
        withAChange(view);

        publishRoute(NO_CLONE);

        assertCommitAndPushEnabled(view);
        assertTrue(bannerText(view), bannerText(view).contains("Clone the repository"));
    }

    public void testAClonesBannerGoesWhenTheRepositoryIsNoLongerLinked() {
        CommitView view = recordingView("dev");
        publishRoute(CLONE);

        publishRoute(NO_REMOTE);

        assertEmpty(all(view, InlineBanner.class));
    }

    public void testChangesLoadedOnACloneKeepTheMessageDisabled() {
        CommitView view = recordingView("dev");
        publishRoute(CLONE);

        publish(List.of(new UncommittedChange("definitions/a.sqlx", ChangeState.MODIFIED)));

        assertFalse(assertOneElement(all(view, EditorTextField.class)).isEnabled());
    }

    public void testNothingCommitsUntilTheRouteIsKnown() {
        CommitView view = recordingView("dev");

        withAChange(view);

        assertCommitAndPushDisabled(view);
    }

    public void testAnUnreadableRepositorySaysWhyCommitIsDisabled() {
        CommitView view = recordingView("dev");

        publishRoute(null);

        assertEquals("The repository settings could not be read. Refresh to try again.",
                assertOneElement(all(view, JBOptionButton.class)).getToolTipText());
    }

    public void testAKnownRouteClearsTheReasonCommitWasDisabled() {
        CommitView view = recordingView("dev");
        publishRoute(null);

        publishRoute(NO_REMOTE);

        assertNull(assertOneElement(all(view, JBOptionButton.class)).getToolTipText());
    }

    public void testAGitConfigChangeChecksTheRouteAgain() throws Exception {
        myFixture.getTempDirFixture().findOrCreateDir(".git");
        recordingView("dev");
        publishRoute(NO_CLONE);

        myFixture.getTempDirFixture().createFile(".git/config", "[core]\n");
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        verify(dispatcher, atLeastOnce()).refreshWhenCommitRouteChanges(NO_CLONE);
    }

    public void testAGitDirectoryAppearingChecksTheRouteAgain() throws Exception {
        recordingView("dev");
        publishRoute(NO_CLONE);

        myFixture.getTempDirFixture().findOrCreateDir(".git");
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        verify(dispatcher).refreshWhenCommitRouteChanges(NO_CLONE);
    }

    public void testAnUnreadableRepositoryChecksTheRouteAgainToo() throws Exception {
        recordingView("dev");
        publishRoute(null);

        myFixture.getTempDirFixture().findOrCreateDir(".git");
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        verify(dispatcher).refreshWhenCommitRouteChanges(null);
    }

    public void testACloneChecksTheRouteAgainWhenItsConfigChanges() throws Exception {
        recordingView("dev");
        publishRoute(CLONE);

        myFixture.getTempDirFixture().findOrCreateDir(".git");
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        verify(dispatcher).refreshWhenCommitRouteChanges(CLONE);
    }

    public void testNoGitRemoteIgnoresGitConfigChanges() throws Exception {
        recordingView("dev");
        publishRoute(NO_REMOTE);

        myFixture.getTempDirFixture().createFile(".git/config", "[core]\n");
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        verify(dispatcher, never()).refreshWhenCommitRouteChanges(any());
    }

    public void testOtherFilesDoNotCheckTheRouteAgain() throws Exception {
        recordingView("dev");
        publishRoute(NO_CLONE);

        myFixture.getTempDirFixture().createFile("definitions/config", "");
        myFixture.getTempDirFixture().createFile(".github/config", "");
        myFixture.getTempDirFixture().createFile(".gitignore", "");
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        verify(dispatcher, never()).refreshWhenCommitRouteChanges(any());
    }
}
