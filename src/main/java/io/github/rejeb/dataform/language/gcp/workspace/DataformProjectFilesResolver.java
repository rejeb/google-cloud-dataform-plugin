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
package io.github.rejeb.dataform.language.gcp.workspace;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DataformProjectFilesResolver {

    private static final Set<String> ALWAYS_IGNORED_DIRS = Set.of(
            ".idea", ".git", "node_modules", ".dataform"
    );

    private static final Set<String> ALWAYS_INCLUDED_FILES = Set.of(
            ".gitignore",
            ".gcloudignore"
    );

    private DataformProjectFilesResolver() {
    }

    @NotNull
    public static List<String> resolve(@NotNull Project project) {
        VirtualFile contentRoot = resolveContentRoot(project);
        if (contentRoot == null) return List.of();

        Set<String> allPatterns = new HashSet<>();
        allPatterns.addAll(readIgnorePatterns(contentRoot, ".gitignore"));
        allPatterns.addAll(readIgnorePatterns(contentRoot, ".gcloudignore"));

        List<String> paths = new ArrayList<>();

        VirtualFileFilter filter = fileOrDir -> {
            if (fileOrDir.equals(contentRoot)) return true;
            String name = fileOrDir.getName();
            if (fileOrDir.isDirectory() && ALWAYS_IGNORED_DIRS.contains(name)) return false;
            if (fileOrDir.isDirectory()) {
                String relativePath = VfsUtil.getRelativePath(fileOrDir, contentRoot);
                return relativePath == null || !isIgnored(relativePath, name, allPatterns);
            }
            return true;
        };

        VfsUtil.iterateChildrenRecursively(contentRoot, filter, fileOrDir -> {
            if (!fileOrDir.isDirectory() && !fileOrDir.equals(contentRoot)) {
                String relativePath = VfsUtil.getRelativePath(fileOrDir, contentRoot);
                if (relativePath != null) {
                    String name = fileOrDir.getName();
                    boolean alwaysInclude = ALWAYS_INCLUDED_FILES.contains(name);
                    if (alwaysInclude || !isIgnored(relativePath, name, allPatterns)) {
                        paths.add(relativePath);
                    }
                }
            }
            return true;
        });

        return List.copyOf(paths);
    }


    @NotNull
    private static Set<String> readIgnorePatterns(
            @NotNull VirtualFile contentRoot,
            @NotNull String fileName
    ) {
        Set<String> patterns = new HashSet<>();
        VirtualFile ignoreFile = contentRoot.findChild(fileName);
        if (ignoreFile == null || ignoreFile.isDirectory()) return patterns;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(ignoreFile.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    patterns.add(line);
                }
            }
        } catch (IOException ignored) {
        }
        return patterns;
    }

    /**
     * Vérifie si un fichier doit être ignoré selon les patterns .gitignore.
     * Supporte les patterns simples : "file.ext", "dir/", "*.log", ".dataform/"
     */
    private static boolean isIgnored(
            @NotNull String relativePath,
            @NotNull String fileName,
            @NotNull Set<String> patterns
    ) {
        for (String pattern : patterns) {
            // Pattern de répertoire : "node_modules/" ou ".dataform/"
            if (pattern.endsWith("/")) {
                String dir = pattern.substring(0, pattern.length() - 1);
                if (relativePath.startsWith(dir + "/") || relativePath.equals(dir)) {
                    return true;
                }
            }
            // Pattern glob simple : "*.log"
            else if (pattern.startsWith("*")) {
                String suffix = pattern.substring(1); // ex: ".log"
                if (fileName.endsWith(suffix)) return true;
            }
            // Match exact du nom de fichier ou du chemin relatif
            else {
                if (fileName.equals(pattern) || relativePath.equals(pattern)) return true;
            }
        }
        return false;
    }

    @Nullable
    private static VirtualFile resolveContentRoot(@NotNull Project project) {
        VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
        return roots.length > 0 ? roots[0] : null;
    }
}
