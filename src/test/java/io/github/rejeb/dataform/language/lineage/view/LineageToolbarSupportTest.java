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
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.SearchTextField;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;

public class LineageToolbarSupportTest extends BasePlatformTestCase {

    public void testAComponentJoinsTheToolbarAsItself() {
        SearchTextField search = new SearchTextField();

        AnAction action = LineageToolbarSupport.component(search);

        assertSame(search, ((CustomComponentAction) action).createCustomComponent(new Presentation(), "test"));
    }

    public void testTheFocusToggleFocusesTheSelectionAndExits() {
        LineageModel model = LineageViewFixtures.model();
        ToggleAction focus = LineageToolbarSupport.focusToggle(model);
        AnActionEvent event = TestActionEvent.createTestEvent(focus);

        focus.update(event);
        assertFalse(event.getPresentation().isEnabled());

        model.select(LineageViewFixtures.ALPHA.id());
        focus.update(event);
        assertTrue(event.getPresentation().isEnabled());
        focus.setSelected(event, true);
        assertEquals(LineageViewFixtures.ALPHA.id(), model.focusId());
        assertTrue(focus.isSelected(event));

        focus.setSelected(event, false);
        assertNull(model.focusId());
    }

    public void testTheLegendIsATextButtonWithADropDownArrow() {
        AnAction legend = LineageToolbarSupport.legendAction(() -> java.util.List.of("view"));
        Presentation presentation = legend.getTemplatePresentation();

        assertEquals("Legend", presentation.getText());
        assertNull(presentation.getIcon());
        assertEquals(Boolean.TRUE, presentation.getClientProperty(com.intellij.openapi.actionSystem.ex.ActionUtil.SHOW_TEXT_IN_TOOLBAR));
        assertInstanceOf(legend, com.intellij.openapi.actionSystem.ActionGroup.class);
        assertTrue(presentation.isPopupGroup());
        assertTrue(presentation.isPerformGroup());
    }

    public void testTheLayoutDirectionIsAButtonShowingTheCurrentDirection() {
        LineageModel model = LineageViewFixtures.model();
        AnAction direction = LineageToolbarSupport.directionAction(model);
        assertFalse(direction instanceof ToggleAction);
        AnActionEvent event = TestActionEvent.createTestEvent(direction);

        direction.update(event);
        assertSame(LineageIcons.DIRECTION_LR, event.getPresentation().getIcon());

        direction.actionPerformed(event);
        direction.update(event);
        assertEquals(io.github.rejeb.dataform.language.lineage.model.Direction.TB, model.direction());
        assertSame(LineageIcons.DIRECTION_TB, event.getPresentation().getIcon());
    }

    public void testTheDensityToggleKeepsOneIconAndIsOnWhenCompact() {
        LineageModel model = LineageViewFixtures.model();
        ToggleAction density = LineageToolbarSupport.densityToggle(model);
        AnActionEvent event = TestActionEvent.createTestEvent(density);

        density.update(event);
        assertEquals("Compact density", event.getPresentation().getText());
        assertSame(LineageIcons.DENSITY, event.getPresentation().getIcon());
        assertFalse(density.isSelected(event));

        density.setSelected(event, true);
        density.update(event);
        assertTrue(density.isSelected(event));
        assertSame(LineageIcons.DENSITY, event.getPresentation().getIcon());
    }

    public void testTheZoomControlsZoomTheCanvas() {
        GraphCanvas canvas = new GraphCanvas(getProject(), LineageViewFixtures.model());
        java.util.concurrent.atomic.AtomicInteger zoom = new java.util.concurrent.atomic.AtomicInteger();
        canvas.setZoomListener(zoom::set);
        canvas.setBounds(0, 0, 800, 600);
        int fitted = zoom.get();

        java.util.List<AnAction> actions = LineageToolbarSupport.zoomActions(canvas);
        assertEquals(java.util.List.of("Zoom out", "Zoom in"),
                actions.stream().map(a -> a.getTemplatePresentation().getText()).toList());
        actions.get(1).actionPerformed(TestActionEvent.createTestEvent(actions.get(1)));
        assertEquals(Math.round(fitted * 1.2), zoom.get(), 1);
        actions.get(0).actionPerformed(TestActionEvent.createTestEvent(actions.get(0)));
        assertEquals(fitted, zoom.get(), 1);
    }
}
