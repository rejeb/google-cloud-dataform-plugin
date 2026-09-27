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
package io.github.rejeb.dataform.language.highlight;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.psi.PsiFile;
import com.intellij.sql.inspections.SqlResolveInspection;
import com.intellij.testFramework.ServiceContainerUtil;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryFailures;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import io.github.rejeb.dataform.language.util.MappedText;
import io.github.rejeb.dataform.language.validation.DataformEditActivityService;

import java.util.List;
import java.util.Objects;

public class SqlxLocalSqlHintsTest extends DataformProjectFixture {

    @Override
    protected void tearDown() throws Exception {
        try {
            BigQueryFailures.clear(getProject());
        } finally {
            super.tearDown();
        }
    }

    private List<String> highlight(String path, String sql) {
        myFixture.enableInspections(new SqlResolveInspection());
        PsiFile file = myFixture.addFileToProject(path, "config { type: \"table\" }\n\n" + sql);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.doHighlighting().stream().map(HighlightInfo::getDescription).filter(Objects::nonNull).toList();
    }

    public void testAMisspelledColumnIsOfferedTheColumnItMostLikelyMeant() {
        List<String> descriptions = highlight("definitions/local_hints.sqlx",
                "SELECT order_idd\nFROM ${ref(\"silver_orders\")}\n");

        assertTrue(descriptions.toString(), descriptions.contains("Did you mean 'order_id'?"));
    }

    public void testTheSuggestionIsAFix() {
        highlight("definitions/local_hints.sqlx", "SELECT order_idd\nFROM ${ref(\"silver_orders\")}\n");
        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getFile().getText().indexOf("order_idd") + 2);

        myFixture.launchAction(myFixture.findSingleIntention("Replace with 'order_id'"));

        assertTrue(myFixture.getFile().getText().contains("SELECT order_id\n"));
    }

    public void testAColumnOfANamedSourceIsLookedForInThatSource() {
        List<String> descriptions = highlight("definitions/local_hints.sqlx",
                "SELECT o.order_tss\nFROM ${ref(\"silver_orders\")} o\n");

        assertTrue(descriptions.toString(), descriptions.contains("Did you mean 'order_ts'?"));
    }

    public void testAnAliasOfTheQueryIsNotTakenForAMisspelledColumn() {
        List<String> descriptions = highlight("definitions/local_hints.sqlx",
                "SELECT order_i\nFROM ${ref(\"silver_orders\")} o, UNNEST([1, 2]) AS order_i\n");

        assertFalse(descriptions.toString(), descriptions.stream().anyMatch(d -> d.startsWith("Did you mean")));
    }

    public void testTheNameOfACommonTableExpressionIsNotTakenForAMisspelledColumn() {
        List<String> descriptions = highlight("definitions/local_hints.sqlx",
                "WITH order_i AS (SELECT 1 AS x)\nSELECT order_i\nFROM ${ref(\"silver_orders\")} o, order_i\n");

        assertFalse(descriptions.toString(), descriptions.stream().anyMatch(d -> d.startsWith("Did you mean")));
    }

    public void testAResolvedColumnGetsNoSuggestion() {
        List<String> descriptions = highlight("definitions/local_hints.sqlx",
                "SELECT order_id\nFROM ${ref(\"silver_orders\")}\n");

        assertFalse(descriptions.toString(), descriptions.stream().anyMatch(d -> d.startsWith("Did you mean")));
    }

    public void testAPlaceBigQueryReportedIsLeftToIt() {
        DataformToolsSettings.getInstance().setShowInlineCompilationErrors(true);
        ServiceContainerUtil.replaceService(getProject(), DataformEditActivityService.class,
                new NotEditing(), getTestRootDisposable());
        MappedText sent = BigQueryFailures.sent(getProject(), BigQueryFailures.COMPILED);
        BigQueryFailures.report(getProject(), "Unrecognized name: order_idd; Did you mean order_id?"
                + BigQueryFailures.at(sent, "order_idd"), sent);
        myFixture.enableInspections(new SqlResolveInspection());
        PsiFile file = myFixture.addFileToProject(BigQueryFailures.PATH, BigQueryFailures.SQLX);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

        List<String> descriptions = myFixture.doHighlighting().stream()
                .map(HighlightInfo::getDescription).filter(Objects::nonNull).toList();

        assertFalse(descriptions.toString(), descriptions.contains("Did you mean 'order_id'?"));
        assertTrue(descriptions.toString(), descriptions.stream().anyMatch(d -> d.startsWith("BigQuery: Unrecognized name")));
    }

    public void testAMissingCommaBetweenSelectItemsCanBeInserted() {
        List<String> descriptions = highlight("definitions/local_hints.sqlx",
                "SELECT order_id customer_id order_ts\nFROM ${ref(\"silver_orders\")}\n");

        assertTrue(descriptions.toString(),
                descriptions.stream().anyMatch(d -> d.endsWith("A ',' may be missing before 'order_ts'.")));
    }

    private static final class NotEditing implements DataformEditActivityService {

        @Override
        public void noteEdit() {
        }

        @Override
        public boolean isEditing() {
            return false;
        }

        @Override
        public long remainingQuietPeriodMs(long quietPeriodMs) {
            return 0;
        }
    }
}
