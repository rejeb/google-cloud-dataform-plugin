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

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.psi.PsiErrorElement;
import com.intellij.sql.psi.SqlLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * Stops the platform from painting the SQL parse errors of a SQLX file red. The SQL injected into a
 * SQLX file is assembled from fragments and placeholders, so a Dataform expression that expands to
 * a whole clause parses as a syntax error even though the compiled query is valid.
 * {@link SqlxSqlProblemAnnotator} reports the same errors again as weak warnings. JavaScript blocks
 * are left alone: their content is written as-is by the user, so their parse errors are real.
 */
public final class SqlxSyntaxErrorFilter extends HighlightErrorFilter {

    @Override
    public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
        return !element.getLanguage().isKindOf(SqlLanguage.INSTANCE)
                || !SqlxHighlightScope.isInSqlxFile(element);
    }
}
