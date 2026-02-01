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

import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSVariable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import io.github.rejeb.dataform.language.service.DataformCoreIndexService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

public class DataformBuiltinFunctionReference extends PsiReferenceBase<PsiElement> {

    private final String functionName;

    public DataformBuiltinFunctionReference(@NotNull PsiElement element,
                                            @NotNull String functionName) {
        super(element, new TextRange(0, element.getTextLength()));
        this.functionName = functionName;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        Project project = myElement.getProject();
        return findFunction(project);
    }

    @Nullable
    private PsiElement findFunction(Project project) {
        DataformCoreIndexService service = DataformCoreIndexService.getInstance(project);
        Collection<JSFunction> functions = service.getCachedDataformFunctionsRef();
        for (JSFunction function : functions) {
            String name = function.getName();
            if (functionName.equals(name)) {
                return function;
            }
        }

        Collection<JSVariable> variables = service.getCachedDataformVariablesRef();
        for (JSVariable variable : variables) {
            String name = variable.getName();
            if (functionName.equals(name)) {
                return variable;
            }
        }

        PsiFile psiFile = service.getPsiFile();
        String text = psiFile.getText();
        int functionIndex = text.indexOf(functionName);
        if (functionIndex >= 0) {
            int exportIndex = text.lastIndexOf("export", functionIndex);
            if (exportIndex >= 0 && exportIndex < functionIndex) {
                return psiFile;
            }
        }

        return null;
    }

    @Override
    public Object @NonNull [] getVariants() {
        return new Object[0];
    }
}
