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
package io.github.rejeb.dataform.language.gcp.toolwindow.action;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.toolwindow.WorkspaceSelection;

import javax.swing.JComponent;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.List;

public class PickerButtonTest extends BasePlatformTestCase {

    private static final DataformRepositoryConfig LONG = new DataformRepositoryConfig("long-id",
            "Sales analytics production repository of the finance team", "p", "sales", "europe-west1");

    private RepositoryPickerAction action;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
        settings.saveAllConfigs(List.of(LONG));
        settings.setActiveRepositoryId(LONG.repositoryConfigId());
        WorkspaceSelection selection = new WorkspaceSelection(getProject(), () -> { }, getTestRootDisposable());
        action = new RepositoryPickerAction(selection, new ManageRepositoriesAction(getProject(), () -> { }));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
            settings.saveAllConfigs(List.of());
            settings.setActiveRepositoryId(null);
            settings.setSelectedWorkspaceId(null);
        } finally {
            super.tearDown();
        }
    }

    public void testThePickerIsAFlatTextButtonOpeningItsChoices() {
        Presentation presentation = updatedPresentation();

        JComponent component = action.createCustomComponent(presentation, ActionPlaces.TOOLWINDOW_CONTENT);

        assertInstanceOf(component, PickerButton.class);
        assertTrue(presentation.isPopupGroup());
        assertFalse(presentation.isPerformGroup());
    }

    public void testTheButtonShowsAChevronSixPixelsAfterTheLabel() {
        PickerButton button = button();

        assertSame(DataformIcons.CHEVRON_DOWN, button.getDownArrowIcon());
        assertEquals(JBUI.scale(6), button.iconTextSpace());
        assertEquals(JBUI.insets(0, 8), button.getMargins());
    }

    public void testTheNameIsBoldAndTwoPointsBiggerThanTheIdeFont() {
        PickerButton button = button();

        Font expected = JBFont.label().biggerOn(2).asBold();
        assertTrue(button.getFont().isBold());
        assertEquals(expected.getSize2D(), button.getFont().getSize2D());
        assertEquals(expected.getFamily(), button.getFont().getFamily());
    }

    public void testTheFontSurvivesALookAndFeelChange() {
        PickerButton button = button();

        button.updateUI();

        assertTrue(button.getFont().isBold());
        assertEquals(JBFont.label().biggerOn(2).getSize2D(), button.getFont().getSize2D());
    }

    public void testTheButtonIsTallerThanAToolbarButton() {
        PickerButton button = button();

        assertEquals(JBUI.scale(PickerButton.HEIGHT), button.getPreferredSize().height);
        assertTrue(JBUI.scale(PickerButton.HEIGHT) > ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height);
    }

    public void testALongNameIsCutWithAnEllipsis() {
        PickerButton button = button();

        String shown = button.getText();
        FontMetrics metrics = button.getFontMetrics(JBFont.label());

        assertTrue(shown, shown.endsWith("…"));
        assertTrue(shown, LONG.displayName().startsWith(shown.substring(0, shown.length() - 1)));
        assertTrue(metrics.stringWidth(shown) <= JBUI.scale(RepositoryPickerAction.MAX_LABEL_WIDTH));
    }

    public void testTheMainBranchIsShownInFullDespiteTheBiggerFont() {
        WorkspaceSelection selection = new WorkspaceSelection(getProject(), () -> { }, getTestRootDisposable());
        selection.setWorkspaces(List.of());
        WorkspacePickerAction workspaces = new WorkspacePickerAction(selection, new ManageRepositoriesAction(getProject(), () -> { }));
        AnActionEvent event = TestActionEvent.createTestEvent(workspaces);
        workspaces.update(event);

        PickerButton button = (PickerButton) workspaces.createCustomComponent(event.getPresentation(), ActionPlaces.TOOLWINDOW_CONTENT);

        assertEquals(WorkspaceSelection.MAIN_BRANCH, button.getText());
    }

    public void testTheTooltipNamesThePicker() {
        assertEquals("Repository", updatedPresentation().getDescription());
    }

    private PickerButton button() {
        return (PickerButton) action.createCustomComponent(updatedPresentation(), ActionPlaces.TOOLWINDOW_CONTENT);
    }

    private Presentation updatedPresentation() {
        AnActionEvent event = TestActionEvent.createTestEvent(action);
        action.update(event);
        return event.getPresentation();
    }
}
