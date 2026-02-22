/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
package io.github.rejeb.dataform.projectWizard;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class DataformModuleBuilder extends ModuleBuilder {

    private DataformProjectSettings settings = new DataformProjectSettings();

    @Override
    public void setupRootModel(@NotNull ModifiableRootModel modifiableRootModel) throws ConfigurationException {
        Project project = modifiableRootModel.getProject();
        VirtualFile contentEntry = createAndGetContentEntry();
        assert contentEntry != null;
        modifiableRootModel.addContentEntry(contentEntry);

        try {
            createProjectStructure(project, contentEntry, settings);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to create Dataform project: " + e.getMessage());
        }
    }

    @Nullable
    private VirtualFile createAndGetContentEntry() {
        String path = getContentEntryPath();
        if (path == null) {
            return null;
        }
        File contentRoot = new File(path);
        contentRoot.mkdirs();
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(contentRoot);
    }

    @Override
    public ModuleType<?> getModuleType() {
        return new DataformModuleType();
    }

    @Nullable
    @Override
    public ModuleWizardStep getCustomOptionsStep(WizardContext context, Disposable parentDisposable) {
        return new DataformModuleWizardStep(this);
    }

    public DataformProjectSettings getSettings() {
        return settings;
    }

    private void createProjectStructure(@NotNull Project project,
                                        @NotNull VirtualFile baseDir,
                                        @NotNull DataformProjectSettings settings) throws IOException {

        VirtualFile definitionsDir = baseDir.createChildDirectory(this, "definitions");
        baseDir.createChildDirectory(this, "includes");


        VirtualFile workflowSettings = baseDir.createChildData(this, "workflow_settings.yaml");
        String workflowSettingsContent = String.format(
                "defaultProject: %s\n" +
                        "defaultLocation: %s\n" +
                        "defaultDataset: %s",
                settings.getGcpProjectId(),
                settings.getDefaultLocation(),
                settings.getDefaultSchema()
        );
        workflowSettings.setBinaryContent(workflowSettingsContent.getBytes());


        VirtualFile gitignore = baseDir.createChildData(this, ".gitignore");
        String gitignoreContent = "node_modules/\n" +
                ".dataform/\n" +
                "*.log";
        gitignore.setBinaryContent(gitignoreContent.getBytes());


        VirtualFile exampleSqlx = definitionsDir.createChildData(this, "example_table.sqlx");
        String exampleSqlxContent = String.format(
                "config {\n" +
                        "  type: \"table\",\n" +
                        "  schema: \"%s\",\n" +
                        "  description: \"Example table\"\n" +
                        "}\n" +
                        "\n" +
                        "SELECT\n" +
                        "  1 AS id,\n" +
                        "  'example' AS name",
                settings.getDefaultSchema()
        );
        exampleSqlx.setBinaryContent(exampleSqlxContent.getBytes());


        VirtualFile readme = baseDir.createChildData(this, "README.md");
        String readmeContent = String.format(
                "# %s\n" +
                        "\n" +
                        "Dataform project for BigQuery data transformation.\n" +
                        "\n" +
                        "## Setup\n" +
                        "\n" +
                        "1. Install dependencies:\n" +
                        "   ```bash\n" +
                        "   npm install\n" +
                        "   ```\n" +
                        "\n" +
                        "2. Configure your GCP credentials\n" +
                        "\n" +
                        "3. Run dataform:\n" +
                        "   ```bash\n" +
                        "   dataform compile\n" +
                        "   dataform run\n" +
                        "   ```\n" +
                        "\n" +
                        "## Project Structure\n" +
                        "\n" +
                        "- `definitions/` - SQL and SQLX files defining your data transformations\n" +
                        "- `includes/` - JavaScript functions and constants\n" +
                        "- `workflow_settings.yaml` - Workflow execution settings",
                project.getName()
        );
        readme.setBinaryContent(readmeContent.getBytes());
    }

    @Override
    public String getPresentableName() {
        return "Dataform";
    }

    @Override
    public String getDescription() {
        return "Create a new Google Cloud Dataform project for BigQuery data transformation";
    }
}
