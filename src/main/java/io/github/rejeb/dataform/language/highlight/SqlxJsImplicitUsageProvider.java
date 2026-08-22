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
package io.github.rejeb.dataform.language.highlight;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSNamedElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.psi.SqlxJsBlock;
import io.github.rejeb.dataform.language.psi.SqlxJsLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Tells the JavaScript unused symbol inspection that a symbol declared in a SQLX {@code js} block is
 * used when a {@code ${...}} template expression of the same file references it. The {@code js} block
 * and each template expression are injected as separate JavaScript files, so the inspection cannot see
 * the usage on its own and grays out the declaration.
 */
public final class SqlxJsImplicitUsageProvider implements ImplicitUsageProvider {

    @Override
    public boolean isImplicitUsage(@NotNull PsiElement element) {
        if (!(element instanceof JSNamedElement namedElement)) {
            return false;
        }

        String name = namedElement.getName();
        if (name == null || name.isEmpty()) {
            return false;
        }

        InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(element.getProject());
        if (!(injectedLanguageManager.getInjectionHost(element) instanceof SqlxJsBlock)) {
            return false;
        }

        PsiFile topLevelFile = injectedLanguageManager.getTopLevelFile(element);
        if (!(topLevelFile instanceof SqlxFile)) {
            return false;
        }

        return isReferencedInTemplateExpression(topLevelFile, name);
    }

    @Override
    public boolean isImplicitRead(@NotNull PsiElement element) {
        return false;
    }

    @Override
    public boolean isImplicitWrite(@NotNull PsiElement element) {
        return false;
    }

    private static boolean isReferencedInTemplateExpression(@NotNull PsiFile file, @NotNull String name) {
        Collection<SqlxJsLiteralExpression> expressions =
                PsiTreeUtil.findChildrenOfType(file, SqlxJsLiteralExpression.class);

        for (SqlxJsLiteralExpression expression : expressions) {
            if (containsIdentifier(expression.getText(), name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIdentifier(@NotNull String text, @NotNull String name) {
        int index = text.indexOf(name);
        while (index >= 0) {
            boolean startsAtBoundary = index == 0
                    || !Character.isJavaIdentifierPart(text.charAt(index - 1));
            int end = index + name.length();
            boolean endsAtBoundary = end == text.length()
                    || !Character.isJavaIdentifierPart(text.charAt(end));

            if (startsAtBoundary && endsAtBoundary) {
                return true;
            }
            index = text.indexOf(name, index + 1);
        }
        return false;
    }
}
