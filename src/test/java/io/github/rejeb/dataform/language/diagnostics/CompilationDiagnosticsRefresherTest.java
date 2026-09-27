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
package io.github.rejeb.dataform.language.diagnostics;

import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.diagnostics.compile.BlankOutputCompiler;
import io.github.rejeb.dataform.language.diagnostics.compile.CompiledGraphErrors;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CompilationDiagnosticsRefresherTest extends BasePlatformTestCase {

    @Override
    protected void tearDown() throws Exception {
        try {
            CompiledGraphErrors.clear(getProject());
        } finally {
            super.tearDown();
        }
    }

    public void testARecompileRepaintsTheErrorsWhoeverAskedForIt() throws Exception {
        CountingInlays inlays = new CountingInlays();
        ServiceContainerUtil.replaceService(getProject(), ValidationProblemInlayManager.class, inlays,
                getTestRootDisposable());
        CompiledGraphErrors.install(getProject(), List.of("any"),
                CompiledGraphErrors.error("definitions/any.sqlx", null, "Something odd happened", null));
        BlankOutputCompiler.install(getProject(), getTestRootDisposable());

        BlankOutputCompiler.compileInBackground(getProject());

        assertTrue(inlays.refreshed.get() > 0);
    }

    private static final class CountingInlays implements ValidationProblemInlayManager {

        private final AtomicInteger refreshed = new AtomicInteger();

        @Override
        public void refresh(@NotNull Editor editor) {
        }

        @Override
        public void refreshAll() {
            refreshed.incrementAndGet();
        }
    }
}
