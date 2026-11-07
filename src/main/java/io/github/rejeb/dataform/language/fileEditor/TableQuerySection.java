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

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.InlineBanner;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.ui.Banners;
import io.github.rejeb.dataform.language.ui.CollapsibleSection;
import io.github.rejeb.dataform.language.ui.Toolbars;
import io.github.rejeb.dataform.language.ui.TransparentTabbedPane;
import io.github.rejeb.dataform.language.ui.UiText;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewer;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewerHeights;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

class TableQuerySection extends CollapsibleSection {

    private static final String PLACE = "Dataform.PreviewAction";
    private static final String CONSOLE_URL = "https://console.cloud.google.com/bigquery?project=";
    private static final String DRY_RUN_FAILED = "BigQuery dry run failed";
    private static final String NOT_COMPILED = "This action did not compile";
    private static final int TAB_GAP = 8;
    private static final int MAX_LINES = 20;

    private final FormattedCompiledQuery query;
    private final Project project;
    private final Consumer<FormattedCompiledQuery> onRun;
    private final @Nullable String consoleProject;
    private final SqlViewerHeights heights;
    private final SqlViewer queryViewer;
    private final List<SqlViewer> viewers = new ArrayList<>();
    private final CostLabel costLabel = new CostLabel();
    private final InlineBanner errorBanner = Banners.error(DRY_RUN_FAILED, "");
    private @Nullable String shownError;
    private final @Nullable InlineBanner compilationBanner;
    private final @Nullable JBTabbedPane tabs;
    private final List<TableQuerySection> assertionSections = new ArrayList<>();
    private final @Nullable JPanel assertionsPanel;

    TableQuerySection(FormattedCompiledQuery query, FileType fileType, Project project) {
        this(query, fileType, project, ignored -> {
        }, null);
    }

    /**
     * @param onRun          runs the query of the action, as the Run Query action of the preview
     * @param consoleProject the project the BigQuery console opens on, {@code null} when unknown
     */
    TableQuerySection(@NotNull FormattedCompiledQuery query, @Nullable FileType fileType, @NotNull Project project,
                      @NotNull Consumer<FormattedCompiledQuery> onRun, @Nullable String consoleProject) {
        this(query, fileType, project, onRun, consoleProject, new SqlViewerHeights());
    }

    /**
     * @param heights the heights the user gave to the query blocks of the preview, kept while the
     *                preview is open
     */
    TableQuerySection(@NotNull FormattedCompiledQuery query, @Nullable FileType fileType, @NotNull Project project,
                      @NotNull Consumer<FormattedCompiledQuery> onRun, @Nullable String consoleProject,
                      @NotNull SqlViewerHeights heights) {
        this(query, query.tableName(), query.type(), fileType, project, onRun, consoleProject, heights);
    }

    private TableQuerySection(@NotNull FormattedCompiledQuery query, @NotNull String title, @Nullable String subtitle,
                              @Nullable FileType fileType, @NotNull Project project,
                              @NotNull Consumer<FormattedCompiledQuery> onRun, @Nullable String consoleProject,
                              @NotNull SqlViewerHeights heights) {
        super(title, verticalPanel());
        this.query = query;
        this.project = project;
        this.onRun = onRun;
        this.consoleProject = consoleProject;
        this.heights = heights;
        setIcon(ActionTypeIcons.of(query.type()));
        setSubtitle(subtitle);

        ActionToolbar toolbar = Toolbars.horizontal(PLACE, this,
                action("Copy SQL", DataformIcons.COPY, this::hasQuery, this::copySql),
                action("Run Query", DataformIcons.RUN, this::hasQuery, this::runQuery),
                action("Open in BigQuery Console", DataformIcons.EXTERNAL_LINK,
                        () -> consoleProject != null && !consoleProject.isBlank(), this::openConsole));
        setTrailing(costLabel, toolbar.getComponent());

        String errors = query.compilationErrors();
        compilationBanner = errors != null && !errors.isBlank() ? Banners.error(NOT_COMPILED, errors) : null;
        if (compilationBanner != null) {
            addToContent(compilationBanner);
            costLabel.showNotCompiled();
        }
        errorBanner.addAction("Copy error", this::copyError);
        errorBanner.setVisible(false);
        addToContent(errorBanner);

        queryViewer = viewer("query", fileType, query.query(), true);
        SqlViewer preOps = viewer("preOps", fileType, query.preOps(), true);
        SqlViewer incrementalPreOps = viewer("incrementalPreOps", fileType, query.incrementalPreOps(), true);
        SqlViewer postOps = viewer("postOps", fileType, query.postOps(), true);
        for (FormattedCompiledQuery assertion : query.assertions()) {
            assertionSections.add(new TableQuerySection(assertion, PreviewQueries.assertionLabel(assertion.tableName()),
                    null, fileType, project, onRun, consoleProject, heights));
        }
        if (!preOps.isVisible() && !incrementalPreOps.isVisible() && !postOps.isVisible()
                && assertionSections.isEmpty()) {
            tabs = null;
            assertionsPanel = null;
            addToContent(queryViewer);
        } else {
            tabs = new TransparentTabbedPane();
            addTab(queryViewer, "Query");
            addTab(preOps, UiText.counted("Pre-operations", query.preOpsCount()));
            addTab(incrementalPreOps, UiText.counted("Incremental pre-operations", query.incrementalPreOpsCount()));
            addTab(postOps, UiText.counted("Post-operations", query.postOpsCount()));
            assertionsPanel = assertionSections.isEmpty() ? null : assertionsPanel();
            if (assertionsPanel != null) {
                tabs.addTab(UiText.counted("Assertions", assertionSections.size()), assertionsPanel);
            }
            tabs.setSelectedIndex(0);
            addToContent(tabs);
        }
        if (compilationBanner != null) addToContent(viewer("errors", PlainTextFileType.INSTANCE, errors, false));
    }

    /**
     * The viewer of the query of the action.
     */
    @NotNull
    SqlViewer queryViewer() {
        return queryViewer;
    }

    CostLabel costLabel() {
        return costLabel;
    }

    public FormattedCompiledQuery getQuery() {
        return this.query;
    }

    /**
     * Shows the error BigQuery answered the dry-run of the action with; {@code null} hides it.
     */
    void showError(@Nullable String message) {
        boolean shown = message != null && !message.isBlank();
        shownError = shown ? message : null;
        if (shown) Banners.setMessage(errorBanner, DRY_RUN_FAILED, message);
        errorBanner.setVisible(shown);
        revalidate();
        repaint();
    }

    /**
     * Shows the dry-run errors of the action and of its assertions, and marks the Assertions tab
     * when one of them failed.
     *
     * @param errors the error of each action, by full name
     */
    void showErrors(@NotNull Map<String, String> errors) {
        showError(errors.get(query.tableName()));
        boolean assertionFailed = false;
        for (TableQuerySection assertion : assertionSections) {
            String error = errors.get(assertion.getQuery().tableName());
            assertion.showError(error);
            assertionFailed |= error != null && !error.isBlank();
        }
        if (tabs != null && assertionsPanel != null) {
            tabs.setIconAt(tabs.indexOfComponent(assertionsPanel), assertionFailed ? DataformIcons.STATUS_FAILED : null);
        }
    }

    /**
     * The sections of the assertions shown in the Assertions tab.
     */
    @NotNull
    List<TableQuerySection> assertionSections() {
        return assertionSections;
    }

    @NotNull
    InlineBanner errorBanner() {
        return errorBanner;
    }

    @Nullable
    InlineBanner compilationBanner() {
        return compilationBanner;
    }

    @Nullable
    JBTabbedPane tabs() {
        return tabs;
    }

    void copySql() {
        if (hasQuery()) CopyPasteManager.getInstance().setContents(new StringSelection(query.query()));
    }

    /**
     * Whether the action did not compile, so it has no query to estimate.
     */
    boolean notCompiled() {
        return compilationBanner != null;
    }

    void runQuery() {
        if (hasQuery()) onRun.accept(query);
    }

    public void dispose() {
        viewers.forEach(SqlViewer::dispose);
        assertionSections.forEach(TableQuerySection::dispose);
    }

    private boolean hasQuery() {
        return query.query() != null && !query.query().isBlank();
    }

    private void copyError() {
        if (shownError != null) CopyPasteManager.getInstance().setContents(new StringSelection(shownError));
    }

    private void openConsole() {
        if (consoleProject != null && !consoleProject.isBlank()) BrowserUtil.browse(CONSOLE_URL + consoleProject);
    }

    private void addToContent(@NotNull JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        content().add(component);
    }

    @NotNull
    private JPanel assertionsPanel() {
        JPanel panel = verticalPanel();
        panel.setBorder(JBUI.Borders.emptyTop(TAB_GAP));
        assertionSections.forEach(panel::add);
        return panel;
    }

    private void addTab(@NotNull SqlViewer viewer, @NotNull String title) {
        if (tabs != null && viewer.isVisible()) {
            viewer.setBorder(JBUI.Borders.emptyTop(TAB_GAP));
            tabs.addTab(title, viewer);
        }
    }

    /**
     * A block of the action, as tall as its text up to twenty lines and resizable; its height is
     * remembered under the action and the block.
     */
    @NotNull
    private SqlViewer viewer(@NotNull String block, @Nullable FileType fileType, @Nullable String text,
                             boolean lineNumbers) {
        SqlViewer viewer = new SqlViewer(project, SqlViewerOptions.fitText(MAX_LINES)
                .withFileType(fileType != null ? fileType : PlainTextFileType.INSTANCE)
                .withLineNumbers(lineNumbers)
                .withResize(heights, query.tableName() + "#" + block));
        viewers.add(viewer);
        viewer.setText(text);
        return viewer;
    }

    @NotNull
    private static DumbAwareAction action(@NotNull String text, @NotNull Icon icon, @NotNull BooleanSupplier enabled,
                                          @NotNull Runnable perform) {
        return new DumbAwareAction(text, text, icon) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                perform.run();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(enabled.getAsBoolean());
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }
}
