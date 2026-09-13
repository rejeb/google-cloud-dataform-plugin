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
package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DataformCteQueryBuilderTest extends BasePlatformTestCase {


    public void testNoSubstitutionWhenKnownSchemasEmpty() {
        String query = "SELECT * FROM project.dataset.table_a";
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, Map.of(), getProject());
        assertEquals(query, result);
    }

    public void testNoSubstitutionWhenFqnNotInQuery() {
        String query = "SELECT * FROM project.dataset.other_table";
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "project.dataset.table_b",
                List.of(new ColumnInfo("col1", "STRING", "NULLABLE", null))
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, schemas, getProject());
        assertEquals(query, result);
    }

    public void testSubstitutesUnquotedFqn() {
        String query = "SELECT a.col1 FROM project.dataset.table_b AS a";
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "project.dataset.table_b",
                List.of(new ColumnInfo("col1", "STRING", "NULLABLE", null))
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, schemas, getProject());
        assertTrue(result.contains("_df_table_b AS ("));
        assertTrue(result.contains("CAST(NULL AS STRING) AS col1"));
        assertTrue(result.contains("FROM _df_table_b AS a"));
        assertFalse(result.contains("project.dataset.table_b"));
    }

    public void testSubstitutesSingleBacktickedFqn() {
        String query = "SELECT * FROM `my-project.dataset.table_c`";
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "my-project.dataset.table_c",
                List.of(new ColumnInfo("id", "INT64", "REQUIRED", null))
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, schemas, getProject());
        assertTrue(result.contains("_df_table_c AS ("));
        assertTrue(result.contains("CAST(NULL AS INT64) AS id"));
        assertTrue(result.contains("FROM _df_table_c"));
        assertFalse(result.contains("`my-project.dataset.table_c`"));
    }

    public void testSubstitutesFullyIndividuallyQuotedFqn() {
        String query = "SELECT * FROM `proj`.`ds`.`tbl`";
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "proj.ds.tbl",
                List.of(new ColumnInfo("name", "STRING", "NULLABLE", null))
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, schemas, getProject());
        assertTrue(result.contains("_df_tbl AS ("));
        assertTrue(result.contains("FROM _df_tbl"));
        assertFalse(result.contains("`proj`.`ds`.`tbl`"));
    }

    public void testSubstitutesProjectOnlyQuotedFqn() {
        String query = "SELECT * FROM `my-project`.dataset.table_d";
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "my-project.dataset.table_d",
                List.of(new ColumnInfo("val", "FLOAT64", "NULLABLE", null))
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, schemas, getProject());
        assertTrue(result.contains("_df_table_d AS ("));
        assertFalse(result.contains("`my-project`.dataset.table_d"));
    }

    public void testHandlesRepeatedColumn() {
        List<ColumnInfo> columns = List.of(
                new ColumnInfo("tags", "STRING", "REPEATED", null)
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(
                "SELECT * FROM proj.ds.t", Map.of("proj.ds.t", columns), getProject());
        assertTrue(result.contains("CAST(NULL AS ARRAY<STRING>) AS tags"));
    }

    public void testHandlesStructColumn() {
        List<ColumnInfo> columns = List.of(
                new ColumnInfo("address", "RECORD", "NULLABLE", null,
                        List.of(
                                new ColumnInfo("city", "STRING", "NULLABLE", null),
                                new ColumnInfo("zip", "INT64", "NULLABLE", null)
                        ))
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(
                "SELECT * FROM proj.ds.t", Map.of("proj.ds.t", columns), getProject());
        assertTrue(result.contains("CAST(NULL AS STRUCT<city STRING, zip INT64>) AS address"));
    }

    public void testHandlesEmptyColumnList() {
        String result = DataformCteQueryBuilder.buildDryRunQuery(
                "SELECT * FROM proj.ds.t",
                Map.of("proj.ds.t", Collections.emptyList()),
                getProject());
        assertTrue(result.contains("CAST(NULL AS STRING) AS _df_placeholder_"));
    }

    public void testNormalizesLegacyTypeNames() {
        List<ColumnInfo> columns = List.of(
                new ColumnInfo("count", "INTEGER", "NULLABLE", null),
                new ColumnInfo("ratio", "FLOAT", "NULLABLE", null),
                new ColumnInfo("flag", "BOOLEAN", "NULLABLE", null)
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(
                "SELECT * FROM proj.ds.t", Map.of("proj.ds.t", columns), getProject());
        assertTrue(result.contains("CAST(NULL AS INT64) AS count"));
        assertTrue(result.contains("CAST(NULL AS FLOAT64) AS ratio"));
        assertTrue(result.contains("CAST(NULL AS BOOL) AS flag"));
    }


    public void testDoesNotSubstituteInsideStringLiteral() {
        String query = "SELECT 'proj.ds.table_b' AS label FROM proj.ds.table_b";
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "proj.ds.table_b",
                List.of(new ColumnInfo("col1", "STRING", "NULLABLE", null))
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, schemas, getProject());
        assertTrue(result.contains("'proj.ds.table_b'"));
        assertTrue(result.contains("FROM _df_table_b"));
    }

    public void testDoesNotSubstituteInsideLineComment() {
        String query = "-- FROM proj.ds.table_e\nSELECT * FROM proj.ds.table_e";
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "proj.ds.table_e",
                List.of(new ColumnInfo("x", "STRING", "NULLABLE", null))
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, schemas, getProject());
        assertTrue(result.contains("-- FROM proj.ds.table_e"));
        assertTrue(result.contains("FROM _df_table_e"));
    }

    public void testAliasCollisionResolution() {
        String query = "SELECT * FROM schema1.ds.orders JOIN schema2.ds.orders AS o2 ON true";
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "schema1.ds.orders", List.of(new ColumnInfo("id", "INT64", "NULLABLE", null)),
                "schema2.ds.orders", List.of(new ColumnInfo("ref", "STRING", "NULLABLE", null))
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, schemas, getProject());
        long cteCount = result.lines()
                .filter(l -> l.contains("_df_") && l.contains("AS ("))
                .count();
        assertEquals(2, cteCount);
    }

    public void testMultipleTablesSubstitutedInOneQuery() {
        String query = """
                SELECT a.col1, b.col2
                FROM proj.ds.table_x AS a
                JOIN proj.ds.table_y AS b ON a.id = b.id
                """;
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "proj.ds.table_x", List.of(
                        new ColumnInfo("id", "INT64", "NULLABLE", null),
                        new ColumnInfo("col1", "STRING", "NULLABLE", null)
                ),
                "proj.ds.table_y", List.of(
                        new ColumnInfo("id", "INT64", "NULLABLE", null),
                        new ColumnInfo("col2", "STRING", "NULLABLE", null)
                )
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, schemas, getProject());
        assertTrue(result.contains("_df_table_x AS ("));
        assertTrue(result.contains("_df_table_y AS ("));
        assertTrue(result.contains("FROM _df_table_x AS a"));
        assertTrue(result.contains("JOIN _df_table_y AS b"));
        assertFalse(result.contains("proj.ds.table_x"));
        assertFalse(result.contains("proj.ds.table_y"));
    }

    public void testMergesWithExistingWithClause() {
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "proj.ds.dep",
                List.of(new ColumnInfo("id", "INT64", "NULLABLE", null))
        );

        String queryFqn = """
                WITH
                existing_cte AS (
                    SELECT * FROM proj.ds.dep
                )
                SELECT * FROM existing_cte
                """;

        String result = DataformCteQueryBuilder.buildDryRunQuery(queryFqn, schemas, getProject());

        long withCount = result.lines()
                .filter(l -> l.trim().equalsIgnoreCase("WITH"))
                .count();
        assertEquals(1, withCount);
        assertTrue(result.contains("_df_dep AS ("));
        assertTrue(result.contains("existing_cte AS ("));
    }

    /** The number of WITH clauses the query opens: more than one is not a query at all. */
    private static long withClauseCount(String sql) {
        return java.util.regex.Pattern.compile("(?im)^\\s*WITH\\b").matcher(sql).results().count();
    }

    private static Map<String, List<ColumnInfo>> dep() {
        return Map.of("proj.ds.dep", List.of(new ColumnInfo("id", "INT64", "NULLABLE", null)));
    }

    /**
     * A compiled Dataform query carries the comments of its SQLX file in front of the query, so the
     * WITH keyword is rarely the first thing in the text. Trimming whitespace alone left the comment
     * in front of it, the clause went unrecognised, and the stubs were put beside it instead of into
     * it — two WITH clauses, which BigQuery rejects with a syntax error. The schema of every action
     * built by a CTE was lost that way, and with it the columns of everything downstream.
     */
    public void testMergesWhenALineCommentPrecedesTheWithClause() {
        String query = """
                -- WHAT TO TEST: a header comment, as every action in a real project has.
                --
                WITH existing_cte AS (
                    SELECT * FROM proj.ds.dep
                )
                SELECT * FROM existing_cte
                """;
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, dep(), getProject());
        assertEquals("the stubs must join the query's own clause, not open a second one, got:\n"
                + result, 1, withClauseCount(result));
        assertTrue(result.contains("_df_dep AS ("));
        assertTrue(result.contains("existing_cte AS ("));
        assertTrue("the comment must survive", result.contains("-- WHAT TO TEST"));
    }

    public void testMergesWhenABlockCommentPrecedesTheWithClause() {
        String query = """
                /* a block comment
                   over two lines */
                WITH existing_cte AS (
                    SELECT * FROM proj.ds.dep
                )
                SELECT * FROM existing_cte
                """;
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, dep(), getProject());
        assertEquals("a block comment hides the clause just as a line comment does, got:\n"
                + result, 1, withClauseCount(result));
        assertTrue(result.contains("_df_dep AS ("));
    }

    /**
     * RECURSIVE belongs to the clause, not to one definition, so it has to move to the front of the
     * merged clause. Left in place it reads as a CTE named RECURSIVE and BigQuery rejects it.
     */
    public void testHoistsRecursiveOntoTheMergedClause() {
        String query = """
                -- a recursive walk
                WITH RECURSIVE walk AS (
                    SELECT 1 AS depth FROM proj.ds.dep
                    UNION ALL
                    SELECT depth + 1 FROM walk WHERE depth < 3
                )
                SELECT * FROM walk
                """;
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, dep(), getProject());
        assertEquals("got:\n" + result, 1, withClauseCount(result));
        assertTrue("the clause must carry RECURSIVE, got:\n" + result,
                result.startsWith("WITH RECURSIVE\n"));
        assertFalse("RECURSIVE must not be left where a CTE name is expected, got:\n" + result,
                result.matches("(?s).*,\\s*RECURSIVE\\b.*"));
        assertTrue(result.contains("_df_dep AS ("));
        assertTrue(result.contains("walk AS ("));
    }

    /** A query that opens with a comment and no CTE still only gets the stubs put in front of it. */
    public void testPrependsWhenACommentPrecedesAPlainSelect() {
        String query = """
                -- a plain select behind a comment
                SELECT * FROM proj.ds.dep
                """;
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, dep(), getProject());
        assertEquals("got:\n" + result, 1, withClauseCount(result));
        assertTrue(result.contains("_df_dep AS ("));
        assertTrue(result.contains("-- a plain select behind a comment"));
    }

    /** A name merely starting with the letters of the keyword is not the keyword. */
    public void testAColumnNamedLikeTheKeywordIsNotMistakenForIt() {
        String query = """
                SELECT withholding FROM proj.ds.dep
                """;
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, dep(), getProject());
        assertEquals("got:\n" + result, 1, withClauseCount(result));
        assertTrue("the query must be kept whole, got:\n" + result,
                result.contains("SELECT withholding FROM _df_dep"));
    }
}
