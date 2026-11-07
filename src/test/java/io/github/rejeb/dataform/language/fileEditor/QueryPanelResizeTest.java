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
package io.github.rejeb.dataform.language.fileEditor;

import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewer;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewerDrag;

import javax.swing.SwingUtilities;
import java.util.List;

public class QueryPanelResizeTest extends BasePlatformTestCase {

    private static final List<FormattedCompiledQuery> QUERIES = List.of(new FormattedCompiledQuery("p.ds.users",
            null, null, "SELECT 1\n".repeat(60), null, null, "table", 0, 0, 0));

    private QueryPanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        panel = new QueryPanel(getProject(), PlainTextFileType.INSTANCE);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            panel.dispose();
        } finally {
            super.tearDown();
        }
    }

    public void testAResizedBlockKeepsItsHeightWhenThePreviewIsRebuilt() {
        panel.setContent(QUERIES);
        SqlViewer viewer = firstViewer();
        int automatic = viewer.editorAreaHeight();
        SqlViewerDrag.drag(viewer, 5 * viewer.editor().getLineHeight());
        int resized = viewer.editorAreaHeight();

        panel.setContent(QUERIES);

        assertNotSame(viewer, firstViewer());
        assertTrue(resized > automatic);
        assertEquals(resized, firstViewer().editorAreaHeight());
    }

    public void testTheBlocksOfThePreviewAreResizable() {
        panel.setContent(QUERIES);
        SqlViewer viewer = firstViewer();
        int automatic = viewer.editorAreaHeight();

        SqlViewerDrag.drag(viewer, 5 * viewer.editor().getLineHeight());

        assertTrue(viewer.editorAreaHeight() > automatic);
    }

    private SqlViewer firstViewer() {
        return (SqlViewer) SwingUtilities.getAncestorOfClass(SqlViewer.class, panel.getEditor().getComponent());
    }
}
