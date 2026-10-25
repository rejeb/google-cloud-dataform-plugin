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
package io.github.rejeb.dataform.language.columns.impact;

import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.DryRunBuildProblem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColumnImpactsTest {

    private static final String CUSTOMERS = "proj.ds.customers";
    private static final String ORDERS = "proj.ds.orders";
    private static final String SILVER = "proj.ds.silver_customers";
    private static final String GOLD = "proj.ds.gold_customers";

    private static final ColumnChange EMAIL = new ColumnChange(CUSTOMERS, ColumnChange.Kind.RENAMED,
            "email", "customer_email", "STRING", "STRING");
    private static final ColumnChange SILVER_EMAIL = new ColumnChange(SILVER, ColumnChange.Kind.RENAMED,
            "email", "customer_email", "STRING", "STRING");
    private static final ColumnChange ORDER_ID = new ColumnChange(ORDERS, ColumnChange.Kind.REMOVED,
            "order_id", null, "INT64", null);

    private static final Map<String, Set<String>> READS = Map.of(
            SILVER, Set.of(CUSTOMERS),
            GOLD, Set.of(SILVER, ORDERS));

    private static DryRunBuildProblem failure(String action, String subject) {
        return new DryRunBuildProblem(action, null, 0, 0, "Unrecognized name: " + subject,
                "Unrecognized name: " + subject, subject);
    }

    @Test
    void aFailureGoesToTheChangeOfTheColumnItNames() {
        DryRunBuildProblem gold = failure(GOLD, "order_id");

        List<ColumnImpact> impacts = ColumnImpacts.of(List.of(EMAIL, ORDER_ID), List.of(gold), READS);

        assertEquals(List.of(new ColumnImpact(EMAIL, List.of()), new ColumnImpact(ORDER_ID, List.of(gold))), impacts);
    }

    @Test
    void aFailureGoesToTheChangeOfTheTableTheActionReadsDirectly() {
        DryRunBuildProblem gold = failure(GOLD, "email");

        List<ColumnImpact> impacts = ColumnImpacts.of(List.of(EMAIL, SILVER_EMAIL), List.of(gold), READS);

        assertEquals(List.of(), impacts.get(0).affected());
        assertEquals(List.of(gold), impacts.get(1).affected());
    }

    @Test
    void aFailureNamingNoChangedColumnGoesToAnUpstreamChange() {
        DryRunBuildProblem silver = failure(SILVER, "something_else");

        assertEquals(List.of(silver), ColumnImpacts.of(List.of(EMAIL), List.of(silver), READS).getFirst().affected());
    }

    @Test
    void aFailureOutsideTheDependentsIsNotAnImpact() {
        DryRunBuildProblem orders = failure(ORDERS, "email");

        assertEquals(List.of(new ColumnImpact(EMAIL, List.of())),
                ColumnImpacts.of(List.of(EMAIL), List.of(orders), READS));
    }

    @Test
    void aChangeNoActionReadsIsLeftOut() {
        ColumnChange gold = new ColumnChange(GOLD, ColumnChange.Kind.REMOVED, "x", null, "INT64", null);

        assertEquals(List.of(), ColumnImpacts.of(List.of(gold), List.of(), READS));
    }
}
