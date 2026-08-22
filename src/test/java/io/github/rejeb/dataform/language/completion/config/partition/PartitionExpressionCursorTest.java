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

import io.github.rejeb.dataform.language.completion.config.partition.PartitionExpressionCursor.Target;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PartitionExpressionCursorTest {

    /**
     * Reads the expression with the caret written as a pipe, the notation the fixtures use.
     */
    private static PartitionExpressionCursor cursor(String expressionWithCaret) {
        int caret = expressionWithCaret.indexOf('|');
        return PartitionExpressionCursor.at(expressionWithCaret.replace("|", ""), caret);
    }

    @Test
    void anEmptyExpressionEditsTheWholeForm() {
        PartitionExpressionCursor cursor = cursor("|");

        assertEquals(Target.FORM, cursor.target());
        assertEquals("", cursor.prefix());
        assertEquals(0, cursor.tailLength());
        assertNull(cursor.form());
    }

    @Test
    void aCaretOnTheFunctionNameEditsTheWholeForm() {
        PartitionExpressionCursor cursor = cursor("TIMESTAMP_TRU|NC(order_ts, DAY)");

        assertEquals(Target.FORM, cursor.target());
        assertEquals("TIMESTAMP_TRU", cursor.prefix());
        assertEquals("NC(order_ts, DAY)".length(), cursor.tailLength());
    }

    @Test
    void aCaretOnTheFirstArgumentEditsTheColumn() {
        PartitionExpressionCursor cursor = cursor("TIMESTAMP_TRUNC(order|_ts, DAY)");

        assertEquals(Target.COLUMN, cursor.target());
        assertEquals("order", cursor.prefix());
        assertEquals("_ts".length(), cursor.tailLength());
        assertNotNull(cursor.form());
        assertEquals("TIMESTAMP_TRUNC", cursor.form().functionName());
    }

    @Test
    void aCaretOnTheSecondArgumentEditsTheGranularity() {
        PartitionExpressionCursor cursor = cursor("TIMESTAMP_TRUNC(order_ts, DA|Y)");

        assertEquals(Target.GRANULARITY, cursor.target());
        assertEquals("DA", cursor.prefix());
        assertEquals(1, cursor.tailLength());
    }

    @Test
    void theSpaceBeforeAnArgumentIsNotPartOfItsPrefix() {
        PartitionExpressionCursor cursor = cursor("TIMESTAMP_TRUNC(order_ts, |DAY)");

        assertEquals(Target.GRANULARITY, cursor.target());
        assertEquals("", cursor.prefix());
        assertEquals("DAY".length(), cursor.tailLength());
    }

    @Test
    void anEmptyArgumentIsStillRecognised() {
        PartitionExpressionCursor cursor = cursor("TIMESTAMP_TRUNC(order_ts, |)");

        assertEquals(Target.GRANULARITY, cursor.target());
        assertEquals("", cursor.prefix());
        assertEquals(0, cursor.tailLength());
    }

    @Test
    void aCaretInsideTheBoundsProposesNothingForThem() {
        PartitionExpressionCursor cursor = cursor("RANGE_BUCKET(id, GENERATE_ARRAY(0, 10|0, 10))");

        assertEquals(Target.NONE, cursor.target());
    }

    @Test
    void aCaretOnTheBoundsOfARangePartitionEditsThem() {
        PartitionExpressionCursor cursor = cursor("RANGE_BUCKET(id, |GENERATE_ARRAY(0, 100, 10))");

        assertEquals(Target.BOUNDARIES, cursor.target());
        assertEquals("", cursor.prefix());
        assertEquals("GENERATE_ARRAY(0, 100, 10)".length(), cursor.tailLength());
    }

    @Test
    void boundsLeftEmptyAreStillOfferedBack() {
        PartitionExpressionCursor cursor = cursor("RANGE_BUCKET(id, |)");

        assertEquals(Target.BOUNDARIES, cursor.target());
        assertEquals(0, cursor.tailLength());
    }

    @Test
    void aHalfTypedBoundsCallIsMatchedOnWhatWasTyped() {
        PartitionExpressionCursor cursor = cursor("RANGE_BUCKET(id, GENERATE_|)");

        assertEquals(Target.BOUNDARIES, cursor.target());
        assertEquals("GENERATE_", cursor.prefix());
        assertEquals(0, cursor.tailLength());
    }

    @Test
    void theArgumentsNestedInACallDoNotSplitTheOuterOnes() {
        PartitionExpressionCursor cursor = cursor("RANGE_BUCKET(i|d, GENERATE_ARRAY(0, 100, 10))");

        assertEquals(Target.COLUMN, cursor.target());
        assertEquals("i", cursor.prefix());
        assertEquals(1, cursor.tailLength());
    }

    @Test
    void anUnclosedCallIsStillRead() {
        PartitionExpressionCursor cursor = cursor("DATE_TRUNC(day, MON|");

        assertEquals(Target.GRANULARITY, cursor.target());
        assertEquals("MON", cursor.prefix());
        assertEquals(0, cursor.tailLength());
    }

    @Test
    void anUnknownFunctionEditsTheWholeForm() {
        PartitionExpressionCursor cursor = cursor("MY_UDF(or|der_ts)");

        assertEquals(Target.FORM, cursor.target());
        assertEquals("MY_UDF(or", cursor.prefix());
    }

    @Test
    void theFunctionNameIsReadWhateverItsCase() {
        assertEquals(Target.COLUMN, cursor("timestamp_trunc(or|der_ts, DAY)").target());
    }

    @Test
    void aCaretAfterTheCallEditsTheWholeForm() {
        assertEquals(Target.FORM, cursor("DATE(order_ts) |").target());
    }

    @Test
    void aLiteralIsReadWithoutItsQuotes() {
        PartitionExpressionCursor cursor = PartitionExpressionCursor.inLiteral(
                "\"TIMESTAMP_TRUNC(order_ts, DAY)\"", "\"TIMESTAMP_TRUNC(order_ts, DA".length());

        assertEquals(Target.GRANULARITY, cursor.target());
        assertEquals("DA", cursor.prefix());
        assertEquals(1, cursor.tailLength());
    }

    @Test
    void theIdentifierTheFrameworkWritesAtTheCaretIsIgnored() {
        String literal = "\"TIMESTAMP_TRUNC(order_ts, DAIntellijIdeaRulezzzY)\"";

        PartitionExpressionCursor cursor = PartitionExpressionCursor.inLiteral(
                literal, "\"TIMESTAMP_TRUNC(order_ts, DA".length());

        assertEquals(Target.GRANULARITY, cursor.target());
        assertEquals("DA", cursor.prefix());
        assertEquals(1, cursor.tailLength());
    }

    @Test
    void theSpaceTrailingTheFrameworkIdentifierIsIgnoredToo() {
        String literal = "\"TIMESTAMP_TRUNC(order_ts, IntellijIdeaRulezzz DAY)\"";

        PartitionExpressionCursor cursor = PartitionExpressionCursor.inLiteral(
                literal, "\"TIMESTAMP_TRUNC(order_ts, ".length());

        assertEquals(Target.GRANULARITY, cursor.target());
        assertEquals("", cursor.prefix());
        assertEquals("DAY".length(), cursor.tailLength());
    }

    @Test
    void anEmptyLiteralLeavesNothingToOverwrite() {
        PartitionExpressionCursor cursor =
                PartitionExpressionCursor.inLiteral("\"IntellijIdeaRulezzz \"", 1);

        assertEquals(Target.FORM, cursor.target());
        assertEquals("", cursor.prefix());
        assertEquals(0, cursor.tailLength());
    }

    @Test
    void anUnterminatedLiteralIsStillRead() {
        PartitionExpressionCursor cursor = PartitionExpressionCursor.inLiteral(
                "\"DATE_TRUNC(day, MON", "\"DATE_TRUNC(day, MON".length());

        assertEquals(Target.GRANULARITY, cursor.target());
        assertEquals("MON", cursor.prefix());
    }
}
