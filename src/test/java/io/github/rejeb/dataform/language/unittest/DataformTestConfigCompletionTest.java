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
package io.github.rejeb.dataform.language.unittest;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.diagnostics.compile.ConfigSchemaFixture;

import java.util.List;

public class DataformTestConfigCompletionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ConfigSchemaFixture.install(getProject(), getTestRootDisposable());
    }

    private List<String> completeIn(String configBlock) {
        PsiFile file = myFixture.addFileToProject("definitions/orders_test.sqlx", configBlock + "\n\nSELECT 1\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();
        return lookups == null ? List.of() : lookups;
    }

    public void testTestIsAnActionType() {
        List<String> lookups = completeIn("config {\n  type: \"<caret>\"\n}");

        assertTrue("got " + lookups, lookups.contains("test"));
    }

    public void testTestConfigProposesItsOwnKeys() {
        List<String> lookups = completeIn("config {\n  type: \"test\",\n  <caret>\n}");

        assertTrue("got " + lookups, lookups.containsAll(List.of("dataset", "name", "tags")));
        assertFalse("got " + lookups, lookups.contains("bigquery"));
        assertFalse("got " + lookups, lookups.contains("partitionBy"));
    }
}
