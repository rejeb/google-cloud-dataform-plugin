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
package io.github.rejeb.dataform.language.lineage.model;

import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineageModelTest {

    private static LineageNode node(String full, String schema, String type, List<String> tags) {
        String name = full.substring(full.lastIndexOf('.') + 1);
        return new LineageNode(LineageNode.idOf(full), name, full, schema, type, tags, "definitions/" + name + ".sqlx");
    }

    private static String id(String full) {
        return LineageNode.idOf(full);
    }

    /** A(schema a, tag x) -> B(schema b, tag y); plus isolated C(schema a). */
    private static LineageGraph sampleGraph() {
        LineageNode a = node("p.a.alpha", "a", "table", List.of("x"));
        LineageNode b = node("p.b.beta", "b", "view", List.of("y"));
        LineageNode c = node("p.a.gamma", "a", "table", List.of());
        return LineageGraph.builder()
                .addNode(a).addNode(b).addNode(c)
                .addEdge(a.id(), b.id())
                .build();
    }

    @Test
    void schemasDefaultToAllShownAndToggleHides() {
        LineageModel m = new LineageModel(null);
        m.setGraph(sampleGraph()); // schemas: {a, b}
        assertEquals(Set.of("a", "b"), m.enabledSchemas(), "all schemas shown by default");

        m.toggleSchema("a");
        assertEquals(Set.of("b"), m.enabledSchemas(), "unchecking a hides schema a");
        assertEquals(Set.of(id("p.b.beta")), m.visibleIds(), "only schema-b node visible");

        m.toggleSchema("b");
        assertTrue(m.enabledSchemas().isEmpty(), "unchecking the last schema shows nothing");
        assertTrue(m.visibleIds().isEmpty(), "no nodes visible when no schema is enabled");

        m.toggleSchema("a");
        assertEquals(Set.of("a"), m.enabledSchemas(), "re-checking a shows only schema a");
    }

    @Test
    void tagFilterIsOr() {
        LineageModel m = new LineageModel(null);
        m.setGraph(sampleGraph());
        assertEquals(3, m.visibleIds().size(), "empty tag set shows all");
        m.toggleTag("x");
        assertEquals(Set.of(id("p.a.alpha")), m.visibleIds());
    }

    @Test
    void multipleTagsSelectUnion() {
        LineageModel m = new LineageModel(null);
        m.setGraph(sampleGraph()); // A has tag x, B has tag y, C has none
        m.toggleTag("x");
        m.toggleTag("y");
        assertEquals(Set.of(id("p.a.alpha"), id("p.b.beta")), m.visibleIds(),
                "two selected tags show the union of their nodes (OR)");
    }

    @Test
    void searchMatchesNameSubstringCaseInsensitive() {
        LineageModel m = new LineageModel(null);
        m.setGraph(sampleGraph());
        m.setSearchQuery("BET");
        assertEquals(Set.of(id("p.b.beta")), m.searchMatches());
    }

    @Test
    void searchKeepsEveryNodeAndDimsTheOthers() {
        LineageModel m = new LineageModel(null);
        m.setGraph(sampleGraph());
        m.setSearchQuery("bet");
        assertEquals(Set.of(id("p.a.alpha"), id("p.b.beta"), id("p.a.gamma")), m.visibleIds());
        assertEquals(Set.of(id("p.a.alpha"), id("p.a.gamma")), m.dimmedIds());
    }

    @Test
    void withoutSearchOrSelectionNothingIsDimmedNorMatched() {
        LineageModel m = new LineageModel(null);
        m.setGraph(sampleGraph());
        m.setHover(id("p.a.alpha"));
        assertTrue(m.dimmedIds().isEmpty());
        assertTrue(m.highlightedIds().isEmpty());
        assertTrue(m.searchMatches().isEmpty());
    }

    /** up2 -> up1 -> mid -> down1 -> down2, up1 -> down1, other -> down1, and an isolated lone. */
    private static LineageGraph chainGraph() {
        LineageGraph.Builder builder = LineageGraph.builder();
        for (String name : List.of("up2", "up1", "mid", "down1", "down2", "other", "lone")) {
            builder.addNode(node("p.s." + name, "s", "table", List.of()));
        }
        return builder
                .addEdge(id("p.s.up2"), id("p.s.up1"))
                .addEdge(id("p.s.up1"), id("p.s.mid"))
                .addEdge(id("p.s.mid"), id("p.s.down1"))
                .addEdge(id("p.s.down1"), id("p.s.down2"))
                .addEdge(id("p.s.up1"), id("p.s.down1"))
                .addEdge(id("p.s.other"), id("p.s.down1"))
                .build();
    }

    @Test
    void selectingANodeDimsEveryNodeOutsideItsLineage() {
        LineageModel m = new LineageModel(null);
        m.setGraph(chainGraph());
        m.select(id("p.s.mid"));
        assertEquals(Set.of(id("p.s.other"), id("p.s.lone")), m.dimmedIds());
    }

    @Test
    void selectingANodeHighlightsItsWholeUpstreamAndDownstream() {
        LineageModel m = new LineageModel(null);
        m.setGraph(chainGraph());
        m.select(id("p.s.mid"));
        assertEquals(Set.of(id("p.s.up2"), id("p.s.up1"), id("p.s.down1"), id("p.s.down2")), m.highlightedIds());
    }

    @Test
    void lineageEdgesAreThePathsThroughTheSelection() {
        LineageModel m = new LineageModel(null);
        m.setGraph(chainGraph());
        m.select(id("p.s.mid"));
        assertTrue(m.isSelectionLineageEdge(id("p.s.up2"), id("p.s.up1")));
        assertTrue(m.isSelectionLineageEdge(id("p.s.up1"), id("p.s.mid")));
        assertTrue(m.isSelectionLineageEdge(id("p.s.mid"), id("p.s.down1")));
        assertTrue(m.isSelectionLineageEdge(id("p.s.down1"), id("p.s.down2")));
        assertFalse(m.isSelectionLineageEdge(id("p.s.up1"), id("p.s.down1")));
        assertFalse(m.isSelectionLineageEdge(id("p.s.other"), id("p.s.down1")));
        m.select(null);
        assertFalse(m.isSelectionLineageEdge(id("p.s.up1"), id("p.s.mid")));
    }

    @Test
    void theSelectionTakesOverFromTheSearchUntilItIsCleared() {
        LineageModel m = new LineageModel(null);
        m.setGraph(chainGraph());
        m.setSearchQuery("lone");
        m.select(id("p.s.mid"));
        assertEquals(Set.of(id("p.s.other"), id("p.s.lone")), m.dimmedIds());
        assertFalse(m.highlightedIds().contains(id("p.s.lone")));
        assertEquals(Set.of(id("p.s.lone")), m.searchMatches());

        m.select(null);
        assertEquals(Set.of(id("p.s.lone")), m.highlightedIds());
        assertEquals(6, m.dimmedIds().size());
    }

    @Test
    void aLineageCoveringEveryShownNodeIsNotOutlined() {
        LineageModel m = new LineageModel(null);
        m.setGraph(sampleGraph());
        m.setScopeIds(Set.of(id("p.a.alpha"), id("p.b.beta")));
        m.select(id("p.b.beta"));
        assertTrue(m.dimmedIds().isEmpty());
        assertTrue(m.highlightedIds().isEmpty());
    }

    @Test
    void resetFiltersShowsEveryActionAgain() {
        LineageModel m = new LineageModel(null);
        m.setGraph(sampleGraph());
        m.toggleType("view");
        m.toggleSchema("a");
        m.toggleTag("x");
        m.focusOn(id("p.a.alpha"));
        m.setSearchQuery("gam");
        m.resetFilters();
        assertEquals(Set.of(id("p.a.alpha"), id("p.b.beta"), id("p.a.gamma")), m.visibleIds());
        assertTrue(m.enabledTags().isEmpty());
        assertEquals("", m.searchQuery());
        assertEquals(null, m.focusId());
    }

    @Test
    void focusRestrictsToLineageOfFocusNode() {
        LineageModel m = new LineageModel(null);
        m.setGraph(sampleGraph());
        m.focusOn(id("p.a.alpha"));
        assertEquals(Set.of(id("p.a.alpha"), id("p.b.beta")), m.visibleIds(),
                "focus keeps alpha + downstream beta, excludes isolated gamma");
    }

    @Test
    void mutatorsFireListeners() {
        LineageModel m = new LineageModel(null);
        m.setGraph(sampleGraph());
        AtomicInteger hits = new AtomicInteger();
        m.addListener(model -> hits.incrementAndGet());
        m.toggleType("table");
        m.select(id("p.b.beta"));
        assertEquals(2, hits.get());
    }

    @Test
    void typeCountsReflectGraph() {
        LineageModel m = new LineageModel(null);
        m.setGraph(sampleGraph());
        assertEquals(2, m.typeCounts().get("table"));
        assertEquals(1, m.typeCounts().get("view"));
        assertFalse(m.typeCounts().containsKey("assertion"));
    }
}
