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
package io.github.rejeb.dataform.language.lineage.dataplex;

import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataplexGraphBuilderTest {

    private static final Instant T0 = Instant.ofEpochSecond(1_000);
    private static final String ORDERS = "bigquery:p.sales.orders";
    private static final String RAW = "bigquery:other.landing.raw_orders";
    private static final String FILES = "gs://bucket/orders/*.csv";
    private static final String CHECK = "bigquery:p.dataform_assertions.orders_not_null";

    private static final LineageNode ORDERS_NODE = new LineageNode(LineageNode.idOf("p.sales.orders"), "orders",
            "p.sales.orders", "sales", "incremental", List.of("daily"), "definitions/orders.sqlx");

    private static LineageGraph dataform() {
        return LineageGraph.builder().addNode(ORDERS_NODE).build();
    }

    private static DataplexSnapshot snapshot(List<String> entities, String... ends) {
        List<RecordedLink> links = new ArrayList<>();
        for (int i = 0; i < ends.length; i += 2) {
            links.add(new RecordedLink("links/" + i, ends[i], ends[i + 1], T0, T0));
        }
        return new DataplexSnapshot(Set.of(entities.getFirst()), new LinkedHashSet<>(entities), links, List.of(),
                Set.of(), Set.of(), Set.of(), new LinkedHashSet<>(entities), false, T0);
    }

    @Test
    void aRepositoryTableKeepsItsNodeTypeTagsAndFile() {
        LineageGraph graph = DataplexGraphBuilder.build(snapshot(List.of(ORDERS)), dataform(), Set.of());

        assertEquals(ORDERS_NODE, graph.node(ORDERS_NODE.id()));
    }

    @Test
    void aBigQueryTableUnknownToTheRepositoryIsOutsideWithProjectAndDatasetAsSchema() {
        LineageGraph graph = DataplexGraphBuilder.build(snapshot(List.of(ORDERS, RAW), RAW, ORDERS), dataform(), Set.of());

        LineageNode raw = graph.node(LineageNode.idOf("other.landing.raw_orders"));
        assertNotNull(raw);
        assertEquals(DataplexGraphBuilder.TYPE_OUTSIDE, raw.dataformType());
        assertEquals("raw_orders", raw.name());
        assertEquals("other.landing.raw_orders", raw.fullName());
        assertEquals("other.landing", raw.schema());
        assertNull(raw.fileName());
        assertEquals(Set.of(raw.id()), graph.predecessors(ORDERS_NODE.id()));
    }

    @Test
    void aTableTheRepositoryOnlyReferencesIsOutside() {
        LineageNode placeholder = new LineageNode(LineageNode.idOf("other.landing.raw_orders"), "raw_orders",
                "other.landing.raw_orders", "landing", "external", List.of(), null);
        LineageGraph repository = LineageGraph.builder().addNode(ORDERS_NODE).addNode(placeholder).build();

        LineageGraph graph = DataplexGraphBuilder.build(snapshot(List.of(ORDERS, RAW), RAW, ORDERS), repository, Set.of());

        assertEquals(DataplexGraphBuilder.TYPE_OUTSIDE, graph.node(placeholder.id()).dataformType());
    }

    @Test
    void aNonBigQueryEntityIsAnAssetWithItsKindAsSchema() {
        LineageGraph graph = DataplexGraphBuilder.build(snapshot(List.of(ORDERS, FILES), FILES, ORDERS), dataform(), Set.of());

        LineageNode files = graph.node(EntityNames.nodeIdOf(FILES));
        assertNotNull(files);
        assertEquals(DataplexGraphBuilder.TYPE_ASSET, files.dataformType());
        assertEquals("orders/*.csv", files.name());
        assertEquals(FILES, files.fullName());
        assertEquals("Cloud Storage", files.schema());
    }

    @Test
    void assertionViewsAndTheirLinksAreLeftOut() {
        String checkId = EntityNames.nodeIdOf(CHECK);

        LineageGraph graph = DataplexGraphBuilder.build(snapshot(List.of(ORDERS, CHECK), ORDERS, CHECK), dataform(),
                Set.of(checkId));

        assertNull(graph.node(checkId));
        assertTrue(graph.successors(ORDERS_NODE.id()).isEmpty());
        assertEquals(1, graph.nodes().size());
    }

    @Test
    void twoLinksBetweenTheSameTablesGiveOneEdge() {
        LineageGraph graph = DataplexGraphBuilder.build(snapshot(List.of(ORDERS, RAW), RAW, ORDERS, RAW, ORDERS),
                dataform(), Set.of());

        assertEquals(1, graph.predecessors(ORDERS_NODE.id()).size());
    }

    @Test
    void aSelfLinkGivesNoEdge() {
        LineageGraph graph = DataplexGraphBuilder.build(snapshot(List.of(ORDERS), ORDERS, ORDERS), dataform(), Set.of());

        assertTrue(graph.predecessors(ORDERS_NODE.id()).isEmpty());
        assertTrue(graph.successors(ORDERS_NODE.id()).isEmpty());
    }

    @Test
    void aCycleBuildsAndAncestorsTerminate() {
        LineageGraph graph = DataplexGraphBuilder.build(snapshot(List.of(ORDERS, RAW), RAW, ORDERS, ORDERS, RAW),
                dataform(), Set.of());

        String rawId = LineageNode.idOf("other.landing.raw_orders");
        assertTrue(graph.ancestors(ORDERS_NODE.id()).contains(rawId));
        assertTrue(graph.descendants(ORDERS_NODE.id()).contains(rawId));
    }

    @Test
    void theRootComesFirst() {
        LineageGraph graph = DataplexGraphBuilder.build(snapshot(List.of(ORDERS, RAW), RAW, ORDERS), dataform(), Set.of());

        assertEquals(ORDERS_NODE.id(), graph.nodes().iterator().next().id());
    }
}
