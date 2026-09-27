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
package io.github.rejeb.dataform.language.diagnostics.sql.bigquery;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;
import io.github.rejeb.dataform.language.schema.sql.DryRunQueryText;
import io.github.rejeb.dataform.language.util.MappedText;
import io.github.rejeb.dataform.language.validation.SqlxValidationProblem;
import io.github.rejeb.dataform.language.validation.SqlxValidationService;

import java.util.List;

import static io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryFailures.*;

public class BigQueryDiagnosticsServiceImplTest extends DataformProjectFixture {

    @Override
    protected void tearDown() throws Exception {
        try {
            clear(getProject());
        } finally {
            super.tearDown();
        }
    }

    private PsiFile openText(String text) {
        PsiFile file = myFixture.addFileToProject(PATH, text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    private BigQueryDiagnostics diagnose(PsiFile file) {
        return BigQueryDiagnosticsService.getInstance(getProject()).diagnose(file);
    }

    public void testAnUnrecognizedNameIsPlacedOnItsTokenWithAFix() {
        PsiFile file = openText(SQLX);
        MappedText sent = sent(getProject(), COMPILED);
        report(getProject(), "Unrecognized name: order_idd; Did you mean order_id?" + at(sent, "order_idd"), sent);

        BigQueryDiagnostics diagnostics = diagnose(file);

        assertEquals(1, diagnostics.located().size());
        SqlxValidationProblem problem = diagnostics.located().getFirst();
        assertEquals(TextRange.from(SQLX.indexOf("order_idd"), 9), problem.range());
        assertEquals("BigQuery: Unrecognized name: order_idd", problem.message());
        assertEquals(SqlxValidationProblem.Severity.ERROR, problem.severity());
        assertEquals("Did you mean 'order_id'?", problem.hint());
        assertEquals("Replace with 'order_id'", problem.fixes().getFirst().label());
        assertEquals(List.of(), diagnostics.unlocated());
    }

    public void testAnErrorInTheValueOfAHoleIsPlacedOnTheHole() {
        PsiFile file = openText(SQLX);
        MappedText sent = MappedText.identity(DryRunQueryText.MAIN_QUERY, COMPILED);
        report(getProject(), "Invalid table name: proj.ds.silver_orders" + at(sent, "`proj.ds.silver_orders`"), sent);

        assertEquals(TextRange.from(SQLX.indexOf("${ref("), "${ref(\"silver_orders\")}".length()),
                diagnose(file).located().getFirst().range());
    }

    public void testAnErrorInThePluginsOwnStubsIsNotPlacedInTheFile() {
        PsiFile file = openText(SQLX);
        MappedText sent = sent(getProject(), COMPILED);
        report(getProject(), "Syntax error: Unexpected keyword ORDER" + at(sent, "CAST(NULL"), sent);

        BigQueryDiagnostics diagnostics = diagnose(file);

        assertEquals(List.of(), diagnostics.located());
        assertEquals(List.of("BigQuery: Syntax error: Unexpected keyword ORDER"), diagnostics.unlocated());
    }

    public void testAnErrorWhoseTokenWasEditedSinceIsDropped() {
        PsiFile file = openText(SQLX.replace("order_idd", "order_id"));
        MappedText sent = sent(getProject(), COMPILED);
        report(getProject(), "Unrecognized name: order_idd" + at(sent, "order_idd"), sent);

        assertEquals(BigQueryDiagnostics.NONE, diagnose(file));
    }

    public void testWindowsLineEndsInTheCompiledQueryStillPlaceTheError() {
        PsiFile file = openText(SQLX);
        MappedText sent = sent(getProject(), COMPILED.replace("\n", "\r\n"));
        report(getProject(), "Unrecognized name: order_idd" + at(sent, "order_idd"), sent);

        assertEquals(TextRange.from(SQLX.indexOf("order_idd"), 9), diagnose(file).located().getFirst().range());
    }

    public void testATabBeforeTheTokenIsCountedAsBigQueryCountsIt() {
        PsiFile file = openText(SQLX.replace("  order_idd", "\torder_idd"));
        MappedText sent = sent(getProject(), COMPILED.replace("  order_idd", "\torder_idd"));
        String position = at(sent, "order_idd");
        String line = position.substring(position.indexOf('[') + 1, position.indexOf(':'));
        report(getProject(), "Unrecognized name: order_idd at [" + line + ":9]", sent);

        assertEquals("order_idd", diagnose(file).located().getFirst().range().substring(file.getText()));
    }

    public void testAHandWrittenTableIsPlacedWithoutPosition() {
        String sqlx = "config { type: \"table\" }\n\nSELECT 1\nFROM proj.ds.silver_orderz\n";
        PsiFile file = openText(sqlx);
        report(getProject(), "Not found: Table proj:ds.silver_orderz was not found in location US",
                sent(getProject(), "\n\nSELECT 1\nFROM proj.ds.silver_orderz\n"));

        SqlxValidationProblem problem = diagnose(file).located().getFirst();

        assertEquals(TextRange.from(sqlx.indexOf("proj.ds.silver_orderz"), 21), problem.range());
        assertEquals("Did you mean ${ref(\"silver_orders\")}?", problem.hint());
    }

    public void testAFileWithoutCompiledActionHasNoDiagnostics() {
        PsiFile file = myFixture.addFileToProject("definitions/not_compiled.sqlx", SQLX);

        assertEquals(BigQueryDiagnostics.NONE, diagnose(file));
    }

    public void testANewFailureIsSeenWithoutEditingTheFile() {
        PsiFile file = openText(SQLX);
        assertEquals(BigQueryDiagnostics.NONE, diagnose(file));
        MappedText sent = sent(getProject(), COMPILED);

        report(getProject(), "Unrecognized name: order_idd" + at(sent, "order_idd"), sent);

        assertEquals(1, diagnose(file).located().size());
    }

    public void testTheValidationServiceReportsThePlacedErrors() {
        PsiFile file = openText(SQLX);
        MappedText sent = sent(getProject(), COMPILED);
        report(getProject(), "Unrecognized name: order_idd" + at(sent, "order_idd"), sent);

        assertTrue(SqlxValidationService.getInstance(getProject()).validate(file).stream()
                .anyMatch(problem -> problem.kind() == SqlxValidationProblem.Kind.BIGQUERY_ERROR));
    }
}
