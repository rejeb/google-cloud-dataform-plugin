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
package io.github.rejeb.dataform.language.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "DataformToolsSettings",
        storages = @Storage("dataform-tools.xml")
)
public final class DataformToolsSettingsImpl
        implements DataformToolsSettings, PersistentStateComponent<DataformToolsSettingsState> {

    private DataformToolsSettingsState state = new DataformToolsSettingsState();

    @Override
    public @Nullable DataformToolsSettingsState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull DataformToolsSettingsState loadedState) {
        this.state = loadedState;
    }

    @Override
    public @NotNull String getCoreInstallPath() {
        return state.coreInstallPath.replace("\\", "/");
    }

    @Override
    public @NotNull String getSqlfluffExecutablePath() {
        return state.sqlfluffExecutablePath.replace("\\", "/");
    }

    @Override
    public @NotNull String getSqlfluffConfigPath() {
        return state.sqlfluffConfigPath.replace("\\", "/");
    }

    @Override
    public @NotNull String getSqlfluffExtraArgs() {
        return state.sqlfluffExtraArgs;
    }

    @Override
    public void update(@NotNull String coreInstallPath,
                       @NotNull String sqlfluffExecutablePath,
                       @NotNull String sqlfluffConfigPath,
                       @NotNull String sqlfluffExtraArgs) {
        state.coreInstallPath = coreInstallPath;
        state.sqlfluffExecutablePath = sqlfluffExecutablePath;
        state.sqlfluffConfigPath = sqlfluffConfigPath;
        state.sqlfluffExtraArgs = sqlfluffExtraArgs;
    }
}
