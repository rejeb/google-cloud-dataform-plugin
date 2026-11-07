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

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import javax.swing.SwingConstants;

public class ToolbarsTest extends BasePlatformTestCase {

    public void testHorizontalToolbarTargetsItsComponent() {
        JPanel target = new JPanel();

        ActionToolbar toolbar = Toolbars.horizontal("Dataform.Test", target, new NoOp());

        assertSame(target, toolbar.getTargetComponent());
        assertFalse(toolbar.getComponent().isOpaque());
    }

    public void testVerticalToolbar() {
        JPanel target = new JPanel();

        ActionToolbar toolbar = Toolbars.vertical("Dataform.Test", target, new DefaultActionGroup(new NoOp()));

        assertSame(target, toolbar.getTargetComponent());
        assertEquals(SwingConstants.VERTICAL, toolbar.getComponent() instanceof javax.swing.JComponent
                ? orientationOf(toolbar) : -1);
    }

    public void testWithTextShowsTheTextOfTheActionInToolbars() {
        NoOp action = new NoOp();

        assertSame(action, Toolbars.withText(action));

        assertEquals(Boolean.TRUE, action.getTemplatePresentation().getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR));
    }

    private static int orientationOf(ActionToolbar toolbar) {
        return toolbar instanceof com.intellij.openapi.actionSystem.impl.ActionToolbarImpl impl
                ? impl.getOrientation() : -1;
    }

    private static final class NoOp extends DumbAwareAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
        }
    }
}
