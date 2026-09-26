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
package io.github.rejeb.dataform.language.diagnostics.compile;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import io.github.rejeb.dataform.language.validation.DataformEditActivityService;
import io.github.rejeb.dataform.language.validation.SqlxValidationProblem;
import io.github.rejeb.dataform.language.validation.SqlxValidationService;

import java.util.List;

public class CompilationProblemsServiceImplTest extends BasePlatformTestCase {

    private static final String INCLUDE_STACK = "ReferenceError: nope is not defined\n"
            + "    at Object.broken (/tmp/copy/includes/helpers.js:2:3)\n"
            + "    at Object.sqlContextable (/tmp/copy/definitions/uses_helper.sqlx:21:18)";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        DataformToolsSettings.getInstance().setShowInlineCompilationErrors(true);
        ServiceContainerUtil.replaceService(getProject(), DataformEditActivityService.class,
                new NotEditing(), getTestRootDisposable());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            CompiledGraphErrors.clear(getProject());
        } finally {
            super.tearDown();
        }
    }

    private CompilationProblems diagnose(PsiFile file) {
        return CompilationProblemsService.getInstance(getProject()).diagnose(file);
    }

    public void testAnUndefinedNameIsPlacedWithTheNameItMostLikelyMeant() {
        String text = "config { type: \"table\" }\n\njs {\n  const base = 1;\n  const a = bse + 1;\n}\n\nSELECT ${a} AS c\n";
        PsiFile file = myFixture.addFileToProject("definitions/typo.sqlx", text);
        CompiledGraphErrors.install(getProject(), List.of("typo"), CompiledGraphErrors.error("definitions/typo.sqlx",
                "proj.ds.typo", "bse is not defined", "ReferenceError: bse is not defined\n"
                        + "    at Object.sqlContextable (/tmp/copy/definitions/typo.sqlx:20:13)"));

        SqlxValidationProblem problem = diagnose(file).located().getFirst();

        assertEquals("bse", problem.range().substring(text));
        assertEquals("Dataform: bse is not defined", problem.message());
        assertEquals(SqlxValidationProblem.Severity.ERROR, problem.severity());
        assertEquals("Did you mean 'base'?", problem.hint());
        assertEquals("Replace with 'base'", problem.fixes().getFirst().label());
    }

    public void testAMissingRefIsReportedOnceOnItsName() {
        String text = "config { type: \"table\" }\n\nSELECT * FROM ${ref(\"ordrs\")}\n";
        PsiFile file = myFixture.addFileToProject("definitions/reader.sqlx", text);
        CompiledGraphErrors.install(getProject(), List.of("reader", "orders"),
                CompiledGraphErrors.error("definitions/reader.sqlx", null, "Could not resolve \"ordrs\"", null),
                CompiledGraphErrors.error("definitions/reader.sqlx", "proj.ds.reader", "Missing dependency detected: Action "
                        + "\"proj.ds.reader\" depends on \"{\"name\":\"ordrs\",\"includeDependentAssertions\":false}\" which does not exist", null));

        CompilationProblems problems = diagnose(file);

        assertEquals(1, problems.located().size());
        assertEquals("ordrs", problems.located().getFirst().range().substring(text));
        assertEquals("Did you mean 'orders'?", problems.located().getFirst().hint());
    }

    public void testAnErrorRaisedInAnIncludeIsShownAtTheCallAndInTheInclude() {
        PsiFile include = myFixture.addFileToProject("includes/helpers.js",
                "function broken() {\n  return nope + 1;\n}\nmodule.exports = { broken };\n");
        PsiFile file = myFixture.addFileToProject("definitions/uses_helper.sqlx",
                "config { type: \"table\" }\n\nSELECT ${helpers.broken()} AS c\n");
        CompiledGraphErrors.install(getProject(), List.of("uses_helper"), CompiledGraphErrors.error(
                "definitions/uses_helper.sqlx", "proj.ds.uses_helper", "nope is not defined", INCLUDE_STACK));

        SqlxValidationProblem atCall = diagnose(file).located().getFirst();
        SqlxValidationProblem inInclude = diagnose(include).located().getFirst();

        assertEquals("Dataform: nope is not defined (raised in includes/helpers.js:2)", atCall.message());
        assertEquals("Fix it in includes/helpers.js line 2.", atCall.hint());
        assertEquals("nope", inInclude.range().substring(include.getText()));
        assertEquals("Dataform: nope is not defined (while compiling definitions/uses_helper.sqlx)", inInclude.message());
    }

    public void testAnErrorThatCannotBePlacedStaysUnplaced() {
        PsiFile file = myFixture.addFileToProject("definitions/odd.sqlx", "config { type: \"table\" }\n\nSELECT 1 AS c\n");
        CompiledGraphErrors.install(getProject(), List.of("odd"),
                CompiledGraphErrors.error("definitions/odd.sqlx", null, "Something odd happened", null));

        assertEquals(List.of("Something odd happened"), diagnose(file).unlocated());
        assertEquals(List.of(), diagnose(file).located());
    }

    public void testAConfirmedErrorReplacesTheWeakWarningAtItsPlace() throws Exception {
        ConfigSchemaFixture.install(getProject(), getTestRootDisposable());
        String text = "config {\n  type: \"table\",\n  descripton: \"x\"\n}\n\nSELECT 1 AS c\n";
        PsiFile file = myFixture.addFileToProject("definitions/config_key.sqlx", text);
        CompiledGraphErrors.install(getProject(), List.of("config_key"), CompiledGraphErrors.error(
                "definitions/config_key.sqlx", null, "Unexpected property \"descripton\", or property value type of "
                        + "\"string\" is incorrect. See https://dataform-co.github.io/dataform/docs/configs-reference", null));

        List<SqlxValidationProblem> problems = SqlxValidationService.getInstance(getProject()).validate(file);

        assertEquals(problems.toString(), 1, problems.stream().filter(p -> p.range().substring(text).equals("descripton")).count());
        assertEquals(SqlxValidationProblem.Kind.COMPILATION_ERROR, problems.stream()
                .filter(p -> p.range().substring(text).equals("descripton")).findFirst().orElseThrow().kind());
    }

    public void testAJavaScriptDefinitionShowsItsErrorsInTheEditor() {
        String text = "const tableName = \"from_js\";\npublish(tableName, { type: \"table\" });\nnotDefinedInJs();\n";
        PsiFile file = myFixture.addFileToProject("definitions/js_definition.js", text);
        CompiledGraphErrors.install(getProject(), List.of(), CompiledGraphErrors.error("definitions/js_definition.js",
                null, "notDefinedInJs is not defined", "ReferenceError: notDefinedInJs is not defined\n"
                        + "    at /tmp/copy/definitions/js_definition.js:3:1"));
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

        List<HighlightInfo> errors = myFixture.doHighlighting(HighlightSeverity.ERROR);

        assertTrue(errors.toString(), errors.stream()
                .anyMatch(info -> info.getDescription() != null
                        && info.getDescription().startsWith("Dataform: notDefinedInJs is not defined")));
    }

    public void testANewCompilationIsSeenWithoutEditingTheFile() {
        String text = "config { type: \"table\" }\n\nSELECT ${zzz} AS c\n";
        PsiFile file = myFixture.addFileToProject("definitions/later.sqlx", text);
        assertEquals(CompilationProblems.NONE, diagnose(file));

        CompiledGraphErrors.install(getProject(), List.of("later"), CompiledGraphErrors.error("definitions/later.sqlx",
                "proj.ds.later", "zzz is not defined", "ReferenceError: zzz is not defined\n"
                        + "    at Object.sqlContextable (/tmp/copy/definitions/later.sqlx:19:13)"));

        assertEquals("zzz", diagnose(file).located().getFirst().range().substring(text));
    }

    public void testAnErrorFixedBeforeARecompileGoesAwayWithTheRecompile() throws Exception {
        PsiFile file = myFixture.addFileToProject("definitions/fixed.sqlx",
                "config { type: \"table\" }\n\njs {\n  const base = 1;\n}\n\nSELECT ${base} AS c\n");
        CompiledGraphErrors.install(getProject(), List.of("fixed"), CompiledGraphErrors.error("definitions/fixed.sqlx",
                "proj.ds.fixed", "bse is not defined", "ReferenceError: bse is not defined\n"
                        + "    at Object.sqlContextable (/tmp/copy/definitions/fixed.sqlx:20:13)"));
        assertNotSame(CompilationProblems.NONE, diagnose(file));
        BlankOutputCompiler.install(getProject(), getTestRootDisposable());

        BlankOutputCompiler.compileInBackground(getProject());

        assertSame(CompilationProblems.NONE, diagnose(file));
    }

    public void testTheJavaScriptOfADependencyShowsNoCompilationError() {
        myFixture.addFileToProject("workflow_settings.yaml", "defaultProject: proj\ndefaultDataset: ds\n");
        PsiFile bundle = myFixture.addFileToProject("node_modules/@dataform/core/bundle.js",
                "function d() { return nope; }\n");
        myFixture.addFileToProject("definitions/uses_core.sqlx", "config { type: \"table\" }\n\nSELECT 1 AS c\n");
        CompiledGraphErrors.install(getProject(), List.of("uses_core"), CompiledGraphErrors.error(
                "definitions/uses_core.sqlx", "proj.ds.uses_core", "nope is not defined",
                "ReferenceError: nope is not defined\n"
                        + "    at d.apply (/tmp/copy/node_modules/@dataform/core/bundle.js:1:23)\n"
                        + "    at Object.sqlContextable (/tmp/copy/definitions/uses_core.sqlx:21:18)"));

        assertEquals(List.of(), SqlxValidationService.getInstance(getProject()).validate(bundle));
    }

    public void testAnUndefinedNameOfTheConfigIsExplainedAsOutOfReachOfTheJsBlocks() {
        String text = "config {\n  type: \"table\",\n  schema: dataset_name\n}\n\njs {\n  const dataset_name = \"x\";\n}\n\nSELECT 1 AS c\n";
        PsiFile file = myFixture.addFileToProject("definitions/config_name.sqlx", text);
        CompiledGraphErrors.install(getProject(), List.of("config_name"), CompiledGraphErrors.error(
                "definitions/config_name.sqlx", "proj.ds.config_name", "dataset_name is not defined",
                "ReferenceError: dataset_name is not defined\n"
                        + "    at Object.<anonymous> (/tmp/copy/definitions/config_name.sqlx:3:11)"));

        SqlxValidationProblem problem = diagnose(file).located().getFirst();

        assertEquals(text.indexOf("dataset_name"), problem.range().getStartOffset());
        assertEquals("A config block cannot read what a js { } block declares: export 'dataset_name' from a file "
                + "of includes/ and read it through the name of that file.", problem.hint());
    }

    private static final class NotEditing implements DataformEditActivityService {

        @Override
        public void noteEdit() {
        }

        @Override
        public boolean isEditing() {
            return false;
        }

        @Override
        public long remainingQuietPeriodMs() {
            return 0;
        }
    }
}
