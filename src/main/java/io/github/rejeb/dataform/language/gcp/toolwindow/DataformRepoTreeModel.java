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

import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.*;

public class DataformRepoTreeModel extends DefaultTreeModel {

    /** path → ChangeState, mis à jour par fetchGitStatuses */
    private Map<String, UncommittedChange.ChangeState> gitStatuses = Map.of();

    public DataformRepoTreeModel(@NotNull String repositoryId) {
        super(new DefaultMutableTreeNode(
                new RootEntry(repositoryId + " (remote)")
        ));
    }

    public void updateRoot(@NotNull String repositoryId) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) getRoot();
        root.setUserObject(new RootEntry(repositoryId + " (remote)"));
        nodeChanged(root);
    }

    /**
     * Met à jour la map des états Git et force le re-rendu du tree.
     * Doit être appelé sur l'EDT.
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
     * Retourne l'état Git du fichier donné, ou {@code null} si non modifié.
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

        Map<String, DefaultMutableTreeNode> dirNodes = new TreeMap<>();

        for (String path : new TreeSet<>(files)) {
            String[] segments = path.split("/");

            StringBuilder currentPath = new StringBuilder();
            for (int i = 0; i < segments.length - 1; i++) {
                if (!currentPath.isEmpty()) currentPath.append("/");
                currentPath.append(segments[i]);

                String dirPath = currentPath.toString();
                if (!dirNodes.containsKey(dirPath)) {
                    DefaultMutableTreeNode dirNode = new DefaultMutableTreeNode(segments[i]);
                    dirNodes.put(dirPath, dirNode);

                    String parentPath = dirPath.contains("/")
                            ? dirPath.substring(0, dirPath.lastIndexOf('/'))
                            : null;
                    DefaultMutableTreeNode parentNode = parentPath != null
                            ? dirNodes.get(parentPath)
                            : root;
                    if (parentNode != null) parentNode.add(dirNode);
                    else root.add(dirNode);
                }
            }

            String parentPath = segments.length > 1
                    ? path.substring(0, path.lastIndexOf('/'))
                    : null;
            DefaultMutableTreeNode parentNode = parentPath != null
                    ? dirNodes.get(parentPath)
                    : root;

            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(
                    new FileEntry(path, segments[segments.length - 1])
            );
            if (parentNode != null) parentNode.add(fileNode);
            else root.add(fileNode);
        }

        sortChildrenRecursively(root);
        reload();
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
     * Retourne le chemin relatif complet d'un nœud dossier (ex: "definitions/sources"),
     * ou null si le nœud est la racine ou un FileEntry.
     */
    @Nullable
    public String getDirectoryPath(@NotNull Object node) {
        if (!(node instanceof DefaultMutableTreeNode dmtn)) return null;
        if (dmtn.getUserObject() instanceof FileEntry) return null;
        if (dmtn.getUserObject() instanceof RootEntry) return null;

        // Remonte les parents pour reconstituer le chemin
        List<String> segments = new ArrayList<>();
        DefaultMutableTreeNode current = dmtn;
        while (current != null) {
            Object obj = current.getUserObject();
            if (obj instanceof RootEntry) break; // stop à la racine
            if (obj instanceof String s) segments.add(0, s);
            current = (DefaultMutableTreeNode) current.getParent();
        }
        return segments.isEmpty() ? null : String.join("/", segments);
    }


    /**
     * Sorts the children of each node recursively: directories first, then files,
     * each group sorted alphabetically (case-insensitive).
     *
     * @param node the node whose children to sort recursively
     */
    private void sortChildrenRecursively(@NotNull DefaultMutableTreeNode node) {
        int count = node.getChildCount();
        if (count == 0) return;

        List<DefaultMutableTreeNode> children = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            children.add((DefaultMutableTreeNode) node.getChildAt(i));
        }

        children.sort(Comparator
                .comparingInt((DefaultMutableTreeNode n) -> isDirectory(n) ? 0 : 1)
                .thenComparing(n -> labelOf(n).toLowerCase())
        );

        node.removeAllChildren();
        for (DefaultMutableTreeNode child : children) {
            node.add(child);
            sortChildrenRecursively(child);
        }
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
