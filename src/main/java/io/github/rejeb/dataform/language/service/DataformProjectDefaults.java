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
package io.github.rejeb.dataform.language.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.ProjectConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The defaults of a Dataform project: what the last compilation used, and where it recorded
 * nothing, what {@code workflow_settings.yaml} says. The compiled graph is read first because the
 * targets, the SQL and the values every feature shows were all produced with its defaults; the file
 * answers before the first compilation.
 *
 * @param database        the default GCP project
 * @param schema          the default dataset
 * @param location        the default BigQuery location
 * @param assertionSchema the dataset assertions are written to
 */
public record DataformProjectDefaults(@Nullable String database, @Nullable String schema,
                                      @Nullable String location, @Nullable String assertionSchema) {

    /**
     * Reads the defaults of the project a file belongs to.
     *
     * @param project the project
     * @param context a file of the Dataform project, which picks its settings file, or {@code null}
     * @param graph   the last compiled graph, or {@code null} before the first compilation
     * @return the defaults, each {@code null} when neither source sets it
     */
    public static @NotNull DataformProjectDefaults of(@NotNull Project project, @Nullable VirtualFile context,
                                                      @Nullable CompiledGraph graph) {
        Map<String, WorkflowSettingsProperty> settings = projectConfigOf(project, context);
        ProjectConfig compiled = graph == null ? null : graph.getProjectConfig();
        return new DataformProjectDefaults(
                pick(settings, "defaultProject", compiled == null ? null : compiled.getDefaultDatabase()),
                pick(settings, "defaultDataset", compiled == null ? null : compiled.getDefaultSchema()),
                pick(settings, "defaultLocation", compiled == null ? null : compiled.getDefaultLocation()),
                pick(settings, "defaultAssertionDataset", compiled == null ? null : compiled.getAssertionSchema()));
    }

    private static @NotNull Map<String, WorkflowSettingsProperty> projectConfigOf(@NotNull Project project,
                                                                                 @Nullable VirtualFile context) {
        WorkflowSettingsProperty dataform = WorkflowSettingsService.getInstance(project)
                .getWorkflowProperties(context).get("dataform");
        WorkflowSettingsProperty projectConfig = dataform == null || dataform.children() == null
                ? null : dataform.children().get(WorkflowSettingsYamlFileWrapper.PROJECT_CONFIG_KEY);
        return projectConfig == null || projectConfig.children() == null ? Map.of() : projectConfig.children();
    }

    private static @Nullable String pick(@NotNull Map<String, WorkflowSettingsProperty> settings, @NotNull String key,
                                         @Nullable String compiled) {
        if (compiled != null && !compiled.isBlank()) return compiled;
        WorkflowSettingsProperty property = settings.get(key);
        String configured = property == null ? null : property.value();
        return configured == null || configured.isBlank() ? null : configured;
    }
}
