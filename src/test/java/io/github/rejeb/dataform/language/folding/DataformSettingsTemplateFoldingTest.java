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

public class DataformSettingsTemplateFoldingTest extends DataformFoldingTestCase {

    private static final String SETTINGS = """
            defaultProject: my-project
            defaultDataset: my_dataset
            vars:
              gold_dataset: gold
            """;

    public void testSettingsTemplateFoldsTheWholeExpression() {
        myFixture.addFileToProject("workflow_settings.yaml", SETTINGS);
        PsiFile file = configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM ${dataform.projectConfig.vars.gold_dataset}.orders\n");
        seed(file, "dataform.projectConfig.vars.gold_dataset", "gold");

        myFixture.doHighlighting();
        StringBuilder found = new StringBuilder();
        for (FoldRegion region : myFixture.getEditor().getFoldingModel().getAllFoldRegions()) {
            found.append("\n  [").append(region.getStartOffset()).append(',').append(region.getEndOffset())
                    .append("] group=").append(region.getGroup())
                    .append(" placeholder=").append(region.getPlaceholderText())
                    .append(" covers=").append(myFixture.getEditor().getDocument().getText()
                            .substring(region.getStartOffset(), region.getEndOffset()));
        }

        assertEquals("the fold must replace the whole ${...}, regions were:" + found,
                "${dataform.projectConfig.vars.gold_dataset}",
                textOfSingleRegion());
    }

    public void testSettingsTemplateIsNotPartiallyFoldedWithoutACachedValue() {
        myFixture.addFileToProject("workflow_settings.yaml", SETTINGS);
        configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM ${dataform.projectConfig.vars.gold_dataset}.orders\n");

        myFixture.doHighlighting();
        StringBuilder found = new StringBuilder();
        for (FoldRegion region : myFixture.getEditor().getFoldingModel().getAllFoldRegions()) {
            if (region.getPlaceholderText().contains("gold")) {
                found.append('[').append(myFixture.getEditor().getDocument().getText()
                        .substring(region.getStartOffset(), region.getEndOffset())).append(']');
            }
        }

        assertEquals("a fold inside the ${...} would render as ${gold}", "", found.toString());
    }

    public void testSettingsRefInATemplateLiteralFoldsTheWholeSubstitution() {
        myFixture.addFileToProject("workflow_settings.yaml", SETTINGS);
        configureDefinition("mart.sqlx",
                "config { type: \"table\", schema: `${dataform.projectConfig.vars.gold_dataset}` }\n\nSELECT 1 AS one\n");

        assertEquals("a fold covering only the reference renders as ${gold}",
                "${dataform.projectConfig.vars.gold_dataset}", foldedTextFor("gold"));
    }

    public void testSettingsRefOutsideATemplateLiteralFoldsTheReferenceOnly() {
        myFixture.addFileToProject("workflow_settings.yaml", SETTINGS);
        configureDefinition("mart.sqlx",
                "config { type: \"table\", schema: dataform.projectConfig.vars.gold_dataset }\n\nSELECT 1 AS one\n");

        assertEquals("dataform.projectConfig.vars.gold_dataset", foldedTextFor("gold"));
    }

    private String foldedTextFor(String placeholder) {
        myFixture.doHighlighting();
        StringBuilder covered = new StringBuilder();
        for (FoldRegion region : myFixture.getEditor().getFoldingModel().getAllFoldRegions()) {
            if (placeholder.equals(region.getPlaceholderText())) {
                covered.append(myFixture.getEditor().getDocument().getText()
                        .substring(region.getStartOffset(), region.getEndOffset()));
            }
        }
        return covered.toString();
    }

    private String textOfSingleRegion() {
        FoldRegion[] regions = myFixture.getEditor().getFoldingModel().getAllFoldRegions();
        StringBuilder covered = new StringBuilder();
        for (FoldRegion region : regions) {
            if (region.getPlaceholderText().contains("gold")) {
                covered.append(myFixture.getEditor().getDocument().getText()
                        .substring(region.getStartOffset(), region.getEndOffset()));
            }
        }
        return covered.toString();
    }
}
