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

public class SqlxTemplateFoldingBuilderTest extends DataformFoldingTestCase {

    public void testTemplateExpressionIsFoldedToItsValue() {
        PsiFile file = configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM ${ref(\"users\")}\n");
        seed(file, "ref(\"users\")", "proj.dataset.users");

        List<FoldRegion> regions = dataformRegions();

        assertEquals("expected one fold region, got " + regions, 1, regions.size());
        FoldRegion region = regions.getFirst();
        assertEquals("proj.dataset.users", region.getPlaceholderText());
        assertEquals("${ref(\"users\")}",
                myFixture.getEditor().getDocument().getText()
                        .substring(region.getStartOffset(), region.getEndOffset()));
    }

    public void testTemplateExpressionInPreOperationsIsFolded() {
        PsiFile file = configureDefinition("mart.sqlx", """
                config { type: "table" }

                pre_operations {
                  DELETE FROM ${self()} WHERE 1 = 1
                }

                SELECT 1 AS one
                """);
        seed(file, "self()", "proj.dataset.mart");

        List<FoldRegion> regions = dataformRegions();

        assertEquals("expected one fold region, got " + regions, 1, regions.size());
        assertEquals("proj.dataset.mart", regions.getFirst().getPlaceholderText());
    }

    public void testEachExpressionGetsItsOwnFoldingGroup() {
        PsiFile file = configureDefinition("mart.sqlx", """
                config { type: "table" }

                SELECT * FROM ${ref("users")} JOIN ${ref("orders")} USING (id)
                """);
        seed(file, "ref(\"users\")", "proj.dataset.users");
        seed(file, "ref(\"orders\")", "proj.dataset.orders");

        List<FoldRegion> regions = dataformRegions();

        assertEquals("got " + regions, 2, regions.size());
        assertNotSame("regions sharing a group expand and collapse together",
                regions.get(0).getGroup(), regions.get(1).getGroup());
    }

    public void testExpandingOneFoldLeavesTheOthersCollapsed() {
        PsiFile file = configureDefinition("mart.sqlx", """
                config { type: "table" }

                SELECT * FROM ${ref("users")} JOIN ${ref("orders")} USING (id)
                """);
        seed(file, "ref(\"users\")", "proj.dataset.users");
        seed(file, "ref(\"orders\")", "proj.dataset.orders");

        List<FoldRegion> regions = dataformRegions();
        myFixture.getEditor().getFoldingModel()
                .runBatchFoldingOperation(() -> regions.getFirst().setExpanded(true));

        List<FoldRegion> afterExpand = dataformRegions();

        assertEquals("got " + afterExpand, 2, afterExpand.size());
        assertTrue("the expanded region must stay expanded", afterExpand.getFirst().isExpanded());
        assertFalse("the other region must stay collapsed", afterExpand.get(1).isExpanded());
    }

    public void testFoldsSurviveFurtherHighlightingPasses() {
        PsiFile file = configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM ${ref(\"users\")}\n");
        seed(file, "ref(\"users\")", "proj.dataset.users");

        assertEquals(1, dataformRegions().size());
        assertEquals(1, dataformRegions().size());
        assertFalse("the fold must stay collapsed across passes", dataformRegions().getFirst().isExpanded());
    }

    public void testUnresolvedTemplateExpressionIsNotFolded() {
        configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM ${ref(\"users\")}\n");

        assertEmpty(dataformRegions());
    }

    public void testFoldingIsDisabledBySetting() {
        PsiFile file = configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM ${ref(\"users\")}\n");
        seed(file, "ref(\"users\")", "proj.dataset.users");

        io.github.rejeb.dataform.language.settings.DataformToolsSettings settings =
                io.github.rejeb.dataform.language.settings.DataformToolsSettings.getInstance();
        settings.setFoldTemplateExpressions(false);
        try {
            assertEmpty(dataformRegions());
        } finally {
            settings.setFoldTemplateExpressions(true);
        }
    }
}
