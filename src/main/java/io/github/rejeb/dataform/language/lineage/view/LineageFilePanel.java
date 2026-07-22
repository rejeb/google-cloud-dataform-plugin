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
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.Density;
import io.github.rejeb.dataform.language.lineage.model.Direction;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Lineage view scoped to a single SQLX file: shows the actions defined in that file
 * together with their direct upstream and downstream actions only.
 *
 * <p>Reuses {@link GraphCanvas} and {@link LineageModel} from the project-wide lineage view;
 * the restriction is expressed through {@link LineageModel#setScopeIds}.</p>
 */
public final class LineageFilePanel extends JPanel {

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_BODY = "body";

    private final Project project;
    private final VirtualFile file;
    private final LineageModel model;
    private final GraphCanvas canvas;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    public LineageFilePanel(@NotNull Project project, @NotNull VirtualFile file) {
        super(new BorderLayout());
        this.project = project;
        this.file = file;
        this.model = new LineageModel(project);
        setOpaque(true);
        setBackground(UIUtil.getPanelBackground());

        canvas = new GraphCanvas(project, model);
        StatusBar statusBar = new StatusBar(model);
        canvas.setZoomListener(statusBar::setZoom);

        JPanel body = new JPanel(new BorderLayout());
        body.add(buildToolbar(), BorderLayout.NORTH);
        body.add(canvas, BorderLayout.CENTER);
        body.add(statusBar, BorderLayout.SOUTH);

        cards.add(buildEmptyCard(), CARD_EMPTY);
        cards.add(body, CARD_BODY);
        add(cards, BorderLayout.CENTER);

        update();
        model.addListener(m -> update());
    }

    /**
     * Applies a freshly extracted lineage graph and re-scopes the view to the actions
     * declared in the edited file plus their direct neighbours. Must be called on the EDT.
     */
    public void setLineage(@Nullable LineageGraph graph) {
        model.setScopeIds(null);
        model.setGraph(graph);
        model.setScopeIds(computeScope(model.graph()));
        canvas.fitToView();
    }

    private @NotNull Set<String> computeScope(@NotNull LineageGraph graph) {
        String path = file.getPath().replace("\\", "/");
        Set<String> own = new LinkedHashSet<>();
        for (LineageNode node : graph.nodes()) {
            String nodeFile = node.fileName();
            if (nodeFile != null && path.endsWith(nodeFile.replace("\\", "/"))) {
                own.add(node.id());
            }
        }
        Set<String> scope = new LinkedHashSet<>(own);
        for (String id : own) {
            scope.addAll(graph.predecessors(id));
            scope.addAll(graph.successors(id));
        }
        return scope;
    }

    private JComponent buildToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(toggle("Layout direction", "Toggle left-to-right / top-to-bottom",
                () -> model.direction() == Direction.TB ? LineageIcons.DIRECTION_TB : LineageIcons.DIRECTION_LR,
                () -> model.direction() == Direction.TB, model::toggleDirection));
        group.add(toggle("Density", "Toggle compact / comfortable node density",
                () -> model.density() == Density.COMPACT ? LineageIcons.DENSITY_COMPACT : LineageIcons.DENSITY_COMFORTABLE,
                () -> model.density() == Density.COMPACT, model::toggleDensity));
        group.add(action("Fit to view", "Fit the graph to the visible area",
                LineageIcons.FIT, canvas::fitToView));
        group.add(action("Open full lineage", "Open the project-wide Dataform lineage view",
                LineageIcons.OPEN_FULL, this::openProjectLineage));

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("DataformFileLineageToolbar", group, true);
        toolbar.setTargetComponent(this);

        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2)));
        title.setOpaque(false);
        JBLabel name = new JBLabel("Lineage", DataformIcons.LINEAGE, SwingConstants.LEADING);
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        title.add(name);
        JBLabel crumb = new JBLabel("direct dependencies of " + file.getName());
        crumb.setForeground(UIUtil.getLabelDisabledForeground());
        title.add(crumb);

        JPanel north = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        north.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 1, 0),
                JBUI.Borders.empty(2, 6)));
        north.add(title, BorderLayout.WEST);
        north.add(toolbar.getComponent(), BorderLayout.EAST);
        return north;
    }

    private void openProjectLineage() {
        FileEditorManager.getInstance(project)
                .openFile(LineageProjectVirtualFile.getInstance(), true);
    }

    private DumbAwareAction action(@NotNull String text, @NotNull String description,
                                   @NotNull Icon icon, @NotNull Runnable run) {
        return new DumbAwareAction(text, description, icon) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                run.run();
            }
        };
    }

    private ToggleAction toggle(@NotNull String text, @NotNull String description,
                                @NotNull Supplier<Icon> icon,
                                @NotNull BooleanSupplier state,
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

    private void update() {
        boolean empty = model.graph().isEmpty() || model.visibleIds().isEmpty();
        cardLayout.show(cards, empty ? CARD_EMPTY : CARD_BODY);
    }

    private JPanel buildEmptyCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UIUtil.getPanelBackground());
        JBLabel label = new JBLabel("No lineage for this file. Compile the Dataform project.",
                SwingConstants.CENTER);
        label.setForeground(JBColor.GRAY);
        panel.add(label);
        return panel;
    }
}
