/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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
import icons.JavaScriptCoreIcons;
import io.github.rejeb.dataform.language.DataformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public class DataformRepoTreeCellRenderer extends ColoredTreeCellRenderer {

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
            append(file.displayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);

        } else if (userObject instanceof String dirName) {
            // Directory node — userObject is just the folder name string
            setIcon(AllIcons.Nodes.Folder);
            append(dirName, SimpleTextAttributes.REGULAR_ATTRIBUTES);

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
