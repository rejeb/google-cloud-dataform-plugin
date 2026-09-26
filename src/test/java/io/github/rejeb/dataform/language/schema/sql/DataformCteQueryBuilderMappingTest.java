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
import io.github.rejeb.dataform.language.util.MappedText;

import java.util.List;
import java.util.Map;

public class DataformCteQueryBuilderMappingTest extends BasePlatformTestCase {

    private static final Map<String, List<ColumnInfo>> ORDERS = Map.of(
            "p.d.orders", List.of(new ColumnInfo("customer_id", "STRING", "NULLABLE", null)));

    public void testAPlaceInTheUserQueryTracesBackToTheCompiledQuery() {
        String query = "SELECT custmer_id FROM p.d.orders AS o";
        MappedText sent = DataformCteQueryBuilder.buildMappedDryRunQuery(query, ORDERS, getProject());

        assertEquals(new MappedText.SourcePosition(DryRunQueryText.MAIN_QUERY, 7, true),
                sent.toSource(sent.text().indexOf("custmer_id")));
    }

    public void testTheStubAliasTracesBackToTheTableItStandsFor() {
        String query = "SELECT custmer_id FROM p.d.orders AS o";
        MappedText sent = DataformCteQueryBuilder.buildMappedDryRunQuery(query, ORDERS, getProject());

        assertEquals(new MappedText.SourcePosition(DryRunQueryText.MAIN_QUERY,
                        query.indexOf("p.d.orders"), false),
                sent.toSource(sent.text().indexOf("_df_orders AS o")));
    }

    public void testTheStubDefinitionsTraceBackToNothing() {
        MappedText sent = DataformCteQueryBuilder.buildMappedDryRunQuery(
                "SELECT 1 FROM p.d.orders", ORDERS, getProject());

        assertNull(sent.toSource(sent.text().indexOf("CAST(NULL")));
    }

    public void testTheQueryOwnWithClauseStillTracesBack() {
        String query = "-- note\nWITH x AS (SELECT customer_id FROM p.d.orders) "
                + "SELECT custmer_id FROM x";
        MappedText sent = DataformCteQueryBuilder.buildMappedDryRunQuery(query, ORDERS, getProject());

        assertEquals(DataformCteQueryBuilder.buildDryRunQuery(query, ORDERS, getProject()), sent.text());
        assertEquals(new MappedText.SourcePosition(DryRunQueryText.MAIN_QUERY,
                        query.indexOf("custmer_id"), true),
                sent.toSource(sent.text().indexOf("custmer_id")));
        assertEquals(new MappedText.SourcePosition(DryRunQueryText.MAIN_QUERY, 0, true),
                sent.toSource(sent.text().indexOf("-- note")));
    }

    public void testANoStubQueryIsTheCompiledQueryItself() {
        MappedText sent = DataformCteQueryBuilder.buildMappedDryRunQuery("SELECT 1", Map.of(), getProject());

        assertEquals("SELECT 1", sent.text());
        assertEquals("SELECT 1", sent.sourceText(DryRunQueryText.MAIN_QUERY));
        assertEquals(new MappedText.SourcePosition(DryRunQueryText.MAIN_QUERY, 7, true), sent.toSource(7));
    }

    public void testATableReadWithoutAliasKeepsTheNameItIsReadBy() {
        String query = "SELECT orders.customer_id FROM p.d.orders JOIN p.d.orders AS o2 ON true";
        MappedText sent = DataformCteQueryBuilder.buildMappedDryRunQuery(query, ORDERS, getProject());

        assertTrue(sent.text(), sent.text().contains("FROM _df_orders AS orders JOIN _df_orders AS o2 ON true"));
    }

    public void testATableNamedLikeAReservedWordKeepsItsNameQuoted() {
        Map<String, List<ColumnInfo>> order = Map.of(
                "p.d.order", List.of(new ColumnInfo("customer_id", "STRING", "NULLABLE", null)));
        String query = "SELECT customer_id FROM `p.d.order`";
        MappedText sent = DataformCteQueryBuilder.buildMappedDryRunQuery(query, order, getProject());

        assertTrue(sent.text(), sent.text().contains(" AS `order`"));
        assertFalse(sent.text(), sent.text().contains(" AS order"));
    }

    public void testTheNameGivenBackTracesToTheTable() {
        String query = "SELECT orders.customer_id FROM p.d.orders";
        MappedText sent = DataformCteQueryBuilder.buildMappedDryRunQuery(query, ORDERS, getProject());

        assertEquals(new MappedText.SourcePosition(DryRunQueryText.MAIN_QUERY, query.indexOf("p.d.orders"), false),
                sent.toSource(sent.text().indexOf("AS orders")));
    }

    public void testATableAliasedWithoutAsKeepsItsAlias() {
        String query = "SELECT o.customer_id FROM p.d.orders o WHERE true";
        MappedText sent = DataformCteQueryBuilder.buildMappedDryRunQuery(query, ORDERS, getProject());

        assertTrue(sent.text(), sent.text().contains("FROM _df_orders o WHERE true"));
    }

    public void testATableNameReadAsAPathIsNotGivenAName() {
        String query = "SELECT p.d.orders.customer_id FROM p.d.orders AS x";
        MappedText sent = DataformCteQueryBuilder.buildMappedDryRunQuery(query, ORDERS, getProject());

        assertTrue(sent.text(), sent.text().contains("SELECT _df_orders.customer_id FROM _df_orders AS x"));
    }
}
