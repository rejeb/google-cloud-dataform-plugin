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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class CompilationErrorPlacerTest extends BasePlatformTestCase {

    private static final String ROOT = "/tmp/copy/";

    private String placed(String path, String text, String message, String stack) {
        PsiFile file = myFixture.addFileToProject(path, text);
        ParsedCompilationError error = CompilationErrorParser.parse(message, stack, path);
        TextRange range = CompilationErrorPlacer.place(file, file.getVirtualFile().getPath(), error);
        return range == null ? null : range.substring(file.getText());
    }

    private static String frame(String function, String path, int line, int column) {
        return "    at " + function + " (" + ROOT + path + ":" + line + ":" + column + ")";
    }

    public void testAnUndefinedNameOfAJsBlockIsPlacedOnIt() {
        assertEquals("undefinedVariable", placed("definitions/js_ref_error.sqlx",
                "config { type: \"table\" }\n\njs {\n  const base = 1;\n  const a = undefinedVariable + base;\n}\n\nSELECT ${a} AS c\n",
                "undefinedVariable is not defined",
                "ReferenceError: undefinedVariable is not defined\n" + frame("Object.sqlContextable", "definitions/js_ref_error.sqlx", 20, 13)));
    }

    public void testAnUndefinedFunctionOfAHoleIsPlacedOnItThoughTheFrameIsTheTemplate() {
        assertEquals("missingFunction", placed("definitions/hole_ref_error.sqlx",
                "config { type: \"table\" }\n\nSELECT\n  id,\n  ${missingFunction(\"x\")} AS c\nFROM ${ref(\"ok_source\")}\n",
                "missingFunction is not defined",
                "ReferenceError: missingFunction is not defined\n" + frame("Object.sqlContextable", "definitions/hole_ref_error.sqlx", 19, 13)));
    }

    public void testThePreOperationsFrameLooksInThePreOperations() {
        String text = "config { type: \"table\" }\n\nSELECT ${missingInPreOps()} AS c\n\n"
                + "pre_operations {\n  DECLARE x INT64 DEFAULT ${missingInPreOps()};\n}\n";
        PsiFile file = myFixture.addFileToProject("definitions/preops_error.sqlx", text);
        ParsedCompilationError error = CompilationErrorParser.parse("missingInPreOps is not defined",
                "ReferenceError: missingInPreOps is not defined\n" + frame("preOperationsContextable", "definitions/preops_error.sqlx", 38, 13),
                "definitions/preops_error.sqlx");

        TextRange range = CompilationErrorPlacer.place(file, file.getVirtualFile().getPath(), error);

        assertEquals(text.lastIndexOf("missingInPreOps"), range.getStartOffset());
    }

    public void testAnIncrementalPreOperationsFrameLooksInThePreOperations() {
        String text = "config { type: \"incremental\" }\n\nSELECT ${missingInPreOps()} AS c\n\n"
                + "pre_operations {\n  DECLARE x INT64 DEFAULT ${missingInPreOps()};\n}\n";
        PsiFile file = myFixture.addFileToProject("definitions/incremental_preops_error.sqlx", text);
        ParsedCompilationError error = CompilationErrorParser.parse("missingInPreOps is not defined",
                "ReferenceError: missingInPreOps is not defined\n"
                        + frame("incrementalPreOperationsContextable", "definitions/incremental_preops_error.sqlx", 38, 13),
                "definitions/incremental_preops_error.sqlx");

        TextRange range = CompilationErrorPlacer.place(file, file.getVirtualFile().getPath(), error);

        assertEquals(text.lastIndexOf("missingInPreOps"), range.getStartOffset());
    }

    public void testAnUndefinedNameOfTheConfigIsPlacedInTheConfig() {
        String text = "config {\n  type: \"table\",\n  schema: dataset_name\n}\n\njs {\n  const dataset_name = \"x\";\n}\n\nSELECT 1 AS c\n";
        PsiFile file = myFixture.addFileToProject("definitions/config_name.sqlx", text);
        ParsedCompilationError error = CompilationErrorParser.parse("dataset_name is not defined",
                "ReferenceError: dataset_name is not defined\n"
                        + frame("Object.<anonymous>", "definitions/config_name.sqlx", 3, 11),
                "definitions/config_name.sqlx");

        TextRange range = CompilationErrorPlacer.place(file, file.getVirtualFile().getPath(), error);

        assertEquals(TextRange.from(text.indexOf("dataset_name"), "dataset_name".length()), range);
    }

    public void testAnUndefinedNameOfACallbackOfAJsBlockIsStillLookedForInTheJsBlock() {
        String text = "config { type: \"table\" }\n\njs {\n  const cols = [1].map(x => missingInCallback + x);\n}\n\n"
                + "SELECT ${cols} AS c\n";
        PsiFile file = myFixture.addFileToProject("definitions/callback_error.sqlx", text);
        ParsedCompilationError error = CompilationErrorParser.parse("missingInCallback is not defined",
                "ReferenceError: missingInCallback is not defined\n"
                        + "    at " + ROOT + "definitions/callback_error.sqlx:20:30\n"
                        + "    at Array.map (<anonymous>)\n"
                        + frame("Object.sqlContextable", "definitions/callback_error.sqlx", 20, 20),
                "definitions/callback_error.sqlx");

        TextRange range = CompilationErrorPlacer.place(file, file.getVirtualFile().getPath(), error);

        assertEquals(TextRange.from(text.indexOf("missingInCallback"), "missingInCallback".length()), range);
    }

    public void testAPropertyReadFromUndefinedIsPlacedOnTheProperty() {
        assertEquals("deep", placed("definitions/type_error.sqlx",
                "config { type: \"table\" }\n\njs {\n  const o = {};\n}\n\nSELECT ${o.missing.deep} AS c\n",
                "Cannot read properties of undefined (reading 'deep')",
                "TypeError: Cannot read properties of undefined (reading 'deep')\n" + frame("Object.sqlContextable", "definitions/type_error.sqlx", 25, 20)));
    }

    public void testAMissingRefIsPlacedOnItsName() {
        String text = "config { type: \"table\" }\n\nSELECT *\nFROM ${ref(\"does_not_exist\")}\n";
        assertEquals("does_not_exist", placed("definitions/ref_missing.sqlx", text, "Could not resolve \"does_not_exist\"",
                "Error: Could not resolve \"does_not_exist\"\n    at t.Session.resolve (" + ROOT + "node_modules/@dataform/core/bundle.js:15:84057)\n"
                        + frame("Object.sqlContextable", "definitions/ref_missing.sqlx", 22, 8)));
        assertEquals("does_not_exist", placed("definitions/ref_missing_dep.sqlx", text, "Missing dependency detected: Action "
                + "\"p.ds.ref_missing\" depends on \"{\"name\":\"does_not_exist\",\"includeDependentAssertions\":false}\" which does not exist", null));
    }

    public void testConfigErrorsArePlacedOnTheValueOrTheKey() {
        assertEquals("tabel", placed("definitions/config_bad_type.sqlx", "config {\n  type: \"tabel\"\n}\n\nSELECT 1 AS c\n",
                "Unrecognized action type: tabel", "Error: Unrecognized action type: tabel\n    at " + ROOT + "definitions/config_bad_type.sqlx:1:86"));
        assertEquals("descripton", placed("definitions/config_unknown_key.sqlx",
                "config {\n  type: \"table\",\n  descripton: \"typo in key\"\n}\n\nSELECT 1 AS c\n",
                "Unexpected property \"descripton\", or property value type of \"string\" is incorrect.", null));
    }

    public void testASyntaxErrorIsPlacedAtItsCaret() {
        assertEquals(";", placed("definitions/js_syntax_error.sqlx", "config { type: \"table\" }\n\njs {\n  const x = ;\n}\n\nSELECT 1 AS c\n",
                "Unexpected token ';'", ROOT + "definitions/js_syntax_error.sqlx:19\n  const x = ;\n            ^\n\nSyntaxError: Unexpected token ';'"));
        assertEquals("SELECT", placed("definitions/unclosed_block.sqlx", "config { type: \"table\"\n\nSELECT 1 AS c\n",
                "Unexpected identifier 'SELECT'", ROOT + "definitions/unclosed_block.sqlx:7\nSELECT 1 AS c\n^^^^^^\n\nSyntaxError: Unexpected identifier 'SELECT'"));
    }

    public void testASyntaxErrorOfAScriptIsPlacedOnTheLineItsHeaderGives() {
        String text = "function a() {\n  return 1;\n}\nfunction b() {\n  return 2;\n}\n}\n";
        PsiFile file = myFixture.addFileToProject("includes/braces.js", text);
        ParsedCompilationError error = CompilationErrorParser.parse("Unexpected token '}'",
                ROOT + "includes/braces.js:7\n}\n^\n\nSyntaxError: Unexpected token '}'", "includes/braces.js");

        TextRange range = CompilationErrorPlacer.place(file, file.getVirtualFile().getPath(), error);

        assertEquals(TextRange.from(text.lastIndexOf('}'), 1), range);
    }

    public void testASyntaxErrorQuotingALineASqlxFileHasTwiceIsNotPlaced() {
        assertNull(placed("definitions/twice.sqlx",
                "config {\n  type: \"table\"\n}\n\njs {\n  const a = (1;\n}\n\nSELECT 1 AS c\n",
                "Unexpected token '}'", ROOT + "definitions/twice.sqlx:22\n}\n^\n\nSyntaxError: Unexpected token '}'"));
    }

    public void testAnErrorRaisedInAnIncludeIsPlacedOnTheCall() {
        assertEquals("broken", placed("definitions/uses_helper.sqlx", "config { type: \"table\" }\n\nSELECT ${helpers.broken()} AS c\n",
                "nope is not defined", "ReferenceError: nope is not defined\n" + frame("Object.broken", "includes/helpers.js", 2, 3) + "\n"
                        + frame("Object.sqlContextable", "definitions/uses_helper.sqlx", 21, 18)));
    }

    public void testAnIncludeShowsTheErrorRaisedInIt() {
        PsiFile include = myFixture.addFileToProject("includes/helpers.js", "function broken() {\n  return nope + 1;\n}\nmodule.exports = { broken };\n");
        ParsedCompilationError error = CompilationErrorParser.parse("nope is not defined", "ReferenceError: nope is not defined\n"
                + frame("Object.broken", "includes/helpers.js", 2, 3) + "\n"
                + frame("Object.sqlContextable", "definitions/uses_helper.sqlx", 21, 18), "definitions/uses_helper.sqlx");

        TextRange range = CompilationErrorPlacer.placeRaised(include, include.getVirtualFile().getPath(), error);

        assertEquals("nope", range.substring(include.getText()));
    }

    public void testAJavaScriptDefinitionIsPlacedAtItsFrame() {
        assertEquals("notDefinedInJs", placed("definitions/js_definition.js",
                "const tableName = \"from_js\";\npublish(tableName, { type: \"table\" });\nnotDefinedInJs();\n",
                "notDefinedInJs is not defined", "ReferenceError: notDefinedInJs is not defined\n    at " + ROOT + "definitions/js_definition.js:3:1"));
    }

    public void testANameNoLongerWrittenIsNotPlaced() {
        assertNull(placed("definitions/fixed.sqlx", "config { type: \"table\" }\n\njs {\n  const a = 1;\n}\n\nSELECT ${a} AS c\n",
                "undefinedVariable is not defined",
                "ReferenceError: undefinedVariable is not defined\n" + frame("Object.sqlContextable", "definitions/fixed.sqlx", 20, 13)));
    }

    public void testAWindowsFrameStillPlacesTheError() {
        assertEquals("undefinedVariable", placed("definitions/w.sqlx",
                "config { type: \"table\" }\n\njs {\n  const a = undefinedVariable;\n}\n\nSELECT ${a} AS c\n",
                "undefinedVariable is not defined", "ReferenceError: undefinedVariable is not defined\n"
                        + "    at Object.sqlContextable (C:\\Users\\me\\AppData\\Local\\Temp\\copy\\definitions\\w.sqlx:20:13)"));
    }
}
