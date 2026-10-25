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
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.service.LineageGraphService;
import org.jetbrains.annotations.NotNull;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Project-wide lineage view: a toolbar (search + view toggles) over a three-column body
 * (filters | graph canvas | details) with a bottom status bar. The details column is
 * revealed only when a node is selected.
 */
public final class LineageProjectPanel extends JPanel {

    private final Project project;
    private final LineageModel model;
    private final GraphCanvas canvas;
    private final FiltersPanel filtersPanel;
    private final SearchTextField searchField = new SearchTextField();
    private final LineageCards cards;
    private final DetailsPanel detailsPanel;
    private final OnePixelSplitter detailsSplitter;
    private final AtomicLong refreshGeneration = new AtomicLong();

    private boolean detailsCollapsed;
    private String lastSelectionKey = "";

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

        cards = new LineageCards(model, buildToolbar(), filtersPanel, detailsSplitter, statusBar,
                "Compile the Dataform project to see the lineage.");
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
        long generation = refreshGeneration.incrementAndGet();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            DataformCompilationService svc = DataformCompilationService.getInstance(project);
            CompiledGraph compiled = force ? svc.compile(true) : svc.getCompiledGraph();

            LineageGraphService.Graphs graphs = compiled == null
                    ? new LineageGraphService.Graphs(null, null)
                    : LineageGraphService.getInstance(project).graphs(compiled);

            ApplicationManager.getApplication().invokeLater(() -> {
                if (generation != refreshGeneration.get()) return;
                model.setGraph(graphs.tableGraph());
                model.setColumnGraph(graphs.columnGraph());
            }, ModalityState.nonModal());
        });
    }

    private JComponent buildToolbar() {
        searchField.getTextEditor().getEmptyText().setText("Search tables, tags…   " + fSearchHint());
        searchField.addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { model.setSearchQuery(searchField.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { model.setSearchQuery(searchField.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { model.setSearchQuery(searchField.getText()); }
        });

        DefaultActionGroup group = new DefaultActionGroup();
        group.add(LineageToolbarSupport.toggle("Toggle filters", "Show or hide the filters sidebar",
                () -> LineageIcons.FILTERS, filtersPanel::isVisible,
                () -> filtersPanel.setVisible(!filtersPanel.isVisible())));
        group.add(LineageToolbarSupport.directionToggle(model));
        group.add(LineageToolbarSupport.densityToggle(model));
        group.add(LineageToolbarSupport.toggle("Minimap", "Show or hide the minimap",
                () -> LineageIcons.MINIMAP, model::minimapVisible, model::toggleMinimap));
        group.add(LineageToolbarSupport.toggle("Details", "Show or hide the details panel for the current selection",
                () -> AllIcons.Actions.PreviewDetails,
                () -> detailsSplitter.getSecondComponent() != null, this::toggleDetails));
        group.add(LineageToolbarSupport.action("Re-layout", "Recompile and refresh lineage",
                LineageIcons.RELAYOUT, () -> refresh(true)));

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("DataformLineageToolbar", group, true);
        toolbar.setTargetComponent(this);

        return LineageToolbarSupport.header(project.getName(), searchField, toolbar);
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
        cards.showEmpty(model.graph().isEmpty());
        boolean hasSelection = model.selectedId() != null;
        String selectionKey = String.valueOf(model.selectedId());
        if (hasSelection && !selectionKey.equals(lastSelectionKey)) {
            detailsCollapsed = false;
        }
        lastSelectionKey = selectionKey;
        boolean showDetails = hasSelection && !detailsCollapsed && !model.graph().isEmpty();
        detailsSplitter.setSecondComponent(showDetails ? detailsPanel : null);
    }
}
