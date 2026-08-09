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
package io.github.rejeb.dataform.language.documentation;

import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DataformRefDocumentationTargetProvider implements DocumentationTargetProvider {

    @Override
    public @NotNull List<? extends DocumentationTarget> documentationTargets(@NotNull PsiFile file,
                                                                             int offset) {
        PsiFile topLevel = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
        if (topLevel == null || !DataformProjectLayout.isDataformSourceName(topLevel.getName(),
                FileUtilRt.getExtension(topLevel.getName()))) {
            return List.of();
        }

        JSLiteralExpression literal = findRefLiteral(file, offset);
        if (literal == null && file != topLevel) {
            literal = findRefLiteral(topLevel, offset);
        }
        if (literal == null) {
            return List.of();
        }

        Object value = literal.getValue();
        if (value == null) {
            return List.of();
        }
        String tableName = String.valueOf(value);
        if (tableName.isBlank()) {
            return List.of();
        }

        return List.of(new DataformTableDocumentationTarget(file.getProject(), tableName));
    }

    @Nullable
    private static JSLiteralExpression findRefLiteral(@NotNull PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        if (element == null) {
            return null;
        }

        PsiElement injected = InjectedLanguageManager.getInstance(file.getProject())
                .findInjectedElementAt(file, offset);
        if (injected != null) {
            JSLiteralExpression fromInjection = matchRefLiteral(injected);
            if (fromInjection != null) {
                return fromInjection;
            }
        }

        return matchRefLiteral(element);
    }

    @Nullable
    private static JSLiteralExpression matchRefLiteral(@NotNull PsiElement element) {
        JSLiteralExpression literal = PsiTreeUtil.getParentOfType(element, JSLiteralExpression.class, false);
        if (literal == null || !literal.isQuotedLiteral()) {
            return null;
        }

        JSCallExpression call = PsiTreeUtil.getParentOfType(literal, JSCallExpression.class);
        if (call == null) {
            return null;
        }

        JSExpression methodExpression = call.getMethodExpression();
        if (!(methodExpression instanceof JSReferenceExpression reference)) {
            return null;
        }

        String functionName = reference.getReferenceName();
        if (!"ref".equals(functionName) && !"resolve".equals(functionName)) {
            return null;
        }

        return literal;
    }
}
