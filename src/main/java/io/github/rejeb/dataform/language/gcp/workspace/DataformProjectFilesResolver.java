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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class DataformProjectFilesResolver {

    private static final List<String> WATCHED_DIRS = List.of("definitions", "includes");
    private static final List<String> WATCHED_ROOT_FILES = List.of("workflow_settings.yaml", "dataform.json");

    private DataformProjectFilesResolver() {}

    /**
     * Resolves all Dataform project files eligible for push.
     * <p>Includes:
     * <ul>
     *   <li>All files recursively under {@code definitions/} and {@code includes/}</li>
     *   <li>{@code workflow_settings.yaml} and {@code dataform.json} if present at content root</li>
     * </ul>
     * <p>Returned paths are relative to the content root.
     *
     * @param project the current IntelliJ project
     * @return relative file paths eligible for push, never null
     */
    @NotNull
    public static List<String> resolve(@NotNull Project project) {
        VirtualFile contentRoot = resolveContentRoot(project);
        if (contentRoot == null) {
            return List.of();
        }

        List<String> paths = new ArrayList<>();

        for (String dirName : WATCHED_DIRS) {
            VirtualFile dir = contentRoot.findChild(dirName);
            if (dir != null && dir.isDirectory()) {
                collectRelativePaths(dir, contentRoot, paths);
            }
        }

        for (String fileName : WATCHED_ROOT_FILES) {
            VirtualFile file = contentRoot.findChild(fileName);
            if (file != null && !file.isDirectory()) {
                paths.add(fileName);
            }
        }

        return List.copyOf(paths);
    }

    /**
     * @return the first content root of the project, or {@code null} if none
     */
    @Nullable
    private static VirtualFile resolveContentRoot(@NotNull Project project) {
        VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
        return roots.length > 0 ? roots[0] : null;
    }

    private static void collectRelativePaths(
            @NotNull VirtualFile dir,
            @NotNull VirtualFile contentRoot,
            @NotNull List<String> accumulator
    ) {
        VfsUtil.iterateChildrenRecursively(dir, null, fileOrDir -> {
            if (!fileOrDir.isDirectory()) {
                String relativePath = VfsUtil.getRelativePath(fileOrDir, contentRoot);
                if (relativePath != null) {
                    accumulator.add(relativePath);
                }
            }
            return true;
        });
    }
}
