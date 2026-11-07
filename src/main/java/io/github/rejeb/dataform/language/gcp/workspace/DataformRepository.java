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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Dataform repository as Google Cloud describes it.
 *
 * @param repositoryId   the repository name
 * @param gitRemoteUrl   the URL of the Git remote it is linked to, {@code null} when it has none
 * @param defaultBranch  the default branch of that remote, {@code null} when it has none
 * @param serviceAccount the service account of the repository, blank when it has none
 */
public record DataformRepository(
        @NotNull String repositoryId,
        @Nullable String gitRemoteUrl,
        @Nullable String defaultBranch,
        @NotNull String serviceAccount
) {

    /**
     * @return whether the repository is linked to a Git remote
     */
    public boolean hasGitRemote() {
        return gitRemoteUrl != null && !gitRemoteUrl.isBlank();
    }

    /**
     * The branches of the Git remote a workspace can push its commits to, as in the Dataform console:
     * the default branch and the branch named after the workspace.
     *
     * @return the branches, none when the repository has no remote
     */
    public @NotNull List<String> pushBranches(@NotNull String workspaceId) {
        if (!hasGitRemote()) {
            return List.of();
        }
        return defaultBranch == null || defaultBranch.equals(workspaceId)
                ? List.of(workspaceId)
                : List.of(defaultBranch, workspaceId);
    }

    /**
     * Whether {@code url} names the Git remote of this repository, whatever protocol, credentials or
     * port it goes through.
     */
    public boolean isRemote(@NotNull String url) {
        String remote = remoteDisplayName();
        String other = new DataformRepository(repositoryId, url, null, serviceAccount).remoteDisplayName();
        return remote != null && other != null && withoutPort(remote).equalsIgnoreCase(withoutPort(other));
    }

    private static @NotNull String withoutPort(@NotNull String address) {
        return address.replaceFirst("^([^/:]+):\\d+/", "$1/");
    }

    /**
     * The remote as a short address, without its scheme, its credentials and its {@code .git} suffix,
     * such as {@code github.com/acme/core}.
     *
     * @return the address, {@code null} when the repository has no remote
     */
    public @Nullable String remoteDisplayName() {
        if (!hasGitRemote()) {
            return null;
        }
        String url = gitRemoteUrl.trim();
        boolean scpLike = !url.contains("://");
        String address = url.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "").replaceFirst("^[^/@]+@", "");
        if (scpLike) {
            address = address.replaceFirst(":", "/");
        }
        return address.replaceFirst("(\\.git)?/?$", "");
    }
}
