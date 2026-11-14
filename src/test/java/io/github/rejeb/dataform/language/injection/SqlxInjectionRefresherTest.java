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
package io.github.rejeb.dataform.language.injection;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionInitializationUtil;
import com.intellij.codeInsight.completion.OffsetsInFile;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationServiceImpl;

/**
 * Completion keeps a copy of the file and reuses it from one completion to the next, with its
 * injected files, each pointing at the injected file of the original it was copied from. A refresh
 * must leave the original with those same injected files, or the next completion in them fails.
 */
public class SqlxInjectionRefresherTest extends BasePlatformTestCase {

    private PsiFile sqlx(String body) {
        PsiFile file = myFixture.addFileToProject("definitions/action.sqlx",
                "config { type: \"table\" }\n" + body);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return file;
    }

    private PsiFile injectedAt(PsiFile file, int offset) {
        PsiElement element = InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(file, offset);
        assertNotNull("nothing is injected at " + offset, element);
        return element.getContainingFile();
    }

    private static OffsetsInFile completionAt(PsiFile copy, int offset) {
        OffsetsInFile offsets = new OffsetsInFile(copy);
        offsets.getOffsets().addOffset(CompletionInitializationContext.START_OFFSET, offset);
        offsets.getOffsets().addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, offset);
        offsets.getOffsets().addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, offset);
        return offsets;
    }

    private void completeTwiceAroundARefresh(PsiFile file, int offset) {
        PsiFile copy = (PsiFile) file.copy();
        CompletionInitializationUtil.toInjectedIfAny(injectedAt(file, offset), completionAt(copy, offset));

        SqlxInjectionRefresher.refresh(getProject(), file.getVirtualFile());

        CompletionInitializationUtil.toInjectedIfAny(injectedAt(file, offset), completionAt(copy, offset));
    }

    public void testCompletionInTheConfigBlockStillWorksAfterARefresh() {
        PsiFile file = sqlx("SELECT 1 AS a FROM t");

        completeTwiceAroundARefresh(file, file.getText().indexOf("table"));
    }

    public void testCompletionInTheSqlStillWorksAfterARefresh() {
        PsiFile file = sqlx("SELECT 1 AS a FROM t");

        completeTwiceAroundARefresh(file, file.getText().indexOf("AS a"));
    }

    public void testCompletionInTheSqlStillWorksAfterARefreshBringingAValue() {
        PsiFile file = sqlx("SELECT ${constants.A} AS a FROM t");
        int offset = file.getText().indexOf("AS a");
        PsiFile copy = (PsiFile) file.copy();
        CompletionInitializationUtil.toInjectedIfAny(injectedAt(file, offset), completionAt(copy, offset));

        ((DataformExpressionEvaluationServiceImpl) DataformExpressionEvaluationService.getInstance(getProject()))
                .putCachedValue(file.getVirtualFile(), "constants.A", "500");
        SqlxInjectionRefresher.refresh(getProject(), file.getVirtualFile());

        PsiFile sql = injectedAt(file, offset);
        CompletionInitializationUtil.toInjectedIfAny(sql, completionAt(copy, offset));
        assertEquals("SELECT 500 AS a FROM t", sql.getText());
    }
}
