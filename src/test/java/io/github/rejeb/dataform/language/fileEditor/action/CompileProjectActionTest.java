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
package io.github.rejeb.dataform.language.fileEditor.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.AnimatedIcon;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.compilation.DataformBuildContext;

public class CompileProjectActionTest extends BasePlatformTestCase {

    public void testCompileShowsTheHammerWhenNoBuildRuns() {
        Presentation presentation = updated(new CompileProjectAction(getProject()));

        assertSame(DataformIcons.COMPILE, presentation.getIcon());
        assertTrue(presentation.isEnabled());
    }

    public void testCompileShowsASpinnerWhileABuildRuns() {
        DataformBuildContext build = new DataformBuildContext(getProject());
        assertTrue(build.waitAndStart());
        try {
            Presentation presentation = updated(new CompileProjectAction(getProject()));

            assertSame(AnimatedIcon.Default.INSTANCE, presentation.getIcon());
            assertSame(AnimatedIcon.Default.INSTANCE, presentation.getDisabledIcon());
            assertFalse(presentation.isEnabled());
        } finally {
            build.finished(true, "Build finished");
        }
    }

    public void testCompileShowsTheHammerAgainOnceTheBuildFinished() {
        CompileProjectAction action = new CompileProjectAction(getProject());
        AnActionEvent e = TestActionEvent.createTestEvent(action);
        DataformBuildContext build = new DataformBuildContext(getProject());
        assertTrue(build.waitAndStart());
        action.update(e);
        build.finished(true, "Build finished");

        action.update(e);

        assertSame(DataformIcons.COMPILE, e.getPresentation().getIcon());
        assertTrue(e.getPresentation().isEnabled());
    }

    private static Presentation updated(CompileProjectAction action) {
        AnActionEvent e = TestActionEvent.createTestEvent(action);
        action.update(e);
        return e.getPresentation();
    }
}
