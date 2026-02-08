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
package io.github.rejeb.dataform.language.service;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service(Service.Level.PROJECT)
public final class WorkflowSettingsService {
    private final Project project;
    private Map<String, WorkflowSettingsProperty> cachedProperties;
    private long lastModificationStamp = -1;

    public WorkflowSettingsService(Project project) {
        this.project = project;
    }

    public static WorkflowSettingsService getInstance(Project project) {
        return project.getService(WorkflowSettingsService.class);
    }

    @NotNull
    public Map<String, WorkflowSettingsProperty> getWorkflowProperties() {
        WorkflowSettingsYamlFileWrapper yamlFile = findWorkflowSettingsFile();
        if (yamlFile == null) {
            return Collections.emptyMap();
        }

        long currentModStamp = yamlFile.getModificationStamp();
        if (cachedProperties != null && lastModificationStamp == currentModStamp) {
            return cachedProperties;
        }

        cachedProperties = parseWorkflowSettings(yamlFile);
        lastModificationStamp = currentModStamp;
        return cachedProperties;
    }

    @NotNull
    public Collection<String> getPropertiesForPrefix(@Nullable String prefix) {
        Map<String, WorkflowSettingsProperty> properties = getWorkflowProperties();

        if (prefix == null || prefix.isEmpty()) {
            return properties.keySet();
        }
        String[] parentPath = prefix.split("\\.");
        Map<String, WorkflowSettingsProperty> current = properties;

        for (String part : parentPath) {
            WorkflowSettingsService.WorkflowSettingsProperty prop = current.get(part);
            if (prop == null || !prop.hasChildren()) {
                return Collections.emptySet();
            }
            current = prop.children();
        }

        return current.keySet();
    }

    @Nullable
    public WorkflowSettingsYamlFileWrapper findWorkflowSettingsFile() {
        if (DumbService.isDumb(project)) {
            return null;
        }

        return ReadAction.nonBlocking(() -> {
            Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
                    "workflow_settings.yaml",
                    GlobalSearchScope.projectScope(project)
            );

            if (files.isEmpty()) {
                return null;
            }

            VirtualFile file = files.iterator().next();
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

            return psiFile instanceof YAMLFile ? WorkflowSettingsYamlFileWrapper.create((YAMLFile) psiFile, project) : null;
        }).executeSynchronously();
    }

    @NotNull
    private Map<String, WorkflowSettingsProperty> parseWorkflowSettings(@NotNull WorkflowSettingsYamlFileWrapper yamlFile) {
        Map<String, WorkflowSettingsProperty> result = new HashMap<>();

        YAMLDocument document = yamlFile.getDocuments().isEmpty() ? null : yamlFile.getDocuments().get(0);
        if (document == null) {
            return result;
        }

        if (document.getTopLevelValue() instanceof YAMLMapping mapping) {
            for (YAMLKeyValue keyValue : mapping.getKeyValues()) {
                String key = keyValue.getKeyText();
                WorkflowSettingsProperty property = parseProperty(keyValue, yamlFile);
                result.put(key, property);
            }
        }

        return result;
    }

    @NotNull
    private WorkflowSettingsProperty parseProperty(@NotNull YAMLKeyValue keyValue, @NotNull WorkflowSettingsYamlFileWrapper yamlFile) {
        String key = keyValue.getKeyText();
        String value = keyValue.getValueText();

        if (keyValue.getValue() instanceof YAMLMapping mapping) {
            Map<String, WorkflowSettingsProperty> children = new HashMap<>();
            for (YAMLKeyValue child : mapping.getKeyValues()) {
                children.put(child.getKeyText(), parseProperty(child, yamlFile));
            }
            YAMLKeyValue wrappedKeyValue = yamlFile.getWrappedKeyValue(keyValue);
            return new WorkflowSettingsProperty(key, null, wrappedKeyValue, children);
        }
        YAMLKeyValue wrappedKeyValue = yamlFile.getWrappedKeyValue(keyValue);
        return new WorkflowSettingsProperty(key, value, wrappedKeyValue, null);
    }



    public record WorkflowSettingsProperty(
            String name,
            @Nullable String value,
            @Nullable YAMLKeyValue yamlRef,
            @Nullable Map<String, WorkflowSettingsProperty> children) {
        public boolean hasChildren() {
            return children != null && !children.isEmpty();
        }
    }
}
