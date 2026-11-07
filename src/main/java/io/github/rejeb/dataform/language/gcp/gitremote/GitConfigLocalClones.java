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
package io.github.rejeb.dataform.language.gcp.gitremote;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the Git configuration of the project directory, as Git finds it from there, which needs neither
 * the Git plugin nor a Git executable. Reading it also makes it known to the virtual file system, without
 * which a remote added later would reach no file listener. Must be called off the EDT.
 */
public final class GitConfigLocalClones implements LocalClones {

    private static final Logger LOG = Logger.getInstance(GitConfigLocalClones.class);
    private static final Pattern REMOTE_SECTION = Pattern.compile("^\\[\\s*remote\\s+\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_KEY = Pattern.compile("^url\\s*=\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT = Pattern.compile("\\s*[#;].*$");
    private static final String GIT_LINK_PREFIX = "gitdir:";

    private final Supplier<@Nullable Path> directory;

    public GitConfigLocalClones(@NotNull Project project) {
        this(() -> projectDirectory(project));
    }

    GitConfigLocalClones(@NotNull Supplier<@Nullable Path> directory) {
        this.directory = directory;
    }

    @Override
    public boolean isCloneOf(@NotNull DataformRepository repository) {
        Path start = directory.get();
        if (start == null || !repository.hasGitRemote()) {
            return false;
        }
        try {
            Path config = configFile(start);
            if (config == null || !Files.isRegularFile(config)) {
                return false;
            }
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(config);
            return remoteUrls(read(config)).stream().anyMatch(repository::isRemote);
        } catch (IOException | InvalidPathException e) {
            LOG.warn("Could not read the Git configuration above " + start, e);
            return false;
        }
    }

    private static @Nullable Path configFile(@NotNull Path start) throws IOException {
        for (Path current = start; current != null; current = current.getParent()) {
            Path dotGit = current.resolve(".git");
            if (Files.isDirectory(dotGit)) {
                return dotGit.resolve("config");
            }
            if (Files.isRegularFile(dotGit)) {
                return linkedConfigFile(dotGit);
            }
        }
        return null;
    }

    private static @Nullable Path linkedConfigFile(@NotNull Path dotGit) throws IOException {
        String link = read(dotGit).strip();
        if (!link.startsWith(GIT_LINK_PREFIX)) {
            return null;
        }
        Path gitDirectory = dotGit.resolveSibling(link.substring(GIT_LINK_PREFIX.length()).strip()).normalize();
        Path commonDirectory = gitDirectory.resolve("commondir");
        if (Files.isRegularFile(commonDirectory)) {
            gitDirectory = gitDirectory.resolve(read(commonDirectory).strip()).normalize();
        }
        return gitDirectory.resolve("config");
    }

    private static @NotNull String read(@NotNull Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static @NotNull List<String> remoteUrls(@NotNull String config) {
        List<String> urls = new ArrayList<>();
        boolean inRemote = false;
        for (String line : config.split("\\R")) {
            String text = line.strip();
            if (text.startsWith("[")) {
                inRemote = REMOTE_SECTION.matcher(text).find();
            } else if (inRemote) {
                Matcher url = URL_KEY.matcher(text);
                if (url.matches()) {
                    urls.add(valueOf(url.group(1)));
                }
            }
        }
        return urls;
    }

    private static @NotNull String valueOf(@NotNull String text) {
        if (text.startsWith("\"")) {
            int end = text.indexOf('"', 1);
            return end < 0 ? text.substring(1) : text.substring(1, end);
        }
        return COMMENT.matcher(text).replaceFirst("");
    }

    static @Nullable Path projectDirectory(@NotNull Project project) {
        VirtualFile directory = ProjectUtil.guessProjectDir(project);
        return directory == null ? null : directory.getFileSystem().getNioPath(directory);
    }
}
