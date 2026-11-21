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

import com.google.cloud.dataform.v1.DataformClient;
import io.github.rejeb.dataform.language.gcp.common.RepositoryCoordinates;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Dataform files of a workspace or of the repository's main branch, one directory level at a
 * time, without {@code node_modules}.
 */
interface FileSource {

    String NODE_MODULES = "node_modules";

    @NotNull
    List<Entry> list(@NotNull String directory);

    @NotNull
    String read(@NotNull String path);

    record Entry(@NotNull String path, boolean directory) {
    }

    @NotNull
    static FileSource of(@NotNull RepositoryCoordinates repository, @Nullable String workspaceId,
                         @NotNull DataformClient client) {
        return Workspace.isMainBranch(workspaceId)
                ? new BranchFileSource(repository, client)
                : new WorkspaceFileSource(repository.workspaceName(workspaceId), client);
    }
}
