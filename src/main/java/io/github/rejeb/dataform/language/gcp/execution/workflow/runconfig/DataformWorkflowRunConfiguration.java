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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationTarget;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.Mode;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowRunRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class DataformWorkflowRunConfiguration
        extends RunConfigurationBase<DataformWorkflowRunConfigurationOptions> {

    protected DataformWorkflowRunConfiguration(
            @NotNull Project project,
            @NotNull ConfigurationFactory factory,
            @NotNull String name
    ) {
        super(project, factory, name);
    }

    @NotNull
    @Override
    protected Class<DataformWorkflowRunConfigurationOptions> getDefaultOptionsClass() {
        return DataformWorkflowRunConfigurationOptions.class;
    }

    @NotNull
    @Override
    public DataformWorkflowRunConfigurationOptions getOptions() {
        return (DataformWorkflowRunConfigurationOptions) super.getOptions();
    }

    @NotNull
    public WorkflowRunRequest toWorkflowRunRequest() {
        List<InvocationTarget> targets = getOptions().getSelectedMode().equals(Mode.ACTIONS) ? getOptions().getIncludedTargets().stream()
                                                                                  .filter(s -> s != null && !s.isBlank())
                                                                                  .map(DataformWorkflowRunConfiguration::parseTarget)
                                                                                  .collect(Collectors.toList())
                : List.of();
        List<String> tags = getOptions().getSelectedMode().equals(Mode.TAGS) ? List.copyOf(getOptions().getIncludedTags()) : List.of();
        return new WorkflowRunRequest(
                getOptions().getWorkspaceId(),
                tags,
                targets,
                !Mode.ALL.equals(getOptions().getSelectedMode()) && getOptions().isTransitiveDependenciesIncluded(),
                !Mode.ALL.equals(getOptions().getSelectedMode()) && getOptions().isTransitiveDependentsIncluded(),
                getOptions().isFullyRefreshIncrementalTables()
        );
    }

    /**
     * Parses a target string in the format "schema.name" or "database.schema.name".
     */
    @NotNull
    private static InvocationTarget parseTarget(@NotNull String raw) {
        String[] parts = raw.split("\\.");
        return switch (parts.length) {
            case 2 -> new InvocationTarget(null, parts[0], parts[1]);
            case 3 -> new InvocationTarget(parts[0], parts[1], parts[2]);
            default -> new InvocationTarget(null, "", raw);
        };
    }

    @NotNull
    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new DataformWorkflowSettingsEditor(getProject());
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        if (getOptions().getWorkspaceId() == null || getOptions().getWorkspaceId().isBlank()) {
            throw new RuntimeConfigurationError("Workspace ID is required.");
        }
    }

    @Nullable
    @Override
    public RunProfileState getState(
            @NotNull Executor executor,
            @NotNull ExecutionEnvironment environment
    ) throws ExecutionException {
        return new DataformWorkflowRunProfileState(environment, this);
    }
}
