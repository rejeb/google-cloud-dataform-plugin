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
package io.github.rejeb.dataform.language.folding;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import io.github.rejeb.dataform.language.SqlxFileType;
import io.github.rejeb.dataform.language.evaluation.DataformExpression;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionCollector;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects the expressions of a file whose value is painted over several lines.
 *
 * <p>Must be called inside a read action.</p>
 */
public final class DataformMultilineValues {

    private DataformMultilineValues() {
    }

    /**
     * Returns the multi-line values of the given file, in document order.
     */
    @NotNull
    public static List<DataformMultilineFoldManager.MultilineValue> of(@NotNull Project project,
                                                                       @NotNull VirtualFile file,
                                                                       @NotNull Document document) {
        if (!DataformToolsSettings.getInstance().isFoldTemplateExpressions()) {
            return List.of();
        }
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile == null) {
            return List.of();
        }

        DataformExpressionEvaluationService service = DataformExpressionEvaluationService.getInstance(project);
        List<DataformMultilineFoldManager.MultilineValue> values = new ArrayList<>();
        for (DataformExpression expression : expressionsOf(psiFile, file, service)) {
            String value = service.getCachedValue(file, expression.source());
            if (value == null || !DataformMultilineFoldPolicy.qualifies(document, expression.hostRange(), value)) {
                continue;
            }
            values.add(new DataformMultilineFoldManager.MultilineValue(
                    expression.source(),
                    DataformFoldingPlaceholder.expanded(value).lines().toList(),
                    DataformMultilineFoldPolicy.startLine(document, expression.hostRange()),
                    DataformMultilineFoldPolicy.endLine(document, expression.hostRange()),
                    DataformMultilineFoldPolicy.prefixOf(document, expression.hostRange()),
                    DataformMultilineFoldPolicy.suffixOf(document, expression.hostRange())));
        }
        return values;
    }

    @NotNull
    private static List<DataformExpression> expressionsOf(@NotNull PsiFile psiFile,
                                                          @NotNull VirtualFile file,
                                                          @NotNull DataformExpressionEvaluationService service) {
        List<DataformExpression> expressions = new ArrayList<>();
        if (SqlxFileType.INSTANCE.equals(file.getFileType())) {
            expressions.addAll(DataformExpressionCollector.collectSqlxTemplates(psiFile));
        } else {
            expressions.addAll(DataformExpressionCollector.collectJsTemplateSubstitutions(psiFile));
        }
        expressions.addAll(DataformInjectedExpressions.withHostRanges(psiFile, service.includeNames(file)));
        return expressions;
    }
}
