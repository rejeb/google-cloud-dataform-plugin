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

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Highlights Dataform validation problems at their exact source ranges, once the user has stopped
 * typing. Text being edited is reported by {@link DataformEditActivityService} and left alone; the
 * refresh that follows the quiet period restarts the daemon so the problems appear then.
 */
public final class DataformValidationAnnotator implements Annotator, DumbAware {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiFile file)) {
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
            if (problem.range().isEmpty()
                    || problem.range().getEndOffset() > file.getTextLength()) {
                continue;
            }
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING, problem.message())
                    .range(problem.range())
                    .create();
        }
    }
}
