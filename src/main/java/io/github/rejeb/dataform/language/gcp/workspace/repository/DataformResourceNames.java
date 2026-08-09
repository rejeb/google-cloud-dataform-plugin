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
package io.github.rejeb.dataform.language.gcp.workspace.repository;

import com.google.cloud.dataform.v1.WorkspaceName;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the resource names the Dataform API addresses repositories and workspaces by, and reads
 * the one error that is not a failure: a repository with no commit yet answers every listing with
 * a not-found, which callers treat as an empty repository.
 */
final class DataformResourceNames {

    private DataformResourceNames() {
    }

    static String workspaceName(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId
    ) {
        return WorkspaceName.of(projectId, location, repositoryId, workspaceId).toString();
    }

    @NotNull
    static String repositoryName(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId
    ) {
        return "projects/" + projectId
                + "/locations/" + location
                + "/repositories/" + repositoryId;
    }

    static boolean isEmptyRepoException(@NotNull Throwable t) {
        Throwable current = t;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && msg.contains("Reading from empty repo")) return true;
            if (current instanceof com.google.api.gax.rpc.FailedPreconditionException) return true;
            current = current.getCause();
        }
        return false;
    }
}
