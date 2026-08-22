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

import com.intellij.psi.PsiFile;

import java.util.List;

/**
 * The reference model of the {@code user_purchase} project, one assertion per row of the design's
 * baseline table. The rows that already held before this feature are pinned here beside the ones
 * it adds, so a regression in either is a failure of the same test.
 */
public class ColumnReferenceBaselineTest extends DataformColumnResolveExtensionTest {

    public void testBronzeSelectItemReadsTheUnnestedStruct() throws Exception {
        PsiFile bronze = open("bronze/bronze_orders.sqlx");
        List<String> targets = targetsAt(bronze, 23, "order_id");
        assertTrue("bronze line 23 reads the struct field on line 29, got " + targets,
                targets.contains("psi@29"));
    }

    public void testBronzeSelectItemDeclaresItsOwnColumn() throws Exception {
        PsiFile bronze = open("bronze/bronze_orders.sqlx");
        List<String> targets = targetsAt(bronze, 23, "order_id");
        assertTrue("bronze line 23 declares bronze_orders.order_id, got " + targets,
                targets.contains("column:bronze_orders.order_id"));
    }

    public void testSilverSelectItemReadsBronzeAndDeclaresSilver() throws Exception {
        PsiFile silver = open("silver/silver_orders.sqlx");
        List<String> targets = targetsAt(silver, 23, "order_id");
        assertTrue("origin, got " + targets, targets.contains("column:bronze_orders.order_id"));
        assertTrue("declares, got " + targets, targets.contains("column:silver_orders.order_id"));
    }

    public void testGoldCteItemReadsSilver() throws Exception {
        PsiFile gold = open("gold/gold_customer_ltv.sqlx");
        List<String> targets = targetsAt(gold, 17, "o.order_id");
        assertTrue("gold line 17 reads silver's column, got " + targets,
                targets.contains("column:silver_orders.order_id"));
    }

    public void testGoldFinalItemDeclaresGold() throws Exception {
        PsiFile gold = open("gold/gold_customer_ltv.sqlx");
        List<String> targets = targetsAt(gold, 31, "co.order_id");
        assertTrue("declares, got " + targets,
                targets.contains("column:gold_customer_ltv.order_id"));
    }

    /**
     * The CTE item of gold line 17 is not among the targets of line 31. Contributing it needs a
     * {@code DasSymbol}, and a select-list item is plain SQL PSI, so resolution cannot carry it.
     * This records the gap rather than asserting a behaviour the platform does not allow.
     */
    public void testGoldFinalItemDoesNotYetReachTheCteItem() throws Exception {
        PsiFile gold = open("gold/gold_customer_ltv.sqlx");
        List<String> targets = targetsAt(gold, 31, "co.order_id");
        assertFalse("the CTE hop is not delivered through resolution, got " + targets,
                targets.contains("psi@17"));
    }
}
