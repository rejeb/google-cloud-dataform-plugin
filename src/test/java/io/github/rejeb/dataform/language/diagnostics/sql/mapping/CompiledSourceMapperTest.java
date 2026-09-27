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
package io.github.rejeb.dataform.language.diagnostics.sql.mapping;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.schema.sql.DryRunQueryText;

public class CompiledSourceMapperTest extends BasePlatformTestCase {

    private static final String SQLX = "config { type: \"table\" }\n\njs {\n  const cols = \"a\";\n}\n\n"
            + "-- a \\d comment\nSELECT\n  custmer_id,\n  ${cols}\nFROM ${ref(\"orders\")}\n\n"
            + "pre_operations {\n  DECLARE x INT64 DEFAULT 1\n}\n";
    private static final String COMPILED = "\n\n\n\n-- a d comment\nSELECT\n  custmer_id,\n  a\nFROM `p.d.orders`\n\n\n";

    private SqlxSourceText main(String sqlx) {
        PsiFile file = myFixture.configureByText("mapped.sqlx", sqlx);
        return SqlxSourceText.of(file, DryRunQueryText.MAIN_QUERY);
    }

    public void testAWordTheFileStillHasMapsToItsPlaceInTheFile() {
        SqlxSourceText sqlx = main(SQLX);

        assertEquals(new CompiledSourceMapper.HostPlace(SQLX.indexOf("custmer_id"), null),
                CompiledSourceMapper.toHost(COMPILED, COMPILED.indexOf("custmer_id"), sqlx));
    }

    public void testAPlaceInAHoleValueMapsToTheWholeHole() {
        SqlxSourceText sqlx = main(SQLX);
        TextRange ref = TextRange.from(SQLX.indexOf("${ref("), "${ref(\"orders\")}".length());
        TextRange cols = TextRange.from(SQLX.indexOf("${cols}"), "${cols}".length());

        assertEquals(ref, CompiledSourceMapper.toHost(COMPILED, COMPILED.indexOf("orders"), sqlx).hole());
        assertEquals(ref, CompiledSourceMapper.toHost(COMPILED, COMPILED.indexOf("`p.d"), sqlx).hole());
        assertEquals(cols, CompiledSourceMapper.toHost(COMPILED, COMPILED.indexOf("  a\n") + 2, sqlx).hole());
    }

    public void testWindowsLineEndsInTheCompiledQueryDoNotMatter() {
        SqlxSourceText sqlx = main(SQLX);
        String crlf = COMPILED.replace("\n", "\r\n");

        assertEquals(SQLX.indexOf("SELECT"), CompiledSourceMapper.toHost(crlf, crlf.indexOf("SELECT"), sqlx).offset());
        assertEquals(SQLX.indexOf("custmer_id"),
                CompiledSourceMapper.toHost(crlf, crlf.indexOf("custmer_id"), sqlx).offset());
    }

    public void testAWordEditedSinceTheCompilationMapsNowhere() {
        SqlxSourceText sqlx = main(SQLX.replace("custmer_id", "customer_id"));

        assertNull(CompiledSourceMapper.toHost(COMPILED, COMPILED.indexOf("custmer_id"), sqlx));
    }

    public void testThePreOperationsAreASourceOfTheirOwn() {
        PsiFile file = myFixture.configureByText("mapped.sqlx", SQLX);
        SqlxSourceText pre = SqlxSourceText.of(file, DryRunQueryText.PRE_OPERATIONS);
        String compiled = "DECLARE x INT64 DEFAULT 1;";

        assertNotNull(pre);
        assertEquals(SQLX.indexOf("INT64"), CompiledSourceMapper.toHost(compiled, compiled.indexOf("INT64"), pre).offset());
    }

    public void testASourceTheFileDoesNotHaveIsNothing() {
        PsiFile file = myFixture.configureByText("mapped.sqlx", "config { type: \"table\" }\nSELECT 1\n");

        assertNull(SqlxSourceText.of(file, DryRunQueryText.PRE_OPERATIONS));
        assertNull(SqlxSourceText.of(file, "unknown"));
    }
}
