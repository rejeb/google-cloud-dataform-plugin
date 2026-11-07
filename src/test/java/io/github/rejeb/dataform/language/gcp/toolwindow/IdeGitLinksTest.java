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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.clone.DataformCloneDialogs;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.JLabel;
import java.util.ArrayList;
import java.util.List;

public class IdeGitLinksTest extends BasePlatformTestCase {

    private static final String NEW_BRANCH_ACTION = "Git.CreateNewBranch";

    private final List<String> warnings = new ArrayList<>();

    private static final class RecordingAction extends AnAction {
        private final boolean enabled;
        private int performed;

        private RecordingAction(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(enabled);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            performed++;
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(DataformGcpEvent.TOPIC, new DataformGcpEvent() {
            @Override
            public void onNotification(@NotNull String message, @NotNull NotificationType type) {
                warnings.add(type + ": " + message);
            }
        });
    }

    private void newBranchActionIs(AnAction action) {
        ActionManager manager = ActionManager.getInstance();
        AnAction original = manager.getAction(NEW_BRANCH_ACTION);
        if (original != null) {
            manager.unregisterAction(NEW_BRANCH_ACTION);
        }
        if (action != null) {
            manager.registerAction(NEW_BRANCH_ACTION, action);
        }
        Disposer.register(getTestRootDisposable(), () -> {
            if (action != null) {
                manager.unregisterAction(NEW_BRANCH_ACTION);
            }
            if (original != null) {
                manager.registerAction(NEW_BRANCH_ACTION, original);
            }
        });
    }

    public void testNoGitActionMeansNoLink() {
        newBranchActionIs(null);

        assertNull(new IdeGitLinks(getProject()).newBranch(new JLabel()));
    }

    public void testTheLinkRunsTheGitAction() {
        RecordingAction action = new RecordingAction(true);
        newBranchActionIs(action);

        new IdeGitLinks(getProject()).newBranch(new JLabel()).run();

        assertEquals(1, action.performed);
        assertEmpty(warnings);
    }

    public void testAGitActionThatCannotRunSaysSoInsteadOfDoingNothing() {
        RecordingAction action = new RecordingAction(false);
        newBranchActionIs(action);

        new IdeGitLinks(getProject()).newBranch(new JLabel()).run();

        assertEquals(0, action.performed);
        String warning = assertOneElement(warnings);
        assertTrue(warning, warning.startsWith("WARNING: "));
        assertTrue(warning, warning.contains("Directory Mappings"));
    }

    public void testTheCloneLinkFollowsTheDialog() {
        assertEquals(DataformCloneDialogs.isAvailable(), new IdeGitLinks(getProject()).cloneRepository() != null);
    }
}
