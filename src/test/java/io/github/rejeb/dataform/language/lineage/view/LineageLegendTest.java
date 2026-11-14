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
package io.github.rejeb.dataform.language.lineage.view;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBLabel;

import io.github.rejeb.dataform.language.lineage.model.LineageSource;

import java.util.List;
import java.util.Set;

public class LineageLegendTest extends BasePlatformTestCase {

    public void testTypesFollowThePrototypeOrderThenTheOthers() {
        assertEquals(List.of("table", "view", "incremental", "declaration", "assertion", "operation", "custom"),
                LineageLegend.orderedTypes(Set.of("operation", "custom", "assertion", "declaration", "incremental", "view", "table")));
    }

    public void testTheLegendListsTheNodeTypesThenTheColourHints() {
        List<JBLabel> rows = LineageViewFixtures.all(LineageLegend.panel(List.of("view", "table"), LineageSource.DATAFORM), JBLabel.class);

        assertEquals(List.of("Node types", "view", "table",
                        "Blue edges: upstream and downstream of the selected node",
                        "Yellow outline: search match, or upstream and downstream of the selected node",
                        "Coloured rows: the lineage of a selected or found column, one colour each",
                        "Blue edges then link the tables that lineage goes through"),
                rows.stream().map(JBLabel::getText).toList());
        assertInstanceOf(rows.get(1).getIcon(), TypeGlyphIcon.class);
        assertEquals("view", ((TypeGlyphIcon) rows.get(1).getIcon()).type());
    }

    public void testTheLegendExplainsColouredRowsAndNoLongerColumnEdges() {
        List<String> texts = LineageViewFixtures.all(LineageLegend.panel(List.of("table"), LineageSource.DATAFORM), JBLabel.class).stream()
                .map(JBLabel::getText).toList();

        assertTrue(texts.contains("Coloured rows: the lineage of a selected or found column, one colour each"));
        assertTrue(texts.contains("Blue edges then link the tables that lineage goes through"));
        assertTrue(texts.stream().noneMatch(text -> text.toLowerCase().contains("column edge")));
    }

    public void testTheDataplexLegendExplainsOutsideNodesExpandersAndTopLevelColumnsOnly() {
        List<JBLabel> rows = LineageViewFixtures.all(
                LineageLegend.panel(List.of("table", "outside", "asset"), LineageSource.DATAPLEX), JBLabel.class);
        List<String> texts = rows.stream().map(JBLabel::getText).toList();

        assertEquals(List.of("Node types", "table", "outside the repository, or not a BigQuery table"), texts.subList(0, 3));
        assertInstanceOf(rows.get(2).getIcon(), TypeGlyphIcon.class);
        assertTrue(texts.contains("+N on a corner: tables recorded but not shown, click to load them"));
        assertTrue(texts.contains("(i): when the table was last written"));
        assertTrue(texts.contains("Columns of a table outside the repository: top-level columns only"));
    }

    public void testTheDataformLegendSaysNothingOfKnowledgeCatalog() {
        List<String> texts = LineageViewFixtures.all(
                LineageLegend.panel(List.of("table"), LineageSource.DATAFORM), JBLabel.class).stream().map(JBLabel::getText).toList();

        assertTrue(texts.stream().noneMatch(text -> text.contains("+N") || text.contains("(i)")));
    }

    public void testTheKnowledgeCatalogLegendExplainsTheEdgesOfTheDifferences() {
        List<String> texts = LineageViewFixtures.all(
                LineageLegend.panel(List.of("table"), LineageSource.DATAPLEX), JBLabel.class).stream().map(JBLabel::getText).toList();

        int title = texts.indexOf("With Differences on");
        assertTrue(texts.toString(), title > 0);
        assertEquals(List.of("Orange edge: recorded by Knowledge Catalog, not declared in Dataform",
                "Dashed grey edge: declared in Dataform, not recorded"), texts.subList(title + 1, title + 3));
    }

    public void testTheDataformLegendSaysNothingOfTheDifferences() {
        List<String> texts = LineageViewFixtures.all(
                LineageLegend.panel(List.of("table"), LineageSource.DATAFORM), JBLabel.class).stream().map(JBLabel::getText).toList();

        assertFalse(texts.contains("With Differences on"));
    }
}
