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
package io.github.rejeb.dataform.language.refactoring.column.usage;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationServiceImpl;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;

import java.util.List;

/**
 * A rename writes host documents, so every injected range has to land on text the host actually
 * holds. Text the injection wrote — a template hole's value — has no such place.
 */
public class HostRangesTest extends BasePlatformTestCase {

    private PsiFile injectedSql(String body, String source, String value) {
        PsiFile file = myFixture.addFileToProject("definitions/action.sqlx",
                "config { type: \"table\" }\n" + body);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        ((DataformExpressionEvaluationServiceImpl) DataformExpressionEvaluationService
                .getInstance(getProject())).putCachedValue(file.getVirtualFile(), source, value);
        SqlxSqlBlock block = PsiTreeUtil.findChildOfType(myFixture.getFile(), SqlxSqlBlock.class);
        return InjectedFiles.of(List.of(block)).getFirst();
    }

    private PsiElement leafAt(PsiFile sql, String token) {
        int offset = sql.getText().indexOf(token);
        assertTrue("token '" + token + "' must be in the injected SQL", offset >= 0);
        return sql.findElementAt(offset);
    }

    public void testATokenTheUserWroteHasAHostRange() {
        PsiFile sql = injectedSql("SELECT ${helpers.top_value(\"order_id\")} AS top_order FROM t",
                "helpers.top_value(\"order_id\")", "MAX(order_id)");
        PsiElement alias = leafAt(sql, "top_order");
        TextRange range = HostRanges.hostRangeOf(alias, TextRange.from(0, alias.getTextLength()));
        assertNotNull(range);
        assertEquals("top_order", myFixture.getFile().getText()
                .substring(range.getStartOffset(), range.getEndOffset()));
    }

    public void testATokenTheInjectionWroteHasNoHostRange() {
        PsiFile sql = injectedSql("SELECT ${helpers.top_value(\"order_id\")} AS top_order FROM t",
                "helpers.top_value(\"order_id\")", "MAX(order_id)");
        PsiElement expanded = leafAt(sql, "order_id");
        assertNull("a name inside a hole's value cannot be rewritten in the host",
                HostRanges.hostRangeOf(expanded, TextRange.from(0, expanded.getTextLength())));
    }
}
