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
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;

public class DataformWorkflowConfigurationFactoryTest extends BasePlatformTestCase {

    private ConfigurationFactory factory() {
        return new DataformWorkflowConfigurationType().getConfigurationFactories()[0];
    }

    public void testTemplateConfigurationOfTheDefaultProjectDoesNotFail() {
        Project defaultProject = ProjectManager.getInstance().getDefaultProject();

        RunConfiguration configuration = factory().createTemplateConfiguration(defaultProject);

        assertNotNull("the platform builds a template configuration on the default project",
                configuration);
    }

    public void testTemplateConfigurationOfARealProjectCarriesTheSelectedWorkspace() {
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
        settings.saveAllConfigs(List.of(new DataformRepositoryConfig(
                "config-1", "My repo", "my-project", "my-repo", "europe-west1", "")));
        settings.setSelectedWorkspaceId("my-workspace");

        RunConfiguration configuration = factory().createTemplateConfiguration(getProject());

        assertEquals("my-workspace",
                ((DataformWorkflowRunConfiguration) configuration).getWorkspaceId());
    }
}
