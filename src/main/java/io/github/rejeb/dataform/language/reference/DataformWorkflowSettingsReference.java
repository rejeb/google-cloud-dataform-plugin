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
package io.github.rejeb.dataform.language.reference;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SlowOperations;
import io.github.rejeb.dataform.language.service.WorkflowSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DataformWorkflowSettingsReference extends PsiReferenceBase<PsiElement> {

    private final String propertyPath;

    public DataformWorkflowSettingsReference(@NotNull PsiElement element,
                                              @NotNull String propertyPath,
                                              @NotNull TextRange rangeInElement) {
        super(element, rangeInElement);
        this.propertyPath = propertyPath;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        Project project = myElement.getProject();
        WorkflowSettingsService service = WorkflowSettingsService.getInstance(project);
        YAMLFile yamlFile = service.findWorkflowSettingsFile();

        if (yamlFile == null) {
            return null;
        }

        String[] parts = propertyPath.split("\\.");

        return findYamlProperty(yamlFile, parts);
    }

    @Override
    public Object @NonNull [] getVariants() {
        Project project = myElement.getProject();
        WorkflowSettingsService service = WorkflowSettingsService.getInstance(project);
        List<LookupElement> variants = new ArrayList<>();
        String prefix = extractParentPrefix(propertyPath);
        Collection<String> properties = service.getPropertiesForPrefix(prefix);

        for (String property : properties) {
            variants.add(
                    LookupElementBuilder.create(property)
                            .withTypeText("workflow_settings.yaml")
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Variable)
            );
        }

        return variants.toArray();
    }

    @Nullable
    private PsiElement findYamlProperty(YAMLFile yamlFile, String[] propertyPath) {
        YAMLDocument document = yamlFile.getDocuments().isEmpty() ? null : yamlFile.getDocuments().get(0);
        if (document == null || !(document.getTopLevelValue() instanceof YAMLMapping currentMapping)) {
            return null;
        }

        for (int i = 0; i < propertyPath.length; i++) {
            String part = propertyPath[i];
            YAMLKeyValue keyValue = currentMapping.getKeyValueByKey(part);

            if (keyValue == null) {
                return null;
            }

            if (i == propertyPath.length - 1) {
                return keyValue;
            }

            if (keyValue.getValue() instanceof YAMLMapping) {
                currentMapping = (YAMLMapping) keyValue.getValue();
            } else {
                return null;
            }
        }

        return null;
    }

    @Nullable
    private String extractParentPrefix(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0) {
            return path.substring(0, lastDot);
        }
        return null;
    }
}
