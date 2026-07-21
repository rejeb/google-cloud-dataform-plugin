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
package io.github.rejeb.dataform.language.lineage.graph;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable representation of a table-level node in the Dataform lineage graph.
 *
 * @param id           unique identifier within the graph, derived from {@link #idOf}.
 * @param name         short name of the action (last segment of the FQN).
 * @param fullName     fully qualified name {@code project.dataset.table}.
 * @param dataformType Dataform action type: table, view, incremental, assertion, declaration, external…
 * @param fileName     project-relative path of the source SQLX file, {@code null} for external nodes.
 */
public record LineageNode(
        @NotNull String id,
        @NotNull String name,
        @NotNull String fullName,
        @NotNull String dataformType,
        @Nullable String fileName
) {
    public static @NotNull String idOf(@NotNull String fullName) {
        return "node:" + fullName;
    }
}
