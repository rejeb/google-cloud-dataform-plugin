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
package io.github.rejeb.dataform.language.unittest.execution.runconfig;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.unittest.execution.engine.DataformTestScope;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataformTestRunConfiguration extends RunConfigurationBase<DataformTestRunConfigurationOptions> {

    DataformTestRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory, @NotNull String name) {
        super(project, factory, name);
    }

    @Override
    protected @NotNull DataformTestRunConfigurationOptions getOptions() {
        return (DataformTestRunConfigurationOptions) super.getOptions();
    }

    /**
     * Returns which tests run: all of them, those under a directory, or those of one file.
     */
    public @NotNull DataformTestScope getScope() {
        DataformTestScope scope = getOptions().getScope();
        return scope != null ? scope : DataformTestScope.ALL;
    }

    public void setScope(@NotNull DataformTestScope scope) {
        getOptions().setScope(scope);
    }

    /**
     * Returns the project-relative directory or file of the scope, with {@code /} separators.
     */
    public @NotNull String getTargetPath() {
        String path = getOptions().getTargetPath();
        return path != null ? path : "";
    }

    public void setTargetPath(@NotNull String path) {
        getOptions().setTargetPath(DataformPaths.normalize(path));
    }

    @Override
    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new DataformTestSettingsEditor();
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        if (getScope() != DataformTestScope.ALL && getTargetPath().isBlank()) {
            throw new RuntimeConfigurationError("Choose the test file or directory to run.");
        }
    }

    @Override
    public @Nullable RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
        return new DataformTestRunProfileState(environment, this);
    }
}
