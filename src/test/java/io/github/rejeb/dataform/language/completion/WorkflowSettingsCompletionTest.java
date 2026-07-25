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
import io.github.rejeb.dataform.language.service.WorkflowSettingsService;

import java.util.Collection;
import java.util.List;

public class WorkflowSettingsCompletionTest extends BasePlatformTestCase {

    private static final String SETTINGS = """
            defaultProject: my-project
            defaultDataset: my_dataset
            vars:
              env: prod
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("workflow_settings.yaml", SETTINGS);
    }

    public void testLeafPathsAreFlattened() {
        Collection<String> paths = WorkflowSettingsService.getInstance(getProject())
                .getLeafPathsForPrefix(null);

        assertTrue("got " + paths, paths.contains("dataform.projectConfig.defaultProject"));
        assertTrue("got " + paths, paths.contains("dataform.projectConfig.vars.env"));
        assertFalse("intermediate nodes must not be proposed, got " + paths,
                paths.contains("dataform"));
    }

    public void testLeafPathsAreRelativeToThePrefix() {
        Collection<String> paths = WorkflowSettingsService.getInstance(getProject())
                .getLeafPathsForPrefix("dataform.projectConfig");

        assertTrue("got " + paths, paths.contains("defaultProject"));
        assertTrue("got " + paths, paths.contains("vars.env"));
    }

    public void testCompletionProposesTheWholePath() {
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx",
                "config { type: \"table\" }\n\njs {\n  const a = defaultProj<caret>\n}\n\nSELECT 1\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.completeBasic();

        List<String> lookups = myFixture.getLookupElementStrings();
        if (lookups == null) {
            String text = myFixture.getFile().getText();
            assertTrue("got [" + text + "]",
                    text.contains("dataform.projectConfig.defaultProject"));
            return;
        }
        assertEquals("no duplicate entry, got " + lookups, 1, lookups.stream()
                .filter("dataform.projectConfig.defaultProject"::equals).count());
    }
}
