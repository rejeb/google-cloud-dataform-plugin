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
import io.github.rejeb.dataform.language.columns.analysis.ColumnEdge;
import io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexGraphBuilder;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.dataplex.Difference;
import io.github.rejeb.dataform.language.lineage.dataplex.OutsideTableColumns;
import io.github.rejeb.dataform.language.lineage.dataplex.TimeRange;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
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
 * {@link #descendants}, {@link #searchMatches()}, {@link #dimmedIds()}, {@link #highlightedIds()}) are pure functions over the
 * current state; the highlight sets are cached until the next mutation.
 *
 * <p>Mutators fire listeners synchronously on the calling thread; callers are responsible
 * for invoking mutators on the EDT when a UI refresh must follow.</p>
 */
public final class LineageModel {

    private static final String KEY_DIRECTION = "dataform.lineage.direction";
    private static final String KEY_SOURCE = "dataform.lineage.source";
    private static final String KEY_DENSITY = "dataform.lineage.density";
    private static final String KEY_MINIMAP = "dataform.lineage.minimap";

    /** A column search draws lineage for at most this many found columns. */
    public static final int MAX_LINEAGE_ROOTS = 10;

    private final @Nullable Project project;
    private final List<Consumer<LineageModel>> listeners = new CopyOnWriteArrayList<>();
    private @Nullable Consumer<String> nodeRequestHandler;

    private @NotNull LineageGraph graph = LineageGraph.builder().build();

    private final Set<String> enabledTypes = new LinkedHashSet<>();
    private final Set<String> enabledTags = new LinkedHashSet<>();
    private final Set<String> enabledSchemas = new LinkedHashSet<>();
    private @NotNull String searchQuery = "";
    private @NotNull SearchScope searchScope = SearchScope.TABLES;
    private @Nullable List<String> foundColumns;
    private @Nullable Set<String> columnLineage;
    private @Nullable Set<String> markedColumns;
    private @Nullable Set<String> rootColumns;
    private @Nullable Set<String> columnLineageEdges;
    private @Nullable Map<String, Integer> colourIndex;
    private @NotNull Map<String, String> columnTypes = Map.of();

    private @Nullable Set<String> scopeIds;

    private @Nullable String selectedId;
    private @Nullable String focusId;
    private @Nullable String hoverId;
    private @Nullable Highlight highlight;

    private @NotNull Direction direction = Direction.LR;
    private @NotNull Density density = Density.COMFORTABLE;
    private boolean minimapVisible = true;
    private @NotNull LineageSource source = LineageSource.DATAFORM;
    private @Nullable DataplexView dataplexView;
    private @NotNull TimeRange timeRange = TimeRange.LAST_30_DAYS;
    private boolean differencesShown;
    private @NotNull List<Difference> differences = List.of();
    private @Nullable LinkRef selectedLink;
    private @NotNull RecordedActivity recordedActivity = RecordedActivity.NONE;
    private @NotNull Set<String> markedIds = Set.of();

    public LineageModel(@Nullable Project project) {
        this.project = project;
        restoreViewState();
    }

    public void addListener(@NotNull Consumer<LineageModel> listener) {
        listeners.add(listener);
    }

    /**
     * Stops telling a listener about the changes of the model.
     */
    public void removeListener(@NotNull Consumer<LineageModel> listener) {
        listeners.remove(listener);
    }

    private void fire() {
        highlight = null;
        foundColumns = null;
        columnLineage = null;
        markedColumns = null;
        rootColumns = null;
        colourIndex = null;
        columnLineageEdges = null;
        for (Consumer<LineageModel> listener : listeners) {
            listener.accept(this);
        }
    }

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
        if (selectedLink != null && !graph.successors(selectedLink.fromId()).contains(selectedLink.toId())) selectedLink = null;
        expandedIds.removeIf(id -> graph.node(id) == null);

        fire();
    }

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

    /**
     * Sets the search text. In the columns scope the found columns become the lineage roots, so
     * an explicit column selection is dropped.
     */
    public void setSearchQuery(@NotNull String query) {
        if (searchQuery.equals(query)) return;
        this.searchQuery = query;
        if (searchScope == SearchScope.COLUMNS) selectedColumnIds.clear();
        if (!query.isBlank()) markedIds = Set.of();
        fire();
    }

    /**
     * @return the tables marked as the ones a view was opened for
     */
    public @NotNull Set<String> markedIds() {
        return markedIds;
    }

    /**
     * Marks tables, such as those of the file the view was opened from: they are outlined and nothing
     * is dimmed, below a search and a selection, which clear the marks. Marks not on the graph wait for
     * a graph that has them.
     */
    public void setMarkedIds(@NotNull Set<String> ids) {
        if (markedIds.equals(ids)) return;
        markedIds = Set.copyOf(ids);
        fire();
    }

    public @NotNull SearchScope searchScope() {
        return searchScope;
    }

    /**
     * Switches between searching tables and searching columns. The text is kept and the column
     * selection is cleared.
     */
    public void setSearchScope(@NotNull SearchScope scope) {
        if (searchScope == scope) return;
        this.searchScope = scope;
        selectedColumnIds.clear();
        fire();
    }

    /**
     * Ids of the columns whose name contains the search text, in the order of the shown tables
     * and of their columns. Empty outside the columns scope and while the text is blank.
     */
    public @NotNull List<String> foundColumnIds() {
        List<String> current = foundColumns;
        if (current == null) {
            current = computeFoundColumns();
            foundColumns = current;
        }
        return current;
    }

    /** Whether the search found more columns than lineage is drawn for. */
    public boolean tooManyFoundColumns() {
        return foundColumnIds().size() > MAX_LINEAGE_ROOTS;
    }

    /**
     * Ids of the columns whose lineage is shown: the selected columns when there are any, else
     * the found columns unless there are more than {@link #MAX_LINEAGE_ROOTS}.
     */
    public @NotNull List<String> lineageRootIds() {
        if (columnGraph == null) return List.of();
        if (!selectedColumnIds.isEmpty()) {
            List<String> roots = new ArrayList<>();
            for (String id : selectedColumnIds) {
                if (columnGraph.column(id) != null) roots.add(id);
            }
            return roots;
        }
        List<String> found = foundColumnIds();
        return found.size() > MAX_LINEAGE_ROOTS ? List.of() : found;
    }

    /**
     * For every column on a shown lineage, the index of the root it belongs to, used to pick its
     * colour. A column on several lineages takes the index of the first root.
     */
    public @NotNull Map<String, Integer> lineageColourIndex() {
        Map<String, Integer> current = colourIndex;
        if (current == null) {
            current = computeColourIndex();
            colourIndex = current;
        }
        return current;
    }

    /**
     * Whether the column is one whose lineage is asked for: a selected column or a column found
     * by the search, even when the search found too many for their lineage to be shown.
     */
    public boolean isRootColumn(@NotNull String columnId) {
        Set<String> current = rootColumns;
        if (current == null) {
            current = new LinkedHashSet<>(lineageRootIds());
            current.addAll(foundColumnIds());
            rootColumns = current;
        }
        return current.contains(columnId);
    }

    private @NotNull Map<String, Integer> computeColourIndex() {
        Map<String, Integer> index = new LinkedHashMap<>();
        if (columnGraph == null) return index;
        List<String> roots = lineageRootIds();
        for (int i = 0; i < roots.size(); i++) {
            String root = roots.get(i);
            index.putIfAbsent(root, i);
            for (String id : columnGraph.upstream(root)) index.putIfAbsent(id, i);
            for (String id : columnGraph.downstream(root)) index.putIfAbsent(id, i);
        }
        return index;
    }

    private @NotNull List<String> computeFoundColumns() {
        String needle = searchQuery.trim().toLowerCase(Locale.ROOT);
        if (searchScope != SearchScope.COLUMNS || needle.isEmpty()) return List.of();
        List<String> found = new ArrayList<>();
        for (String nodeId : filteredIds()) {
            for (ColumnRef column : columnsOf(nodeId)) {
                if (column.columnName().toLowerCase(Locale.ROOT).contains(needle)) found.add(column.id());
            }
        }
        return List.copyOf(found);
    }

    /**
     * Shows every action again: every type and schema checked, no tag filter, no focus and no
     * search.
     */
    public void resetFilters() {
        enabledTypes.clear();
        enabledSchemas.clear();
        for (LineageNode node : graph.nodes()) {
            enabledTypes.add(node.dataformType());
            enabledSchemas.add(node.schema());
        }
        enabledTags.clear();
        focusId = null;
        searchQuery = "";
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
        this.selectedLink = null;
        if (id != null) markedIds = Set.of();
        fire();
    }

    /**
     * Sets who shows a node that is asked for: the editor that selects and centres a node of the graph
     * and reads the recorded lineage of another one.
     */
    public void setNodeRequestHandler(@Nullable Consumer<String> handler) {
        this.nodeRequestHandler = handler;
    }

    /**
     * Asks for a node to be shown and selected. The request is handed to the handler when there is
     * one; without it a node of the graph is selected and another one ignored.
     */
    public void requestNode(@NotNull String nodeId) {
        if (nodeRequestHandler != null) {
            nodeRequestHandler.accept(nodeId);
        } else if (graph.node(nodeId) != null) {
            select(nodeId);
        }
    }

    /**
     * @return the selected edge of the graph, {@code null} when a node or nothing is selected
     */
    public @Nullable LinkRef selectedLink() {
        return selectedLink;
    }

    /**
     * Selects an edge of the graph in place of the selected node. A link the graph does not have
     * selects nothing.
     */
    public void selectLink(@Nullable LinkRef link) {
        LinkRef known = link != null && graph.successors(link.fromId()).contains(link.toId()) ? link : null;
        selectedLink = known;
        if (known != null) selectedId = null;
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

    private @Nullable ColumnLineageGraph columnGraph;
    private @NotNull OutsideTableColumns.Result outsideColumns = OutsideTableColumns.Result.NONE;
    private final Set<String> selectedColumnIds = new LinkedHashSet<>();
    private final Set<String> expandedIds = new LinkedHashSet<>();

    /** Opens or closes the full column list of a node card. */
    public void toggleExpanded(@NotNull String nodeId) {
        if (!expandedIds.remove(nodeId)) expandedIds.add(nodeId);
        fire();
    }

    public boolean isExpanded(@NotNull String nodeId) {
        return expandedIds.contains(nodeId);
    }

    /**
     * The column rows of a node card: every column when the card is expanded, otherwise the
     * columns on a shown lineage and those found by the search. Rows keep the order of the
     * table; beyond {@link CardColumns#MAX_ROWS} the columns on a lineage or found by the
     * search are kept before the others.
     */
    public @NotNull CardColumns cardColumns(@NotNull String nodeId) {
        List<ColumnRef> all = columnsOf(nodeId);
        if (all.isEmpty()) return CardColumns.NONE;
        Set<String> marked = markedColumns();
        if (marked.isEmpty() && !expandedIds.contains(nodeId)) return CardColumns.NONE;
        List<ColumnRef> wanted = new ArrayList<>();
        for (ColumnRef column : all) {
            if (expandedIds.contains(nodeId) || marked.contains(column.id())) wanted.add(column);
        }
        if (wanted.size() <= CardColumns.MAX_ROWS) return new CardColumns(wanted, 0);
        Set<ColumnRef> kept = new LinkedHashSet<>();
        for (ColumnRef column : wanted) {
            if (kept.size() < CardColumns.MAX_ROWS && marked.contains(column.id())) kept.add(column);
        }
        for (ColumnRef column : wanted) {
            if (kept.size() < CardColumns.MAX_ROWS) kept.add(column);
        }
        List<ColumnRef> rows = new ArrayList<>();
        for (ColumnRef column : wanted) {
            if (kept.contains(column)) rows.add(column);
        }
        return new CardColumns(rows, wanted.size() - rows.size());
    }

    private @NotNull Set<String> markedColumns() {
        Set<String> current = markedColumns;
        if (current == null) {
            current = new LinkedHashSet<>(highlightColumnLineage());
            current.addAll(foundColumnIds());
            markedColumns = current;
        }
        return current;
    }

    /** Whether the columns of the node are known, so its card can list them. */
    public boolean hasColumns(@NotNull String nodeId) {
        return !columnsOf(nodeId).isEmpty();
    }

    private @NotNull List<ColumnRef> columnsOf(@NotNull String nodeId) {
        List<ColumnRef> known = columnGraph == null ? List.<ColumnRef>of() : columnGraph.columnsForTable(nodeId);
        if (!known.isEmpty()) return known;
        LineageNode node = graph.node(nodeId);
        if (node == null || !DataplexGraphBuilder.TYPE_OUTSIDE.equals(node.dataformType())) return List.of();
        return outsideColumns.columns().getOrDefault(node.fullName(), List.of());
    }

    /**
     * @return the columns read from BigQuery for the tables outside the repository
     */
    public @NotNull OutsideTableColumns.Result outsideColumns() {
        return outsideColumns;
    }

    /**
     * Sets the columns read from BigQuery for the tables outside the repository, and the tables
     * whose columns could not be read.
     */
    public void setOutsideColumns(@NotNull OutsideTableColumns.Result result) {
        if (outsideColumns.equals(result)) return;
        outsideColumns = result;
        fire();
    }

    /**
     * @return the number of shown tables outside the repository a column search could not look
     * into; {@code 0} outside a column search
     */
    public int unsearchedTableCount() {
        if (!searchingColumns()) return 0;
        int count = 0;
        for (String nodeId : filteredIds()) {
            if (unreadable(nodeId)) count++;
        }
        return count;
    }

    private boolean searchingColumns() {
        return searchScope == SearchScope.COLUMNS && !searchQuery.isBlank();
    }

    private boolean unreadable(@NotNull String nodeId) {
        LineageNode node = graph.node(nodeId);
        return node != null && DataplexGraphBuilder.TYPE_OUTSIDE.equals(node.dataformType())
                && outsideColumns.unreadable().contains(node.fullName());
    }

    /**
     * Sets the BigQuery type of the known columns, by {@link #columnTypeKey(String, String)}.
     */
    public void setColumnTypes(@NotNull Map<String, String> types) {
        this.columnTypes = Map.copyOf(types);
        fire();
    }

    /** The BigQuery type of a column, or {@code null} when it is not known. */
    public @Nullable String columnType(@NotNull ColumnRef column) {
        String key = columnTypeKey(column.tableFullName(), column.columnName());
        String type = columnTypes.get(key);
        return type != null ? type : outsideColumns.types().get(key);
    }

    /**
     * @param tableFullName the full name of the table
     * @param columnName    the name of the column, in any case
     * @return the key a column type is stored under
     */
    public static @NotNull String columnTypeKey(@NotNull String tableFullName, @NotNull String columnName) {
        return tableFullName + "#" + columnName.toLowerCase(Locale.ROOT);
    }

    /** The note shown at the bottom of a node card, or {@code null} when it has none. */
    public @Nullable String cardNote(@NotNull String nodeId) {
        return searchingColumns() && unreadable(nodeId) ? "Columns not readable" : null;
    }

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
     * Ids to highlight: every lineage root ({@link #lineageRootIds()}) plus its full upstream and
     * downstream column lineage. Empty when there is no root or no graph is set.
     */
    public @NotNull Set<String> highlightColumnLineage() {
        Set<String> current = columnLineage;
        if (current == null) {
            current = computeColumnLineage();
            columnLineage = current;
        }
        return current;
    }

    private @NotNull Set<String> computeColumnLineage() {
        if (columnGraph == null) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String id : lineageRootIds()) {
            result.add(id);
            result.addAll(columnGraph.upstream(id));
            result.addAll(columnGraph.downstream(id));
        }
        return Set.copyOf(result);
    }

    /**
     * @return where the graph comes from
     */
    public @NotNull LineageSource source() {
        return source;
    }

    /**
     * Changes where the graph comes from and remembers it for the project. The search, the tag
     * filter, the focus, the selected link and the column selection are cleared; the recorded lineage is dropped when
     * leaving Knowledge Catalog.
     */
    public void setSource(@NotNull LineageSource newSource) {
        if (source == newSource) return;
        source = newSource;
        persist(KEY_SOURCE, newSource.name());
        if (newSource == LineageSource.DATAFORM) {
            dataplexView = null;
            differencesShown = false;
            differences = List.of();
        }
        enabledTags.clear();
        searchQuery = "";
        focusId = null;
        selectedLink = null;
        selectedColumnIds.clear();
        fire();
    }

    /**
     * @return the recorded lineage the graph was built from, {@code null} when the graph does not
     * come from Knowledge Catalog or nothing is loaded
     */
    public @Nullable DataplexView dataplexView() {
        return dataplexView;
    }

    /**
     * Sets the recorded lineage the graph is built from.
     */
    public void setDataplexView(@Nullable DataplexView view) {
        if (dataplexView == view) return;
        dataplexView = view;
        fire();
    }

    /**
     * @return what the view does with the recorded lineage, for the status bar
     */
    public @NotNull RecordedActivity recordedActivity() {
        return recordedActivity;
    }

    /**
     * Sets what the view does with the recorded lineage.
     */
    public void setRecordedActivity(@NotNull RecordedActivity activity) {
        if (recordedActivity == activity) return;
        recordedActivity = activity;
        fire();
    }

    /**
     * @return how far back the recorded lineage is shown
     */
    public @NotNull TimeRange timeRange() {
        return timeRange;
    }

    /**
     * Changes how far back the recorded lineage is shown.
     */
    public void setTimeRange(@NotNull TimeRange range) {
        if (timeRange == range) return;
        timeRange = range;
        fire();
    }

    /**
     * @return whether the differences between the repository and the recorded lineage are drawn
     */
    public boolean differencesShown() {
        return differencesShown;
    }

    /**
     * Shows or hides the differences between the repository and the recorded lineage.
     */
    public void toggleDifferences() {
        setDifferencesShown(!differencesShown);
    }

    /**
     * Shows or hides the differences between the repository and the recorded lineage.
     */
    public void setDifferencesShown(boolean shown) {
        if (differencesShown == shown) return;
        differencesShown = shown;
        fire();
    }

    /**
     * @return the links on which the repository and the recorded lineage disagree, on the loaded graph
     */
    public @NotNull List<Difference> differences() {
        return differences;
    }

    /**
     * Sets the links on which the repository and the recorded lineage disagree.
     */
    public void setDifferences(@NotNull List<Difference> found) {
        if (differences.equals(found)) return;
        differences = List.copyOf(found);
        fire();
    }

    /**
     * @return how the repository and the recorded lineage disagree on an edge, {@code null} when
     * they agree or while the differences are not shown
     */
    public @Nullable Difference.Kind differenceKind(@NotNull String fromId, @NotNull String toId) {
        if (!differencesShown) return null;
        for (Difference difference : differences) {
            if (difference.fromId().equals(fromId) && difference.toId().equals(toId)) return difference.kind();
        }
        return null;
    }

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

    /**
     * Ids of all nodes passing the active filters (types AND tags AND schemas, then scope, then
     * focus), plus the tables a shown column lineage goes through even when a filter excludes them.
     */
    public @NotNull Set<String> visibleIds() {
        Set<String> visible = filteredIds();
        for (String id : columnLineageTables()) {
            if (graph.node(id) != null) visible.add(id);
        }
        return visible;
    }

    private @NotNull Set<String> filteredIds() {
        Set<String> visible = new LinkedHashSet<>();
        for (LineageNode node : graph.nodes()) {
            if (passesFilters(node)) visible.add(node.id());
        }
        if (scopeIds != null) visible.retainAll(scopeIds);
        if (focusId != null && graph.node(focusId) != null) {
            Set<String> scope = new LinkedHashSet<>();
            scope.add(focusId);
            scope.addAll(ancestors(focusId));
            scope.addAll(descendants(focusId));
            visible.retainAll(scope);
        }
        return visible;
    }

    private @NotNull Set<String> columnLineageTables() {
        if (columnGraph == null) return Set.of();
        Set<String> tables = new LinkedHashSet<>();
        for (String columnId : highlightColumnLineage()) {
            ColumnRef ref = columnGraph.column(columnId);
            if (ref != null) tables.add(ref.tableNodeId());
        }
        return tables;
    }

    private boolean passesFilters(@NotNull LineageNode node) {
        if (!enabledTypes.contains(node.dataformType())) return false;
        if (!enabledTags.isEmpty() && node.tags().stream().noneMatch(enabledTags::contains)) return false;
        return enabledSchemas.contains(node.schema());
    }

    /**
     * Ids of the shown nodes whose name, schema or tags contain the search text; empty while the
     * search is empty. With the recorded lineage the project of the table is searched too.
     */
    public @NotNull Set<String> searchMatches() {
        String needle = searchQuery.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty() || searchScope == SearchScope.COLUMNS) return Set.of();
        Set<String> matches = new LinkedHashSet<>();
        for (String id : visibleIds()) {
            LineageNode node = graph.node(id);
            if (node != null && matchesSearch(node, needle)) matches.add(id);
        }
        return matches;
    }

    /**
     * Ids of the shown nodes drawn faded: those outside the upstream and downstream of the
     * selected node, otherwise those the search leaves out. Empty with no selection and no search.
     */
    public @NotNull Set<String> dimmedIds() {
        return highlight().dimmed();
    }

    /**
     * Ids of the shown nodes drawn with the search outline: the upstream and downstream nodes of
     * the selection while it leaves other nodes out, otherwise the search matches.
     */
    public @NotNull Set<String> highlightedIds() {
        return highlight().outlined();
    }

    /**
     * Whether the edge lies on an upstream or downstream path of the selected node, drawn in the
     * accent colour.
     */
    public boolean isSelectionLineageEdge(@NotNull String fromId, @NotNull String toId) {
        Highlight current = highlight();
        return current.upstream().contains(fromId) && current.upstream().contains(toId)
                || current.downstream().contains(fromId) && current.downstream().contains(toId);
    }

    /**
     * Whether a shown column lineage goes through the table edge: a column edge links a
     * highlighted column of the table {@code fromId} to a highlighted column of the table
     * {@code toId}, whatever its confidence.
     */
    public boolean isColumnLineageEdge(@NotNull String fromId, @NotNull String toId) {
        Set<String> current = columnLineageEdges;
        if (current == null) {
            current = computeColumnLineageEdges();
            columnLineageEdges = current;
        }
        return current.contains(tableEdgeKey(fromId, toId));
    }

    private @NotNull Set<String> computeColumnLineageEdges() {
        Set<String> lineage = highlightColumnLineage();
        if (columnGraph == null || lineage.isEmpty()) return Set.of();
        Set<String> edges = new LinkedHashSet<>();
        for (ColumnEdge edge : columnGraph.edges()) {
            if (lineage.contains(edge.from().id()) && lineage.contains(edge.to().id())) {
                edges.add(tableEdgeKey(edge.from().tableNodeId(), edge.to().tableNodeId()));
            }
        }
        return edges;
    }

    private static @NotNull String tableEdgeKey(@NotNull String fromId, @NotNull String toId) {
        return fromId + '\n' + toId;
    }

    private @NotNull Highlight highlight() {
        Highlight current = highlight;
        if (current == null) {
            current = computeHighlight();
            highlight = current;
        }
        return current;
    }

    private @NotNull Highlight computeHighlight() {
        Set<String> visible = visibleIds();
        Set<String> lineageTables = columnLineageTables();
        if (!lineageTables.isEmpty()) {
            Set<String> dimmed = new LinkedHashSet<>(visible);
            dimmed.removeAll(lineageTables);
            return new Highlight(Set.copyOf(dimmed), Set.of(), Set.of(), Set.of());
        }
        if (selectedId != null && visible.contains(selectedId)) {
            Set<String> upstream = new LinkedHashSet<>(ancestors(selectedId));
            upstream.add(selectedId);
            Set<String> downstream = new LinkedHashSet<>(descendants(selectedId));
            downstream.add(selectedId);
            Set<String> dimmed = new LinkedHashSet<>(visible);
            dimmed.removeAll(upstream);
            dimmed.removeAll(downstream);
            Set<String> outlined = new LinkedHashSet<>();
            if (!dimmed.isEmpty()) {
                for (String id : visible) {
                    if (!dimmed.contains(id) && !id.equals(selectedId)) outlined.add(id);
                }
            }
            return new Highlight(Set.copyOf(dimmed), Set.copyOf(outlined), Set.copyOf(upstream), Set.copyOf(downstream));
        }
        if (!searchQuery.isBlank() && searchScope == SearchScope.TABLES) {
            Set<String> matches = searchMatches();
            Set<String> dimmed = new LinkedHashSet<>(visible);
            dimmed.removeAll(matches);
            return new Highlight(Set.copyOf(dimmed), Set.copyOf(matches), Set.of(), Set.of());
        }
        Set<String> marked = new LinkedHashSet<>(markedIds);
        marked.retainAll(visible);
        return marked.isEmpty() ? Highlight.NONE : new Highlight(Set.of(), Set.copyOf(marked), Set.of(), Set.of());
    }

    private record Highlight(@NotNull Set<String> dimmed, @NotNull Set<String> outlined,
                             @NotNull Set<String> upstream, @NotNull Set<String> downstream) {
        static final Highlight NONE = new Highlight(Set.of(), Set.of(), Set.of(), Set.of());
    }

    private boolean matchesSearch(@NotNull LineageNode node, @NotNull String needle) {
        String project = source == LineageSource.DATAPLEX ? " " + node.fullName() : "";
        String haystack = (node.name() + " " + node.schema() + " " + String.join(",", node.tags()) + project)
                .toLowerCase(Locale.ROOT);
        return haystack.contains(needle);
    }

    public @NotNull Set<String> ancestors(@NotNull String id) {
        return graph.ancestors(id);
    }

    public @NotNull Set<String> descendants(@NotNull String id) {
        return graph.descendants(id);
    }

    private void restoreViewState() {
        if (project == null) return;
        PropertiesComponent props = PropertiesComponent.getInstance(project);
        String dir = props.getValue(KEY_DIRECTION);
        if (dir != null) direction = safeValueOf(Direction.class, dir, Direction.LR);
        String den = props.getValue(KEY_DENSITY);
        if (den != null) density = safeValueOf(Density.class, den, Density.COMFORTABLE);
        minimapVisible = props.getBoolean(KEY_MINIMAP, true);
        String src = props.getValue(KEY_SOURCE);
        if (src != null) source = safeValueOf(LineageSource.class, src, LineageSource.DATAFORM);
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
