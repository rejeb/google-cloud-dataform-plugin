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
package io.github.rejeb.dataform.language.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The one place file paths are compared and resolved, whatever system wrote them.
 *
 * <p>The IDE names every file with {@code /}, but the Dataform CLI writes the project-relative
 * {@code fileName} of an action with the separator of the system it runs on: on Windows the
 * compiled graph holds {@code definitions\x.sqlx} for the file the IDE calls
 * {@code definitions/x.sqlx}. A path read from outside the VFS goes through {@link #normalize}
 * before it is compared, and a project-relative path is matched with {@link #pointsTo} and
 * resolved with {@link #findInProject}, never with {@code equals} or a bare {@code endsWith}.</p>
 */
public final class DataformPaths {

    private DataformPaths() {
    }

    /**
     * The path with {@code /} as its only separator.
     */
    @Contract("null -> null; !null -> !null")
    public static @Nullable String normalize(@Nullable String path) {
        return path == null ? null : FileUtil.toSystemIndependentName(path);
    }

    /**
     * Whether a path designates the file a project-relative path names: the two are the same, or
     * the path ends with the relative one on a segment boundary, so that
     * {@code /p/definitions/orders.sqlx} is {@code definitions/orders.sqlx} but
     * {@code /p/definitions/old_orders.sqlx} is not {@code orders.sqlx}. Either path may use
     * either separator.
     */
    public static boolean pointsTo(@Nullable String path, @Nullable String projectRelativePath) {
        if (path == null || projectRelativePath == null || projectRelativePath.isBlank()) {
            return false;
        }
        String full = normalize(path);
        String relative = StringUtil.trimLeading(normalize(projectRelativePath), '/');
        if (relative.isEmpty()) {
            return false;
        }
        return full.equals(relative) || full.endsWith("/" + relative);
    }

    /**
     * The file a project-relative path names, looked up under the project directory, or
     * {@code null} when there is no such file or no project directory.
     */
    public static @Nullable VirtualFile findInProject(@NotNull Project project,
                                                     @Nullable String projectRelativePath) {
        if (projectRelativePath == null || projectRelativePath.isBlank()) {
            return null;
        }
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        return projectDir == null ? null : projectDir.findFileByRelativePath(normalize(projectRelativePath));
    }

    /**
     * The PSI file a project-relative path names, or {@code null} when there is no such valid file.
     * Must be called inside a read action.
     */
    public static @Nullable PsiFile findPsiFileInProject(@NotNull Project project,
                                                         @Nullable String projectRelativePath) {
        VirtualFile file = findInProject(project, projectRelativePath);
        return file == null || !file.isValid() ? null : PsiManager.getInstance(project).findFile(file);
    }

    /**
     * Writes a UTF-8 text file below a directory, creating the missing directories and the file.
     *
     * @param root         the directory the path starts from
     * @param relativePath the path of the file below {@code root}
     * @param content      the text the file gets
     * @throws IOException when a directory or the file cannot be created or written
     */
    public static void writeText(@NotNull VirtualFile root, @NotNull String relativePath,
                                 @NotNull String content) throws IOException {
        String path = normalize(relativePath);
        int slash = path.lastIndexOf('/');
        VirtualFile dir = slash < 0 ? root : VfsUtil.createDirectoryIfMissing(root, path.substring(0, slash));
        dir.findOrCreateChildData(null, path.substring(slash + 1)).setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
    }
}
