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

import io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindowTab;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.SeparatorOrientation;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Function;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.SwingTextTrimmer;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.gcp.common.BigQueryConsoleUrls;
import io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui.RunConfigUiUtils;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexGraphBuilder;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageService;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.dataplex.Freshness;
import io.github.rejeb.dataform.language.lineage.dataplex.LinkDetails;
import io.github.rejeb.dataform.language.lineage.dataplex.ProcessUse;
import io.github.rejeb.dataform.language.lineage.dataplex.RecordedProcess;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;
import io.github.rejeb.dataform.language.lineage.model.LinkRef;
import io.github.rejeb.dataform.language.ui.Actions;
import io.github.rejeb.dataform.language.ui.ReadOnlyTextFields;
import io.github.rejeb.dataform.language.ui.Toolbars;
import io.github.rejeb.dataform.language.ui.UiText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Right-hand details panel of the project lineage, as in the UI prototype: the glyph, name, schema
 * and type of the selected action, buttons to open its SQLX, run it and focus on its lineage, its
 * file, schema and tags, then its upstream and downstream actions, a click selecting one. Shows an
 * empty state while nothing is selected.
 *
 * <p>With the Knowledge Catalog source it also shows what was recorded: for a table its BigQuery
 * name, its last write and the processes on its links; for a table outside the repository its
 * project and dataset; for a selected link when it was recorded, by what, and what Dataform
 * declares for it. Those are read off the EDT the first time they are needed.
 */
public final class DetailsPanel extends JPanel {

    private static final String PLACE = "Dataform.Lineage.Details";
    private static final int KEY_WIDTH = 70;
    private static final int ROW_HEIGHT = 26;
    private static final String NO_VALUE = "—";
    private static final String LOADING = "Loading…";
    private static final String UNAVAILABLE = "Knowledge Catalog cannot answer";
    private static final String EMPTY_DECLARED = "Select a node to see its details";
    private static final String EMPTY_RECORDED = "Select a node or a link to see its details";

    private final Project project;
    private final LineageModel model;
    private final JPanel north = new JPanel(new BorderLayout());
    private final JPanel headerHolder = new JPanel(new BorderLayout());
    private @NotNull Runnable hideHandler = () -> {};
    private final InplaceButton minus = new InplaceButton(new IconButton("Hide details", LineageIcons.MINUS),
            e -> hideHandler.run());
    private final StatusText emptyText = new StatusText(this) {
        @Override
        protected boolean isStatusVisible() {
            return isEmptyStateShown();
        }
    };
    private final JPanel content = new WidthTrackingPanel();
    private final JBScrollPane scroll;
    private final ActionToolbar toolbar;
    private final DependencyList upstream;
    private final DependencyList downstream;
    private final DependencyList upstreamColumns;
    private final DependencyList downstreamColumns;
    private final Map<String, JComponent> values = new LinkedHashMap<>();
    private final DataplexLineageService lineageService;
    private final Executor executor;
    private final Clock clock;
    private final Consumer<String> urlOpener;
    private final Map<String, Object> answers = new HashMap<>();
    private final Set<String> asked = new HashSet<>();
    private final List<String> notes = new ArrayList<>();
    private final List<String> recordedBy = new ArrayList<>();
    private final List<String> recordedProcesses = new ArrayList<>();
    private final List<AnAction> declaredActions;
    private final AnAction openSqlx;
    private final AnAction run;
    private final AnAction focus;
    private final AnAction openCatalog;
    private final AnAction openBigQuery;
    private final AnAction copyName;
    private final AnAction openLatestJob;
    private final AnAction showRuns;
    private final AnAction trace;
    private @Nullable Consumer<LineageToolWindowTab> tabOpener;
    private @Nullable DataplexView answersFor;
    private @Nullable LinkRef link;
    private @Nullable String latestJobUrl;
    private @Nullable LineageNode node;
    private @Nullable JBTextField title;
    private @Nullable JBLabel subtitle;
    private @Nullable JBLabel glyph;
    private String lastSignature;

    /**
     * Creates the panel bound to the given model.
     */
    public DetailsPanel(@NotNull Project project, @NotNull LineageModel model) {
        this(project, model, DataplexLineageService.getInstance(project), AppExecutorUtil.getAppExecutorService(),
                Clock.systemDefaultZone(), BrowserUtil::browse);
    }

    /**
     * Creates the panel bound to the given model.
     *
     * @param lineageService reads what Knowledge Catalog recorded about the selection
     * @param executor       runs those reads off the EDT
     * @param clock          the current time and the zone the times are shown in
     * @param urlOpener      opens a page of the Google Cloud console
     */
    DetailsPanel(@NotNull Project project, @NotNull LineageModel model, @NotNull DataplexLineageService lineageService,
                 @NotNull Executor executor, @NotNull Clock clock, @NotNull Consumer<String> urlOpener) {
        super(new BorderLayout());
        this.project = project;
        this.model = model;
        this.lineageService = lineageService;
        this.executor = executor;
        this.clock = clock;
        this.urlOpener = urlOpener;
        setBackground(UIUtil.getPanelBackground());
        emptyText.setText(EMPTY_DECLARED);

        upstream = new DependencyList("Upstream", "No upstream actions", new NodeRenderer(), this::nodeName, model::select);
        downstream = new DependencyList("Downstream", "No downstream actions", new NodeRenderer(), this::nodeName, model::select);
        upstreamColumns = new DependencyList("Upstream columns", "No upstream columns", new ColumnRenderer(), this::columnName, model::selectColumn);
        downstreamColumns = new DependencyList("Downstream columns", "No downstream columns", new ColumnRenderer(), this::columnName, model::selectColumn);

        openSqlx = Toolbars.withText(nodeAction("Open SQLX", "Open the SQLX file of the action", DataformIcons.FILE,
                false, LineageActions::openSource));
        run = Toolbars.withText(nodeAction("Run", "Run the action", DataformIcons.RUN, true, LineageActions::runAction));
        focus = Toolbars.withText(LineageToolbarSupport.focusToggle(model));
        openCatalog = consoleAction("Open in Knowledge Catalog", "Open the lineage of the table in the Google Cloud console",
                () -> node == null ? null : BigQueryConsoleUrls.lineageUrl(node.fullName()));
        openBigQuery = consoleAction("Open in BigQuery", "Open the table in the BigQuery console",
                () -> node == null ? null : BigQueryConsoleUrls.tableUrl(node.fullName()));
        openLatestJob = consoleAction("Open latest job", "Open the latest BigQuery job recorded for this link",
                () -> latestJobUrl);
        showRuns = Toolbars.withText(Actions.of("Show runs", "Show the runs recorded for this link",
                DataformIcons.RUN, () -> {
                    if (tabOpener != null) tabOpener.accept(LineageToolWindowTab.RUNS);
                }));
        trace = Toolbars.withText(Actions.of("Trace", "List everything recorded upstream and downstream of the table",
                DataformIcons.LINEAGE, () -> {
                    if (tabOpener != null) tabOpener.accept(LineageToolWindowTab.TRACE);
                }));
        copyName = Toolbars.withText(Actions.of("Copy name", "Copy the full name", AllIcons.Actions.Copy, () -> {
            if (node != null) CopyPasteManager.getInstance().setContents(new StringSelection(node.fullName()));
        }));
        declaredActions = List.of(openSqlx, run, focus);
        toolbar = Toolbars.horizontal(PLACE, this, declaredActions.toArray(AnAction.EMPTY_ARRAY));

        headerHolder.setOpaque(false);
        JPanel minusHolder = new JPanel(new BorderLayout());
        minusHolder.setOpaque(false);
        minusHolder.setBorder(JBUI.Borders.empty(10, 0, 0, 8));
        minusHolder.add(minus, BorderLayout.NORTH);
        JComponent toolbarComponent = toolbar.getComponent();
        toolbarComponent.setBorder(JBUI.Borders.empty(0, 6, 6, 6));
        north.setOpaque(false);
        north.add(headerHolder, BorderLayout.CENTER);
        north.add(minusHolder, BorderLayout.EAST);
        north.add(toolbarComponent, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        content.setOpaque(false);
        content.setBorder(JBUI.Borders.empty(12, 12, 12, 8));
        scroll = new JBScrollPane(content,
                JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(JBUI.Borders.empty());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);

        rebuild();
        model.addListener(m -> onModelChanged());
    }

    /**
     * Sets what shows a tab of the Dataform Lineage tool window; without it the panel offers no
     * action that needs the tool window.
     */
    public void setTabOpener(@Nullable Consumer<LineageToolWindowTab> opener) {
        this.tabOpener = opener;
        rebuild();
    }

    /**
     * Sets the action run when the header's minus button is pressed: hiding the panel without
     * clearing the selection.
     */
    public void setHideHandler(@NotNull Runnable handler) {
        this.hideHandler = handler;
    }

    /**
     * Whether the panel shows its empty state, as while nothing is selected.
     */
    public boolean isEmptyStateShown() {
        return node == null && link == null;
    }

    /**
     * The explanations shown as plain text, in order: what Dataform declares for a link, then the
     * note on how long the lineage is kept.
     */
    @NotNull List<String> notes() {
        return List.copyOf(notes);
    }

    /**
     * The processes listed under "Recorded by" for a link.
     */
    @NotNull List<String> recordedBy() {
        return List.copyOf(recordedBy);
    }

    /**
     * The rows listed under "Recorded processes" for a table: the process, what it did and when.
     */
    @NotNull List<String> recordedProcesses() {
        return List.copyOf(recordedProcesses);
    }

    /**
     * The message shown while nothing is selected.
     */
    public @NotNull StatusText getEmptyText() {
        return emptyText;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        emptyText.paint(this, g);
    }

    ActionToolbar toolbar() {
        return toolbar;
    }

    JBList<String> upstream() {
        return upstream.list;
    }

    JBList<String> downstream() {
        return downstream.list;
    }

    @Nullable String headerTitle() {
        return title != null ? title.getText() : null;
    }

    @Nullable String headerSubtitle() {
        return subtitle != null ? subtitle.getText() : null;
    }

    @Nullable Icon headerIcon() {
        return glyph != null ? glyph.getIcon() : null;
    }

    /**
     * The text shown for the given key of the form rows, such as "Tags".
     */
    @Nullable String valueOf(@NotNull String key) {
        JComponent value = values.get(key);
        if (value instanceof JBTextField field) return field.getText();
        if (value instanceof ActionLink link) return link.getText();
        if (value instanceof JBLabel label) return label.getText();
        return null;
    }

    private void onModelChanged() {
        String signature = System.identityHashCode(model.graph()) + "|" + model.selectedId()
                + "|" + model.focusId() + "|" + model.selectedColumnId() + "|" + model.source()
                + "|" + System.identityHashCode(model.dataplexView()) + "|" + model.selectedLink();
        if (signature.equals(lastSignature)) return;
        lastSignature = signature;
        SwingUtilities.invokeLater(this::rebuild);
    }

    private void rebuild() {
        content.removeAll();
        headerHolder.removeAll();
        values.clear();
        title = null;
        subtitle = null;
        glyph = null;
        notes.clear();
        recordedBy.clear();
        recordedProcesses.clear();
        latestJobUrl = null;
        DataplexView view = model.source() == LineageSource.DATAPLEX ? model.dataplexView() : null;
        if (view != answersFor) {
            answersFor = view;
            answers.clear();
            asked.clear();
        }
        emptyText.setText(model.source() == LineageSource.DATAPLEX ? EMPTY_RECORDED : EMPTY_DECLARED);
        link = view != null ? shownLink() : null;
        node = link == null ? shownNode() : null;
        LineageNode shown = node;
        boolean something = shown != null || link != null;
        north.setBorder(something
                ? JBUI.Borders.customLine(JBUI.CurrentTheme.Separator.color(), 0, 0, 1, 0)
                : JBUI.Borders.empty());
        toolbar.getComponent().setVisible(something);
        scroll.setVisible(something);
        if (link != null) showLink(view, link);
        setActions(view);
        if (shown != null) {
            headerHolder.add(header(shown), BorderLayout.CENTER);
            content.add(view != null ? recordedForm(view, shown) : form(shown));
            Collection<String> sources = model.graph().predecessors(shown.id());
            Collection<String> targets = model.graph().successors(shown.id());
            upstream.show(content, sources, count(sources, view), false);
            downstream.show(content, targets, count(targets, view), true);
            if (view != null) showProcesses(view, shown);
            ColumnLineageGraph columns = model.columnGraph();
            String columnId = model.selectedColumnId();
            if (columnId != null && columns != null) {
                ColumnRef selected = columns.column(columnId);
                if (selected != null) {
                    content.add(separator("Column lineage", null, true));
                    content.add(columnForm(selected));
                }
                upstreamColumns.show(content, columns.predecessors(columnId), true);
                downstreamColumns.show(content, columns.successors(columnId), true);
            }
        }
        toolbar.updateActionsAsync();
        revalidate();
        repaint();
    }

    private @Nullable LinkRef shownLink() {
        LinkRef selected = model.selectedLink();
        if (selected == null) return null;
        return model.graph().node(selected.fromId()) != null && model.graph().node(selected.toId()) != null ? selected : null;
    }

    private void setActions(@Nullable DataplexView view) {
        List<AnAction> actions;
        if (link != null) {
            actions = tabOpener == null ? List.of(openLatestJob) : List.of(showRuns, openLatestJob);
        } else if (view == null || node == null) {
            actions = declaredActions;
        } else if (DataplexGraphBuilder.TYPE_OUTSIDE.equals(node.dataformType())) {
            actions = withTrace(List.of(openBigQuery, focus, copyName));
        } else if (DataplexGraphBuilder.TYPE_ASSET.equals(node.dataformType())) {
            actions = withTrace(List.of(focus, copyName));
        } else {
            actions = withTrace(List.of(openSqlx, run, focus, openCatalog));
        }
        DefaultActionGroup group = (DefaultActionGroup) toolbar.getActionGroup();
        if (List.of(group.getChildActionsOrStubs()).equals(actions)) return;
        group.removeAll();
        group.addAll(actions);
    }

    private @NotNull List<AnAction> withTrace(@NotNull List<AnAction> actions) {
        if (tabOpener == null) return actions;
        List<AnAction> all = new ArrayList<>(actions);
        all.add(trace);
        return all;
    }

    private @NotNull String count(@NotNull Collection<String> ids, @Nullable DataplexView view) {
        String total = String.valueOf(ids.size());
        if (view == null) return total;
        long outside = ids.stream().map(model.graph()::node).filter(n -> n != null && isOutside(n)).count();
        return outside == 0 ? total : UiText.dot(total, outside + " outside this repository");
    }

    private static boolean isOutside(@NotNull LineageNode shown) {
        return DataplexGraphBuilder.TYPE_OUTSIDE.equals(shown.dataformType())
                || DataplexGraphBuilder.TYPE_ASSET.equals(shown.dataformType());
    }

    /**
     * What Knowledge Catalog answered for the given key, read off the EDT the first time it is
     * asked for: {@code null} while it is being read, a {@link Failure} when the read failed.
     */
    private @Nullable Object answer(@NotNull String key, @NotNull DataplexView view, @NotNull Supplier<Object> read) {
        Object known = answers.get(key);
        if (known != null) return known;
        if (asked.add(key)) {
            executor.execute(() -> {
                Object value;
                try {
                    value = read.get();
                } catch (RuntimeException e) {
                    value = new Failure(e.getMessage());
                }
                Object result = value;
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (answersFor != view) return;
                    answers.put(key, result);
                    rebuild();
                }, ModalityState.any());
            });
        }
        return null;
    }

    private @NotNull JComponent recordedForm(@NotNull DataplexView view, @NotNull LineageNode shown) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(JBUI.Borders.emptyBottom(12));
        int row = 0;
        if (DataplexGraphBuilder.TYPE_OUTSIDE.equals(shown.dataformType())) {
            String[] parts = shown.fullName().split("\\.");
            addRow(form, row++, "Project", rowValue(parts.length > 2 ? parts[0] : NO_VALUE));
            addRow(form, row++, "Dataset", rowValue(parts.length > 2 ? parts[1] : NO_VALUE));
        } else if (DataplexGraphBuilder.TYPE_ASSET.equals(shown.dataformType())) {
            addRow(form, row++, "Kind", rowValue(shown.schema()));
            JBTextField name = rowValue(shown.fullName());
            name.setToolTipText(shown.fullName());
            addRow(form, row++, "Name", name);
        } else {
            addRow(form, row++, "File", fileValue(shown));
            addRow(form, row++, "Schema", rowValue(shown.schema()));
            addRow(form, row++, "Tags", rowValue(
                    shown.tags().isEmpty() ? NO_VALUE : String.join(", ", shown.tags())));
            JBTextField bigQuery = rowValue(shown.fullName());
            bigQuery.setToolTipText(shown.fullName());
            addRow(form, row++, "BigQuery", bigQuery);
        }
        Object fresh = answer("freshness|" + shown.id(), view, () -> lineageService.freshness(view, shown.id()));
        boolean isView = fresh instanceof Freshness known ? known.view()
                : "view".equals(shown.dataformType()) || "materialized_view".equals(shown.dataformType());
        String value = fresh == null ? LOADING
                : fresh instanceof Freshness known ? FreshnessText.value(known, view.range(), clock) : UNAVAILABLE;
        addRow(form, row, isView ? "Created or replaced" : "Last write", rowValue(value));
        return form;
    }

    @SuppressWarnings("unchecked")
    private void showProcesses(@NotNull DataplexView view, @NotNull LineageNode shown) {
        Object answer = answer("processes|" + shown.id(), view, () -> lineageService.processesOf(view, shown.id()));
        List<ProcessUse> uses = answer instanceof List<?> list ? (List<ProcessUse>) list : List.of();
        content.add(separator("Recorded processes", answer instanceof List<?> ? String.valueOf(uses.size()) : null, true));
        if (uses.isEmpty()) {
            content.add(hint(answer == null ? LOADING : answer instanceof Failure ? UNAVAILABLE
                    : "None in the " + view.range().label().toLowerCase(Locale.ROOT)));
            return;
        }
        for (ProcessUse use : uses) {
            String what = (use.wrote() ? "wrote it " : "read it ") + lowerCaseDay(FreshnessText.when(use.lastSeen(), clock));
            recordedProcesses.add(UiText.dot(use.displayName(), what));
            content.add(listRow(use.displayName(), what));
        }
    }

    private static @NotNull String lowerCaseDay(@NotNull String when) {
        return when.startsWith("Today") || when.startsWith("Yesterday") ? StringUtil.decapitalize(when) : when;
    }

    private void showLink(@NotNull DataplexView view, @NotNull LinkRef shown) {
        LineageNode from = model.graph().node(shown.fromId());
        LineageNode to = model.graph().node(shown.toId());
        if (from == null || to == null) return;
        headerHolder.add(header(LineageIcons.DIRECTION_LR, from.name() + " → " + to.name(), from.fullName() + " → " + to.fullName(),
                "Link recorded by Knowledge Catalog"), BorderLayout.CENTER);

        Object answer = answer("link|" + shown.fromId() + ">" + shown.toId(), view,
                () -> lineageService.linkDetails(view, shown.fromId(), shown.toId()));
        LinkDetails details = answer instanceof LinkDetails known ? known : null;
        String pending = answer == null ? LOADING : UNAVAILABLE;
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(JBUI.Borders.emptyBottom(12));
        addRow(form, 0, "First recorded", rowValue(details == null ? pending
                : details.firstSeen() == null ? NO_VALUE : FreshnessText.when(details.firstSeen(), clock)));
        addRow(form, 1, "Last recorded", rowValue(details == null ? pending
                : details.lastSeen() == null ? NO_VALUE : FreshnessText.whenAndAge(details.lastSeen(), clock)));
        addRow(form, 2, "Runs", rowValue(details == null ? pending
                : details.runs().size() + " in the " + view.range().label().toLowerCase(Locale.ROOT)));
        content.add(form);

        List<RecordedProcess> processes = details == null ? List.of() : details.processes();
        content.add(separator("Recorded by", details == null ? null : String.valueOf(processes.size()), false));
        if (processes.isEmpty()) content.add(hint(details == null ? pending : "No process recorded"));
        for (RecordedProcess process : processes) {
            recordedBy.add(process.displayName());
            content.add(listRow(process.displayName(), null));
            if (latestJobUrl == null && process.latestJobId() != null) {
                latestJobUrl = RunConfigUiUtils.bigQueryJobUrl(view.scope().projectId(), view.scope().location(),
                        process.latestJobId());
            }
        }

        content.add(separator("In Dataform", null, true));
        boolean declared = view.repository().successors(shown.fromId()).contains(shown.toId());
        if (declared && to.fileName() != null) {
            JPanel declaration = new JPanel(new GridBagLayout());
            declaration.setOpaque(false);
            declaration.setBorder(JBUI.Borders.emptyTop(8));
            addRow(declaration, 0, "Declared in", fileValue(to));
            addRow(declaration, 1, "Through", rowValue("ref(\"" + from.name() + "\")"));
            content.add(declaration);
        } else if (isOutside(from) || isOutside(to)) {
            content.add(note("One of the two tables is outside this repository, so Dataform declares nothing here."));
        } else {
            content.add(note("Both tables are in this repository, but no ref() declares this link."));
        }
        JComponent kept = note("Knowledge Catalog keeps lineage for 30 days, so no link can show a date older than that.");
        kept.setForeground(UIUtil.getContextHelpForeground());
        content.add(kept);
    }

    private @NotNull JComponent note(@NotNull String text) {
        notes.add(text);
        JBTextArea area = ReadOnlyTextFields.multiLine(text);
        area.setOpaque(false);
        area.setBorder(JBUI.Borders.emptyTop(8));
        return area;
    }

    private static @NotNull JBTextField rowValue(@Nullable String text) {
        return ReadOnlyTextFields.singleLineTrimmed(text, SwingTextTrimmer.ELLIPSIS_AT_RIGHT);
    }

    private static @NotNull JComponent hint(@NotNull String text) {
        JBLabel label = new JBLabel(text);
        label.setForeground(UIUtil.getContextHelpForeground());
        label.setBorder(JBUI.Borders.empty(4, 8));
        return label;
    }

    private static @NotNull JComponent listRow(@NotNull String text, @Nullable String trailing) {
        JPanel row = new JPanel(new BorderLayout(JBUIScale.scale(8), 0));
        row.setOpaque(false);
        row.setBorder(JBUI.Borders.empty(4, 8));
        JBLabel name = new JBLabel(text);
        name.setMinimumSize(new Dimension(0, name.getPreferredSize().height));
        row.add(name, BorderLayout.CENTER);
        if (trailing != null) {
            JBLabel detail = new JBLabel(trailing);
            detail.setFont(JBFont.medium());
            detail.setForeground(UIUtil.getContextHelpForeground());
            row.add(detail, BorderLayout.EAST);
        }
        return row;
    }

    private AnAction consoleAction(@NotNull String text, @NotNull String description, @NotNull Supplier<String> url) {
        return Toolbars.withText(Actions.enabledWhen(text, description, AllIcons.Ide.External_link_arrow,
                () -> url.get() != null, () -> {
                    String page = url.get();
                    if (page != null) urlOpener.accept(page);
                }));
    }

    private record Failure(@Nullable String message) {
    }

    private @Nullable LineageNode shownNode() {
        LineageNode selected = model.selectedId() != null ? model.graph().node(model.selectedId()) : null;
        if (selected == null && model.selectedColumnId() != null && model.columnGraph() != null) {
            ColumnRef ref = model.columnGraph().column(model.selectedColumnId());
            if (ref != null) selected = model.graph().node(ref.tableNodeId());
        }
        return selected;
    }

    private @NotNull JComponent header(@NotNull LineageNode shown) {
        String type = DataplexGraphBuilder.TYPE_OUTSIDE.equals(shown.dataformType()) ? "outside this repository"
                : DataplexGraphBuilder.TYPE_ASSET.equals(shown.dataformType()) ? null : shown.dataformType();
        return header(TypeGlyphIcon.of(shown.dataformType()), shown.name(), shown.fullName(), UiText.dot(shown.schema(), type));
    }

    private @NotNull JComponent header(@NotNull Icon icon, @NotNull String name, @NotNull String tooltip,
                                       @NotNull String description) {
        JPanel panel = new JPanel(new BorderLayout(JBUIScale.scale(8), 0));
        panel.setOpaque(false);
        panel.setBorder(JBUI.Borders.empty(10, 12, 6, 0));

        glyph = new JBLabel(icon);
        glyph.setVerticalAlignment(SwingConstants.TOP);
        glyph.setBorder(JBUI.Borders.emptyTop(1));
        panel.add(glyph, BorderLayout.WEST);

        JPanel texts = new JPanel(new VerticalLayout(JBUIScale.scale(2)));
        texts.setOpaque(false);
        title = ReadOnlyTextFields.singleLineTrimmed(name, SwingTextTrimmer.ELLIPSIS_AT_RIGHT);
        title.setToolTipText(tooltip);
        title.setFont(JBFont.label().biggerOn(1f).asBold());
        texts.add(title);
        subtitle = new JBLabel(description);
        subtitle.setFont(JBFont.medium());
        subtitle.setForeground(UIUtil.getContextHelpForeground());
        texts.add(subtitle);
        panel.add(texts, BorderLayout.CENTER);
        return panel;
    }

    private @NotNull JComponent form(@NotNull LineageNode shown) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(JBUI.Borders.emptyBottom(12));
        addRow(form, 0, "File", fileValue(shown));
        addRow(form, 1, "Schema", rowValue(shown.schema()));
        addRow(form, 2, "Tags", rowValue(
                shown.tags().isEmpty() ? NO_VALUE : String.join(", ", shown.tags())));
        return form;
    }

    private @NotNull JComponent fileValue(@NotNull LineageNode shown) {
        if (shown.fileName() == null) return rowValue(NO_VALUE);
        ActionLink file = new ActionLink(shown.fileName(), (ActionListener) e -> LineageActions.openSource(project, shown));
        file.setToolTipText(shown.fileName());
        file.setMinimumSize(new Dimension(0, file.getPreferredSize().height));
        return file;
    }

    private @NotNull JComponent columnForm(@NotNull ColumnRef column) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        JBTextField table = rowValue(tableWithoutProject(column.tableFullName()));
        table.setToolTipText(column.tableFullName());
        addRow(form, 0, "Column", rowValue(column.columnName()));
        addRow(form, 1, "Table", table);
        return form;
    }

    private static @NotNull String tableWithoutProject(@NotNull String tableFullName) {
        return tableFullName.substring(tableFullName.indexOf('.') + 1);
    }

    private void addRow(@NotNull JPanel form, int row, @NotNull String key, @NotNull JComponent value) {
        JBLabel label = new JBLabel(key);
        label.setForeground(UIUtil.getContextHelpForeground());
        label.setPreferredSize(new Dimension(Math.max(JBUIScale.scale(KEY_WIDTH), label.getPreferredSize().width),
                label.getPreferredSize().height));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = JBUI.insetsTop(row == 0 ? 0 : 8);
        form.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = JBUI.insets(row == 0 ? 0 : 8, 12, 0, 0);
        form.add(value, c);
        values.put(key, value);
    }

    private static @NotNull JComponent separator(@NotNull String title, @Nullable String count, boolean spaced) {
        JPanel row = new JPanel(new BorderLayout(JBUIScale.scale(8), 0));
        row.setOpaque(false);
        row.setBorder(JBUI.Borders.emptyTop(spaced ? 10 : 0));
        JPanel texts = new JPanel(new HorizontalLayout(JBUIScale.scale(8), SwingConstants.CENTER));
        texts.setOpaque(false);
        JBLabel name = new JBLabel(title);
        name.setFont(JBFont.label().asBold());
        texts.add(name);
        if (count != null) {
            JBLabel number = new JBLabel(count);
            number.setForeground(UIUtil.getContextHelpForeground());
            texts.add(number);
        }
        row.add(texts, BorderLayout.WEST);
        JPanel line = new JPanel(new GridBagLayout());
        line.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        line.add(new SeparatorComponent(JBUI.CurrentTheme.Separator.color(), SeparatorOrientation.HORIZONTAL), c);
        row.add(line, BorderLayout.CENTER);
        return row;
    }

    private AnAction nodeAction(@NotNull String text, @NotNull String description, @NotNull Icon icon,
                                boolean hiddenForDeclarations, @NotNull java.util.function.BiConsumer<Project, LineageNode> run) {
        return new DumbAwareAction(text, description, icon) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if (node != null) run.accept(project, node);
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                boolean declaration = node != null && "declaration".equals(node.dataformType());
                e.getPresentation().setVisible(!(hiddenForDeclarations && declaration));
                e.getPresentation().setEnabled(node != null && node.fileName() != null);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }

    private @NotNull String nodeName(@NotNull String id) {
        LineageNode dependency = model.graph().node(id);
        return dependency != null ? dependency.name() : id;
    }

    private @NotNull String columnName(@NotNull String id) {
        ColumnLineageGraph columns = model.columnGraph();
        ColumnRef ref = columns != null ? columns.column(id) : null;
        return ref != null ? ref.columnName() : id;
    }

    private static final class WidthTrackingPanel extends JPanel implements Scrollable {

        private WidthTrackingPanel() {
            super(new VerticalLayout(0));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return JBUIScale.scale(ROW_HEIGHT);
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class DependencyList {

        private final String title;
        private final JBLabel empty;
        private final CollectionListModel<String> items = new CollectionListModel<>();
        private final JBList<String> list = new JBList<>(items);

        DependencyList(@NotNull String title, @NotNull String emptyText,
                       @NotNull ColoredListCellRenderer<String> renderer,
                       @NotNull Function<String, String> searchText,
                       @NotNull Consumer<String> onSelect) {
            this.title = title;
            this.empty = new JBLabel(emptyText);
            empty.setForeground(UIUtil.getContextHelpForeground());
            empty.setBorder(JBUI.Borders.empty(4, 8));
            list.setCellRenderer(renderer);
            list.setFixedCellHeight(JBUIScale.scale(ROW_HEIGHT));
            list.setOpaque(false);
            list.setBackground(UIUtil.getPanelBackground());
            list.setBorder(JBUI.Borders.emptyTop(4));
            ListSpeedSearch.installOn(list, searchText);
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    int index = list.locationToIndex(e.getPoint());
                    if (index >= 0) onSelect.accept(items.getElementAt(index));
                }
            });
            list.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    String id = list.getSelectedValue();
                    if (e.getKeyCode() == KeyEvent.VK_ENTER && id != null) onSelect.accept(id);
                }
            });
        }

        void show(@NotNull JPanel parent, @NotNull Collection<String> ids, boolean spaced) {
            show(parent, ids, String.valueOf(ids.size()), spaced);
        }

        void show(@NotNull JPanel parent, @NotNull Collection<String> ids, @NotNull String count, boolean spaced) {
            items.replaceAll(List.copyOf(ids));
            parent.add(separator(title, count, spaced));
            parent.add(ids.isEmpty() ? empty : list);
        }
    }

    private final class NodeRenderer extends ColoredListCellRenderer<String> {
        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends String> list, String id, int index,
                                             boolean selected, boolean hasFocus) {
            setIconTextGap(JBUIScale.scale(8));
            LineageNode dependency = model.graph().node(id);
            if (dependency == null) {
                append(id);
                return;
            }
            setIcon(TypeGlyphIcon.small(dependency.dataformType()));
            append(dependency.name());
            append("  " + dependency.schema(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }

    private final class ColumnRenderer extends ColoredListCellRenderer<String> {
        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends String> list, String id, int index,
                                             boolean selected, boolean hasFocus) {
            ColumnLineageGraph columns = model.columnGraph();
            ColumnRef ref = columns != null ? columns.column(id) : null;
            if (ref == null) {
                append(id);
                return;
            }
            append(ref.columnName());
            append("  " + ref.tableFullName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }
}
