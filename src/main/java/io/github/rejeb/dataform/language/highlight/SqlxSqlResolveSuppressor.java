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

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Hands the unresolved table references of a SQLX file over to {@link SqlxSqlProblemAnnotator}. The
 * SQL resolve inspection reports an unresolved table with a problem type whose severity is always
 * an error, so no inspection profile can lower it; suppressing it here and reporting it again is
 * the only way to keep the message without the red highlighting. Column references are left to the
 * inspection, which already reports them as weak warnings.
 *
 * <p>A column read from a source the IDE cannot name the columns of is dropped rather than handed
 * over: there is nothing to say about it. The query reads rows a template hole builds, and the
 * inspection is answering from the filler text the injection put there — which it reports as an
 * error, since as far as it can tell the source is a perfectly ordinary one.</p>
 *
 * <p>A name inside text the injection wrote is dropped as well. A {@code ${ref()}} hole becomes the
 * full BigQuery name of its table, and the project and dataset in front of it are objects no
 * schema of the plugin holds, so the inspection reports the project as an unresolvable symbol —
 * an error on a name the user never typed.</p>
 */
public final class SqlxSqlResolveSuppressor implements InspectionSuppressor {

    private static final Set<String> SQL_RESOLVE_TOOL_IDS =
            Set.of("SqlResolve", "SqlResolveInspection");

    @Override
    public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
        if (!SQL_RESOLVE_TOOL_IDS.contains(toolId) || !SqlxHighlightScope.isInSqlxFile(element)) {
            return false;
        }
        return SqlxSqlProblemAnnotator.isCoveredReference(element)
                || SqlxQuerySources.isGenerated(element)
                || !SqlxQuerySources.areKnown(element);
    }

    @Override
    public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element,
                                                           @NotNull String toolId) {
        return SuppressQuickFix.EMPTY_ARRAY;
    }
}
