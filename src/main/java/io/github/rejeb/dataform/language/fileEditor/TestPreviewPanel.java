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
import com.intellij.openapi.project.Project;
import com.intellij.sql.SqlFileType;
import com.intellij.ui.InlineBanner;
import io.github.rejeb.dataform.language.ui.Banners;
import io.github.rejeb.dataform.language.ui.CollapsibleSection;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewer;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewerHeights;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class TestPreviewPanel extends SectionsPreviewPanel {

    private static final int MAX_LINES = 20;

    private final SqlViewerHeights heights = new SqlViewerHeights();
    private final SqlViewer testQuery;
    private final SqlViewer expectedOutput;
    private final SqlViewer errors;
    private final CollapsibleSection testGroup;
    private final CollapsibleSection expectedGroup;
    private final CollapsibleSection errorsGroup;
    private final InlineBanner errorBanner = Banners.error("");

    TestPreviewPanel(@NotNull Project project) {
        testQuery = new SqlViewer(project, block("query").withFileType(SqlFileType.INSTANCE));
        expectedOutput = new SqlViewer(project, block("expected").withFileType(SqlFileType.INSTANCE));
        errors = new SqlViewer(project, block("errors").withFileType(PlainTextFileType.INSTANCE).withLineNumbers(false));
        testGroup = new CollapsibleSection("Test query", testQuery);
        testGroup.setSubtitle("dataset query on the input rows");
        expectedGroup = new CollapsibleSection("Expected output", expectedOutput);
        errorsGroup = new CollapsibleSection("Compilation errors", errors);

        errorBanner.setVisible(false);
        sectionsPanel.add(errorBanner);
        sectionsPanel.add(testGroup);
        sectionsPanel.add(expectedGroup);
        sectionsPanel.add(errorsGroup);
    }

    void setContent(@Nullable String testSql, @Nullable String expectedSql, @Nullable String compilationErrors) {
        testQuery.setText(testSql);
        expectedOutput.setText(expectedSql);
        errors.setText(compilationErrors);
        testGroup.setVisible(testQuery.isVisible());
        expectedGroup.setVisible(expectedOutput.isVisible());
        errorsGroup.setVisible(errors.isVisible());
        errorBanner.setVisible(errors.isVisible());
        if (errors.isVisible()) Banners.setMessage(errorBanner, compilationErrors);
        revalidate();
        repaint();
    }

    void dispose() {
        testQuery.dispose();
        expectedOutput.dispose();
        errors.dispose();
    }

    private SqlViewerOptions block(String key) {
        return SqlViewerOptions.fitText(MAX_LINES).withResize(heights, "test#" + key);
    }
}
