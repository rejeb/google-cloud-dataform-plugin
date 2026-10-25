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
package io.github.rejeb.dataform.language.reference;

import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.service.WorkflowSettingsService;

public class DataformJsReferenceOutsideDataformTest extends BasePlatformTestCase {

    public void testWorkflowSettingPropertyIsFalseWithoutSettingsFile() {
        assertFalse(WorkflowSettingsService.getInstance(getProject()).isWorkflowSettingProperty("dataform"));
        assertNull(WorkflowSettingsService.getInstance(getProject()).getProperty("projectConfig"));
    }

    public void testJavaScriptOutsideDataformProjectGetsNoDataformReferences() {
        PsiFile file = myFixture.addFileToProject("frontend/app.js", """
                const session = dataform.projectConfig;
                session.toString();
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

        for (JSReferenceExpression expression : PsiTreeUtil.findChildrenOfType(file, JSReferenceExpression.class)) {
            PsiReference[] references = ReferenceProvidersRegistry.getReferencesFromProviders(expression);
            assertEquals(expression.getText(), 0, references.length);
        }
        myFixture.doHighlighting();
    }
}
