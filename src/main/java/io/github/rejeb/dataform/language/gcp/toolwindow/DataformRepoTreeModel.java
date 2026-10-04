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

import com.intellij.util.ui.tree.TreeUtil;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class DataformRepoTreeModel extends DefaultTreeModel {

    private Map<String, UncommittedChange.ChangeState> gitStatuses = Map.of();

    public DataformRepoTreeModel(@NotNull String repositoryId) {
        super(new DefaultMutableTreeNode(
                new RootEntry(repositoryId + " (remote)")
        ));
    }

    /**
     * Replaces the Git states of the files and repaints the tree. Must be called on the EDT.
     */
    public void setGitStatuses(@NotNull List<UncommittedChange> changes) {
        Map<String, UncommittedChange.ChangeState> map = new HashMap<>();
        for (UncommittedChange c : changes) {
            map.put(c.path(), c.state());
        }
        this.gitStatuses = Collections.unmodifiableMap(map);
        reload(); // force le re-rendu de tous les nœuds
    }

    /**
     * Returns the Git state of the file, or {@code null} when it is unmodified.
     */
    @Nullable
    public UncommittedChange.ChangeState getChangeState(@NotNull String relativePath) {
        return gitStatuses.get(relativePath);
    }

    /** Marker type for the root node. */
    public record RootEntry(@NotNull String label) {
        @Override public String toString() { return label; }
    }

    public void clear() {
        ((DefaultMutableTreeNode) getRoot()).removeAllChildren();
        reload();
    }

    public void setLoading(boolean loading) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) getRoot();
        root.removeAllChildren();
        if (loading) {
            root.add(new DefaultMutableTreeNode("Loading…"));
        }
        reload();
    }

    public void setFiles(@NotNull List<String> files) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) getRoot();
        root.removeAllChildren();
        Map<String, DefaultMutableTreeNode> dirNodes = new HashMap<>();
        for (String path : new TreeSet<>(files)) {
            int slash = path.lastIndexOf('/');
            dirNode(root, dirNodes, slash < 0 ? "" : path.substring(0, slash))
                    .add(new DefaultMutableTreeNode(new FileEntry(path, path.substring(slash + 1))));
        }
        TreeUtil.sortRecursively(root, Comparator
                .comparingInt((DefaultMutableTreeNode n) -> isDirectory(n) ? 0 : 1)
                .thenComparing(n -> labelOf(n).toLowerCase()));
        reload();
    }

    private static @NotNull DefaultMutableTreeNode dirNode(@NotNull DefaultMutableTreeNode root,
                                                           @NotNull Map<String, DefaultMutableTreeNode> dirNodes,
                                                           @NotNull String dirPath) {
        if (dirPath.isEmpty()) return root;
        DefaultMutableTreeNode existing = dirNodes.get(dirPath);
        if (existing != null) return existing;
        int slash = dirPath.lastIndexOf('/');
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(dirPath.substring(slash + 1));
        dirNode(root, dirNodes, slash < 0 ? "" : dirPath.substring(0, slash)).add(node);
        dirNodes.put(dirPath, node);
        return node;
    }

    @Nullable
    public FileEntry getFileEntry(@NotNull Object node) {
        if (node instanceof DefaultMutableTreeNode dmtn
                && dmtn.getUserObject() instanceof FileEntry fe) {
            return fe;
        }
        return null;
    }

    /**
     * Returns the full relative path of a directory node (e.g. "definitions/sources"),
     * or null when the node is the root or a FileEntry.
     */
    @Nullable
    public String getDirectoryPath(@NotNull Object node) {
        if (!(node instanceof DefaultMutableTreeNode dmtn)) return null;
        if (dmtn.getUserObject() instanceof FileEntry) return null;
        if (dmtn.getUserObject() instanceof RootEntry) return null;

        List<String> segments = new ArrayList<>();
        DefaultMutableTreeNode current = dmtn;
        while (current != null) {
            Object obj = current.getUserObject();
            if (obj instanceof RootEntry) break;
            if (obj instanceof String s) segments.add(0, s);
            current = (DefaultMutableTreeNode) current.getParent();
        }
        return segments.isEmpty() ? null : String.join("/", segments);
    }

    private boolean isDirectory(@NotNull DefaultMutableTreeNode node) {
        return !(node.getUserObject() instanceof FileEntry);
    }

    private @NotNull String labelOf(@NotNull DefaultMutableTreeNode node) {
        Object obj = node.getUserObject();
        if (obj instanceof FileEntry fe) return fe.displayName();
        return obj != null ? obj.toString() : "";
    }

    /**
     * Holds the path and content of a single repository file node.
     *
     * @param relativePath full relative path from content root
     * @param displayName  filename only, used for tree label
     */
    public record FileEntry(
            @NotNull String relativePath,
            @NotNull String displayName
    ) {
        @Override
        public String toString() { return displayName; }
    }
}
