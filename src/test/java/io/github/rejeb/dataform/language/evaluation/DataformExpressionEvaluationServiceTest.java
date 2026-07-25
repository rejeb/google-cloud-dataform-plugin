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
package io.github.rejeb.dataform.language.evaluation;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class DataformExpressionEvaluationServiceTest extends BasePlatformTestCase {

    public void testInvalidateDropsCachedValuesAndBumpsModificationCount() {
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx", "SELECT 1");
        DataformExpressionEvaluationServiceImpl service = (DataformExpressionEvaluationServiceImpl)
                DataformExpressionEvaluationService.getInstance(getProject());
        service.putCachedValue(file.getVirtualFile(), "ref(\"x\")", "p.d.x");
        long before = service.getModificationCount();

        service.invalidate(file.getVirtualFile());

        assertNull(service.getCachedValue(file.getVirtualFile(), "ref(\"x\")"));
        assertTrue(service.getModificationCount() > before);
    }

    public void testInvalidateAllDropsEveryCachedValue() {
        PsiFile file = myFixture.addFileToProject("definitions/other.sqlx", "SELECT 1");
        DataformExpressionEvaluationServiceImpl service = (DataformExpressionEvaluationServiceImpl)
                DataformExpressionEvaluationService.getInstance(getProject());
        service.putCachedValue(file.getVirtualFile(), "ref(\"y\")", "p.d.y");

        service.invalidateAll();

        assertNull(service.getCachedValue(file.getVirtualFile(), "ref(\"y\")"));
    }
}
