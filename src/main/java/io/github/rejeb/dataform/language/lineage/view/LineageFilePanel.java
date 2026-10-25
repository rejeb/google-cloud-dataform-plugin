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
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.BorderLayout;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Lineage view scoped to a single SQLX file: shows the actions defined in that file
 * together with their direct upstream and downstream actions only.
 *
 * <p>Reuses {@link GraphCanvas} and {@link LineageModel} from the project-wide lineage view;
 * the restriction is expressed through {@link LineageModel#setScopeIds}.</p>
 */
public final class LineageFilePanel extends JPanel {

    private final Project project;
    private final VirtualFile file;
    private final LineageModel model;
    private final GraphCanvas canvas;
    private final LineageCards cards;

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

        cards = new LineageCards(model, buildToolbar(), null, canvas, statusBar,
                "No lineage for this file. Compile the Dataform project.");
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
        canvas.centerOnView();
    }

    private @NotNull Set<String> computeScope(@NotNull LineageGraph graph) {
        String path = file.getPath();
        Set<String> own = new LinkedHashSet<>();
        for (LineageNode node : graph.nodes()) {
            if (DataformPaths.pointsTo(path, node.fileName())) {
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
        group.add(LineageToolbarSupport.directionToggle(model));
        group.add(LineageToolbarSupport.densityToggle(model));
        group.add(LineageToolbarSupport.action("Fit to view", "Fit the graph to the visible area",
                LineageIcons.FIT, canvas::fitToView));
        group.add(LineageToolbarSupport.action("Open full lineage", "Open the project-wide Dataform lineage view",
                LineageIcons.OPEN_FULL, this::openProjectLineage));

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("DataformFileLineageToolbar", group, true);
        toolbar.setTargetComponent(this);

        return LineageToolbarSupport.header("direct dependencies of " + file.getName(), null, toolbar);
    }

    private void openProjectLineage() {
        FileEditorManager.getInstance(project)
                .openFile(LineageProjectVirtualFile.getInstance(), true);
    }

    private void update() {
        boolean empty = model.graph().isEmpty() || model.visibleIds().isEmpty();
        cards.showEmpty(empty);
    }
}
