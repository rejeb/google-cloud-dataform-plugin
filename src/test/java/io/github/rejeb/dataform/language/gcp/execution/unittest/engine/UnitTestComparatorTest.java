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
package io.github.rejeb.dataform.language.gcp.execution.unittest.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitTestComparatorTest {

    private static UnitTestRows rows(List<String> columns, Object[]... rows) {
        List<List<Object>> values = new ArrayList<>();
        for (Object[] row : rows) {
            values.add(Arrays.asList(row));
        }
        return new UnitTestRows(columns, values);
    }

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    @Test
    void identicalRowsPass() {
        UnitTestRows rows = rows(List.of("id", "name"), new Object[]{n("1"), "a"}, new Object[]{n("2"), "b"});

        assertTrue(UnitTestComparator.compare(rows, rows).passed());
    }

    @Test
    void rowCountMismatch() {
        UnitTestOutcome outcome = UnitTestComparator.compare(
                rows(List.of("id"), new Object[]{n("1")}),
                rows(List.of("id"), new Object[]{n("1")}, new Object[]{n("2")}));

        assertFalse(outcome.passed());
        assertEquals(List.of("Expected 2 rows, but saw 1 rows."), outcome.messages());
        assertEquals("id\n1\n2", outcome.expected());
        assertEquals("id\n1", outcome.actual());
    }

    @Test
    void noRowsPassEvenWithOtherColumns() {
        assertTrue(UnitTestComparator.compare(rows(List.of("a")), rows(List.of("b"))).passed());
    }

    @Test
    void columnMismatch() {
        UnitTestOutcome outcome = UnitTestComparator.compare(
                rows(List.of("id", "other"), new Object[]{n("1"), "x"}),
                rows(List.of("id", "name"), new Object[]{n("1"), "x"}));

        assertEquals(List.of("Expected columns \"id,name\", but saw \"id,other\"."), outcome.messages());
    }

    @Test
    void columnsMatchCaseInsensitively() {
        assertTrue(UnitTestComparator.compare(
                rows(List.of("ID"), new Object[]{n("1")}),
                rows(List.of("id"), new Object[]{n("1")})).passed());
    }

    @Test
    void valueMismatchesAreReportedPerRowAndColumn() {
        UnitTestOutcome outcome = UnitTestComparator.compare(
                rows(List.of("id", "name"), new Object[]{n("1"), "a"}, new Object[]{n("3"), "b"}),
                rows(List.of("id", "name"), new Object[]{n("1"), "x"}, new Object[]{n("2"), "b"}));

        assertEquals(List.of(
                "For row 0 and column \"name\": expected \"x\", but saw \"a\".",
                "For row 1 and column \"id\": expected \"2\", but saw \"3\"."), outcome.messages());
    }

    @Test
    void nullAndTypeMismatchesStopTheRow() {
        UnitTestOutcome outcome = UnitTestComparator.compare(
                rows(List.of("a", "b"), new Object[]{"1", "y"}, new Object[]{null, "y"}),
                rows(List.of("a", "b"), new Object[]{n("1"), "x"}, new Object[]{n("1"), "x"}));

        assertEquals(List.of(
                "For row 0 and column \"a\": expected type \"number\", but saw type \"string\".",
                "For row 1 and column \"a\": expected \"1\", but saw null."), outcome.messages());
    }

    @Test
    void numbersCompareByValue() {
        assertTrue(UnitTestComparator.compare(
                rows(List.of("v"), new Object[]{n("1.0")}),
                rows(List.of("v"), new Object[]{n("1")})).passed());
    }

    @Test
    void rowOrderMatters() {
        assertFalse(UnitTestComparator.compare(
                rows(List.of("id"), new Object[]{n("2")}, new Object[]{n("1")}),
                rows(List.of("id"), new Object[]{n("1")}, new Object[]{n("2")})).passed());
    }

    @Test
    void recordsAndArraysCompareAsJson() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("k", "v");
        record.put("n", n("2.50"));

        UnitTestOutcome outcome = UnitTestComparator.compare(
                rows(List.of("r", "l"), new Object[]{record, List.of(n("1"), "a")}),
                rows(List.of("r", "l"), new Object[]{record, List.of(n("1"), "b")}));

        assertEquals(List.of("For row 0 and column \"l\": expected \"[1,\"b\"]\", but saw \"[1,\"a\"]\"."),
                outcome.messages());
        assertEquals("{\"k\":\"v\",\"n\":2.5}", UnitTestValues.render(record));
    }

    @Test
    void queryErrorsUseTheCliWording() {
        UnitTestOutcome outcome = UnitTestOutcome.error("Syntax error at [1:8]");

        assertEquals(List.of("Error thrown: Syntax error at [1:8]."), outcome.messages());
        assertNull(outcome.expected());
    }
}
