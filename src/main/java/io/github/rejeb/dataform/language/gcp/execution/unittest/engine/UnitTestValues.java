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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class UnitTestValues {

    private UnitTestValues() {
    }

    /**
     * Returns the JavaScript-like type name of a canonical value, as the Dataform CLI reports it.
     */
    @NotNull
    public static String typeOf(@NotNull Object value) {
        if (value instanceof BigDecimal) return "number";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof List<?>) return "array";
        if (value instanceof Map<?, ?>) return "object";
        return "string";
    }

    /**
     * Renders a canonical value for messages and comparison: numbers without trailing zeros, nested
     * values as JSON.
     */
    @NotNull
    public static String render(@Nullable Object value) {
        if (value instanceof String text) return text;
        return json(value);
    }

    /**
     * Renders rows as a header line followed by one line per row, cells separated by {@code " | "}.
     */
    @NotNull
    public static String renderTable(@NotNull UnitTestRows rows) {
        StringBuilder table = new StringBuilder(String.join(" | ", rows.columns()));
        for (List<Object> row : rows.rows()) {
            table.append('\n').append(row.stream().map(UnitTestValues::render).collect(Collectors.joining(" | ")));
        }
        return table.toString();
    }

    @NotNull
    private static String json(@Nullable Object value) {
        if (value == null) return "null";
        if (value instanceof BigDecimal number) {
            return number.signum() == 0 ? "0" : number.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Boolean bool) return bool.toString();
        if (value instanceof List<?> list) {
            return list.stream().map(UnitTestValues::json).collect(Collectors.joining(",", "[", "]"));
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> quote(String.valueOf(entry.getKey())) + ":" + json(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        return quote(value.toString());
    }

    @NotNull
    private static String quote(@NotNull String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
