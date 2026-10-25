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
package io.github.rejeb.dataform.language.columns.usages;

import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.columns.origin.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The reads of a column written as a JavaScript string in the actions reading its table.
 *
 * <p>An action builds part of its SQL in JavaScript: {@code ${helper("assistsProvided")}} hands the
 * column name to an include, which expands it into a {@code STRUCT} or an aggregate the compiled
 * query reads. The SQL the IDE analyses never shows that read — a template hole is injected as
 * filler text — so the reference search cannot find it. The name is found as a string instead, in
 * the {@code js} blocks and the template expressions of the SQLX files whose action reads the
 * table declaring the column, and in those declaring it.</p>
 *
 * <p>Only a string that is exactly the column name is reported. A string merely containing it is
 * SQL text a person has to read, and the window is not the place to show a guess.</p>
 */
final class JsStringColumnUsageSearch implements ColumnUsageSearch {

    private final Project project;
    private final ColumnWindowTarget target;

    JsStringColumnUsageSearch(@NotNull Project project, @NotNull ColumnWindowTarget target) {
        this.project = project;
        this.target = target;
    }

    @Override
    public void forEachRead(@NotNull Processor<PsiElement> reads, int maxReads) {
        String name = target.name();
        for (PsiFile file : ColumnOccurrences.filesDeclaringOrReading(project, tablesOfTheColumn())) {
            for (PsiFile javaScript : InjectedFiles.javaScriptOf(file)) {
                for (JSLiteralExpression literal :
                        PsiTreeUtil.findChildrenOfType(javaScript, JSLiteralExpression.class)) {
                    if (!literal.isQuotedLiteral() || !name.equals(literal.getStringValue())) continue;
                    if (!reads.process(literal)) return;
                }
            }
        }
    }

    private @NotNull Set<String> tablesOfTheColumn() {
        ColumnOriginService origins = ColumnOriginService.getInstance(project);
        Set<String> tables = new LinkedHashSet<>();
        for (PsiElement searched : target.searchTargets()) {
            if (!(searched instanceof DataformDasColumn column)) continue;
            ColumnRef reference = origins.reference(column);
            if (reference != null) tables.add(reference.tableFullName());
        }
        return tables;
    }
}
