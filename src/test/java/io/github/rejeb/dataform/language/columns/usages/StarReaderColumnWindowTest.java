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
import io.github.rejeb.dataform.language.columns.origin.SqlxColumnAtCaret;
import io.github.rejeb.dataform.language.columns.rename.ColumnRenameFixture;

import java.util.ArrayList;
import java.util.List;

/**
 * A downstream action selecting {@code *} from a table reads every column of it without naming
 * one, so no reference points at the column. The star is still where the column is read, and the
 * column it carries on is read under the same name further down.
 */
public class StarReaderColumnWindowTest extends ColumnRenameFixture {

    private static final String STAGING = """
            config { type: "table" }

            SELECT
                t.item_id,
                t.amount
            FROM ${ref("src_items")} AS t
            """;

    private List<String> window(PsiFile file, String token) {
        int offset = file.getText().indexOf(token) + 1;
        ColumnWindowTarget target = ColumnWindowTarget.at(file, offset);
        if (target == null) return List.of("<no target>");
        PsiElement injected = InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(file, offset);
        List<String> out = new ArrayList<>();
        for (ColumnUsageRow row : ColumnUsageRows.of(getProject(), target, SqlxColumnAtCaret.referenceOf(injected))) {
            out.add(row.isHeading() ? "== " + row.heading() : row.kind() + " " + row.location());
        }
        return out;
    }

    public void testTheStarOfADownstreamActionIsListedAsAUsage() {
        installProject(List.of(new Source("src_items", List.of("item_id", "amount"))),
                new Action("stg_items", "SELECT t.item_id, t.amount FROM `p.d.src_items` AS t",
                        List.of("src_items"), List.of("item_id", "amount")),
                new Action("all_items", "SELECT * FROM `p.d.stg_items` s",
                        List.of("stg_items"), List.of("item_id", "amount")));
        addFile("all_items", """
                config { type: "table" }

                SELECT * FROM ${ref("stg_items")} s
                """);
        PsiFile staging = addFile("stg_items", STAGING);
        assertEquals(List.of("== DECLARATION", "DECLARATION BigQuery table p.d.src_items",
                        "== USAGES", "USAGE all_items.sqlx:3"),
                window(staging, "item_id"));
    }

    public void testTheReadsOfTheColumnCarriedOnByTheStarAreListed() {
        installProject(List.of(new Source("src_items", List.of("item_id", "amount"))),
                new Action("stg_items", "SELECT t.item_id, t.amount FROM `p.d.src_items` AS t",
                        List.of("src_items"), List.of("item_id", "amount")),
                new Action("all_items", "SELECT * FROM `p.d.stg_items` s",
                        List.of("stg_items"), List.of("item_id", "amount")),
                new Action("item_ids", "SELECT a.item_id FROM `p.d.all_items` a",
                        List.of("all_items"), List.of("item_id")));
        addFile("all_items", """
                config { type: "table" }

                SELECT * FROM ${ref("stg_items")} s
                """);
        addFile("item_ids", """
                config { type: "table" }

                SELECT
                    a.item_id
                FROM ${ref("all_items")} a
                """);
        PsiFile staging = addFile("stg_items", STAGING);
        assertEquals(List.of("== DECLARATION", "DECLARATION BigQuery table p.d.src_items",
                        "== USAGES", "USAGE all_items.sqlx:3", "USAGE item_ids.sqlx:4"),
                window(staging, "item_id"));
    }

    private PsiFile installStar(String query, List<String> columns) {
        installProject(List.of(new Source("src_items", List.of("item_id", "amount"))),
                new Action("stg_items", "SELECT t.item_id, t.amount FROM `p.d.src_items` AS t",
                        List.of("src_items"), List.of("item_id", "amount")),
                new Action("all_items", query.replace("${ref(\"stg_items\")}", "`p.d.stg_items`"),
                        List.of("stg_items"), columns),
                new Action("item_amounts", "SELECT a.amount FROM `p.d.all_items` a",
                        List.of("all_items"), List.of("amount")));
        addFile("all_items", "config { type: \"table\" }\n\n" + query + "\n");
        addFile("item_amounts", """
                config { type: "table" }

                SELECT
                    a.amount
                FROM ${ref("all_items")} a
                """);
        return addFile("stg_items", STAGING);
    }

    public void testAStarWithAnExceptListIsAUsageOfTheColumnsItKeeps() {
        PsiFile staging = installStar("SELECT * EXCEPT(amount) FROM ${ref(\"stg_items\")} s",
                List.of("item_id"));
        assertEquals(List.of("== DECLARATION", "DECLARATION BigQuery table p.d.src_items",
                        "== USAGES", "USAGE all_items.sqlx:3"),
                window(staging, "item_id"));
    }

    public void testAStarWithAnExceptListIsNoUsageOfTheColumnsItExcludes() {
        PsiFile staging = installStar("SELECT * EXCEPT(amount) FROM ${ref(\"stg_items\")} s",
                List.of("item_id"));
        assertEquals(List.of("== DECLARATION", "DECLARATION BigQuery table p.d.src_items",
                        "== USAGES", "USAGE all_items.sqlx:3"),
                window(staging, "amount"));
    }

    public void testAnExcludedColumnRedeclaredUnderItsOwnNameIsNotCarriedOnByTheStar() {
        PsiFile staging = installStar("SELECT * EXCEPT(amount), 0 AS amount FROM ${ref(\"stg_items\")} s",
                List.of("item_id", "amount"));
        assertEquals(List.of("== DECLARATION", "DECLARATION BigQuery table p.d.src_items",
                        "== USAGES", "USAGE all_items.sqlx:3"),
                window(staging, "amount"));
    }

    public void testAColumnReplacedByTheStarIsReadByTheReplacementOnly() {
        PsiFile staging = installStar("SELECT * REPLACE(ROUND(s.amount, 2) AS amount) FROM ${ref(\"stg_items\")} s",
                List.of("item_id", "amount"));
        assertEquals(List.of("== DECLARATION", "DECLARATION BigQuery table p.d.src_items",
                        "== USAGES", "USAGE all_items.sqlx:3"),
                window(staging, "amount"));
    }
}
