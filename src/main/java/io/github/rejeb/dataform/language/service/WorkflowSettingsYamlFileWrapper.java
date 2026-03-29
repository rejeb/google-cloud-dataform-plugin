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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.impl.YAMLFileImpl;

import java.util.Collection;
import java.util.Objects;

public class WorkflowSettingsYamlFileWrapper extends YAMLFileImpl {
    public static final String DATAFORM_KEY = "dataform";
    public static final String PROJECT_CONFIG_KEY = "projectConfig";
    private final YAMLFile originalFile;

    public WorkflowSettingsYamlFileWrapper(YAMLFile modifiedFile, YAMLFile originalFile) {
        super(modifiedFile.getViewProvider());
        this.originalFile = originalFile;
    }

    @Override
    public PsiElement findElementAt(int offset) {
        PsiElement element = super.findElementAt(offset);

        if (element == null) {
            return null;
        }

        YAMLKeyValue keyValue = PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class);

        if (keyValue != null) {
            PsiElement original = findOriginalElement(keyValue);

            if (original != null) {
                return original;
            }
        }

        return originalFile;
    }

    public YAMLKeyValue getWrappedKeyValue(YAMLKeyValue keyValue) {
        YAMLKeyValue original = findOriginalElement(keyValue);
        return Objects.requireNonNullElseGet(original, () -> new YAMLKeyValueWrapper(keyValue, originalFile));
    }

    public YAMLKeyValue findOriginalElement(YAMLKeyValue modifiedKey) {
        String keyText = modifiedKey.getKeyText();

        Collection<YAMLKeyValue> originalKeys =
                PsiTreeUtil.findChildrenOfType(originalFile, YAMLKeyValue.class);

        for (YAMLKeyValue originalKey : originalKeys) {
            if (keyText.equals(originalKey.getKeyText())) {
                return originalKey;
            }
        }

        return modifiedKey;
    }


    public static WorkflowSettingsYamlFileWrapper create(YAMLFile originalFile, Project project) {
        String modifiedText = wrapWithParents(originalFile.getText());

        YAMLFile modifiedFile = (YAMLFile) PsiFileFactory.getInstance(project)
                .createFileFromText("worflow_settings_copy.yaml", YAMLLanguage.INSTANCE, modifiedText);

        return new WorkflowSettingsYamlFileWrapper(modifiedFile, originalFile);
    }

    private static String wrapWithParents(String yamlContent) {
        String[] lines = yamlContent.split("\n");
        StringBuilder result = new StringBuilder(DATAFORM_KEY + ":\n").append(" " + PROJECT_CONFIG_KEY + ":\n");

        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                result.append("  ").append(line).append("\n");
            }
        }

        return result.toString();
    }
}
