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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

public class DataformRepoTreeCellRenderer extends ColoredTreeCellRenderer {

    // Couleurs identiques au plugin Git d'IntelliJ
    private static final Color COLOR_ADDED    = new Color(0x629755);
    private static final Color COLOR_MODIFIED = new Color(0x6897BB);
    private static final Color COLOR_DELETED  = new Color(0x808080);
    private static final Color COLOR_CONFLICT = new Color(0xBC3F3C);

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
            append(file.displayName(), textAttributesFor(state));

        } else if (userObject instanceof String dirName) {
            setIcon(AllIcons.Nodes.Folder);
            append(dirName, SimpleTextAttributes.REGULAR_ATTRIBUTES);

        } else {
            append(String.valueOf(userObject), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }

    @NotNull
    private static SimpleTextAttributes textAttributesFor(
            @Nullable UncommittedChange.ChangeState state
    ) {
        if (state == null) return SimpleTextAttributes.REGULAR_ATTRIBUTES;
        return switch (state) {
            case ADDED         -> new SimpleTextAttributes(
                    SimpleTextAttributes.STYLE_PLAIN, COLOR_ADDED);
            case MODIFIED      -> new SimpleTextAttributes(
                    SimpleTextAttributes.STYLE_PLAIN, COLOR_MODIFIED);
            case DELETED       -> new SimpleTextAttributes(
                    SimpleTextAttributes.STYLE_STRIKEOUT, COLOR_DELETED);
            case HAS_CONFLICTS -> new SimpleTextAttributes(
                    SimpleTextAttributes.STYLE_PLAIN, COLOR_CONFLICT);
            default            -> SimpleTextAttributes.REGULAR_ATTRIBUTES;
        };
    }

    @NotNull
    private static Icon iconForFile(@NotNull String fileName) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
        return fileType.getIcon() != null ? fileType.getIcon() : AllIcons.FileTypes.Text;
    }
}
