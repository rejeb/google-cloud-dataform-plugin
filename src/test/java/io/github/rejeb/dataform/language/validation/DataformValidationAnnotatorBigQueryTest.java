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
package io.github.rejeb.dataform.language.validation;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlFix;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;

import java.util.List;

public class DataformValidationAnnotatorBigQueryTest extends BasePlatformTestCase {

    private static final String SQLX = "config { type: \"table\" }\n\nSELECT custmer_id FROM t\n";
    private static final TextRange NAME = TextRange.from(SQLX.indexOf("custmer_id"), 10);

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        DataformToolsSettings.getInstance().setShowInlineCompilationErrors(true);
        ServiceContainerUtil.replaceService(getProject(), DataformEditActivityService.class,
                new NotEditing(), getTestRootDisposable());
        ServiceContainerUtil.replaceService(getProject(), SqlxValidationService.class,
                file -> List.of(SqlxValidationProblem.bigQueryError(NAME, "BigQuery: Unrecognized name: custmer_id",
                        "Did you mean 'customer_id'?",
                        List.of(new SqlFix("Replace with 'customer_id'", NAME, "customer_id")))),
                getTestRootDisposable());
    }

    public void testABigQueryErrorIsPaintedAsAnErrorWithItsHint() {
        myFixture.configureByText("bq.sqlx", SQLX);

        List<HighlightInfo> errors = myFixture.doHighlighting(HighlightSeverity.ERROR);

        assertEquals(List.of("BigQuery: Unrecognized name: custmer_id → Did you mean 'customer_id'?"),
                errors.stream().map(HighlightInfo::getDescription).toList());
    }

    public void testItsFixIsOfferedAndApplied() {
        myFixture.configureByText("bq.sqlx", SQLX);
        myFixture.getEditor().getCaretModel().moveToOffset(NAME.getStartOffset() + 2);
        myFixture.doHighlighting();

        myFixture.launchAction(myFixture.findSingleIntention("Replace with 'customer_id'"));

        assertEquals(SQLX.replace("custmer_id", "customer_id"), myFixture.getFile().getText());
    }

    public void testTheChipReadsTheMessageThenTheHint() {
        assertEquals("BigQuery: x → Do y.",
                SqlxValidationProblem.bigQueryError(TextRange.from(0, 1), "BigQuery: x", "Do y.", List.of()).chipText());
        assertEquals("plain", new SqlxValidationProblem(TextRange.from(0, 1), "plain",
                SqlxValidationProblem.Kind.UNRESOLVED_REFERENCE).chipText());
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
        public long remainingQuietPeriodMs() {
            return 0;
        }
    }
}
