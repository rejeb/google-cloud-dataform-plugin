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
package io.github.rejeb.dataform.language.gcp.execution.bigquery.serviceview;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.intellij.database.run.ui.FloatingPagingManager;
import com.intellij.database.run.ui.grid.GridMainPanel;
import com.intellij.icons.AllIcons;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionWrapper;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.BrowserLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.labels.LinkLabel;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryJobResult;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryJobStats;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryPagedResult;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryRun;
import io.github.rejeb.dataform.language.gcp.pricing.BigQueryPricingService;
import io.github.rejeb.dataform.language.ui.UiText;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewer;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.rejeb.dataform.language.util.Utils.formatBytes;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class QueryExecutionPanelTest extends BasePlatformTestCase {

    private static final long GIB = 1L << 30;
    private static final String TABLE = "my-project.analytics.orders";
    private static final String SQL = "SELECT order_id FROM analytics.raw_orders";

    public void testASucceededQueryShowsItsRowsInBoldThenDurationBytesCostAndCache() {
        BigQueryJobStats stats = stats(1_234, 3 * GIB, 0, true);

        QueryExecutionPanel panel = panel(succeeded(stats));

        String cost = JobInfoCost.of(stats, BigQueryPricingService.getInstance().quote("EU")).value();
        assertEquals("1,234 rows" + UiText.SEPARATOR
                        + UiText.dot(NlsMessages.formatDuration(1_500), formatBytes(3 * GIB) + " processed", cost, "cache hit"),
                text(panel.summary()));
        assertEquals("1,234 rows", boldText(panel.summary()));
        assertSame(DataformIcons.STATUS_SUCCEEDED, panel.summary().getIcon());
    }

    public void testOneRowIsSingularAndABilledQueryHasNoCacheHit() {
        QueryExecutionPanel panel = panel(succeeded(stats(1, GIB, GIB, false)));

        assertTrue(text(panel.summary()), text(panel.summary()).startsWith("1 row" + UiText.SEPARATOR));
        assertFalse(text(panel.summary()).contains("cache hit"));
    }

    public void testARunningQueryShowsASpinnerAndCanBeCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean();

        QueryExecutionPanel panel = panel(
                QueryRun.running(TABLE, SQL, null, 0, () -> cancelled.set(true)));

        assertEquals("Running orders…", text(panel.summary()));
        assertSame(DataformIcons.SPINNER, panel.summary().getIcon());
        AnAction cancel = panel.cancelAction();
        assertNotNull(cancel);
        assertEquals("Cancel", cancel.getTemplatePresentation().getText());
        assertSame(DataformIcons.STOP, cancel.getTemplatePresentation().getIcon());
        cancel.actionPerformed(TestActionEvent.createTestEvent(cancel));
        assertTrue(cancelled.get());
        JBLabel waiting = assertInstanceOf(panel.results(), JBLabel.class);
        assertEquals("Waiting for BigQuery…", waiting.getText());
        assertSame(DataformIcons.SPINNER, waiting.getIcon());
    }

    public void testAFinishedQueryCannotBeCancelled() {
        assertNull(panel(succeeded(stats(1, GIB, GIB, false))).cancelAction());
    }

    public void testAFailedQuerySaysSoInBold() {
        QueryExecutionPanel panel = panel(failed("Syntax error"));

        assertEquals("Query failed", boldText(panel.summary()));
        assertSame(DataformIcons.STATUS_FAILED, panel.summary().getIcon());
    }

    public void testACancelledQuerySaysSo() {
        QueryRun run = QueryRun.running(TABLE, SQL, null, 0, () -> {
        }).finished(new BigQueryJobResult(TABLE, null, null, "Query execution cancelled"), true);

        QueryExecutionPanel panel = panel(run);

        assertEquals("Cancelled", boldText(panel.summary()));
        assertSame(DataformIcons.STATUS_CANCELLED, panel.summary().getIcon());
        assertEquals("The query was cancelled.", emptyText(panel.results()));
    }

    public void testResultsComeBeforeJobInfoAndTheQueryAndAreSelected() {
        QueryExecutionPanel panel = panel(succeeded(stats(1, GIB, GIB, false)));

        JBTabbedPane tabs = panel.tabs();
        assertEquals(3, tabs.getTabCount());
        assertEquals(List.of("Results", "Job Info", "Query"),
                List.of(tabs.getTitleAt(0), tabs.getTitleAt(1), tabs.getTitleAt(2)));
        assertEquals(0, tabs.getSelectedIndex());
    }

    public void testJobInfoGroupsTheJobAndItsStatistics() {
        QueryExecutionPanel panel = panel(succeeded(stats(1, GIB, GIB, false)));

        List<String> groups = all(panel.jobInfo(), TitledSeparator.class).stream().map(TitledSeparator::getText).toList();
        assertEquals(List.of("Job", "Statistics"), groups);
        BrowserLink link = assertOneElement(all(panel.jobInfo(), BrowserLink.class));
        assertSame(AllIcons.Ide.External_link_arrow, link.getIcon());
        assertTrue(link.getUrl(), link.getUrl().contains("job_1"));
        assertEmpty(all(panel.jobInfo(), EditorTextField.class));
        assertTrue(keys(panel.jobInfo()).containsAll(List.of("Job ID", "Location", "State", "Created", "Duration",
                "Bytes processed", "Bytes billed", "Estimated cost", "Slot time", "Cache hit")));
    }

    public void testTheQueryTabShowsTheExecutedSqlInTheSqlViewer() {
        QueryExecutionPanel panel = panel(succeeded(stats(1, GIB, GIB, false)));

        SqlViewer viewer = panel.queryViewer();
        assertEquals(SQL, viewer.text());
        assertTrue(viewer.editor().getSettings().isLineNumbersShown());
        assertTrue(SwingUtilities.isDescendingFrom(viewer, panel.tabs().getComponentAt(2)));
    }

    public void testDisposingThePanelReleasesTheQueryEditor() {
        QueryExecutionPanel panel = new QueryExecutionPanel(getProject(), succeeded(stats(1, GIB, GIB, false)));
        EditorEx editor = panel.queryViewer().editor();

        Disposer.dispose(panel);

        assertTrue(editor.isDisposed());
    }

    public void testAFailureShowsABigQueryErrorBannerThatCopiesTheError() {
        String error = "Syntax error: Unexpected keyword FROM at [3:1]";

        QueryExecutionPanel panel = panel(failed(error));

        assertNotNull(panel.failureBanner());
        assertTrue(panel.failureBanner().getToolTipText().contains("Unexpected keyword FROM at [3:1]"));
        assertEquals(List.of("Copy error"), links(panel.failureBanner()));
        assertTrue(all(panel.failureBanner(), JLabel.class).stream()
                .anyMatch(label -> label.getIcon() == DataformIcons.STATUS_FAILED));
    }

    public void testAFailureOfAFileQueryOffersToShowItInTheEditor() {
        VirtualFile source = myFixture.addFileToProject("definitions/orders.sqlx", "SELECT 1").getVirtualFile();
        QueryRun run = QueryRun.running(TABLE, SQL, source, 0, () -> {
        }).finished(new BigQueryJobResult(TABLE, null, null, "Syntax error"), false);

        QueryExecutionPanel panel = panel(run);

        assertEquals(List.of("Show in editor", "Copy error"), links(panel.failureBanner()));
    }

    public void testAQueryWithoutRowsSaysSo() {
        QueryExecutionPanel panel = panel(succeeded(stats(0, GIB, GIB, false)));

        assertEquals("No rows", emptyText(panel.results()));
    }

    public void testAStatementSaysItWasExecuted() {
        BigQueryJobStats stats = new BigQueryJobStats("job_1", "my-project", "EU", 0, 0, 900, GIB, GIB, false,
                "INSERT", 0, 0);

        QueryExecutionPanel panel = panel(succeeded(stats));

        assertEquals("Statement executed", emptyText(panel.results()));
    }

    public void testTheResultsPagerReusesTheGridPagingWithThePrototypeIcons() {
        AnAction[] children = QueryExecutionPanel.pagerActions().getChildActionsOrStubs();

        assertEquals(8, children.length);
        assertWrapped(children[0], "Console.TableResult.FirstPage", DataformIcons.PAGE_FIRST);
        assertWrapped(children[1], "Console.TableResult.PreviousPage", DataformIcons.PAGE_PREVIOUS);
        assertEquals("Console.TableResult.ChangePageSize", ActionManager.getInstance().getId(children[2]));
        assertEquals("Console.TableResult.CountRows", ActionManager.getInstance().getId(children[3]));
        assertWrapped(children[4], "Console.TableResult.NextPage", DataformIcons.PAGE_NEXT);
        assertWrapped(children[5], "Console.TableResult.LastPage", DataformIcons.PAGE_LAST);
        assertInstanceOf(children[6], Separator.class);
    }

    public void testTheResultsPagerEndsWithTheGridExportAsADownloadButton() {
        AnAction[] children = QueryExecutionPanel.pagerActions().getChildActionsOrStubs();

        AnActionWrapper export = assertWrapped(children[children.length - 1],
                "Console.TableResult.ShowDumpDialogAction", DataformIcons.DOWNLOAD);
        assertEquals("Export", export.getTemplatePresentation().getText());
        assertEquals(Boolean.TRUE, export.getTemplatePresentation().getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR));
    }

    public void testRowsAreShownInTheGridWithThePagerBelowThem() {
        Field id = Field.of("id", StandardSQLTypeName.INT64);
        BigQueryPagedResult pages = mock(BigQueryPagedResult.class);
        when(pages.getSchema()).thenReturn(Schema.of(id));
        when(pages.getTotalRows()).thenReturn(2L);
        when(pages.getPageSize()).thenReturn(BigQueryPagedResult.DEFAULT_PAGE_SIZE);
        when(pages.getPageEnd()).thenReturn(2L);
        when(pages.isFirstPage()).thenReturn(true);
        when(pages.isLastPage()).thenReturn(true);
        when(pages.loadFirstPage()).thenReturn(List.of(
                FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, "1")), id),
                FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, "2")), id)));
        QueryRun run = QueryRun.running(TABLE, SQL, null, 0, () -> {
        }).finished(new BigQueryJobResult(TABLE, stats(2, GIB, GIB, false), pages, null), false);

        QueryExecutionPanel panel = panel(run);

        GridMainPanel grid = assertInstanceOf(panel.results(), GridMainPanel.class);
        List<ActionToolbar> toolbars = all(grid, JComponent.class).stream()
                .filter(ActionToolbar.class::isInstance)
                .map(ActionToolbar.class::cast)
                .toList();
        assertTrue(toolbars.stream().anyMatch(t -> Arrays.stream(((DefaultActionGroup) t.getActionGroup()).getChildActionsOrStubs())
                .anyMatch(a -> a instanceof AnActionWrapper wrapper
                        && "Console.TableResult.NextPage".equals(ActionManager.getInstance().getId(wrapper.getDelegate())))));
        assertNotSame(Boolean.TRUE, grid.getGrid().getUserData(FloatingPagingManager.AVAILABLE_FOR_GRID_TYPE));
    }

    private QueryExecutionPanel panel(QueryRun run) {
        QueryExecutionPanel panel = new QueryExecutionPanel(getProject(), run);
        Disposer.register(getTestRootDisposable(), panel);
        return panel;
    }

    private static AnActionWrapper assertWrapped(AnAction action, String delegateId, Icon icon) {
        AnActionWrapper wrapper = assertInstanceOf(action, AnActionWrapper.class);
        assertEquals(delegateId, ActionManager.getInstance().getId(wrapper.getDelegate()));
        assertSame(icon, wrapper.getTemplatePresentation().getIcon());
        return wrapper;
    }

    private static QueryRun succeeded(BigQueryJobStats stats) {
        return QueryRun.running(TABLE, SQL, null, 0, () -> {
        }).finished(new BigQueryJobResult(TABLE, stats, null, null), false);
    }

    private static QueryRun failed(String error) {
        return QueryRun.running(TABLE, SQL, null, 0, () -> {
        }).finished(new BigQueryJobResult(TABLE, null, null, error), false);
    }

    private static BigQueryJobStats stats(long rows, long processed, long billed, boolean cacheHit) {
        return new BigQueryJobStats("job_1", "my-project", "EU", 1_000, 2_000, 3_500, processed, billed, cacheHit,
                "SELECT", rows, 4_200);
    }

    private static String text(SimpleColoredComponent component) {
        return component.getCharSequence(false).toString();
    }

    private static String boldText(SimpleColoredComponent component) {
        StringBuilder bold = new StringBuilder();
        SimpleColoredComponent.ColoredIterator it = component.iterator();
        while (it.hasNext()) {
            it.next();
            if ((it.getTextAttributes().getStyle() & SimpleTextAttributes.STYLE_BOLD) != 0) {
                bold.append(it.getFragment());
            }
        }
        return bold.toString();
    }

    private static String emptyText(Component component) {
        return assertInstanceOf(component, JBPanelWithEmptyText.class).getEmptyText().getText();
    }

    private static List<String> keys(Container root) {
        return all(root, JBLabel.class).stream().map(JBLabel::getText).toList();
    }

    private static List<String> links(Container root) {
        return all(root, LinkLabel.class).stream().filter(Component::isVisible).map(LinkLabel::getText).toList();
    }

    private static <T extends Component> List<T> all(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                found.add(type.cast(child));
            }
            if (child instanceof Container container) {
                found.addAll(all(container, type));
            }
        }
        return found;
    }
}
