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

import io.github.rejeb.dataform.language.unittest.preview.TestQueries;
import com.intellij.execution.services.ServiceEventListener;
import com.intellij.execution.services.ServiceViewManager;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.sql.SqlFileType;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.DatabaseIcons;
import io.github.rejeb.dataform.language.compilation.CompilationFailures;
import io.github.rejeb.dataform.language.compilation.DataformCompilationEvent;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompilationError;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledQuery;
import io.github.rejeb.dataform.language.compilation.model.CompiledTest;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryExecutionService;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryJobResult;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryResultsRegistry;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.serviceview.DataformQueryContributor;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.serviceview.QueryResultNode;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.lineage.extractor.LineageExtractorImpl;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.view.LineageFilePanel;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.unittest.SqlxUnitTests;
import io.github.rejeb.dataform.language.util.DataformNotifications;
import io.github.rejeb.dataform.language.util.PreOperationsFilter;
import io.github.rejeb.dataform.language.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.swing.*;

/**
 * Preview side of the SQLX split editor: lineage, compiled query and schema of the file's
 * actions. The compiled SQL is formatted only when the Query view is shown, and once per
 * compilation: formatting runs a write command on the EDT, which is far too costly to pay on
 * every tab switch.
 */
public class SqlxCompiledPreviewEditor extends UserDataHolderBase implements FileEditor {

    public enum View {LINEAGE, QUERY, SCHEMA, TEST}

    private final JPanel mainPanel = new JPanel(new CardLayout());
    private final SchemaPanel schemaPanel;
    private final LineageFilePanel lineagePanel;
    private final QueryPanel queryPanel;
    private final Project project;
    private final VirtualFile file;
    private long myLastCompiledStamp = -1;
    private volatile List<CompiledQuery> rawQueries = List.of();
    private boolean queryViewStale = false;
    private final TestPreviewPanel testPanel;
    private volatile SqlxPreviewMode mode = SqlxPreviewMode.ACTION;
    private volatile CompiledTest compiledTest;
    private volatile String testCompilationErrors;
    private boolean testViewStale = false;
    private final AtomicLong refreshGeneration = new AtomicLong();
    private volatile boolean graphStale = false;

    public SqlxCompiledPreviewEditor(@NotNull Project project, VirtualFile file) {
        this.project = project;
        this.file = file;
        schemaPanel = new SchemaPanel(project);
        lineagePanel = new LineageFilePanel(project, file);
        queryPanel = new QueryPanel(project, resolveFileType(file));
        testPanel = new TestPreviewPanel(project);

        mainPanel.setOpaque(true);
        mainPanel.setBackground(UIUtil.getPanelBackground());
        mainPanel.add(lineagePanel, View.LINEAGE.name());
        mainPanel.add(withHeader("Query", DatabaseIcons.Sql, queryPanel), View.QUERY.name());
        mainPanel.add(withHeader("Schema", AllIcons.Nodes.DataTables, schemaPanel), View.SCHEMA.name());
        mainPanel.add(withHeader("Test", AllIcons.Nodes.Test, testPanel), View.TEST.name());

        showPanel(View.LINEAGE);
        project.getMessageBus().connect(this).subscribe(DataformCompilationEvent.TOPIC,
                (DataformCompilationEvent) this::onGraphChanged);
        updateCompiledSql();
    }

    private void onGraphChanged() {
        graphStale = true;
        ApplicationManager.getApplication().invokeLater(() -> {
            if (graphStale && mainPanel.isShowing()) {
                graphStale = false;
                refreshPreview(false);
            }
        }, ModalityState.nonModal(), project.getDisposed());
    }

    @NotNull
    public Project getProject() {
        return project;
    }

    /**
     * Reloads the preview from the current compiled graph and refreshes the schemas of its
     * actions.
     */
    public void updateCompiledSql() {
        refreshPreview(true);
    }

    private void refreshPreview(boolean refreshSchemas) {
        long generation = refreshGeneration.incrementAndGet();
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Dataform: loading preview", true) {
            private List<CompiledQuery> compiledQueries;
            private List<GraphTarget> graphTargets;
            private LineageGraph fileLineage;
            private boolean unitTestFile;
            private CompiledTest foundTest;
            private String foundErrors;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Compiling " + file.getName() + "...");
                unitTestFile = ReadAction.nonBlocking(() -> {
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                    return psiFile != null && SqlxUnitTests.isUnitTestFile(psiFile);
                }).executeSynchronously();
                DataformCompilationService svc = DataformCompilationService.getInstance(project);
                CompiledGraph graph = svc.getCompiledGraph();
                if (graph != null) {
                    String path = file.getCanonicalPath();
                    if (unitTestFile) {
                        foundTest = graph.findTestByFileName(path).stream().findFirst().orElse(null);
                        foundErrors = graph.findCompilationErrorByFileName(path).stream()
                                .map(CompilationError::getMessage)
                                .filter(Objects::nonNull)
                                .reduce((a, b) -> a + "\n" + b)
                                .orElse(null);
                    } else {
                        compiledQueries = graph.findCompiledQueryByFileName(path);
                        graphTargets = GraphTarget.targetsOf(graph, path);
                        fileLineage = new LineageExtractorImpl().extract(graph);
                    }
                }
                if (graph != null && refreshSchemas) {
                    DataformTableSchemaService.getInstance(project)
                            .refreshAsync(graph, false, CompilationFailures.fileNamesOf(graph));
                }
                indicator.checkCanceled();
            }

            @Override
            public void onSuccess() {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (generation != refreshGeneration.get()) {
                        return;
                    }
                    mode = unitTestFile ? SqlxPreviewMode.UNIT_TEST : SqlxPreviewMode.ACTION;
                    compiledTest = foundTest;
                    testCompilationErrors = foundErrors;
                    testViewStale = true;
                    schemaPanel.setContent(graphTargets != null ? graphTargets : List.of());
                    rawQueries = compiledQueries != null ? compiledQueries : List.of();
                    queryViewStale = true;
                    if (!mode.shows(activeView)) {
                        showPanel(mode.defaultView());
                    } else if (activeView == View.QUERY) {
                        refreshQueryView();
                    } else if (activeView == View.TEST) {
                        refreshTestView();
                    }
                    if (fileLineage != null) {
                        lineagePanel.setLineage(fileLineage);
                    }
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
        testPanel.dispose();
    }

    @Override
    public void selectNotify() {
        long stamp = file.getTimeStamp();
        if (stamp > myLastCompiledStamp) {
            myLastCompiledStamp = stamp;
            graphStale = false;
            updateCompiledSql();
        } else if (graphStale) {
            graphStale = false;
            refreshPreview(false);
        }
    }

    public boolean hasQuery() {
        if (mode == SqlxPreviewMode.UNIT_TEST) {
            return !TestQueries.of(compiledTest).isEmpty();
        }
        return rawQueries.stream().anyMatch(q -> q.query() != null && !q.query().isBlank());
    }

    public void executeQuery(@NotNull AnActionEvent e) {
        List<FormattedCompiledQuery> queries = mode == SqlxPreviewMode.UNIT_TEST
                ? TestQueries.of(compiledTest).stream()
                        .map(q -> new FormattedCompiledQuery(q.label(), null, null, q.sql(), null, null))
                        .toList()
                : rawQueries.stream().map(q -> format(q, UnaryOperator.identity())).toList();
        if (queries.isEmpty()) return;

        if (queries.size() == 1) {
            runQueries(List.of(queries.getFirst()));
        } else {
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
            DataformNotifications.create("BigQuery execution", "No GCP project configured.", NotificationType.WARNING)
                    .notify(project);
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

                            String sql = q.preOps() != null
                                    ? Utils.withPreOperations(
                                            PreOperationsFilter.keepReadOnly(List.of(q.preOps())), q.query())
                                    : q.query();
                            BigQueryJobResult result = svc.execute(sql, projectId, q.tableName(), indicator);
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
        if (view == View.QUERY) {
            refreshQueryView();
        }
        if (view == View.TEST) {
            refreshTestView();
        }
        CardLayout cl = (CardLayout) mainPanel.getLayout();
        cl.show(mainPanel, view.name());
    }

    private void refreshQueryView() {
        if (!queryViewStale) return;
        queryViewStale = false;
        queryPanel.setContent(rawQueries.stream().map(q -> format(q, sql -> Utils.formatSql(project, sql))).toList());
    }

    private void refreshTestView() {
        if (!testViewStale) return;
        testViewStale = false;
        CompiledTest test = compiledTest;
        testPanel.setContent(
                test == null ? null : Utils.formatSql(project, test.getTestQuery()),
                test == null ? null : Utils.formatSql(project, test.getExpectedOutputQuery()),
                testCompilationErrors);
    }

    /**
     * Returns whether the preview shows an action (lineage, query, schema) or a unit test.
     */
    @NotNull
    public SqlxPreviewMode getMode() {
        return mode;
    }

    private static FormattedCompiledQuery format(CompiledQuery q, UnaryOperator<String> sql) {
        return new FormattedCompiledQuery(q.tableName(), joinOrNull(q.preOps(), sql),
                joinOrNull(q.incrementalPreOps(), sql), q.query() == null ? null : sql.apply(q.query()),
                joinOrNull(q.postOps(), sql), joinOrNull(q.compilationErrors(), UnaryOperator.identity()));
    }

    private static @Nullable String joinOrNull(@Nullable List<String> parts, UnaryOperator<String> map) {
        return parts == null || parts.isEmpty() ? null : parts.stream().map(map).collect(Collectors.joining("\n"));
    }

    public View getActiveView() {
        return activeView;
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
