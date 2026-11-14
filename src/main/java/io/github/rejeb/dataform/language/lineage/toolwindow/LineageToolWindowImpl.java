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
package io.github.rejeb.dataform.language.lineage.toolwindow;

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageService;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;
import io.github.rejeb.dataform.language.lineage.view.LineageActions;
import io.github.rejeb.dataform.language.lineage.view.OpenLineageViewAction;
import io.github.rejeb.dataform.language.util.DataformNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shows the Dataform Lineage tool window and keeps its tabs on the lineage editor that changed
 * last.
 */
public final class LineageToolWindowImpl implements LineageToolWindow {

    private static final Key<LineageToolWindowTab> TAB = Key.create("dataform.lineage.toolwindow.tab");

    private final Supplier<@Nullable ToolWindow> lookup;
    private final Supplier<DataplexLineageService> service;
    private final Executor executor;
    private final Clock clock;
    private final Runnable showCatalog;
    private final Consumer<String> urlOpener;
    private final Consumer<LineageNode> openSqlx;
    private final Consumer<String> notifier;
    private final List<LineageModel> attached = new ArrayList<>();
    private final Consumer<LineageModel> listener = this::onChanged;
    private @Nullable LineageModel shown;
    private @Nullable RunsPanel runsPanel;
    private @Nullable TracePanel tracePanel;
    private @Nullable DifferencesPanel differencesPanel;

    public LineageToolWindowImpl(@NotNull Project project) {
        this(() -> ToolWindowManager.getInstance(project).getToolWindow(ID),
                () -> DataplexLineageService.getInstance(project), AppExecutorUtil.getAppExecutorService(),
                Clock.systemDefaultZone(), () -> OpenLineageViewAction.open(project, LineageSource.DATAPLEX),
                BrowserUtil::browse, node -> LineageActions.openSource(project, node),
                message -> DataformNotifications.create(message, NotificationType.INFORMATION).notify(project));
    }

    /**
     * @param lookup      finds the tool window, {@code null} when the project has none
     * @param service     reads what Knowledge Catalog recorded
     * @param executor    runs those reads off the event dispatch thread
     * @param clock       the current time and the zone the times are shown in
     * @param showCatalog opens the lineage editor on the Knowledge Catalog source
     * @param urlOpener   opens a page of the Google Cloud console
     * @param openSqlx    opens the SQLX file of a table of the repository
     * @param notifier    tells the user what an action did
     */
    LineageToolWindowImpl(@NotNull Supplier<@Nullable ToolWindow> lookup, @NotNull Supplier<DataplexLineageService> service,
                          @NotNull Executor executor, @NotNull Clock clock, @NotNull Runnable showCatalog,
                          @NotNull Consumer<String> urlOpener, @NotNull Consumer<LineageNode> openSqlx,
                          @NotNull Consumer<String> notifier) {
        this.openSqlx = openSqlx;
        this.notifier = notifier;
        this.lookup = lookup;
        this.service = service;
        this.executor = executor;
        this.clock = clock;
        this.showCatalog = showCatalog;
        this.urlOpener = urlOpener;
    }

    @Override
    public void attach(@NotNull LineageModel model) {
        if (attached.contains(model)) return;
        attached.add(model);
        model.addListener(listener);
        follow(model);
    }

    @Override
    public void detach(@NotNull LineageModel model) {
        if (!attached.remove(model)) return;
        model.removeListener(listener);
        if (shown == model) follow(attached.isEmpty() ? null : attached.getLast());
    }

    @Override
    public void show(@Nullable LineageToolWindowTab tab) {
        ToolWindow window = lookup.get();
        if (window == null) return;
        window.setAvailable(true);
        createContents(window);
        if (tab != null) select(window.getContentManager(), tab);
        window.show(null);
    }

    @Override
    public void hide() {
        ToolWindow window = lookup.get();
        if (window != null) window.hide(null);
    }

    /**
     * Fills the tool window with its tabs, once.
     */
    void createContents(@NotNull ToolWindow window) {
        ContentManager contents = window.getContentManager();
        if (contents.getContentCount() > 0) return;
        RunsPanel runs = new RunsPanel(service, executor, clock, showCatalog, urlOpener);
        TracePanel trace = new TracePanel(service, executor, showCatalog);
        runsPanel = runs;
        tracePanel = trace;
        contents.addContent(content(runs, LineageToolWindowTab.RUNS));
        DifferencesPanel differences = new DifferencesPanel(showCatalog, openSqlx, notifier);
        differencesPanel = differences;
        contents.addContent(content(trace, LineageToolWindowTab.TRACE));
        Content differencesContent = content(differences, LineageToolWindowTab.DIFFERENCES);
        contents.addContent(differencesContent);
        differences.setCountListener(count -> differencesContent.setDisplayName(LineageToolWindowTab.differencesTitle(count)));
        runs.show(shown);
        trace.show(shown);
        differences.show(shown);
    }

    /**
     * The Differences tab, {@code null} until the tool window is filled.
     */
    @Nullable DifferencesPanel differencesPanel() {
        return differencesPanel;
    }

    private static @NotNull Content content(@NotNull JComponent panel, @NotNull LineageToolWindowTab tab) {
        Content content = ContentFactory.getInstance().createContent(panel, tab.title(), false);
        content.setCloseable(false);
        content.putUserData(TAB, tab);
        return content;
    }

    /**
     * The Trace tab, {@code null} until the tool window is filled.
     */
    @Nullable TracePanel tracePanel() {
        return tracePanel;
    }

    /**
     * The Runs tab, {@code null} until the tool window is filled.
     */
    @Nullable RunsPanel runsPanel() {
        return runsPanel;
    }

    /**
     * The model the tabs follow, {@code null} when no lineage editor is open.
     */
    @Nullable LineageModel shownModel() {
        return shown;
    }

    private void onChanged(@NotNull LineageModel model) {
        if (attached.contains(model)) follow(model);
    }

    private void follow(@Nullable LineageModel model) {
        shown = model;
        if (runsPanel != null) runsPanel.show(model);
        if (tracePanel != null) tracePanel.show(model);
        if (differencesPanel != null) differencesPanel.show(model);
    }

    private static void select(@NotNull ContentManager contents, @NotNull LineageToolWindowTab tab) {
        for (Content content : contents.getContents()) {
            if (content.getUserData(TAB) == tab) {
                contents.setSelectedContent(content);
                return;
            }
        }
    }
}
