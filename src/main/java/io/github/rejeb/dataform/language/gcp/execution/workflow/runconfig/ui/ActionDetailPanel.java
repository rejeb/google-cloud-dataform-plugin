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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.InlineBanner;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.GraphAction;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.ui.Banners;
import io.github.rejeb.dataform.language.ui.TransparentTabbedPane;
import io.github.rejeb.dataform.language.ui.UiText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * The detail of an action of a workflow run, as the prototype draws it: a header with the status,
 * the name, the type and duration, and buttons to jump to the source and to run the action again;
 * a banner telling why it failed, was skipped or cancelled; then the Summary, Executed SQL and Jobs
 * tabs.
 */
public class ActionDetailPanel extends JPanel {

    private static final int EXECUTED_SQL_TAB = 1;
    private static final int JOBS_TAB = 2;
    private static final String JOBS = "Jobs";
    private static final String PLACE = "Dataform.RunConsoleAction";
    private static final int MARGIN = 16;

    private final Project project;
    private final ActionNavigator navigator;
    private final ActionSummaryPanel summaryPanel;
    private final ActionDetailsTabPanel detailsPanel;
    private final ActionJobDetailsCache jobDetails;
    private final DetailHeader header;
    private final JPanel bannerSlot = new JPanel(new BorderLayout());
    private final JBTabbedPane tabs = new TransparentTabbedPane();
    private @Nullable InvocationActionResult shownAction;
    private @Nullable InlineBanner banner;
    private @Nullable EditorNotificationPanel.Status bannerStatus;
    private int loadGeneration;

    public ActionDetailPanel(@NotNull Project project, @NotNull ActionJobDetailsCache jobDetails) {
        this(project, jobDetails, ActionNavigator.NONE);
    }

    /**
     * @param navigator opens the source of an action, runs it, or selects another action of the run
     */
    public ActionDetailPanel(@NotNull Project project, @NotNull ActionJobDetailsCache jobDetails,
                             @NotNull ActionNavigator navigator) {
        super(new BorderLayout());
        this.project = project;
        this.jobDetails = jobDetails;
        this.navigator = navigator;
        setOpaque(false);
        summaryPanel = new ActionSummaryPanel(project);
        detailsPanel = new ActionDetailsTabPanel(project, () -> tabs.setSelectedIndex(EXECUTED_SQL_TAB));
        Disposer.register(project, detailsPanel);
        AnAction jumpToSource = new JumpToSourceAction();
        header = new DetailHeader(PLACE, false, jumpToSource, new RunThisAction());
        jumpToSource.registerCustomShortcutSet(this, null);
        bannerSlot.setOpaque(false);
        bannerSlot.setBorder(JBUI.Borders.empty(10, MARGIN, 0, MARGIN));
        bannerSlot.setVisible(false);
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(header, BorderLayout.NORTH);
        north.add(bannerSlot, BorderLayout.CENTER);
        tabs.setBorder(JBUI.Borders.empty(6, MARGIN, 0, MARGIN));
        tabs.addTab("Summary", summaryPanel);
        tabs.addTab("Executed SQL", summaryPanel.executedSql());
        tabs.addTab(JOBS, detailsPanel);
        add(north, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    /**
     * Shows an action of the given progress. Must be called on the EDT.
     */
    public void show(@NotNull WorkflowInvocationProgress progress,
                     @NotNull InvocationActionResult action) {
        shownAction = action;
        String type = typeOf(action.target());
        header.show(ActionStateIcons.of(action.state()), RunText.schemaAndName(action.target()),
                UiText.dot(type, RunText.duration(action)));
        showBanner(progress, action);
        summaryPanel.show(progress, action, type);
        int generation = ++loadGeneration;
        if (action.jobId() == null || action.jobProject() == null) {
            showJobs(action, null);
            return;
        }
        detailsPanel.showLoading();
        jobDetails.load(action).thenAccept(details ->
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (generation != loadGeneration) return;
                    summaryPanel.showJobDetails(details);
                    showJobs(action, details);
                }));
    }

    /**
     * Shows the BigQuery jobs of the action and counts them in the title of their tab.
     */
    void showJobs(@NotNull InvocationActionResult action, @Nullable BigQueryJobDetails details) {
        detailsPanel.showDetails(action, details);
        tabs.setTitleAt(JOBS_TAB, UiText.counted(JOBS, details != null ? details.childJobs().size() : 0));
    }

    @NotNull
    JBTabbedPane tabs() {
        return tabs;
    }

    @NotNull
    ActionDetailsTabPanel jobs() {
        return detailsPanel;
    }

    @Nullable
    InlineBanner banner() {
        return banner;
    }

    @Nullable
    EditorNotificationPanel.Status bannerStatus() {
        return bannerStatus;
    }

    @NotNull
    DetailHeader header() {
        return header;
    }

    @NotNull
    ActionSummaryPanel summary() {
        return summaryPanel;
    }

    /**
     * Releases the editors of the summary and of the jobs. Must be called when the panel is disposed.
     */
    public void release() {
        summaryPanel.release();
        Disposer.dispose(detailsPanel);
    }

    private void showBanner(@NotNull WorkflowInvocationProgress progress, @NotNull InvocationActionResult action) {
        bannerSlot.removeAll();
        banner = null;
        bannerStatus = null;
        if (action.state() == InvocationActionState.FAILED) {
            showFailure(action);
        } else if (action.state() == InvocationActionState.SKIPPED) {
            progress.actions().stream()
                    .filter(other -> other.state() == InvocationActionState.FAILED)
                    .findFirst()
                    .ifPresent(this::showSkipped);
        } else if (action.state() == InvocationActionState.CANCELLED) {
            setBanner(Banners.warning("The invocation was stopped before this action finished."),
                    EditorNotificationPanel.Status.Warning);
        }
        bannerSlot.setVisible(banner != null);
        bannerSlot.revalidate();
        bannerSlot.repaint();
    }

    private void showFailure(@NotNull InvocationActionResult action) {
        String reason = action.failureReason() != null && !action.failureReason().isBlank()
                ? action.failureReason() : "The action failed.";
        InlineBanner failure = Banners.error(action.jobId() != null ? "BigQuery job failed" : "Action failed", reason);
        failure.setIcon(DataformIcons.STATUS_FAILED);
        failure.addAction("Show in editor", () -> navigator.openSource(action));
        failure.addAction("Copy error", () -> CopyPasteManager.getInstance().setContents(new StringSelection(reason)));
        setBanner(failure, EditorNotificationPanel.Status.Error);
    }

    private void showSkipped(@NotNull InvocationActionResult failed) {
        InlineBanner skipped = Banners.info("Skipped because an upstream action failed:");
        skipped.addAction(RunText.schemaAndName(failed.target()), () -> navigator.select(failed.target()));
        setBanner(skipped, EditorNotificationPanel.Status.Info);
    }

    private void setBanner(@NotNull InlineBanner shown, @NotNull EditorNotificationPanel.Status status) {
        banner = shown;
        bannerStatus = status;
        bannerSlot.add(shown, BorderLayout.CENTER);
    }

    @Nullable
    private String typeOf(@NotNull String target) {
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (graph == null) return null;
        String fileName = graph.actionFileName(target);
        if (fileName == null) return null;
        return graph.actionsOfFile(fileName).stream()
                .filter(action -> target.equals(action.target().getFullName()))
                .map(GraphAction::kind)
                .findFirst()
                .orElse(null);
    }

    private final class JumpToSourceAction extends DumbAwareAction {

        private JumpToSourceAction() {
            super("Jump to Source", "Open the SQLX file of the action", DataformIcons.FILE);
            AnAction editSource = ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE);
            if (editSource != null) copyShortcutFrom(editSource);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            if (shownAction != null) navigator.openSource(shownAction);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(shownAction != null);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    private final class RunThisAction extends DumbAwareAction {

        private RunThisAction() {
            super("Run This Action", "Run this action only", DataformIcons.RUN);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            if (shownAction != null) navigator.run(shownAction);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(shownAction != null);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }
}
