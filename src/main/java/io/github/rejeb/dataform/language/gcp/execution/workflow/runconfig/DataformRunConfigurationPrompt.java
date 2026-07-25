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

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Opens the Dataform run configuration editor for launches started from a source location, so the
 * prefilled options can be reviewed before the workflow is invoked.
 *
 * <p>The prompt is driven explicitly by the launch entry point instead of the {@code editBeforeRun}
 * flag, which the platform also honours for the run toolbar and for reruns.
 *
 * <p>Applying the editor registers the configuration in the {@link RunManager} as a permanent one.
 * That side effect is undone here, so reviewing the options of a run started from a file never adds
 * an entry to the saved run configuration list.
 */
final class DataformRunConfigurationPrompt {

    private static final String TITLE = "Run Dataform Workflow";

    private DataformRunConfigurationPrompt() {
    }

    /**
     * Shows the editor for a run started from a source location. The configuration stays temporary,
     * matching how the platform registers context driven runs.
     *
     * @return {@code true} when the user confirmed and the launch may proceed
     */
    static boolean confirmContextRun(@NotNull Project project,
                                     @NotNull RunnerAndConfigurationSettings settings) {
        RunManager runManager = RunManager.getInstance(project);
        boolean registered = isRegistered(runManager, settings);
        boolean confirmed = RunDialog.editConfiguration(project, settings, TITLE, runExecutor());

        if (!confirmed) {
            if (!registered) {
                runManager.removeConfiguration(settings);
            }
            return false;
        }
        settings.setTemporary(true);
        return true;
    }

    /**
     * Shows the editor for a "run current file" launch, which by design owns no entry in the run
     * configuration list. Any registration performed by the editor is reverted.
     *
     * @return {@code true} when the user confirmed and the launch may proceed
     */
    static boolean confirmCurrentFileRun(@NotNull ExecutionEnvironment environment) {
        RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
        if (settings == null) {
            return true;
        }
        RunManager runManager = RunManager.getInstance(environment.getProject());
        RunnerAndConfigurationSettings replaced = findById(runManager, settings.getUniqueID());
        boolean confirmed = RunDialog.editConfiguration(environment, TITLE);

        if (replaced != settings) {
            runManager.removeConfiguration(settings);
            if (replaced != null) {
                runManager.addConfiguration(replaced);
            }
        }
        return confirmed;
    }

    private static boolean isRegistered(@NotNull RunManager runManager,
                                        @NotNull RunnerAndConfigurationSettings settings) {
        return runManager.getAllSettings().stream().anyMatch(existing -> existing == settings);
    }

    @Nullable
    private static RunnerAndConfigurationSettings findById(@NotNull RunManager runManager,
                                                           @NotNull String uniqueId) {
        return runManager.getAllSettings().stream()
                .filter(existing -> uniqueId.equals(existing.getUniqueID()))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private static Executor runExecutor() {
        return ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID);
    }
}
