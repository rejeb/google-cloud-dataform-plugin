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
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lists the files of the project that a push sends to the Dataform workspace: everything under
 * the content root, minus what {@code .gitignore} and {@code .gcloudignore} exclude.
 */
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

        List<IgnoreRule> rules = new ArrayList<>();
        rules.addAll(readIgnorePatterns(contentRoot, ".gitignore"));
        rules.addAll(readIgnorePatterns(contentRoot, ".gcloudignore"));

        List<String> paths = new ArrayList<>();
        VirtualFileFilter filter = fileOrDir -> {
            if (fileOrDir.equals(contentRoot)) return true;
            String name = fileOrDir.getName();
            if (fileOrDir.isDirectory() && ALWAYS_IGNORED_DIRS.contains(name)) return false;
            if (fileOrDir.isDirectory()) {
                String relativePath = VfsUtil.getRelativePath(fileOrDir, contentRoot);
                return relativePath == null || !isIgnored(relativePath, true, rules);
            }
            return true;
        };

        VfsUtil.iterateChildrenRecursively(contentRoot, filter, fileOrDir -> {
            if (!fileOrDir.isDirectory() && !fileOrDir.equals(contentRoot)) {
                String relativePath = VfsUtil.getRelativePath(fileOrDir, contentRoot);
                if (relativePath != null) {
                    boolean alwaysInclude = ALWAYS_INCLUDED_FILES.contains(fileOrDir.getName());
                    if (alwaysInclude || !isIgnored(relativePath, false, rules)) {
                        paths.add(relativePath);
                    }
                }
            }
            return true;
        });

        return List.copyOf(paths);
    }

    @NotNull
    private static List<IgnoreRule> readIgnorePatterns(
            @NotNull VirtualFile contentRoot,
            @NotNull String fileName
    ) {
        List<IgnoreRule> rules = new ArrayList<>();
        VirtualFile ignoreFile = contentRoot.findChild(fileName);
        if (ignoreFile == null || ignoreFile.isDirectory()) return rules;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(ignoreFile.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                IgnoreRule rule = IgnoreRule.parse(line);
                if (rule != null) {
                    rules.add(rule);
                }
            }
        } catch (IOException ignored) {
        }
        return rules;
    }

    /**
     * Whether a path is excluded by the rules, read in order with the last matching rule winning,
     * as gitignore does: a negated rule ({@code !kept.sqlx}) brings back what an earlier one
     * excluded.
     */
    private static boolean isIgnored(@NotNull String relativePath,
                                     boolean directory,
                                     @NotNull List<IgnoreRule> rules) {
        boolean ignored = false;
        for (IgnoreRule rule : rules) {
            if (rule.matches(relativePath, directory)) {
                ignored = !rule.negated();
            }
        }
        return ignored;
    }

    @Nullable
    private static VirtualFile resolveContentRoot(@NotNull Project project) {
        VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
        return roots.length > 0 ? roots[0] : null;
    }

    /**
     * One line of an ignore file. A pattern without a slash matches a name at any depth; one with
     * a slash is relative to the root. {@code *} and {@code ?} never cross a slash, {@code **}
     * does. A trailing slash matches directories only.
     */
    private record IgnoreRule(@NotNull Pattern pattern, boolean negated, boolean directoryOnly) {

        @Nullable
        static IgnoreRule parse(@NotNull String line) {
            String text = line.trim();
            if (text.isEmpty() || text.startsWith("#")) return null;
            boolean negated = text.startsWith("!");
            if (negated) text = text.substring(1);
            boolean directoryOnly = text.endsWith("/");
            if (directoryOnly) text = text.substring(0, text.length() - 1);
            boolean anchored = text.contains("/");
            if (text.startsWith("/")) text = text.substring(1);
            if (text.isEmpty()) return null;
            String regex = (anchored ? "" : "(?:.*/)?") + toRegex(text) + "(?:/.*)?";
            return new IgnoreRule(Pattern.compile(regex), negated, directoryOnly);
        }

        boolean matches(@NotNull String relativePath, boolean directory) {
            if (directoryOnly && !directory && !pattern.matcher(parentOf(relativePath)).matches()) {
                return false;
            }
            return pattern.matcher(relativePath).matches();
        }

        private static String parentOf(@NotNull String relativePath) {
            int slash = relativePath.lastIndexOf('/');
            return slash < 0 ? "" : relativePath.substring(0, slash);
        }

        private static String toRegex(@NotNull String glob) {
            StringBuilder regex = new StringBuilder();
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                if (c == '*') {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        i++;
                        if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                            regex.append("(?:.*/)?");
                            i++;
                        } else {
                            regex.append(".*");
                        }
                    } else {
                        regex.append("[^/]*");
                    }
                } else if (c == '?') {
                    regex.append("[^/]");
                } else {
                    regex.append(Pattern.quote(String.valueOf(c)));
                }
            }
            return regex.toString();
        }
    }
}
