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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.mockito.Mockito;

public class HideDataformToolsActionTest extends BasePlatformTestCase {

    private HideDataformToolsAction action;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        action = new HideDataformToolsAction();
    }

    public void testItIsNamedHideWithThePlatformShortcut() {
        String platformShortcut = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("HideActiveWindow"));

        assertEquals("Hide", action.getTemplatePresentation().getText());
        assertEquals(KeymapUtil.getKeystrokeText(javax.swing.KeyStroke.getKeyStroke("shift ESCAPE")), platformShortcut);
        assertEquals(platformShortcut, KeymapUtil.getFirstKeyboardShortcutText(action));
    }

    public void testItHidesTheToolWindowOfTheMenu() {
        ToolWindow toolWindow = Mockito.mock(ToolWindow.class);
        AnActionEvent event = TestActionEvent.createTestEvent(action,
                SimpleDataContext.getSimpleContext(PlatformDataKeys.TOOL_WINDOW, toolWindow));

        action.update(event);
        action.actionPerformed(event);

        assertTrue(event.getPresentation().isEnabledAndVisible());
        Mockito.verify(toolWindow).hide();
    }

    public void testWithoutToolWindowItIsHidden() {
        AnActionEvent event = TestActionEvent.createTestEvent(action, DataContext.EMPTY_CONTEXT);

        action.update(event);

        assertFalse(event.getPresentation().isVisible());
    }
}
