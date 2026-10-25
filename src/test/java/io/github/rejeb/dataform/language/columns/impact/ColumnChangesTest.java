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

import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColumnChangesTest {

    private static final String CUSTOMERS = "proj.ds.customers";

    private static ColumnInfo column(String name, String type) {
        return new ColumnInfo(name, type, "NULLABLE", null);
    }

    private static List<ColumnChange> between(List<ColumnInfo> before, List<ColumnInfo> after) {
        return ColumnChanges.between(Map.of(CUSTOMERS, before), Map.of(CUSTOMERS, after));
    }

    @Test
    void aColumnReplacedInPlaceByOneOfTheSameTypeIsARename() {
        List<ColumnChange> changes = between(
                List.of(column("customer_id", "INT64"), column("email", "STRING")),
                List.of(column("customer_id", "INT64"), column("customer_email", "STRING")));

        assertEquals(List.of(new ColumnChange(CUSTOMERS, ColumnChange.Kind.RENAMED,
                "email", "customer_email", "STRING", "STRING")), changes);
        assertEquals("Column `email` was renamed to `customer_email` in `customers`", changes.getFirst().describe());
    }

    @Test
    void aColumnThatIsGoneIsRemoved() {
        List<ColumnChange> changes = between(
                List.of(column("customer_id", "INT64"), column("email", "STRING")),
                List.of(column("customer_id", "INT64")));

        assertEquals(ColumnChange.Kind.REMOVED, changes.getFirst().kind());
        assertEquals("Column `email` no longer exists in `customers`", changes.getFirst().describe());
    }

    @Test
    void aReplacementOfAnotherTypeIsARemoval() {
        List<ColumnChange> changes = between(
                List.of(column("email", "STRING")),
                List.of(column("signup_date", "DATE")));

        assertEquals(List.of(ColumnChange.Kind.REMOVED), changes.stream().map(ColumnChange::kind).toList());
    }

    @Test
    void aNewTypeUnderTheSameNameIsATypeChange() {
        List<ColumnChange> changes = between(List.of(column("customer_id", "INT64")),
                List.of(column("customer_id", "STRING")));

        assertEquals("Column `customer_id` changed type from INT64 to STRING in `customers`",
                changes.getFirst().describe());
    }

    @Test
    void namesAreComparedWithoutRegardToCase() {
        assertEquals(List.of(), between(List.of(column("Email", "STRING")), List.of(column("email", "STRING"))));
    }

    @Test
    void addedColumnsAndUnknownTablesChangeNothing() {
        assertEquals(List.of(), between(List.of(column("a", "INT64")),
                List.of(column("a", "INT64"), column("b", "INT64"))));
        assertEquals(List.of(), ColumnChanges.between(Map.of(), Map.of(CUSTOMERS, List.of(column("a", "INT64")))));
        assertEquals(List.of(), ColumnChanges.between(Map.of(CUSTOMERS, List.of(column("a", "INT64"))), Map.of()));
    }
}
