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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.gitremote.FakeGitRemoteBranch;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.workspace.MainBranchSource;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkspaceSelectionTest extends BasePlatformTestCase {

    static final DataformRepositoryConfig SALES = new DataformRepositoryConfig("sales-id", "Sales", "p", "sales", "europe-west1");
    static final DataformRepositoryConfig MARKETING = new DataformRepositoryConfig("marketing-id", "Marketing", "p", "marketing", "us-central1");

    private final AtomicInteger changes = new AtomicInteger();
    private WorkspaceSelection selection;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
        resetSettings(settings);
        settings.saveAllConfigs(List.of(SALES, MARKETING));
        settings.setActiveRepositoryId(SALES.repositoryConfigId());
        selection = new WorkspaceSelection(getProject(), changes::incrementAndGet, getTestRootDisposable());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            resetSettings(GcpRepositorySettings.getInstance(getProject()));
        } finally {
            super.tearDown();
        }
    }

    static void resetSettings(GcpRepositorySettings settings) {
        settings.saveAllConfigs(List.of());
        settings.setActiveRepositoryId(null);
        settings.setSelectedWorkspaceId(null);
    }

    static Workspace workspace(String id) {
        return new Workspace("projects/p/locations/l/repositories/r/workspaces/" + id, id);
    }

    public void testWorkspacesAreUnknownUntilLoaded() {
        assertNull(selection.workspaceIds());

        selection.setWorkspaces(List.of(workspace("dev"), workspace("prod")));

        assertEquals(List.of("dev", "prod"), selection.workspaceIds());
    }

    public void testAMissingSelectedWorkspaceFallsBackToMain() {
        GcpRepositorySettings.getInstance(getProject()).setSelectedWorkspaceId("old");

        selection.setWorkspaces(List.of(workspace("dev")));

        assertNull(selection.selectedWorkspaceId());
    }

    public void testAnExistingSelectedWorkspaceIsKept() {
        GcpRepositorySettings.getInstance(getProject()).setSelectedWorkspaceId("dev");

        selection.setWorkspaces(List.of(workspace("dev")));

        assertEquals("dev", selection.selectedWorkspaceId());
    }

    public void testSelectingAWorkspaceStoresItAndNotifiesOnce() {
        selection.selectWorkspace("dev");
        selection.selectWorkspace("dev");

        assertEquals("dev", GcpRepositorySettings.getInstance(getProject()).getSelectedWorkspaceId());
        assertEquals(1, changes.get());
    }

    public void testSelectingARepositoryActivatesItAndReloadsItsWorkspaces() {
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
        selection.setWorkspaces(List.of(workspace("dev")));

        selection.selectRepository(MARKETING);

        assertEquals(MARKETING.repositoryConfigId(), settings.getActiveRepositoryId());
        assertNull(selection.workspaceIds());
        assertEquals(1, changes.get());
    }

    public void testTheModelStopsListeningWhenDisposed() {
        var parent = Disposer.newDisposable();
        WorkspaceSelection shortLived = new WorkspaceSelection(getProject(), () -> { }, parent);
        Disposer.dispose(parent);

        getProject().getMessageBus().syncPublisher(io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent.TOPIC)
                .onWorkspacesLoaded(List.of(workspace("dev")));

        assertNull(shortLived.workspaceIds());
    }

    private static final MainBranchSource GIT_REMOTE =
            new MainBranchSource.GitRemote(new FakeGitRemoteBranch("main", Map.of()));

    public void testADataformRepositoryOffersItsMainBranch() {
        selection.setMainBranchSource(MainBranchSource.DATAFORM);

        assertEquals("No workspace (repository main)", selection.mainBranchChoice());
        assertEquals(WorkspaceSelection.MAIN_BRANCH, selection.noWorkspaceName());
    }

    public void testALinkedRepositoryWithACloneOffersTheBranchOfItsRemote() {
        selection.setMainBranchSource(GIT_REMOTE);

        assertEquals("Git remote (origin/main)", selection.mainBranchChoice());
        assertEquals("origin/main", selection.noWorkspaceName());
    }

    public void testALinkedRepositoryWithoutACloneOffersNoMainBranch() {
        selection.setMainBranchSource(MainBranchSource.UNAVAILABLE);

        assertNull(selection.mainBranchChoice());
        assertEquals(WorkspaceSelection.NO_WORKSPACE, selection.noWorkspaceName());
    }

    public void testTheSourceArrivesFromTheEvent() {
        getProject().getMessageBus().syncPublisher(io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent.TOPIC)
                .onMainBranchLoaded(GIT_REMOTE);

        assertEquals("origin/main", selection.noWorkspaceName());
    }

    public void testAnotherRepositoryForgetsTheSource() {
        selection.setMainBranchSource(GIT_REMOTE);

        selection.selectRepository(MARKETING);

        assertEquals(WorkspaceSelection.MAIN_BRANCH, selection.noWorkspaceName());
    }
}
