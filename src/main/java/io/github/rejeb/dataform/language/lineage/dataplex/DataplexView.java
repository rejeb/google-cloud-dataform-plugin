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
package io.github.rejeb.dataform.language.lineage.dataplex;

import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The recorded lineage around a table, ready to be drawn.
 *
 * @param snapshot         what was loaded
 * @param graph            the graph of the loaded entities
 * @param range            how far back the links go
 * @param scope            where the lineage was read
 * @param repository       the lineage graph of the repository the entities were matched against
 * @param assertionNodeIds the node ids of the assertion views, left out of the graph
 */
public record DataplexView(@NotNull DataplexSnapshot snapshot, @NotNull LineageGraph graph, @NotNull TimeRange range,
                           @NotNull LineageScope scope, @NotNull LineageGraph repository,
                           @NotNull Set<String> assertionNodeIds) {

    /**
     * @return the fully qualified name of the loaded entity drawn as the given node, or
     * {@code null} when the node is not a loaded entity
     */
    public @Nullable String fqnOf(@NotNull String nodeId) {
        for (String fqn : snapshot.entities()) {
            if (EntityNames.nodeIdOf(fqn).equals(nodeId)) return fqn;
        }
        return null;
    }
}
