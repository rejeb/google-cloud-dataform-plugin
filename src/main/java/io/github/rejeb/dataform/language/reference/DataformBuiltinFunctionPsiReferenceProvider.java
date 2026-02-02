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

import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.json.psi.JsonReferenceExpression;
import com.intellij.lang.javascript.JavaScriptFileType;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.service.DataformCoreIndexService;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;

public class DataformBuiltinFunctionPsiReferenceProvider extends PsiReferenceProvider {

    @NotNull
    @Override
    public PsiReference @NonNull [] getReferencesByElement(
            @NotNull PsiElement element,
            @NotNull ProcessingContext context
    ) {

        String referencedName = getReferenceName(element).orElse(null);

        if (referencedName == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        Project project = element.getProject();

        DataformCoreIndexService service = DataformCoreIndexService.getInstance(project);

        if (service.getCachedDataformFunctionsNames().contains(referencedName) ||
                service.getCachedDataformVariablesNames().contains(referencedName)) {
            return new PsiReference[]{
                    new DataformBuiltinFunctionReference(element, referencedName)
            };
        } else {
            return PsiReference.EMPTY_ARRAY;
        }

    }

    private Optional<String> getReferenceName(PsiElement element) {
        return switch (element) {
            case JSReferenceExpression refExpr -> getJsReferenceName(refExpr);
            case JsonReferenceExpression jsonRef -> getJSONReferenceName(jsonRef);
            default -> Optional.empty();
        };
    }

    private Optional<String> getJsReferenceName(JSReferenceExpression refExpr) {
        return Optional
                .ofNullable(refExpr.getReferenceNameElement())
                .map(PsiElement::getText);
    }

    private Optional<String> getJSONReferenceName(JsonReferenceExpression refExpr) {
        JsonPsiUtil.isPropertyValue(refExpr);

        return Optional
                .of(refExpr)
                .filter(JsonPsiUtil::isPropertyValue)
                .map(JsonPsiUtil::getElementTextWithoutHostEscaping)
                .map(text -> createJSReferenceExpression(refExpr.getProject(), text))
                .flatMap(this::getJsReferenceName);
    }

    public JSReferenceExpression createJSReferenceExpression(Project project, String text) {
        String jsText = "const x = " + text + ";";
        String fileName = "dummy" + UUID.randomUUID() + ".js";

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaScriptFileType.INSTANCE, jsText);
        return PsiTreeUtil.findChildOfType(file, JSReferenceExpression.class);
    }
}
