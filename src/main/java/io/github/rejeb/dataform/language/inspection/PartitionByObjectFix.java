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
package io.github.rejeb.dataform.language.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Replaces the object a {@code partitionBy} is written as by the partitioning expression it stands
 * for.
 */
public class PartitionByObjectFix implements LocalQuickFix {

    private final String expression;

    public PartitionByObjectFix(@NotNull String expression) {
        this.expression = expression;
    }

    @Override
    public @NotNull String getName() {
        return "Replace with \"" + expression + "\"";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Replace the partitioning object with an expression";
    }

    /**
     * Writes the expression over the object. The config block is edited through an injected
     * JavaScript fragment, so the replacement is made on the SQLX file hosting it.
     */
    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement object = descriptor.getPsiElement();
        if (object == null || !object.isValid()) {
            return;
        }
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(project);
        PsiFile hostFile = manager.getTopLevelFile(object.getContainingFile());
        if (hostFile == null) {
            return;
        }
        Document document = PsiDocumentManager.getInstance(project).getDocument(hostFile);
        if (document == null) {
            return;
        }
        TextRange range = manager.injectedToHost(object.getContainingFile(), object.getTextRange());
        document.replaceString(range.getStartOffset(), range.getEndOffset(),
                "\"" + expression + "\"");
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }
}
