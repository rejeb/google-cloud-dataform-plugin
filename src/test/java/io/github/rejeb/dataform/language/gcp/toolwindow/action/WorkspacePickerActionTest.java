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
package io.github.rejeb.dataform.language.gcp.toolwindow.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.auth.AuthTrigger;
import io.github.rejeb.dataform.language.gcp.auth.DataformAuthState;
import io.github.rejeb.dataform.language.gcp.gitremote.FakeGitRemoteBranch;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.toolwindow.WorkspaceSelection;
import io.github.rejeb.dataform.language.gcp.workspace.MainBranchSource;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class WorkspacePickerActionTest extends BasePlatformTestCase {

    private static final DataformRepositoryConfig SALES = new DataformRepositoryConfig("sales-id", "Sales", "p", "sales", "europe-west1");
    private static final MainBranchSource GIT_REMOTE =
            new MainBranchSource.GitRemote(new FakeGitRemoteBranch("main", Map.of()));
    private static final AnAction NEW_WORKSPACE = DumbAwareAction.create("Create Workspace…", e -> { });

    private WorkspaceSelection selection;
    private WorkspacePickerAction action;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
        settings.saveAllConfigs(List.of(SALES));
        settings.setActiveRepositoryId(SALES.repositoryConfigId());
        settings.setSelectedWorkspaceId(null);
        selection = new WorkspaceSelection(getProject(), () -> { }, getTestRootDisposable());
        action = new WorkspacePickerAction(selection, NEW_WORKSPACE);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
            settings.saveAllConfigs(List.of());
            settings.setActiveRepositoryId(null);
            settings.setSelectedWorkspaceId(null);
        } finally {
            super.tearDown();
        }
    }

    private String textAfterUpdate() {
        AnActionEvent event = TestActionEvent.createTestEvent(action);
        action.update(event);
        return event.getPresentation().getText();
    }

    public void testItReadsLoadingUntilTheWorkspacesArrive() {
        assertEquals("Loading…", textAfterUpdate());

        selection.setWorkspaces(List.of(new Workspace("w/dev", "dev")));

        assertEquals("No workspace (main)", textAfterUpdate());
    }

    public void testSignedOutItShowsTheSelectedWorkspaceDisabledInsteadOfLoading() {
        GcpRepositorySettings.getInstance(getProject()).setSelectedWorkspaceId("dev");
        DataformAuthState.getInstance().markAuthRequired(AuthTrigger.USER_ACTION);
        try {
            AnActionEvent event = TestActionEvent.createTestEvent(action);

            action.update(event);

            assertEquals("dev", event.getPresentation().getText());
            assertFalse(event.getPresentation().isEnabled());
        } finally {
            DataformAuthState.getInstance().markAuthenticated();
        }
    }

    public void testTheButtonShowsTheBranchIcon() {
        AnActionEvent event = TestActionEvent.createTestEvent(action);

        action.update(event);

        assertSame(DataformIcons.BRANCH, event.getPresentation().getIcon());
    }

    public void testItShowsTheSelectedWorkspace() {
        selection.setWorkspaces(List.of(new Workspace("w/dev", "dev")));
        selection.selectWorkspace("dev");

        assertEquals("dev", textAfterUpdate());
    }

    public void testThePopupListsMainTheWorkspacesThenCreateWorkspace() {
        selection.setWorkspaces(List.of(new Workspace("w/dev", "dev"), new Workspace("w/prod", "prod")));

        AnAction[] children = action.choices().getChildActionsOrStubs();

        List<String> texts = Arrays.stream(children)
                .map(child -> child instanceof Separator ? "---" : child.getTemplatePresentation().getText())
                .toList();
        assertEquals(List.of("No workspace (repository main)", "dev", "prod", "---", "Create Workspace…"), texts);
    }

    public void testThePopupChecksTheSelectedWorkspaceAndShowsBranchIcons() {
        selection.setWorkspaces(List.of(new Workspace("w/dev", "dev"), new Workspace("w/prod", "prod")));
        selection.selectWorkspace("prod");

        AnAction[] children = action.choices().getChildActionsOrStubs();

        List<Boolean> checked = Arrays.stream(children).limit(3)
                .map(child -> ((ToggleAction) child).isSelected(TestActionEvent.createTestEvent(child)))
                .toList();
        assertEquals(List.of(false, false, true), checked);
        assertSame(DataformIcons.BRANCH, children[1].getTemplatePresentation().getIcon());
        assertSame(DataformIcons.BRANCH, children[2].getTemplatePresentation().getIcon());
    }

    public void testChoosingAWorkspaceSelectsIt() {
        selection.setWorkspaces(List.of(new Workspace("w/dev", "dev")));
        AnAction dev = action.choices().getChildActionsOrStubs()[1];

        dev.actionPerformed(TestActionEvent.createTestEvent(dev));

        assertEquals("dev", selection.selectedWorkspaceId());
    }

    public void testItIsDisabledWithoutARepository() {
        GcpRepositorySettings.getInstance(getProject()).setActiveRepositoryId(null);
        GcpRepositorySettings.getInstance(getProject()).saveAllConfigs(List.of());
        AnActionEvent event = TestActionEvent.createTestEvent(action);

        action.update(event);

        assertFalse(event.getPresentation().isEnabled());
    }

    public void testALinkedRepositoryWithACloneOffersTheBranchOfItsRemoteFirst() {
        selection.setWorkspaces(List.of(new Workspace("w/dev", "dev"), new Workspace("w/prod", "prod")));
        selection.setMainBranchSource(GIT_REMOTE);

        assertEquals(List.of("Git remote (origin/main)", "dev", "prod", "---", "Create Workspace…"), choiceTexts());
    }

    public void testALinkedRepositoryWithACloneIsLabelledWithTheBranchOfItsRemote() {
        selection.setWorkspaces(List.of());
        selection.setMainBranchSource(GIT_REMOTE);

        assertEquals("origin/main", textAfterUpdate());
    }

    public void testALinkedRepositoryWithoutACloneOffersOnlyItsWorkspaces() {
        selection.setWorkspaces(List.of(new Workspace("w/dev", "dev"), new Workspace("w/prod", "prod")));
        selection.setMainBranchSource(MainBranchSource.UNAVAILABLE);

        assertEquals(List.of("dev", "prod", "---", "Create Workspace…"), choiceTexts());
    }

    public void testALinkedRepositoryWithoutACloneOrWorkspacesOnlyOffersToCreateOne() {
        selection.setWorkspaces(List.of());
        selection.setMainBranchSource(MainBranchSource.UNAVAILABLE);

        assertEquals(List.of("Create Workspace…"), choiceTexts());
    }

    public void testALinkedRepositoryWithoutACloneIsNotLabelledAsMain() {
        selection.setWorkspaces(List.of());
        selection.setMainBranchSource(MainBranchSource.UNAVAILABLE);

        assertEquals("No workspace", textAfterUpdate());
    }

    private List<String> choiceTexts() {
        return Arrays.stream(action.choices().getChildActionsOrStubs())
                .map(child -> child instanceof Separator ? "---" : child.getTemplatePresentation().getText())
                .toList();
    }
}
