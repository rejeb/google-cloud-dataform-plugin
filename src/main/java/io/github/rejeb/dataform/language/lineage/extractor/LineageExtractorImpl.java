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

import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledOperation;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Declaration;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Builds a table-level {@link LineageGraph} from a {@link CompiledGraph}.
 *
 * <p>Registers nodes for declarations, tables (all types), and operations with an output.
 * Assertions are deliberately excluded from the graph.
 * For each dependency that does not correspond to a known action, a placeholder
 * node of type {@code "external"} is created. Edges go from dependency to dependent
 * (A → B means "A feeds B").</p>
 */
public final class LineageExtractorImpl implements LineageExtractor {

    @Override
    public @NotNull LineageGraph extract(@NotNull CompiledGraph compiledGraph) {
        LineageGraph.Builder builder = LineageGraph.builder();

        for (Declaration d : compiledGraph.getDeclarations()) {
            addNode(builder, d.getTarget(), "declaration", List.of(), d.getFileName(), false);
        }
        for (CompiledTable table : compiledGraph.getTables()) {
            addNode(builder, table.getTarget(), tableType(table), tagsOf(table.getTags()), table.getFileName(),
                    table.isDisabled());
        }
        List<CompiledOperation> operations = compiledGraph.getOperations().stream()
                .filter(CompiledOperation::isHasOutput).toList();
        for (CompiledOperation operation : operations) {
            addNode(builder, operation.getTarget(), "operation", tagsOf(operation.getTags()), operation.getFileName(),
                    operation.isDisabled());
        }
        compiledGraph.getTables().forEach(table -> addEdges(builder, table.getTarget(), table.getDependencyTargets()));
        operations.forEach(operation -> addEdges(builder, operation.getTarget(), operation.getDependencyTargets()));
        return builder.build();
    }

    private static void addNode(@NotNull LineageGraph.Builder builder, @Nullable Target t, @NotNull String type,
                                @NotNull List<String> tags, @Nullable String fileName, boolean disabled) {
        if (t == null || t.getFullName() == null) return;
        builder.addNode(new LineageNode(LineageNode.idOf(t.getFullName()), t.getName(), t.getFullName(), schemaOf(t),
                type, tags, fileName, disabled));
    }

    private void addEdges(@NotNull LineageGraph.Builder builder,
                          Target target,
                          @NotNull List<Target> dependencies) {
        if (target == null || target.getFullName() == null) return;
        String targetId = LineageNode.idOf(target.getFullName());
        for (Target dep : dependencies) {
            if (dep.getFullName() == null) continue;
            addNode(builder, dep, "external", List.of(), null, false);
            builder.addEdge(LineageNode.idOf(dep.getFullName()), targetId);
        }
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
