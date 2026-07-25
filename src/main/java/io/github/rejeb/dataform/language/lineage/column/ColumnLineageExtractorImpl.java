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

import io.github.rejeb.dataform.language.compilation.model.CompiledAssertion;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledOperation;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Default {@link ColumnLineageExtractor}. Column identity is owned by the resolved table
 * schemas: every action's columns are seeded from its schema leaves before any edge is built,
 * so edge resolution can only connect existing columns and can never invent one. For each
 * action, the compiled SQL is parsed with a {@link BigQuerySelectAnalyzer} to map inputs onto
 * those columns. Actions whose schema is unknown are skipped and reported through
 * {@link ColumnLineageGraph#unresolvedTables()}. Cross-layer rename tracking is emergent
 * through transitive traversal of the resulting graph.
 */
public final class ColumnLineageExtractorImpl implements ColumnLineageExtractor {

    private final SelectAnalyzer analyzer;

    public ColumnLineageExtractorImpl(@NotNull SelectAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public @NotNull ColumnLineageGraph extract(@NotNull CompiledGraph graph,
                                               @NotNull Map<String, List<ColumnInfo>> schemas) {
        List<Analyzable> units = new ArrayList<>();
        for (CompiledTable table : graph.getTables()) {
            units.add(new Analyzable(table.getTarget(), table.getQuery(), table.getDependencyTargets()));
        }
        for (CompiledAssertion assertion : graph.getAssertions()) {
            units.add(new Analyzable(assertion.getTarget(), queryOf(assertion),
                    assertion.getDependencyTargets()));
        }

        List<TableAnalysis> analyses = units.parallelStream()
                .map(this::analyzeUnit)
                .filter(Objects::nonNull)
                .toList();

        ColumnLineageGraph.Builder builder = ColumnLineageGraph.builder();
        seedColumns(builder, graph, schemas);
        for (TableAnalysis analysis : analyses) {
            if (!hasSchema(schemas, analysis.tableFullName())) continue;
            processTable(builder, analysis, schemas);
        }
        return builder.build();
    }

    /**
     * Registers one column per schema leaf for every action of the compiled graph, making the
     * resolved schemas the single source of truth for column identity. Actions with no resolved
     * schema are recorded as unresolved so the caller can warn that the graph is incomplete.
     */
    private void seedColumns(@NotNull ColumnLineageGraph.Builder builder,
                             @NotNull CompiledGraph graph,
                             @NotNull Map<String, List<ColumnInfo>> schemas) {
        for (String fullName : collectActionFullNames(graph)) {
            List<ColumnInfo> columns = schemas.get(fullName);
            if (columns == null || columns.isEmpty()) {
                builder.addUnresolvedTable(fullName);
                continue;
            }
            for (String path : leafPaths(columns, "")) {
                builder.addColumn(new ColumnRef(fullName, path));
            }
        }
    }

    /**
     * Fully qualified names of everything that can carry columns: tables, assertions, operations
     * with an output, declared sources, and the dependency targets of all of them (upstream
     * tables that are not themselves actions of this project).
     */
    private @NotNull Set<String> collectActionFullNames(@NotNull CompiledGraph graph) {
        Set<String> names = new LinkedHashSet<>();
        graph.getTables().forEach(t -> {
            addFullName(names, t.getTarget());
            t.getDependencyTargets().forEach(d -> addFullName(names, d));
        });
        graph.getAssertions().forEach(a -> {
            addFullName(names, a.getTarget());
            a.getDependencyTargets().forEach(d -> addFullName(names, d));
        });
        graph.getOperations().stream()
                .filter(CompiledOperation::isHasOutput)
                .forEach(o -> {
                    addFullName(names, o.getTarget());
                    o.getDependencyTargets().forEach(d -> addFullName(names, d));
                });
        graph.getDeclarations().forEach(d -> addFullName(names, d.getTarget()));
        return names;
    }

    private void addFullName(@NotNull Set<String> names, @Nullable Target target) {
        if (target != null && target.getFullName() != null) names.add(target.getFullName());
    }

    /** Dotted paths of the leaf columns; STRUCT containers are not columns of their own. */
    private @NotNull List<String> leafPaths(@NotNull List<ColumnInfo> columns, @NotNull String prefix) {
        List<String> paths = new ArrayList<>();
        for (ColumnInfo column : columns) {
            String path = prefix.isEmpty() ? column.name() : prefix + "." + column.name();
            if (isRecord(column)) {
                paths.addAll(leafPaths(column.subFields(), path));
            } else {
                paths.add(path);
            }
        }
        return paths;
    }

    private boolean hasSchema(@NotNull Map<String, List<ColumnInfo>> schemas, @NotNull String fullName) {
        List<ColumnInfo> columns = schemas.get(fullName);
        return columns != null && !columns.isEmpty();
    }

    /**
     * Parses one action's SQL into an intermediate result. This is the expensive step (PSI
     * parsing per {@code analyze} call) and is safe to run in parallel because it only reads and
     * produces immutable data; the shared {@link ColumnLineageGraph.Builder} is populated later,
     * sequentially. Returns {@code null} for actions without a usable target or query.
     */
    private @Nullable TableAnalysis analyzeUnit(@NotNull Analyzable unit) {
        try {
            Target target = unit.target();
            if (target == null || target.getFullName() == null) return null;
            String sql = unit.sql();
            if (sql == null || sql.isBlank()) return null;
            SelectAnalyzer.QueryAnalysis analysis = analyzer.analyzeQuery(sql);
            return new TableAnalysis(target.getFullName(), analysis.outputs(),
                    analysis.aliases(), unit.deps());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void processTable(@NotNull ColumnLineageGraph.Builder builder,
                              @NotNull TableAnalysis analysis,
                              @NotNull Map<String, List<ColumnInfo>> schemas) {
        String tableFullName = analysis.tableFullName();
        Map<String, List<InputColumn>> outputs = analysis.outputs();
        Map<String, String> aliases = analysis.aliases();
        List<Target> deps = analysis.deps();

        List<ColumnInfo> targetSchema = schemas.get(tableFullName);
        outputs.forEach((outputName, inputs) -> {
            for (InputColumn input : inputs) {
                addEdges(builder, tableFullName, outputName, input, aliases, deps, schemas, targetSchema);
            }
        });
    }

    private void addEdges(@NotNull ColumnLineageGraph.Builder builder,
                          @NotNull String tableFullName,
                          @NotNull String outputName,
                          @NotNull InputColumn input,
                          @NotNull Map<String, String> aliases,
                          @NotNull List<Target> deps,
                          @NotNull Map<String, List<ColumnInfo>> schemas,
                          @Nullable List<ColumnInfo> targetSchema) {
        List<String> depTables = resolveDependencies(input.sourceAlias(), aliases, deps);
        for (String depFullName : depTables) {
            if (!hasSchema(schemas, depFullName)) continue;
            if (input.star()) {
                addStarInputEdges(builder, tableFullName, depFullName, input, aliases, schemas, targetSchema);
            } else {
                ColumnInfo sourceColumn = resolveColumnInfo(schemas.get(depFullName), input.columnName());
                ColumnInfo targetColumn = resolveColumnInfo(targetSchema, outputName);
                addColumnEdges(builder, depFullName, input.columnName(),
                        tableFullName, outputName, sourceColumn, targetColumn, input.kind());
            }
        }
    }

    /**
     * Handles a star input. A qualifier that is a table alias ({@code alias.*}) expands all of
     * the table's columns; a qualifier that is a STRUCT column ({@code struct.*}) expands only
     * that struct's nested fields as top-level output columns.
     */
    private void addStarInputEdges(@NotNull ColumnLineageGraph.Builder builder,
                                   @NotNull String tableFullName,
                                   @NotNull String depFullName,
                                   @NotNull InputColumn input,
                                   @NotNull Map<String, String> aliases,
                                   @NotNull Map<String, List<ColumnInfo>> schemas,
                                   @Nullable List<ColumnInfo> targetSchema) {
        String qualifier = input.sourceAlias();
        if (qualifier != null && !aliases.containsKey(qualifier)) {
            ColumnInfo structColumn = resolveColumnInfo(schemas.get(depFullName), qualifier);
            if (isRecord(structColumn)) {
                for (ColumnInfo sub : structColumn.subFields()) {
                    ColumnInfo targetColumn = resolveColumnInfo(targetSchema, sub.name());
                    addColumnEdges(builder, depFullName, qualifier + "." + sub.name(),
                            tableFullName, sub.name(), sub, targetColumn, Confidence.STAR);
                }
                return;
            }
            if (structColumn != null) {
                addEdge(builder, depFullName, qualifier, tableFullName, qualifier, Confidence.STAR);
                return;
            }
        }
        addStarEdges(builder, tableFullName, depFullName, schemas);
    }

    /**
     * Emits edges for a plain column input, aligning STRUCT/RECORD structure between source and
     * target so struct fields keep individual identities across the whole graph:
     * <ul>
     *   <li>source struct → mirror its nested fields onto the (possibly renamed) target;</li>
     *   <li>flat source into a struct target (a {@code STRUCT(...)} constructor field) → route the
     *       edge to the target's nested field whose name matches the source column;</li>
     *   <li>otherwise a single flat edge.</li>
     * </ul>
     */
    private void addColumnEdges(@NotNull ColumnLineageGraph.Builder builder,
                                @NotNull String depFullName,
                                @NotNull String sourceName,
                                @NotNull String tableFullName,
                                @NotNull String targetName,
                                @Nullable ColumnInfo sourceColumn,
                                @Nullable ColumnInfo targetColumn,
                                @NotNull Confidence kind) {
        if (isRecord(sourceColumn)) {
            for (ColumnInfo sub : sourceColumn.subFields()) {
                ColumnInfo targetSub = isRecord(targetColumn) ? findField(targetColumn, sub.name()) : null;
                addColumnEdges(builder, depFullName, sourceName + "." + sub.name(),
                        tableFullName, targetName + "." + sub.name(), sub, targetSub, kind);
            }
            return;
        }
        String resolvedTarget = targetName;
        if (isRecord(targetColumn)) {
            String relativePath = relativePathForLeaf(targetColumn, leafOf(sourceName));
            if (relativePath != null) resolvedTarget = targetName + "." + relativePath;
        }
        addEdge(builder, depFullName, sourceName, tableFullName, resolvedTarget, kind);
    }

    /**
     * Connects two seeded columns. Columns absent from the seeded schemas are unknown to the
     * builder, so such an edge is silently dropped instead of creating a column that does not
     * belong to the table.
     */
    private void addEdge(@NotNull ColumnLineageGraph.Builder builder,
                         @NotNull String sourceTable,
                         @NotNull String sourceName,
                         @NotNull String targetTable,
                         @NotNull String targetName,
                         @NotNull Confidence kind) {
        builder.addEdge(new ColumnRef(sourceTable, sourceName).id(),
                new ColumnRef(targetTable, targetName).id(), kind);
    }

    private boolean isRecord(@Nullable ColumnInfo column) {
        return column != null && column.isRecord() && !column.subFields().isEmpty();
    }

    private @Nullable ColumnInfo findField(@NotNull ColumnInfo struct, @NotNull String name) {
        for (ColumnInfo sub : struct.subFields()) {
            if (sub.name().equalsIgnoreCase(name)) return sub;
        }
        return null;
    }

    private @Nullable String relativePathForLeaf(@NotNull ColumnInfo struct, @NotNull String leaf) {
        for (ColumnInfo sub : struct.subFields()) {
            if (isRecord(sub)) {
                String nested = relativePathForLeaf(sub, leaf);
                if (nested != null) return sub.name() + "." + nested;
            } else if (sub.name().equalsIgnoreCase(leaf)) {
                return sub.name();
            }
        }
        return null;
    }

    private @NotNull String leafOf(@NotNull String dottedName) {
        int dot = dottedName.lastIndexOf('.');
        return dot < 0 ? dottedName : dottedName.substring(dot + 1);
    }

    private @Nullable ColumnInfo resolveColumnInfo(@Nullable List<ColumnInfo> columns,
                                                   @NotNull String dottedName) {
        if (columns == null) return null;
        List<ColumnInfo> current = columns;
        ColumnInfo found = null;
        for (String part : dottedName.split("\\.")) {
            found = null;
            for (ColumnInfo candidate : current) {
                if (candidate.name().equalsIgnoreCase(part)) {
                    found = candidate;
                    break;
                }
            }
            if (found == null) return null;
            current = found.subFields();
        }
        return found;
    }

    /**
     * Emits a STAR edge per leaf column of the dependency, using the dotted leaf paths shared by
     * both schemas. Leaves the target does not actually expose are dropped by the builder.
     */
    private void addStarEdges(@NotNull ColumnLineageGraph.Builder builder,
                              @NotNull String tableFullName,
                              @NotNull String depFullName,
                              @NotNull Map<String, List<ColumnInfo>> schemas) {
        List<ColumnInfo> columns = schemas.get(depFullName);
        if (columns == null) return;
        for (String path : leafPaths(columns, "")) {
            addEdge(builder, depFullName, path, tableFullName, path, Confidence.STAR);
        }
    }

    private @NotNull List<String> resolveDependencies(@Nullable String sourceAlias,
                                                      @NotNull Map<String, String> aliases,
                                                      @NotNull List<Target> deps) {
        if (deps.isEmpty()) return List.of();
        if (sourceAlias == null) {
            return deps.size() == 1 ? List.of(fullName(deps.get(0))) : allFullNames(deps);
        }
        String tableToken = aliases.getOrDefault(sourceAlias, sourceAlias);
        for (Target dep : deps) {
            if (matches(dep, tableToken) || matches(dep, sourceAlias)) {
                return List.of(fullName(dep));
            }
        }
        return deps.size() == 1 ? List.of(fullName(deps.get(0))) : allFullNames(deps);
    }

    private @NotNull List<String> allFullNames(@NotNull List<Target> deps) {
        return deps.stream().map(this::fullName).filter(java.util.Objects::nonNull).toList();
    }

    private boolean matches(@NotNull Target dep, @NotNull String token) {
        String normalized = unquote(token);
        String full = dep.getFullName();
        if (full != null && (full.equals(normalized) || full.endsWith("." + normalized))) return true;
        String name = dep.getName();
        return name != null && name.equals(normalized);
    }

    /**
     * Strips BigQuery quoting from a table token as written in the SQL, so that a compiled
     * reference such as {@code `project.dataset.table`} can be compared to a dependency
     * fully qualified name.
     */
    private @NotNull String unquote(@NotNull String token) {
        return token.replace("`", "").replace("\"", "").trim();
    }

    private @Nullable String fullName(@NotNull Target target) {
        return target.getFullName();
    }

    private @Nullable String queryOf(@NotNull CompiledAssertion assertion) {
        try {
            return assertion.getQuery();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A compiled action (table or assertion) whose SQL can be analyzed for column lineage. */
    private record Analyzable(@Nullable Target target,
                              @Nullable String sql,
                              @NotNull List<Target> deps) {
    }

    /** Immutable per-table analysis result produced in the parallel phase. */
    private record TableAnalysis(@NotNull String tableFullName,
                                 @NotNull Map<String, List<InputColumn>> outputs,
                                 @NotNull Map<String, String> aliases,
                                 @NotNull List<Target> deps) {
    }
}
