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

import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Forces every Dataform workflow run configuration to open its editor before being launched, so
 * the user always reviews the options prefilled from the place the run was triggered from.
 */
public final class DataformRunConfigurationEditPolicy implements RunManagerListener {

    @Override
    public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        enforce(settings);
    }

    @Override
    public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        enforce(settings);
    }

    /**
     * Applies the policy to every Dataform run configuration already registered in the project.
     *
     * @param project project whose run configurations must be updated
     */
    public static void enforceOnExistingConfigurations(@NotNull Project project) {
        for (RunnerAndConfigurationSettings settings : RunManager.getInstance(project).getAllSettings()) {
            enforce(settings);
        }
    }

    private static void enforce(@NotNull RunnerAndConfigurationSettings settings) {
        if (settings.getConfiguration() instanceof DataformWorkflowRunConfiguration
                && !settings.isEditBeforeRun()) {
            settings.setEditBeforeRun(true);
        }
    }
}
