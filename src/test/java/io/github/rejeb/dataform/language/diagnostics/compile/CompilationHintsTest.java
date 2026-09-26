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
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlFix;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlHint;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompilationHintsTest {

    private record Scope(Collection<String> jsNames, Collection<String> actionNames, Collection<String> configKeys)
            implements CompilationScope {
    }

    private static SqlHint hint(String message, String text, String token, CompilationScope scope) {
        int start = text.indexOf(token);
        return CompilationHints.hintFor(CompilationErrorParser.parse(message, null, null),
                TextRange.from(start, token.length()), text, scope);
    }

    @Test
    public void anUndefinedNameSuggestsTheNamesTheFileCanRead() {
        SqlHint hint = hint("bse is not defined", "const base = 1; const a = bse + 1;", "bse",
                new Scope(List.of("base", "ref"), List.of(), List.of()));

        assertEquals("Did you mean 'base'?", hint.text());
        assertEquals(List.of(new SqlFix("Replace with 'base'", new TextRange(26, 29), "base")), hint.fixes());
    }

    @Test
    public void anUndefinedNameWithNothingCloseSaysWhereToDefineIt() {
        assertEquals("Define 'zzz' in a js { } block, or export it from a file of includes/.",
                hint("zzz is not defined", "SELECT ${zzz} AS c", "zzz", CompilationScope.EMPTY).text());
    }

    @Test
    public void aMissingRefSuggestsTheClosestAction() {
        SqlHint hint = hint("Could not resolve \"ordrs\"", "FROM ${ref(\"ordrs\")}", "ordrs",
                new Scope(List.of(), List.of("orders", "customers"), List.of()));

        assertEquals("Did you mean 'orders'?", hint.text());
        assertEquals("Replace with 'orders'", hint.fixes().getFirst().label());
    }

    @Test
    public void aWrongActionTypeSuggestsAValidOne() {
        SqlHint hint = hint("Unrecognized action type: tabel", "type: \"tabel\"", "tabel", CompilationScope.EMPTY);

        assertEquals("Did you mean 'table'?", hint.text());
    }

    @Test
    public void anUnexpectedConfigPropertySuggestsTheKeysOfItsObject() {
        SqlHint hint = hint("Unexpected property \"descripton\", or property value type of \"string\" is incorrect.",
                "descripton: \"x\"", "descripton", new Scope(List.of(), List.of(), List.of("description", "tags")));

        assertEquals("Did you mean 'description'?", hint.text());
    }

    @Test
    public void sqlReadAsJavaScriptPointsAtTheUnclosedBlock() {
        SqlHint hint = CompilationHints.hintFor(CompilationErrorParser.parse("Unexpected identifier 'SELECT'",
                        "/tmp/copy/definitions/u.sqlx:7\nSELECT 1 AS c\n^^^^^^\n\nSyntaxError: Unexpected identifier 'SELECT'",
                        "definitions/u.sqlx"),
                TextRange.from(0, 6), "SELECT 1 AS c", CompilationScope.EMPTY);

        assertTrue(hint.text().contains("missing its closing '}'"));
    }

    @Test
    public void propertyAndFunctionErrorsExplainWhatWasRead() {
        assertTrue(hint("Cannot read properties of undefined (reading 'deep')", "o.missing.deep", "deep", CompilationScope.EMPTY)
                .text().startsWith("The value read before '.deep' is undefined"));
        assertTrue(hint("helpers.brokn is not a function", "helpers.brokn()", "helpers.brokn", CompilationScope.EMPTY)
                .text().contains("module.exports"));
    }
}
