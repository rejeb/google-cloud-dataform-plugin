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
package io.github.rejeb.dataform.language.diagnostics.sql.fix;

import com.intellij.modcommand.ActionContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlFix;

public class ApplySqlFixActionTest extends BasePlatformTestCase {

    private static final String SQLX = "config { type: \"table\" }\nSELECT custmer_id FROM t\n";
    private static final TextRange NAME = TextRange.from(SQLX.indexOf("custmer_id"), 10);

    private ApplySqlFixAction fix(PsiFile file) {
        return new ApplySqlFixAction(file, new SqlFix("Replace with 'customer_id'", NAME, "customer_id"));
    }

    public void testTheFixWritesItsReplacementOverItsRange() {
        PsiFile file = myFixture.configureByText("fix.sqlx", SQLX);

        myFixture.launchAction(fix(file).asIntention());

        assertEquals(SQLX.replace("custmer_id", "customer_id"), myFixture.getFile().getText());
    }

    public void testTheFixIsNamedByItsLabel() {
        PsiFile file = myFixture.configureByText("fix.sqlx", SQLX);

        assertEquals("Replace with 'customer_id'",
                fix(file).getPresentation(ActionContext.from(myFixture.getEditor(), file)).name());
    }

    public void testATextChangedSinceTheFixWasOfferedIsLeftAlone() {
        PsiFile file = myFixture.configureByText("fix.sqlx", SQLX);
        ApplySqlFixAction action = fix(file);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> myFixture.getEditor().getDocument()
                .replaceString(NAME.getStartOffset(), NAME.getEndOffset(), "order_ts__"));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

        myFixture.launchAction(action.asIntention());

        assertTrue(myFixture.getFile().getText().contains("order_ts__"));
    }
}
