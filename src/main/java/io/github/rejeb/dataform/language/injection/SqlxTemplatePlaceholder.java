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
package io.github.rejeb.dataform.language.injection;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import io.github.rejeb.dataform.language.evaluation.DataformTemplateSyntax;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The SQL text a template hole stands for in the query the IDE analyses.
 *
 * <p>A hole is what Dataform evaluates it to, when that value is known: {@code ${ref()}} and
 * {@code ${self()}} through the compiled graph, and any other expression through the value Node
 * computed for it, which the evaluation service keeps per file. What is not known yet is filler,
 * chosen so the query around it still parses: nothing at all when the hole opens the query, where
 * an expression would break the statement, and {@code NULL} anywhere else, where an expression is
 * what the grammar expects.</p>
 */
final class SqlxTemplatePlaceholder {

    private static final String EXPRESSION_FILLER = "NULL";
    private static final String STATEMENT_FILLER = "";

    private SqlxTemplatePlaceholder() {
    }

    /**
     * The text to inject for a hole.
     *
     * @param hole            the template expression, {@code ${...}} included
     * @param file            the SQLX file the hole is written in, {@code null} when it has none
     * @param currentFileName the name of that file without extension, for {@code self()}
     * @param opensTheQuery   whether only whitespace precedes the hole in its SQL block
     */
    static @NotNull String of(@NotNull PsiElement hole,
                              @Nullable VirtualFile file,
                              @Nullable String currentFileName,
                              boolean opensTheQuery) {
        String resolved = SqlxRefSelfResolver.resolveToSqlIdentifier(hole, currentFileName);
        if (resolved != null) return resolved;
        String evaluated = evaluatedValue(hole, file);
        if (evaluated != null) return evaluated;
        return opensTheQuery ? STATEMENT_FILLER : EXPRESSION_FILLER;
    }

    private static @Nullable String evaluatedValue(@NotNull PsiElement hole,
                                                   @Nullable VirtualFile file) {
        if (file == null) return null;
        String text = hole.getText();
        if (text == null) return null;
        return DataformExpressionEvaluationService.getInstance(hole.getProject())
                .getCachedValue(file, DataformTemplateSyntax.sourceOf(text));
    }
}
