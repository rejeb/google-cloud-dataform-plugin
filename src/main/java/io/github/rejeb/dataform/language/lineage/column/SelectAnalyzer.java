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
package io.github.rejeb.dataform.language.lineage.column;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Maps the output columns of a SELECT query to their input columns and exposes the
 * source aliases declared in its FROM/JOIN clause.
 */
public interface SelectAnalyzer {

    @NotNull Map<String, List<InputColumn>> analyze(@NotNull String sql);

    @NotNull Map<String, String> fromAliases(@NotNull String sql);

    /**
     * Parses the SQL once and returns both the output-to-input map and the FROM aliases.
     * The default parses twice; implementations should override to parse a single time.
     */
    default @NotNull QueryAnalysis analyzeQuery(@NotNull String sql) {
        return new QueryAnalysis(analyze(sql), fromAliases(sql));
    }

    /** Combined result of analysing a query: output columns and FROM aliases. */
    record QueryAnalysis(@NotNull Map<String, List<InputColumn>> outputs,
                         @NotNull Map<String, String> aliases) {
        static final QueryAnalysis EMPTY = new QueryAnalysis(Map.of(), Map.of());
    }
}
