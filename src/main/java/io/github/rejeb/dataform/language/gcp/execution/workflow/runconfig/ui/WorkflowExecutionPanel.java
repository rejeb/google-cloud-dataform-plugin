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

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.render.RenderingHelper;
import com.intellij.ui.treeStructure.Tree;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationSummary;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationState;
import io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.RunSqlxHelper;
import io.github.rejeb.dataform.language.gcp.pricing.BigQueryPricingService;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The console of a workflow run, as the prototype lays it out: at the left the run status above the
 * tree of actions; at the right the detail of the invocation or of the selected action. Show Failed
 * Only and Open in Google Cloud Console go to the toolbar of the Run tool window.
 */
public class WorkflowExecutionPanel extends JPanel {

    private static final String CARD_INVOCATION = "invocation";
    private static final String CARD_ACTION = "action";
    private static final float TREE_PROPORTION = 0.4f;

    private final Project project;
    private final WorkflowInvocationTreeModel treeModel;
    private final Tree tree;
    private final RunStatusHeader statusHeader = new RunStatusHeader();
    private final InvocationSummaryPanel summaryPanel;
    private final ActionDetailPanel actionDetailPanel;
    private final ActionJobDetailsCache jobDetails;
    private final Map<String, BigQueryJobDetails> detailsByJobId = new HashMap<>();
    private final Set<String> requestedJobIds = new HashSet<>();
    private final CardLayout rightCardLayout = new CardLayout();
    private final JPanel rightCards = new JPanel(rightCardLayout);
    private final AnAction[] toolbarActions;
    private WorkflowInvocationProgress lastProgress;
    private InvocationActionResult shownAction;
    private boolean terminalSeen;

    /**
     * @param title the name of the run configuration, the title of the root of the tree
     */
    public WorkflowExecutionPanel(@NotNull String title, @NotNull Project project) {
        super(new BorderLayout());
        this.project = project;

        treeModel = new WorkflowInvocationTreeModel(title);
        tree = new Tree(treeModel);
        tree.setCellRenderer(new WorkflowInvocationTreeCellRenderer());
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.putClientProperty(RenderingHelper.SHRINK_LONG_RENDERER, true);
        tree.putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true);

        summaryPanel = new InvocationSummaryPanel(title);
        jobDetails = new ActionJobDetailsCache(project);
        actionDetailPanel = new ActionDetailPanel(project, jobDetails, new ConsoleNavigator());

        rightCards.add(summaryPanel, CARD_INVOCATION);
        rightCards.add(actionDetailPanel, CARD_ACTION);
        rightCardLayout.show(rightCards, CARD_INVOCATION);

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() < 2) return;
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                var node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (node.getUserObject() instanceof InvocationActionResult action) {
                    openActionFile(action);
                }
            }
        });
        tree.addTreeSelectionListener(e -> showSelection());

        toolbarActions = new AnAction[]{new ShowFailedOnlyAction(), new OpenInConsoleAction()};

        JPanel treePanel = new JPanel(new BorderLayout());
        treePanel.add(statusHeader, BorderLayout.NORTH);
        treePanel.add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER);

        OnePixelSplitter mainSplitter = new OnePixelSplitter(false, TREE_PROPORTION);
        mainSplitter.setFirstComponent(treePanel);
        mainSplitter.setSecondComponent(rightCards);
        add(mainSplitter, BorderLayout.CENTER);

        tree.setSelectionRow(0);
    }

    /**
     * The actions of the run console that the Run tool window shows in its toolbar, after Stop:
     * Show Failed Only and Open in Google Cloud Console.
     */
    @NotNull
    public AnAction[] toolbarActions() {
        return toolbarActions.clone();
    }

    @NotNull
    WorkflowInvocationTreeModel treeModel() {
        return treeModel;
    }

    void showFailedOnly(boolean failedOnly) {
        treeModel.setFailedOnly(failedOnly);
        expandAll();
        if (tree.getSelectionPath() == null) tree.setSelectionRow(0);
    }

    /**
     * Refreshes the status, the action tree and the details. Must be called on the EDT.
     */
    public void updateProgress(@NotNull WorkflowInvocationProgress progress) {
        this.lastProgress = progress;
        treeModel.update(progress);
        statusHeader.update(progress);
        summaryPanel.update(progress);
        loadFinishedJobDetails(progress);
        refreshTotals();
        refreshShownAction(progress);
        expandAll();
        if (tree.getSelectionPath() == null) tree.setSelectionRow(0);
        selectFirstFailureOnce(progress);
    }

    /**
     * Selects the action with the given target, when the tree shows it.
     */
    void select(@NotNull String target) {
        DefaultMutableTreeNode node = treeModel.nodeOf(target);
        if (node == null) return;
        TreePath path = new TreePath(node.getPath());
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
    }

    @Nullable
    String selectedTarget() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;
        Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return userObject instanceof InvocationActionResult action ? action.target() : null;
    }

    boolean isRootSelected() {
        TreePath path = tree.getSelectionPath();
        return path != null && path.getLastPathComponent() == treeModel.getRoot();
    }

    /**
     * Releases editor resources. Must be called on disposal.
     */
    public void release() {
        actionDetailPanel.release();
        Disposer.dispose(summaryPanel);
    }

    private void showSelection() {
        TreePath path = tree.getSelectionPath();
        if (path == null || lastProgress == null) {
            shownAction = null;
            rightCardLayout.show(rightCards, CARD_INVOCATION);
            return;
        }
        var node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getUserObject() instanceof InvocationActionResult action) {
            shownAction = action;
            actionDetailPanel.show(lastProgress, action);
            rightCardLayout.show(rightCards, CARD_ACTION);
        } else {
            shownAction = null;
            rightCardLayout.show(rightCards, CARD_INVOCATION);
        }
    }

    private void selectFirstFailureOnce(@NotNull WorkflowInvocationProgress progress) {
        if (terminalSeen || !progress.isTerminal()) return;
        terminalSeen = true;
        if (progress.state() != WorkflowInvocationState.FAILED || !isRootSelected()) return;
        progress.actions().stream()
                .filter(action -> action.state() == InvocationActionState.FAILED)
                .findFirst()
                .ifPresent(action -> select(action.target()));
    }

    private void loadFinishedJobDetails(@NotNull WorkflowInvocationProgress progress) {
        for (InvocationActionResult action : progress.actions()) {
            String jobId = action.jobId();
            if (jobId == null || action.jobProject() == null || !ActionJobDetailsCache.isFinished(action)) continue;
            if (!requestedJobIds.add(jobId)) continue;
            jobDetails.load(action).thenAccept(details ->
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (details != null) {
                            detailsByJobId.put(jobId, details);
                        } else {
                            requestedJobIds.remove(jobId);
                        }
                        refreshTotals();
                    }, ModalityState.any()));
        }
    }

    private void refreshTotals() {
        WorkflowInvocationProgress progress = lastProgress;
        if (progress == null) return;
        BigQueryPricingService pricing = BigQueryPricingService.getInstance();
        summaryPanel.updateTotals(RunCostTotals.of(progress.actions(), detailsByJobId, pricing::quote,
                progress.isTerminal()));
    }

    private void refreshShownAction(@NotNull WorkflowInvocationProgress progress) {
        if (shownAction == null) return;
        for (InvocationActionResult action : progress.actions()) {
            if (action.target().equals(shownAction.target())) {
                if (!action.equals(shownAction)) {
                    shownAction = action;
                    actionDetailPanel.show(progress, action);
                }
                return;
            }
        }
    }

    private void openActionFile(@NotNull InvocationActionResult action) {
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (graph == null) return;
        String fileName = graph.actionFileName(action.target());
        if (fileName == null) return;
        VirtualFile file = DataformPaths.findInProject(project, fileName);
        if (file == null) return;
        FileEditorManager.getInstance(project).openFile(file, true);
    }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private final class ConsoleNavigator implements ActionNavigator {

        @Override
        public void openSource(@NotNull InvocationActionResult action) {
            openActionFile(action);
        }

        @Override
        public void run(@NotNull InvocationActionResult action) {
            RunSqlxHelper.launchAction(project, action.target(), RunText.name(action.target()));
        }

        @Override
        public void select(@NotNull String target) {
            WorkflowExecutionPanel.this.select(target);
        }
    }

    private final class ShowFailedOnlyAction extends ToggleAction {

        private ShowFailedOnlyAction() {
            super("Show Failed Only", "Show only the actions that failed", DataformIcons.FAILED_ONLY);
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return treeModel.isFailedOnly();
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            showFailedOnly(state);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    private final class OpenInConsoleAction extends DumbAwareAction {

        private OpenInConsoleAction() {
            super("Open in Google Cloud Console", "Open the workflow invocation in the Google Cloud console",
                    DataformIcons.EXTERNAL_LINK);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            InvocationSummary summary = lastProgress != null ? lastProgress.summary() : null;
            if (summary != null) BrowserUtil.browse(summary.gcpConsoleUrl());
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(lastProgress != null && lastProgress.summary() != null);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }
}
