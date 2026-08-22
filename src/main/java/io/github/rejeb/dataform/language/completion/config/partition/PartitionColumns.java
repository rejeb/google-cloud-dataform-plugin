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
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Selects the columns of an action a BigQuery partitioning expression can be built on.
 *
 * <p>A partition key is a single scalar column of a date, time or integer type, which leaves out
 * records, repeated columns and every other type whatever its name.</p>
 */
public final class PartitionColumns {

    /**
     * The column types a partitioning expression accepts, in the spellings the schema extractor
     * produces for them.
     */
    public static final String DATE = "DATE";
    public static final String DATETIME = "DATETIME";
    public static final String TIMESTAMP = "TIMESTAMP";
    public static final String INTEGER = "INTEGER";

    private static final Set<String> INTEGER_SPELLINGS = Set.of("INTEGER", "INT64");

    private PartitionColumns() {
    }

    /**
     * Returns the columns of the given type a partitioning expression can be built on.
     */
    @NotNull
    public static List<ColumnInfo> ofTypes(@NotNull List<ColumnInfo> columns,
                                           @NotNull Set<String> types) {
        return columns.stream().filter(column -> isOfType(column, types)).toList();
    }

    /**
     * Tells whether the given column can carry a partitioning expression of one of the given types.
     */
    public static boolean isOfType(@NotNull ColumnInfo column, @NotNull Set<String> types) {
        return !column.isRecord() && !column.isRepeated() && types.contains(normalize(column.type()));
    }

    /**
     * Reduces a column type to the spelling the forms are declared with, so that the two names
     * BigQuery gives its integer type are recognised alike.
     */
    @NotNull
    public static String normalize(@NotNull String type) {
        String upper = type.toUpperCase();
        return INTEGER_SPELLINGS.contains(upper) ? INTEGER : upper;
    }
}
