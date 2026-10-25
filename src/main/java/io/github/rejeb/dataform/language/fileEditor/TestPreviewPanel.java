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

import com.intellij.openapi.project.Project;
import com.intellij.sql.SqlFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class TestPreviewPanel extends SectionsPreviewPanel {

    private final QuerySection testQuery;
    private final QuerySection expectedOutput;
    private final QuerySection errors;

    TestPreviewPanel(@NotNull Project project) {
        testQuery = new QuerySection("Test query (dataset query on the input rows)", SqlFileType.INSTANCE, project, false);
        expectedOutput = new QuerySection("Expected output", SqlFileType.INSTANCE, project, false);
        errors = new QuerySection("Compilation Errors", null, project, true);

        sectionsPanel.add(testQuery);
        sectionsPanel.add(expectedOutput);
        sectionsPanel.add(errors);
    }

    void setContent(@Nullable String testSql, @Nullable String expectedSql, @Nullable String compilationErrors) {
        testQuery.setContent(testSql);
        expectedOutput.setContent(expectedSql);
        errors.setContent(compilationErrors);
        revalidate();
        repaint();
    }

    void dispose() {
        testQuery.dispose();
        expectedOutput.dispose();
        errors.dispose();
    }
}
