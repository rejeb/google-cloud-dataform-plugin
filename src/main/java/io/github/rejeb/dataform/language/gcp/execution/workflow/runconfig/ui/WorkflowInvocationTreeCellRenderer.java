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

import com.intellij.ide.ui.UISettings;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * Renders the rows of a workflow run as the prototype does: the status icon, the action name then
 * its schema grayed, and its duration grayed at the right end of the row, as the test runner shows
 * test durations; the root shows the run configuration in bold and the time the run took.
 */
public class WorkflowInvocationTreeCellRenderer extends ColoredTreeCellRenderer {

    private static final int RIGHT_GAP = 8;

    private @Nullable String rightText;
    private Color rightColor;

    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded,
                                      boolean leaf, int row, boolean hasFocus) {
        rightText = null;
        setToolTipText(null);
        rightColor = selected ? UIUtil.getTreeSelectionForeground(hasFocus) : SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor();
        if (!(value instanceof DefaultMutableTreeNode node)) return;
        Object userObject = node.getUserObject();
        if (userObject instanceof WorkflowInvocationTreeModel.InvocationRootNode root) {
            setIcon(ActionStateIcons.of(root.state()));
            append(root.title(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            rightText = root.elapsed() != null ? RunText.duration(root.elapsed()) : null;
        } else if (userObject instanceof InvocationActionResult action) {
            setIcon(ActionStateIcons.of(action.state()));
            append(RunText.name(action.target()));
            String schema = RunText.schema(action.target());
            if (schema != null) append(" " + schema, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            rightText = RunText.duration(action);
            setToolTipText(action.failureReason() != null ? action.failureReason() : action.target());
        }
    }

    /**
     * The text drawn at the right end of the row: the duration of the action or of the run.
     */
    @Nullable
    String rightText() {
        return rightText;
    }

    @Override
    public @NotNull Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (rightText != null) size.width += rightWidth();
        return size;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (rightText == null) {
            super.paintComponent(g);
            return;
        }
        UISettings.setupAntialiasing(g);
        int textRight = getWidth() - rightWidth();
        Shape clip = g.getClip();
        if (textRight > 0) {
            g.setColor(rightColor);
            g.setFont(getFont());
            g.drawString(rightText, textRight + JBUI.scale(RIGHT_GAP), getTextBaseLine(g.getFontMetrics(), getHeight()));
            g.clipRect(0, 0, textRight, getHeight());
        }
        super.paintComponent(g);
        g.setClip(clip);
    }

    private int rightWidth() {
        return getFontMetrics(getFont()).stringWidth(rightText) + 2 * JBUI.scale(RIGHT_GAP);
    }
}
