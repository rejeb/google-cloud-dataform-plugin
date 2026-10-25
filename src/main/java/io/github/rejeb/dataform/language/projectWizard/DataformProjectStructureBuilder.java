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
import io.github.rejeb.dataform.language.setup.DataformPackageJson;
import io.github.rejeb.dataform.language.setup.DataformPendingPackageInstallActivity;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class DataformProjectStructureBuilder {

    private DataformProjectStructureBuilder() {
    }

    /**
     * Creates the standard Dataform project file structure under the given base directory, pins the
     * Dataform core version in {@code package.json} and installs it once the project is opened.
     */
    public static void createProjectStructure(@NotNull Project project,
                                              @NotNull VirtualFile baseDir,
                                              @NotNull DataformProjectSettings settings) throws IOException {
        VirtualFile definitionsDir = baseDir.createChildDirectory(project, "definitions");
        baseDir.createChildDirectory(project, "includes");

        write(project, baseDir, "workflow_settings.yaml", String.format(
                "defaultProject: %s%n" +
                        "defaultLocation: %s%n" +
                        "defaultDataset: %s%n",
                settings.getGcpProjectId(),
                settings.getDefaultLocation(),
                settings.getDefaultSchema()));
        write(project, baseDir, DataformPackageJson.FILE_NAME, DataformPackageJson.content(settings.getDataformCoreVersion()));
        write(project, baseDir, ".gitignore", "node_modules/\n.dataform/\n*.log");
        write(project, baseDir, ".gcloudignore", "# ignore files when pushing to gcp dataform repository using dataform API\n");
        write(project, definitionsDir, "example_table.sqlx", String.format(
                "config {%n" +
                        "  type: \"table\",%n" +
                        "  schema: \"%s\",%n" +
                        "  description: \"Example table\"%n" +
                        "}%n%n" +
                        "SELECT%n" +
                        "  1 AS id,%n" +
                        "  'example' AS name",
                settings.getDefaultSchema()));
        write(project, baseDir, "README.md", "# Dataform project for BigQuery data transformation.");

        DataformPendingPackageInstallActivity.schedule(project, baseDir);
    }

    private static void write(@NotNull Project project, @NotNull VirtualFile dir, @NotNull String name,
                              @NotNull String content) throws IOException {
        dir.createChildData(project, name).setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
    }
}