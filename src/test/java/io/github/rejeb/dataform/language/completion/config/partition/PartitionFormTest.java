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
package io.github.rejeb.dataform.language.completion.config.partition;

import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartitionFormTest {

    private static ColumnInfo column(String name, String type) {
        return new ColumnInfo(name, type, "NULLABLE", null);
    }

    private static List<String> labels(List<ColumnInfo> columns) {
        return PartitionForm.availableFor(columns).stream().map(PartitionForm::label).toList();
    }

    @Test
    void offersOnlyTheFormsTheColumnTypesAccept() {
        List<String> labels = labels(List.of(column("signup_date", "DATE")));

        assertTrue(labels.contains("DATE_TRUNC(..., MONTH)"), () -> "got " + labels);
        assertFalse(labels.contains("DATE(...)"), () -> "got " + labels);
        assertFalse(labels.contains("DATETIME_TRUNC(..., DAY)"), () -> "got " + labels);
        assertFalse(labels.contains("RANGE_BUCKET(..., GENERATE_ARRAY(0, 100, 10))"),
                () -> "got " + labels);
    }

    @Test
    void theTimestampTruncationStaysAvailableForTheIngestionTimePseudoColumn() {
        List<String> labels = labels(List.of(column("name", "STRING")));

        assertTrue(labels.contains("TIMESTAMP_TRUNC(..., DAY)"), () -> "got " + labels);
    }

    @Test
    void offersTheTimestampFormsForATimestampColumn() {
        List<String> labels = labels(List.of(column("event_ts", "TIMESTAMP")));

        assertTrue(labels.contains("TIMESTAMP_TRUNC(..., DAY)"), () -> "got " + labels);
        assertTrue(labels.contains("DATE(...)"), () -> "got " + labels);
        assertFalse(labels.contains("DATETIME_TRUNC(..., DAY)"), () -> "got " + labels);
    }

    @Test
    void offersRangeBucketForBothSpellingsOfTheIntegerType() {
        assertTrue(labels(List.of(column("id", "INTEGER")))
                .contains("RANGE_BUCKET(..., GENERATE_ARRAY(0, 100, 10))"));
        assertTrue(labels(List.of(column("id", "INT64")))
                .contains("RANGE_BUCKET(..., GENERATE_ARRAY(0, 100, 10))"));
    }

    @Test
    void alwaysOffersIngestionTimePartitioning() {
        List<String> labels = labels(List.of(column("name", "STRING")));

        assertTrue(labels.contains("_PARTITIONDATE"), () -> "got " + labels);
        assertEquals(List.of(PartitionForm.PARTITION_TIME),
                PartitionForm.byFunction("TIMESTAMP_TRUNC").argumentsIn(
                        List.of(column("name", "STRING"))));
    }

    @Test
    void aBareTimestampColumnIsNoPartitionExpression() {
        assertEquals(Set.of("DATE"), PartitionForm.BARE_COLUMN_TYPES);
    }

    @Test
    void offersNoColumnFormWhenNoColumnTypeAcceptsOne() {
        List<String> labels = labels(List.of(column("name", "STRING"), column("ok", "BOOLEAN")));

        assertEquals(List.of("TIMESTAMP_TRUNC(..., DAY)", "_PARTITIONDATE"), labels);
    }

    @Test
    void offersEveryFormWhenTheSchemaIsUnknown() {
        assertEquals(6, PartitionForm.availableFor(List.of()).size());
    }

    @Test
    void aRecordColumnPartitionsNothing() {
        ColumnInfo record = new ColumnInfo("payload", "RECORD", "NULLABLE", null,
                List.of(column("at", "TIMESTAMP")));

        assertTrue(PartitionForm.namedColumnsOf(
                PartitionForm.byFunction("DATE"), List.of(record)).isEmpty());
    }

    @Test
    void aRepeatedColumnPartitionsNothing() {
        ColumnInfo repeated = new ColumnInfo("days", "DATE", "REPEATED", null);

        assertTrue(PartitionColumns.ofTypes(
                List.of(repeated), PartitionForm.BARE_COLUMN_TYPES).isEmpty());
    }

    @Test
    void aFormIsWrittenOnAColumnWithItsRemainingPlaceholdersAtTheirDefaults() {
        PartitionForm truncation = PartitionForm.byFunction("TIMESTAMP_TRUNC");

        assertNotNull(truncation);
        assertEquals("TIMESTAMP_TRUNC(order_ts, DAY)", truncation.labelOn("order_ts"));
        assertEquals("TIMESTAMP_TRUNC(order_ts, $granularity$)", truncation.templateOn("order_ts"));
    }

    @Test
    void theRangeFormIsWrittenOnAColumnWithItsBoundsAtTheirDefaults() {
        PartitionForm range = PartitionForm.byFunction("RANGE_BUCKET");

        assertNotNull(range);
        assertEquals("RANGE_BUCKET(customer_id, GENERATE_ARRAY(0, 100, 10))",
                range.labelOn("customer_id"));
    }

    @Test
    void anIntegerColumnIsNotABarePartitionKey() {
        List<ColumnInfo> columns = List.of(column("id", "INTEGER"), column("day", "DATE"));

        List<String> bare = PartitionColumns.ofTypes(columns, PartitionForm.BARE_COLUMN_TYPES)
                .stream().map(ColumnInfo::name).toList();

        assertEquals(List.of("day"), bare);
    }

    @Test
    void everyFormWithPlaceholdersDeclaresAColumnStop() {
        for (PartitionForm form : PartitionForm.availableFor(List.of())) {
            if (form.hasPlaceholders()) {
                assertTrue(form.takesColumn(), () -> "got " + form.template());
            }
        }
    }

    @Test
    void onlyTheRangeFormTakesBounds() {
        for (PartitionForm form : PartitionForm.availableFor(List.of())) {
            if ("RANGE_BUCKET".equals(form.functionName())) {
                assertEquals("GENERATE_ARRAY(0, 100, 10)", form.boundariesLabel());
            } else {
                assertTrue(form.boundaries().isEmpty(), () -> "got " + form.label());
            }
        }
    }

    @Test
    void theBoundsAreTheSecondArgumentOfTheRangeForm() {
        PartitionForm range = PartitionForm.byFunction("RANGE_BUCKET");

        assertNotNull(range);
        assertEquals("RANGE_BUCKET($col$, " + range.boundaries() + ")", range.template());
    }

    @Test
    void theDefaultGranularityIsDayWhenTheFormOffersIt() {
        for (PartitionForm form : PartitionForm.availableFor(List.of())) {
            if (form.granularities().isEmpty()) {
                continue;
            }
            assertEquals(form.granularities().contains("DAY") ? "DAY" : "MONTH",
                    form.defaultGranularity());
        }
    }
}
