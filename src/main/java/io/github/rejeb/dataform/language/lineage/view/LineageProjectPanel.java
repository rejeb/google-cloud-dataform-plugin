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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.lineage.column.BigQuerySelectAnalyzer;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageExtractor;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageExtractorImpl;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageGraph;
import io.github.rejeb.dataform.language.lineage.extractor.LineageExtractorImpl;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.model.Density;
import io.github.rejeb.dataform.language.lineage.model.Direction;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import org.jetbrains.annotations.NotNull;

import io.github.rejeb.dataform.language.compilation.model.Target;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Project-wide lineage view: a toolbar (search + view toggles) over a three-column body
 * (filters | graph canvas | details) with a bottom status bar. The details column is
 * revealed only when a node is selected.
 */
public final class LineageProjectPanel extends JPanel {

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_BODY = "body";

    private final Project project;
    private final LineageModel model;
    private final GraphCanvas canvas;
    private final FiltersPanel filtersPanel;
    private final SearchTextField searchField = new SearchTextField();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final DetailsPanel detailsPanel;
    private final OnePixelSplitter detailsSplitter;

    private boolean detailsCollapsed;
    private String lastSelectionKey = "";

    private volatile CompiledGraph cachedCompiled;
    private volatile long cachedSchemaStamp = Long.MIN_VALUE;
    private volatile LineageGraph cachedTableGraph;
    private volatile ColumnLineageGraph cachedColumnGraph;

    public LineageProjectPanel(@NotNull Project project, @NotNull LineageModel model) {
        super(new BorderLayout());
        this.project = project;
        this.model = model;
        setOpaque(true);
        setBackground(UIUtil.getPanelBackground());

        canvas = new GraphCanvas(project, model);
        filtersPanel = new FiltersPanel(model, canvas::fitToView);
        detailsPanel = new DetailsPanel(project, model);
        detailsPanel.setReduceHandler(() -> {
            detailsCollapsed = true;
            update();
        });
        StatusBar statusBar = new StatusBar(model);
        canvas.setZoomListener(statusBar::setZoom);

        detailsSplitter = new OnePixelSplitter(false, 0.74f);
        detailsSplitter.setFirstComponent(canvas);
        detailsSplitter.setSecondComponent(null);

        JPanel header = new JPanel(new BorderLayout());
        header.add(new LineageWarningBanner(model), BorderLayout.NORTH);
        header.add(buildToolbar(), BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout());
        body.add(header, BorderLayout.NORTH);
        body.add(filtersPanel, BorderLayout.WEST);
        body.add(detailsSplitter, BorderLayout.CENTER);
        body.add(statusBar, BorderLayout.SOUTH);

        cards.add(buildEmptyCard(), CARD_EMPTY);
        cards.add(body, CARD_BODY);
        add(cards, BorderLayout.CENTER);

        installSearchShortcut();
        update();
        model.addListener(m -> update());
    }

    /**
     * Recompiles (when {@code force}) or reads the current compiled graph, extracts the
     * lineage on a pooled thread, and applies it to the model on the EDT.
     */
    public void refresh(boolean force) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            DataformCompilationService svc = DataformCompilationService.getInstance(project);
            CompiledGraph compiled = force ? svc.compile(true) : svc.getCompiledGraph();

            long schemaStamp = DataformTableSchemaService.getInstance(project).getModificationCount();
            LineageGraph graph = null;
            ColumnLineageGraph columnGraph = null;
            if (compiled != null && compiled == cachedCompiled && schemaStamp == cachedSchemaStamp
                    && cachedColumnGraph != null) {
                graph = cachedTableGraph;
                columnGraph = cachedColumnGraph;
            } else if (compiled != null) {
                CompiledGraph finalCompiled = compiled;
                java.util.concurrent.CompletableFuture<LineageGraph> tableFuture =
                        java.util.concurrent.CompletableFuture.supplyAsync(
                                () -> new LineageExtractorImpl().extract(finalCompiled),
                                com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService());
                columnGraph = computeColumnGraph(compiled);
                graph = tableFuture.join();

                cachedCompiled = compiled;
                cachedSchemaStamp = schemaStamp;
                cachedTableGraph = graph;
                cachedColumnGraph = columnGraph;
            }

            LineageGraph finalGraph = graph;
            ColumnLineageGraph finalColumnGraph = columnGraph;
            ApplicationManager.getApplication().invokeLater(() -> {
                model.setGraph(finalGraph);
                model.setColumnGraph(finalColumnGraph);
            }, ModalityState.nonModal());
        });
    }

    /**
     * Builds the column graph from the schemas of the actions present in the compiled graph.
     * Cached schemas of actions that are no longer part of the graph are ignored, so a stale
     * entry cannot contribute columns to the lineage.
     */
    private ColumnLineageGraph computeColumnGraph(@NotNull CompiledGraph compiled) {
        Set<String> actionNames = actionFullNames(compiled);
        Map<String, List<ColumnInfo>> schemas = new LinkedHashMap<>();
        DataformTableSchemaService.getInstance(project).getAllTables()
                .forEach((fqn, table) -> {
                    if (actionNames.contains(fqn)) schemas.put(fqn, table.getColumns());
                });
        ColumnLineageExtractor extractor =
                new ColumnLineageExtractorImpl(new BigQuerySelectAnalyzer(project));
        return extractor.extract(compiled, schemas);
    }

    private @NotNull Set<String> actionFullNames(@NotNull CompiledGraph compiled) {
        Set<String> names = new LinkedHashSet<>();
        compiled.getTables().forEach(t -> addFullName(names, t.getTarget()));
        compiled.getAssertions().forEach(a -> addFullName(names, a.getTarget()));
        compiled.getOperations().forEach(o -> addFullName(names, o.getTarget()));
        compiled.getDeclarations().forEach(d -> addFullName(names, d.getTarget()));
        return names;
    }

    private void addFullName(@NotNull Set<String> names, Target target) {
        if (target != null && target.getFullName() != null) names.add(target.getFullName());
    }

    private JComponent buildToolbar() {
        searchField.getTextEditor().getEmptyText().setText("Search tables, tags…   " + fSearchHint());
        searchField.addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { model.setSearchQuery(searchField.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { model.setSearchQuery(searchField.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { model.setSearchQuery(searchField.getText()); }
        });

        DefaultActionGroup group = new DefaultActionGroup();
        group.add(toggle("Toggle filters", "Show or hide the filters sidebar",
                () -> LineageIcons.FILTERS, filtersPanel::isVisible,
                () -> filtersPanel.setVisible(!filtersPanel.isVisible())));
        group.add(toggle("Layout direction", "Toggle left-to-right / top-to-bottom",
                () -> model.direction() == Direction.TB ? LineageIcons.DIRECTION_TB : LineageIcons.DIRECTION_LR,
                () -> model.direction() == Direction.TB, model::toggleDirection));
        group.add(toggle("Density", "Toggle compact / comfortable node density",
                () -> model.density() == Density.COMPACT ? LineageIcons.DENSITY_COMPACT : LineageIcons.DENSITY_COMFORTABLE,
                () -> model.density() == Density.COMPACT, model::toggleDensity));
        group.add(toggle("Minimap", "Show or hide the minimap",
                () -> LineageIcons.MINIMAP, model::minimapVisible, model::toggleMinimap));
        group.add(toggle("Details", "Show or hide the details panel for the current selection",
                () -> AllIcons.Actions.PreviewDetails,
                () -> detailsSplitter.getSecondComponent() != null, this::toggleDetails));
        group.add(action("Re-layout", "Recompile and refresh lineage",
                LineageIcons.RELAYOUT, () -> refresh(true)));

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("DataformLineageToolbar", group, true);
        toolbar.setTargetComponent(this);

        JPanel title = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, JBUIScale.scale(6), JBUIScale.scale(2)));
        title.setOpaque(false);
        JBLabel name = new JBLabel("Lineage", DataformIcons.LINEAGE, SwingConstants.LEADING);
        name.setFont(name.getFont().deriveFont(java.awt.Font.BOLD));
        title.add(name);
        JBLabel crumb = new JBLabel(project.getName());
        crumb.setForeground(UIUtil.getLabelDisabledForeground());
        title.add(crumb);

        JPanel north = new JPanel(new BorderLayout(JBUIScale.scale(8), 0));
        north.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 1, 0),
                JBUI.Borders.empty(2, 6)));
        north.add(title, BorderLayout.WEST);
        north.add(searchField, BorderLayout.CENTER);
        north.add(toolbar.getComponent(), BorderLayout.EAST);
        return north;
    }

    private static String fSearchHint() {
        return java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() == java.awt.event.InputEvent.META_DOWN_MASK
                ? "⌘F" : "Ctrl+F";
    }

    private DumbAwareAction action(@NotNull String text, @NotNull String description,
                                   javax.swing.Icon icon, @NotNull Runnable run) {
        return new DumbAwareAction(text, description, icon) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                run.run();
            }
        };
    }

    private ToggleAction toggle(@NotNull String text, @NotNull String description,
                                @NotNull java.util.function.Supplier<javax.swing.Icon> icon,
                                @NotNull java.util.function.BooleanSupplier state,
                                @NotNull Runnable toggle) {
        return new ToggleAction(text, description, icon.get()) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return state.getAsBoolean();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean requested) {
                if (requested != state.getAsBoolean()) toggle.run();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                super.update(e);
                e.getPresentation().setIcon(icon.get());
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
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

    /**
     * Shows or hides the details panel for the current selection. Does nothing when nothing is
     * selected, since the panel only has content for a selected node or column.
     */
    private void toggleDetails() {
        if (model.selectedId() == null) return;
        detailsCollapsed = !detailsCollapsed;
        update();
    }

    private void update() {
        cardLayout.show(cards, model.graph().isEmpty() ? CARD_EMPTY : CARD_BODY);
        boolean hasSelection = model.selectedId() != null;
        String selectionKey = String.valueOf(model.selectedId());
        if (hasSelection && !selectionKey.equals(lastSelectionKey)) {
            detailsCollapsed = false;
        }
        lastSelectionKey = selectionKey;
        boolean showDetails = hasSelection && !detailsCollapsed && !model.graph().isEmpty();
        detailsSplitter.setSecondComponent(showDetails ? detailsPanel : null);
    }

    private JPanel buildEmptyCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UIUtil.getPanelBackground());
        JBLabel label = new JBLabel("Compile the Dataform project to see the lineage.", SwingConstants.CENTER);
        label.setForeground(JBColor.GRAY);
        panel.add(label);
        return panel;
    }
}
