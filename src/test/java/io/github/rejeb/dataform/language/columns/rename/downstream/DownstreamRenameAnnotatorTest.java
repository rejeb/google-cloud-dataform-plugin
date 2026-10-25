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
package io.github.rejeb.dataform.language.columns.rename.downstream;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;
import io.github.rejeb.dataform.language.columns.impact.ColumnChange;
import io.github.rejeb.dataform.language.columns.impact.ColumnImpact;
import io.github.rejeb.dataform.language.columns.impact.ColumnImpactService;
import io.github.rejeb.dataform.language.columns.impact.ColumnImpactServiceImpl;
import io.github.rejeb.dataform.language.columns.rename.ColumnRenameFixture;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.DryRunBuildProblem;
import io.github.rejeb.dataform.language.schema.sql.DryRunErrorRegistry;

import java.util.List;
import java.util.Set;

public class DownstreamRenameAnnotatorTest extends ColumnRenameFixture {

    private static final String CUSTOMERS_SQLX =
            "config { type: \"table\" }\n\nSELECT 1 AS customer_id, 'a' AS customer_email\n";
    private static final String FAILURE = "Unrecognized name: email at [3:20]";

    private void installRenameFailingSilver() {
        installProject(
                new Action("customers", "SELECT 1 AS customer_id, 'a' AS customer_email",
                        List.of(), List.of("customer_id", "customer_email")),
                new Action("silver", "SELECT customer_id, email FROM `p.d.customers`",
                        List.of("customers"), List.of("customer_id", "email")));
        addFile("silver", "config { type: \"table\" }\n\nSELECT customer_id, email\nFROM ${ref(\"customers\")}\n");
        addFile("customers", CUSTOMERS_SQLX);
        DryRunErrorRegistry.getInstance(getProject()).report("p.d.silver", FAILURE);
        ColumnChange change = new ColumnChange("p.d.customers", ColumnChange.Kind.RENAMED,
                "email", "customer_email", "STRING", "STRING");
        ((ColumnImpactServiceImpl) ColumnImpactService.getInstance(getProject())).publish(
                List.of(new ColumnImpact(change, List.of(new DryRunBuildProblem("p.d.silver", null, 0, 0,
                        "Unrecognized name: email", FAILURE, "email")))),
                List.of(new DownstreamRename("p.d.customers", "email", "customer_email", Set.of("p.d.customers"))));
    }

    private List<HighlightInfo> renameWarnings() {
        return myFixture.doHighlighting(HighlightSeverity.WARNING).stream()
                .filter(info -> info.getDescription() != null && info.getDescription().contains("downstream"))
                .toList();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            DryRunErrorRegistry.getInstance(getProject()).clear("p.d.silver");
        } finally {
            super.tearDown();
        }
    }

    public void testTheRenamedColumnOffersToRenameItsDownstreamUsages() {
        installRenameFailingSilver();

        List<HighlightInfo> warnings = renameWarnings();

        assertEquals(1, warnings.size());
        HighlightInfo warning = warnings.getFirst();
        assertEquals("customer_email", CUSTOMERS_SQLX.substring(warning.getStartOffset(), warning.getEndOffset()));
        myFixture.getEditor().getCaretModel().moveToOffset(warning.getStartOffset());
        List<String> fixes = myFixture.getAvailableIntentions().stream().map(IntentionAction::getText).toList();
        assertTrue(fixes.toString(), fixes.contains("Rename downstream usages of `email` to `customer_email`"));
    }

    public void testNoWarningOnceTheDownstreamActionsReadTheNewName() {
        installRenameFailingSilver();
        DryRunErrorRegistry.getInstance(getProject()).clear("p.d.silver");

        assertEquals(List.of(), renameWarnings());
    }
}
