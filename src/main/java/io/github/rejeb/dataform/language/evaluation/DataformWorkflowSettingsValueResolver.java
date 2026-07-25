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
package io.github.rejeb.dataform.language.evaluation;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import io.github.rejeb.dataform.language.service.WorkflowSettingsProperty;
import io.github.rejeb.dataform.language.service.WorkflowSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Resolves dotted workflow-settings paths to their YAML leaf value, synchronously.
 *
 * <p>{@code WorkflowSettingsService.getWorkflowProperties()} rebuilds a wrapper PSI file on every
 * call, so the property map is cached per file to keep it off the folding hot path.</p>
 */
public final class DataformWorkflowSettingsValueResolver {

    public static final String WORKFLOW_SETTINGS_FILE_NAME = "workflow_settings.yaml";

    private DataformWorkflowSettingsValueResolver() {
    }

    /**
     * Returns the scalar value of the settings property the given dotted path points at,
     * or {@code null} when the path is unknown or refers to a container.
     */
    @Nullable
    public static String resolve(@NotNull PsiElement context, @NotNull String dottedPath) {
        PsiFile file = context.getContainingFile();
        if (file == null) {
            return null;
        }
        WorkflowSettingsProperty property = lookup(properties(file), dottedPath);
        if (property == null || property.hasChildren()) {
            return null;
        }
        return property.value();
    }

    @NotNull
    private static Map<String, WorkflowSettingsProperty> properties(@NotNull PsiFile file) {
        Project project = file.getProject();
        if (DumbService.isDumb(project)) {
            return Collections.emptyMap();
        }
        PsiFile settingsFile = findSettingsFile(project, file);
        if (settingsFile == null) {
            return Collections.emptyMap();
        }
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(
                WorkflowSettingsService.getInstance(project).getWorkflowProperties(), file, settingsFile));
    }

    /**
     * Resolves the settings file relative to the given file, which also warms the lookup cache the
     * property map relies on.
     */
    @Nullable
    private static PsiFile findSettingsFile(@NotNull Project project, @NotNull PsiFile context) {
        VirtualFile file = WorkflowSettingsService.getInstance(project)
                .findWorkflowSettingsVirtualFile(context.getVirtualFile());
        return file == null ? null : PsiManager.getInstance(project).findFile(file);
    }

    @Nullable
    private static WorkflowSettingsProperty lookup(@NotNull Map<String, WorkflowSettingsProperty> root,
                                                   @NotNull String dottedPath) {
        Map<String, WorkflowSettingsProperty> current = root;
        WorkflowSettingsProperty property = null;
        for (String part : dottedPath.split("\\.")) {
            if (current == null) {
                return null;
            }
            property = current.get(part);
            if (property == null) {
                return null;
            }
            current = property.children();
        }
        return property;
    }
}
