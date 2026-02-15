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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import io.github.rejeb.dataform.language.service.WorkflowSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

public class DataformWorkflowSettingsReference extends PsiReferenceBase<PsiElement> {

    public DataformWorkflowSettingsReference(@NotNull PsiElement element) {
        super(element);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        return findYamlProperty(WorkflowSettingsService.getInstance(myElement.getProject()), propertyPath());
    }

    private String[] propertyPath() {
        return myElement.getText().split("\\.");
    }

    @Override
    public Object @NonNull [] getVariants() {
        return new Object[0];
    }

    @Nullable
    private PsiElement findYamlProperty(WorkflowSettingsService service, String[] propertyPath) {

        var current = service.getWorkflowProperties();
        for (int i = 0; i < propertyPath.length; i++) {
            String part = propertyPath[i];
            WorkflowSettingsService.WorkflowSettingsProperty prop = current.get(part);

            if (prop == null) {
                return null;
            }

            if (i == propertyPath.length - 1) {
                return prop.yamlRef();
            }
            if (prop.hasChildren()) {
                current = prop.children();
            } else {
                return null;
            }
        }

        return null;
    }

    @Override
    public @NonNull TextRange getRangeInElement() {
        return TextRange.from(0, myElement.getTextLength());
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        return myElement;
    }

}
