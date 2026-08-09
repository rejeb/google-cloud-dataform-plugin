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
package io.github.rejeb.dataform.language.lineage.column;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;
import java.util.Map;

public class BigQuerySelectAnalyzerTest extends BasePlatformTestCase {

    private BigQuerySelectAnalyzer analyzer;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        analyzer = new BigQuerySelectAnalyzer(getProject());
    }

    public void testDirectReference() {
        Map<String, List<InputColumn>> r = analyzer.analyze("SELECT id FROM p.d.src");
        assertEquals(1, r.get("id").size());
        InputColumn in = r.get("id").get(0);
        assertEquals("id", in.columnName());
        assertEquals(Confidence.DIRECT, in.kind());
    }

    public void testRename() {
        Map<String, List<InputColumn>> r = analyzer.analyze("SELECT amount AS amt FROM p.d.src");
        assertTrue(r.containsKey("amt"));
        InputColumn in = r.get("amt").get(0);
        assertEquals("amount", in.columnName());
        assertEquals(Confidence.RENAME, in.kind());
    }

    public void testExpressionOverTwoColumns() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT (a + b) AS total FROM p.d.src");
        List<InputColumn> ins = r.get("total");
        assertEquals(2, ins.size());
        assertTrue(ins.stream().allMatch(i -> i.kind() == Confidence.DERIVED));
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("a")));
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("b")));
    }

    public void testStarProducesStarInput() {
        Map<String, List<InputColumn>> r = analyzer.analyze("SELECT * FROM p.d.src");
        assertEquals(1, r.size());
        InputColumn in = r.values().iterator().next().get(0);
        assertTrue(in.star());
        assertEquals(Confidence.STAR, in.kind());
    }

    public void testQualifiedStarCarriesAlias() {
        Map<String, List<InputColumn>> r = analyzer.analyze("SELECT o.* FROM p.d.other o");
        InputColumn in = r.values().iterator().next().get(0);
        assertTrue(in.star());
        assertEquals("o", in.sourceAlias());
    }

    public void testCteRenameChaining() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "WITH cte AS (SELECT amount AS amt FROM p.d.src) SELECT amt AS total FROM cte");
        InputColumn in = r.get("total").get(0);
        assertEquals("amount", in.columnName());
    }

    public void testUnionAllMapsPositionally() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT a AS x FROM p.d.t1 UNION ALL SELECT b AS x FROM p.d.t2");
        List<InputColumn> ins = r.get("x");
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("a")));
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("b")));
    }

    public void testFunctionCallUsesArgumentsNotFunctionName() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT SAFE_CAST(goalsScored AS INT64) AS goalsScored FROM p.d.src");
        List<InputColumn> ins = r.get("goalsScored");
        assertEquals(1, ins.size());
        assertEquals("goalsScored", ins.get(0).columnName());
        assertEquals(Confidence.DERIVED, ins.get(0).kind());
    }

    public void testNestedFunctionArgsCollectedNotKeywords() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT IF(savePercentage <> \"-\", TRUE, FALSE) AS flag FROM p.d.src");
        List<InputColumn> ins = r.get("flag");
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("savePercentage")));
        assertTrue(ins.stream().noneMatch(i -> i.columnName().equalsIgnoreCase("IF")));
        assertTrue(ins.stream().noneMatch(i -> i.columnName().equalsIgnoreCase("TRUE")));
    }

    public void testNiladicFunctionHasNoInputColumns() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT CURRENT_TIMESTAMP() AS ts FROM p.d.src");
        assertTrue(r.getOrDefault("ts", List.of()).isEmpty());
    }

    public void testComputedOutputColumnIsRegisteredWithoutInputs() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT CURRENT_DATE AS PARTITION_DATE FROM p.d.src");
        assertTrue("computed column must still be an output", r.containsKey("PARTITION_DATE"));
        assertTrue(r.get("PARTITION_DATE").isEmpty());
    }

    public void testAggregateUsesArgumentColumn() {
        Map<String, List<InputColumn>> r = analyzer.analyze("SELECT SUM(x) AS s FROM p.d.src");
        List<InputColumn> ins = r.get("s");
        assertEquals(1, ins.size());
        assertEquals("x", ins.get(0).columnName());
        assertTrue(ins.stream().noneMatch(i -> i.columnName().equalsIgnoreCase("SUM")));
    }

    public void testAnalyzeQueryReturnsOutputsAndAliasesFromOneParse() {
        SelectAnalyzer.QueryAnalysis result = analyzer.analyzeQuery("SELECT amount AS amt FROM p.d.src s");
        assertTrue(result.outputs().containsKey("amt"));
        assertEquals("amount", result.outputs().get("amt").get(0).columnName());
        assertEquals("p.d.src", result.aliases().get("s"));
    }

    public void testDerivedTableInlinesInnerExpression() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT full_name FROM (SELECT CONCAT(a, b) AS full_name FROM p.d.src)");
        List<InputColumn> ins = r.get("full_name");
        assertEquals(2, ins.size());
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("a")));
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("b")));
    }

    public void testAliasedDerivedTableInlinesInnerExpression() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT x.full_name FROM (SELECT CONCAT(a, b) AS full_name FROM p.d.src) x");
        List<InputColumn> ins = r.get("full_name");
        assertEquals(2, ins.size());
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("a")));
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("b")));
    }

    public void testDerivedTableExposesInnerFromSource() {
        Map<String, String> aliases = analyzer.fromAliases(
                "SELECT full_name FROM (SELECT CONCAT(a, b) AS full_name FROM p.d.src) x");
        assertTrue("inner source must be resolvable for dependency matching",
                aliases.containsValue("p.d.src"));
    }

    public void testDerivedTableRenameChaining() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT amt AS total FROM (SELECT amount AS amt FROM p.d.src) s");
        assertEquals("amount", r.get("total").get(0).columnName());
    }

    public void testCteDeclaredInsideDerivedTableIsResolved() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT v FROM (WITH b AS (SELECT CONCAT(a, c) AS v FROM p.d.src) SELECT v FROM b)");
        List<InputColumn> ins = r.get("v");
        assertEquals(2, ins.size());
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("a")));
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("c")));
    }

    public void testCteDeclaredInsideDerivedTableExposesItsSource() {
        Map<String, String> aliases = analyzer.fromAliases(
                "SELECT v FROM (WITH b AS (SELECT CONCAT(a, c) AS v FROM p.d.src) SELECT v FROM b)");
        assertTrue(aliases.containsValue("p.d.src"));
    }

    public void testCteReferencedThroughAnAliasIsInlined() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "WITH b AS (SELECT amount AS amt FROM p.d.src) SELECT x.amt AS total FROM b x");
        assertEquals("amount", r.get("total").get(0).columnName());
    }

    public void testScalarSubqueryDoesNotProduceTableNameColumn() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT (SELECT MAX(z) FROM p.d.other) AS v FROM p.d.src");
        List<InputColumn> ins = r.get("v");
        assertTrue("the subquery table name must not become an input column",
                ins.stream().noneMatch(i -> i.columnName().equals("other")));
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("z")));
    }

    public void testStructConstructorFieldsGetTheirOwnOutputPaths() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT STRUCT(CONCAT(a, b) AS full_name, c AS nick) AS s FROM p.d.src");
        assertTrue(r.containsKey("s.full_name"));
        assertTrue(r.containsKey("s.nick"));
        assertEquals(2, r.get("s.full_name").size());
        assertEquals("c", r.get("s.nick").get(0).columnName());
    }

    public void testPivotCreatesOneOutputColumnPerPivotValue() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT * FROM p.d.src PIVOT(SUM(sales) FOR quarter IN ('Q1', 'Q2'))");
        assertTrue(r.containsKey("Q1"));
        assertTrue(r.containsKey("Q2"));
        assertTrue(r.get("Q1").stream().anyMatch(i -> i.columnName().equals("sales")));
        assertTrue("the pivot key feeds every generated column",
                r.get("Q1").stream().anyMatch(i -> i.columnName().equals("quarter")));
    }

    public void testPivotAggregateAliasPrefixesTheOutputName() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT * FROM p.d.src PIVOT(SUM(sales) AS total FOR quarter IN ('Q1'))");
        assertTrue(r.containsKey("total_Q1"));
    }

    public void testPivotValueAliasNamesTheOutputColumn() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT * FROM p.d.src PIVOT(SUM(sales) FOR quarter IN ('Q1' AS first_quarter))");
        assertTrue(r.containsKey("first_quarter"));
    }

    public void testNumericPivotValueIsPrefixedLikeBigQuery() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT * FROM p.d.src PIVOT(SUM(sales) FOR year IN (2024))");
        assertTrue(r.containsKey("_2024"));
    }

    public void testPivotColumnSelectedByNameResolvesToItsInputs() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT product, Q1 FROM p.d.src PIVOT(SUM(sales) FOR quarter IN ('Q1'))");
        assertTrue(r.get("Q1").stream().anyMatch(i -> i.columnName().equals("sales")));
        assertEquals("product", r.get("product").get(0).columnName());
    }

    public void testPivotClausesAreNotRegisteredAsTableSources() {
        Map<String, String> aliases = analyzer.fromAliases(
                "SELECT * FROM p.d.src PIVOT(SUM(sales) FOR quarter IN ('Q1', 'Q2'))");
        assertFalse(aliases.containsKey("SUM"));
        assertFalse(aliases.containsKey("sales"));
        assertFalse(aliases.containsKey("quarter"));
        assertTrue(aliases.containsValue("p.d.src"));
    }

    public void testUnpivotMapsValueAndKeyColumnsToTheUnpivotedColumns() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT * FROM p.d.src UNPIVOT(sales FOR quarter IN (Q1, Q2))");
        assertTrue(r.get("sales").stream().anyMatch(i -> i.columnName().equals("Q1")));
        assertTrue(r.get("sales").stream().anyMatch(i -> i.columnName().equals("Q2")));
        assertTrue(r.get("quarter").stream().anyMatch(i -> i.columnName().equals("Q1")));
        assertTrue(r.get("quarter").stream().anyMatch(i -> i.columnName().equals("Q2")));
    }

    public void testPivotOverDerivedTableChainsToUnderlyingColumns() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT * FROM (SELECT CONCAT(a, b) AS sales, quarter FROM p.d.src) "
                        + "PIVOT(SUM(sales) FOR quarter IN ('Q1'))");
        List<InputColumn> ins = r.get("Q1");
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("a")));
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("b")));
    }

    public void testPivotSourceIsStillResolvableForDependencyMatching() {
        Map<String, String> aliases = analyzer.fromAliases(
                "SELECT * FROM (SELECT sales, quarter FROM p.d.src) PIVOT(SUM(sales) FOR quarter IN ('Q1'))");
        assertTrue(aliases.containsValue("p.d.src"));
    }

    public void testParseFailureReturnsEmptyMap() {
        Map<String, List<InputColumn>> r = analyzer.analyze("this is not sql !!!");
        assertTrue(r.isEmpty());
    }

    public void testQualifiedNestedStructFieldKeepsThePathToTheSchemaLeaf() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT t.payload.amount AS amount FROM p.d.src t");
        List<InputColumn> ins = r.get("amount");
        assertNotNull("the output column must be produced", ins);
        assertEquals(1, ins.size());
        InputColumn in = ins.get(0);
        assertEquals("the input must name the schema leaf path, not only the last identifier",
                "payload.amount", in.columnName());
        assertEquals("the qualifier must be the table alias, not the struct column",
                "t", in.sourceAlias());
    }

    public void testUnqualifiedNestedStructFieldKeepsThePathToTheSchemaLeaf() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT payload.amount AS amount FROM p.d.src");
        List<InputColumn> ins = r.get("amount");
        assertNotNull("the output column must be produced", ins);
        assertEquals(1, ins.size());
        InputColumn in = ins.get(0);
        assertEquals("the input must name the schema leaf path", "payload.amount", in.columnName());
        assertNull("a struct container is not a table alias", in.sourceAlias());
    }

    public void testNestedStructFieldInAnExpressionKeepsItsPath() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT CONCAT(payload.first, payload.last) AS full_name FROM p.d.src");
        List<InputColumn> ins = r.get("full_name");
        assertNotNull(ins);
        assertTrue("expression inputs must name the schema leaf paths",
                ins.stream().anyMatch(i -> i.columnName().equals("payload.first")));
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("payload.last")));
    }

    public void testUnnestExposesItsSourceTableForDependencyMatching() {
        Map<String, String> aliases = analyzer.fromAliases(
                "SELECT item.price AS price FROM p.d.src t, UNNEST(t.items) AS item");
        assertTrue("the table read next to the UNNEST must stay resolvable",
                aliases.containsValue("p.d.src"));
    }

    public void testUnnestedFieldIsAttributedToTheArrayColumn() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT item.price AS price FROM p.d.src t, UNNEST(t.items) AS item");
        List<InputColumn> ins = r.get("price");
        assertNotNull("the output column must be produced", ins);
        assertEquals(1, ins.size());
        assertEquals("an unnested field must resolve to the array column path",
                "items.price", ins.get(0).columnName());
    }

    public void testUnnestedScalarArrayIsAttributedToTheArrayColumn() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT tag AS tag FROM p.d.src t, UNNEST(t.tags) AS tag");
        List<InputColumn> ins = r.get("tag");
        assertNotNull("the output column must be produced", ins);
        assertEquals(1, ins.size());
        assertEquals("an unnested scalar array element must resolve to the array column",
                "tags", ins.get(0).columnName());
    }

    public void testStarExceptDoesNotTurnExcludedColumnsIntoInputs() {
        Map<String, List<InputColumn>> r = analyzer.analyze("SELECT * EXCEPT (secret) FROM p.d.src");
        assertTrue("an excluded column must never become an input column",
                r.values().stream().flatMap(List::stream)
                        .noneMatch(i -> "secret".equals(i.columnName())));
        assertTrue("the item must still be recognised as a star",
                r.values().stream().flatMap(List::stream).anyMatch(InputColumn::star));
    }

    public void testPsiContractSmokeTest() {
        Map<String, List<InputColumn>> r = analyzer.analyze("SELECT a FROM p.d.src");
        assertFalse("the BigQuery SQL PSI class names this analyzer matches on have changed; "
                + "see the type constants at the top of BigQuerySelectAnalyzer", r.isEmpty());
    }

    public void testParenthesizedUnionBranchesAreBothAnalyzed() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "(SELECT a AS v FROM p.d.one) UNION ALL (SELECT b AS v FROM p.d.two)");
        List<InputColumn> ins = r.get("v");
        assertNotNull("both union branches must produce the output column", ins);
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("a")));
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("b")));
    }

    public void testStarOverDerivedTableExpandsComputedColumns() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT * FROM (SELECT CONCAT(a, b) AS joined FROM p.d.src)");
        List<InputColumn> ins = r.get("joined");
        assertNotNull("a column computed in a derived table must survive the outer star", ins);
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("a")));
        assertTrue(ins.stream().anyMatch(i -> i.columnName().equals("b")));
    }

    public void testStarOverParenthesizedUnionExpandsBothBranches() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "WITH cte AS (SELECT o.id AS id, o.n AS n FROM p.d.one o) "
                        + "SELECT * FROM ("
                        + "(SELECT CONCAT(c.id, c.id) AS joined FROM cte c) "
                        + "UNION ALL "
                        + "(SELECT CONCAT(d.n) AS joined FROM cte d))");
        List<InputColumn> ins = r.get("joined");
        assertNotNull("the computed column must be resolved through the star", ins);
        assertTrue("the first branch must chain through the CTE",
                ins.stream().anyMatch(i -> i.columnName().equals("id")));
        assertTrue("the second branch must chain through the CTE",
                ins.stream().anyMatch(i -> i.columnName().equals("n")));
    }

    public void testStarOverCteExpandsItsColumns() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "WITH cte AS (SELECT amount AS amt FROM p.d.src) SELECT * FROM cte");
        List<InputColumn> ins = r.get("amt");
        assertNotNull("a CTE column must survive the outer star", ins);
        assertEquals("amount", ins.get(0).columnName());
    }

    public void testStarOverPlainTableStaysAStar() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "WITH cte AS (SELECT a AS a FROM p.d.one) SELECT * FROM p.d.src");
        assertTrue("a plain table has no known columns, the star must be kept",
                r.values().stream().flatMap(List::stream).anyMatch(InputColumn::star));
    }

    public void testStarOverCteJoinedToPlainTableStaysAStar() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "WITH cte AS (SELECT a AS a FROM p.d.one) "
                        + "SELECT * FROM cte c JOIN p.d.src s ON c.a = s.a");
        assertTrue("the plain table side is unknown, so the star must be kept",
                r.values().stream().flatMap(List::stream).anyMatch(InputColumn::star));
    }

    public void testStarReplaceIsStillRecognisedAsAStar() {
        Map<String, List<InputColumn>> r = analyzer.analyze(
                "SELECT * REPLACE (amount * 2 AS amount) FROM p.d.src");
        assertTrue("the item must still be recognised as a star",
                r.values().stream().flatMap(List::stream).anyMatch(InputColumn::star));
    }
}
