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

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import org.jetbrains.annotations.NotNull;

public class DataformWorkflowConfigurationFactory extends ConfigurationFactory {

    public DataformWorkflowConfigurationFactory(@NotNull ConfigurationType type) {
        super(type);
    }

    @NotNull
    @Override
    public String getId() {
        return DataformWorkflowConfigurationType.ID;
    }

    /**
     * The platform also builds a template configuration for the default project, which carries no
     * Dataform settings and where the settings service is not available, so the selected workspace
     * is only read from a real project.
     */
    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        DataformWorkflowRunConfiguration configuration =
                new DataformWorkflowRunConfiguration(project, this, "Dataform Workflow");
        GcpRepositorySettings settings = project.isDefault()
                ? null
                : GcpRepositorySettings.getInstance(project);
        if (settings != null) {
            configuration.setWorkspaceId(settings.getSelectedWorkspaceId());
        }
        return configuration;
    }

    @NotNull
    @Override
    public Class<? extends BaseState> getOptionsClass() {
        return DataformWorkflowRunConfigurationOptions.class;
    }
}
