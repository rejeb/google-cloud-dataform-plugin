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

import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationState;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.HashMap;
import java.util.Map;

public class WorkflowInvocationTreeModel extends DefaultTreeModel {

    private final DefaultMutableTreeNode root;
    private final Map<String, DefaultMutableTreeNode> actionNodes = new HashMap<>();

    public WorkflowInvocationTreeModel(@NotNull String invocationName) {
        super(new DefaultMutableTreeNode(new InvocationRootNode(invocationName, WorkflowInvocationState.RUNNING)));
        this.root = (DefaultMutableTreeNode) getRoot();
    }

    /**
     * Updates the tree with the latest progress snapshot. Must be called on the EDT.
     */
    public void update(@NotNull WorkflowInvocationProgress progress) {
        root.setUserObject(new InvocationRootNode(progress.invocationName(), progress.state()));

        for (InvocationActionResult action : progress.actions()) {
            DefaultMutableTreeNode node = actionNodes.computeIfAbsent(action.target(), key -> {
                DefaultMutableTreeNode n = new DefaultMutableTreeNode(action);
                root.add(n);
                nodesWereInserted(root, new int[]{root.getIndex(n)});
                return n;
            });
            node.setUserObject(action);
            nodeChanged(node);
        }

        nodeChanged(root);
    }

    public record InvocationRootNode(
            @NotNull String invocationName,
            @NotNull WorkflowInvocationState state
    ) {
        @Override
        public String toString() {
            return invocationName;
        }
    }
}