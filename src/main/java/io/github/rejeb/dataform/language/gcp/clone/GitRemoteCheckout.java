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
package io.github.rejeb.dataform.language.gcp.clone;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.checkout.GitCheckoutProvider;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import io.github.rejeb.dataform.language.util.DataformNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Clones the Git remote of a Dataform repository with the local Git credentials. The clone ends on
 * the default branch set on the Dataform repository, which is the one its workspaces are based on
 * and not always the branch the remote checks out.
 */
final class GitRemoteCheckout {

    private static final String OPTION_PREFIX = "-";

    private GitRemoteCheckout() {
    }

    static void start(@NotNull Project project, @NotNull RepositoryCheckoutRequest request,
                      @NotNull CheckoutProvider.Listener listener, @NotNull PendingRepositoryLinks links) {
        String url = request.repository().gitRemoteUrl();
        if (url == null || url.startsWith(OPTION_PREFIX)) {
            notifyUser(project, "The Git remote of " + request.repository().repositoryId() + " is not a valid URL.",
                    NotificationType.ERROR);
            return;
        }
        Path directory = request.directory().toAbsolutePath().normalize();
        Path parent = directory.getParent();
        VirtualFile destinationParent = parent == null ? null : destinationParent(parent);
        if (destinationParent == null) {
            notifyUser(project, "The directory " + directory + " cannot be created.", NotificationType.ERROR);
            return;
        }
        GitCheckoutProvider.clone(project, Git.getInstance(), new LinkingListener(project, request, listener, links),
                destinationParent, url, directory.getFileName().toString(), parent.toString());
    }

    private static @Nullable VirtualFile destinationParent(@NotNull Path parent) {
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            return null;
        }
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(parent);
    }

    private static void notifyUser(@NotNull Project project, @NotNull String message, @NotNull NotificationType type) {
        DataformNotifications.create("Dataform repository not cloned", StringUtil.escapeXmlEntities(message), type)
                .notify(project.isDefault() ? null : project);
    }

    private record LinkingListener(@NotNull Project project,
                                   @NotNull RepositoryCheckoutRequest request,
                                   @NotNull CheckoutProvider.Listener delegate,
                                   @NotNull PendingRepositoryLinks links) implements CheckoutProvider.Listener {

        @Override
        public void directoryCheckedOut(File directory, VcsKey vcs) {
            switchToDefaultBranch(directory.toPath());
            links.register(directory.toPath(), request.link());
            delegate.directoryCheckedOut(directory, vcs);
        }

        @Override
        public void checkoutCompleted() {
            delegate.checkoutCompleted();
        }

        private void switchToDefaultBranch(@NotNull Path directory) {
            String branch = request.repository().defaultBranch();
            if (branch == null || branch.startsWith(OPTION_PREFIX)) {
                return;
            }
            GitLineHandler handler = new GitLineHandler(project, directory, GitCommand.CHECKOUT);
            handler.addParameters(branch);
            GitCommandResult result = Git.getInstance().runCommand(handler);
            if (!result.success()) {
                notifyUser(project, "The clone stays on the branch of the remote: the default branch " + branch
                        + " of the Dataform repository could not be checked out.", NotificationType.WARNING);
            }
        }
    }
}
