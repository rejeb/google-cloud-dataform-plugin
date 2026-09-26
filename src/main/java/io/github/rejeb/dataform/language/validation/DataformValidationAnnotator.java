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

import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xml.util.XmlStringUtil;
import io.github.rejeb.dataform.language.diagnostics.sql.fix.ApplySqlFixAction;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlFix;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Highlights Dataform validation problems at their exact source ranges, with the severity, hint
 * and fixes each one carries, once the user has stopped typing. Text being edited is reported by
 * {@link DataformEditActivityService} and left alone; the refresh that follows the quiet period
 * restarts the daemon so the problems appear then. It runs for SQLX and JavaScript files, and
 * leaves alone the JavaScript injected into a SQLX file, whose problems the SQLX file reports.
 */
public final class DataformValidationAnnotator implements Annotator, DumbAware {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiFile file)
                || InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
            return;
        }
        if (!DataformToolsSettings.getInstance().isShowInlineCompilationErrors()) {
            return;
        }
        if (DataformEditActivityService.getInstance(file.getProject()).isEditing()) {
            return;
        }
        for (SqlxValidationProblem problem :
                SqlxValidationService.getInstance(file.getProject()).validate(file)) {
            if (problem.range().getEndOffset() > file.getTextLength()) {
                continue;
            }
            boolean empty = problem.range().isEmpty();
            if (empty && problem.severity() != SqlxValidationProblem.Severity.ERROR) {
                continue;
            }
            AnnotationBuilder builder = holder.newAnnotation(severityOf(problem), problem.chipText())
                    .range(problem.range())
                    .tooltip(tooltipOf(problem));
            if (empty) {
                builder = builder.afterEndOfLine();
            }
            for (SqlFix fix : problem.fixes()) {
                builder = builder.withFix(new ApplySqlFixAction(file, fix));
            }
            builder.create();
        }
    }

    private static @NotNull HighlightSeverity severityOf(@NotNull SqlxValidationProblem problem) {
        return problem.severity() == SqlxValidationProblem.Severity.ERROR
                ? HighlightSeverity.ERROR
                : HighlightSeverity.WEAK_WARNING;
    }

    private static @NotNull String tooltipOf(@NotNull SqlxValidationProblem problem) {
        String message = XmlStringUtil.escapeString(problem.message());
        return XmlStringUtil.wrapInHtml(problem.hint() == null
                ? message
                : message + "<br>" + XmlStringUtil.escapeString(problem.hint()));
    }
}
