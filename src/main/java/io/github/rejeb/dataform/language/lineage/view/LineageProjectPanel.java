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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.service.LineageGraphService;
import org.jetbrains.annotations.NotNull;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;

/**
 * Project-wide lineage view, as in the UI prototype: a toolbar (filters toggle, search, view
 * toggles, fit, refresh and legend) over a three-column body (filters | graph canvas | details)
 * with a bottom status bar. The details column starts 300 pixels wide and is resized by dragging
 * its divider; it shows an empty state while nothing is selected and can be hidden from the
 * toolbar or its own header.
 */
public final class LineageProjectPanel extends JPanel {

    private static final int SEARCH_WIDTH = 280;
    private static final int DETAILS_WIDTH = 300;
    private static final int MIN_SIDE_WIDTH = 160;

    private final Project project;
    private final LineageModel model;
    private final GraphCanvas canvas;
    private final FiltersPanel filtersPanel;
    private final SearchTextField searchField = new SearchTextField(false);
    private final JBLabel foundLabel = new JBLabel();
    private final LineageCards cards;
    private final DetailsPanel detailsPanel;
    private final AtomicLong refreshGeneration = new AtomicLong();

    public LineageProjectPanel(@NotNull Project project, @NotNull LineageModel model) {
        super(new BorderLayout());
        this.project = project;
        this.model = model;
        setOpaque(true);
        setBackground(UIUtil.getPanelBackground());

        canvas = new GraphCanvas(project, model);
        filtersPanel = new FiltersPanel(model);
        detailsPanel = new DetailsPanel(project, model);
        detailsPanel.setHideHandler(() -> setDetailsShown(false));
        StatusBar statusBar = new StatusBar(model);
        canvas.setZoomListener(statusBar::setZoom);
        canvas.setSelectionHandler(() -> setDetailsShown(true));

        ThreeComponentsSplitter graphArea = new ThreeComponentsSplitter(false, true);
        graphArea.setInnerComponent(new CanvasOverlayPanel(canvas, new LineageFocusBanner(model).view(),
                LineageToolbarSupport.zoomControls(canvas)));
        graphArea.setLastComponent(detailsPanel);
        graphArea.setMinSize(JBUIScale.scale(MIN_SIDE_WIDTH));
        graphArea.setLastSize(JBUIScale.scale(DETAILS_WIDTH));

        cards = new LineageCards(model, buildToolbar(), filtersPanel, graphArea, statusBar,
                "Compile the Dataform project to see the lineage.");
        add(cards, BorderLayout.CENTER);

        installSearchShortcut();
        update();
        model.addListener(m -> update());
    }

    /**
     * The search field of the toolbar.
     */
    @NotNull SearchTextField searchField() {
        return searchField;
    }

    /**
     * The "N found" label next to the search field, hidden while the search is empty.
     */
    @NotNull JBLabel foundLabel() {
        return foundLabel;
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
        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                model.setSearchQuery(searchField.getText());
            }
        });
        searchField.setPreferredSize(new Dimension(JBUIScale.scale(SEARCH_WIDTH), searchField.getPreferredSize().height));
        foundLabel.setFont(JBFont.medium());
        foundLabel.setForeground(UIUtil.getContextHelpForeground());
        foundLabel.setBorder(JBUI.Borders.empty(0, 2, 0, 4));

        DefaultActionGroup group = new DefaultActionGroup();
        group.add(LineageToolbarSupport.toggle("Show filters", "Show or hide the filters",
                () -> LineageIcons.FILTERS, filtersPanel::isVisible,
                () -> filtersPanel.setVisible(!filtersPanel.isVisible())));
        group.add(LineageToolbarSupport.component(searchField));
        group.add(LineageToolbarSupport.component(foundLabel));
        group.addSeparator();
        group.add(LineageToolbarSupport.directionAction(model));
        group.add(LineageToolbarSupport.densityToggle(model));
        group.add(LineageToolbarSupport.toggle("Show minimap", "Show or hide the minimap",
                () -> LineageIcons.MINIMAP, model::minimapVisible, model::toggleMinimap));
        group.add(LineageToolbarSupport.toggle("Show details", "Show or hide the details of the selection",
                () -> LineageIcons.DETAILS, detailsPanel::isVisible,
                () -> setDetailsShown(!detailsPanel.isVisible())));
        group.addSeparator();
        group.add(LineageToolbarSupport.action("Fit to view", "Fit the graph to the visible area",
                LineageIcons.FIT, canvas::fitToView));
        group.add(LineageToolbarSupport.action("Recompile and refresh", "Recompile the project and refresh the lineage",
                DataformIcons.REFRESH, () -> refresh(true)));
        group.add(LineageToolbarSupport.legendAction(() -> LineageLegend.orderedTypes(model.typeCounts().keySet())));

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("DataformLineageToolbar", group, true);
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

    private void setDetailsShown(boolean shown) {
        if (detailsPanel.isVisible() == shown) return;
        detailsPanel.setVisible(shown);
        revalidate();
        repaint();
    }

    private void update() {
        cards.showEmpty(model.graph().isEmpty());
        String query = model.searchQuery();
        if (!query.equals(searchField.getText())) searchField.setText(query);
        boolean searching = !query.isBlank();
        foundLabel.setVisible(searching);
        foundLabel.setText(searching ? model.searchMatches().size() + " found" : "");
    }
}
