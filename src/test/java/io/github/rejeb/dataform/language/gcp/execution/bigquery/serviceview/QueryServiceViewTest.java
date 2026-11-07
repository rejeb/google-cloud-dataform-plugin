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

import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor.ColoredFragment;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBPanelWithEmptyText;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryJobResult;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryResultsRegistry;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryRun;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class QueryServiceViewTest extends BasePlatformTestCase {

    private static final String TABLE = "my-project.analytics.orders";
    private static final long CREATED = 1_730_000_000_000L;

    @Override
    protected void tearDown() throws Exception {
        try {
            QueryResultsRegistry registry = QueryResultsRegistry.getInstance(getProject());
            registry.getAll().forEach(run -> registry.remove(run.tableName()));
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testAQueryRowShowsItsShortNameThenTheTimeItStartedGrayed() {
        PresentationData presentation = presentation(running());

        List<ColoredFragment> fragments = presentation.getColoredText();
        assertEquals("orders", fragments.get(0).getText());
        assertEquals(DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(CREATED)), fragments.get(1).getText().strip());
        assertEquals(SimpleTextAttributes.GRAYED_ATTRIBUTES, fragments.get(1).getAttributes());
        assertEquals(TABLE, presentation.getTooltip());
    }

    public void testAQueryRowShowsTheStatusOfItsRunWithThePrototypeIcons() {
        assertSame(DataformIcons.SPINNER, presentation(running()).getIcon(false));
        assertSame(DataformIcons.STATUS_SUCCEEDED,
                presentation(running().finished(new BigQueryJobResult(TABLE, null, null, null), false)).getIcon(false));
        assertSame(DataformIcons.STATUS_FAILED,
                presentation(running().finished(new BigQueryJobResult(TABLE, null, null, "Error"), false)).getIcon(false));
        assertSame(DataformIcons.STATUS_CANCELLED,
                presentation(running().finished(new BigQueryJobResult(TABLE, null, null, "Cancelled"), true)).getIcon(false));
    }

    public void testAQueryOfAFileJumpsToItsSource() {
        VirtualFile source = myFixture.addFileToProject("definitions/orders.sqlx", "SELECT 1").getVirtualFile();
        QueryRun run = QueryRun.running(TABLE, "SELECT 1", source, CREATED, () -> {
        });

        OpenFileDescriptor navigatable = assertInstanceOf(
                new DataformServiceViewDescriptor(getProject(), run).getNavigatable(), OpenFileDescriptor.class);

        assertEquals(source, navigatable.getFile());
        assertNull(new DataformServiceViewDescriptor(getProject(), running()).getNavigatable());
    }

    public void testEveryQueryIsListedUnderOneDataformQueriesNode() {
        QueryResultsRegistry registry = QueryResultsRegistry.getInstance(getProject());
        registry.start(running());
        registry.start(QueryRun.running("my-project.analytics.customers", "SELECT 1", null, CREATED, null));
        DataformQueryContributor contributor = new DataformQueryContributor();

        List<QueryGroup> groups = contributor.getServices(getProject()).stream()
                .flatMap(node -> contributor.getGroups(node).stream())
                .toList();

        assertEquals(2, groups.size());
        assertEquals(groups.get(0), groups.get(1));
    }

    public void testTheDataformQueriesNodeCountsTheQueriesInBold() {
        QueryResultsRegistry.getInstance(getProject()).start(running());

        PresentationData group = (PresentationData) groupDescriptor().getPresentation();

        assertEquals(List.of("Dataform Queries", "1"),
                group.getColoredText().stream().map(f -> f.getText().strip()).toList());
        assertEquals(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, group.getColoredText().get(0).getAttributes());
        assertEquals(SimpleTextAttributes.GRAYED_ATTRIBUTES, group.getColoredText().get(1).getAttributes());
        assertSame(DataformIcons.QUERIES, group.getIcon(false));
    }

    public void testTheDataformQueriesNodeAsksToSelectAQuery() {
        QueryResultsRegistry.getInstance(getProject()).start(running());

        JBPanelWithEmptyText content = assertInstanceOf(groupDescriptor().getContentComponent(), JBPanelWithEmptyText.class);

        assertEquals("Select a query", content.getEmptyText().getText());
    }

    public void testTheContributorIsNamedDataformAndKeepsItsId() {
        ServiceViewDescriptor root = new DataformQueryContributor().getViewDescriptor(getProject());

        assertEquals("Dataform", ((PresentationData) root.getPresentation()).getPresentableText());
        assertEquals("Dataform Queries", root.getId());
    }

    public void testEveryLevelOffersToRemoveTheFinishedQueriesWithATrashIcon() {
        QueryResultsRegistry.getInstance(getProject()).start(running());

        assertTrue(hasRemoveFinished(new DataformQueryContributor().getViewDescriptor(getProject()).getToolbarActions()));
        assertTrue(hasRemoveFinished(groupDescriptor().getToolbarActions()));
        assertTrue(hasRemoveFinished(new DataformServiceViewDescriptor(getProject(), running()).getToolbarActions()));
        assertSame(DataformIcons.TRASH, new RemoveFinishedQueriesAction().getTemplatePresentation().getIcon());
    }

    public void testRemovingTheFinishedQueriesKeepsTheRunningOnes() {
        QueryResultsRegistry registry = QueryResultsRegistry.getInstance(getProject());
        QueryRun finished = QueryRun.running("my-project.analytics.customers", "SELECT 1", null, CREATED, null);
        registry.start(finished);
        registry.finish(finished, new BigQueryJobResult(finished.tableName(), null, null, null), false);
        registry.start(running());
        RemoveFinishedQueriesAction action = new RemoveFinishedQueriesAction();

        AnActionEvent e = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(getProject()));
        action.update(e);
        assertTrue(e.getPresentation().isEnabled());
        action.actionPerformed(e);

        assertEquals(List.of(TABLE), registry.getAll().stream().map(QueryRun::tableName).toList());
    }

    private ServiceViewDescriptor groupDescriptor() {
        DataformQueryContributor contributor = new DataformQueryContributor();
        QueryResultNode node = assertOneElement(contributor.getServices(getProject()));
        return contributor.getGroupDescriptor(assertOneElement(contributor.getGroups(node)));
    }

    private PresentationData presentation(QueryRun run) {
        return (PresentationData) new DataformServiceViewDescriptor(getProject(), run).getPresentation();
    }

    private static QueryRun running() {
        return QueryRun.running(TABLE, "SELECT 1", null, CREATED, () -> {
        });
    }

    private static boolean hasRemoveFinished(ActionGroup group) {
        assertNotNull(group);
        AnAction[] children = group instanceof DefaultActionGroup defaultGroup
                ? defaultGroup.getChildActionsOrStubs()
                : group.getChildren(null);
        return Arrays.stream(children).anyMatch(RemoveFinishedQueriesAction.class::isInstance);
    }
}
