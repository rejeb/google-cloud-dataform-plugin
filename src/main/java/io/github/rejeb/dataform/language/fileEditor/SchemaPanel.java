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
package io.github.rejeb.dataform.language.fileEditor;

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.DryRunErrorRegistry;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import io.github.rejeb.dataform.language.ui.PreviewHeader;
import io.github.rejeb.dataform.language.ui.UiText;
import org.jetbrains.annotations.NotNull;

import java.awt.BorderLayout;
import java.util.List;
import java.util.Map;

/**
 * The schema tab of the SQLX preview: one section per action of the file that has a table, with
 * the columns BigQuery answered its dry-run with.
 */
public class SchemaPanel extends SectionsPreviewPanel {

    private static final String SOURCE = "Schemas from BigQuery dry run";
    private static final String ASSERTION = "assertion";

    private final Project project;
    private final Runnable refresh;
    private final PreviewHeader header = new PreviewHeader();

    public SchemaPanel(Project project) {
        this(project, () -> {
        });
    }

    /**
     * @param refresh refreshes the schemas of the preview, offered by every section
     */
    public SchemaPanel(Project project, Runnable refresh) {
        this.project = project;
        this.refresh = refresh;
        showCompilation(-1);
        add(header, BorderLayout.NORTH);
    }

    /**
     * Shows one section per action that has a table: tables and views under their full name,
     * assertions under their name.
     */
    public void setContent(List<GraphTarget> tables) {
        sectionsPanel.removeAll();
        if (tables != null && !tables.isEmpty()) {
            Map<String, DataformDasTable> allTables = DataformTableSchemaService.getInstance(project).getAllTables();
            DryRunErrorRegistry errors = DryRunErrorRegistry.getInstance(project);
            for (GraphTarget target : tables) {
                if (!target.hasTable()) continue;
                DataformDasTable table = target.fullName() != null ? allTables.get(target.fullName()) : null;
                sectionsPanel.add(new TableSchemaSection(titleOf(target), target.type(),
                        table != null ? table.getColumns() : null,
                        target.fullName() != null ? errors.getError(target.fullName()) : null, refresh));
            }
        }
        sectionsPanel.revalidate();
        sectionsPanel.repaint();
    }

    /**
     * Shows in the header when the displayed graph was compiled.
     *
     * @param compiledAt epoch milliseconds, or a negative value when unknown
     */
    public void showCompilation(long compiledAt) {
        header.setSummary(compiledAt > 0 ? UiText.dot(SOURCE, PreviewHeader.clock(compiledAt)) : SOURCE);
    }

    @NotNull
    PreviewHeader header() {
        return header;
    }

    @NotNull
    private static String titleOf(@NotNull GraphTarget target) {
        if (ASSERTION.equals(target.type()) || target.fullName() == null) return target.name();
        return target.fullName();
    }
}
