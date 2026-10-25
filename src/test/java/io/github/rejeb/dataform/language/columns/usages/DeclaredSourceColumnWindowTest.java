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

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;
import io.github.rejeb.dataform.language.columns.origin.SqlxColumnAtCaret;

import java.util.ArrayList;
import java.util.List;

/**
 * A source is a BigQuery table the project reads but does not build. However the project declares
 * it, with a literal {@code declare()} call or with one computed in a loop, a column read from it
 * is shown as a column of that BigQuery table, which no line of the project declares.
 */
public class DeclaredSourceColumnWindowTest extends DataformProjectFixture {

    private static final String LITERAL = """
            declare({
              database: "proj",
              schema: "ds",
              name: "raw_events"
            });
            """;

    private static final String COMPUTED = """
            [
              "raw_events"
            ].forEach((name) =>
              declare({
                database: dataform.projectConfig.defaultProject,
                schema: dataform.projectConfig.defaultDataset,
                name,
              })
            );
            """;

    private List<ColumnUsageRow> rowsAt(PsiFile file, int line, String token) {
        PsiElement injected = injectedAt(file, line, token);
        int offset = InjectedLanguageManager.getInstance(getProject())
                .injectedToHost(injected, injected.getTextOffset());
        ColumnWindowTarget target = ColumnWindowTarget.at(file, offset);
        assertNotNull("the caret must land on a column", target);
        return ColumnUsageRows.of(getProject(), target, SqlxColumnAtCaret.referenceOf(injected));
    }

    private static List<String> describe(List<ColumnUsageRow> rows) {
        List<String> out = new ArrayList<>();
        for (ColumnUsageRow row : rows) {
            out.add(row.isHeading()
                    ? "== " + row.heading() + " " + row.count()
                    : row.kind() + " " + row.before() + row.name() + row.after() + " | " + row.location()
                    + " | " + (row.target() == null ? "no target" : "opens " + row.target().getFile().getName()));
        }
        return out;
    }

    private List<String> windowWith(String sources) throws Exception {
        myFixture.addFileToProject("definitions/sources.js", sources);
        PsiFile bronze = open("bronze/bronze_events.sqlx");
        return describe(rowsAt(bronze, 8, "event_id"));
    }

    private static final List<String> BIGQUERY_SOURCE = List.of(
            "== DECLARATION 1",
            "DECLARATION event_id | BigQuery table proj.ds.raw_events | no target");

    public void testASourceDeclaredByALiteralCallIsShownAsABigQueryTable() throws Exception {
        assertEquals(BIGQUERY_SOURCE, windowWith(LITERAL));
    }

    public void testASourceDeclaredInALoopIsShownAsABigQueryTable() throws Exception {
        assertEquals(BIGQUERY_SOURCE, windowWith(COMPUTED));
    }

    public void testAnAliasOverASourceColumnIsBuiltFromTheBigQueryTable() throws Exception {
        myFixture.addFileToProject("definitions/sources.js", COMPUTED);
        PsiFile file = myFixture.addFileToProject("definitions/renamed_events.sqlx", """
                config { type: "table" }
                SELECT event_id AS id FROM ${ref("raw_events")}
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        int offset = myFixture.getFile().getText().indexOf("AS id") + 4;
        ColumnWindowTarget target = ColumnWindowTarget.at(myFixture.getFile(), offset);
        assertNotNull("an alias opens the window", target);
        assertTrue("nothing of the project declares a source column, got " + target.declarations(),
                target.declarations().isEmpty());
        assertEquals(List.of(new ColumnRef("proj.ds.raw_events", "event_id")), target.bigQuerySources());
    }
}
