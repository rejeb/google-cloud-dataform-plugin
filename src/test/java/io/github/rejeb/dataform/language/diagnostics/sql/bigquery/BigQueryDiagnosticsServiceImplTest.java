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
import io.github.rejeb.dataform.language.columns.impact.ColumnChange;
import io.github.rejeb.dataform.language.columns.impact.ColumnImpact;
import io.github.rejeb.dataform.language.columns.impact.ColumnImpactService;
import io.github.rejeb.dataform.language.columns.impact.ColumnImpactServiceImpl;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledOperation;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.diagnostics.PlacedProblems;
import io.github.rejeb.dataform.language.schema.sql.DryRunErrorRegistry;
import io.github.rejeb.dataform.language.schema.sql.DryRunFailure;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;
import io.github.rejeb.dataform.language.schema.sql.DryRunQueryText;
import io.github.rejeb.dataform.language.util.MappedText;
import io.github.rejeb.dataform.language.validation.SqlxValidationProblem;
import io.github.rejeb.dataform.language.validation.SqlxValidationService;

import java.lang.reflect.Field;
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

    private PlacedProblems diagnose(PsiFile file) {
        return BigQueryDiagnosticsService.getInstance(getProject()).diagnose(file);
    }

    public void testAnUnrecognizedNameIsPlacedOnItsTokenWithAFix() {
        PsiFile file = openText(SQLX);
        MappedText sent = sent(getProject(), COMPILED);
        report(getProject(), "Unrecognized name: order_idd; Did you mean order_id?" + at(sent, "order_idd"), sent);

        PlacedProblems diagnostics = diagnose(file);

        assertEquals(1, diagnostics.located().size());
        SqlxValidationProblem problem = diagnostics.located().getFirst();
        assertEquals(TextRange.from(SQLX.indexOf("order_idd"), 9), problem.range());
        assertEquals("BigQuery: Unrecognized name: order_idd", problem.message());
        assertEquals(SqlxValidationProblem.Severity.ERROR, problem.severity());
        assertEquals("Did you mean 'order_id'?", problem.hint());
        assertEquals("Replace with 'order_id'", problem.fixes().getFirst().label());
        assertEquals(List.of(), diagnostics.unlocated());
    }

    public void testAFailureColumnChangeExplainsNamesTheChange() {
        PsiFile file = openText(SQLX);
        MappedText sent = sent(getProject(), COMPILED);
        String message = "Unrecognized name: order_idd" + at(sent, "order_idd");
        report(getProject(), message, sent);
        ColumnChange change = new ColumnChange("proj.ds.silver_orders", ColumnChange.Kind.REMOVED,
                "order_idd", null, "INT64", null);
        ((ColumnImpactServiceImpl) ColumnImpactService.getInstance(getProject())).publish(List.of(
                new ColumnImpact(change, List.of(new DryRunBuildProblem(ACTION, null, 0, 0,
                        "Unrecognized name: order_idd", message, "order_idd")))));

        assertEquals("BigQuery: Unrecognized name: order_idd — Column `order_idd` no longer exists in `silver_orders`",
                diagnose(file).located().getFirst().message());
    }

    public void testAFailedOperationIsPlacedInItsFile() throws Exception {
        String operationPath = "definitions/silver_operation.sqlx";
        String operationSqlx = "config { type: \"operations\", hasOutput: true }\n\nSELECT\n  order_idd\n"
                + "FROM ${ref(\"silver_orders\")}\n";
        String compiled = "\n\nSELECT\n  order_idd\nFROM `proj.ds.silver_orders`\n";
        CompiledGraph graph = DataformCompilationService.getInstance(getProject()).getCompiledGraph();
        List<CompiledOperation> previous = graph.getOperations();
        setField(graph, "operations", List.of(operation("silver_operation", operationPath, compiled)));
        setField(graph, "fileNameIndex", null);
        try {
            PsiFile file = openPath(operationPath, operationSqlx);
            MappedText sent = sent(getProject(), compiled);
            DryRunErrorRegistry.getInstance(getProject()).reportFailure("proj.ds.silver_operation",
                    new DryRunFailure("Unrecognized name: order_idd" + at(sent, "order_idd"), sent));

            PlacedProblems diagnostics = diagnose(file);

            assertEquals(1, diagnostics.located().size());
            assertEquals(TextRange.from(operationSqlx.indexOf("order_idd"), 9), diagnostics.located().getFirst().range());
        } finally {
            DryRunErrorRegistry.getInstance(getProject()).clear("proj.ds.silver_operation");
            setField(graph, "operations", previous);
            setField(graph, "fileNameIndex", null);
        }
    }

    private PsiFile openPath(String path, String text) {
        PsiFile file = myFixture.addFileToProject(path, text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    private static CompiledOperation operation(String name, String fileName, String query) throws Exception {
        Target target = new Target();
        setField(target, "database", "proj");
        setField(target, "schema", "ds");
        setField(target, "name", name);
        CompiledOperation operation = new CompiledOperation();
        setField(operation, "target", target);
        setField(operation, "fileName", fileName);
        setField(operation, "queries", List.of(query));
        setField(operation, "hasOutput", true);
        return operation;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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

        PlacedProblems diagnostics = diagnose(file);

        assertEquals(List.of(), diagnostics.located());
        assertEquals(List.of("BigQuery: Syntax error: Unexpected keyword ORDER"), diagnostics.unlocated());
    }

    public void testAnErrorWhoseTokenWasEditedSinceIsDropped() {
        PsiFile file = openText(SQLX.replace("order_idd", "order_id"));
        MappedText sent = sent(getProject(), COMPILED);
        report(getProject(), "Unrecognized name: order_idd" + at(sent, "order_idd"), sent);

        assertEquals(PlacedProblems.NONE, diagnose(file));
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

        assertEquals(PlacedProblems.NONE, diagnose(file));
    }

    public void testANewFailureIsSeenWithoutEditingTheFile() {
        PsiFile file = openText(SQLX);
        assertEquals(PlacedProblems.NONE, diagnose(file));
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
