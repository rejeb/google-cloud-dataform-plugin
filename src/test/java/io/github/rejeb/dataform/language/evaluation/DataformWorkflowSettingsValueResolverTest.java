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

public class DataformWorkflowSettingsValueResolverTest extends BasePlatformTestCase {

    private static final String SETTINGS = """
            defaultProject: my-project
            defaultDataset: my_dataset
            vars:
              gold_dataset: gold
              nested:
                deep: value
            """;

    private PsiFile definition;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("workflow_settings.yaml", SETTINGS);
        definition = myFixture.addFileToProject("definitions/mart.sqlx",
                "config { type: \"table\" }\n\nSELECT 1\n");
    }

    public void testSettingsAreExposedUnderTheProjectConfigPath() {
        assertEquals("my-project", resolve("dataform.projectConfig.defaultProject"));
        assertEquals("my_dataset", resolve("dataform.projectConfig.defaultDataset"));
        assertNull(resolve("defaultProject"));
    }

    public void testResolvesNestedScalarsByDottedPath() {
        assertEquals("gold", resolve("dataform.projectConfig.vars.gold_dataset"));
        assertEquals("value", resolve("dataform.projectConfig.vars.nested.deep"));
    }

    public void testContainersHaveNoValue() {
        assertNull(resolve("dataform"));
        assertNull(resolve("dataform.projectConfig"));
        assertNull(resolve("dataform.projectConfig.vars"));
    }

    public void testUnknownPathsHaveNoValue() {
        assertNull(resolve("nope"));
        assertNull(resolve("dataform.projectConfig.vars.nope"));
        assertNull(resolve("dataform.projectConfig.defaultProject.deeper"));
    }

    public void testResolvesFromAnyElementOfTheFile() {
        assertEquals("gold", DataformWorkflowSettingsValueResolver.resolve(
                definition.findElementAt(definition.getTextLength() - 2),
                "dataform.projectConfig.vars.gold_dataset"));
    }

    public void testWithoutSettingsFileNothingResolves() {
        PsiFile settings = myFixture.findFileInTempDir("workflow_settings.yaml") == null ? null
                : myFixture.getPsiManager().findFile(myFixture.findFileInTempDir("workflow_settings.yaml"));
        assertNotNull(settings);
        com.intellij.openapi.application.WriteAction.run(settings::delete);
        assertNull(resolve("dataform.projectConfig.defaultProject"));
    }

    private String resolve(String dottedPath) {
        return DataformWorkflowSettingsValueResolver.resolve(definition, dottedPath);
    }
}
