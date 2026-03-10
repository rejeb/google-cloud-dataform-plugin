package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DataformCteQueryBuilderTest extends BasePlatformTestCase {

    // ─────────────────────────────────────────────────────────────────────────
    // No substitution
    // ─────────────────────────────────────────────────────────────────────────

    public void testNoSubstitutionWhenKnownSchemasEmpty() {
        String query = "SELECT * FROM project.dataset.table_a";
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, Map.of(), getProject());
        assertEquals(query, result);
    }

    public void testNoSubstitutionWhenFqnNotInQuery() {
        String query = "SELECT * FROM project.dataset.other_table";
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "project.dataset.table_b",
                List.of(new ColumnInfo("col1", "STRING", "NULLABLE"))
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, schemas, getProject());
        assertEquals(query, result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FQN quoting variants
    // ─────────────────────────────────────────────────────────────────────────

    public void testSubstitutesUnquotedFqn() {
        String query = "SELECT a.col1 FROM project.dataset.table_b AS a";
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "project.dataset.table_b",
                List.of(new ColumnInfo("col1", "STRING", "NULLABLE"))
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
                List.of(new ColumnInfo("id", "INT64", "REQUIRED"))
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
                List.of(new ColumnInfo("name", "STRING", "NULLABLE"))
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
                List.of(new ColumnInfo("val", "FLOAT64", "NULLABLE"))
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, schemas, getProject());
        assertTrue(result.contains("_df_table_d AS ("));
        assertFalse(result.contains("`my-project`.dataset.table_d"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Column types
    // ─────────────────────────────────────────────────────────────────────────

    public void testHandlesRepeatedColumn() {
        List<ColumnInfo> columns = List.of(
                new ColumnInfo("tags", "STRING", "REPEATED")
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(
                "SELECT * FROM proj.ds.t", Map.of("proj.ds.t", columns), getProject());
        assertTrue(result.contains("CAST(NULL AS ARRAY<STRING>) AS tags"));
    }

    public void testHandlesStructColumn() {
        List<ColumnInfo> columns = List.of(
                new ColumnInfo("address", "RECORD", "NULLABLE",
                        List.of(
                                new ColumnInfo("city", "STRING", "NULLABLE"),
                                new ColumnInfo("zip", "INT64", "NULLABLE")
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
                new ColumnInfo("count", "INTEGER", "NULLABLE"),
                new ColumnInfo("ratio", "FLOAT", "NULLABLE"),
                new ColumnInfo("flag", "BOOLEAN", "NULLABLE")
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(
                "SELECT * FROM proj.ds.t", Map.of("proj.ds.t", columns), getProject());
        assertTrue(result.contains("CAST(NULL AS INT64) AS count"));
        assertTrue(result.contains("CAST(NULL AS FLOAT64) AS ratio"));
        assertTrue(result.contains("CAST(NULL AS BOOL) AS flag"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Safety : no substitution inside string literals or comments
    // ─────────────────────────────────────────────────────────────────────────

    public void testDoesNotSubstituteInsideStringLiteral() {
        // FQN appears inside a string value — must NOT be replaced
        String query = "SELECT 'proj.ds.table_b' AS label FROM proj.ds.table_b";
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "proj.ds.table_b",
                List.of(new ColumnInfo("col1", "STRING", "NULLABLE"))
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, schemas, getProject());
        // The string literal part must be unchanged
        assertTrue(result.contains("'proj.ds.table_b'"));
        // The FROM reference must be substituted
        assertTrue(result.contains("FROM _df_table_b"));
    }

    public void testDoesNotSubstituteInsideLineComment() {
        String query = "-- FROM proj.ds.table_e\nSELECT * FROM proj.ds.table_e";
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "proj.ds.table_e",
                List.of(new ColumnInfo("x", "STRING", "NULLABLE"))
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, schemas, getProject());
        // Comment content must be untouched
        assertTrue(result.contains("-- FROM proj.ds.table_e"));
        // The real FROM must be substituted
        assertTrue(result.contains("FROM _df_table_e"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Alias collision
    // ─────────────────────────────────────────────────────────────────────────

    public void testAliasCollisionResolution() {
        // Two tables from different schemas but same table name
        String query = "SELECT * FROM schema1.ds.orders JOIN schema2.ds.orders AS o2 ON true";
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "schema1.ds.orders", List.of(new ColumnInfo("id", "INT64", "NULLABLE")),
                "schema2.ds.orders", List.of(new ColumnInfo("ref", "STRING", "NULLABLE"))
        );
        String result = DataformCteQueryBuilder.buildDryRunQuery(query, schemas, getProject());
        // Both CTEs must appear with distinct aliases
        long cteCount = result.lines()
                .filter(l -> l.contains("_df_") && l.contains("AS ("))
                .count();
        assertEquals(2, cteCount);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multiple tables in one query
    // ─────────────────────────────────────────────────────────────────────────

    public void testMultipleTablesSubstitutedInOneQuery() {
        String query = """
                SELECT a.col1, b.col2
                FROM proj.ds.table_x AS a
                JOIN proj.ds.table_y AS b ON a.id = b.id
                """;
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "proj.ds.table_x", List.of(
                        new ColumnInfo("id", "INT64", "NULLABLE"),
                        new ColumnInfo("col1", "STRING", "NULLABLE")
                ),
                "proj.ds.table_y", List.of(
                        new ColumnInfo("id", "INT64", "NULLABLE"),
                        new ColumnInfo("col2", "STRING", "NULLABLE")
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
        String query = """
            WITH
            existing_cte AS (
                SELECT * FROM _df_dep
            )
            SELECT * FROM existing_cte
            """;
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "proj.ds.dep",
                List.of(new ColumnInfo("id", "INT64", "NULLABLE"))
        );
        // Simulate that _df_dep was already substituted in the query
        String queryWithSubstitution = query.replace("_df_dep",
                "proj.ds.dep"); // pretend the original had the FQN
        String queryFqn = """
            WITH
            existing_cte AS (
                SELECT * FROM proj.ds.dep
            )
            SELECT * FROM existing_cte
            """;

        String result = DataformCteQueryBuilder.buildDryRunQuery(queryFqn, schemas, getProject());

        // Must have exactly ONE "WITH" keyword
        long withCount = result.lines()
                .filter(l -> l.trim().equalsIgnoreCase("WITH"))
                .count();
        assertEquals(1, withCount);
        assertTrue(result.contains("_df_dep AS ("));
        assertTrue(result.contains("existing_cte AS ("));
    }

}
