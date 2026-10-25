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
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.columns.rename.ColumnRenameFixture;
import io.github.rejeb.dataform.language.columns.origin.SqlxColumnAtCaret;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * A column of the main select list is often built by an expression over a column it reads:
 * {@code CAST(TRIM(name) AS STRING) AS name}. Whether the caret sits on the read or on the alias,
 * the reader asks where the column goes next, so both open a window listing the reads of the
 * column the item declares.
 */
public class ReadInsideAnAliasedColumnTest extends ColumnRenameFixture {

    private static final String STAGING = """
            config { type: "view" }

            SELECT
                CAST(TRIM(last_name) AS STRING) AS last_name
                , person_id
            FROM ${ref("src_people")} AS t
            """;

    private static final String AGGREGATE = """
            config { type: "table" }

            WITH a AS (
                SELECT p.last_name AS name, p.person_id AS id
                FROM ${ref("stg_people")} AS p
            )
            SELECT name, id FROM a
            """;

    private List<String> window(PsiFile file, int occurrence, String token) {
        String text = file.getText();
        int offset = -1;
        for (int i = 0; i <= occurrence; i++) offset = text.indexOf(token, offset + 1);
        offset += 1;
        ColumnWindowTarget target = ColumnWindowTarget.at(file, offset);
        if (target == null) return List.of("<no target>");
        PsiElement injected = InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(file, offset);
        List<String> out = new ArrayList<>();
        for (ColumnUsageRow row : ColumnUsageRows.of(getProject(), target, SqlxColumnAtCaret.referenceOf(injected))) {
            out.add(row.isHeading() ? "== " + row.heading() : row.kind() + " " + row.location());
        }
        return out;
    }

    private PsiFile install() {
        installProject(List.of(new Source("src_people", List.of("person_id", "last_name"))),
                new Action("stg_people", "SELECT CAST(TRIM(last_name) AS STRING) AS last_name, person_id FROM `p.d.src_people` AS t",
                        List.of("src_people"), List.of("last_name", "person_id")),
                new Action("agg_people", "WITH a AS (SELECT p.last_name AS name, p.person_id AS id FROM `p.d.stg_people` AS p) SELECT name, id FROM a",
                        List.of("stg_people"), List.of("name", "id")));
        addFile("agg_people", AGGREGATE);
        return addFile("stg_people", STAGING);
    }

    public void testAReadInsideAnAliasedItemListsTheReadsOfTheColumnTheItemDeclares() {
        PsiFile staging = install();
        assertEquals(List.of("== DECLARATION", "DECLARATION BigQuery table p.d.src_people",
                        "== USAGES", "USAGE agg_people.sqlx:4"),
                window(staging, 0, "last_name"));
    }

    public void testTheReadAndTheAliasOpenTheSameWindow() {
        PsiFile staging = install();
        assertEquals(window(staging, 1, "last_name"), window(staging, 0, "last_name"));
    }

    public void testTheAliasListsReadsNestedInFunctionsJoinsAndFilters() {
        installProject(List.of(new Source("src_people", List.of("person_id", "last_name")),
                        new Source("src_teams", List.of("team_id", "label"))),
                new Action("stg_people", "SELECT CAST(TRIM(last_name) AS STRING) AS last_name, person_id FROM `p.d.src_people` AS t",
                        List.of("src_people"), List.of("last_name", "person_id")),
                new Action("agg_people", "SELECT 1", List.of("stg_people"), List.of("name", "valid")));
        addFile("agg_people", """
                config { type: "incremental" }

                WITH a AS (
                    SELECT
                        CONCAT(UPPER(SUBSTR(p.last_name, 1, 1)), LOWER(SUBSTR(p.last_name, 2))) AS name,
                        TRIM(UPPER(p.last_name)) AS upper_name,
                        coalesce((p.last_name NOT LIKE '%x%' AND p.person_id = 1), TRUE) AS valid
                    FROM ${ref("stg_people")} AS p
                    LEFT JOIN ${ref("src_teams")} AS tm ON p.person_id = tm.team_id
                    WHERE p.last_name IS NOT NULL
                )
                SELECT name, valid FROM a
                """);
        PsiFile staging = addFile("stg_people", STAGING);
        List<String> expected = List.of("== DECLARATION", "DECLARATION BigQuery table p.d.src_people",
                "== USAGES", "USAGE agg_people.sqlx:5", "USAGE agg_people.sqlx:6",
                "USAGE agg_people.sqlx:7", "USAGE agg_people.sqlx:10");
        assertEquals(expected, window(staging, 1, "last_name"));
        assertEquals(expected, window(staging, 0, "last_name"));
    }

    /**
     * An include may write a whole common table expression: {@code WITH ${helper(ref, "x")}}. Until
     * Node has evaluated it, the hole must still leave a query whose main select list is found, or
     * no column of the file declares anything and nothing downstream is listed.
     */
    public void testAnUnevaluatedCteWrittenByAnIncludeKeepsTheUsages() {
        installProject(List.of(new Source("src_people", List.of("person_id", "last_name"))),
                new Action("stg_people", "SELECT 1", List.of("src_people"), List.of("last_name", "person_id")),
                new Action("agg_people", "SELECT 1", List.of("stg_people"), List.of("name", "id")));
        addFile("agg_people", AGGREGATE);
        PsiFile staging = addFile("stg_people", """
                config { type: "view" }

                WITH ${helpers.some_cte(ref, "people")}

                SELECT
                    CAST(TRIM(last_name) AS STRING) AS last_name
                    , person_id
                FROM ${ref("src_people")} AS t
                WHERE
                    ${helpers.some_filter("stamp")}
                """);
        List<String> expected = List.of("== DECLARATION", "DECLARATION BigQuery table p.d.src_people",
                "== USAGES", "USAGE agg_people.sqlx:4");
        assertEquals(expected, window(staging, 1, "last_name"));
        assertEquals(expected, window(staging, 0, "last_name"));
    }

    /**
     * The Dataform CLI writes the file name of an action with the separator of the system it runs
     * on, so on Windows the compiled graph holds {@code definitions\stg_people.sqlx} while the IDE
     * names the same file {@code definitions/stg_people.sqlx}. The file must still be recognised as
     * the one building its table, or no alias of it declares a column and no usage is listed.
     */
    public void testTheAliasListsItsUsagesWhenTheCompilerWritesWindowsPaths() throws Exception {
        PsiFile staging = install();
        CompiledGraph graph = DataformCompilationService.getInstance(getProject()).getCompiledGraph();
        for (CompiledTable table : graph.getTables()) {
            Field fileName = CompiledTable.class.getDeclaredField("fileName");
            fileName.setAccessible(true);
            fileName.set(table, table.getFileName().replace('/', '\\'));
        }
        assertEquals(List.of("== DECLARATION", "DECLARATION BigQuery table p.d.src_people",
                        "== USAGES", "USAGE agg_people.sqlx:4"),
                window(staging, 1, "last_name"));
    }
}
