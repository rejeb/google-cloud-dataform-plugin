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
package io.github.rejeb.dataform.language.folding;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.psi.PsiFile;

import java.util.List;

public class DataformJsFoldingBuilderTest extends DataformFoldingTestCase {

    public void testSqlxTemplateIsFoldedExactlyOnce() {
        PsiFile file = configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM ${ref(\"users\")}\n");
        seed(file, "ref(\"users\")", "proj.dataset.users");

        List<FoldRegion> regions = dataformRegions();

        assertEquals("the injected JavaScript must not fold the same range, got " + regions,
                1, regions.size());
    }

    public void testTemplateSubstitutionInDefinitionFileIsFolded() {
        PsiFile file = configureDefinition("mart.js",
                "publish(\"mart\").query(ctx => `SELECT * FROM ${ctx.ref(\"users\")}`);\n");
        seed(file, "ctx.ref(\"users\")", "proj.dataset.users");

        List<FoldRegion> regions = dataformRegions();

        assertEquals("expected one fold region, got " + regions, 1, regions.size());
        FoldRegion region = regions.getFirst();
        assertEquals("proj.dataset.users", region.getPlaceholderText());
        assertEquals("${ctx.ref(\"users\")}",
                myFixture.getEditor().getDocument().getText()
                        .substring(region.getStartOffset(), region.getEndOffset()));
    }

    public void testWorkflowSettingsReferenceInConfigBlockIsFolded() {
        myFixture.addFileToProject("workflow_settings.yaml", """
                defaultProject: my-project
                defaultDataset: my_dataset
                vars:
                  env: prod
                """);
        configureDefinition("mart.sqlx",
                "config { type: \"table\", schema: dataform.projectConfig.vars.env }\n\nSELECT 1 AS one\n");

        assertEquals("expected one fold region", 1, regionsWithPlaceholder("prod").size());
    }

    public void testPlainJavaScriptFileOutsideDataformLayoutBuildsNoRegions() {
        PsiFile file = myFixture.addFileToProject("standalone/app.js",
                "const env = dataform.projectConfig.vars.env;\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.doHighlighting();

        assertEquals("a JS file outside a Dataform layout must not be processed",
                0, myFixture.getEditor().getFoldingModel().getAllFoldRegions().length);
    }

    public void testDefinitionFileWithoutSettingsFileStillFolds() {
        PsiFile file = configureDefinition("mart.js",
                "publish(\"mart\").query(ctx => `SELECT * FROM ${ctx.ref(\"users\")}`);\n");
        seed(file, "ctx.ref(\"users\")", "proj.dataset.users");

        assertEquals(1, dataformRegions().size());
    }

    public void testWorkflowSettingsFoldSurvivesFurtherHighlightingPasses() {
        myFixture.addFileToProject("workflow_settings.yaml", """
                defaultProject: my-project
                vars:
                  env: prod
                """);
        configureDefinition("mart.sqlx",
                "config { type: \"table\", schema: dataform.projectConfig.vars.env }\n\nSELECT 1 AS one\n");

        assertEquals(1, regionsWithPlaceholder("prod").size());
        assertEquals("the host folding pass must not drop the injected region",
                1, regionsWithPlaceholder("prod").size());
        assertEquals(1, regionsWithPlaceholder("prod").size());
    }
}
