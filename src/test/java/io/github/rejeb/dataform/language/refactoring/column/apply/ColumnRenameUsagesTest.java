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
package io.github.rejeb.dataform.language.refactoring.column.apply;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import io.github.rejeb.dataform.language.refactoring.column.ColumnRenameFixture;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlan;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlanner;
import io.github.rejeb.dataform.language.refactoring.column.target.ColumnRenameSubject;
import io.github.rejeb.dataform.language.refactoring.column.target.ColumnRenameSubjectFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Every place a rename writes is a row of the rename window, and only the rows the user keeps are
 * written.
 */
public class ColumnRenameUsagesTest extends ColumnRenameFixture {

    private static final String SRC_TEXT = """
            config {
              type: "table",
              bigquery: { clusterBy: ["order_id"] },
              assertions: { rowConditions: ["order_id IS NOT NULL"] }
            }

            SELECT
                order_id
            FROM ${ref("raw")}
            """;

    private static final String USE_TEXT = """
            config { type: "table" }

            SELECT
                order_id
            FROM ${ref("src")}
            """;

    private ColumnRenamePlan plan(String newName) {
        installProject(
                new Action("raw", "SELECT 1 AS order_id", List.of(), List.of("order_id")),
                new Action("src", "SELECT order_id FROM `p.d.raw`", List.of("raw"), List.of("order_id")),
                new Action("use", "SELECT order_id FROM `p.d.src`", List.of("src"), List.of("order_id")));
        addFile("raw", "config { type: \"table\" }\n\nSELECT 1 AS order_id\n");
        addFile("src", SRC_TEXT);
        addFile("use", USE_TEXT);

        PsiFile src = fileOf("src");
        int offset = src.getText().indexOf("    order_id");
        ColumnRenameSubject subject = ColumnRenameSubjectFactory.at(src, offset + 5).orElseThrow();
        return ColumnRenamePlanner.getInstance(getProject()).plan(subject, newName);
    }

    public void testEveryPlaceOfThePlanIsOneRow() {
        ColumnRenamePlan plan = plan("order_ref");
        ColumnRenameProcessor processor = new ColumnRenameProcessor(getProject(), plan);

        UsageInfo[] usages = processor.findUsages();

        assertEquals("one row per place, so every place can be reviewed",
                plan.edits().size(), usages.length);
    }

    public void testEveryRowIsShownInTheFileItWrites() {
        ColumnRenamePlan plan = plan("order_ref");
        ColumnRenameProcessor processor = new ColumnRenameProcessor(getProject(), plan);
        InjectedLanguageManager injections = InjectedLanguageManager.getInstance(getProject());

        for (UsageInfo usage : processor.findUsages()) {
            PsiFile file = usage.getFile();
            assertNotNull("a row without a file cannot be shown", file);
            assertFalse("a row of an injected file makes the rename window restore the injection"
                            + " on the event thread, outside a read action: " + usage,
                    injections.isInjectedFragment(file));
            assertEquals("the row is shown in the file its place is written to",
                    ((ColumnRenameUsageInfo) usage).edit().file(), file.getVirtualFile());

            Segment segment = usage.getSegment();
            assertNotNull("a row without a range cannot be shown", segment);
            assertTrue("the row covers the name in the host file, not an offset of the injection",
                    file.getText().substring(segment.getStartOffset(), segment.getEndOffset())
                            .contains("order_id"));
        }
    }

    public void testTextFoundByMatchingIsGroupedApartFromCode() {
        ColumnRenamePlan plan = plan("order_ref");
        ColumnRenameProcessor processor = new ColumnRenameProcessor(getProject(), plan);

        List<UsageInfo> nonCode = Arrays.stream(processor.findUsages())
                .filter(UsageInfo::isNonCodeUsage).toList();

        assertEquals("the row condition is text the user reviews", 1, nonCode.size());
    }

    public void testOnlyTheRowsKeptAreWritten() {
        ColumnRenamePlan plan = plan("order_ref");
        ColumnRenameProcessor processor = new ColumnRenameProcessor(getProject(), plan);
        UsageInfo[] kept = Arrays.stream(processor.findUsages())
                .filter(usage -> usage instanceof ColumnRenameUsageInfo info
                        && "src.sqlx".equals(info.edit().file().getName()))
                .toArray(UsageInfo[]::new);

        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            processor.performRefactoring(kept);
            processor.performPsiSpoilingRefactoring();
        });

        assertTrue("the kept rows are written\n" + fileOf("src").getText(),
                fileOf("src").getText().contains("order_ref"));
        assertTrue("an excluded row keeps the old name\n" + fileOf("use").getText(),
                fileOf("use").getText().contains("order_id"));
        assertFalse("an excluded row is not written",
                fileOf("use").getText().contains("order_ref"));
    }

    public void testAPlanWithNothingToReviewIsAppliedWithoutThePreview() {
        installProject(new Action("only", "SELECT 1 AS order_id", List.of(), List.of("order_id")));
        PsiFile file = addFile("only",
                "config { type: \"table\" }\n\nSELECT 1 AS order_id\n");
        ColumnRenameSubject subject = ColumnRenameSubjectFactory
                .at(file, file.getText().indexOf("AS order_id") + 4).orElseThrow();
        ColumnRenamePlan plan = ColumnRenamePlanner.getInstance(getProject()).plan(subject, "order_ref");

        assertFalse("nothing was guessed, so there is nothing to review", plan.needsPreview());
        assertEquals("the plan of a clean rename holds only certain places", plan.edits().size(),
                new ColumnRenameProcessor(getProject(), plan).findUsages().length);
    }

    public void testAPlanHoldingAGuessIsReviewedFirst() {
        ColumnRenamePlan plan = plan("order_ref");

        assertTrue("a place found by matching text is shown before it is written",
                plan.needsPreview());
    }
}
