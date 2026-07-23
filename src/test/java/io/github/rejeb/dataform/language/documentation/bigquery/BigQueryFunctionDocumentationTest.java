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
package io.github.rejeb.dataform.language.documentation.bigquery;

import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class BigQueryFunctionDocumentationTest extends BasePlatformTestCase {

    public void testFunctionCallProducesTarget() {
        PsiFile file = myFixture.configureByText("test.sqlx",
                "config { type: \"table\" }\nSELECT DATE_TR<caret>UNC(d, DAY) FROM t");

        List<? extends DocumentationTarget> targets =
                new BigQueryFunctionDocumentationTargetProvider()
                        .documentationTargets(file, myFixture.getCaretOffset());

        assertFalse("expected a documentation target on the function name", targets.isEmpty());
        assertEquals("DATE_TRUNC", targets.get(0).computePresentation().getPresentableText());
    }

    public void testFunctionCallIsCaseInsensitive() {
        PsiFile file = myFixture.configureByText("test.sqlx",
                "config { type: \"table\" }\nSELECT su<caret>m(amount) FROM t");

        List<? extends DocumentationTarget> targets =
                new BigQueryFunctionDocumentationTargetProvider()
                        .documentationTargets(file, myFixture.getCaretOffset());

        assertFalse(targets.isEmpty());
        assertEquals("SUM", targets.get(0).computePresentation().getPresentableText());
    }

    public void testColumnNamedLikeFunctionProducesNoTarget() {
        PsiFile file = myFixture.configureByText("test.sqlx",
                "config { type: \"table\" }\nSELECT le<caret>ft FROM t");

        assertTrue("a bare column reference must not render function documentation",
                new BigQueryFunctionDocumentationTargetProvider()
                        .documentationTargets(file, myFixture.getCaretOffset()).isEmpty());
    }

    public void testUnknownFunctionProducesNoTarget() {
        PsiFile file = myFixture.configureByText("test.sqlx",
                "config { type: \"table\" }\nSELECT my_ud<caret>f(x) FROM t");

        assertTrue(new BigQueryFunctionDocumentationTargetProvider()
                .documentationTargets(file, myFixture.getCaretOffset()).isEmpty());
    }

    public void testDocumentationIsRendered() {
        PsiFile file = myFixture.configureByText("test.sqlx",
                "config { type: \"table\" }\nSELECT DATE_TR<caret>UNC(d, DAY) FROM t");

        DocumentationTarget target = new BigQueryFunctionDocumentationTargetProvider()
                .documentationTargets(file, myFixture.getCaretOffset()).get(0);

        assertNotNull(target.computeDocumentation());
    }
}
