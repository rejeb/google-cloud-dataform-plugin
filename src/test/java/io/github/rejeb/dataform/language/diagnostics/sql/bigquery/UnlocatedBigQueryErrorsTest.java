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
package io.github.rejeb.dataform.language.diagnostics.sql.bigquery;

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ServiceContainerUtil;
import io.github.rejeb.dataform.language.diagnostics.UnlocatedErrorsNotificationProvider;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;
import io.github.rejeb.dataform.language.schema.sql.DryRunErrorRegistry;
import io.github.rejeb.dataform.language.util.MappedText;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.List;

import static io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryFailures.*;

public class UnlocatedBigQueryErrorsTest extends DataformProjectFixture {

    @Override
    protected void tearDown() throws Exception {
        try {
            clear(getProject());
        } finally {
            super.tearDown();
        }
    }

    public void testAnErrorThatCannotBePlacedIsListedInTheBanner() {
        PsiFile file = myFixture.addFileToProject(PATH, SQLX);
        DryRunErrorRegistry.getInstance(getProject())
                .report(ACTION, "Not found: Dataset proj:ds was not found in location US");

        assertEquals(List.of("BigQuery: Not found: Dataset proj:ds was not found in location US"),
                UnlocatedErrorsNotificationProvider.messages(getProject(), file.getVirtualFile()));
    }

    public void testAnErrorWhosePlaceCannotBeTracedIsListedInTheBanner() {
        ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), ComparisonManager.class,
                tooBigToCompareWords(), getTestRootDisposable());
        PsiFile file = myFixture.addFileToProject(PATH, SQLX);
        MappedText sent = sent(getProject(), COMPILED);
        report(getProject(), "Unrecognized name: order_idd" + at(sent, "order_idd"), sent);

        List<String> messages = UnlocatedErrorsNotificationProvider.messages(getProject(), file.getVirtualFile());

        assertEquals(messages.toString(), 1, messages.size());
        assertTrue(messages.getFirst(), messages.getFirst().startsWith("BigQuery: Unrecognized name: order_idd"));
    }

    public void testAFileWithoutErrorsHasNothingToList() {
        PsiFile file = myFixture.addFileToProject(PATH, SQLX);

        assertEquals(List.of(), UnlocatedErrorsNotificationProvider.messages(getProject(), file.getVirtualFile()));
    }

    private static ComparisonManager tooBigToCompareWords() {
        ComparisonManager comparison = Mockito.mock(ComparisonManager.class,
                AdditionalAnswers.delegatesTo(ComparisonManager.getInstance()));
        Mockito.doThrow(new DiffTooBigException()).when(comparison).compareWords(ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        return comparison;
    }
}
