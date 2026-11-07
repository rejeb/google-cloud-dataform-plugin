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

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBPanelWithEmptyText;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.auth.AuthStatus;
import io.github.rejeb.dataform.language.gcp.auth.DataformAuthListener;
import io.github.rejeb.dataform.language.gcp.auth.DataformAuthState;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
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
import io.github.rejeb.dataform.language.gcp.toolwindow.action.WorkspaceTransferAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher.GcpPanelActionDispatcher;
import io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher.GcpPanelActionDispatcherImpl;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.ui.EmptyStates;
import io.github.rejeb.dataform.language.ui.Toolbars;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The two tabs of the Dataform tool window, Files and Changes, with their toolbars. Rebuilds the
 * views when the active repository or workspace changes.
 */
public class DataformGcpPanel implements Disposable {

    private final Project project;
    private final GcpPanelActionDispatcher dispatcher;
    private final WorkspaceSelection selection;
    private final List<AnAction> titleActions;
    private final DefaultActionGroup gearActions;
    private final WorkspacePickerAction workspacePicker;
    private final SimpleToolWindowPanel filesPanel = new SimpleToolWindowPanel(true, true);
    private final SimpleToolWindowPanel changesPanel = new SimpleToolWindowPanel(true, true);
    private @Nullable Disposable views;
    private IntConsumer changesCountListener = count -> {
    };

    public DataformGcpPanel(@NotNull Project project) {
        this.project = project;
        this.dispatcher = new GcpPanelActionDispatcherImpl(project);
        this.selection = new WorkspaceSelection(project, this::refresh, this);
        AnAction createWorkspace = new CreateWorkspaceAction(dispatcher);
        this.titleActions = List.of(new RefreshAction(dispatcher));
        this.gearActions = new DefaultActionGroup(
                new ManageRepositoriesAction(project, this::refresh, DataformIcons.MANAGE_REPOSITORIES),
                createWorkspace, Separator.getInstance(), new GcpAuthAction(project),
                Separator.getInstance(), new HideDataformToolsAction());
        ActionManagerEx.getInstanceEx().addActionPopupMenuListener(
                new OptionsMenuTrimmer(Arrays.asList(gearActions.getChildActionsOrStubs())), this);

        this.workspacePicker = new WorkspacePickerAction(selection, createWorkspace);
        Supplier<String> workspace = selection::selectedWorkspaceId;
        AnAction[] toolbar = {
                new RepositoryPickerAction(selection, new ManageRepositoriesAction(project, this::refresh)),
                workspacePicker,
                Separator.getInstance(),
                WorkspaceTransferAction.pull(workspace, dispatcher),
                WorkspaceTransferAction.push(workspace, dispatcher),
                createWorkspace,
                Separator.getInstance(),
                new OpenInConsoleAction(selection::activeRepository, workspace)
        };
        filesPanel.setToolbar(wrapping(Toolbars.horizontal("Dataform.Files.Toolbar", filesPanel, toolbar)));
        changesPanel.setToolbar(wrapping(Toolbars.horizontal("Dataform.Changes.Toolbar", changesPanel, toolbar)));
        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(DataformAuthListener.TOPIC, status -> refresh());
        project.getMessageBus().connect(this).subscribe(DataformGcpEvent.TOPIC, new DataformGcpEvent() {
            @Override
            public void onGitStatusesLoaded(@NotNull List<UncommittedChange> changes) {
                changesCountListener.accept(changes.size());
            }

            @Override
            public void onRepositoryChanged() {
                refresh();
            }
        });
        refresh();
    }

    public GcpPanelActionDispatcher getDispatcher() {
        return dispatcher;
    }

    /**
     * The Files tab: the remote files of the selected workspace.
     */
    public @NotNull SimpleToolWindowPanel filesPanel() {
        return filesPanel;
    }

    /**
     * The Changes tab: the uncommitted changes of the selected workspace.
     */
    public @NotNull SimpleToolWindowPanel changesPanel() {
        return changesPanel;
    }

    /**
     * The actions of the tool window title bar: Refresh.
     */
    public @NotNull List<AnAction> titleActions() {
        return titleActions;
    }

    /**
     * The actions added to the tool window Options menu: Manage Repositories…, Create Workspace…
     * and the Google Cloud sign-in.
     */
    public @NotNull ActionGroup gearActions() {
        return gearActions;
    }

    /**
     * Receives the number of uncommitted changes of the selected workspace each time it is known;
     * zero when another repository or workspace is shown.
     */
    public void setChangesCountListener(@NotNull IntConsumer listener) {
        this.changesCountListener = listener;
    }

    /**
     * Rebuilds both tabs for the active repository and reloads its workspaces.
     */
    public void refresh() {
        if (views != null) {
            Disposer.dispose(views);
        }
        Disposable current = Disposer.newDisposable(this, "Dataform tool window views");
        views = current;
        changesCountListener.accept(0);
        if (DataformAuthState.getInstance().getStatus() != AuthStatus.NONE) {
            filesPanel.setContent(signedOut());
            changesPanel.setContent(signedOut());
            return;
        }
        DataformRepositoryConfig config = GcpRepositorySettings.getInstance(project).getActiveConfig();
        if (config == null) {
            filesPanel.setContent(noRepository());
            changesPanel.setContent(noRepository());
            return;
        }
        selection.startLoading();
        filesPanel.setContent(new FilesView(project, config, () -> showWorkspaceChoices(filesPanel), current));
        changesPanel.setContent(new CommitView(project, dispatcher, () -> showWorkspaceChoices(changesPanel), current));
        dispatcher.refreshWorkspaces();
    }

    @Override
    public void dispose() {
    }

    private @NotNull JBPanelWithEmptyText noRepository() {
        JBPanelWithEmptyText panel = new JBPanelWithEmptyText();
        EmptyStates.show(panel.getEmptyText(), "No Dataform repository", "Add repository…", this::openManageDialog);
        return panel;
    }

    private @NotNull JBPanelWithEmptyText signedOut() {
        JBPanelWithEmptyText panel = new JBPanelWithEmptyText();
        EmptyStates.showWithLinkBelow(panel.getEmptyText(), "Not signed in to Google Cloud",
                "Sign in to browse Dataform repositories and workspaces.", "Sign in to Google Cloud",
                () -> ApplicationManager.getApplication().executeOnPooledThread(
                        () -> DataformCredentialsService.getInstance().signIn(project)));
        return panel;
    }

    private void showWorkspaceChoices(@NotNull JComponent anchor) {
        JBPopupFactory.getInstance()
                .createActionGroupPopup("Workspaces", workspacePicker.choices(),
                        DataManager.getInstance().getDataContext(anchor),
                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
                .showInCenterOf(anchor);
    }

    private static @NotNull JComponent wrapping(@NotNull ActionToolbar toolbar) {
        toolbar.setLayoutStrategy(ToolbarLayoutStrategy.WRAP_STRATEGY);
        return toolbar.getComponent();
    }

    private void openManageDialog() {
        new ManageRepositoriesDialog(project).show();
        refresh();
    }
}
