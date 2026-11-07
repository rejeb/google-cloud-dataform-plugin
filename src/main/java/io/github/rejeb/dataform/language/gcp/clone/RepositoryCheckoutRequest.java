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

import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * What the Dataform tab of the Get from Version Control dialog asks to fetch.
 *
 * @param projectId   the GCP project hosting the repository
 * @param location    the location of the repository
 * @param repository  the repository
 * @param source      where its files come from
 * @param workspaceId the workspace to download, {@code null} when the Git remote is cloned
 * @param directory   the directory of the new project
 */
public record RepositoryCheckoutRequest(
        @NotNull String projectId,
        @NotNull String location,
        @NotNull DataformRepository repository,
        @NotNull RepositorySource source,
        @Nullable String workspaceId,
        @NotNull Path directory
) {

    /**
     * @return what the project opened from this request is linked to
     */
    public @NotNull RepositoryLink link() {
        return new RepositoryLink(projectId, location, repository.repositoryId(), repository.serviceAccount(), workspaceId);
    }
}
