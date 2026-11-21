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
package io.github.rejeb.dataform.language.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.Icon;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ActionsTest extends BasePlatformTestCase {

    public void testAnActionRunsItsWork() {
        AtomicInteger runs = new AtomicInteger();
        AnAction action = Actions.of("Do", "Do it", AllIcons.Actions.Execute, runs::incrementAndGet);

        action.actionPerformed(TestActionEvent.createTestEvent(action));

        assertEquals(1, runs.get());
        assertEquals("Do", action.getTemplatePresentation().getText());
        assertEquals("Do it", action.getTemplatePresentation().getDescription());
    }

    public void testAnEnabledWhenActionFollowsItsCondition() {
        AtomicBoolean enabled = new AtomicBoolean(false);
        AnAction action = Actions.enabledWhen("Do", "Do it", null, enabled::get, () -> {
        });

        AnActionEvent off = TestActionEvent.createTestEvent(action);
        action.update(off);
        enabled.set(true);
        AnActionEvent on = TestActionEvent.createTestEvent(action);
        action.update(on);

        assertFalse(off.getPresentation().isEnabled());
        assertTrue(off.getPresentation().isVisible());
        assertTrue(on.getPresentation().isEnabled());
        assertEquals(ActionUpdateThread.EDT, action.getActionUpdateThread());
    }

    public void testAShownWhenActionHidesWithItsCondition() {
        AtomicBoolean shown = new AtomicBoolean(false);
        AnAction action = Actions.shownWhen("Do", "Do it", null, shown::get, () -> {
        });

        AnActionEvent off = TestActionEvent.createTestEvent(action);
        action.update(off);
        shown.set(true);
        AnActionEvent on = TestActionEvent.createTestEvent(action);
        action.update(on);

        assertFalse(off.getPresentation().isVisible());
        assertFalse(off.getPresentation().isEnabled());
        assertTrue(on.getPresentation().isVisible());
        assertTrue(on.getPresentation().isEnabled());
        assertEquals(ActionUpdateThread.EDT, action.getActionUpdateThread());
    }

    public void testAToggleFollowsItsStateAndTogglesOnlyOnChange() {
        AtomicBoolean state = new AtomicBoolean(false);
        AtomicInteger toggles = new AtomicInteger();
        ToggleAction toggle = Actions.toggle("Show", "Show it", () -> AllIcons.Actions.Show, state::get,
                toggles::incrementAndGet);
        AnActionEvent event = TestActionEvent.createTestEvent(toggle);

        assertFalse(toggle.isSelected(event));
        toggle.setSelected(event, false);
        assertEquals(0, toggles.get());
        toggle.setSelected(event, true);
        assertEquals(1, toggles.get());
        state.set(true);
        assertTrue(toggle.isSelected(event));
        assertEquals(ActionUpdateThread.EDT, toggle.getActionUpdateThread());
    }

    public void testAToggleTakesItsIconOnEachUpdate() {
        AtomicReference<Icon> icon = new AtomicReference<>(AllIcons.Actions.Show);
        ToggleAction toggle = Actions.toggle("Show", "Show it", icon::get, () -> false, () -> {
        });
        icon.set(AllIcons.Actions.Execute);

        AnActionEvent event = TestActionEvent.createTestEvent(toggle);
        toggle.update(event);

        assertSame(AllIcons.Actions.Execute, event.getPresentation().getIcon());
    }
}
