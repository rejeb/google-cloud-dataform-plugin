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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.inspections.SqlResolveInspection;
import com.intellij.sql.psi.SqlFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationServiceImpl;
import io.github.rejeb.dataform.language.injection.SqlxInjectionRefresher;

import java.util.ArrayList;
import java.util.List;

public class SqlxSqlProblemAnnotatorTest extends BasePlatformTestCase {

    private List<HighlightInfo> highlight(String fileName, String sqlx) {
        myFixture.enableInspections(new SqlResolveInspection());
        myFixture.configureByText(fileName, sqlx);
        return myFixture.doHighlighting();
    }

    private List<String> descriptionsOf(List<HighlightInfo> infos, HighlightSeverity severity) {
        List<String> descriptions = new ArrayList<>();
        for (HighlightInfo info : infos) {
            if (info.getSeverity() == severity && info.getDescription() != null) {
                descriptions.add(info.getDescription());
            }
        }
        return descriptions;
    }

    private List<String> errors(List<HighlightInfo> infos) {
        List<String> descriptions = new ArrayList<>();
        for (HighlightInfo info : infos) {
            if (info.getSeverity().compareTo(HighlightSeverity.ERROR) >= 0) {
                descriptions.add(info.getSeverity() + ": " + info.getDescription());
            }
        }
        return descriptions;
    }

    public void testTemplateExpressionExpandingToAClauseIsNotAnError() {
        String sqlx = "config { type: \"table\" }\n"
                + "SELECT 1\n"
                + "${when(incremental(), \"WHERE ts > 1\", \"\")}\n";

        List<HighlightInfo> infos = highlight("when_clause.sqlx", sqlx);

        assertEquals("injected SQL syntax must not be reported as an error",
                List.of(), errors(infos));
        assertFalse("the parser message must still be reported as a weak warning",
                descriptionsOf(infos, HighlightSeverity.WEAK_WARNING).isEmpty());
    }

    public void testUnresolvedTableIsAWeakWarning() {
        String sqlx = "config { type: \"table\" }\nSELECT 1 FROM unknown_table\n";

        List<HighlightInfo> infos = highlight("unknown_table.sqlx", sqlx);

        assertEquals("an unresolved table must not be reported as an error",
                List.of(), errors(infos));
        assertEquals("the unresolved table must be reported exactly once, as a weak warning",
                List.of("Unable to resolve table 'unknown_table'"),
                descriptionsOf(infos, HighlightSeverity.WEAK_WARNING));
    }

    public void testUnresolvedColumnStaysWithTheSqlInspection() {
        String sqlx = "config { type: \"table\" }\nSELECT unknown_col FROM unknown_table\n";

        List<HighlightInfo> infos = highlight("unknown_column.sqlx", sqlx);
        List<String> weakWarnings = descriptionsOf(infos, HighlightSeverity.WEAK_WARNING);

        assertEquals("an unresolved column must be reported exactly once", 1,
                weakWarnings.stream()
                        .filter("Unable to resolve column 'unknown_col'"::equals)
                        .count());
        assertEquals("an unresolved column must not be reported as an error",
                List.of(), errors(infos));
    }

    public void testJavaScriptBlockKeepsItsErrors() {
        String sqlx = "config { type: \"table\" }\njs {\n  const x = ;\n}\nSELECT 1\n";

        List<HighlightInfo> infos = highlight("bad_js.sqlx", sqlx);

        assertFalse("a JavaScript block must keep its red syntax errors", errors(infos).isEmpty());
    }

    public void testPlainSqlFileKeepsItsErrors() {
        myFixture.configureByText("plain.sql", "SELECT 1 FROM\n");
        PsiErrorElement error =
                PsiTreeUtil.findChildOfType(myFixture.getFile(), PsiErrorElement.class);

        assertNotNull("the probe needs a parse error to assert on", error);
        assertTrue("a plain SQL file must keep its red syntax errors",
                new SqlxSyntaxErrorFilter().shouldHighlightErrorElement(error));
    }

    public void testSqlxFileHandsItsSyntaxErrorsToTheAnnotator() {
        myFixture.configureByText("errors.sqlx",
                "config { type: \"table\" }\nSELECT 1\n${when(true, \"WHERE a > 1\", \"\")}\n");
        List<PsiErrorElement> errors = new ArrayList<>();
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
        myFixture.getFile().accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                manager.enumerate(element, (injected, places) -> {
                    if (injected instanceof SqlFile) {
                        errors.addAll(PsiTreeUtil.findChildrenOfType(injected, PsiErrorElement.class));
                    }
                });
                super.visitElement(element);
            }
        });

        assertFalse("the probe needs a parse error to assert on", errors.isEmpty());
        assertFalse("SQLX parse errors must not be painted red",
                new SqlxSyntaxErrorFilter().shouldHighlightErrorElement(errors.get(0)));
    }
    /**
     * A {@code ${ref()}} hole is injected as the full BigQuery name of the table, project first.
     * Nothing of that name was written by the user, so nothing of it is worth a report — least of
     * all the project, which no schema of the plugin holds as an object.
     */
    public void testTheProjectOfAResolvedRefIsNotReported() {
        myFixture.enableInspections(new SqlResolveInspection());
        PsiFile file = myFixture.addFileToProject("definitions/reader.sqlx",
                "config { type: \"table\" }\nSELECT 1 FROM ${ref(\"orders\")}\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        ((DataformExpressionEvaluationServiceImpl) DataformExpressionEvaluationService
                .getInstance(getProject())).putCachedValue(file.getVirtualFile(),
                "ref(\"orders\")", "`test-dataform-489602`.dataform_ds.orders");
        SqlxInjectionRefresher.refresh(getProject(), file.getVirtualFile());

        List<HighlightInfo> infos = myFixture.doHighlighting();
        List<String> reported = new ArrayList<>();
        for (HighlightInfo info : infos) {
            String description = info.getDescription();
            if (description != null && description.contains("test-dataform-489602")) {
                reported.add(info.getSeverity() + ": " + description);
            }
        }
        assertEquals("the project of a name the injection wrote is never reported",
                List.of(), reported);
        assertEquals("nothing of a SQLX file is an error", List.of(), errors(infos));
    }
}
