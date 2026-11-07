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
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.toolwindow.WorkspaceSelection;

import java.util.Arrays;
import java.util.List;

public class RepositoryPickerActionTest extends BasePlatformTestCase {

    private static final DataformRepositoryConfig SALES = new DataformRepositoryConfig("sales-id", "Sales", "p", "sales", "europe-west1");
    private static final DataformRepositoryConfig MARKETING = new DataformRepositoryConfig("marketing-id", "Marketing", "mkt-project", "marketing", "us-central1");
    private static final AnAction MANAGE = DumbAwareAction.create("Manage Repositories…", e -> { });

    private WorkspaceSelection selection;
    private RepositoryPickerAction action;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
        settings.saveAllConfigs(List.of(SALES, MARKETING));
        settings.setActiveRepositoryId(SALES.repositoryConfigId());
        selection = new WorkspaceSelection(getProject(), () -> { }, getTestRootDisposable());
        action = new RepositoryPickerAction(selection, MANAGE);
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

    public void testItShowsTheActiveRepository() {
        AnActionEvent event = TestActionEvent.createTestEvent(action);

        action.update(event);

        assertEquals("Sales", event.getPresentation().getText());
        assertSame(DataformIcons.REPOSITORY, event.getPresentation().getIcon());
    }

    public void testThePopupChecksTheActiveRepositoryAndShowsItsProject() {
        AnAction[] children = action.choices().getChildActionsOrStubs();
        ToggleAction sales = (ToggleAction) children[0];
        ToggleAction marketing = (ToggleAction) children[1];
        AnActionEvent marketingEvent = TestActionEvent.createTestEvent(marketing);

        marketing.update(marketingEvent);

        assertTrue(sales.isSelected(TestActionEvent.createTestEvent(sales)));
        assertFalse(marketing.isSelected(marketingEvent));
        assertEquals("mkt-project", marketingEvent.getPresentation().getClientProperty(ActionUtil.SECONDARY_TEXT));
    }

    public void testThePopupListsTheRepositoriesThenManage() {
        AnAction[] children = action.choices().getChildActionsOrStubs();

        List<String> texts = Arrays.stream(children)
                .map(child -> child instanceof Separator ? "---" : child.getTemplatePresentation().getText())
                .toList();
        assertEquals(List.of("Sales", "Marketing", "---", "Manage Repositories…"), texts);
    }

    public void testChoosingARepositoryActivatesIt() {
        AnAction marketing = action.choices().getChildActionsOrStubs()[1];

        marketing.actionPerformed(TestActionEvent.createTestEvent(marketing));

        assertEquals(MARKETING.repositoryConfigId(), GcpRepositorySettings.getInstance(getProject()).getActiveRepositoryId());
    }

    public void testWithoutRepositoryItSaysSo() {
        GcpRepositorySettings.getInstance(getProject()).saveAllConfigs(List.of());
        GcpRepositorySettings.getInstance(getProject()).setActiveRepositoryId(null);
        AnActionEvent event = TestActionEvent.createTestEvent(action);

        action.update(event);

        assertEquals("No repository", event.getPresentation().getText());
    }
}
