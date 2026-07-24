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

    public void testParseFailureReturnsEmptyMap() {
        Map<String, List<InputColumn>> r = analyzer.analyze("this is not sql !!!");
        assertTrue(r.isEmpty());
    }
}
