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
package io.github.rejeb.dataform.language.diagnostics.sql.hint;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * What a query can read at the place of an error: the columns of its sources, the functions of
 * BigQuery and the actions of the project. Hints draw their suggestions from it.
 */
public interface SqlScope {

    /**
     * The columns of each source the query reads, keyed by the name the query reads it by, its
     * alias or its table name, in FROM order.
     */
    @NotNull Map<String, List<String>> columnsBySource();

    /**
     * Every column the sources of the query hold, in FROM order, each name once.
     */
    default @NotNull List<String> columns() {
        return columnsBySource().values().stream().flatMap(List::stream).distinct().toList();
    }

    /**
     * The names of the BigQuery functions.
     */
    @NotNull Collection<String> functions();

    /**
     * The names of the actions of the project, which {@code ${ref()}} accepts.
     */
    @NotNull Collection<String> actionNames();

    /**
     * The host offset a column is appended to the GROUP BY of the query at, or {@code -1} when the
     * query has none.
     */
    int groupByEnd();

    /**
     * The select items of the query producing an output column of that name, in order.
     */
    @NotNull List<SelectItem> selectItemsNamed(@NotNull String name);

    /**
     * The host offset the SQL of the query starts at, past the config and js blocks before it, or
     * {@code 0} when that cannot be told.
     */
    default int sqlStart() {
        return 0;
    }

    /**
     * A select item: its host range, and the host range of its alias when it has one.
     */
    record SelectItem(@NotNull TextRange range, @Nullable TextRange alias) {
    }

    SqlScope EMPTY = new SqlScope() {
        @Override
        public @NotNull Map<String, List<String>> columnsBySource() {
            return Map.of();
        }

        @Override
        public @NotNull Collection<String> functions() {
            return List.of();
        }

        @Override
        public @NotNull Collection<String> actionNames() {
            return List.of();
        }

        @Override
        public int groupByEnd() {
            return -1;
        }

        @Override
        public @NotNull List<SelectItem> selectItemsNamed(@NotNull String name) {
            return List.of();
        }
    };
}
