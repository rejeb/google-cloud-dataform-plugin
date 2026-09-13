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
package io.github.rejeb.dataform.language.compilation;

import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A Dataform project can change without the IDE writing a line of it: a rollback, a checkout, a
 * pull. What the editor resolves against is read from a compilation, so unless such a change asks
 * for a new one, the project goes on being understood as it was before the change.
 */
public class DataformExternalChangeListenerTest extends BasePlatformTestCase {

    private final AtomicInteger scheduled = new AtomicInteger();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ServiceContainerUtil.replaceService(getProject(), DataformAutoCompileService.class,
                new DataformAutoCompileService() {
                    @Override
                    public void scheduleCompile() {
                        scheduled.incrementAndGet();
                    }

                    @Override
                    public void scheduleCompileAfterEdit() {
                    }
                }, getTestRootDisposable());
    }

    private VirtualFile action() {
        myFixture.addFileToProject("workflow_settings.yaml", "defaultProject: p\n");
        PsiFile file = myFixture.addFileToProject("definitions/orders.sqlx",
                "config { type: \"table\" }\n\nSELECT 1 AS order_id\n");
        return file.getVirtualFile();
    }

    private static List<VFileEvent> refreshOf(VirtualFile file) {
        return List.of(new VFileContentChangeEvent(VFileEvent.REFRESH_REQUESTOR, file, 1L, 2L));
    }

    private static List<VFileEvent> saveOf(VirtualFile file) {
        return List.of(new VFileContentChangeEvent("the plugin itself", file, 1L, 2L));
    }

    public void testAChangeFoundOnDiskAsksForANewCompilation() {
        VirtualFile file = action();

        AsyncFileListener.ChangeApplier applier =
                new DataformExternalChangeListener().prepareChange(refreshOf(file));

        assertNotNull("a source changed behind the IDE is worth a compilation", applier);
        applier.afterVfsChange();
        assertEquals("exactly one compilation is asked for", 1, scheduled.get());
    }

    public void testWhatTheIdeWroteItselfAsksForNothing() {
        VirtualFile file = action();

        assertNull("compiling saves the sources, and that must not compile again",
                new DataformExternalChangeListener().prepareChange(saveOf(file)));
    }

    public void testTheJavaScriptOfADependencyAsksForNothing() {
        myFixture.addFileToProject("workflow_settings.yaml", "defaultProject: p\n");
        VirtualFile dependency =
                myFixture.addFileToProject("node_modules/@dataform/core/index.js", "module.exports={}")
                        .getVirtualFile();

        assertNull("an npm install must not cost a compilation of the whole project",
                new DataformExternalChangeListener().prepareChange(refreshOf(dependency)));
    }

    public void testAFileOfAnotherKindAsksForNothing() {
        myFixture.addFileToProject("workflow_settings.yaml", "defaultProject: p\n");
        VirtualFile readme = myFixture.addFileToProject("definitions/README.md", "hi")
                .getVirtualFile();

        assertNull("only the sources of the project decide what it compiles to",
                new DataformExternalChangeListener().prepareChange(refreshOf(readme)));
    }
}
