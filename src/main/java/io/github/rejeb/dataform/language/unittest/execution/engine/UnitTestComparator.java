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
package io.github.rejeb.dataform.language.unittest.execution.engine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UnitTestComparator {

    private UnitTestComparator() {
    }

    /**
     * Compares the rows of the test query with the expected rows the way {@code dataform test} does:
     * same row count, same columns ignoring case, then row by row in order.
     */
    @NotNull
    public static UnitTestOutcome compare(@NotNull UnitTestRows actual, @NotNull UnitTestRows expected) {
        if (actual.rows().size() != expected.rows().size()) {
            return failed(List.of("Expected " + expected.rows().size() + " rows, but saw "
                    + actual.rows().size() + " rows."), actual, expected);
        }
        if (actual.rows().isEmpty()) {
            return UnitTestOutcome.success();
        }
        Map<String, Integer> expectedIndex = indexByName(expected.columns());
        if (actual.columns().size() != expected.columns().size()
                || !indexByName(actual.columns()).keySet().equals(expectedIndex.keySet())) {
            return failed(List.of("Expected columns \"" + String.join(",", expected.columns())
                    + "\", but saw \"" + String.join(",", actual.columns()) + "\"."), actual, expected);
        }
        List<String> messages = new ArrayList<>();
        for (int row = 0; row < actual.rows().size(); row++) {
            List<Object> actualRow = actual.rows().get(row);
            List<Object> expectedRow = expected.rows().get(row);
            for (int column = 0; column < actual.columns().size(); column++) {
                String name = actual.columns().get(column);
                Object expectedValue = expectedRow.get(expectedIndex.get(normalize(name)));
                Object actualValue = actualRow.get(column);
                String message = mismatch(row, name, expectedValue, actualValue);
                if (message == null) {
                    continue;
                }
                messages.add(message);
                if (expectedValue == null || actualValue == null
                        || !UnitTestValues.typeOf(expectedValue).equals(UnitTestValues.typeOf(actualValue))) {
                    break;
                }
            }
        }
        return messages.isEmpty() ? UnitTestOutcome.success() : failed(messages, actual, expected);
    }

    @Nullable
    private static String mismatch(int row, @NotNull String column, @Nullable Object expected, @Nullable Object actual) {
        if (expected == null && actual == null) {
            return null;
        }
        String at = "For row " + row + " and column \"" + column + "\": ";
        if (expected == null) {
            return at + "expected null, but saw \"" + UnitTestValues.render(actual) + "\".";
        }
        if (actual == null) {
            return at + "expected \"" + UnitTestValues.render(expected) + "\", but saw null.";
        }
        String expectedType = UnitTestValues.typeOf(expected);
        String actualType = UnitTestValues.typeOf(actual);
        if (!expectedType.equals(actualType)) {
            return at + "expected type \"" + expectedType + "\", but saw type \"" + actualType + "\".";
        }
        String expectedText = UnitTestValues.render(expected);
        String actualText = UnitTestValues.render(actual);
        return expectedText.equals(actualText)
                ? null
                : at + "expected \"" + expectedText + "\", but saw \"" + actualText + "\".";
    }

    @NotNull
    private static Map<String, Integer> indexByName(@NotNull List<String> columns) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            index.put(normalize(columns.get(i)), i);
        }
        return index;
    }

    @NotNull
    private static String normalize(@NotNull String column) {
        return column.toUpperCase(Locale.ROOT);
    }

    @NotNull
    private static UnitTestOutcome failed(@NotNull List<String> messages,
                                          @NotNull UnitTestRows actual,
                                          @NotNull UnitTestRows expected) {
        return UnitTestOutcome.failed(messages, UnitTestValues.renderTable(expected), UnitTestValues.renderTable(actual));
    }
}
