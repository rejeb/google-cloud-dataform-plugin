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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
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

    private static final Logger LOG = Logger.getInstance(ColumnLineageExtractorImpl.class);

    private final SelectAnalyzer analyzer;

    public ColumnLineageExtractorImpl(@NotNull SelectAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public @NotNull ColumnLineageGraph extract(@NotNull CompiledGraph graph,
                                               @NotNull Map<String, List<ColumnInfo>> schemas) {
        List<Analyzable> units = collectAnalyzables(graph, schemas);

        List<TableAnalysis> analyses = units.parallelStream()
                .map(this::analyzeUnit)
                .filter(Objects::nonNull)
                .toList();

        ColumnLineageGraph.Builder builder = ColumnLineageGraph.builder();
        seedColumns(builder, graph, schemas, unreportableFullNames(graph));
        for (TableAnalysis analysis : analyses) {
            if (!hasSchema(schemas, analysis.tableFullName())) continue;
            processTable(builder, analysis, schemas);
        }
        return builder.build();
    }

    /**
     * Every query that can produce column lineage. A table contributes its main query and, when it
     * is incremental, its incremental query too: a column built differently on the incremental
     * branch reads inputs the main query never names. An operation contributes every statement it
     * runs, because its output is written by them — a {@code MERGE}, an {@code INSERT}, an
     * {@code UPDATE} — rather than selected by one; the schema's column order goes with it for a
     * statement writing by position. Several units may share a target; their edges accumulate.
     */
    private @NotNull List<Analyzable> collectAnalyzables(@NotNull CompiledGraph graph,
                                                         @NotNull Map<String, List<ColumnInfo>> schemas) {
        List<Analyzable> units = new ArrayList<>();
        for (CompiledTable table : graph.getTables()) {
            if (table.isDisabled()) continue;
            units.add(new Analyzable(table.getTarget(), table.getQuery(), table.getDependencyTargets(), null));
            String incremental = table.getIncrementalQuery();
            if (incremental != null && !incremental.isBlank()) {
                units.add(new Analyzable(table.getTarget(), incremental, table.getDependencyTargets(), null));
            }
        }
        for (CompiledOperation operation : graph.getOperations()) {
            if (operation.isDisabled() || !operation.isHasOutput()) continue;
            List<String> queries = operation.getQueries();
            Target target = operation.getTarget();
            if (queries.isEmpty() || target == null || target.getName() == null) continue;
            List<ColumnInfo> schema = schemas.get(target.getFullName());
            List<String> columns = schema == null ? List.of() : schema.stream().map(ColumnInfo::name).toList();
            units.add(new Analyzable(target, String.join(";\n", queries),
                    operation.getDependencyTargets(), new Writes(target.getName(), columns)));
        }
        return units;
    }

    /**
     * Actions whose missing schema must not be reported as a column lineage gap: the ones declaring
     * {@code disabled: true} and everything downstream of them. A disabled action is never executed,
     * so neither it nor anything reading it has a schema to resolve, and warning about that would
     * describe a deliberate choice as an error.
     */
    private @NotNull Set<String> unreportableFullNames(@NotNull CompiledGraph graph) {
        Set<String> excluded = new LinkedHashSet<>();
        graph.getTables().stream().filter(CompiledTable::isDisabled)
                .forEach(t -> addFullName(excluded, t.getTarget()));
        graph.getOperations().stream().filter(CompiledOperation::isDisabled)
                .forEach(o -> addFullName(excluded, o.getTarget()));
        if (excluded.isEmpty()) return excluded;

        boolean grown = true;
        while (grown) {
            grown = false;
            for (Dependent dependent : dependents(graph)) {
                if (excluded.contains(dependent.fullName())) continue;
                if (dependent.dependencies().stream().anyMatch(excluded::contains)) {
                    excluded.add(dependent.fullName());
                    grown = true;
                }
            }
        }
        return excluded;
    }

    private @NotNull List<Dependent> dependents(@NotNull CompiledGraph graph) {
        List<Dependent> dependents = new ArrayList<>();
        graph.getTables().forEach(t -> addDependent(dependents, t.getTarget(), t.getDependencyTargets()));
        graph.getOperations().forEach(o -> addDependent(dependents, o.getTarget(), o.getDependencyTargets()));
        return dependents;
    }

    private void addDependent(@NotNull List<Dependent> dependents,
                              @Nullable Target target,
                              @NotNull List<Target> dependencies) {
        if (target == null || target.getFullName() == null) return;
        Set<String> names = new LinkedHashSet<>();
        dependencies.forEach(d -> addFullName(names, d));
        dependents.add(new Dependent(target.getFullName(), names));
    }

    /**
     * Registers one column per schema leaf for every action of the compiled graph, making the
     * resolved schemas the single source of truth for column identity. Actions with no resolved
     * schema are recorded as unresolved so the caller can warn that the graph is incomplete.
     */
    private void seedColumns(@NotNull ColumnLineageGraph.Builder builder,
                             @NotNull CompiledGraph graph,
                             @NotNull Map<String, List<ColumnInfo>> schemas,
                             @NotNull Set<String> unreportable) {
        for (String fullName : collectActionFullNames(graph)) {
            List<ColumnInfo> columns = schemas.get(fullName);
            if (columns == null || columns.isEmpty()) {
                if (!unreportable.contains(fullName)) builder.addUnresolvedTable(fullName);
                continue;
            }
            for (String path : leafPaths(columns, "")) {
                builder.addColumn(new ColumnRef(fullName, path));
            }
        }
    }

    /**
     * Fully qualified names of everything that can carry columns: tables, operations with an
     * output, declared sources, and the dependency targets of all of them (upstream tables that
     * are not themselves actions of this project). Assertions are deliberately excluded from
     * column lineage.
     */
    private @NotNull Set<String> collectActionFullNames(@NotNull CompiledGraph graph) {
        Set<String> names = new LinkedHashSet<>();
        graph.getTables().forEach(t -> {
            addFullName(names, t.getTarget());
            t.getDependencyTargets().forEach(d -> addFullName(names, d));
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
            SelectAnalyzer.QueryAnalysis analysis = unit.writes() == null
                    ? analyzer.analyzeQuery(sql)
                    : analyzer.analyzeWrites(sql, unit.writes().tableName(), unit.writes().columns());
            return new TableAnalysis(target.getFullName(), analysis.outputs(),
                    analysis.aliases(), unit.deps());
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warn("Column lineage analysis failed for "
                    + (unit.target() != null ? unit.target().getFullName() : "an unnamed action"), e);
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
        List<String> depTables = resolveDependencies(input, aliases, deps, schemas);
        Confidence kind = depTables.size() > 1 && !input.star() ? Confidence.AMBIGUOUS : input.kind();
        for (String depFullName : depTables) {
            if (!hasSchema(schemas, depFullName)) continue;
            if (input.star()) {
                addStarInputEdges(builder, tableFullName, depFullName, input, aliases, schemas, targetSchema);
            } else {
                ColumnInfo sourceColumn = resolveColumnInfo(schemas.get(depFullName), input.columnName());
                ColumnInfo targetColumn = resolveColumnInfo(targetSchema, outputName);
                addColumnEdges(builder, depFullName, input.columnName(),
                        tableFullName, outputName, sourceColumn, targetColumn, kind);
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

    /**
     * Dependencies that can supply an input column. A qualified input resolves through its alias
     * to a single dependency. An unqualified one is narrowed to the dependencies the query
     * actually reads, then to those whose schema declares the column: an unqualified name is
     * unambiguous in valid SQL, so a single owner among the read tables identifies the source.
     * A name owned by several read tables is genuinely ambiguous and keeps every candidate.
     */
    private @NotNull List<String> resolveDependencies(@NotNull InputColumn input,
                                                      @NotNull Map<String, String> aliases,
                                                      @NotNull List<Target> deps,
                                                      @NotNull Map<String, List<ColumnInfo>> schemas) {
        if (deps.isEmpty()) return List.of();
        String sourceAlias = input.sourceAlias();
        if (sourceAlias != null) {
            String tableToken = aliases.getOrDefault(sourceAlias, sourceAlias);
            for (Target dep : deps) {
                if (matches(dep, tableToken) || matches(dep, sourceAlias)) {
                    return List.of(fullName(dep));
                }
            }
        }
        List<String> candidates = readDependencies(aliases, deps);
        if (candidates.isEmpty()) candidates = allFullNames(deps);
        if (candidates.size() == 1 || input.star()) return candidates;
        List<String> owners = candidates.stream()
                .filter(fullName -> resolveColumnInfo(schemas.get(fullName), input.columnName()) != null)
                .toList();
        return owners.isEmpty() ? candidates : owners;
    }

    /**
     * Dependencies named in the FROM/JOIN clauses of the query, including those reached through
     * CTEs and derived tables. Dependencies declared elsewhere, such as through the action
     * configuration, expose no column to this query and are excluded.
     */
    private @NotNull List<String> readDependencies(@NotNull Map<String, String> aliases,
                                                   @NotNull List<Target> deps) {
        List<String> read = new ArrayList<>();
        for (Target dep : deps) {
            for (String token : aliases.values()) {
                if (matches(dep, token)) {
                    read.add(fullName(dep));
                    break;
                }
            }
        }
        return read;
    }

    private @NotNull List<String> allFullNames(@NotNull List<Target> deps) {
        return deps.stream().map(this::fullName).filter(java.util.Objects::nonNull).toList();
    }

    private boolean matches(@NotNull Target dep, @NotNull String token) {
        String normalized = unquote(token);
        String full = dep.getFullName();
        if (full != null && (full.equalsIgnoreCase(normalized) || endsWithSegment(full, normalized))) {
            return true;
        }
        String name = dep.getName();
        return name != null && name.equalsIgnoreCase(normalized);
    }

    /**
     * Whether a fully qualified name ends with the given dot-separated suffix, comparing without
     * case. BigQuery project and dataset identifiers are matched case-insensitively, so a token
     * written with a different case than the compiled target must still resolve to it.
     */
    private boolean endsWithSegment(@NotNull String fullName, @NotNull String suffix) {
        int offset = fullName.length() - suffix.length() - 1;
        return offset >= 0 && fullName.regionMatches(true, offset, "." + suffix, 0, suffix.length() + 1);
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

    /** A compiled table whose SQL can be analyzed for column lineage. */
    private record Analyzable(@Nullable Target target,
                              @Nullable String sql,
                              @NotNull List<Target> deps,
                              @Nullable Writes writes) {
    }

    /** The table an operation's statements write, with its columns in schema order when known. */
    private record Writes(@NotNull String tableName, @NotNull List<String> columns) {
    }

    /** Immutable per-table analysis result produced in the parallel phase. */
    /** An action and the fully qualified names it reads, used to propagate disabled state. */
    private record Dependent(@NotNull String fullName, @NotNull Set<String> dependencies) {
    }

    private record TableAnalysis(@NotNull String tableFullName,
                                 @NotNull Map<String, List<InputColumn>> outputs,
                                 @NotNull Map<String, String> aliases,
                                 @NotNull List<Target> deps) {
    }
}
