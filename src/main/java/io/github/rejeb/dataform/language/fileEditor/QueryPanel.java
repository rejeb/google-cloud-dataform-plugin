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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import io.github.rejeb.dataform.language.schema.sql.DataformSchemaEvent;
import io.github.rejeb.dataform.language.schema.sql.DryRunErrorRegistry;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

public class QueryPanel extends SectionsPreviewPanel {

    private final Project project;
    private final FileType fileType;
    private final QueryDryRunErrorBanner errorBanner;
    private final MessageBusConnection connection;
    private final List<TableQuerySection> sections = new ArrayList<>();

    public QueryPanel(Project project, FileType fileType) {
        this.project  = project;
        this.fileType = fileType;

        errorBanner = new QueryDryRunErrorBanner();
        add(errorBanner, BorderLayout.NORTH);

        connection = project.getMessageBus().connect();
        connection.subscribe(DataformSchemaEvent.TOPIC,
                (DataformSchemaEvent) this::refreshErrorsLater);
    }

    /**
     * Reports the dry-run errors of the displayed actions. The extraction publishes its results
     * from a background thread, so the banner is always updated on the EDT.
     */
    private void refreshErrorsLater() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            refreshErrors();
        }, ModalityState.nonModal());
    }

    private void refreshErrors() {
        List<String> displayedActions = sections.stream()
                .map(s -> s.getQuery().tableName())
                .toList();
        errorBanner.update(QueryDryRunErrorBanner.errorsOf(
                displayedActions, DryRunErrorRegistry.getInstance(project).getErrors()));
    }

    public void setContent(List<FormattedCompiledQuery> queries) {
        sections.forEach(TableQuerySection::dispose);
        sections.clear();
        sectionsPanel.removeAll();
        for (FormattedCompiledQuery q : queries != null ? queries : List.<FormattedCompiledQuery>of()) {
            TableQuerySection section = new TableQuerySection(q, fileType, project);
            sections.add(section);
            sectionsPanel.add(section);
        }
        refreshErrors();
        sectionsPanel.revalidate();
        sectionsPanel.repaint();
    }

    public EditorEx getEditor() {
        if (sections.isEmpty()) return null;
        return sections.get(0).getQuerySection().getEditor();
    }

    public void dispose() {
        connection.disconnect();
        sections.forEach(TableQuerySection::dispose);
        sections.clear();
    }

    /**
     * Returns true if at least one compiled query with a non-null query body is available.
     */
    public boolean hasQuery() {
        return sections.stream().anyMatch(s -> s.getQuerySection().hasContent());
    }

    /**
     * Returns the current list of compiled queries.
     */
    public List<FormattedCompiledQuery> getCompiledQueries() {
        return sections.stream()
                .map(TableQuerySection::getQuery)
                .toList();
    }
}
