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
package io.github.rejeb.dataform.language.lineage.extractor;

import io.github.rejeb.dataform.language.compilation.model.CompiledAssertion;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Declaration;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.jetbrains.annotations.NotNull;

/**
 * Builds a table-level {@link LineageGraph} from a {@link CompiledGraph}.
 *
 * <p>Registers nodes for declarations, tables (all types), and assertions.
 * For each dependency that does not correspond to a known action, a placeholder
 * node of type {@code "external"} is created. Edges go from dependency to dependent
 * (A → B means "A feeds B").</p>
 */
public final class LineageExtractorImpl implements LineageExtractor {

    @Override
    public @NotNull LineageGraph extract(@NotNull CompiledGraph compiledGraph) {
        LineageGraph.Builder builder = LineageGraph.builder();

        registerDeclarations(builder, compiledGraph);
        registerTables(builder, compiledGraph);
        registerAssertions(builder, compiledGraph);

        addTableEdges(builder, compiledGraph);
        addAssertionEdges(builder, compiledGraph);

        return builder.build();
    }

    private void registerDeclarations(@NotNull LineageGraph.Builder builder,
                                      @NotNull CompiledGraph graph) {
        for (Declaration d : graph.getDeclarations()) {
            Target t = d.getTarget();
            if (t == null || t.getFullName() == null) continue;
            builder.addNode(new LineageNode(
                    LineageNode.idOf(t.getFullName()),
                    t.getName(),
                    t.getFullName(),
                    "declaration",
                    d.getFileName()));
        }
    }

    private void registerTables(@NotNull LineageGraph.Builder builder,
                                @NotNull CompiledGraph graph) {
        for (CompiledTable table : graph.getTables()) {
            Target t = table.getTarget();
            if (t == null || t.getFullName() == null) continue;
            builder.addNode(new LineageNode(
                    LineageNode.idOf(t.getFullName()),
                    t.getName(),
                    t.getFullName(),
                    table.getType(),
                    table.getFileName()));
        }
    }

    private void registerAssertions(@NotNull LineageGraph.Builder builder,
                                    @NotNull CompiledGraph graph) {
        for (CompiledAssertion a : graph.getAssertions()) {
            Target t = a.getTarget();
            if (t == null || t.getFullName() == null) continue;
            builder.addNode(new LineageNode(
                    LineageNode.idOf(t.getFullName()),
                    t.getName(),
                    t.getFullName(),
                    "assertion",
                    a.getFileName()));
        }
    }

    private void addTableEdges(@NotNull LineageGraph.Builder builder,
                               @NotNull CompiledGraph graph) {
        for (CompiledTable table : graph.getTables()) {
            Target t = table.getTarget();
            if (t == null || t.getFullName() == null) continue;
            String targetId = LineageNode.idOf(t.getFullName());
            for (Target dep : table.getDependencyTargets()) {
                if (dep.getFullName() == null) continue;
                ensureNode(builder, dep);
                builder.addEdge(LineageNode.idOf(dep.getFullName()), targetId);
            }
        }
    }

    private void addAssertionEdges(@NotNull LineageGraph.Builder builder,
                                   @NotNull CompiledGraph graph) {
        for (CompiledAssertion assertion : graph.getAssertions()) {
            Target t = assertion.getTarget();
            if (t == null || t.getFullName() == null) continue;
            String targetId = LineageNode.idOf(t.getFullName());
            for (Target dep : assertion.getDependencyTargets()) {
                if (dep.getFullName() == null) continue;
                ensureNode(builder, dep);
                builder.addEdge(LineageNode.idOf(dep.getFullName()), targetId);
            }
        }
    }

    private void ensureNode(@NotNull LineageGraph.Builder builder, @NotNull Target dep) {
        builder.addNode(new LineageNode(
                LineageNode.idOf(dep.getFullName()),
                dep.getName(),
                dep.getFullName(),
                "external",
                null));
    }
}
