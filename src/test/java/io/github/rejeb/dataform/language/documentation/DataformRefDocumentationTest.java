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
package io.github.rejeb.dataform.language.documentation;

import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class DataformRefDocumentationTest extends BasePlatformTestCase {

    public void testTargetProvidedOnRefLiteral() {
        PsiFile file = myFixture.configureByText("test.sqlx",
                "config { type: \"table\" }\nSELECT * FROM ${ref(\"my_ta<caret>ble\")}");

        List<? extends DocumentationTarget> targets =
                new DataformRefDocumentationTargetProvider()
                        .documentationTargets(file, myFixture.getCaretOffset());

        assertFalse("expected a documentation target on the ref literal", targets.isEmpty());
        assertEquals("my_table", targets.get(0).computePresentation().getPresentableText());
    }

    public void testTargetProvidedOnResolveLiteral() {
        PsiFile file = myFixture.configureByText("test.sqlx",
                "config { type: \"table\" }\nSELECT * FROM ${resolve(\"other_ta<caret>ble\")}");

        List<? extends DocumentationTarget> targets =
                new DataformRefDocumentationTargetProvider()
                        .documentationTargets(file, myFixture.getCaretOffset());

        assertFalse(targets.isEmpty());
        assertEquals("other_table", targets.get(0).computePresentation().getPresentableText());
    }

    public void testTargetProvidedInJsDefinitionFile() {
        PsiFile file = myFixture.configureByText("stat_dynamic_tables_and_views.js",
                "statViews.forEach((view) => {\n"
                        + "    publish(view.viewName + \"_stat\").query(\n"
                        + "        ctx => `SELECT * FROM ${ctx.ref(\"team_play<caret>ers_stat\")}`\n"
                        + "    );\n"
                        + "});");

        List<? extends DocumentationTarget> targets =
                new DataformRefDocumentationTargetProvider()
                        .documentationTargets(file, myFixture.getCaretOffset());

        assertFalse("ref hover must work in .js definition files, not only .sqlx", targets.isEmpty());
        assertEquals("team_players_stat", targets.get(0).computePresentation().getPresentableText());
    }

    public void testTargetProvidedInTsDefinitionFile() {
        PsiFile file = myFixture.configureByText("defs.ts",
                "publish(\"x\").query(ctx => `SELECT * FROM ${ctx.ref(\"team_play<caret>ers_stat\")}`);");

        assertFalse(new DataformRefDocumentationTargetProvider()
                .documentationTargets(file, myFixture.getCaretOffset()).isEmpty());
    }

    public void testNoTargetInUnrelatedFileType() {
        PsiFile file = myFixture.configureByText("notes.txt",
                "SELECT * FROM ${ctx.ref(\"team_play<caret>ers_stat\")}");

        assertTrue(new DataformRefDocumentationTargetProvider()
                .documentationTargets(file, myFixture.getCaretOffset()).isEmpty());
    }

    public void testNoTargetOutsideRefCall() {
        PsiFile file = myFixture.configureByText("test.sqlx",
                "config { type: \"ta<caret>ble\" }\nSELECT 1");

        assertTrue(new DataformRefDocumentationTargetProvider()
                .documentationTargets(file, myFixture.getCaretOffset()).isEmpty());
    }

    public void testDocumentationRendersWithoutCompiledGraph() {
        PsiFile file = myFixture.configureByText("test.sqlx",
                "config { type: \"table\" }\nSELECT * FROM ${ref(\"my_ta<caret>ble\")}");

        DocumentationTarget target = new DataformRefDocumentationTargetProvider()
                .documentationTargets(file, myFixture.getCaretOffset()).get(0);

        DocumentationResult result = target.computeDocumentation();
        assertNotNull("cold cache must still render, not return null", result);
    }
}
