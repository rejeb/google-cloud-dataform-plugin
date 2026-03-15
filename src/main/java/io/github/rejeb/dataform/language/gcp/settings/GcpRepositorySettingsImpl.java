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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "DataformGcpRepositorySettings",
        storages = @Storage("dataform-gcp-repository-setting.xml")
)
public final class GcpRepositorySettingsImpl
        implements GcpRepositorySettings, PersistentStateComponent<GcpRepositorySettingsImpl.State> {

    private State state = new State();

    public GcpRepositorySettingsImpl(@NotNull Project project) {}

    @Override
    public @Nullable DataformRepositoryConfig getConfig() {
        if (state.projectId == null || state.repositoryId == null || state.location == null) {
            return null;
        }
        DataformRepositoryConfig config = new DataformRepositoryConfig(
                state.projectId, state.repositoryId, state.location);
        return config.orNull();
    }

    @Override
    public void saveConfig(@NotNull DataformRepositoryConfig config) {
        state.projectId = config.projectId();
        state.repositoryId = config.repositoryId();
        state.location = config.location();
    }

    @Override
    public void setSelectedWorkspaceId(@Nullable String workspaceId) {
        getState().selectedWorkspaceId = workspaceId;
    }

    @Override
    @Nullable
    public String getSelectedWorkspaceId() {
        return getState().selectedWorkspaceId;
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public static final class State {
        public @Nullable String projectId;
        public @Nullable String repositoryId;
        public @Nullable String location;
        public @Nullable String selectedWorkspaceId;
    }
}
