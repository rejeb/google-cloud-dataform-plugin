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

import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.diagnostics.compile.ConfigSchemaFixture;
import io.github.rejeb.dataform.language.columns.rename.ColumnRenameFixture;
import io.github.rejeb.dataform.language.columns.origin.SqlxColumnAtCaret;

import java.util.ArrayList;
import java.util.List;

public class TestFileColumnUsagesTest extends ColumnRenameFixture {

    private static final String TEST_FIN = """
            config {
              type: "test",
              dataset: "fin"
            }

            input "src" {
              SELECT 'a' AS full_name
            }

            SELECT 'a' AS full_name
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ConfigSchemaFixture.install(getProject(), getTestRootDisposable());
        installProject(
                new Action("src", "SELECT 'a' AS full_name", List.of(), List.of("full_name")),
                new Action("fin", "SELECT full_name FROM `p.d.src`", List.of("src"), List.of("full_name")));
        addFile("src", "config { type: \"table\" }\n\nSELECT 'a' AS full_name\n");
        addFile("fin", "config { type: \"table\" }\n\nSELECT\n    full_name\nFROM ${ref(\"src\")}\n");
        addTest("test_fin", "fin", TEST_FIN);
    }

    private List<String> rowsAt(String file, String token, int occurrence) {
        PsiFile host = fileOf(file);
        int offset = -1;
        for (int i = 0; i <= occurrence; i++) offset = host.getText().indexOf(token, offset + 1);
        ColumnWindowTarget target = ColumnWindowTarget.at(host, offset + 1);
        assertNotNull("the caret must land on a column", target);
        List<String> out = new ArrayList<>();
        for (ColumnUsageRow row : ColumnUsageRows.of(getProject(), target,
                SqlxColumnAtCaret.referenceAt(host, offset + 1))) {
            out.add(row.isHeading() ? "== " + row.heading() : row.kind() + " " + row.location());
        }
        return out;
    }

    public void testTheUsagesOfAColumnListTheTestAliasesStandingForIt() {
        List<String> rows = rowsAt("fin", "full_name", 0);

        assertTrue("the expected output of the test of fin, got " + rows,
                rows.contains("USAGE test_fin.sqlx:10"));
    }

    public void testTheUsagesOfAnUpstreamColumnListTheInputsMockingIt() {
        List<String> rows = rowsAt("src", "full_name", 0);

        assertTrue("the input mocking src, got " + rows, rows.contains("USAGE test_fin.sqlx:7"));
    }

    public void testTheUsagesStartedOnATestAliasAreThoseOfItsColumn() {
        List<String> rows = rowsAt("test_fin", "full_name", 0);

        assertTrue("the declaration of src, got " + rows, rows.contains("DECLARATION src.sqlx:3"));
        assertTrue("the read of fin, got " + rows, rows.contains("USAGE fin.sqlx:4"));
        assertFalse("not the alias the caret is on, got " + rows, rows.contains("USAGE test_fin.sqlx:7"));
    }
}
