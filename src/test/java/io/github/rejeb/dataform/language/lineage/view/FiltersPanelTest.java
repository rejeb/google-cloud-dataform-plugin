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

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.scale.JBUIScale;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.ui.CollapsibleSection;

import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Set;

public class FiltersPanelTest extends BasePlatformTestCase {

    private static List<CollapsibleSection> sections(FiltersPanel panel) {
        return LineageViewFixtures.all(panel, CollapsibleSection.class);
    }

    private static List<String> names(List<FiltersPanel.Row> rows) {
        return rows.stream().map(row -> row.label().getText()).toList();
    }

    private static List<String> counts(List<FiltersPanel.Row> rows) {
        return rows.stream().map(row -> row.count().getText()).toList();
    }

    private static void settle() {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    public void testTheFiltersAreCollapsibleSections() {
        FiltersPanel panel = new FiltersPanel(LineageViewFixtures.model());

        assertEquals(List.of("Action types", "Tags", "Schemas"),
                sections(panel).stream().map(CollapsibleSection::title).toList());
    }

    public void testOnlyTheSchemasStartCollapsed() {
        List<CollapsibleSection> sections = sections(new FiltersPanel(LineageViewFixtures.model()));

        assertTrue(sections.get(0).isExpanded());
        assertTrue(sections.get(1).isExpanded());
        assertFalse(sections.get(2).isExpanded());
    }

    public void testRowsShowTheNameAndTheCountApart() {
        FiltersPanel panel = new FiltersPanel(LineageViewFixtures.model());

        assertEquals(List.of("table", "view"), names(panel.typeRows()));
        assertEquals(List.of("2", "1"), counts(panel.typeRows()));
        assertEquals(List.of("x", "y"), names(panel.tagRows()));
        assertEquals(List.of("2", "1"), counts(panel.tagRows()));
        assertEquals(List.of("a", "b"), names(panel.schemaRows()));
        assertEquals(List.of("2", "1"), counts(panel.schemaRows()));
    }

    public void testOnlyTheTypeRowsShowTheGlyph() {
        FiltersPanel panel = new FiltersPanel(LineageViewFixtures.model());

        assertInstanceOf(panel.typeRows().getFirst().label().getIcon(), TypeGlyphIcon.class);
        assertNull(panel.tagRows().getFirst().label().getIcon());
        assertNull(panel.schemaRows().getFirst().label().getIcon());
    }

    public void testTheSummariesSayHowMuchIsShown() {
        LineageModel model = LineageViewFixtures.model();
        FiltersPanel panel = new FiltersPanel(model);
        assertEquals(List.of("2 of 2", "any", "2 of 2"), panel.summaries());

        model.toggleType("view");
        model.toggleTag("x");
        model.toggleSchema("b");
        settle();

        assertEquals(List.of("1 of 2", "1 selected", "1 of 2"), panel.summaries());
    }

    public void testTheChecksMirrorTheFilters() {
        LineageModel model = LineageViewFixtures.model();
        model.toggleType("view");
        FiltersPanel panel = new FiltersPanel(model);

        assertTrue(panel.typeRows().get(0).checkBox().isSelected());
        assertFalse(panel.typeRows().get(1).checkBox().isSelected());
        assertFalse(panel.tagRows().get(0).checkBox().isSelected());
        assertTrue(panel.schemaRows().get(0).checkBox().isSelected());
    }

    public void testCheckingAnItemTogglesTheFilter() {
        LineageModel model = LineageViewFixtures.model();
        FiltersPanel panel = new FiltersPanel(model);

        panel.typeRows().get(0).checkBox().doClick();
        settle();
        panel.tagRows().get(0).checkBox().doClick();
        settle();
        panel.schemaRows().get(1).checkBox().doClick();
        settle();

        assertFalse(model.enabledTypes().contains("table"));
        assertTrue(model.enabledTags().contains("x"));
        assertFalse(model.enabledSchemas().contains("b"));
        assertFalse(panel.typeRows().get(0).checkBox().isSelected());
    }

    public void testClickingTheNameTogglesTheFilter() {
        LineageModel model = LineageViewFixtures.model();
        FiltersPanel panel = new FiltersPanel(model);
        FiltersPanel.Row row = panel.tagRows().get(1);

        row.label().dispatchEvent(new MouseEvent(row.label(), MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, 2, 2, 1, false, MouseEvent.BUTTON1));
        settle();

        assertEquals(Set.of("y"), model.enabledTags());
    }

    public void testOneLinkResetsEveryFilter() {
        LineageModel model = LineageViewFixtures.model();
        FiltersPanel panel = new FiltersPanel(model);
        model.toggleType("table");
        model.toggleTag("x");
        model.toggleSchema("b");

        List<ActionLink> links = LineageViewFixtures.all(panel, ActionLink.class);
        assertEquals(List.of("Reset all filters"), links.stream().map(ActionLink::getText).toList());
        links.getFirst().doClick();

        assertEquals(3, model.visibleIds().size());
        assertTrue(model.enabledTags().isEmpty());
    }

    public void testThePanelIsAsWideAsThePrototypeSidebar() {
        FiltersPanel panel = new FiltersPanel(LineageViewFixtures.model());

        assertEquals(JBUIScale.scale(240), panel.getPreferredSize().width);
    }
}
