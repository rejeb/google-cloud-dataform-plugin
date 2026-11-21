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

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PickerActionTest extends BasePlatformTestCase {

    private final AnAction first = action("first");
    private final AnAction second = action("second");
    private final PickerAction picker = new PickerAction("Things", 100) {
        @Override
        public @NotNull DefaultActionGroup choices() {
            return new DefaultActionGroup(first, second);
        }
    };

    public void testTheTemplateIsAPopupGroupWithTheDescription() {
        Presentation template = picker.getTemplatePresentation();

        assertTrue(template.isPopupGroup());
        assertFalse(template.isDisableGroupIfEmpty());
        assertEquals("Things", template.getDescription());
    }

    public void testTheChildrenAreTheChoices() {
        assertEquals(List.of(first, second), List.of(picker.getChildren(null)));
    }

    public void testItUpdatesInTheBackground() {
        assertEquals(ActionUpdateThread.BGT, picker.getActionUpdateThread());
    }

    public void testItShowsAsAPickerButton() {
        assertInstanceOf(picker.createCustomComponent(picker.getTemplatePresentation().clone(), "place"), PickerButton.class);
    }

    private static AnAction action(String text) {
        return new DumbAwareAction(text) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
            }
        };
    }
}
