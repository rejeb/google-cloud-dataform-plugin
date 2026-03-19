/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(
        name = "DataformGcpRepositorySettings",
        storages = @Storage("dataform-gcp-repository-setting.xml")
)
public final class GcpRepositorySettingsImpl
        implements GcpRepositorySettings, PersistentStateComponent<GcpRepositorySettingsImpl.State> {

    private State state = new State();

    public GcpRepositorySettingsImpl(@NotNull Project project) {
    }

    @Override
    public @NotNull List<DataformRepositoryConfig> getAllConfigs() {
        List<DataformRepositoryConfig> result = new ArrayList<>();
        for (RepoState r : state.repositories) {
            if (r.projectId != null && r.repositoryId != null && r.location != null) {
                DataformRepositoryConfig c = new DataformRepositoryConfig(r.label, r.projectId, r.repositoryId, r.location);
                if (c.isComplete()) result.add(c);
            }
        }
        return result;
    }

    @Override
    public void saveAllConfigs(@NotNull List<DataformRepositoryConfig> configs) {
        List<RepoState> updated = new ArrayList<>();
        for (DataformRepositoryConfig c : configs) {
            RepoState existing = findRepoState(c.repositoryId());
            RepoState r = existing != null ? existing : new RepoState();
            r.label = c.label();
            r.projectId = c.projectId();
            r.repositoryId = c.repositoryId();
            r.location = c.location();
            updated.add(r);
        }
        state.repositories = updated;
    }

    @Override
    public @Nullable DataformRepositoryConfig getActiveConfig() {
        List<DataformRepositoryConfig> all = getAllConfigs();
        if (all.isEmpty()) return null;
        if (state.activeRepositoryId != null) {
            return all.stream()
                    .filter(c -> c.repositoryId().equals(state.activeRepositoryId))
                    .findFirst()
                    .orElse(all.get(0));
        }
        return all.get(0);
    }

    @Override
    public void setActiveRepositoryId(@Nullable String repositoryId) {
        state.activeRepositoryId = repositoryId;
    }

    @Override
    public @Nullable String getActiveRepositoryId() {
        return state.activeRepositoryId;
    }

    @Override
    public void setSelectedWorkspaceId(@Nullable String workspaceId) {
        RepoState r = activeRepoState();
        if (r != null) {
            r.selectedWorkspaceId = workspaceId;
        }
    }

    @Override
    public @Nullable String getSelectedWorkspaceId() {
        RepoState r = activeRepoState();
        return r != null && StringUtil.isNotEmpty(r.selectedWorkspaceId) ? r.selectedWorkspaceId : null;
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    @Nullable
    private RepoState activeRepoState() {
        if (state.activeRepositoryId == null) {
            return state.repositories.isEmpty() ? null : state.repositories.get(0);
        }
        return findRepoState(state.activeRepositoryId);
    }

    @Nullable
    private RepoState findRepoState(@NotNull String repositoryId) {
        return state.repositories.stream()
                .filter(r -> repositoryId.equals(r.repositoryId))
                .findFirst()
                .orElse(null);
    }

    public static final class State {
        public @Nullable String activeRepositoryId;
        public @NotNull List<RepoState> repositories = new ArrayList<>();
    }

    public static final class RepoState {
        public @Nullable String label;
        public @Nullable String projectId;
        public @Nullable String repositoryId;
        public @Nullable String location;
        public @Nullable String selectedWorkspaceId;

        public RepoState() {
        }

        public RepoState(@Nullable String label, @NotNull String projectId,
                         @NotNull String repositoryId, @NotNull String location) {
            this.label = label;
            this.projectId = projectId;
            this.repositoryId = repositoryId;
            this.location = location;
        }
    }
}
