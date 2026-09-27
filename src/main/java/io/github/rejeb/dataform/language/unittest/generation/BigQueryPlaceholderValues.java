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
package io.github.rejeb.dataform.language.unittest.generation;

import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.util.BigQueryKeywords;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class BigQueryPlaceholderValues {

    private static final Pattern PLAIN_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private BigQueryPlaceholderValues() {
    }

    /**
     * Returns a literal of the column's type: a typed placeholder for a scalar, a {@code STRUCT}
     * built field by field for a record, wrapped in a one-element array for a repeated column.
     */
    @NotNull
    public static String of(@NotNull ColumnInfo column) {
        String single = column.isRecord() ? struct(column.subFields()) : scalar(column.type());
        return column.isRepeated() ? "[" + single + "]" : single;
    }

    /**
     * Returns the column name as BigQuery accepts it as an alias, backquoted when it is a reserved
     * word or not a plain identifier.
     */
    @NotNull
    public static String identifier(@NotNull String name) {
        return PLAIN_IDENTIFIER.matcher(name).matches() && !BigQueryKeywords.isReserved(name)
                ? name
                : "`" + name + "`";
    }

    private static String struct(@NotNull List<ColumnInfo> fields) {
        return fields.stream()
                .map(field -> of(field) + " AS " + identifier(field.name()))
                .collect(Collectors.joining(", ", "STRUCT(", ")"));
    }

    private static String scalar(@NotNull String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "STRING" -> "''";
            case "INT64", "INTEGER" -> "0";
            case "FLOAT64", "FLOAT" -> "0.0";
            case "NUMERIC" -> "NUMERIC '0'";
            case "BIGNUMERIC" -> "BIGNUMERIC '0'";
            case "BOOL", "BOOLEAN" -> "FALSE";
            case "BYTES" -> "b''";
            case "DATE" -> "DATE '1970-01-01'";
            case "DATETIME" -> "DATETIME '1970-01-01 00:00:00'";
            case "TIME" -> "TIME '00:00:00'";
            case "TIMESTAMP" -> "TIMESTAMP '1970-01-01 00:00:00 UTC'";
            case "JSON" -> "JSON 'null'";
            case "GEOGRAPHY" -> "ST_GEOGPOINT(0, 0)";
            case "INTERVAL" -> "INTERVAL 0 DAY";
            default -> "CAST(NULL AS " + type + ")";
        };
    }
}
