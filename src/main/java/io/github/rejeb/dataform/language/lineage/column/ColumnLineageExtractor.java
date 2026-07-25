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

import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Builds a column-level {@link ColumnLineageGraph} from a {@link CompiledGraph}.
 */
public interface ColumnLineageExtractor {

    /**
     * Extracts column lineage across all model tables in the graph.
     *
     * @param graph   the compiled Dataform graph.
     * @param schemas resolved columns per table full name, used to expand {@code SELECT *}.
     * @return the assembled column lineage graph.
     */
    @NotNull ColumnLineageGraph extract(@NotNull CompiledGraph graph,
                                        @NotNull Map<String, List<ColumnInfo>> schemas);
}
