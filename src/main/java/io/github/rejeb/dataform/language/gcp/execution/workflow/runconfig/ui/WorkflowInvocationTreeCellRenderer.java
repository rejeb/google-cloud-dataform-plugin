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

import com.intellij.icons.AllIcons;
import com.intellij.ui.AnimatedIcon;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationState;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class WorkflowInvocationTreeCellRenderer extends DefaultTreeCellRenderer {

    @Override
    public Component getTreeCellRendererComponent(
            JTree tree, Object value, boolean sel,
            boolean expanded, boolean leaf, int row, boolean hasFocus
    ) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        if (!(value instanceof DefaultMutableTreeNode node)) return this;

        Object userObject = node.getUserObject();

        if (userObject instanceof WorkflowInvocationTreeModel.InvocationRootNode rootNode) {
            setText(shortName(rootNode.invocationName()));
            setIcon(iconForInvocationState(rootNode.state()));
        } else if (userObject instanceof InvocationActionResult action) {
            setText(action.target()
                    + (action.failureReason() != null ? " — " + action.failureReason() : ""));
            setIcon(iconForActionState(action));
        }

        return this;
    }

    @NotNull
    private Icon iconForInvocationState(@NotNull WorkflowInvocationState state) {
        return switch (state) {
            case RUNNING -> AnimatedIcon.Default.INSTANCE;
            case SUCCEEDED -> AllIcons.RunConfigurations.TestPassed;
            case FAILED -> AllIcons.RunConfigurations.TestFailed;
            case CANCELLED -> AllIcons.RunConfigurations.TestIgnored;
            default -> AllIcons.RunConfigurations.TestUnknown;
        };
    }

    @NotNull
    private Icon iconForActionState(@NotNull InvocationActionResult action) {
        return switch (action.state()) {
            case RUNNING -> AnimatedIcon.Default.INSTANCE;
            case SUCCEEDED -> AllIcons.RunConfigurations.TestPassed;
            case FAILED -> AllIcons.RunConfigurations.TestFailed;
            case SKIPPED, DISABLED, CANCELLED -> AllIcons.RunConfigurations.TestIgnored;
            case PENDING -> AllIcons.RunConfigurations.TestNotRan;
            default -> AllIcons.RunConfigurations.TestUnknown;
        };
    }

    /**
     * Returns the last segment of a GCP resource name (after the last '/').
     */
    @NotNull
    private String shortName(@NotNull String fullName) {
        int idx = fullName.lastIndexOf('/');
        return idx >= 0 ? fullName.substring(idx + 1) : fullName;
    }
}