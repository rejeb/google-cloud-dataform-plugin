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
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public final class TestSelectGenerator {

    private static final String ITEM_INDENT = "  ";

    private TestSelectGenerator() {
    }

    /**
     * Returns a {@code SELECT} listing every given column under its own name with a placeholder of
     * its type, one item per line. Every line after the first starts with {@code indent}; the text
     * ends without a line break.
     */
    @NotNull
    public static String select(@NotNull List<ColumnInfo> columns, @NotNull String indent) {
        return columns.stream()
                .map(column -> indent + ITEM_INDENT + BigQueryPlaceholderValues.of(column)
                        + " AS " + BigQueryPlaceholderValues.identifier(column.name()))
                .collect(Collectors.joining(",\n", "SELECT\n", ""));
    }
}
