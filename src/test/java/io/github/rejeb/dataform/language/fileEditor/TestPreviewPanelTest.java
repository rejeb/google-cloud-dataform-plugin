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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewer;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewerDrag;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

public class TestPreviewPanelTest extends BasePlatformTestCase {

    private static final String TEST_SQL = "SELECT 1\n".repeat(60);
    private static final String ERRORS = "Unknown column foo";

    private TestPreviewPanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        panel = new TestPreviewPanel(getProject());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            panel.dispose();
        } finally {
            super.tearDown();
        }
    }

    public void testEachBlockIsShownInTheSqlViewer() {
        panel.setContent(TEST_SQL, "SELECT 2", ERRORS);

        assertEquals(List.of(TEST_SQL, "SELECT 2", ERRORS), viewers().stream().map(SqlViewer::text).toList());
    }

    public void testTheCompilationErrorsShowWithoutLineNumbers() {
        panel.setContent(TEST_SQL, "SELECT 2", ERRORS);

        assertTrue(viewers().get(0).editor().getSettings().isLineNumbersShown());
        assertFalse(viewers().get(2).editor().getSettings().isLineNumbersShown());
    }

    public void testAResizedBlockKeepsItsHeightWhenTheTestIsShownAgain() {
        panel.setContent(TEST_SQL, "SELECT 2", null);
        SqlViewer testQuery = viewers().getFirst();
        int automatic = testQuery.editorAreaHeight();
        SqlViewerDrag.drag(testQuery, 5 * testQuery.editor().getLineHeight());
        int resized = testQuery.editorAreaHeight();

        panel.setContent(TEST_SQL, "SELECT 3", null);

        assertTrue(resized > automatic);
        assertEquals(resized, viewers().getFirst().editorAreaHeight());
    }

    private List<SqlViewer> viewers() {
        List<SqlViewer> found = new ArrayList<>();
        collect(panel, found);
        return found;
    }

    private static void collect(Component root, List<SqlViewer> found) {
        if (root instanceof SqlViewer viewer) found.add(viewer);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) collect(child, found);
        }
    }
}
