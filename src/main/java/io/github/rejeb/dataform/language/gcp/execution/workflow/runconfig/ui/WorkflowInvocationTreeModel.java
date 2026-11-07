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

import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkflowInvocationTreeModel extends DefaultTreeModel {

    private final DefaultMutableTreeNode root;
    private final Map<String, DefaultMutableTreeNode> actionNodes = new HashMap<>();
    private List<InvocationActionResult> actions = List.of();
    private boolean failedOnly;

    private final String title;

    /**
     * @param title the title of the root, the name of the run configuration
     */
    public WorkflowInvocationTreeModel(@NotNull String title) {
        super(new DefaultMutableTreeNode(new InvocationRootNode(title, WorkflowInvocationState.RUNNING, null)));
        this.title = title;
        this.root = (DefaultMutableTreeNode) getRoot();
    }

    /**
     * The node of the action with the given target, {@code null} when it is not shown.
     */
    @Nullable
    public DefaultMutableTreeNode nodeOf(@NotNull String target) {
        return actionNodes.get(target);
    }

    /**
     * The invocation shown at the root.
     */
    @NotNull
    public InvocationRootNode rootNode() {
        return (InvocationRootNode) root.getUserObject();
    }

    /**
     * Updates the tree with the latest progress snapshot. Must be called on the EDT.
     */
    public void update(@NotNull WorkflowInvocationProgress progress) {
        root.setUserObject(new InvocationRootNode(title, progress.state(), RunText.elapsed(progress.summary())));
        actions = List.copyOf(progress.actions());

        for (InvocationActionResult action : actions) {
            DefaultMutableTreeNode node = actionNodes.get(action.target());
            if (!isShown(action)) {
                if (node != null) remove(node);
                continue;
            }
            if (node == null) {
                node = new DefaultMutableTreeNode(action);
                root.add(node);
                actionNodes.put(action.target(), node);
                nodesWereInserted(root, new int[]{root.getIndex(node)});
            }
            node.setUserObject(action);
            nodeChanged(node);
        }

        nodeChanged(root);
    }

    /**
     * Shows only the actions that failed, as the test runner's "Show Failed Only". Must be called
     * on the EDT.
     */
    public void setFailedOnly(boolean failedOnly) {
        if (this.failedOnly == failedOnly) return;
        this.failedOnly = failedOnly;
        root.removeAllChildren();
        actionNodes.clear();
        for (InvocationActionResult action : actions) {
            if (!isShown(action)) continue;
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(action);
            root.add(node);
            actionNodes.put(action.target(), node);
        }
        nodeStructureChanged(root);
    }

    /**
     * Whether only the failed actions are shown.
     */
    public boolean isFailedOnly() {
        return failedOnly;
    }

    private boolean isShown(@NotNull InvocationActionResult action) {
        return !failedOnly || action.state() == InvocationActionState.FAILED;
    }

    private void remove(@NotNull DefaultMutableTreeNode node) {
        int index = root.getIndex(node);
        root.remove(node);
        actionNodes.values().remove(node);
        nodesWereRemoved(root, new int[]{index}, new Object[]{node});
    }

    /**
     * The root of the tree: the run, titled by its run configuration.
     *
     * @param elapsed the time the invocation has run, {@code null} before it exists
     */
    public record InvocationRootNode(
            @NotNull String title,
            @NotNull WorkflowInvocationState state,
            @Nullable Duration elapsed
    ) {
        @Override
        public String toString() {
            return title;
        }
    }
}