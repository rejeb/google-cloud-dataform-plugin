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
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.lineage.extractor.LineageExtractorImpl;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.model.Density;
import io.github.rejeb.dataform.language.lineage.model.Direction;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;

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

    public LineageProjectPanel(@NotNull Project project, @NotNull LineageModel model) {
        super(new BorderLayout());
        this.project = project;
        this.model = model;
        setOpaque(true);
        setBackground(UIUtil.getPanelBackground());

        canvas = new GraphCanvas(project, model);
        filtersPanel = new FiltersPanel(model, canvas::fitToView);
        detailsPanel = new DetailsPanel(project, model);
        StatusBar statusBar = new StatusBar(model);
        canvas.setZoomListener(statusBar::setZoom);

        detailsSplitter = new OnePixelSplitter(false, 0.74f);
        detailsSplitter.setFirstComponent(canvas);
        detailsSplitter.setSecondComponent(null);

        JPanel body = new JPanel(new BorderLayout());
        body.add(buildToolbar(), BorderLayout.NORTH);
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
            LineageGraph graph = compiled != null ? new LineageExtractorImpl().extract(compiled) : null;
            ApplicationManager.getApplication().invokeLater(
                    () -> model.setGraph(graph), ModalityState.nonModal());
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
        group.add(action("Re-layout", "Recompile and refresh lineage",
                LineageIcons.RELAYOUT, () -> refresh(true)));

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("DataformLineageToolbar", group, true);
        toolbar.setTargetComponent(this);

        JPanel title = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2)));
        title.setOpaque(false);
        JBLabel name = new JBLabel("Lineage", DataformIcons.LINEAGE, SwingConstants.LEADING);
        name.setFont(name.getFont().deriveFont(java.awt.Font.BOLD));
        title.add(name);
        JBLabel crumb = new JBLabel(project.getName());
        crumb.setForeground(UIUtil.getLabelDisabledForeground());
        title.add(crumb);

        JPanel north = new JPanel(new BorderLayout(JBUI.scale(8), 0));
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

    private void update() {
        cardLayout.show(cards, model.graph().isEmpty() ? CARD_EMPTY : CARD_BODY);
        boolean showDetails = model.selectedId() != null && !model.graph().isEmpty();
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
