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
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.injected.editor.EditorWindow;

public class ValidationProblemInlayManagerImplTest extends BasePlatformTestCase {

    public void testManagerIsRegisteredAndResolvable() {
        assertNotNull(ValidationProblemInlayManager.getInstance(getProject()));
    }

    public void testRefreshAddsNoChipsWhenThereAreNoDiagnostics() {
        myFixture.configureByText("a.sqlx", "select 1\n");
        Editor editor = hostEditor();
        ValidationProblemInlayManager.getInstance(getProject()).refresh(editor);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEmpty(editor.getInlayModel().getAfterLineEndElementsInRange(
                0, editor.getDocument().getTextLength()));
        assertEmpty(editor.getInlayModel().getBlockElementsInRange(
                0, editor.getDocument().getTextLength()));
    }

    public void testRefreshIsIdempotentAndDoesNotAccumulateChips() {
        myFixture.configureByText("b.sqlx", "select 1\n");
        Editor editor = hostEditor();
        ValidationProblemInlayManager manager =
                ValidationProblemInlayManager.getInstance(getProject());
        manager.refresh(editor);
        manager.refresh(editor);
        manager.refresh(editor);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEmpty(editor.getInlayModel().getAfterLineEndElementsInRange(
                0, editor.getDocument().getTextLength()));
    }

    public void testInjectedEditorsAreSkippedWithoutError() {
        myFixture.configureByText("c.sqlx", "config { type: \"table\" }\n\nselect 1\n");
        ValidationProblemInlayManager.getInstance(getProject()).refreshAll();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    private Editor hostEditor() {
        Editor editor = myFixture.getEditor();
        return editor instanceof EditorWindow window ? window.getDelegate() : editor;
    }
}
