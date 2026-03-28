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
package io.github.rejeb.dataform.language.projectWizard;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class DataformProjectStructureBuilder {

    private DataformProjectStructureBuilder() {
    }

    /**
     * Creates the standard Dataform project file structure under the given base directory.
     */
    public static void createProjectStructure(@NotNull Project project,
                                              @NotNull VirtualFile baseDir,
                                              @NotNull DataformProjectSettings settings) throws IOException {
        VirtualFile definitionsDir = baseDir.createChildDirectory(project, "definitions");
        baseDir.createChildDirectory(project, "includes");

        VirtualFile workflowSettings = baseDir.createChildData(project, "workflow_settings.yaml");
        String workflowSettingsContent = String.format(
                "defaultProject: %s%n" +
                        "defaultLocation: %s%n" +
                        "defaultDataset: %s%n" +
                        "dataformCoreVersion: %s%n",
                settings.getGcpProjectId(),
                settings.getDefaultLocation(),
                settings.getDefaultSchema(),
                settings.getDataformCoreVersion()
        );
        workflowSettings.setBinaryContent(workflowSettingsContent.getBytes());

        VirtualFile gitignore = baseDir.createChildData(project, ".gitignore");
        String gitignoreContent = "node_modules/\n.dataform/\n*.log";
        gitignore.setBinaryContent(gitignoreContent.getBytes());

        VirtualFile gcloudignore = baseDir.createChildData(project, ".gcloudignore");
        String gcloudIgnoreContent = "# ignore files when pushing to gcp dataform repository using dataform API\n";
        gcloudignore.setBinaryContent(gcloudIgnoreContent.getBytes());

        VirtualFile exampleSqlx = definitionsDir.createChildData(project, "example_table.sqlx");
        String exampleSqlxContent = String.format(
                "config {%n" +
                        "  type: \"table\",%n" +
                        "  schema: \"%s\",%n" +
                        "  description: \"Example table\"%n" +
                        "}%n%n" +
                        "SELECT%n" +
                        "  1 AS id,%n" +
                        "  'example' AS name",
                settings.getDefaultSchema()
        );
        exampleSqlx.setBinaryContent(exampleSqlxContent.getBytes());

        VirtualFile readme = baseDir.createChildData(project, "README.md");
        String readmeContent = String.format(
                "# %s%n%n" +
                        "Dataform project for BigQuery data transformation.%n%n" +
                        "## Setup%n%n" +
                        "1. Install dependencies:%n" +
                        "   ```bash%n" +
                        "   npm install%n" +
                        "   ```%n%n" +
                        "2. Configure your GCP credentials%n%n" +
                        "3. Run dataform:%n" +
                        "   ```bash%n" +
                        "   dataform compile%n" +
                        "   dataform run%n" +
                        "   ```%n%n" +
                        "## Project Structure%n%n" +
                        "- `definitions/` - SQL and SQLX files defining your data transformations%n" +
                        "- `includes/` - JavaScript functions and constants%n" +
                        "- `workflow_settings.yaml` - Workflow execution settings",
                project.getName()
        );
        readme.setBinaryContent(readmeContent.getBytes());
    }
}