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
package io.github.rejeb.dataform.language.lineage.view;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;

import java.util.List;
import java.util.Set;

public class OpenLineageViewActionTest extends BasePlatformTestCase {

    private LineageFileEditor editor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        editor = new LineageFileEditor(getProject(), LineageProjectVirtualFile.getInstance());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Disposer.dispose(editor);
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testOpeningTheViewAgainKeepsTheMarksOfTheTablesShownBefore() {
        editor.model().setMarkedIds(Set.of("p.d.orders", "p.d.customers"));

        OpenLineageViewAction.showIn(new FileEditor[]{editor}, LineageSource.DATAFORM, null);

        assertEquals(Set.of("p.d.orders", "p.d.customers"), editor.model().markedIds());
        assertEquals(LineageSource.DATAFORM, editor.model().source());
    }

    public void testOpeningTheViewOnNoTableClearsTheMarks() {
        editor.model().setMarkedIds(Set.of("p.d.orders", "p.d.customers"));

        OpenLineageViewAction.showIn(new FileEditor[]{editor}, null, List.of());

        assertEquals(Set.of(), editor.model().markedIds());
    }
}
