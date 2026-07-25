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

import com.intellij.ui.EditorNotificationPanel;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageGraph;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Warning banner shown at the top of a lineage view when some table schemas could not be
 * resolved. Column lineage is skipped for those tables, so the graph may be missing columns
 * and edges. The banner hides itself as soon as every schema resolves.
 */
public final class LineageWarningBanner extends EditorNotificationPanel {

    private static final int MAX_LISTED_TABLES = 5;

    private final LineageModel model;
    private Set<String> dismissedFor = Set.of();

    public LineageWarningBanner(@NotNull LineageModel model) {
        super(Status.Warning);
        this.model = model;
        createActionLabel("Hide", () -> {
            dismissedFor = unresolvedTables();
            refresh();
        }, false);
        refresh();
        model.addListener(m -> refresh());
    }

    /**
     * Hiding the banner only silences the tables it was reporting: if a later extraction fails to
     * resolve a different table, the warning comes back.
     */
    private void refresh() {
        Set<String> unresolved = unresolvedTables();
        if (unresolved.isEmpty() || dismissedFor.containsAll(unresolved)) {
            setVisible(false);
            return;
        }
        setVisible(true);
        setText(message(unresolved));
        setToolTipText("<html>" + String.join("<br>", unresolved) + "</html>");
        revalidate();
        repaint();
    }

    private @NotNull Set<String> unresolvedTables() {
        ColumnLineageGraph columnGraph = model.columnGraph();
        return columnGraph != null ? columnGraph.unresolvedTables() : Set.of();
    }

    private @NotNull String message(@NotNull Set<String> unresolved) {
        List<String> shown = unresolved.stream().limit(MAX_LISTED_TABLES).collect(Collectors.toList());
        String tables = String.join(", ", shown);
        int hidden = unresolved.size() - shown.size();
        if (hidden > 0) tables += " and " + hidden + " more";
        return "Column lineage may be incomplete: the schema of " + unresolved.size()
                + (unresolved.size() == 1 ? " table" : " tables")
                + " could not be resolved, so some columns and edges are missing (" + tables + ").";
    }
}
