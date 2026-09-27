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

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.rejeb.dataform.language.diagnostics.compile.CompilationErrorKind.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CompilationErrorParserTest {

    private static final String ROOT = "/tmp/copy/";

    @Test
    public void anUndefinedNameCarriesItsNameAndTheFramesOfTheProject() {
        ParsedCompilationError error = CompilationErrorParser.parse("undefinedVariable is not defined",
                "ReferenceError: undefinedVariable is not defined\n"
                        + "    at Object.sqlContextable (" + ROOT + "definitions/js_ref_error.sqlx:20:13)\n"
                        + "    at " + ROOT + "node_modules/@dataform/core/bundle.js:15:79488\n"
                        + "    at d.apply (" + ROOT + "node_modules/@dataform/core/bundle.js:15:77766)",
                "definitions/js_ref_error.sqlx");

        assertEquals(UNDEFINED_NAME, error.kind());
        assertEquals("undefinedVariable", error.name());
        assertEquals(new StackFrame("Object.sqlContextable", "definitions/js_ref_error.sqlx", 20, 13), error.frames().getFirst());
        assertEquals(0, error.indexOfFrameIn("/src/definitions/js_ref_error.sqlx"));
        assertEquals("sqlContextable", error.frames().getFirst().functionName());
    }

    @Test
    public void anErrorRaisedInAnIncludeKeepsBothFrames() {
        ParsedCompilationError error = CompilationErrorParser.parse("nope is not defined",
                "ReferenceError: nope is not defined\n"
                        + "    at Object.broken (" + ROOT + "includes/helpers.js:2:3)\n"
                        + "    at Object.sqlContextable (" + ROOT + "definitions/uses_helper.sqlx:21:18)",
                "definitions/uses_helper.sqlx");

        assertEquals(List.of(new StackFrame("Object.broken", "includes/helpers.js", 2, 3),
                new StackFrame("Object.sqlContextable", "definitions/uses_helper.sqlx", 21, 18)), error.frames());
        assertEquals(1, error.indexOfFrameIn("/src/definitions/uses_helper.sqlx"));
        assertEquals(0, error.indexOfFrameIn("/src/includes/helpers.js"));
    }

    @Test
    public void aSyntaxErrorCarriesTheLineItQuotesAndItsCaret() {
        ParsedCompilationError error = CompilationErrorParser.parse("Unexpected token ';'",
                ROOT + "definitions/js_syntax_error.sqlx:19\n  const x = ;\n            ^\n\n"
                        + "SyntaxError: Unexpected token ';'\n    at new Script (node:vm:117:7)",
                "definitions/js_syntax_error.sqlx");

        assertEquals(SYNTAX_ERROR, error.kind());
        assertEquals(new SourceSnippet("definitions/js_syntax_error.sqlx", 19, "  const x = ;", 12, 1), error.snippet());
    }

    @Test
    public void refsAndDependenciesNameTheMissingAction() {
        assertEquals("does_not_exist", CompilationErrorParser.parse("Could not resolve \"does_not_exist\"", null, null).name());
        ParsedCompilationError missing = CompilationErrorParser.parse("Missing dependency detected: Action "
                + "\"p.ds.ref_missing\" depends on \"{\"name\":\"does_not_exist\",\"includeDependentAssertions\":false}\" "
                + "which does not exist", null, null);
        assertEquals(UNRESOLVED_REF, missing.kind());
        assertEquals("does_not_exist", missing.name());
    }

    @Test
    public void configErrorsNameTheTypeOrThePropertyAndDropTheLink() {
        assertEquals("tabel", CompilationErrorParser.parse("Unrecognized action type: tabel", null, null).name());
        ParsedCompilationError property = CompilationErrorParser.parse("Unexpected property \"descripton\", or property "
                + "value type of \"string\" is incorrect. See https://dataform-co.github.io/dataform/docs/configs-reference"
                + "#dataform-ActionConfig-TableConfig for allowed properties.", null, null);
        assertEquals(UNEXPECTED_CONFIG_PROPERTY, property.kind());
        assertEquals("descripton", property.name());
        assertEquals("Unexpected property \"descripton\", or property value type of \"string\" is incorrect", property.message());
    }

    @Test
    public void propertyAndFunctionErrorsNameWhatWasRead() {
        assertEquals("deep", CompilationErrorParser.parse("Cannot read properties of undefined (reading 'deep')", null, null).name());
        assertEquals("helpers.brokn", CompilationErrorParser.parse("helpers.brokn is not a function", null, null).name());
    }

    @Test
    public void windowsFramesAreRelativizedToo() {
        ParsedCompilationError error = CompilationErrorParser.parse("x is not defined",
                "ReferenceError: x is not defined\n"
                        + "    at Object.sqlContextable (C:\\Users\\me\\AppData\\Local\\Temp\\copy\\definitions\\w.sqlx:20:13)",
                "definitions\\w.sqlx");

        assertEquals("definitions/w.sqlx", error.frames().getFirst().path());
        assertEquals(0, error.indexOfFrameIn("C:/work/proj/definitions/w.sqlx"));
    }

    @Test
    public void aMessageWithoutKnownShapeIsKeptWhole() {
        ParsedCompilationError error = CompilationErrorParser.parse(null, "Error: Something odd\n    at x (y)", null);

        assertEquals(OTHER, error.kind());
        assertEquals("Something odd", error.message());
        assertNull(error.name());
    }
}
