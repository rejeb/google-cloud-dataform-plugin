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
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Turns a loaded part of the recorded lineage into the graph the lineage view draws. A table an
 * action of the repository produces keeps the node of that action; the others are marked as
 * coming from outside the repository.
 */
public final class DataplexGraphBuilder {

    /** The type of a BigQuery table that no action of the repository produces. */
    public static final String TYPE_OUTSIDE = "outside";
    /** The type of an entity that is not a BigQuery table, such as files in Cloud Storage. */
    public static final String TYPE_ASSET = "asset";

    private static final String TYPE_REFERENCED_ONLY = "external";

    private DataplexGraphBuilder() {
    }

    /**
     * @param snapshot         the loaded lineage
     * @param dataform         the lineage graph of the repository
     * @param assertionNodeIds the node ids of the views Dataform creates for assertions, left out
     *                         as they are in the repository graph
     * @return the graph of the loaded entities and of the links between them
     */
    public static @NotNull LineageGraph build(@NotNull DataplexSnapshot snapshot, @NotNull LineageGraph dataform,
                                              @NotNull Set<String> assertionNodeIds) {
        LineageGraph.Builder builder = LineageGraph.builder();
        for (String fqn : snapshot.entities()) {
            String nodeId = EntityNames.nodeIdOf(fqn);
            if (assertionNodeIds.contains(nodeId)) continue;
            LineageNode known = dataform.node(nodeId);
            builder.addNode(known != null && !TYPE_REFERENCED_ONLY.equals(known.dataformType()) ? known : outsideNode(fqn));
        }
        for (RecordedLink link : snapshot.links()) {
            String from = EntityNames.nodeIdOf(link.sourceFqn());
            String to = EntityNames.nodeIdOf(link.targetFqn());
            if (!from.equals(to)) builder.addEdge(from, to);
        }
        return builder.build();
    }

    private static @NotNull LineageNode outsideNode(@NotNull String fqn) {
        String fullName = EntityNames.fullNameOf(fqn);
        return new LineageNode(EntityNames.nodeIdOf(fqn), EntityNames.shortNameOf(fqn),
                fullName != null ? fullName : fqn, EntityNames.containerOf(fqn),
                fullName != null ? TYPE_OUTSIDE : TYPE_ASSET, List.of(), null);
    }
}
