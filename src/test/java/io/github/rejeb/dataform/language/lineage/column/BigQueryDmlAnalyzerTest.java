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

/**
 * An operation writes its output with DML rather than selecting it, so the lineage of a column
 * is whatever the statements assign to it.
 */
public class BigQueryDmlAnalyzerTest extends BasePlatformTestCase {

    private BigQuerySelectAnalyzer analyzer;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        analyzer = new BigQuerySelectAnalyzer(getProject());
    }

    private static List<String> names(List<InputColumn> inputs) {
        return inputs.stream().map(InputColumn::columnName).toList();
    }

    public void testMergeCopiesSourceColumnsThroughItsSubquery() {
        SelectAnalyzer.QueryAnalysis r = analyzer.analyzeWrites("""
                CREATE TABLE IF NOT EXISTS `p.d.t` (a STRING, b INT64);
                MERGE `p.d.t` AS target
                USING (SELECT x AS a, y AS b FROM `p.d.s`) AS source
                ON target.a = source.a
                WHEN MATCHED THEN UPDATE SET b = source.b
                WHEN NOT MATCHED THEN INSERT (a, b) VALUES (source.a, source.b)
                """, "t", List.of("a", "b"));

        Map<String, List<InputColumn>> outputs = r.outputs();
        assertEquals("a is inserted from source.a, which is x of the subquery",
                List.of("x"), names(outputs.get("a")));
        assertTrue("b is both updated and inserted from y", names(outputs.get("b")).contains("y"));
        assertTrue("the subquery's table is what the columns come from",
                r.aliases().containsValue("`p.d.s`"));
        assertFalse("the alias of the written table is not a source", r.aliases().containsKey("target"));
    }

    public void testUpdateReadsItsFromClauseAndSkipsItself() {
        SelectAnalyzer.QueryAnalysis r = analyzer.analyzeWrites("""
                UPDATE `p.d.t` AS h
                SET b = s.y, c = CURRENT_TIMESTAMP(), a = h.a
                FROM `p.d.s` AS s
                WHERE h.a = s.x
                """, "t", List.of());

        Map<String, List<InputColumn>> outputs = r.outputs();
        assertEquals(List.of("y"), names(outputs.get("b")));
        assertEquals("s", outputs.get("b").getFirst().sourceAlias());
        assertEquals(Confidence.RENAME, outputs.get("b").getFirst().kind());
        assertTrue("a column set from a constant has no input", outputs.get("c").isEmpty());
        assertTrue("a column set from the table itself has no lineage", outputs.get("a").isEmpty());
        assertEquals("`p.d.s`", r.aliases().get("s"));
    }

    public void testInsertPairsItsColumnListWithTheSelect() {
        SelectAnalyzer.QueryAnalysis r = analyzer.analyzeWrites(
                "INSERT INTO `p.d.t` (a, b) SELECT x, y FROM `p.d.s`", "t", List.of());

        assertEquals(List.of("x"), names(r.outputs().get("a")));
        assertEquals(List.of("y"), names(r.outputs().get("b")));
    }

    public void testInsertWithoutColumnListWritesTheSchemaInOrder() {
        SelectAnalyzer.QueryAnalysis r = analyzer.analyzeWrites(
                "INSERT INTO `p.d.t` SELECT x, y FROM `p.d.s`", "t", List.of("a", "b"));

        assertEquals(List.of("x"), names(r.outputs().get("a")));
        assertEquals(List.of("y"), names(r.outputs().get("b")));
    }

    public void testCreateTableAsSelectWritesTheSchemaInOrder() {
        SelectAnalyzer.QueryAnalysis r = analyzer.analyzeWrites(
                "CREATE OR REPLACE TABLE `p.d.t` AS SELECT x AS a, y FROM `p.d.s`", "t", List.of("k", "y"));

        assertEquals(List.of("x"), names(r.outputs().get("k")));
        assertEquals(List.of("y"), names(r.outputs().get("y")));
        assertFalse(r.outputs().containsKey("a"));
    }

    public void testAStatementWritingAnotherTableIsIgnored() {
        SelectAnalyzer.QueryAnalysis r = analyzer.analyzeWrites("""
                INSERT INTO `p.d.log` (a) SELECT x FROM `p.d.s`;
                UPDATE `p.d.t` AS h SET b = s.y FROM `p.d.s` AS s WHERE h.a = s.x
                """, "t", List.of());

        assertEquals(List.of("b"), List.copyOf(r.outputs().keySet()));
    }

    public void testWithoutAWriteTheLastSelectStandsForTheOutput() {
        SelectAnalyzer.QueryAnalysis r = analyzer.analyzeWrites("""
                INSERT INTO `p.d.log` (a) SELECT x FROM `p.d.s`;
                SELECT y AS b FROM `p.d.s`
                """, "t", List.of());

        assertEquals(List.of("y"), names(r.outputs().get("b")));
    }

    public void testPlainDdlYieldsNothing() {
        SelectAnalyzer.QueryAnalysis r = analyzer.analyzeWrites(
                "CREATE TABLE IF NOT EXISTS `p.d.t` (a STRING)", "t", List.of("a"));

        assertTrue(r.outputs().isEmpty());
    }
}
