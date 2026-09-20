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
package io.github.rejeb.dataform.language.lineage.model;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageGraph;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Central observable state for the lineage view: graph data, filter state, selection,
 * and view preferences. Views subscribe via {@link #addListener} and re-render on any
 * mutation. All derived queries ({@link #visibleIds()}, {@link #ancestors},
 * {@link #descendants}, {@link #highlightLineage()}) are pure functions over the current state.
 *
 * <p>Mutators fire listeners synchronously on the calling thread; callers are responsible
 * for invoking mutators on the EDT when a UI refresh must follow.</p>
 */
public final class LineageModel {

    private static final String KEY_DIRECTION = "dataform.lineage.direction";
    private static final String KEY_DENSITY = "dataform.lineage.density";
    private static final String KEY_MINIMAP = "dataform.lineage.minimap";

    private final @Nullable Project project;
    private final List<Consumer<LineageModel>> listeners = new CopyOnWriteArrayList<>();

    private @NotNull LineageGraph graph = LineageGraph.builder().build();

    private final Set<String> enabledTypes = new LinkedHashSet<>();
    private final Set<String> enabledTags = new LinkedHashSet<>();
    private final Set<String> enabledSchemas = new LinkedHashSet<>();
    private @NotNull String searchQuery = "";

    private @Nullable Set<String> scopeIds;

    private @Nullable String selectedId;
    private @Nullable String focusId;
    private @Nullable String hoverId;

    private @NotNull Direction direction = Direction.LR;
    private @NotNull Density density = Density.COMFORTABLE;
    private boolean minimapVisible = true;

    public LineageModel(@Nullable Project project) {
        this.project = project;
        restoreViewState();
    }

    // ---------------------------------------------------------------------
    // Listeners
    // ---------------------------------------------------------------------

    public void addListener(@NotNull Consumer<LineageModel> listener) {
        listeners.add(listener);
    }

    private void fire() {
        for (Consumer<LineageModel> listener : listeners) {
            listener.accept(this);
        }
    }

    // ---------------------------------------------------------------------
    // Graph
    // ---------------------------------------------------------------------

    public @NotNull LineageGraph graph() {
        return graph;
    }

    /**
     * Replaces the graph. Enables all present types, drops filter entries that no longer
     * exist, and clears any selection/focus/hover that points at a missing node.
     */
    public void setGraph(@Nullable LineageGraph newGraph) {
        this.graph = newGraph != null ? newGraph : LineageGraph.builder().build();

        Set<String> presentTypes = new LinkedHashSet<>();
        Set<String> presentTags = new LinkedHashSet<>();
        Set<String> presentSchemas = new LinkedHashSet<>();
        for (LineageNode node : graph.nodes()) {
            presentTypes.add(node.dataformType());
            presentTags.addAll(node.tags());
            presentSchemas.add(node.schema());
        }

        enabledTypes.clear();
        enabledTypes.addAll(presentTypes);
        enabledTags.retainAll(presentTags);
        enabledSchemas.clear();
        enabledSchemas.addAll(presentSchemas);

        if (selectedId != null && graph.node(selectedId) == null) selectedId = null;
        if (focusId != null && graph.node(focusId) == null) focusId = null;
        if (hoverId != null && graph.node(hoverId) == null) hoverId = null;

        fire();
    }

    // ---------------------------------------------------------------------
    // Filters
    // ---------------------------------------------------------------------

    public @NotNull Set<String> enabledTypes() {
        return Set.copyOf(enabledTypes);
    }

    public @NotNull Set<String> enabledTags() {
        return Set.copyOf(enabledTags);
    }

    public @NotNull Set<String> enabledSchemas() {
        return Set.copyOf(enabledSchemas);
    }

    public @NotNull String searchQuery() {
        return searchQuery;
    }

    public void toggleType(@NotNull String type) {
        if (!enabledTypes.remove(type)) enabledTypes.add(type);
        fire();
    }

    public void toggleTag(@NotNull String tag) {
        if (!enabledTags.remove(tag)) enabledTags.add(tag);
        fire();
    }

    /**
     * Toggles whether a schema is shown. {@code enabledSchemas} holds exactly the schemas
     * that are visible; it defaults to all present schemas, so unchecking one hides its
     * nodes and unchecking every schema shows nothing.
     */
    public void toggleSchema(@NotNull String schema) {
        if (!enabledSchemas.remove(schema)) enabledSchemas.add(schema);
        fire();
    }

    public void setSearchQuery(@NotNull String query) {
        this.searchQuery = query;
        fire();
    }

    public @Nullable Set<String> scopeIds() {
        return scopeIds == null ? null : Set.copyOf(scopeIds);
    }

    /**
     * Restricts the view to a fixed set of node ids, on top of the regular filters.
     * Pass {@code null} to show the whole graph again.
     */
    public void setScopeIds(@Nullable Set<String> ids) {
        this.scopeIds = ids == null ? null : new LinkedHashSet<>(ids);
        fire();
    }

    // ---------------------------------------------------------------------
    // Selection / focus / hover
    // ---------------------------------------------------------------------

    public @Nullable String selectedId() {
        return selectedId;
    }

    public @Nullable String focusId() {
        return focusId;
    }

    public @Nullable String hoverId() {
        return hoverId;
    }

    public void select(@Nullable String id) {
        this.selectedId = id;
        fire();
    }

    public void focusOn(@Nullable String id) {
        this.focusId = id;
        this.selectedId = id;
        fire();
    }

    public void exitFocus() {
        this.focusId = null;
        fire();
    }

    public void setHover(@Nullable String id) {
        if (java.util.Objects.equals(this.hoverId, id)) return;
        this.hoverId = id;
        fire();
    }

    // ---------------------------------------------------------------------
    // Column lineage
    // ---------------------------------------------------------------------

    private @Nullable ColumnLineageGraph columnGraph;
    private final Set<String> selectedColumnIds = new LinkedHashSet<>();

    public @Nullable ColumnLineageGraph columnGraph() {
        return columnGraph;
    }

    /**
     * Sets the column lineage graph. Drops any selected column that no longer exists.
     */
    public void setColumnGraph(@Nullable ColumnLineageGraph graph) {
        this.columnGraph = graph;
        selectedColumnIds.removeIf(id -> graph == null || graph.column(id) == null);
        fire();
    }

    /** The primary selected column (first of the selection), or {@code null} when none. */
    public @Nullable String selectedColumnId() {
        return selectedColumnIds.isEmpty() ? null : selectedColumnIds.iterator().next();
    }

    public @NotNull Set<String> selectedColumnIds() {
        return Set.copyOf(selectedColumnIds);
    }

    /** Replaces the selection with a single column (or clears it when {@code null}). */
    public void selectColumn(@Nullable String columnId) {
        selectedColumnIds.clear();
        if (columnId != null) selectedColumnIds.add(columnId);
        fire();
    }

    /**
     * Toggles a column in the multi-selection. Selection is confined to one node: toggling a
     * column on a different node than the current selection replaces the selection.
     */
    public void toggleColumn(@NotNull String columnId) {
        if (columnGraph != null && !selectedColumnIds.isEmpty()) {
            ColumnRef current = columnGraph.column(selectedColumnIds.iterator().next());
            ColumnRef added = columnGraph.column(columnId);
            if (current != null && added != null && !current.tableNodeId().equals(added.tableNodeId())) {
                selectedColumnIds.clear();
            }
        }
        if (!selectedColumnIds.remove(columnId)) selectedColumnIds.add(columnId);
        fire();
    }

    public void clearColumnSelection() {
        selectedColumnIds.clear();
        fire();
    }

    /**
     * Ids to highlight: every selected column plus its full upstream and downstream column
     * lineage. Empty when no column is selected or no graph is set.
     */
    public @NotNull Set<String> highlightColumnLineage() {
        if (columnGraph == null || selectedColumnIds.isEmpty()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String id : selectedColumnIds) {
            if (columnGraph.column(id) == null) continue;
            result.add(id);
            result.addAll(columnGraph.upstream(id));
            result.addAll(columnGraph.downstream(id));
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // View state
    // ---------------------------------------------------------------------

    public @NotNull Direction direction() {
        return direction;
    }

    public @NotNull Density density() {
        return density;
    }

    public boolean minimapVisible() {
        return minimapVisible;
    }

    public void toggleDirection() {
        direction = direction == Direction.LR ? Direction.TB : Direction.LR;
        persist(KEY_DIRECTION, direction.name());
        fire();
    }

    public void toggleDensity() {
        density = density == Density.COMFORTABLE ? Density.COMPACT : Density.COMFORTABLE;
        persist(KEY_DENSITY, density.name());
        fire();
    }

    public void toggleMinimap() {
        minimapVisible = !minimapVisible;
        persist(KEY_MINIMAP, Boolean.toString(minimapVisible));
        fire();
    }

    // ---------------------------------------------------------------------
    // Derived queries (pure)
    // ---------------------------------------------------------------------

    public @NotNull Map<String, Integer> typeCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (LineageNode node : graph.nodes()) {
            counts.merge(node.dataformType(), 1, Integer::sum);
        }
        return counts;
    }

    public @NotNull Map<String, Integer> tagCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (LineageNode node : graph.nodes()) {
            for (String tag : node.tags()) counts.merge(tag, 1, Integer::sum);
        }
        return counts;
    }

    public @NotNull List<String> schemas() {
        Set<String> sorted = new TreeSet<>();
        for (LineageNode node : graph.nodes()) sorted.add(node.schema());
        return List.copyOf(sorted);
    }

    /** Ids of all nodes passing the active filters (types AND tags AND schemas AND search, then scope, then focus). */
    public @NotNull Set<String> visibleIds() {
        String needle = searchQuery.trim().toLowerCase(Locale.ROOT);
        Set<String> visible = new LinkedHashSet<>();
        for (LineageNode node : graph.nodes()) {
            if (matches(node, needle)) visible.add(node.id());
        }
        if (scopeIds != null) visible.retainAll(scopeIds);
        if (focusId != null && graph.node(focusId) != null) {
            Set<String> scope = new LinkedHashSet<>();
            scope.add(focusId);
            scope.addAll(ancestors(focusId));
            scope.addAll(descendants(focusId));
            visible.retainAll(scope);
        }
        Set<String> columnScope = columnTableScope();
        if (columnScope != null) {
            visible.clear();
            for (String id : columnScope) {
                if (graph.node(id) != null) visible.add(id);
            }
        }
        return visible;
    }

    /**
     * When a column is selected, the table node ids that participate in its column lineage. They
     * become the visible set on their own, so tables excluded by the active filters still show up
     * when they carry the selected lineage; {@code null} when no column is selected.
     */
    private @Nullable Set<String> columnTableScope() {
        if (selectedColumnIds.isEmpty() || columnGraph == null) return null;
        Set<String> tables = new LinkedHashSet<>();
        for (String columnId : highlightColumnLineage()) {
            ColumnRef ref = columnGraph.column(columnId);
            if (ref != null) tables.add(ref.tableNodeId());
        }
        return tables.isEmpty() ? null : tables;
    }

    private boolean matches(@NotNull LineageNode node, @NotNull String needle) {
        if (!enabledTypes.contains(node.dataformType())) return false;
        if (!enabledTags.isEmpty() && node.tags().stream().noneMatch(enabledTags::contains)) return false;
        if (!enabledSchemas.contains(node.schema())) return false;
        if (!needle.isEmpty()) {
            String haystack = (node.name() + " " + node.schema() + " " + String.join(",", node.tags()))
                    .toLowerCase(Locale.ROOT);
            return haystack.contains(needle);
        }
        return true;
    }

    public @NotNull Set<String> ancestors(@NotNull String id) {
        return graph.ancestors(id);
    }

    public @NotNull Set<String> descendants(@NotNull String id) {
        return graph.descendants(id);
    }

    /**
     * Ids to highlight: the hovered node if any, otherwise the selected node, together with
     * its full upstream and downstream lineage. Empty when nothing is hovered or selected.
     */
    public @NotNull Set<String> highlightLineage() {
        String target = hoverId != null ? hoverId : selectedId;
        if (target == null || graph.node(target) == null) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        result.add(target);
        result.addAll(ancestors(target));
        result.addAll(descendants(target));
        return result;
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    private void restoreViewState() {
        if (project == null) return;
        PropertiesComponent props = PropertiesComponent.getInstance(project);
        String dir = props.getValue(KEY_DIRECTION);
        if (dir != null) direction = safeValueOf(Direction.class, dir, Direction.LR);
        String den = props.getValue(KEY_DENSITY);
        if (den != null) density = safeValueOf(Density.class, den, Density.COMFORTABLE);
        minimapVisible = props.getBoolean(KEY_MINIMAP, true);
    }

    private void persist(@NotNull String key, @NotNull String value) {
        if (project == null) return;
        PropertiesComponent.getInstance(project).setValue(key, value);
    }

    private static <E extends Enum<E>> E safeValueOf(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
