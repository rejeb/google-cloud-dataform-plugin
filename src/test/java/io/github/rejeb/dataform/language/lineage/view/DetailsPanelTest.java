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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;

import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DetailsPanelTest extends BasePlatformTestCase {

    private DetailsPanel showDetailsOf(LineageModel model, String id) {
        DetailsPanel panel = new DetailsPanel(getProject(), model);
        model.select(id);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        return panel;
    }

    private static List<String> visibleLabels(DetailsPanel panel) {
        return LineageViewFixtures.all(panel, JBLabel.class).stream()
                .filter(label -> isVisibleUpTo(label, panel))
                .map(JBLabel::getText).toList();
    }

    private static boolean isVisibleUpTo(java.awt.Component component, java.awt.Component root) {
        for (java.awt.Component c = component; c != null && c != root; c = c.getParent()) {
            if (!c.isVisible()) return false;
        }
        return true;
    }

    public void testNothingSelectedShowsTheEmptyState() {
        DetailsPanel panel = new DetailsPanel(getProject(), LineageViewFixtures.model());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertTrue(panel.isEmptyStateShown());
        assertEquals("Select a node to see its details", panel.getEmptyText().getText());
    }

    public void testTheMinusButtonHidesTheDetailsWithoutASelection() {
        DetailsPanel panel = new DetailsPanel(getProject(), LineageViewFixtures.model());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        AtomicBoolean hidden = new AtomicBoolean();
        panel.setHideHandler(() -> hidden.set(true));

        List<InplaceButton> buttons = LineageViewFixtures.all(panel, InplaceButton.class).stream()
                .filter(button -> isVisibleUpTo(button, panel)).toList();
        assertEquals(1, buttons.size());
        assertEquals("Hide details", buttons.getFirst().getToolTipText());
        buttons.getFirst().doClick();

        assertTrue(hidden.get());
        assertTrue(panel.isEmptyStateShown());
    }

    public void testTheEmptyStateKeepsOnlyTheMinusButton() {
        DetailsPanel panel = new DetailsPanel(getProject(), LineageViewFixtures.model());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertFalse(isVisibleUpTo(panel.toolbar().getComponent(), panel));
        assertNull(panel.headerTitle());
    }

    public void testTheHeaderShowsTheNameThenTheSchemaAndType() {
        DetailsPanel panel = showDetailsOf(LineageViewFixtures.model(), LineageViewFixtures.BETA.id());

        assertFalse(panel.isEmptyStateShown());
        assertEquals("beta", panel.headerTitle());
        assertEquals("b · view", panel.headerSubtitle());
        assertInstanceOf(panel.headerIcon(), TypeGlyphIcon.class);
    }

    public void testTheRowsAreFileSchemaAndTags() {
        DetailsPanel panel = showDetailsOf(LineageViewFixtures.model(), LineageViewFixtures.BETA.id());

        List<String> labels = visibleLabels(panel);
        assertTrue(labels.toString(), labels.containsAll(List.of("File", "Schema", "Tags")));
        assertFalse(labels.toString(), labels.contains("Full name"));
        assertFalse(labels.toString(), labels.contains("Type"));
        List<String> links = LineageViewFixtures.all(panel, ActionLink.class).stream().map(ActionLink::getText).toList();
        assertTrue(links.toString(), links.contains("definitions/beta.sqlx"));
        assertEquals("y, x", panel.valueOf("Tags"));
    }

    public void testAnActionWithoutTagsShowsADash() {
        DetailsPanel panel = showDetailsOf(LineageViewFixtures.model(), LineageViewFixtures.GAMMA.id());

        assertEquals("—", panel.valueOf("Tags"));
    }

    public void testTheDependenciesAreCountedListsWithAnEmptyNote() {
        DetailsPanel panel = showDetailsOf(LineageViewFixtures.model(), LineageViewFixtures.BETA.id());

        assertEquals(List.of(LineageViewFixtures.ALPHA.id()), items(panel.upstream()));
        assertEquals(List.of(), items(panel.downstream()));
        List<String> labels = visibleLabels(panel);
        assertTrue(labels.toString(), labels.containsAll(List.of("Upstream", "1", "Downstream", "0", "No downstream actions")));
        assertFalse(labels.toString(), labels.contains("No upstream actions"));
    }

    public void testASelectedColumnShowsItsNameAndTableOnTheirOwnRows() {
        ColumnRef column = new ColumnRef("p.analytics.orders_enriched", "customer_id");
        LineageNode table = LineageViewFixtures.node("p.analytics.orders_enriched", "analytics", "table", List.of(), null);
        LineageModel model = new LineageModel(null);
        model.setGraph(LineageGraph.builder().addNode(table).build());
        model.setColumnGraph(ColumnLineageGraph.builder().addColumn(column).build());
        DetailsPanel panel = new DetailsPanel(getProject(), model);

        model.selectColumn(column.id());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        List<String> labels = visibleLabels(panel);
        assertTrue(labels.toString(), labels.contains("Column") && labels.contains("Table"));
        List<String> fields = LineageViewFixtures.all(panel, JBTextField.class).stream().map(JBTextField::getText).toList();
        assertTrue(fields.toString(), fields.contains("customer_id"));
        assertTrue(fields.toString(), fields.contains("analytics.orders_enriched"));
        JBTextField tableField = LineageViewFixtures.all(panel, JBTextField.class).stream()
                .filter(field -> field.getText().equals("analytics.orders_enriched")).findFirst().orElseThrow();
        assertEquals("p.analytics.orders_enriched", tableField.getToolTipText());
    }

    public void testAClickSelectsTheDependency() {
        LineageModel model = LineageViewFixtures.model();
        DetailsPanel panel = showDetailsOf(model, LineageViewFixtures.BETA.id());
        JBList<String> list = panel.upstream();
        list.setSize(200, 100);

        long now = System.currentTimeMillis();
        list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_PRESSED, now, 0, 5, 5, 1, false, MouseEvent.BUTTON1));
        list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_RELEASED, now, 0, 5, 5, 1, false, MouseEvent.BUTTON1));
        list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_CLICKED, now, 0, 5, 5, 1, false, MouseEvent.BUTTON1));

        assertEquals(LineageViewFixtures.ALPHA.id(), model.selectedId());
    }

    public void testOpenSqlxRunAndFocusAreTextButtons() {
        DetailsPanel panel = showDetailsOf(LineageViewFixtures.model(), LineageViewFixtures.BETA.id());

        List<AnAction> actions = actions(panel);
        assertEquals(List.of("Open SQLX", "Run", "Focus"),
                actions.stream().map(a -> a.getTemplatePresentation().getText()).toList());
        for (AnAction action : actions) {
            assertEquals(Boolean.TRUE, action.getTemplatePresentation().getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR));
        }
    }

    public void testFocusReadsExitFocusWhileFocused() {
        LineageModel model = LineageViewFixtures.model();
        DetailsPanel panel = showDetailsOf(model, LineageViewFixtures.BETA.id());
        model.focusOn(LineageViewFixtures.BETA.id());
        AnAction focus = actions(panel).get(2);
        AnActionEvent event = TestActionEvent.createTestEvent(focus);

        focus.update(event);

        assertEquals("Exit focus", event.getPresentation().getText());
    }

    public void testADeclarationCannotBeRun() {
        LineageNode source = LineageViewFixtures.node("p.raw.orders", "raw", "declaration", List.of(), "definitions/orders.sqlx");
        LineageModel model = new LineageModel(null);
        model.setGraph(LineageGraph.builder().addNode(source).build());
        DetailsPanel panel = showDetailsOf(model, source.id());
        AnAction run = actions(panel).get(1);
        AnActionEvent event = TestActionEvent.createTestEvent(run);

        run.update(event);

        assertFalse(event.getPresentation().isVisible());
    }

    public void testTheMinusButtonHidesTheDetails() {
        DetailsPanel panel = showDetailsOf(LineageViewFixtures.model(), LineageViewFixtures.BETA.id());
        AtomicBoolean hidden = new AtomicBoolean();
        panel.setHideHandler(() -> hidden.set(true));
        InplaceButton minus = LineageViewFixtures.all(panel, InplaceButton.class).getFirst();

        assertEquals("Hide details", minus.getToolTipText());
        minus.doClick();

        assertTrue(hidden.get());
    }

    public void testALongFileNameIsCutToThePanelWidth() {
        LineageNode deep = LineageViewFixtures.node("p.analytics.orders_enriched", "analytics", "table", List.of(),
                "definitions/analytics/very/deep/folder/structure/orders_enriched_with_a_long_name.sqlx");
        LineageModel model = new LineageModel(null);
        model.setGraph(LineageGraph.builder().addNode(deep).build());
        DetailsPanel panel = showDetailsOf(model, deep.id());
        panel.setSize(300, 600);
        layout(panel);
        layout(panel);

        ActionLink link = LineageViewFixtures.all(panel, ActionLink.class).getFirst();
        java.awt.Point corner = javax.swing.SwingUtilities.convertPoint(link, link.getWidth(), 0, panel);
        assertTrue("link ends at " + corner.x, corner.x <= 300);
    }

    private static void layout(java.awt.Component c) {
        c.doLayout();
        if (c instanceof java.awt.Container k) {
            for (java.awt.Component child : k.getComponents()) layout(child);
        }
    }

    private static List<AnAction> actions(DetailsPanel panel) {
        DefaultActionGroup group = (DefaultActionGroup) panel.toolbar().getActionGroup();
        return Arrays.asList(group.getChildActionsOrStubs());
    }

    private static List<String> items(JBList<String> list) {
        return java.util.stream.IntStream.range(0, list.getModel().getSize()).mapToObj(list.getModel()::getElementAt).toList();
    }
}
