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
package io.github.rejeb.dataform.language.gcp.settings;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface GcpRepositorySettings {

    static GcpRepositorySettings getInstance(@NotNull Project project) {
        return project.getService(GcpRepositorySettings.class);
    }

    /**
     * @return all configured repositories, never null
     */
    @NotNull List<DataformRepositoryConfig> getAllConfigs();

    /**
     * Replaces the full list of configured repositories.
     */
    void saveAllConfigs(@NotNull List<DataformRepositoryConfig> configs);

    /**
     * @return the currently active repository config, or {@code null} if none configured
     */
    @Nullable DataformRepositoryConfig getActiveConfig();

    /**
     * Sets the active repository by its repositoryId.
     */
    void setActiveRepositoryId(@Nullable String repositoryId);

    /**
     * @return the active repositoryId, or {@code null}
     */
    @Nullable String getActiveRepositoryId();

    void setSelectedWorkspaceId(@Nullable String workspaceId);

    @Nullable String getSelectedWorkspaceId();

    /**
     * @deprecated use {@link #getActiveConfig()}
     */
    @Deprecated
    default @Nullable DataformRepositoryConfig getConfig() {
        return getActiveConfig();
    }

    default @Nullable String getRepositoryId() {
        DataformRepositoryConfig c = getActiveConfig();
        return c != null ? c.repositoryId() : null;
    }

    default @Nullable String getProjectId() {
        DataformRepositoryConfig c = getActiveConfig();
        return c != null ? c.projectId() : null;
    }

    default @Nullable String getLocation() {
        DataformRepositoryConfig c = getActiveConfig();
        return c != null ? c.location() : null;
    }


    final class State {

        private @Nullable String selectedConfigId;
        private @NotNull List<RepositoryConfigState> repositories = new ArrayList<>();

        public State() {
        }

        public void setSelectedConfigId(@Nullable String selectedConfigId) {
            this.selectedConfigId = selectedConfigId;
        }

        public void setRepositories(List<RepositoryConfigState> repositories) {
            this.repositories = new ArrayList<>(repositories);
        }

        public @Nullable String getSelectedConfigId() {
            return this.selectedConfigId;
        }

        public @NonNull List<RepositoryConfigState> getRepositories() {
            return repositories;
        }
    }
}
