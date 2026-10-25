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

import io.github.rejeb.dataform.language.columns.rename.downstream.DownstreamRename;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DownstreamRenamesTest {

    private static final String CUSTOMERS = "proj.ds.customers";
    private static final String STAR = "proj.ds.all_customers";
    private static final String ORDERS = "proj.ds.orders";
    private static final String GOLD = "proj.ds.gold";

    private static ColumnChange renamed(String table, String from, String to) {
        return new ColumnChange(table, ColumnChange.Kind.RENAMED, from, to, "STRING", "STRING");
    }

    @Test
    void aRenamePassedOnByAStarBelongsToTheActionItWasMadeIn() {
        List<DownstreamRename> renames = DownstreamRenames.of(
                List.of(renamed(CUSTOMERS, "email", "customer_email"), renamed(STAR, "email", "customer_email")),
                Map.of(STAR, Set.of(CUSTOMERS), GOLD, Set.of(STAR)));

        assertEquals(List.of(new DownstreamRename(CUSTOMERS, "email", "customer_email", Set.of(CUSTOMERS, STAR))),
                renames);
    }

    @Test
    void removedAndRetypedColumnsAreNoRenames() {
        List<DownstreamRename> renames = DownstreamRenames.of(List.of(
                new ColumnChange(ORDERS, ColumnChange.Kind.REMOVED, "order_id", null, "INT64", null),
                new ColumnChange(ORDERS, ColumnChange.Kind.TYPE_CHANGED, "amount", "amount", "INT64", "NUMERIC"),
                renamed(ORDERS, "ts", "order_ts")), Map.of(GOLD, Set.of(ORDERS)));

        assertEquals(List.of(new DownstreamRename(ORDERS, "ts", "order_ts", Set.of(ORDERS))), renames);
    }
}
