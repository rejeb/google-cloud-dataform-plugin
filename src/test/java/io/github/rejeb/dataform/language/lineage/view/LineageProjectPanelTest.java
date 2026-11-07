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

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;

import javax.swing.JComponent;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

public class LineageProjectPanelTest extends BasePlatformTestCase {

    private LineageModel model;
    private LineageProjectPanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        model = LineageViewFixtures.model();
        panel = new LineageProjectPanel(getProject(), model);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    private ActionToolbar toolbar() {
        return LineageViewFixtures.all(panel, JComponent.class).stream()
                .filter(ActionToolbar.class::isInstance).map(ActionToolbar.class::cast)
                .filter(t -> "DataformLineageToolbar".equals(t.getPlace()))
                .findFirst().orElseThrow();
    }

    private List<AnAction> toolbarActions() {
        return Arrays.asList(toolbar().getActionGroup().getChildren(null));
    }

    private static String describe(AnAction action) {
        if (action instanceof Separator) return "|";
        if (action instanceof CustomComponentAction) return "[component]";
        return action.getTemplatePresentation().getText();
    }

    public void testTheToolbarFollowsThePrototypeOrder() {
        assertEquals(List.of("Show filters", "[component]", "[component]", "|",
                        "Layout direction", "Compact density", "Show minimap", "Show details", "|",
                        "Fit to view", "Recompile and refresh", "Legend"),
                toolbarActions().stream().map(LineageProjectPanelTest::describe).toList());
    }

    public void testTheToolbarHasNoTitle() {
        List<String> texts = LineageViewFixtures.all(panel, JBLabel.class).stream().map(JBLabel::getText).toList();
        assertFalse(texts.toString(), texts.contains("Lineage"));
    }

    public void testTheSearchFieldIsAsWideAsThePrototypeField() {
        SearchTextField search = panel.searchField();
        assertEquals(com.intellij.ui.scale.JBUIScale.scale(280), search.getPreferredSize().width);
    }

    public void testTheSearchCountsTheMatches() {
        JBLabel found = panel.foundLabel();
        assertFalse(found.isVisible());

        panel.searchField().setText("gam");

        assertTrue(found.isVisible());
        assertEquals("1 found", found.getText());
    }

    public void testResettingTheFiltersClearsTheSearchField() {
        SearchTextField search = panel.searchField();
        search.setText("gam");

        model.resetFilters();

        assertEquals("", search.getText());
    }

    public void testTheDetailsAreShownBeforeAnythingIsSelected() {
        DetailsPanel details = LineageViewFixtures.all(panel, DetailsPanel.class).getFirst();
        assertTrue(details.isVisible());
        assertTrue(details.isEmptyStateShown());
    }

    public void testTheDetailsToggleHidesAndShowsTheDetails() {
        DetailsPanel details = LineageViewFixtures.all(panel, DetailsPanel.class).getFirst();
        ToggleAction toggle = (ToggleAction) toolbarActions().stream()
                .filter(a -> "Show details".equals(a.getTemplatePresentation().getText())).findFirst().orElseThrow();

        toggle.setSelected(TestActionEvent.createTestEvent(toggle), false);
        assertFalse(details.isVisible());

        toggle.setSelected(TestActionEvent.createTestEvent(toggle), true);
        assertTrue(details.isVisible());
    }

    public void testTheDetailsMinusButtonHidesTheDetailsWithoutASelection() {
        DetailsPanel details = LineageViewFixtures.all(panel, DetailsPanel.class).getFirst();

        LineageViewFixtures.all(details, com.intellij.ui.InplaceButton.class).getFirst().doClick();

        assertFalse(details.isVisible());
    }

    public void testTheDetailsMinusButtonHidesTheDetails() {
        DetailsPanel details = LineageViewFixtures.all(panel, DetailsPanel.class).getFirst();
        model.select(LineageViewFixtures.ALPHA.id());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        LineageViewFixtures.all(details, com.intellij.ui.InplaceButton.class).getFirst().doClick();

        assertFalse(details.isVisible());
    }

    public void testClickingANodeReopensTheHiddenDetails() {
        DetailsPanel details = hiddenDetails();
        GraphCanvas canvas = sizedCanvas();

        click(canvas, pointOver(canvas, LineageViewFixtures.ALPHA.id()));

        assertTrue(details.isVisible());
        assertEquals(LineageViewFixtures.ALPHA.id(), model.selectedId());
    }

    public void testClickingTheSelectedNodeReopensTheHiddenDetails() {
        model.select(LineageViewFixtures.ALPHA.id());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        DetailsPanel details = hiddenDetails();
        GraphCanvas canvas = sizedCanvas();

        click(canvas, pointOver(canvas, LineageViewFixtures.ALPHA.id()));

        assertTrue(details.isVisible());
    }

    public void testClickingTheBackgroundKeepsTheDetailsHidden() {
        DetailsPanel details = hiddenDetails();
        GraphCanvas canvas = sizedCanvas();

        click(canvas, new Point(2, 2));

        assertFalse(details.isVisible());
    }

    private DetailsPanel hiddenDetails() {
        DetailsPanel details = LineageViewFixtures.all(panel, DetailsPanel.class).getFirst();
        LineageViewFixtures.all(details, com.intellij.ui.InplaceButton.class).getFirst().doClick();
        assertFalse(details.isVisible());
        return details;
    }

    private GraphCanvas sizedCanvas() {
        GraphCanvas canvas = LineageViewFixtures.all(panel, GraphCanvas.class).getFirst();
        canvas.setBounds(0, 0, 900, 600);
        return canvas;
    }

    private Point pointOver(GraphCanvas canvas, String id) {
        for (int y = 0; y < canvas.getHeight(); y += 5) {
            for (int x = 0; x < canvas.getWidth(); x += 5) {
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
                if (id.equals(model.hoverId())) return new Point(x, y);
            }
        }
        throw new AssertionError("No point over " + id);
    }

    private static void click(GraphCanvas canvas, Point point) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 0, InputEvent.BUTTON1_DOWN_MASK,
                point.x, point.y, 1, false, MouseEvent.BUTTON1));
    }

    public void testTheDetailsAreTheResizableSideOfASplitter() {
        DetailsPanel details = LineageViewFixtures.all(panel, DetailsPanel.class).getFirst();
        GraphCanvas canvas = LineageViewFixtures.all(panel, GraphCanvas.class).getFirst();
        ThreeComponentsSplitter splitter = LineageViewFixtures.all(panel, ThreeComponentsSplitter.class).getFirst();
        model.select(LineageViewFixtures.ALPHA.id());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        panel.setSize(1400, 700);
        layout(panel);

        assertSame(details, splitter.getLastComponent());
        assertEquals(com.intellij.ui.scale.JBUIScale.scale(300), details.getWidth());
        assertTrue(canvas.getWidth() > 700);

        splitter.setLastSize(420);
        layout(panel);
        assertEquals(420, details.getWidth());
    }

    public void testHidingTheDetailsGivesTheirSpaceToTheCanvas() {
        GraphCanvas canvas = LineageViewFixtures.all(panel, GraphCanvas.class).getFirst();
        ThreeComponentsSplitter splitter = LineageViewFixtures.all(panel, ThreeComponentsSplitter.class).getFirst();
        panel.setSize(1400, 700);
        layout(panel);
        int withDetails = canvas.getWidth();

        LineageViewFixtures.all(panel, DetailsPanel.class).getFirst().setVisible(false);
        layout(panel);

        assertEquals(splitter.getWidth(), canvas.getWidth());
        assertTrue(canvas.getWidth() > withDetails);
    }

    public void testTheCanvasHasZoomControls() {
        List<String> zoom = LineageViewFixtures.all(panel, JComponent.class).stream()
                .filter(ActionToolbar.class::isInstance).map(ActionToolbar.class::cast)
                .flatMap(t -> Arrays.stream(t.getActionGroup().getChildren(null)))
                .map(a -> a.getTemplatePresentation().getText())
                .filter(t -> t != null && t.startsWith("Zoom"))
                .toList();
        assertEquals(List.of("Zoom out", "Zoom in"), zoom);
    }

    private static void layout(java.awt.Component c) {
        c.doLayout();
        if (c instanceof java.awt.Container k) {
            for (java.awt.Component child : k.getComponents()) layout(child);
        }
    }
}
