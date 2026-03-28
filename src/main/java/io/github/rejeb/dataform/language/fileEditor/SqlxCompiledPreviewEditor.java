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

import com.intellij.execution.services.ServiceEventListener;
import com.intellij.execution.services.ServiceViewManager;
import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.sql.SqlFileType;
import com.intellij.ui.JBColor;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.DatabaseIcons;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledQuery;
import io.github.rejeb.dataform.language.fileEditor.lineage.LineageGraph;
import io.github.rejeb.dataform.language.fileEditor.lineage.LineageGraphHelper;
import io.github.rejeb.dataform.language.fileEditor.lineage.LineagePanel;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryExecutionService;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryJobResult;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryResultsRegistry;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.serviceview.DataformQueryContributor;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.serviceview.QueryResultNode;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.List;

public class SqlxCompiledPreviewEditor implements FileEditor {

    public enum View {LINEAGE, QUERY, SCHEMA}

    private final JPanel mainPanel = new JPanel(new CardLayout());
    private final SchemaPanel schemaPanel;
    private final LineagePanel lineagePanel;
    private final QueryPanel queryPanel;
    private final Project project;
    private final VirtualFile file;
    private long myLastCompiledStamp = -1;

    public SqlxCompiledPreviewEditor(@NotNull Project project, VirtualFile file) {
        this.project = project;
        this.file = file;
        schemaPanel = new SchemaPanel(project);
        lineagePanel = new LineagePanel(project);
        queryPanel = new QueryPanel(project, resolveFileType(file));

        mainPanel.setOpaque(true);
        mainPanel.setBackground(UIUtil.getPanelBackground());
        mainPanel.add(withHeader("Lineage", IconUtil.colorize(AllIcons.CodeWithMe.CwmShared, JBColor.BLUE),
                lineagePanel), View.LINEAGE.name());
        mainPanel.add(withHeader("Query", DatabaseIcons.Sql, queryPanel), View.QUERY.name());
        mainPanel.add(withHeader("Schema", AllIcons.Nodes.DataTables, schemaPanel), View.SCHEMA.name());

        showPanel(View.LINEAGE);
        updateCompiledSql();
    }

    @NotNull
    public Project getProject() {
        return project;
    }

    public void updateCompiledSql() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Dataform: compiling", true) {
            private List<FormattedCompiledQuery> compiledQueries;
            private List<LineageGraph> lineageGraphs;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Compiling " + file.getName() + "...");
                DataformCompilationService svc = project.getService(DataformCompilationService.class);
                CompiledGraph graph = svc.compile(false);
                if (graph != null) {
                    String path = file.getCanonicalPath();
                    List<CompiledQuery> rawQueries = graph.findCompiledQueryByFileName(path);
                    compiledQueries = WriteCommandAction.runWriteCommandAction(project,
                            (ThrowableComputable<List<FormattedCompiledQuery>, RuntimeException>) () ->
                                    rawQueries.stream()
                                            .map(q -> toFormatted(q, project))
                                            .toList()
                    );
                    lineageGraphs = LineageGraphHelper.buildGraph(graph, path);
                }
                if (graph != null && (graph.getGraphErrors() == null
                        || graph.getGraphErrors().getCompilationErrors().isEmpty())) {
                    project.getService(DataformTableSchemaService.class).refreshAsync(graph, false);
                }
                indicator.checkCanceled();
            }

            @Override
            public void onSuccess() {
                ApplicationManager.getApplication().invokeLater(() -> {
                    schemaPanel.setContent(lineageGraphs);
                    queryPanel.setContent(compiledQueries);
                    lineagePanel.setData(lineageGraphs);
                    mainPanel.revalidate();
                }, ModalityState.nonModal());
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    EditorEx editor = queryPanel.getEditor();
                    if (editor != null) {
                        WriteCommandAction.runWriteCommandAction(project, () ->
                                editor.getDocument().setText(
                                        "-- Compilation error:\n-- " + error.getMessage())
                        );
                    }
                }, ModalityState.nonModal());
            }
        });
    }

    private FileType resolveFileType(VirtualFile file) {
        if ("js".equals(file.getExtension())) {
            FileType t = FileTypeManager.getInstance().getFileTypeByExtension("js");
            if (!(t instanceof UnknownFileType)) return t;
        }
        return SqlFileType.INSTANCE;
    }

    @Override
    public @NotNull JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public @NotNull String getName() {
        return "Dataform Actions Panel";
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        EditorEx editor = queryPanel.getEditor();
        return editor != null ? editor.getComponent() : null;
    }

    @Override
    public void setState(@NotNull FileEditorState s) {
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener l) {
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener l) {
    }

    @Override
    public void dispose() {
        queryPanel.dispose();
    }

    @Override
    public @Nullable <T> T getUserData(@NotNull Key<T> key) {
        return null;
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @org.jspecify.annotations.Nullable T t) {
    }

    @Override
    public void selectNotify() {
        long stamp = file.getTimeStamp();
        if (stamp > myLastCompiledStamp) {
            myLastCompiledStamp = stamp;
            updateCompiledSql();
        }
    }

    public boolean hasQuery() {
        return queryPanel.hasQuery();
    }

    public void executeQuery(@NotNull AnActionEvent e) {
        List<FormattedCompiledQuery> queries = queryPanel.getCompiledQueries();
        if (queries == null || queries.isEmpty()) return;

        if (queries.size() == 1) {
            // Cas simple : une seule table, on exécute directement
            runQueries(List.of(queries.getFirst()));
        } else {
            // Plusieurs tables : popup bulle ancrée sur le bouton
            List<String> tableNames = queries.stream()
                    .map(FormattedCompiledQuery::tableName)
                    .toList();

            JBPopup popup = JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(tableNames)
                    .setTitle("Select Query to Execute")
                    .setMovable(false)
                    .setResizable(false)
                    .setRequestFocus(true)
                    .setItemChosenCallback(tableName -> queries.stream()
                            .filter(q -> q.tableName().equals(tableName))
                            .findFirst()
                            .ifPresent(q -> runQueries(List.of(q))))
                    .createPopup();

            Component sourceComponent = e.getInputEvent() != null
                    ? (Component) e.getInputEvent().getSource()
                    : getComponent();
            popup.showUnderneathOf(sourceComponent);
        }
    }

    private void runQueries(@NotNull List<FormattedCompiledQuery> toExecute) {
        DataformRepositoryConfig config = GcpRepositorySettings.getInstance(project).getActiveConfig();
        if (config == null) {
            Notifications.Bus.notify(new Notification(
                    "Dataform.Notifications",
                    "BigQuery execution",
                    "No GCP project configured.",
                    NotificationType.WARNING
            ), project);
            return;
        }

        String projectId = config.projectId();
        QueryResultsRegistry registry = QueryResultsRegistry.getInstance(project);

        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Dataform: executing queries", true) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        BigQueryExecutionService svc = BigQueryExecutionService.getInstance(project);
                        for (FormattedCompiledQuery q : toExecute) {
                            if (q.query() == null || q.query().isBlank()) continue;
                            indicator.setText("Executing " + q.tableName() + "...");

                            BigQueryJobResult result = svc.execute(q.query(), projectId, q.tableName());
                            registry.put(result);
                            ServiceEventListener.ServiceEvent resetEvent =
                                    ServiceEventListener.ServiceEvent.createResetEvent(
                                            DataformQueryContributor.class);
                            project.getMessageBus()
                                    .syncPublisher(ServiceEventListener.TOPIC)
                                    .handle(resetEvent);

                            ServiceViewManager.getInstance(project)
                                    .select(new QueryResultNode(result),
                                            DataformQueryContributor.class,
                                            true,
                                            true);
                        }
                    }
                }
        );
    }


    private View activeView = View.LINEAGE;

    public void showPanel(@NotNull View view) {
        this.activeView = view;
        CardLayout cl = (CardLayout) mainPanel.getLayout();
        cl.show(mainPanel, view.name());
    }

    public View getActiveView() {
        return activeView;
    }

    private static FormattedCompiledQuery toFormatted(CompiledQuery q, Project project) {
        List<String> preOps = q.preOps().stream()
                .map(s -> Utils.formatSql(project, s)).toList();
        List<String> postOps = q.postOps().stream()
                .map(s -> Utils.formatSql(project, s)).toList();
        String query = q.query() != null ? Utils.formatSql(project, q.query()) : null;
        String errors = q.compilationErrors() != null && !q.compilationErrors().isEmpty()
                ? String.join("\n", q.compilationErrors()) : null;
        return new FormattedCompiledQuery(
                q.tableName(),
                preOps.isEmpty() ? null : String.join("\n", preOps),
                query,
                postOps.isEmpty() ? null : String.join("\n", postOps),
                errors
        );
    }

    private static JPanel withHeader(@NotNull String title, @NotNull Icon icon, @NotNull JComponent content) {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(UIUtil.getPanelBackground());
        header.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 1, 0),
                JBUI.Borders.empty(4, 8)
        ));

        JLabel label = new JLabel(title, icon, SwingConstants.LEFT);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        header.add(label, BorderLayout.WEST);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(true);
        wrapper.setBackground(UIUtil.getPanelBackground());
        wrapper.add(header, BorderLayout.NORTH);
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }
}
