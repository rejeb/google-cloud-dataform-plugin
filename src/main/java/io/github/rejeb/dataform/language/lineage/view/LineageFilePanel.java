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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageService;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;
import io.github.rejeb.dataform.language.lineage.model.RecordedActivity;
import io.github.rejeb.dataform.language.ui.Actions;
import io.github.rejeb.dataform.language.ui.PreviewHeader;
import io.github.rejeb.dataform.language.ui.SegmentedSwitch;
import io.github.rejeb.dataform.language.ui.Toolbars;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.BorderLayout;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Lineage view scoped to a single SQLX file: shows the actions defined in that file
 * together with their direct upstream and downstream actions only. With the Knowledge Catalog
 * source it shows what was recorded as read and written around every table of the file, tables
 * outside the repository included, without expanders, link selection or tool window.
 *
 * <p>Reuses {@link GraphCanvas} and {@link LineageModel} from the project-wide lineage view;
 * the restriction is expressed through {@link LineageModel#setScopeIds}. The recorded lineage is the
 * full lineage of the repository shared with the project view: while the view is shown it comes from
 * the cache, then is checked and updated in the background; hidden, the view clears its drawing.</p>
 */
public final class LineageFilePanel extends JPanel {

    private static final String SHOW_DATAFORM = "Show Dataform lineage";
    private static final String ASSERTION = "assertion";
    private static final String NO_COMPILED_TABLE = "No compiled table of this file to look up in Knowledge Catalog";

    private final Project project;
    private final VirtualFile file;
    private final LineageModel model;
    private final GraphCanvas canvas;
    private final LineageCards cards;
    private final DataplexLineageService lineageService;
    private final Executor executor;
    private final Supplier<@Nullable CompiledGraph> compiledGraph;
    private final SegmentedSwitch<LineageSource> sourceSwitch;
    private final PreviewHeader header = new PreviewHeader();
    private final JBLabel loadingIcon = new JBLabel(AnimatedIcon.Default.INSTANCE);
    private final StateBannerSlot stateBannerSlot = new StateBannerSlot();
    private final RecordedLineageRefresher refresher;
    private @Nullable LineageGraph declared;
    private @Nullable DataplexView recordedView;
    private boolean loading;
    private boolean shown;
    private @NotNull BiConsumer<LineageSource, List<String>> projectLineageOpener;

    public LineageFilePanel(@NotNull Project project, @NotNull VirtualFile file) {
        this(project, file, DataplexLineageService.getInstance(project), AppExecutorUtil.getAppExecutorService(),
                () -> DataformCompilationService.getInstance(project).getCompiledGraph());
    }

    /**
     * @param lineageService reads the lineage recorded by Knowledge Catalog
     * @param executor       runs those reads off the event dispatch thread
     * @param compiledGraph  gives the compiled project the recorded tables are matched against,
     *                       {@code null} before the first compilation; called on the event dispatch thread
     */
    LineageFilePanel(@NotNull Project project, @NotNull VirtualFile file, @NotNull DataplexLineageService lineageService,
                     @NotNull Executor executor, @NotNull Supplier<@Nullable CompiledGraph> compiledGraph) {
        super(new BorderLayout());
        this.project = project;
        this.file = file;
        this.model = new LineageModel(project);
        this.lineageService = lineageService;
        this.executor = executor;
        this.compiledGraph = compiledGraph;
        this.projectLineageOpener = (source, nodeIds) -> OpenLineageViewAction.open(project, source, nodeIds);
        this.refresher = new RecordedLineageRefresher(lineageService, executor, new RecordedLineageRefresher.Listener() {
            @Override
            public void onView(@NotNull DataplexView view, boolean update) {
                recordedView = view;
                stateBannerSlot.setBanner(null);
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
        canvas.setMinimapShown(false);
        canvas.setFitDown(true);
        canvas.setFreshnessTips(new FreshnessTips(model, lineageService, executor, Clock.systemDefaultZone()));
        sourceSwitch = new SegmentedSwitch<>(List.of(LineageSource.DATAFORM, LineageSource.DATAPLEX),
                LineageSource::label, model.source(), this::switchSource);

        JPanel banners = new JPanel(new VerticalLayout(JBUIScale.scale(6)));
        banners.setOpaque(false);
        banners.add(stateBannerSlot);
        banners.add(new LineageFocusBanner(model).view());
        CanvasOverlayPanel graphArea = new CanvasOverlayPanel(canvas, banners, null);
        cards = new LineageCards(model, buildHeader(), null, graphArea, StatusBar.forFile(model),
                "No lineage for this file. Compile the Dataform project.");
        add(cards, BorderLayout.CENTER);

        update();
        model.addListener(m -> update());
    }

    /**
     * Applies a freshly extracted lineage graph, re-scopes the view to the actions declared in the
     * edited file plus their direct neighbours, and selects the action of the file. With the
     * Knowledge Catalog source the recorded lineage already shown is scoped again to the tables of
     * the file, without asking Knowledge Catalog. Must be called on the EDT.
     */
    public void setLineage(@Nullable LineageGraph graph) {
        declared = graph;
        if (model.source() != LineageSource.DATAPLEX) {
            showDeclared();
        } else if (recordedView != null) {
            showRecorded(recordedView, true);
        } else {
            askRecorded();
        }
    }

    /**
     * Tells the view whether it is visible: its Lineage card shown in a selected file editor. Hidden
     * with the Knowledge Catalog source, it clears its drawing and releases the recorded lineage;
     * shown again, it draws the cached lineage, then checks it.
     */
    public void setShown(boolean visible) {
        if (shown == visible) return;
        shown = visible;
        if (model.source() != LineageSource.DATAPLEX) return;
        if (visible) {
            askRecorded();
            return;
        }
        stopRecorded();
        clearDrawing();
    }

    @NotNull LineageModel model() {
        return model;
    }

    /**
     * The Dataform | Knowledge Catalog switch of the header.
     */
    @NotNull SegmentedSwitch<LineageSource> sourceSwitch() {
        return sourceSwitch;
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

    private void showDeclared() {
        canvas.clearPlaceholder();
        model.setScopeIds(null);
        model.setGraph(declared);
        Set<String> own = ownActions(model.graph());
        model.setScopeIds(computeScope(model.graph(), own));
        model.select(own.isEmpty() ? null : own.iterator().next());
        canvas.recenter();
    }

    private void switchSource(@NotNull LineageSource source) {
        if (model.source() == source) return;
        model.setSource(source);
        if (source == LineageSource.DATAPLEX) {
            askRecorded();
        } else {
            stopRecorded();
            showDeclared();
        }
    }

    private void askRecorded() {
        if (ownActions().isEmpty()) {
            stopRecorded();
            showNothing(NO_COMPILED_TABLE);
            return;
        }
        if (!shown) return;
        CompiledGraph compiled = compiledGraph.get();
        if (compiled == null) {
            showNothing(NO_COMPILED_TABLE);
            return;
        }
        refresher.show(compiled, model.timeRange());
    }

    private void reloadRecorded() {
        CompiledGraph compiled = compiledGraph.get();
        if (!shown || compiled == null || model.source() != LineageSource.DATAPLEX) return;
        stateBannerSlot.setBanner(null);
        refresher.reload(compiled, model.timeRange());
    }

    private void stopRecorded() {
        refresher.hide();
        recordedView = null;
        setLoading(false);
        stateBannerSlot.setBanner(null);
    }

    private void clearDrawing() {
        canvas.clearPlaceholder();
        model.setScopeIds(null);
        model.setDataplexView(null);
        model.setGraph(null);
    }

    private void showRecorded(@NotNull DataplexView view, boolean update) {
        Set<String> declaredOwn = ownActions();
        Set<String> own = new LinkedHashSet<>();
        for (String id : declaredOwn) {
            if (view.graph().node(id) != null) own.add(id);
        }
        Set<String> scope = computeScope(view.graph(), own);
        if (scope.size() == own.size()) {
            showNothing(declaredOwn.isEmpty() ? NO_COMPILED_TABLE : "No lineage recorded for "
                    + tablesName(view.graph(), own) + " in the " + view.range().label().toLowerCase(Locale.ROOT));
            return;
        }
        String selected = model.selectedId();
        canvas.clearPlaceholder();
        model.setScopeIds(null);
        model.setDataplexView(view);
        model.setGraph(view.graph());
        model.setScopeIds(scope);
        boolean keep = update && selected != null && scope.contains(selected);
        model.select(keep ? selected : own.iterator().next());
        if (!keep) canvas.recenter();
    }

    private @NotNull String tablesName(@NotNull LineageGraph graph, @NotNull Set<String> own) {
        LineageNode table = own.size() == 1 ? graph.node(own.iterator().next()) : null;
        return table == null ? file.getName() : table.name();
    }

    private void showActivity(@NotNull RecordedActivity activity) {
        setLoading(activity.isBusy());
        if (activity == RecordedActivity.LOADING && recordedView == null) {
            stateBannerSlot.setBanner(null);
            showNothing("Loading the Knowledge Catalog lineage…");
        }
        if (activity == RecordedActivity.UPDATING) stateBannerSlot.setBanner(null);
    }

    private void showFailure(@NotNull DataplexLineageException.Kind kind, @Nullable String detail, boolean viewShown) {
        if (viewShown && recordedView != null) {
            stateBannerSlot.setBanner(DataplexStateBanner.updateFailed(detail, this::reloadRecorded));
            return;
        }
        clearDrawing();
        stateBannerSlot.setBanner(DataplexStateBanner.of(project, DataplexStateBanner.stateOf(kind),
                DataplexStateBanner.projectIdOf(lineageService, compiledGraph.get()), model.timeRange(),
                detail, this::reloadRecorded, () -> sourceSwitch.select(LineageSource.DATAFORM)));
    }

    private void showNothing(@NotNull String message) {
        model.setScopeIds(null);
        model.setDataplexView(null);
        model.setGraph(null);
        canvas.setPlaceholder(message, SHOW_DATAFORM, () -> sourceSwitch.select(LineageSource.DATAFORM));
    }

    private void setLoading(boolean value) {
        loading = value;
        loadingIcon.setVisible(value);
    }

    private @NotNull Set<String> ownActions() {
        return declared == null ? Set.of() : ownActions(declared);
    }

    private @NotNull Set<String> ownActions(@NotNull LineageGraph graph) {
        String path = file.getPath();
        Set<String> own = new LinkedHashSet<>();
        for (LineageNode node : graph.nodes()) {
            if (DataformPaths.pointsTo(path, node.fileName())) {
                own.add(node.id());
            }
        }
        return own;
    }

    private static @NotNull Set<String> computeScope(@NotNull LineageGraph graph, @NotNull Set<String> own) {
        Set<String> scope = new LinkedHashSet<>(own);
        for (String id : own) {
            scope.addAll(graph.predecessors(id));
            scope.addAll(graph.successors(id));
        }
        return scope;
    }

    private JComponent buildHeader() {
        sourceSwitch.setCompact(true);
        sourceSwitch.getAccessibleContext().setAccessibleName("Lineage source");
        loadingIcon.setVisible(false);
        loadingIcon.setToolTipText("Reading the lineage recorded by Knowledge Catalog");
        loadingIcon.setBorder(JBUI.Borders.emptyLeft(4));
        header.addLeading(sourceSwitch);
        header.addLeading(loadingIcon);
        header.setTrailing(Toolbars.horizontal("Dataform.FileLineage", this,
                LineageToolbarSupport.directionAction(model),
                Toolbars.withText(Actions.of("Open full lineage",
                        "Open the project-wide Dataform lineage view",
                        DataformIcons.LINEAGE, this::openProjectLineage))).getComponent());
        return header;
    }

    /**
     * Sets what Open full lineage does with the source of this view and the tables of the file.
     */
    void setProjectLineageOpener(@NotNull BiConsumer<LineageSource, List<String>> opener) {
        projectLineageOpener = opener;
    }

    private void openProjectLineage() {
        List<String> tables = new ArrayList<>();
        if (declared != null) {
            for (String id : ownActions(declared)) {
                LineageNode node = declared.node(id);
                if (node != null && !ASSERTION.equals(node.dataformType())) tables.add(id);
            }
        }
        projectLineageOpener.accept(model.source(), tables);
    }

    private void update() {
        boolean recorded = model.source() == LineageSource.DATAPLEX;
        boolean empty = model.graph().isEmpty() || model.visibleIds().isEmpty();
        cards.showEmpty(!recorded && empty);
        if (sourceSwitch.selected() != model.source()) sourceSwitch.select(model.source());
        header.setSummary((recorded ? "Direct recorded neighbours of " : "Direct dependencies of ") + file.getName());
    }
}
