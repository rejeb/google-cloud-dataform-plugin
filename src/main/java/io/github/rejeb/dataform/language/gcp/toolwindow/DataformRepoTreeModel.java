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

import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class DataformRepoTreeModel extends DefaultTreeModel {

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

    /** Marker type for the root node. */
    public record RootEntry(@NotNull String label) {
        @Override public String toString() { return label; }
    }

    /**
     * Rebuilds the tree from a map of relative path → file content.
     * Intermediate directory nodes are created automatically.
     */
    public void setFiles(@NotNull Map<String, String> files) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) getRoot();
        root.removeAllChildren();

        // path → node pour tous les dossiers déjà créés
        Map<String, DefaultMutableTreeNode> dirNodes = new TreeMap<>();

        for (String path : new java.util.TreeSet<>(files.keySet())) {
            String[] segments = path.split("/");

            // Créer les dossiers intermédiaires si nécessaire
            StringBuilder currentPath = new StringBuilder();
            for (int i = 0; i < segments.length - 1; i++) {
                if (!currentPath.isEmpty()) currentPath.append("/");
                currentPath.append(segments[i]);

                String dirPath = currentPath.toString();
                if (!dirNodes.containsKey(dirPath)) {
                    DefaultMutableTreeNode dirNode = new DefaultMutableTreeNode(segments[i]);
                    dirNodes.put(dirPath, dirNode);

                    // Rattacher au parent immédiatement
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

            // Nœud fichier — rattaché au dossier parent direct
            String parentPath = segments.length > 1
                    ? path.substring(0, path.lastIndexOf('/'))
                    : null;
            DefaultMutableTreeNode parentNode = parentPath != null
                    ? dirNodes.get(parentPath)
                    : root;

            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(
                    new FileEntry(path, segments[segments.length - 1], files.get(path))
            );
            if (parentNode != null) parentNode.add(fileNode);
            else root.add(fileNode);
        }

        reload();
    }


    /**
     * Clears all nodes from the tree.
     */
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


    /**
     * Holds the path and content of a single repository file node.
     *
     * @param relativePath full relative path from content root
     * @param displayName  filename only, used for tree label
     * @param content      file content as UTF-8 string
     */
    public record FileEntry(
            @NotNull String relativePath,
            @NotNull String displayName,
            @NotNull String content
    ) {
        @Override
        public String toString() { return displayName; }
    }
}
