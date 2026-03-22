/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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

import com.intellij.openapi.project.Project;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

public class WorkflowExecutionPanel extends JPanel {

    private static final String CARD_INVOCATION = "invocation";
    private static final String CARD_ACTION = "action";

    private final WorkflowInvocationTreeModel treeModel;
    private final Tree tree;
    private final InvocationSummaryPanel summaryPanel;
    private final ActionDetailPanel actionDetailPanel;
    private final CardLayout rightCardLayout = new CardLayout();
    private final JPanel rightCards = new JPanel(rightCardLayout);
    private WorkflowInvocationProgress lastProgress;

    public WorkflowExecutionPanel(@NotNull String invocationName, @NotNull Project project) {
        super(new BorderLayout());

        treeModel = new WorkflowInvocationTreeModel(invocationName);
        tree = new Tree(treeModel);
        tree.setCellRenderer(new WorkflowInvocationTreeCellRenderer());
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);

        summaryPanel = new InvocationSummaryPanel();
        actionDetailPanel = new ActionDetailPanel(project);

        rightCards.add(ScrollPaneFactory.createScrollPane(summaryPanel), CARD_INVOCATION);
        rightCards.add(actionDetailPanel, CARD_ACTION);
        rightCardLayout.show(rightCards, CARD_INVOCATION);

        tree.addTreeSelectionListener(e -> {
            var path = e.getNewLeadSelectionPath();
            if (path == null || lastProgress == null) {
                rightCardLayout.show(rightCards, CARD_INVOCATION);
                return;
            }
            var node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node.getUserObject() instanceof InvocationActionResult action) {
                actionDetailPanel.show(lastProgress, action);
                rightCardLayout.show(rightCards, CARD_ACTION);
            } else {
                rightCardLayout.show(rightCards, CARD_INVOCATION);
            }
        });

        OnePixelSplitter mainSplitter = new OnePixelSplitter(false, 0.30f);
        mainSplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(tree));
        mainSplitter.setSecondComponent(rightCards);

        add(mainSplitter, BorderLayout.CENTER);
    }

    /**
     * Refreshes both the action tree and the invocation summary panel. Must be called on the EDT.
     */
    public void updateProgress(@NotNull WorkflowInvocationProgress progress) {
        this.lastProgress = progress;
        treeModel.update(progress);
        summaryPanel.update(progress);
        expandAll();
    }

    /**
     * Releases editor resources. Must be called on disposal.
     */
    public void release() {
        actionDetailPanel.release();
    }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }
}