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
        for (RepositoryConfigState r : state.getRepositories()) {
            DataformRepositoryConfig c = null;
            if (r.getRepositoryConfigId() != null && r.getLabel() != null && r.getProjectId() != null && r.getRepositoryId() != null && r.getLocation() != null) {
                c = new DataformRepositoryConfig(r.getRepositoryConfigId(),
                        r.getLabel(),
                        r.getProjectId(),
                        r.getRepositoryId(),
                        r.getLocation());
            }
            result.add(c);
        }
        return result;
    }

    @Override
    public void saveAllConfigs(@NotNull List<DataformRepositoryConfig> configs) {
        List<RepositoryConfigState> updated = new ArrayList<>();
        for (DataformRepositoryConfig c : configs) {
            RepositoryConfigState existing = findRepoState(c.repositoryConfigId());
            RepositoryConfigState r = existing != null ? existing : new RepositoryConfigState(c.repositoryConfigId(),
                    c.label(),
                    c.projectId(),
                    c.repositoryId(),
                    c.location());
            updated.add(r);
        }
        state.setRepositories(updated);
    }

    @Override
    public @Nullable DataformRepositoryConfig getActiveConfig() {
        List<DataformRepositoryConfig> all = getAllConfigs();
        if (all.isEmpty()) return null;
        if (state.getSelectedConfigId() != null) {
            return all.stream()
                    .filter(c -> c.repositoryConfigId().equals(state.getSelectedConfigId()))
                    .findFirst()
                    .orElse(all.getFirst());
        }
        return all.getFirst();
    }

    @Override
    public void setActiveRepositoryId(@Nullable String selectedConfigId) {
        state.setSelectedConfigId(selectedConfigId);
    }

    @Override
    public @Nullable String getActiveRepositoryId() {
        return state.getSelectedConfigId();
    }

    @Override
    public void setSelectedWorkspaceId(@Nullable String workspaceId) {
        RepositoryConfigState r = activeRepoState();
        if (r != null) {
            r.setSelectedWorkspaceId(workspaceId);
        }
    }

    @Override
    public @Nullable String getSelectedWorkspaceId() {
        RepositoryConfigState r = activeRepoState();
        return r != null && StringUtil.isNotEmpty(r.getSelectedWorkspaceId()) ? r.getSelectedWorkspaceId() : null;
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
    private RepositoryConfigState activeRepoState() {
        if (state.getSelectedConfigId() == null) {
            return state.getRepositories().isEmpty() ? null : state.getRepositories().getFirst();
        }
        return findRepoState(state.getSelectedConfigId());
    }

    @Nullable
    private RepositoryConfigState findRepoState(@NotNull String repositoryConfigId) {
        return state.getRepositories().stream()
                .filter(r -> repositoryConfigId.equals(r.getRepositoryConfigId()))
                .findFirst()
                .orElse(null);
    }
}
