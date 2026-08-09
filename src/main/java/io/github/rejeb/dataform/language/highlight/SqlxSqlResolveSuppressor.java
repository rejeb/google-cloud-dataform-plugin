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
 */
public final class SqlxSqlResolveSuppressor implements InspectionSuppressor {

    private static final Set<String> SQL_RESOLVE_TOOL_IDS =
            Set.of("SqlResolve", "SqlResolveInspection");

    @Override
    public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
        return SQL_RESOLVE_TOOL_IDS.contains(toolId)
                && SqlxSqlProblemAnnotator.isCoveredReference(element)
                && SqlxHighlightScope.isInSqlxFile(element);
    }

    @Override
    public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element,
                                                           @NotNull String toolId) {
        return SuppressQuickFix.EMPTY_ARRAY;
    }
}
