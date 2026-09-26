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
package io.github.rejeb.dataform.language.diagnostics.compile;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.diagnostics.UnlocatedErrorsNotificationProvider;

import java.util.List;

public class UnplacedCompilationErrorsTest extends BasePlatformTestCase {

    @Override
    protected void tearDown() throws Exception {
        try {
            CompiledGraphErrors.clear(getProject());
        } finally {
            super.tearDown();
        }
    }

    public void testOnlyTheErrorsThatCannotBePlacedAreListed() {
        PsiFile file = myFixture.addFileToProject("definitions/mixed.sqlx",
                "config { type: \"table\" }\n\nSELECT ${zzz} AS c\n");
        CompiledGraphErrors.install(getProject(), List.of("mixed"),
                CompiledGraphErrors.error("definitions/mixed.sqlx", "proj.ds.mixed", "zzz is not defined",
                        "ReferenceError: zzz is not defined\n    at Object.sqlContextable (/tmp/copy/definitions/mixed.sqlx:19:13)"),
                CompiledGraphErrors.error("definitions/mixed.sqlx", null, "Something odd happened", null));

        assertEquals(List.of("Something odd happened"),
                UnlocatedErrorsNotificationProvider.messages(getProject(), file.getVirtualFile()));
    }

    public void testTheErrorsOfAFileTheEditorShowsNoProblemsInAreListed() {
        PsiFile file = myFixture.addFileToProject("definitions/orders.sql", "SELECT * FROM does_not_exist\n");
        String message = "Missing dependency detected: Action \"proj.ds.orders\" depends on "
                + "\"{\"name\":\"does_not_exist\",\"includeDependentAssertions\":false}\" which does not exist";
        CompiledGraphErrors.install(getProject(), List.of("orders"),
                CompiledGraphErrors.error("definitions/orders.sql", "proj.ds.orders", message, null));

        assertEquals(List.of(message), UnlocatedErrorsNotificationProvider.messages(getProject(), file.getVirtualFile()));
    }
}
