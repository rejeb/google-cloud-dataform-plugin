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

import com.intellij.externalProcessAuthHelper.AuthenticationMode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.util.GitFileUtils;
import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs Git in the directory of the project, whether or not the IDE has it mapped as a Git root.
 */
public final class GitRemoteBranchesImpl implements GitRemoteBranches {

    private static final Pattern FETCH_REMOTE = Pattern.compile("^(\\S+)\\s+(\\S+) \\(fetch\\)$");

    private final Project project;
    private final Supplier<@Nullable Path> directory;

    public GitRemoteBranchesImpl(@NotNull Project project) {
        this(project, () -> GitConfigLocalClones.projectDirectory(project));
    }

    GitRemoteBranchesImpl(@NotNull Project project, @NotNull Supplier<@Nullable Path> directory) {
        this.project = project;
        this.directory = directory;
    }

    @Override
    public @Nullable GitRemoteBranch find(@NotNull DataformRepository repository) {
        Path root = directory.get();
        String branch = repository.defaultBranch();
        if (root == null || branch == null) {
            return null;
        }
        GitCommandResult remotes = run(silent(root, GitCommand.REMOTE, "-v"));
        if (!remotes.success()) {
            return null;
        }
        for (String line : remotes.getOutput()) {
            Matcher remote = FETCH_REMOTE.matcher(line);
            if (remote.matches() && repository.isRemote(remote.group(2))) {
                TrackedBranch tracked = new TrackedBranch(root, remote.group(1), remote.group(2), branch);
                return tracked.exists() ? tracked : null;
            }
        }
        return null;
    }

    private @NotNull GitLineHandler silent(@NotNull Path root, @NotNull GitCommand command, @NotNull String... parameters) {
        GitLineHandler handler = new GitLineHandler(project, root, command);
        handler.setSilent(true);
        handler.addParameters(parameters);
        return handler;
    }

    private static @NotNull GitCommandResult run(@NotNull GitLineHandler handler) {
        return Git.getInstance().runCommand(handler);
    }

    private final class TrackedBranch implements GitRemoteBranch {

        private final Path root;
        private final String remote;
        private final String url;
        private final String name;

        TrackedBranch(@NotNull Path root, @NotNull String remote, @NotNull String url, @NotNull String name) {
            this.root = root;
            this.remote = remote;
            this.url = url;
            this.name = name;
        }

        boolean exists() {
            return run(silent(root, GitCommand.REV_PARSE, "--verify", "--quiet", ref() + "^{commit}")).success();
        }

        @Override
        public @NotNull String name() {
            return name;
        }

        @Override
        public @NotNull String trackingName() {
            return remote + "/" + name;
        }

        @Override
        public void fetch() {
            GitLineHandler handler = silent(root, GitCommand.FETCH, "--no-tags", "--recurse-submodules=no", remote,
                    "+refs/heads/" + name + ":" + ref());
            handler.setUrl(url);
            handler.setIgnoreAuthenticationMode(AuthenticationMode.SILENT);
            GitCommandResult result = run(handler);
            if (!result.success()) {
                throw new GitRemoteBranchException("Could not fetch " + trackingName() + ": "
                        + result.getErrorOutputAsJoinedString(), null);
            }
        }

        @Override
        public @NotNull List<String> listFiles() {
            GitCommandResult result = run(silent(root, GitCommand.LS_TREE, "-r", "--name-only", "-z", ref()));
            if (!result.success()) {
                throw new GitRemoteBranchException("Could not list the files of " + trackingName() + ": "
                        + result.getErrorOutputAsJoinedString(), null);
            }
            return Arrays.stream(result.getOutputAsJoinedString().split("\\x00"))
                    .map(String::strip)
                    .filter(path -> !path.isEmpty())
                    .toList();
        }

        @Override
        public @NotNull String readFile(@NotNull String path) {
            VirtualFile directory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root);
            if (directory == null) {
                throw new GitRemoteBranchException("Could not read " + path + " from " + trackingName()
                        + ": " + root + " does not exist", null);
            }
            try {
                return new String(GitFileUtils.getFileContent(project, directory, ref(), path), StandardCharsets.UTF_8);
            } catch (VcsException e) {
                throw new GitRemoteBranchException("Could not read " + path + " from " + trackingName() + ": "
                        + e.getMessage(), e);
            }
        }

        private @NotNull String ref() {
            return "refs/remotes/" + remote + "/" + name;
        }
    }
}
