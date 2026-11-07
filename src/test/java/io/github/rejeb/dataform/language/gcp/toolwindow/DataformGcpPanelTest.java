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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBPanelWithEmptyText;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.auth.AuthTrigger;
import io.github.rejeb.dataform.language.gcp.auth.DataformAuthState;
import io.github.rejeb.dataform.language.gcp.common.DataformConsoleUrls;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.CreateWorkspaceAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.GcpAuthAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.HideDataformToolsAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.ManageRepositoriesAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.OpenInConsoleAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.RefreshAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.RepositoryPickerAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.WorkspacePickerAction;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;

import javax.swing.JPopupMenu;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DataformGcpPanelTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        WorkspaceSelectionTest.resetSettings(GcpRepositorySettings.getInstance(getProject()));
        DataformAuthState.getInstance().markAuthenticated();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            DataformAuthState.getInstance().markAuthenticated();
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        } finally {
            super.tearDown();
        }
    }

    public void testWithoutRepositoryBothTabsOfferToAddOne() {
        DataformGcpPanel panel = new DataformGcpPanel(getProject());
        Disposer.register(getTestRootDisposable(), panel);

        for (var tab : java.util.List.of(panel.filesPanel(), panel.changesPanel())) {
            JBPanelWithEmptyText empty = assertInstanceOf(tab.getContent(), JBPanelWithEmptyText.class);
            assertEquals("No Dataform repository", empty.getEmptyText().getText());
            assertNotNull(tab.getToolbar());
        }
    }

    public void testARepositoryLinkedAfterThePanelWasBuiltRebuildsIt() {
        DataformGcpPanel panel = new DataformGcpPanel(getProject());
        Disposer.register(getTestRootDisposable(), panel);
        var before = panel.filesPanel().getContent();

        getProject().getMessageBus().syncPublisher(DataformGcpEvent.TOPIC).onRepositoryChanged();

        assertNotSame(before, panel.filesPanel().getContent());
    }

    public void testSignedOutBothTabsOfferToSignIn() {
        DataformGcpPanel panel = newPanel();

        DataformAuthState.getInstance().markAuthRequired(AuthTrigger.USER_ACTION);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        for (var tab : List.of(panel.filesPanel(), panel.changesPanel())) {
            JBPanelWithEmptyText empty = assertInstanceOf(tab.getContent(), JBPanelWithEmptyText.class);
            assertEquals("Not signed in to Google Cloud", empty.getEmptyText().getText());
        }
    }

    public void testSigningInShowsTheRepositoryAgain() {
        DataformGcpPanel panel = newPanel();
        DataformAuthState.getInstance().markAuthRequired(AuthTrigger.USER_ACTION);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        DataformAuthState.getInstance().markAuthenticated();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        JBPanelWithEmptyText empty = assertInstanceOf(panel.filesPanel().getContent(), JBPanelWithEmptyText.class);
        assertEquals("No Dataform repository", empty.getEmptyText().getText());
    }

    public void testRefreshIsDisabledWhileSignedOut() {
        RefreshAction refresh = (RefreshAction) newPanel().titleActions().getFirst();
        DataformAuthState.getInstance().markAuthRequired(AuthTrigger.USER_ACTION);
        AnActionEvent event = TestActionEvent.createTestEvent(refresh);

        refresh.update(event);

        assertFalse(event.getPresentation().isEnabled());
    }

    public void testBothTabsShowThePickersThenPullPushAndCreateWorkspace() {
        DataformGcpPanel panel = newPanel();

        for (var tab : List.of(panel.filesPanel(), panel.changesPanel())) {
            List<String> kinds = Arrays.stream(toolbarActions(tab.getToolbar())).map(DataformGcpPanelTest::kind).toList();
            assertEquals(List.of("RepositoryPickerAction", "WorkspacePickerAction", "---", "Pull", "Push", "Create Workspace…",
                    "---", "Open in Google Cloud Console"), kinds);
        }
    }

    public void testBothToolbarsWrapInsteadOfHidingActions() {
        DataformGcpPanel panel = newPanel();

        for (var tab : List.of(panel.filesPanel(), panel.changesPanel())) {
            ActionToolbar toolbar = assertInstanceOf(tab.getToolbar(), ActionToolbar.class);
            assertSame(ToolbarLayoutStrategy.WRAP_STRATEGY, toolbar.getLayoutStrategy());
        }
    }

    public void testTheConsoleLinkFollowsTheSelectedWorkspace() {
        DataformRepositoryConfig sales = new DataformRepositoryConfig("sales-id", "Sales", "my-gcp-project", "sales", "europe-west1");
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
        settings.saveAllConfigs(List.of(sales));
        settings.setActiveRepositoryId(sales.repositoryConfigId());
        AnAction[] actions = toolbarActions(newPanel().filesPanel().getToolbar());
        OpenInConsoleAction console = assertInstanceOf(actions[7], OpenInConsoleAction.class);

        assertEquals(DataformConsoleUrls.of(sales, null), console.url());
        settings.setSelectedWorkspaceId("dev");
        assertEquals(DataformConsoleUrls.of(sales, "dev"), console.url());
    }

    public void testPullAndPushUseThePrototypeIconsAndTooltips() {
        AnAction[] actions = toolbarActions(newPanel().filesPanel().getToolbar());

        assertSame(DataformIcons.PULL, actions[3].getTemplatePresentation().getIcon());
        assertEquals("Copy the workspace files into the project", actions[3].getTemplatePresentation().getDescription());
        assertSame(DataformIcons.PUSH, actions[4].getTemplatePresentation().getIcon());
        assertEquals("Copy the project files into the workspace", actions[4].getTemplatePresentation().getDescription());
        assertSame(DataformIcons.ADD, actions[5].getTemplatePresentation().getIcon());
    }

    public void testTheTitleBarOnlyRefreshes() {
        List<AnAction> title = newPanel().titleActions();

        RefreshAction refresh = assertInstanceOf(assertOneElement(title), RefreshAction.class);
        assertSame(DataformIcons.REFRESH, refresh.getTemplatePresentation().getIcon());
    }

    public void testTheOptionsMenuManagesRepositoriesCreatesWorkspacesAndSignsIn() {
        AnAction[] gear = assertInstanceOf(newPanel().gearActions(), DefaultActionGroup.class).getChildActionsOrStubs();

        assertEquals(6, gear.length);
        assertInstanceOf(gear[0], ManageRepositoriesAction.class);
        assertInstanceOf(gear[1], CreateWorkspaceAction.class);
        assertInstanceOf(gear[2], Separator.class);
        assertInstanceOf(gear[3], GcpAuthAction.class);
        assertInstanceOf(gear[4], Separator.class);
        assertInstanceOf(gear[5], HideDataformToolsAction.class);
    }

    public void testTheOptionsMenuDropsThePlatformItemsWhenItOpens() {
        AnAction[] gear = assertInstanceOf(newPanel().gearActions(), DefaultActionGroup.class).getChildActionsOrStubs();
        JPopupMenu popup = new JPopupMenu();
        popup.add(menuItem(gear[0]));
        popup.add(new JPopupMenu.Separator());
        popup.add(menuItem(gear[5]));
        popup.add(new JPopupMenu.Separator());
        popup.add(menuItem(DumbAwareAction.create("Show Toolbar", e -> { })));
        ActionPopupMenu menu = popupMenu(popup);
        ActionManagerImpl actions = (ActionManagerImpl) ActionManager.getInstance();

        actions.addActionPopup(menu);
        actions.removeActionPopup(menu);

        assertEquals(3, popup.getComponentCount());
        assertSame(gear[5], ((ActionMenuItem) popup.getComponent(2)).getAnAction());
    }

    public void testTheOptionsMenuUsesThePrototypeTextsAndIcons() {
        AnAction[] gear = assertInstanceOf(newPanel().gearActions(), DefaultActionGroup.class).getChildActionsOrStubs();

        assertEquals("Manage Repositories…", gear[0].getTemplatePresentation().getText());
        assertSame(DataformIcons.MANAGE_REPOSITORIES, gear[0].getTemplatePresentation().getIcon());
        assertEquals("Create Workspace…", gear[1].getTemplatePresentation().getText());
        assertSame(DataformIcons.ADD, gear[1].getTemplatePresentation().getIcon());
    }

    public void testTheRepositoryPickerManagesRepositoriesWithTheGear() {
        AnAction[] actions = toolbarActions(newPanel().filesPanel().getToolbar());
        RepositoryPickerAction picker = assertInstanceOf(actions[0], RepositoryPickerAction.class);
        AnAction[] choices = picker.choices().getChildActionsOrStubs();

        AnAction manage = choices[choices.length - 1];
        assertEquals("Manage Repositories…", manage.getTemplatePresentation().getText());
        assertSame(AllIcons.General.Settings, manage.getTemplatePresentation().getIcon());
    }

    public void testTheChangesTabCountsTheWorkspaceChanges() {
        DataformGcpPanel panel = newPanel();
        List<Integer> counts = new ArrayList<>();
        panel.setChangesCountListener(counts::add);

        getProject().getMessageBus().syncPublisher(DataformGcpEvent.TOPIC).onGitStatusesLoaded(List.of(
                new UncommittedChange("definitions/a.sqlx", UncommittedChange.ChangeState.MODIFIED),
                new UncommittedChange("definitions/b.sqlx", UncommittedChange.ChangeState.ADDED)));

        assertEquals(List.of(2), counts);
    }

    public void testTheChangesTabTitleShowsTheCountGrayed() {
        assertEquals("Changes", ChangesTabTitle.of(0));
        String title = ChangesTabTitle.of(4);
        assertTrue(title, title.startsWith("<html>"));
        assertTrue(title, title.contains("Changes"));
        assertTrue(title, title.contains(">4<"));
    }

    private DataformGcpPanel newPanel() {
        DataformGcpPanel panel = new DataformGcpPanel(getProject());
        Disposer.register(getTestRootDisposable(), panel);
        return panel;
    }

    private static ActionMenuItem menuItem(AnAction action) {
        return new ActionMenuItem(action, DataContext.EMPTY_CONTEXT, ActionPlaces.TOOLWINDOW_POPUP, ActionUiKind.POPUP,
                false, false, false);
    }

    private static ActionPopupMenu popupMenu(JPopupMenu popup) {
        return (ActionPopupMenu) Proxy.newProxyInstance(ActionPopupMenu.class.getClassLoader(),
                new Class<?>[]{ActionPopupMenu.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPlace" -> ActionPlaces.TOOLWINDOW_POPUP;
                    case "getComponent" -> popup;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static AnAction[] toolbarActions(javax.swing.JComponent toolbar) {
        return assertInstanceOf(assertInstanceOf(toolbar, ActionToolbar.class).getActionGroup(), DefaultActionGroup.class).getChildActionsOrStubs();
    }

    private static String kind(AnAction action) {
        if (action instanceof Separator) return "---";
        if (action instanceof RepositoryPickerAction || action instanceof WorkspacePickerAction) {
            return action.getClass().getSimpleName();
        }
        return action.getTemplatePresentation().getText();
    }
}
