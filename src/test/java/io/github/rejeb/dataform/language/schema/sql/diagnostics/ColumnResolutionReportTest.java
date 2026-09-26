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
package io.github.rejeb.dataform.language.schema.sql.diagnostics;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;

/**
 * On a project where everything resolves, the report walks every step down to the lineage graph
 * and reports no failure.
 */
public class ColumnResolutionReportTest extends DataformProjectFixture {

    private int offsetOf(PsiFile file, int line, String token) {
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        int lineStart = document.getLineStartOffset(line - 1);
        String lineText = document.getText().substring(lineStart, document.getLineEndOffset(line - 1));
        return lineStart + lineText.indexOf(token) + 1;
    }

    public void testAResolvingColumnIsTracedDownToTheLineageGraph() throws Exception {
        PsiFile silver = open("silver/silver_orders.sqlx");

        String report = ColumnResolutionReport.build(silver, offsetOf(silver, 23, "order_id"));

        assertFalse("no step may fail:\n" + report, report.contains("!!"));
        assertTrue(report, report.contains("compiled action: proj.ds.silver_orders"));
        assertTrue(report, report.contains("-> Dataform table proj.ds.bronze_orders"));
        assertTrue(report, report.contains("declares output column: proj.ds.silver_orders"));
        assertTrue(report, report.contains("Dataform column proj.ds.bronze_orders.order_id"));
        assertTrue(report, report.contains("in the column graph"));
    }
}
