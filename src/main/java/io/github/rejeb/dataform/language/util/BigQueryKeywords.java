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
package io.github.rejeb.dataform.language.util;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;

/**
 * The reserved keywords of GoogleSQL, the dialect of BigQuery: words that can never name a table,
 * a column or an alias without backquotes.
 */
public final class BigQueryKeywords {

    private static final Set<String> RESERVED = Set.of(
            "ALL", "AND", "ANY", "ARRAY", "AS", "ASC", "ASSERT_ROWS_MODIFIED", "AT", "BETWEEN", "BY",
            "CASE", "CAST", "COLLATE", "CONTAINS", "CREATE", "CROSS", "CUBE", "CURRENT", "DEFAULT",
            "DEFINE", "DESC", "DISTINCT", "ELSE", "END", "ENUM", "ESCAPE", "EXCEPT", "EXCLUDE",
            "EXISTS", "EXTRACT", "FALSE", "FETCH", "FOLLOWING", "FOR", "FROM", "FULL", "GROUP",
            "GROUPING", "GROUPS", "HASH", "HAVING", "IF", "IGNORE", "IN", "INNER", "INTERSECT",
            "INTERVAL", "INTO", "IS", "JOIN", "LATERAL", "LEFT", "LIKE", "LIMIT", "LOOKUP", "MERGE",
            "NATURAL", "NEW", "NO", "NOT", "NULL", "NULLS", "OF", "ON", "OR", "ORDER", "OUTER",
            "OVER", "PARTITION", "PRECEDING", "PROTO", "QUALIFY", "RANGE", "RECURSIVE", "RESPECT",
            "RIGHT", "ROLLUP", "ROWS", "SELECT", "SET", "SOME", "STRUCT", "TABLESAMPLE", "THEN", "TO",
            "TREAT", "TRUE", "UNBOUNDED", "UNION", "UNNEST", "USING", "WHEN", "WHERE", "WINDOW",
            "WITH", "WITHIN");

    private BigQueryKeywords() {
    }

    /**
     * Whether a word is a reserved keyword, ignoring case.
     */
    public static boolean isReserved(@NotNull String word) {
        return RESERVED.contains(word.toUpperCase(Locale.ROOT));
    }
}
