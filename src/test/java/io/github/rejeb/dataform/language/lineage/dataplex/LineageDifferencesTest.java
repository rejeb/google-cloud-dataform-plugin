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

import io.github.rejeb.dataform.language.lineage.dataplex.Difference.Kind;
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

class LineageDifferencesTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    private static final LineageNode STG = node("p.staging.stg_orders", "view");
    private static final LineageNode ORDERS = node("p.analytics.orders", "table");
    private static final LineageNode REVENUE = node("p.analytics.revenue", "table");
    private static final Set<String> WRITTEN = Set.of("p.staging", "p.analytics");

    private static LineageNode node(String fullName, String type) {
        String[] parts = fullName.split("\\.");
        return new LineageNode(LineageNode.idOf(fullName), parts[2], fullName, parts[1], type, List.of(),
                "definitions/" + parts[2] + ".sqlx");
    }

    private static String id(String fullName) {
        return LineageNode.idOf(fullName);
    }

    private static LineageGraph declared() {
        return LineageGraph.builder().addNode(STG).addNode(ORDERS).addNode(REVENUE)
                .addEdge(STG.id(), ORDERS.id()).addEdge(ORDERS.id(), REVENUE.id()).build();
    }

    private static DataplexView view(LineageGraph repository, List<String> ends, List<String> pendingEnds,
                                     Set<String> unqueriedUpstream) {
        Set<String> entities = new LinkedHashSet<>();
        entities.add(EntityNames.fqnOf(ORDERS.fullName()));
        List<RecordedLink> links = new ArrayList<>();
        for (int i = 0; i < ends.size(); i += 2) {
            entities.add(EntityNames.fqnOf(ends.get(i)));
            entities.add(EntityNames.fqnOf(ends.get(i + 1)));
            links.add(new RecordedLink("links/" + i, EntityNames.fqnOf(ends.get(i)), EntityNames.fqnOf(ends.get(i + 1)), NOW, NOW));
        }
        List<RecordedLink> pending = new ArrayList<>();
        for (int i = 0; i < pendingEnds.size(); i += 2) {
            pending.add(new RecordedLink("pending/" + i, EntityNames.fqnOf(pendingEnds.get(i)),
                    EntityNames.fqnOf(pendingEnds.get(i + 1)), NOW, NOW));
        }
        Set<String> unqueried = new LinkedHashSet<>();
        unqueriedUpstream.forEach(name -> unqueried.add(EntityNames.fqnOf(name)));
        DataplexSnapshot snapshot = new DataplexSnapshot(Set.of(EntityNames.fqnOf(ORDERS.fullName())), entities, links, pending,
                unqueried, Set.of(), Set.of(), read(entities, unqueried), false, NOW);
        return new DataplexView(snapshot, DataplexGraphBuilder.build(snapshot, repository, Set.of()),
                TimeRange.LAST_30_DAYS, new LineageScope("p", "EU"), repository, Set.of());
    }

    private static Set<String> read(Set<String> entities, Set<String> unqueried) {
        Set<String> read = new LinkedHashSet<>(entities);
        read.removeAll(unqueried);
        return read;
    }

    private static DataplexView view(String... ends) {
        return view(declared(), List.of(ends), List.of(), Set.of());
    }

    private static final String[] AGREEING = {"p.staging.stg_orders", "p.analytics.orders", "p.analytics.orders", "p.analytics.revenue"};

    private static String[] agreeingPlus(String... ends) {
        List<String> all = new ArrayList<>(List.of(AGREEING));
        all.addAll(List.of(ends));
        return all.toArray(String[]::new);
    }

    @Test
    void agreeingGraphsGiveNoDifference() {
        assertEquals(List.of(), LineageDifferences.compute(view(AGREEING), declared(), WRITTEN));
    }

    @Test
    void aRecordedSourceThatIsNotADeclaredDependencyIsRecordedNotDeclared() {
        List<Difference> differences = LineageDifferences.compute(
                view(agreeingPlus("landing.raw.orders", "p.analytics.orders")), declared(), WRITTEN);

        assertEquals(List.of(new Difference(Kind.RECORDED_NOT_DECLARED, id("landing.raw.orders"), ORDERS.id())), differences);
    }

    @Test
    void aRecordedLinkBetweenTwoRepositoryTablesThatDeclareNothingIsRecordedNotDeclared() {
        List<Difference> differences = LineageDifferences.compute(
                view(agreeingPlus("p.staging.stg_orders", "p.analytics.revenue")), declared(), WRITTEN);

        assertEquals(List.of(new Difference(Kind.RECORDED_NOT_DECLARED, STG.id(), REVENUE.id())), differences);
    }

    @Test
    void aRecordedTargetInADatasetTheRepositoryWritesToIsRecordedNotDeclared() {
        List<Difference> differences = LineageDifferences.compute(
                view(agreeingPlus("p.analytics.orders", "p.analytics.orders_backup")), declared(), WRITTEN);

        assertEquals(List.of(new Difference(Kind.RECORDED_NOT_DECLARED, ORDERS.id(), id("p.analytics.orders_backup"))),
                differences);
    }

    @Test
    void aConsumerInAnotherProjectIsNotADifference() {
        assertEquals(List.of(), LineageDifferences.compute(
                view(agreeingPlus("p.analytics.orders", "bi.dashboards.orders_extract",
                        "p.analytics.orders", "p.sandbox.orders_copy")), declared(), WRITTEN));
    }

    @Test
    void aDeclaredDependencyWithoutRecordedLinkIsDeclaredNotRecorded() {
        List<Difference> differences = LineageDifferences.compute(
                view("p.analytics.orders", "p.analytics.revenue"), declared(), WRITTEN);

        assertEquals(List.of(new Difference(Kind.DECLARED_NOT_RECORDED, STG.id(), ORDERS.id())), differences);
    }

    @Test
    void aLinkKnownButNotLoadedCountsAsRecorded() {
        DataplexView view = view(declared(), List.of("p.analytics.orders", "p.analytics.revenue"),
                List.of("p.staging.stg_orders", "p.analytics.orders"), Set.of());

        assertEquals(List.of(), LineageDifferences.compute(view, declared(), WRITTEN));
    }

    @Test
    void aTableWhoseSourcesWereNotAskedForIsNotSaidToMissThem() {
        LineageNode kpis = node("p.analytics.kpis", "table");
        LineageGraph repository = LineageGraph.builder().addNode(STG).addNode(ORDERS).addNode(REVENUE).addNode(kpis)
                .addEdge(STG.id(), ORDERS.id()).addEdge(ORDERS.id(), REVENUE.id()).addEdge(REVENUE.id(), kpis.id())
                .addEdge(STG.id(), kpis.id()).build();
        DataplexView view = view(repository, List.of(agreeingPlus("p.analytics.revenue", "p.analytics.kpis")), List.of(),
                Set.of("p.analytics.kpis"));

        assertEquals(List.of(), LineageDifferences.compute(view, repository, WRITTEN));
    }

    @Test
    void onlyRepositoryTablesOnTheLoadedGraphAreCompared() {
        LineageNode kpis = node("p.analytics.kpis", "table");
        LineageGraph repository = LineageGraph.builder().addNode(STG).addNode(ORDERS).addNode(REVENUE).addNode(kpis)
                .addEdge(STG.id(), ORDERS.id()).addEdge(ORDERS.id(), REVENUE.id()).addEdge(REVENUE.id(), kpis.id()).build();

        assertEquals(List.of(), LineageDifferences.compute(view(repository, List.of(AGREEING), List.of(), Set.of()),
                repository, WRITTEN));
    }

    @Test
    void operationsWithoutOutputAreIgnored() {
        LineageNode cleanup = node("p.analytics.cleanup", "operations");
        LineageGraph repository = LineageGraph.builder().addNode(STG).addNode(ORDERS).addNode(REVENUE).addNode(cleanup)
                .addEdge(STG.id(), ORDERS.id()).addEdge(ORDERS.id(), REVENUE.id())
                .addEdge(ORDERS.id(), cleanup.id()).addEdge(cleanup.id(), REVENUE.id()).build();

        assertEquals(List.of(), LineageDifferences.compute(view(repository, List.of(AGREEING), List.of(), Set.of()),
                repository, WRITTEN));
    }

    @Test
    void theDatasetsTheRepositoryWritesToAreThoseOfItsTablesAndViews() {
        LineageNode source = node("landing.raw.orders", "declaration");
        LineageNode check = node("p.assertions.orders_not_null", "assertion");
        LineageGraph repository = LineageGraph.builder().addNode(STG).addNode(ORDERS).addNode(source).addNode(check).build();

        assertEquals(Set.of("p.staging", "p.analytics"), LineageDifferences.writtenDatasets(repository));
    }

    @Test
    void theGraphOfTheDifferencesAddsTheDeclaredOnlyEdgesAndTheirTables() {
        DataplexView view = view("p.analytics.orders", "p.analytics.revenue");
        List<Difference> differences = LineageDifferences.compute(view, declared(), WRITTEN);
        assertNull(view.graph().node(STG.id()));

        LineageGraph shown = LineageDifferences.withDeclaredOnly(view.graph(), declared(), differences);

        assertNotNull(shown.node(STG.id()));
        assertEquals("view", shown.node(STG.id()).dataformType());
        assertTrue(shown.predecessors(ORDERS.id()).contains(STG.id()));
        assertTrue(shown.successors(ORDERS.id()).contains(REVENUE.id()));
    }

    @Test
    void withoutADeclaredOnlyDifferenceTheGraphIsTheRecordedOne() {
        DataplexView view = view(AGREEING);

        assertEquals(view.graph(), LineageDifferences.withDeclaredOnly(view.graph(), declared(), List.of()));
    }
}
