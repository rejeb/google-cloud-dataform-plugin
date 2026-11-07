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

import com.intellij.util.io.URLUtil;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The pages of a Dataform repository in the Google Cloud console.
 */
public final class DataformConsoleUrls {

    private static final String DATAFORM = "https://console.cloud.google.com/bigquery/dataform/locations/";

    private DataformConsoleUrls() {
    }

    /**
     * The development page of the workspace, or the page of the repository when no workspace is
     * given (its main branch).
     */
    public static @NotNull String of(@NotNull DataformRepositoryConfig repository, @Nullable String workspaceId) {
        return page(repository, workspaceId == null ? "/details/workspaces" : workspace(workspaceId));
    }

    /**
     * The page of a file of the workspace; the path relative to the repository root is one escaped
     * segment, its slashes included.
     */
    public static @NotNull String ofFile(@NotNull DataformRepositoryConfig repository, @NotNull String workspaceId,
                                         @NotNull String filePath) {
        return page(repository, workspace(workspaceId) + "/files/" + encode(filePath));
    }

    private static @NotNull String workspace(@NotNull String workspaceId) {
        return "/workspaces/" + encode(workspaceId);
    }

    private static @NotNull String page(@NotNull DataformRepositoryConfig repository, @NotNull String path) {
        return DATAFORM + encode(repository.location()) + "/repositories/" + encode(repository.repositoryId())
                + path + "?project=" + encode(repository.projectId());
    }

    private static @NotNull String encode(@NotNull String segment) {
        return URLUtil.encodeURIComponent(segment);
    }
}
