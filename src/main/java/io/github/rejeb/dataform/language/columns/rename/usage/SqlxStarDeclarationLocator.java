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
package io.github.rejeb.dataform.language.columns.rename.usage;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;
import io.github.rejeb.dataform.language.columns.origin.SqlxOutputColumnLocator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the aliases that write the name of a column a query produces with a star.
 *
 * <p>A query reading a literal {@code UNNEST([STRUCT(1 AS customer_id, …)])} and selecting a star
 * names its columns in that struct. Those aliases are the declaration of the column, so a rename
 * has somewhere exact to write and the user has nothing to decide.</p>
 */
public final class SqlxStarDeclarationLocator {

    private SqlxStarDeclarationLocator() {
    }

    /**
     * The identifiers naming {@code columnName} outside the select list of the main query, which is
     * where a starred query takes the name from. Empty when the file names it nowhere: an alias
     * standing in the value a template hole evaluated to is not written in the file, so it is not
     * counted.
     */
    public static @NotNull List<PsiElement> findStructAliases(@NotNull PsiFile hostFile,
                                                              @NotNull String columnName) {
        List<PsiElement> found = new ArrayList<>();
        for (PsiFile injected : InjectedFiles.mainSql(hostFile)) {
            PsiElement selectClause = SqlxOutputColumnLocator.mainSelectClauseOf(injected);
            for (PsiElement expression : PsiTreeUtil.collectElements(injected,
                    element -> SqlPsiParts.isType(element,
                            SqlCompositeElementTypes.SQL_AS_EXPRESSION))) {
                if (selectClause != null && PsiTreeUtil.isAncestor(selectClause, expression, false)) {
                    continue;
                }
                PsiElement identifier = SqlPsiParts.lastIdentifier(expression);
                if (identifier != null
                        && SqlPsiParts.unquoted(identifier.getText()).equalsIgnoreCase(columnName)
                        && HostRanges.hostRangeOf(identifier, TextRange.from(0, identifier.getTextLength())) != null) {
                    found.add(identifier);
                }
            }
        }
        return found;
    }
}
