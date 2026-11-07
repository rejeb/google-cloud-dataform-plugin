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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public class DataformRepoTreeCellRenderer extends ColoredTreeCellRenderer {

    private final DataformRepoTreeModel treeModel;

    public DataformRepoTreeCellRenderer(@NotNull DataformRepoTreeModel treeModel) {
        this.treeModel = treeModel;
    }

    @Override
    public void customizeCellRenderer(
            @NotNull JTree tree,
            Object value,
            boolean selected,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus
    ) {
        if (!(value instanceof DefaultMutableTreeNode node)) return;
        Object userObject = node.getUserObject();

        if (userObject instanceof DataformRepoTreeModel.RootEntry root) {
            setIcon(AllIcons.Nodes.PpLib);
            append(root.label(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

        } else if (userObject instanceof DataformRepoTreeModel.FileEntry file) {
            setIcon(iconForFile(file.displayName()));
            UncommittedChange.ChangeState state =
                    treeModel.getChangeState(file.relativePath());
            append(file.displayName(), WorkspaceFileStatuses.attributes(state));
            setToolTipText(WorkspaceFileStatuses.tooltip(state));

        } else if (userObject instanceof String dirName) {
            setIcon(AllIcons.Nodes.Folder);
            String directoryPath = treeModel.getDirectoryPath(node);
            boolean changed = directoryPath != null && treeModel.hasChangesUnder(directoryPath);
            append(dirName, changed
                    ? WorkspaceFileStatuses.attributes(UncommittedChange.ChangeState.MODIFIED)
                    : SimpleTextAttributes.REGULAR_ATTRIBUTES);

        } else {
            append(String.valueOf(userObject), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }

    @NotNull
    private static Icon iconForFile(@NotNull String fileName) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
        return fileType.getIcon() != null ? fileType.getIcon() : AllIcons.FileTypes.Text;
    }
}
