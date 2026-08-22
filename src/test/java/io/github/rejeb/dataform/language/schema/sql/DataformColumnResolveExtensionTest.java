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
package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class DataformColumnResolveExtensionTest extends DataformProjectFixture {

    protected List<String> targetsAt(PsiFile file, int line, String token) {
        PsiElement injected = injectedAt(file, line, token);
        PsiReference reference = injected.getContainingFile()
                .findReferenceAt(injected.getTextRange().getStartOffset());
        assertNotNull("the token must carry a reference", reference);
        List<String> described = new ArrayList<>();
        if (reference instanceof PsiPolyVariantReference poly) {
            for (ResolveResult result : poly.multiResolve(false)) {
                described.add(describe(result.getElement()));
            }
        } else {
            described.add(describe(reference.resolve()));
        }
        return described;
    }

    protected String describe(PsiElement element) {
        if (element == null) return "null";
        if (element instanceof DataformDasColumn column) {
            String owner = column.getTable() == null ? "?" : column.getTable().getName();
            return "column:" + owner + "." + column.getName();
        }
        return "psi@" + lineOf(element);
    }

    public void testSilverSelectItemAlsoResolvesToItsOwnColumn() throws Exception {
        PsiFile silver = open("silver/silver_orders.sqlx");
        List<String> targets = targetsAt(silver, 23, "order_id");
        assertTrue("the upstream column must still resolve, got " + targets,
                targets.contains("column:bronze_orders.order_id"));
        assertTrue("the column this item declares must resolve too, got " + targets,
                targets.contains("column:silver_orders.order_id"));
    }

    public void testGoldFinalSelectItemAlsoResolvesToItsOwnColumn() throws Exception {
        PsiFile gold = open("gold/gold_customer_ltv.sqlx");
        List<String> targets = targetsAt(gold, 31, "co.order_id");
        assertTrue("the column this item declares must resolve, got " + targets,
                targets.contains("column:gold_customer_ltv.order_id"));
    }

    public void testNoDuplicateTargets() throws Exception {
        PsiFile silver = open("silver/silver_orders.sqlx");
        List<String> targets = targetsAt(silver, 23, "order_id");
        assertEquals("duplicate targets make the resolve cache non-idempotent, got " + targets,
                targets.size(), new LinkedHashSet<>(targets).size());
    }

    public void testCteAliasStillResolvesUpstream() throws Exception {
        PsiFile gold = open("gold/gold_customer_ltv.sqlx");
        List<String> targets = targetsAt(gold, 17, "o.order_id");
        assertTrue("o is a table alias, so the upstream column stands, got " + targets,
                targets.contains("column:silver_orders.order_id"));
    }

    public void testColumnCompletionIsNotPolluted() {
        myFixture.configureByText("probe.sqlx",
                "config { type: \"table\" }\nSELECT <caret> FROM `proj.ds.bronze_orders`");
        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();
        assertNotNull("completing a select list must offer the table's columns", lookups);
        assertTrue("the table's columns must be offered, got " + lookups,
                lookups.contains("order_id"));
        assertEquals("a column must be offered once, got " + lookups,
                1, lookups.stream().filter("order_id"::equals).count());
        assertFalse("a column is offered by its bare name, never qualified, got " + lookups,
                lookups.stream().anyMatch(l -> l.contains("bronze_orders.")));
    }
}
