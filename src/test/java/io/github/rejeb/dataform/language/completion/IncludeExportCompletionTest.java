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
package io.github.rejeb.dataform.language.completion;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class IncludeExportCompletionTest extends BasePlatformTestCase {

    private static final String INCLUDE = """
            const suffix = "_v1";

            function tableName(name) {
              return name + suffix;
            }

            module.exports = { suffix, tableName };
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("includes/constants.js", INCLUDE);
    }

    public void testExportsAreProposedAsQualifiedPaths() {
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx",
                "config { type: \"table\" }\n\njs {\n  const a = <caret>\n}\n\nSELECT 1\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.completeBasic();

        List<String> lookups = myFixture.getLookupElementStrings();
        assertNotNull("expected a completion popup", lookups);
        assertTrue("got " + lookups, lookups.contains("constants.suffix"));
        assertTrue("got " + lookups, lookups.contains("constants.tableName"));
        assertFalse("the bare file name must not be proposed, got " + lookups,
                lookups.contains("constants"));
        assertEquals("no duplicate entry, got " + lookups,
                1, lookups.stream().filter("constants.tableName"::equals).count());
        assertFalse("the bare export name must not be proposed, got " + lookups,
                lookups.contains("tableName"));
    }

    public void testExportsMatchOnTheirOwnName() {
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx",
                "config { type: \"table\" }\n\njs {\n  const a = tableNa<caret>\n}\n\nSELECT 1\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.completeBasic();

        List<String> lookups = myFixture.getLookupElementStrings();
        if (lookups == null) {
            String text = myFixture.getFile().getText();
            assertTrue("got [" + text + "]", text.contains("constants.tableName("));
            return;
        }
        assertTrue("got " + lookups, lookups.contains("constants.tableName"));
    }
}
