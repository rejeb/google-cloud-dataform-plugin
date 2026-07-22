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
import io.github.rejeb.dataform.language.compilation.model.CompiledOperation;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Declaration;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Builds a table-level {@link LineageGraph} from a {@link CompiledGraph}.
 *
 * <p>Registers nodes for declarations, tables (all types), operations, and assertions.
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
        registerOperations(builder, compiledGraph);
        registerAssertions(builder, compiledGraph);

        addTableEdges(builder, compiledGraph);
        addOperationEdges(builder, compiledGraph);
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
                    schemaOf(t),
                    "declaration",
                    List.of(),
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
                    schemaOf(t),
                    tableType(table),
                    tagsOf(table.getTags()),
                    table.getFileName()));
        }
    }

    private void registerOperations(@NotNull LineageGraph.Builder builder,
                                    @NotNull CompiledGraph graph) {
        for (CompiledOperation operation : graph.getOperations()) {
            if (!operation.isHasOutput()) continue;
            Target t = operation.getTarget();
            if (t == null || t.getFullName() == null) continue;
            builder.addNode(new LineageNode(
                    LineageNode.idOf(t.getFullName()),
                    t.getName(),
                    t.getFullName(),
                    schemaOf(t),
                    "operation",
                    tagsOf(operation.getTags()),
                    operation.getFileName()));
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
                    schemaOf(t),
                    "assertion",
                    tagsOf(a.getTags()),
                    a.getFileName()));
        }
    }

    private void addTableEdges(@NotNull LineageGraph.Builder builder,
                               @NotNull CompiledGraph graph) {
        for (CompiledTable table : graph.getTables()) {
            addEdges(builder, table.getTarget(), table.getDependencyTargets());
        }
    }

    private void addOperationEdges(@NotNull LineageGraph.Builder builder,
                                   @NotNull CompiledGraph graph) {
        for (CompiledOperation operation : graph.getOperations()) {
            if (!operation.isHasOutput()) continue;
            addEdges(builder, operation.getTarget(), operation.getDependencyTargets());
        }
    }

    private void addAssertionEdges(@NotNull LineageGraph.Builder builder,
                                   @NotNull CompiledGraph graph) {
        for (CompiledAssertion assertion : graph.getAssertions()) {
            addEdges(builder, assertion.getTarget(), assertion.getDependencyTargets());
        }
    }

    private void addEdges(@NotNull LineageGraph.Builder builder,
                          Target target,
                          @NotNull List<Target> dependencies) {
        if (target == null || target.getFullName() == null) return;
        String targetId = LineageNode.idOf(target.getFullName());
        for (Target dep : dependencies) {
            if (dep.getFullName() == null) continue;
            ensureNode(builder, dep);
            builder.addEdge(LineageNode.idOf(dep.getFullName()), targetId);
        }
    }

    private void ensureNode(@NotNull LineageGraph.Builder builder, @NotNull Target dep) {
        builder.addNode(new LineageNode(
                LineageNode.idOf(dep.getFullName()),
                dep.getName(),
                dep.getFullName(),
                schemaOf(dep),
                "external",
                List.of(),
                null));
    }

    private static @NotNull String tableType(@NotNull CompiledTable table) {
        String enumType = table.getEnumType();
        if (enumType != null && !enumType.isBlank()) return enumType;
        return table.isMaterialized() ? "materialized_view" : "table";
    }

    private static @NotNull String schemaOf(@NotNull Target target) {
        String schema = target.getSchema();
        return schema != null && !schema.isBlank() ? schema : "default";
    }

    private static @NotNull List<String> tagsOf(List<String> tags) {
        return tags != null ? List.copyOf(tags) : List.of();
    }
}
