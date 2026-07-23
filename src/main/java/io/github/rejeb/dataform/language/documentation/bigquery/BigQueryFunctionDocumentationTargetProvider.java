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
package io.github.rejeb.dataform.language.documentation.bigquery;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.SqlFunctionCallExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class BigQueryFunctionDocumentationTargetProvider implements DocumentationTargetProvider {

    @Override
    public @NotNull List<? extends DocumentationTarget> documentationTargets(@NotNull PsiFile file,
                                                                             int offset) {
        PsiFile topLevel = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
        if (topLevel == null || !topLevel.getName().endsWith(".sqlx")) {
            return List.of();
        }

        PsiElement element = elementAt(file, offset);
        if (element == null) {
            return List.of();
        }

        String candidate = element.getText();
        if (candidate == null || candidate.isBlank() || !isIdentifier(candidate)) {
            return List.of();
        }

        if (!isFunctionCall(element)) {
            return List.of();
        }

        Optional<BigQueryFunctionDoc> doc = BigQueryFunctionDocService.getInstance().find(candidate);
        return doc.<List<? extends DocumentationTarget>>map(
                        value -> List.of(new BigQueryFunctionDocumentationTarget(value)))
                .orElseGet(List::of);
    }

    @Nullable
    private static PsiElement elementAt(@NotNull PsiFile file, int offset) {
        PsiElement injected = InjectedLanguageManager.getInstance(file.getProject())
                .findInjectedElementAt(file, offset);
        return injected != null ? injected : file.findElementAt(offset);
    }

    private static boolean isFunctionCall(@NotNull PsiElement element) {
        SqlFunctionCallExpression call =
                PsiTreeUtil.getParentOfType(element, SqlFunctionCallExpression.class);
        if (call == null) {
            return false;
        }
        return call.getTextRange().getStartOffset() == element.getTextRange().getStartOffset();
    }

    private static boolean isIdentifier(@NotNull String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }
}
