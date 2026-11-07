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
package io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher;

import com.intellij.notification.NotificationType;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.gitremote.FakeGitRemoteBranch;
import io.github.rejeb.dataform.language.gcp.gitremote.GitRemoteBranchException;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.workspace.CommitRoute;
import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import io.github.rejeb.dataform.language.gcp.workspace.MainBranchSource;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GcpPanelActionDispatcherImplTest extends BasePlatformTestCase {

    private static final DataformRepositoryConfig SALES =
            new DataformRepositoryConfig("sales-id", "Sales", "p", "sales", "europe-west1");
    private static final MainBranchSource GIT_REMOTE =
            new MainBranchSource.GitRemote(new FakeGitRemoteBranch("main", Map.of()));

    private final List<String> events = new ArrayList<>();
    private DataformGcpService service;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        GcpRepositorySettings.getInstance(getProject()).setSelectedWorkspaceId(null);
        service = mock(DataformGcpService.class);
        when(service.refreshMainBranchSource()).thenReturn(MainBranchSource.DATAFORM);
        ServiceContainerUtil.replaceService(getProject(), DataformGcpService.class, service, getTestRootDisposable());
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(DataformGcpEvent.TOPIC, new DataformGcpEvent() {
            @Override
            public void onCommitRouteLoaded(@Nullable CommitRoute route) {
                events.add("route=" + (route == null ? null : route.repository().repositoryId()));
            }

            @Override
            public void onMainBranchLoaded(@NotNull MainBranchSource source) {
                events.add(source.getClass().getSimpleName());
            }

            @Override
            public void onWorkspacesLoaded(@NotNull List<Workspace> workspaces) {
                events.add("workspaces");
            }

            @Override
            public void onFilesLoaded(@NotNull List<String> files) {
                events.add("files");
            }

            @Override
            public void onGitStatusesLoaded(@NotNull List<UncommittedChange> changes) {
                events.add("statuses");
            }

            @Override
            public void onNotification(@NotNull String message, @NotNull NotificationType type) {
                events.add(type + ": " + message);
            }
        });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
            settings.saveAllConfigs(List.of());
            settings.setActiveRepositoryId(null);
            settings.setSelectedWorkspaceId(null);
        } finally {
            super.tearDown();
        }
    }

    private void refresh(int expectedEvents) {
        new GcpPanelActionDispatcherImpl(getProject()).refreshWorkspaces();
        PlatformTestUtil.waitWithEventsDispatching("events never arrived: " + events,
                () -> events.size() >= expectedEvents, 10);
    }

    public void testTheSourceIsReportedBeforeTheWorkspaces() {
        when(service.refreshMainBranchSource()).thenReturn(GIT_REMOTE);

        refresh(4);

        assertEquals(List.of("route=null", "GitRemote", "workspaces", "files"), events);
    }

    public void testTheBranchIsFetchedBeforeItsFilesAreListed() {
        refresh(4);

        InOrder order = inOrder(service);
        order.verify(service).fetchMainBranch();
        order.verify(service).listAllPaths(isNull());
    }

    public void testAWorkspaceFetchesNoBranch() {
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
        settings.saveAllConfigs(List.of(SALES));
        settings.setActiveRepositoryId(SALES.repositoryConfigId());
        settings.setSelectedWorkspaceId("dev");

        refresh(5);

        verify(service, never()).fetchMainBranch();
        verify(service).listAllPaths("dev");
    }

    public void testAFailedFetchWarnsAndStillListsTheLastFiles() {
        doThrow(new GitRemoteBranchException("could not read <url>", null)).when(service).fetchMainBranch();

        refresh(5);

        assertContainsElements(events, "files",
                "WARNING: Could not fetch the Git remote, showing the last fetched files: could not read &lt;url&gt;");
    }

    private void waitForEvents(int expected) {
        PlatformTestUtil.waitWithEventsDispatching("events never arrived: " + events, () -> events.size() >= expected, 10);
    }

    public void testTheRepositoryIsReportedBeforeTheSource() {
        when(service.commitRoute()).thenReturn(routeWith(CommitRoute.LocalClone.NONE));

        refresh(4);

        assertEquals(List.of("route=r", "Dataform", "workspaces", "files"), events);
    }

    public void testAnUnreadableRepositoryIsReportedAsUnknown() {
        refresh(4);

        assertEquals("route=null", events.getFirst());
    }

    public void testPushingTellsWhichBranchReceivedTheCommits() {
        new GcpPanelActionDispatcherImpl(getProject()).pushGitCommits("dev", "my<branch");
        waitForEvents(1);

        verify(service).pushGitCommits("dev", "my<branch");
        assertEquals(List.of("INFORMATION: Commits pushed to my&lt;branch on the Git remote."), events);
    }

    public void testPushingToTheDefaultBranchOfAHostedRepositorySaysNothing() {
        new GcpPanelActionDispatcherImpl(getProject()).pushGitCommits("dev", null);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        verify(service).pushGitCommits("dev", null);
        assertEmpty(events);
    }

    public void testCommittingAndPushingCommitsFirstThenPushesToTheBranch() {
        new GcpPanelActionDispatcherImpl(getProject()).commitAndPush("dev", List.of("a.sqlx"), "message", "main");
        waitForEvents(2);

        InOrder order = inOrder(service);
        order.verify(service).commitWorkspaceChanges("dev", List.of("a.sqlx"), "message");
        order.verify(service).pushGitCommits("dev", "main");
        assertContainsElements(events, "statuses", "INFORMATION: Commits pushed to main on the Git remote.");
    }

    public void testAPushFailingAfterTheCommitStillRefreshesTheChanges() {
        doThrow(new RuntimeException("protected branch")).when(service).pushGitCommits("dev", "main");

        new GcpPanelActionDispatcherImpl(getProject()).commitAndPush("dev", List.of("a.sqlx"), "message", "main");
        waitForEvents(2);

        assertContainsElements(events, "statuses", "ERROR: Commit & Push failed: protected branch");
    }

    public void testAFailedCommitPushesNothingAndKeepsTheChanges() {
        doThrow(new RuntimeException("conflict")).when(service).commitWorkspaceChanges("dev", List.of("a.sqlx"), "message");

        new GcpPanelActionDispatcherImpl(getProject()).commitAndPush("dev", List.of("a.sqlx"), "message", "main");
        waitForEvents(1);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertEquals(List.of("ERROR: Commit & Push failed: conflict"), events);
        verify(service, never()).pushGitCommits(any(), any());
    }

    public void testAFailedPushIsReported() {
        doThrow(new RuntimeException("denied")).when(service).pushGitCommits("dev", "main");

        new GcpPanelActionDispatcherImpl(getProject()).pushGitCommits("dev", "main");
        waitForEvents(1);

        assertEquals(List.of("ERROR: Push commits failed: denied"), events);
    }

    private static CommitRoute routeWith(CommitRoute.LocalClone clone) {
        return new CommitRoute(new DataformRepository("r", "https://example.com/r.git", "main", ""), clone);
    }

    public void testAChangedRouteReloadsTheRepository() {
        when(service.commitRoute()).thenReturn(routeWith(CommitRoute.LocalClone.WITH_GIT_PLUGIN));

        new GcpPanelActionDispatcherImpl(getProject()).refreshWhenCommitRouteChanges(routeWith(CommitRoute.LocalClone.NONE));
        waitForEvents(4);

        assertEquals("route=r", events.getFirst());
        verify(service).listWorkspaces();
    }

    public void testAnUnchangedRouteReloadsNothing() {
        when(service.commitRoute()).thenReturn(routeWith(CommitRoute.LocalClone.NONE));

        new GcpPanelActionDispatcherImpl(getProject()).refreshWhenCommitRouteChanges(routeWith(CommitRoute.LocalClone.NONE));
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertEmpty(events);
        verify(service, never()).listWorkspaces();
    }

    public void testARouteThatBecameReadableReloadsTheRepository() {
        when(service.commitRoute()).thenReturn(routeWith(CommitRoute.LocalClone.NONE));

        new GcpPanelActionDispatcherImpl(getProject()).refreshWhenCommitRouteChanges(null);
        waitForEvents(4);

        verify(service).listWorkspaces();
    }
}
