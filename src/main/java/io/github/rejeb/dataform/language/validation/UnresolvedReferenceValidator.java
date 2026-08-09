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
package io.github.rejeb.dataform.language.validation;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.psi.SqlxPsiElement;
import io.github.rejeb.dataform.language.reference.DataformJsReference;
import io.github.rejeb.dataform.language.reference.DataformRefFunctionReference;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Reports Dataform references that do not resolve to any known target.
 */
public final class UnresolvedReferenceValidator implements SqlxValidator {

    @Override
    public @NotNull List<SqlxValidationProblem> validate(@NotNull PsiFile file) {
        if (!(file instanceof SqlxFile)) {
            return List.of();
        }
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
        List<SqlxValidationProblem> problems = new ArrayList<>();

        Collection<SqlxPsiElement> hosts =
                PsiTreeUtil.findChildrenOfType(file, SqlxPsiElement.class);
        for (SqlxPsiElement host : hosts) {
            manager.enumerate(host, (injectedPsi, places) ->
                    collectFrom(injectedPsi, manager, problems));
        }
        return List.copyOf(problems);
    }

    private static void collectFrom(@NotNull PsiFile injectedPsi,
                                    @NotNull InjectedLanguageManager manager,
                                    @NotNull List<SqlxValidationProblem> problems) {
        injectedPsi.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                for (PsiReference reference : element.getReferences()) {
                    if (!isDataformReference(reference) || reference.resolve() != null) {
                        continue;
                    }
                    TextRange inInjected = reference.getRangeInElement()
                            .shiftRight(element.getTextRange().getStartOffset());
                    TextRange inHost = manager.injectedToHost(injectedPsi, inInjected);
                    problems.add(new SqlxValidationProblem(
                            inHost,
                            message(reference),
                            SqlxValidationProblem.Kind.UNRESOLVED_REFERENCE));
                }
                super.visitElement(element);
            }
        });
    }

    private static boolean isDataformReference(@NotNull PsiReference reference) {
        return reference instanceof DataformRefFunctionReference
                || reference instanceof DataformJsReference;
    }

    private static String message(@NotNull PsiReference reference) {
        String text = reference.getCanonicalText();
        return reference instanceof DataformRefFunctionReference
                ? "Cannot resolve Dataform table \"" + text + "\""
                : "Cannot resolve Dataform symbol \"" + text + "\"";
    }
}
