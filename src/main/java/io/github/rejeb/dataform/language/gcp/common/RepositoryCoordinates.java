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
package io.github.rejeb.dataform.language.gcp.common;

import com.google.cloud.dataform.v1.RepositoryName;
import com.google.cloud.dataform.v1.WorkspaceName;
import org.jetbrains.annotations.NotNull;

/**
 * The project, location and id of a Dataform repository, and the resource names the Dataform API
 * addresses it and its workspaces by.
 */
public record RepositoryCoordinates(@NotNull String projectId, @NotNull String location, @NotNull String repositoryId) {

    /**
     * The resource name of the repository, {@code projects/{p}/locations/{l}/repositories/{r}}.
     */
    public @NotNull String repositoryName() {
        return RepositoryName.of(projectId, location, repositoryId).toString();
    }

    /**
     * The resource name of one of its workspaces, {@code projects/{p}/locations/{l}/repositories/{r}/workspaces/{w}}.
     */
    public @NotNull String workspaceName(@NotNull String workspaceId) {
        return WorkspaceName.of(projectId, location, repositoryId, workspaceId).toString();
    }
}
