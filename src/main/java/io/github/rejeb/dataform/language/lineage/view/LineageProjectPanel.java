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
package io.github.rejeb.dataform.language.lineage.view;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.InlineBanner;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexColumnLineage;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexGraphBuilder;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageService;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.dataplex.LineageDifferences;
import io.github.rejeb.dataform.language.lineage.dataplex.OutsideTableColumns;
import io.github.rejeb.dataform.language.lineage.dataplex.TimeRange;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;
import io.github.rejeb.dataform.language.lineage.model.RecordedActivity;
import io.github.rejeb.dataform.language.lineage.model.SearchScope;
import io.github.rejeb.dataform.language.lineage.service.LineageGraphService;
import io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindow;
import io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindowTab;
import io.github.rejeb.dataform.language.ui.Actions;
import io.github.rejeb.dataform.language.ui.Banners;
import io.github.rejeb.dataform.language.ui.LatestRequest;
import io.github.rejeb.dataform.language.ui.SegmentedSwitch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Project-wide lineage view, as in the UI prototype: a toolbar (source switch, filters toggle,
 * search, view toggles, fit, refresh and legend) over a three-column body (filters | graph canvas | details)
 * with a bottom status bar. The details column starts 300 pixels wide and is resized by dragging
 * its divider; it shows an empty state while nothing is selected and can be hidden from the
 * toolbar or its own header.
 *
 * <p>The graph comes from the compiled Dataform project or, with the Knowledge Catalog source, from
 * the lineage recorded around every table of the repository. While the panel is shown, the recorded
 * lineage comes from the cache at once, then is checked and updated in the background with the
 * selection and the viewport kept. Hidden, the panel forgets its drawing and keeps only its selection
 * and viewport, to draw it again as it was.
 */
public final class LineageProjectPanel extends JPanel implements Disposable {

    private static final int SEARCH_WIDTH = 340;
    private static final int DETAILS_WIDTH = 300;
    private static final int MIN_SIDE_WIDTH = 160;

    private final Project project;
    private final LineageModel model;
    private final GraphCanvas canvas;
    private final FiltersPanel filtersPanel;
    private final SearchTextField searchField = new SearchTextField(false);
    private final SegmentedSwitch<SearchScope> scopeSwitch;
    private final JPanel searchBox = new JPanel(new BorderLayout(JBUIScale.scale(4), 0));
    private final JBLabel foundLabel = new JBLabel();
    private final LineageCards cards;
    private final DetailsPanel detailsPanel;
    private final LatestRequest refreshes = new LatestRequest(
            task -> ApplicationManager.getApplication().executeOnPooledThread(task),
            update -> ApplicationManager.getApplication().invokeLater(update, ModalityState.nonModal()));
    private final AtomicLong loadGeneration = new AtomicLong();
    private final DataplexLineageService lineageService;
    private final Executor executor;
    private final LineageToolWindow toolWindow;
    private final SegmentedSwitch<LineageSource> sourceSwitch;
    private final JBLabel loadingIcon = new JBLabel(AnimatedIcon.Default.INSTANCE);
    private final StateBannerSlot stateBannerSlot = new StateBannerSlot();
    private ActionToolbar toolbar;
    private @Nullable CompiledGraph compiled;
    private @NotNull LineageGraphService.Graphs dataform = new LineageGraphService.Graphs(null, null);
    private final RecordedLineageRefresher refresher;
    private @NotNull TimeRange requestedRange;
    private @Nullable String shownFor;
    private boolean loading;
    private boolean shown;
    private @Nullable Set<String> askedTables;
    private @Nullable String savedSelection;
    private GraphCanvas.@Nullable ViewportState savedViewport;
    private @Nullable List<String> pendingNodes;
    private boolean differencesDrawn;
    private final OutsideTableColumns outsideColumns;
    private final InlineBanner columnReasonBanner = Banners.warning("");
    private @Nullable String columnLineageReason;
    private @Nullable DataplexView columnView;
    private @NotNull List<String> columnRoots = List.of();
    private @Nullable DataplexColumnLineage.Result recordedColumns;
    private final Map<String, ColumnRef> discoveredColumns = new LinkedHashMap<>();
    private final LatestRequest columnReads;
    private @Nullable ColumnLineageGraph recordedGraph;
    private @Nullable ColumnLineageGraph appliedKnown;
    private @Nullable OutsideTableColumns.Result appliedOutside;
    private @Nullable DataplexColumnLineage.Result appliedRecorded;
    private int appliedDiscovered = -1;
    private final Set<String> outsideAsked = new HashSet<>();
    private long outsideGeneration;

    public LineageProjectPanel(@NotNull Project project, @NotNull LineageModel model) {
        this(project, model, DataplexLineageService.getInstance(project), AppExecutorUtil.getAppExecutorService());
    }

    LineageProjectPanel(@NotNull Project project, @NotNull LineageModel model,
                        @NotNull DataplexLineageService lineageService, @NotNull Executor executor) {
        this(project, model, lineageService, executor, LineageToolWindow.getInstance(project),
                OutsideTableColumns.getInstance(project));
    }

    /**
     * @param lineageService reads the lineage recorded by Knowledge Catalog
     * @param executor       runs those reads off the event dispatch thread
     * @param toolWindow     shows the runs, the trace and the differences of the selection
     * @param outsideColumns reads the columns of the tables outside the repository
     */
    LineageProjectPanel(@NotNull Project project, @NotNull LineageModel model,
                        @NotNull DataplexLineageService lineageService, @NotNull Executor executor,
                        @NotNull LineageToolWindow toolWindow, @NotNull OutsideTableColumns outsideColumns) {
        super(new BorderLayout());
        this.outsideColumns = outsideColumns;
        this.project = project;
        this.model = model;
        this.lineageService = lineageService;
        this.executor = executor;
        this.columnReads = new LatestRequest(executor,
                update -> ApplicationManager.getApplication().invokeLater(update, ModalityState.nonModal()));
        this.toolWindow = toolWindow;
        this.requestedRange = model.timeRange();
        this.refresher = new RecordedLineageRefresher(lineageService, executor, new RecordedLineageRefresher.Listener() {
            @Override
            public void onView(@NotNull DataplexView view, boolean update) {
                showRecorded(view, update);
            }

            @Override
            public void onActivity(@NotNull RecordedActivity activity) {
                showActivity(activity);
            }

            @Override
            public void onFailure(@NotNull DataplexLineageException.Kind kind, @Nullable String detail, boolean viewShown) {
                showFailure(kind, detail, viewShown);
            }
        });
        setOpaque(true);
        setBackground(UIUtil.getPanelBackground());

        canvas = new GraphCanvas(project, model);
        canvas.setExpandHandler(this::expand);
        canvas.setFreshnessTips(new FreshnessTips(model, lineageService, executor, Clock.systemDefaultZone()));
        sourceSwitch = new SegmentedSwitch<>(List.of(LineageSource.DATAFORM, LineageSource.DATAPLEX),
                LineageSource::label, model.source(), this::switchSource);
        scopeSwitch = new SegmentedSwitch<>(List.of(SearchScope.TABLES, SearchScope.COLUMNS),
                scope -> scope == SearchScope.TABLES ? "Tables" : "Columns", model.searchScope(), model::setSearchScope);
        filtersPanel = new FiltersPanel(model);
        detailsPanel = new DetailsPanel(project, model, lineageService, executor, Clock.systemDefaultZone(), BrowserUtil::browse);
        canvas.setLinkSelection(true);
        detailsPanel.setHideHandler(() -> setDetailsShown(false));
        detailsPanel.setTabOpener(toolWindow::show);
        StatusBar statusBar = new StatusBar(model);
        canvas.setZoomListener(statusBar::setZoom);
        canvas.setSelectionHandler(() -> setDetailsShown(true));

        ThreeComponentsSplitter graphArea = new ThreeComponentsSplitter(false, true);
        JPanel banners = new JPanel(new VerticalLayout(JBUIScale.scale(6)));
        banners.setOpaque(false);
        banners.add(stateBannerSlot);
        columnReasonBanner.setVisible(false);
        banners.add(columnReasonBanner);
        banners.add(new LineageFocusBanner(model).view());
        graphArea.setInnerComponent(new CanvasOverlayPanel(canvas, banners, LineageToolbarSupport.zoomControls(canvas)));
        graphArea.setLastComponent(detailsPanel);
        graphArea.setMinSize(JBUIScale.scale(MIN_SIDE_WIDTH));
        graphArea.setLastSize(JBUIScale.scale(DETAILS_WIDTH));

        cards = new LineageCards(model, buildToolbar(), filtersPanel, graphArea, statusBar,
                "Compile the Dataform project to see the lineage.");
        add(cards, BorderLayout.CENTER);

        installSearchShortcut();
        update();
        model.addListener(m -> update());
        model.setNodeRequestHandler(this::reveal);
        toolWindow.attach(model);
    }

    /**
     * Switches to a lineage source, as the toolbar switch does.
     */
    public void showSource(@NotNull LineageSource source) {
        sourceSwitch.select(source);
    }

    /**
     * Switches to a lineage source, then shows tables on the first graph that holds them: one is
     * selected and centred, several are marked and fitted to the view, none clears the marks. With
     * Knowledge Catalog that graph is the recorded one. A newer call replaces one still waiting.
     *
     * @param source the source to switch to, {@code null} to keep the one shown
     */
    public void showNodes(@Nullable LineageSource source, @NotNull List<String> nodeIds) {
        if (source != null) showSource(source);
        pendingNodes = List.copyOf(nodeIds);
        applyPendingNodes();
    }

    private void applyPendingNodes() {
        List<String> ids = pendingNodes;
        if (ids == null) return;
        if (ids.isEmpty()) {
            pendingNodes = null;
            model.setMarkedIds(Set.of());
            return;
        }
        if (model.source() == LineageSource.DATAPLEX && model.dataplexView() == null) return;
        Set<String> present = new LinkedHashSet<>();
        for (String id : ids) {
            if (model.graph().node(id) != null) present.add(id);
        }
        if (present.isEmpty()) return;
        pendingNodes = null;
        if (present.size() == 1) {
            String id = present.iterator().next();
            model.select(id);
            canvas.centerOn(id);
            return;
        }
        model.select(null);
        model.setMarkedIds(present);
        canvas.fitTo(present);
    }

    @Override
    public void dispose() {
        refresher.hide();
        model.setNodeRequestHandler(null);
        toolWindow.detach(model);
    }

    /**
     * Tells the panel whether it is visible. Hidden with the Knowledge Catalog source, it clears its
     * drawing and releases the recorded lineage, keeping the selection and the viewport; shown again,
     * it draws the cached lineage as it was, then checks it. The Dataform Lineage tool window keeps
     * what it shows.
     */
    public void setShown(boolean visible) {
        if (shown == visible) return;
        shown = visible;
        if (model.source() != LineageSource.DATAPLEX) return;
        if (visible) {
            askRecorded();
            return;
        }
        if (model.dataplexView() != null) {
            savedSelection = model.selectedId();
            savedViewport = canvas.viewportState();
        }
        stopRecorded();
        dropView();
        dropOutsideColumns();
    }

    private void dropOutsideColumns() {
        outsideAsked.clear();
        outsideGeneration++;
        model.setOutsideColumns(OutsideTableColumns.Result.NONE);
    }

    private void reveal(@NotNull String nodeId) {
        if (model.graph().node(nodeId) != null) {
            model.select(nodeId);
            canvas.centerOn(nodeId);
            return;
        }
        DataplexView view = model.dataplexView();
        if (model.source() != LineageSource.DATAPLEX || view == null) return;
        long generation = loadGeneration.get();
        setLoading(true);
        executor.execute(() -> {
            DataplexView read = null;
            String failure = null;
            try {
                read = lineageService.include(view, nodeId);
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (RuntimeException e) {
                failure = e.getMessage();
            }
            DataplexView larger = read;
            String detail = failure;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (generation != loadGeneration.get()) return;
                setLoading(model.recordedActivity().isBusy());
                if (larger == null) {
                    stateBannerSlot.setBanner(DataplexStateBanner.of(project, DataplexStateBanner.State.FAILED,
                            view.scope().projectId(), view.range(), detail, this::reloadDataplex,
                            () -> sourceSwitch.select(LineageSource.DATAFORM)));
                    return;
                }
                if (larger != view) {
                    canvas.keepView();
                    show(larger);
                    applyPendingNodes();
                }
                if (model.graph().node(nodeId) != null) {
                    model.select(nodeId);
                    canvas.centerOn(nodeId);
                }
            }, ModalityState.nonModal());
        });
    }

    /**
     * The search field of the toolbar.
     */
    @NotNull SearchTextField searchField() {
        return searchField;
    }

    /**
     * The search field with its Tables | Columns switch, as it sits in the toolbar.
     */
    @NotNull JComponent searchBox() {
        return searchBox;
    }

    /**
     * The counter next to the search field, hidden while the search is empty.
     */
    @NotNull JBLabel foundLabel() {
        return foundLabel;
    }

    /**
     * The Dataform | Knowledge Catalog switch, first in the toolbar.
     */
    @NotNull SegmentedSwitch<LineageSource> sourceSwitch() {
        return sourceSwitch;
    }

    /**
     * The graph canvas.
     */
    @NotNull GraphCanvas canvas() {
        return canvas;
    }

    /**
     * The banner telling why Knowledge Catalog has nothing to show, {@code null} while it has.
     */
    @Nullable DataplexStateBanner stateBanner() {
        return stateBannerSlot.banner();
    }

    /**
     * Whether the recorded lineage is being read.
     */
    boolean isLoading() {
        return loading;
    }

    /**
     * Recompiles (when {@code force}) or reads the current compiled graph, extracts the
     * lineage on a pooled thread, and applies it on the EDT: as the graph with the Dataform source,
     * as what the recorded lineage is matched against with Knowledge Catalog.
     */
    public void refresh(boolean force) {
        refreshes.submit(() -> {
            DataformCompilationService svc = DataformCompilationService.getInstance(project);
            CompiledGraph compiled = force ? svc.compile(true) : svc.getCompiledGraph();
            LineageGraphService.Graphs graphs = compiled == null
                    ? new LineageGraphService.Graphs(null, null)
                    : LineageGraphService.getInstance(project).graphs(compiled);
            return new Refreshed(compiled, graphs, ColumnTypes.of(project));
        }, refreshed -> {
            model.setColumnTypes(refreshed.columnTypes());
            showCompiled(refreshed.compiled(), refreshed.graphs());
        });
    }

    /**
     * Takes a compiled project and its Dataform lineage. With Knowledge Catalog the recorded lineage
     * is shown and checked again only when the tables of the repository changed.
     */
    void showCompiled(@Nullable CompiledGraph compiledGraph, @NotNull LineageGraphService.Graphs graphs) {
        compiled = compiledGraph;
        dataform = graphs;
        if (model.source() == LineageSource.DATAFORM) {
            model.setColumnGraph(graphs.columnGraph());
            model.setGraph(graphs.tableGraph());
            applyPendingNodes();
        } else {
            applyRecordedColumns();
            if (!tablesOf(graphs).equals(askedTables)) askRecorded();
        }
        refreshEmptyCard();
    }

    /**
     * Reads the recorded lineage again, without the check.
     */
    void reloadDataplex() {
        outsideAsked.clear();
        outsideGeneration++;
        CompiledGraph compiledGraph = compiled;
        if (!shown || compiledGraph == null || model.source() != LineageSource.DATAPLEX) return;
        requestedRange = model.timeRange();
        askedTables = tablesOf(dataform);
        loadGeneration.incrementAndGet();
        stateBannerSlot.setBanner(null);
        refresher.reload(compiledGraph, requestedRange);
    }

    private void switchSource(@NotNull LineageSource source) {
        if (model.source() == source) return;
        String kept = model.selectedId() != null ? model.selectedId() : savedSelection;
        model.setSource(source);
        if (source == LineageSource.DATAPLEX) {
            savedSelection = kept;
            savedViewport = null;
            askRecorded();
        } else {
            stopRecorded();
            savedSelection = null;
            savedViewport = null;
            shownFor = null;
            toolWindow.hide();
            model.setGraph(dataform.tableGraph());
            if (kept != null && model.graph().node(kept) != null) model.select(kept);
            canvas.recenter();
            applyPendingNodes();
        }
        if (toolbar != null) toolbar.updateActionsAsync();
    }

    private void askRecorded() {
        requestedRange = model.timeRange();
        CompiledGraph compiledGraph = compiled;
        if (!shown || compiledGraph == null) return;
        askedTables = tablesOf(dataform);
        loadGeneration.incrementAndGet();
        refresher.show(compiledGraph, requestedRange);
    }

    private void stopRecorded() {
        refresher.hide();
        loadGeneration.incrementAndGet();
        askedTables = null;
        setLoading(false);
        model.setRecordedActivity(RecordedActivity.NONE);
        stateBannerSlot.setBanner(null);
        canvas.clearPlaceholder();
    }

    private static @NotNull Set<String> tablesOf(@NotNull LineageGraphService.Graphs graphs) {
        LineageGraph graph = graphs.tableGraph();
        Set<String> tables = new HashSet<>();
        if (graph == null) return tables;
        for (LineageNode node : graph.nodes()) tables.add(node.id() + " " + node.dataformType());
        return tables;
    }

    private void showRecorded(@NotNull DataplexView view, boolean update) {
        loadGeneration.incrementAndGet();
        canvas.clearPlaceholder();
        if (update || model.dataplexView() != null) {
            canvas.keepView();
            show(view);
        } else {
            drawAsLeft(view);
        }
        applyPendingNodes();
        stateBannerSlot.setBanner(view.snapshot().links().isEmpty()
                ? DataplexStateBanner.of(project, DataplexStateBanner.State.NOTHING_RECORDED, view.scope().projectId(),
                view.range(), null, this::reloadDataplex, () -> sourceSwitch.select(LineageSource.DATAFORM))
                : null);
    }

    private void drawAsLeft(@NotNull DataplexView view) {
        String selection = savedSelection;
        GraphCanvas.ViewportState viewport = savedViewport;
        savedSelection = null;
        savedViewport = null;
        if (viewport != null && !view.graph().isEmpty()) canvas.restoreViewport(viewport);
        show(view);
        if (selection != null && model.graph().node(selection) != null) {
            shownFor = selection;
            model.select(selection);
        }
        if (viewport == null) canvas.recenter();
    }

    private void showActivity(@NotNull RecordedActivity activity) {
        model.setRecordedActivity(activity);
        setLoading(activity.isBusy());
        if (activity == RecordedActivity.LOADING && model.dataplexView() == null) {
            stateBannerSlot.setBanner(null);
            dropView();
            canvas.setPlaceholder("Loading the Knowledge Catalog lineage…", "Show Dataform lineage",
                    () -> sourceSwitch.select(LineageSource.DATAFORM));
        }
    }

    private void showFailure(@NotNull DataplexLineageException.Kind kind, @Nullable String detail, boolean viewShown) {
        if (viewShown && model.dataplexView() != null) {
            stateBannerSlot.setBanner(DataplexStateBanner.updateFailed(detail, this::reloadDataplex));
            return;
        }
        canvas.clearPlaceholder();
        dropView();
        stateBannerSlot.setBanner(DataplexStateBanner.of(project, DataplexStateBanner.stateOf(kind),
                DataplexStateBanner.projectIdOf(lineageService, compiled), requestedRange,
                detail, this::reloadDataplex, () -> sourceSwitch.select(LineageSource.DATAFORM)));
    }

    private void expand(@NotNull String nodeId, @NotNull DataplexLineageClient.Direction direction) {
        DataplexView view = model.dataplexView();
        if (view == null || loading) return;
        long generation = loadGeneration.get();
        setLoading(true);
        executor.execute(() -> {
            DataplexView read = null;
            String failure = null;
            try {
                read = lineageService.expand(view, nodeId, direction);
            } catch (RuntimeException e) {
                failure = e.getMessage();
            }
            DataplexView larger = read;
            String detail = failure;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (generation != loadGeneration.get()) return;
                setLoading(model.recordedActivity().isBusy());
                if (larger == null) {
                    stateBannerSlot.setBanner(DataplexStateBanner.of(project, DataplexStateBanner.State.FAILED,
                            view.scope().projectId(), view.range(), detail, this::reloadDataplex,
                            () -> sourceSwitch.select(LineageSource.DATAFORM)));
                    return;
                }
                if (larger == view) return;
                canvas.keepInPlace(nodeId);
                show(larger);
                applyPendingNodes();
            }, ModalityState.nonModal());
        });
    }

    private void show(@NotNull DataplexView view) {
        model.setDataplexView(view);
        model.setDifferences(LineageDifferences.compute(view, view.repository(),
                LineageDifferences.writtenDatasets(view.repository())));
        differencesDrawn = model.differencesShown();
        model.setGraph(drawn(view));
    }

    private void dropView() {
        model.setDataplexView(null);
        model.setDifferences(List.of());
        model.setGraph(null);
    }

    private @NotNull LineageGraph drawn(@NotNull DataplexView view) {
        return model.differencesShown()
                ? LineageDifferences.withDeclaredOnly(view.graph(), view.repository(), model.differences())
                : view.graph();
    }

    private void drawDifferences(boolean recorded) {
        boolean shown = recorded && model.differencesShown();
        if (differencesDrawn == shown) return;
        differencesDrawn = shown;
        if (!recorded) return;
        DataplexView view = model.dataplexView();
        if (view != null) model.setGraph(drawn(view));
        if (shown) toolWindow.show(LineageToolWindowTab.DIFFERENCES);
    }

    private void readOutsideColumns(boolean recorded) {
        DataplexView view = model.dataplexView();
        if (!recorded || view == null || model.searchScope() != SearchScope.COLUMNS) return;
        List<String> names = new ArrayList<>();
        for (LineageNode node : view.graph().nodes()) {
            if (DataplexGraphBuilder.TYPE_OUTSIDE.equals(node.dataformType()) && outsideAsked.add(node.fullName())) {
                names.add(node.fullName());
            }
        }
        if (names.isEmpty()) return;
        long generation = outsideGeneration;
        String projectId = view.scope().projectId();
        executor.execute(() -> {
            OutsideTableColumns.Result read;
            try {
                read = outsideColumns.load(projectId, names);
            } catch (RuntimeException e) {
                read = new OutsideTableColumns.Result(Map.of(), Map.of(), Set.copyOf(names));
            }
            OutsideTableColumns.Result result = read;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (generation == outsideGeneration) model.setOutsideColumns(model.outsideColumns().with(result));
            }, ModalityState.nonModal());
        });
    }

    /**
     * @return why the column lineage recorded by Knowledge Catalog cannot be shown, {@code null}
     * when it can or when none is asked for
     */
    @Nullable String columnLineageReason() {
        return columnLineageReason;
    }

    private void syncColumnLineage(boolean recorded) {
        if (!recorded) {
            if (recordedGraph == null) return;
            columnReads.cancel();
            recordedGraph = null;
            columnView = null;
            columnRoots = List.of();
            recordedColumns = null;
            discoveredColumns.clear();
            showColumnLineageReason(null);
            model.setColumnGraph(dataform.columnGraph());
            return;
        }
        DataplexView view = model.dataplexView();
        if (view != columnView) {
            columnReads.cancel();
            columnView = view;
            columnRoots = List.of();
            recordedColumns = null;
            discoveredColumns.clear();
            showColumnLineageReason(null);
        }
        applyRecordedColumns();
        List<String> roots = view == null ? List.of() : model.lineageRootIds();
        if (roots.equals(columnRoots)) return;
        columnReads.cancel();
        columnRoots = roots;
        recordedColumns = null;
        showColumnLineageReason(null);
        applyRecordedColumns();
        ColumnLineageGraph graph = model.columnGraph();
        if (roots.isEmpty() || view == null || graph == null) return;
        List<ColumnRef> refs = new ArrayList<>();
        for (String id : roots) {
            ColumnRef column = graph.column(id);
            if (column != null) refs.add(column);
        }
        columnReads.submit(() -> lineageService.columnLineage(view, refs),
                this::showRecordedColumns,
                RuntimeException.class,
                e -> showRecordedColumns(new DataplexColumnLineage.Result(ColumnLineageGraph.builder().build(),
                        "Knowledge Catalog cannot give the column lineage: " + e.getMessage())));
    }

    private void showRecordedColumns(@NotNull DataplexColumnLineage.Result result) {
        recordedColumns = result;
        for (ColumnRef column : result.graph().columns()) {
            discoveredColumns.putIfAbsent(column.id().toLowerCase(Locale.ROOT), column);
        }
        showColumnLineageReason(result.reason());
        applyRecordedColumns();
    }

    private void applyRecordedColumns() {
        ColumnLineageGraph known = dataform.columnGraph();
        OutsideTableColumns.Result outside = model.outsideColumns();
        DataplexColumnLineage.Result result = recordedColumns;
        if (recordedGraph != null && model.columnGraph() == recordedGraph && known == appliedKnown
                && outside == appliedOutside && result == appliedRecorded && discoveredColumns.size() == appliedDiscovered) {
            return;
        }
        appliedKnown = known;
        appliedOutside = outside;
        appliedRecorded = result;
        appliedDiscovered = discoveredColumns.size();
        recordedGraph = DataplexColumnLineage.over(known, outside.columns(), result == null ? null : result.graph(),
                discoveredColumns.values());
        model.setColumnGraph(recordedGraph);
    }

    private void showColumnLineageReason(@Nullable String reason) {
        if (java.util.Objects.equals(columnLineageReason, reason)) return;
        columnLineageReason = reason;
        if (reason != null) Banners.setMessage(columnReasonBanner, reason);
        columnReasonBanner.setVisible(reason != null);
        Container overlay = columnReasonBanner.getParent() == null ? null : columnReasonBanner.getParent().getParent();
        if (overlay != null) {
            overlay.revalidate();
            overlay.repaint();
        }
    }

    private void setLoading(boolean value) {
        loading = value;
        loadingIcon.setVisible(value);
    }

    private JComponent buildToolbar() {
        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                model.setSearchQuery(searchField.getText());
            }
        });
        scopeSwitch.setCompact(true);
        scopeSwitch.getAccessibleContext().setAccessibleName("Search in");
        JPanel scopeHolder = new JPanel(new GridBagLayout());
        scopeHolder.setOpaque(false);
        scopeHolder.add(scopeSwitch);
        searchBox.setOpaque(false);
        searchBox.add(scopeHolder, BorderLayout.WEST);
        searchBox.add(searchField, BorderLayout.CENTER);
        searchBox.setPreferredSize(new Dimension(JBUIScale.scale(SEARCH_WIDTH), searchField.getPreferredSize().height));
        foundLabel.setFont(JBFont.medium());
        foundLabel.setForeground(UIUtil.getContextHelpForeground());
        foundLabel.setBorder(JBUI.Borders.empty(0, 2, 0, 4));

        sourceSwitch.getAccessibleContext().setAccessibleName("Lineage source");
        JPanel sourceHolder = new JPanel(new GridBagLayout());
        sourceHolder.setOpaque(false);
        sourceHolder.setBorder(JBUI.Borders.emptyRight(6));
        sourceHolder.add(sourceSwitch);
        loadingIcon.setVisible(false);
        loadingIcon.setToolTipText("Reading the lineage recorded by Knowledge Catalog");
        loadingIcon.setBorder(JBUI.Borders.empty(0, 4));

        List<AnAction> shared = new ArrayList<>();
        shared.add(LineageToolbarSupport.component(sourceHolder));
        shared.add(Actions.toggle("Show filters", "Show or hide the filters",
                () -> LineageIcons.FILTERS, filtersPanel::isVisible,
                () -> filtersPanel.setVisible(!filtersPanel.isVisible())));
        shared.add(LineageToolbarSupport.component(searchBox));
        shared.add(LineageToolbarSupport.component(foundLabel));
        shared.add(Separator.create());
        shared.add(LineageToolbarSupport.directionAction(model));
        shared.add(LineageToolbarSupport.densityToggle(model));
        shared.add(Actions.toggle("Show minimap", "Show or hide the minimap",
                () -> LineageIcons.MINIMAP, model::minimapVisible, model::toggleMinimap));
        shared.add(Actions.toggle("Show details", "Show or hide the details of the selection",
                () -> LineageIcons.DETAILS, detailsPanel::isVisible,
                () -> setDetailsShown(!detailsPanel.isVisible())));
        shared.add(Separator.create());
        shared.add(Actions.of("Fit to view", "Fit the graph to the visible area",
                LineageIcons.FIT, canvas::fitToView));
        AnAction legend = LineageToolbarSupport.legendAction(
                () -> LineageLegend.orderedTypes(model.typeCounts().keySet()), model::source);

        List<AnAction> declared = new ArrayList<>(shared);
        declared.add(Actions.of("Recompile and refresh", "Recompile the project and refresh the lineage",
                DataformIcons.REFRESH, () -> refresh(true)));
        declared.add(legend);

        List<AnAction> recorded = new ArrayList<>(shared);
        recorded.add(Actions.of("Reload from Knowledge Catalog",
                "Read the recorded lineage again", DataformIcons.REFRESH, this::reloadDataplex));
        recorded.add(LineageToolbarSupport.component(loadingIcon));
        recorded.add(LineageToolbarSupport.timeRangeAction(model));
        recorded.add(LineageToolbarSupport.differencesToggle(model));
        recorded.add(legend);

        AnAction[] declaredActions = declared.toArray(AnAction.EMPTY_ARRAY);
        AnAction[] recordedActions = recorded.toArray(AnAction.EMPTY_ARRAY);
        ActionGroup group = new ActionGroup() {
            @Override
            public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
                return model.source() == LineageSource.DATAPLEX ? recordedActions : declaredActions;
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };

        toolbar = ActionManager.getInstance().createActionToolbar("DataformLineageToolbar", group, true);
        toolbar.setTargetComponent(this);
        return LineageToolbarSupport.toolbarRow(toolbar);
    }

    private static String fSearchHint() {
        return java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() == java.awt.event.InputEvent.META_DOWN_MASK
                ? "⌘F" : "Ctrl+F";
    }

    private void installSearchShortcut() {
        int menuMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, menuMask), "lineage.search");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "lineage.search");
        getActionMap().put("lineage.search", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchField.getTextEditor().requestFocusInWindow();
                searchField.selectText();
            }
        });
    }

    private void showToolWindowForSelection(boolean recorded) {
        String selected = recorded ? model.selectedId() : null;
        if (Objects.equals(selected, shownFor)) return;
        shownFor = selected;
        if (selected != null) toolWindow.show(null);
    }

    private void setDetailsShown(boolean shown) {
        if (detailsPanel.isVisible() == shown) return;
        detailsPanel.setVisible(shown);
        revalidate();
        repaint();
    }

    private void refreshEmptyCard() {
        boolean recorded = model.source() == LineageSource.DATAPLEX;
        cards.showEmpty(model.graph().isEmpty() && (!recorded || compiled == null));
    }

    private void update() {
        boolean recorded = model.source() == LineageSource.DATAPLEX;
        refreshEmptyCard();
        if (sourceSwitch.selected() != model.source()) sourceSwitch.select(model.source());
        if (recorded && askedTables != null && requestedRange != model.timeRange()) askRecorded();
        drawDifferences(recorded);
        readOutsideColumns(recorded);
        syncColumnLineage(recorded);
        showToolWindowForSelection(recorded);
        String query = model.searchQuery();
        if (!query.equals(searchField.getText())) searchField.setText(query);
        if (scopeSwitch.selected() != model.searchScope()) scopeSwitch.select(model.searchScope());
        String placeholder = model.searchScope() == SearchScope.COLUMNS ? "Search columns…"
                : recorded ? "Search tables, projects…" : "Search tables, tags…";
        searchField.getTextEditor().getEmptyText().setText(placeholder + "   " + fSearchHint());
        String counter = SearchCounter.text(model);
        foundLabel.setVisible(!counter.isEmpty());
        foundLabel.setText(counter);
    }

    private record Refreshed(@Nullable CompiledGraph compiled, @NotNull LineageGraphService.Graphs graphs,
                             @NotNull Map<String, String> columnTypes) {
    }
}
