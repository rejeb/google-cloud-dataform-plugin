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

import com.intellij.build.BuildContentManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import io.github.rejeb.dataform.language.fileEditor.cost.CostEstimate;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
import io.github.rejeb.dataform.language.gcp.pricing.BigQueryPricingService;
import io.github.rejeb.dataform.language.gcp.pricing.CostFormat;
import io.github.rejeb.dataform.language.gcp.pricing.CostText;
import io.github.rejeb.dataform.language.gcp.pricing.CostTooltip;
import io.github.rejeb.dataform.language.gcp.pricing.PriceQuote;
import io.github.rejeb.dataform.language.gcp.pricing.PriceSource;
import io.github.rejeb.dataform.language.schema.sql.DataformSchemaEvent;
import io.github.rejeb.dataform.language.schema.sql.DryRunErrorRegistry;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewerHeights;
import io.github.rejeb.dataform.language.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.*;

public class QueryPanel extends SectionsPreviewPanel {

    private static final String CAPACITY_PRICING = "capacity pricing";
    private static final String MIXED_LOCATIONS = "mixed locations";
    private static final String ESTIMATE_PREFIX = "≈ ";

    private final Project project;
    private final FileType fileType;
    private final Consumer<FormattedCompiledQuery> onRun;
    private final MessageBusConnection connection;
    private final List<TableQuerySection> sections = new ArrayList<>();
    private final SqlViewerHeights heights = new SqlViewerHeights();
    private final QueryCostTotalBar costTotalBar;
    private final QueryPreviewHeader header;
    private final Set<String> estimatedActions = new LinkedHashSet<>();
    private final Map<String, CostEstimate> estimates = new HashMap<>();
    private long compiledAt = -1;
    private boolean lastCompileFailed;

    public QueryPanel(Project project, FileType fileType) {
        this(project, fileType, ignored -> {
        });
    }

    /**
     * @param onRun runs the query of one action, from the Run Query action of its section
     */
    public QueryPanel(@NotNull Project project, FileType fileType, @NotNull Consumer<FormattedCompiledQuery> onRun) {
        this.project  = project;
        this.fileType = fileType;
        this.onRun = onRun;

        costTotalBar = new QueryCostTotalBar(() -> DataformCredentialsService.getInstance().signIn(project));
        header = new QueryPreviewHeader(costTotalBar, this::showBuildWindow);
        header.showSummary(0, compiledAt, lastCompileFailed);
        add(header, BorderLayout.NORTH);

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
        List<String> displayedActions = allSections()
                .map(s -> s.getQuery().tableName())
                .toList();
        Map<String, String> errors = errorsOf(displayedActions, DryRunErrorRegistry.getInstance(project).getErrors());
        sections.forEach(section -> section.showErrors(errors));
    }

    private Stream<TableQuerySection> allSections() {
        return sections.stream()
                .flatMap(section -> Stream.concat(Stream.of(section), section.assertionSections().stream()));
    }

    /**
     * Keeps the errors of the given actions, preserving their display order.
     */
    @NotNull
    static Map<String, String> errorsOf(@NotNull List<String> actionFullNames,
                                        @NotNull Map<String, String> allErrors) {
        Map<String, String> errors = new LinkedHashMap<>();
        actionFullNames.stream()
                .filter(allErrors::containsKey)
                .forEach(name -> errors.put(name, allErrors.get(name)));
        return errors;
    }

    public void setContent(List<FormattedCompiledQuery> queries) {
        setContent(queries, null);
    }

    /**
     * Shows one section per action.
     *
     * @param consoleProject the project the BigQuery console opens on, {@code null} when unknown
     */
    public void setContent(List<FormattedCompiledQuery> queries, @Nullable String consoleProject) {
        sections.forEach(TableQuerySection::dispose);
        sections.clear();
        estimatedActions.clear();
        estimates.clear();
        costTotalBar.hideTotal();
        sectionsPanel.removeAll();
        for (FormattedCompiledQuery q : queries != null ? queries : List.<FormattedCompiledQuery>of()) {
            TableQuerySection section = new TableQuerySection(q, fileType, project, onRun, consoleProject, heights);
            sections.add(section);
            sectionsPanel.add(section);
        }
        header.showSummary((int) allSections().count(), compiledAt, lastCompileFailed);
        refreshErrors();
        sectionsPanel.revalidate();
        sectionsPanel.repaint();
    }

    /**
     * Shows in the header when the displayed graph was compiled and whether the last compilation
     * failed, in which case the graph is the one compiled before.
     *
     * @param compiledAt        epoch milliseconds, or a negative value when unknown
     * @param lastCompileFailed whether the last run of the Dataform CLI compiled nothing
     */
    public void showCompilation(long compiledAt, boolean lastCompileFailed) {
        this.compiledAt = compiledAt;
        this.lastCompileFailed = lastCompileFailed;
        header.showSummary((int) allSections().count(), compiledAt, lastCompileFailed);
    }

    /**
     * Shows that the cost of an action is being estimated; an action not displayed is ignored.
     */
    public void markEstimating(@NotNull String tableName) {
        CostLabel label = costLabelOf(tableName);
        if (label == null) return;
        estimatedActions.add(tableName);
        estimates.remove(tableName);
        label.showEstimating();
        refreshCostTotal();
    }

    /**
     * Shows the estimated cost of an action, priced for the location of its dry-run; an action not
     * displayed is ignored.
     */
    public void showEstimate(@NotNull String tableName, @NotNull CostEstimate estimate) {
        CostLabel label = costLabelOf(tableName);
        if (label == null) return;
        estimatedActions.add(tableName);
        estimates.put(tableName, estimate);
        if (estimate.failed() || estimate.bytesProcessed() == null) {
            label.showFailure(estimate.error() != null ? estimate.error() : "No bytes reported");
        } else {
            label.showCost(Utils.formatBytes(estimate.bytesProcessed()), costOf(estimate));
        }
        refreshCostTotal();
    }

    /**
     * Asks to sign in instead of estimating the cost of the displayed actions.
     */
    public void markSignedOut() {
        estimatedActions.clear();
        estimates.clear();
        allSections()
                .filter(section -> !section.notCompiled())
                .forEach(section -> section.costLabel().showSignedOut());
        costTotalBar.showSignedOut();
    }

    @Nullable
    CostLabel costLabelOf(@NotNull String tableName) {
        return allSections()
                .filter(section -> section.getQuery().tableName().equals(tableName))
                .findFirst()
                .map(TableQuerySection::costLabel)
                .orElse(null);
    }

    @NotNull
    QueryCostTotalBar costTotalBar() {
        return costTotalBar;
    }

    @NotNull
    QueryPreviewHeader header() {
        return header;
    }

    private void showBuildWindow() {
        BuildContentManager.getInstance(project).getOrCreateToolWindow().activate(null);
    }

    private void refreshCostTotal() {
        List<CostEstimate> done = estimatedActions.stream()
                .map(estimates::get)
                .filter(Objects::nonNull)
                .filter(estimate -> !estimate.failed() && estimate.bytesProcessed() != null)
                .toList();
        PreviewCostTotal total = PreviewCostTotal.of(done, estimatedActions.size());
        if (total.visible()) {
            costTotalBar.update(total, Utils.formatBytes(total.bytes()), totalCost(done));
        } else if (estimatedActions.stream().anyMatch(action -> !estimates.containsKey(action))) {
            costTotalBar.showEstimating();
        } else {
            costTotalBar.hideTotal();
        }
    }

    @NotNull
    private static CostText costOf(@NotNull CostEstimate estimate) {
        return estimate.minimumApplies()
                ? CostText.estimateAtMinimum(estimate.billedBytes(), quoteOf(estimate))
                : CostText.estimate(estimate.billedBytes(), quoteOf(estimate));
    }

    @NotNull
    private static CostText totalCost(@NotNull List<CostEstimate> done) {
        BigDecimal sum = BigDecimal.ZERO;
        PriceQuote first = null;
        boolean mixedLocations = false;
        boolean reliable = true;
        for (CostEstimate estimate : done) {
            PriceQuote quote = quoteOf(estimate);
            if (quote.source() == PriceSource.CAPACITY || quote.pricePerTib() == null) {
                return new CostText(CAPACITY_PRICING, "", true);
            }
            if (first == null) {
                first = quote;
            } else if (!first.locationLabel().equals(quote.locationLabel())) {
                mixedLocations = true;
            }
            reliable &= quote.reliable();
            sum = sum.add(CostFormat.costOf(estimate.billedBytes(), quote.pricePerTib()));
        }
        if (first == null) {
            return new CostText("", "", true);
        }
        String note = mixedLocations ? MIXED_LOCATIONS : CostFormat.priceNote(first, false);
        boolean atMinimum = done.stream().anyMatch(CostEstimate::minimumApplies);
        return new CostText(ESTIMATE_PREFIX + CostFormat.money(sum, first.currency()), note, reliable,
                atMinimum ? CostTooltip.totalAtMinimum(note) : CostTooltip.total(note));
    }

    @NotNull
    private static PriceQuote quoteOf(@NotNull CostEstimate estimate) {
        return BigQueryPricingService.getInstance().quote(estimate.location());
    }

    public EditorEx getEditor() {
        if (sections.isEmpty()) return null;
        return sections.get(0).queryViewer().editor();
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
        return sections.stream().anyMatch(s -> s.queryViewer().hasText());
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
