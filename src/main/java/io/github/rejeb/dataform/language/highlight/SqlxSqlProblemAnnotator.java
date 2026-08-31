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

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import com.intellij.sql.psi.SqlReferenceElementType;
import com.intellij.sql.psi.SqlReferenceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reports the SQL problems of a SQLX file as weak warnings. Everything the SQL support flags as an
 * error inside a SQLX file is a guess made on a query that is not the one Dataform compiles, so it
 * is reported without the red highlighting that suggests broken code.
 */
public final class SqlxSqlProblemAnnotator implements Annotator, DumbAware {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!SqlxHighlightScope.isInSqlxFile(element)) {
            return;
        }
        if (element instanceof PsiErrorElement error) {
            annotateSyntaxError(error, holder);
            return;
        }
        if (element instanceof SqlReferenceExpression reference) {
            annotateUnresolvedReference(reference, holder);
        }
    }

    /**
     * Tells whether unresolved-reference reporting for the element is taken over by this annotator.
     * Only table references are covered: the SQL resolve inspection reports those with a problem
     * type whose severity is always an error, while it already reports columns as weak warnings.
     * The inspection reports on the identifier rather than on the reference expression, so the
     * enclosing reference is looked at as well.
     */
    static boolean isCoveredReference(@Nullable PsiElement element) {
        return isTableReference(element)
                || (element != null && isTableReference(element.getParent()));
    }

    private static boolean isTableReference(@Nullable PsiElement element) {
        if (!(element instanceof SqlReferenceExpression reference)) {
            return false;
        }
        SqlReferenceElementType type = reference.getReferenceElementType();
        return type == SqlCompositeElementTypes.SQL_TABLE_REFERENCE;
    }

    private static void annotateSyntaxError(@NotNull PsiErrorElement error,
                                            @NotNull AnnotationHolder holder) {
        String description = error.getErrorDescription();
        if (description.isBlank()) {
            return;
        }
        if (error.getTextRange().isEmpty()) {
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING, description)
                    .range(error)
                    .afterEndOfLine()
                    .create();
            return;
        }
        holder.newAnnotation(HighlightSeverity.WEAK_WARNING, description)
                .range(error)
                .create();
    }

    private static void annotateUnresolvedReference(@NotNull SqlReferenceExpression reference,
                                                    @NotNull AnnotationHolder holder) {
        if (!isTableReference(reference) || SqlxQuerySources.isGenerated(reference)
                || reference.resolve() != null) {
            return;
        }
        String name = reference.getName();
        if (name == null || name.isBlank()) {
            return;
        }
        PsiElement identifier = reference.getIdentifier();
        holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
                        "Unable to resolve table '" + name + "'")
                .range(identifier != null ? identifier : reference)
                .create();
    }

}
