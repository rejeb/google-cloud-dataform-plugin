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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Collection;
import java.util.Map;

public interface WorkflowSettingsService {

    static WorkflowSettingsService getInstance(Project project) {
        return project.getService(WorkflowSettingsService.class);
    }

    @NotNull
    Map<String, WorkflowSettingsProperty> getWorkflowProperties();

    /**
     * Same as {@link #getWorkflowProperties()}, resolving the settings file relative to the given
     * file so a Dataform project nested in a larger repository reads its own settings.
     */
    @NotNull
    Map<String, WorkflowSettingsProperty> getWorkflowProperties(@Nullable VirtualFile context);

    @NotNull
    Collection<String> getPropertiesForPrefix(@Nullable String prefix);

    /**
     * Returns the dot-separated paths of every leaf property below the given prefix,
     * relative to that prefix.
     */
    @NotNull
    Collection<String> getLeafPathsForPrefix(@Nullable String prefix);

    @Nullable
    WorkflowSettingsYamlFileWrapper findWorkflowSettingsFile();

    /**
     * Returns the {@code workflow_settings.yaml} file of the project, or {@code null} when it cannot be
     * located without index access on the EDT.
     */
    @Nullable
    VirtualFile findWorkflowSettingsVirtualFile();

    /**
     * Same as {@link #findWorkflowSettingsVirtualFile()}, starting the search at the given file so a
     * Dataform project nested in a larger repository resolves its own settings.
     */
    @Nullable
    VirtualFile findWorkflowSettingsVirtualFile(@Nullable VirtualFile context);

    @Nullable
    WorkflowSettingsProperty getProperty(@Nullable String prop);

    boolean isWorkflowSettingProperty(@NotNull String property);
}
