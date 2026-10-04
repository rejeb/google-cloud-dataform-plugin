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

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.Mode;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

public class RunSqlxHelper {

    private static final String TAGS_NAME_SUFFIX = " (tags)";

    /**
     * Creates a temporary tag based run configuration for the given file, prefilled with its tags
     * and the selected workspace, and launches it after the user confirmed the run.
     */
    public static void launchFromTags(@NotNull Project project,
                                      @NotNull VirtualFile file) {
        CompiledGraph graphs = DataformCompilationService.getInstance(project).getCompiledGraph();
        launch(project, file.getNameWithoutExtension() + TAGS_NAME_SUFFIX, true, options -> {
            options.setIncludedTags(graphs.getTags(file.getCanonicalPath()));
            options.setSelectedMode(Mode.TAGS);
        });
    }

    /**
     * Runs a single Dataform action identified by its fully-qualified target
     * ({@code database.schema.name}), without dependencies or dependents.
     */
    public static void launchAction(@NotNull Project project,
                                    @NotNull String targetFullName,
                                    @NotNull String configName) {
        launch(project, configName, false, options -> {
            options.setSelectedMode(Mode.ACTIONS);
            options.setIncludedTargets(List.of(targetFullName));
        });
    }

    private static void launch(@NotNull Project project, @NotNull String name, boolean confirm,
                               @NotNull Consumer<DataformWorkflowRunConfigurationOptions> setup) {
        RunManager runManager = RunManager.getInstance(project);
        DataformWorkflowConfigurationType type = ConfigurationTypeUtil.findConfigurationType(
                DataformWorkflowConfigurationType.class);
        RunnerAndConfigurationSettings settings =
                runManager.createConfiguration(name, type.getConfigurationFactories()[0]);
        DataformWorkflowRunConfigurationOptions options =
                ((DataformWorkflowRunConfiguration) settings.getConfiguration()).getOptions();
        options.setWorkspaceId(GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId());
        options.setTransitiveDependenciesIncluded(false);
        options.setTransitiveDependentsIncluded(false);
        options.setFullyRefreshIncrementalTables(false);
        setup.accept(options);

        runManager.addConfiguration(settings);
        runManager.setSelectedConfiguration(settings);
        settings.setTemporary(true);
        settings.setEditBeforeRun(false);
        settings.setActivateToolWindowBeforeRun(true);
        if (confirm && !DataformRunConfigurationPrompt.confirmContextRun(project, settings)) {
            return;
        }
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());
    }
}
