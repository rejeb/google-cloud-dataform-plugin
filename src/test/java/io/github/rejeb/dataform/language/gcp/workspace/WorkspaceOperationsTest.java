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
package io.github.rejeb.dataform.language.gcp.workspace;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.common.CommitAuthorConfig;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.common.GcpConfigProvider;
import io.github.rejeb.dataform.language.gcp.gitremote.FakeGitRemoteBranch;
import io.github.rejeb.dataform.language.gcp.gitremote.GitRemoteBranch;
import io.github.rejeb.dataform.language.gcp.gitremote.GitRemoteBranchException;
import io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.rejeb.dataform.language.gcp.gitremote.LocalClones;
import io.github.rejeb.dataform.language.gcp.gitremote.GitRemoteBranches;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.*;

public class WorkspaceOperationsTest extends BasePlatformTestCase {

    private WorkspaceRepository repository;
    private GcpConfigProvider fullConfig;
    private GcpConfigProvider emptyConfig;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        repository = mock(WorkspaceRepository.class);

        fullConfig = mock(GcpConfigProvider.class);
        when(fullConfig.getProjectId()).thenReturn("test-project");
        when(fullConfig.getLocation()).thenReturn("europe-west1");
        when(fullConfig.getRepositoryId()).thenReturn("test-repo");
        when(fullConfig.getCommitAuthor())
                .thenReturn(new CommitAuthorConfig("Test User", "test@example.com"));
        emptyConfig = mock(GcpConfigProvider.class);
        when(emptyConfig.getProjectId()).thenReturn(null);
        when(emptyConfig.getLocation()).thenReturn(null);
        when(emptyConfig.getRepositoryId()).thenReturn(null);
    }

    private WorkspaceOperationsHandler handler(GcpConfigProvider config) {
        return new WorkspaceOperationsHandler(repository, config, getProject());
    }

    private WorkspaceOperationsHandler handlerWithResolver(
            GcpConfigProvider config,
            List<String> resolvedFiles
    ) {
        return new WorkspaceOperationsHandler(
                repository, config, getProject(), p -> resolvedFiles
        );
    }


    public void testListWorkspacesReturnsWorkspacesFromRepository() {
        List<Workspace> expected = List.of(
                Workspace.fromResourceName("projects/p/locations/l/repositories/r/workspaces/dev"),
                Workspace.fromResourceName("projects/p/locations/l/repositories/r/workspaces/staging")
        );
        when(repository.findAll("test-project", "europe-west1", "test-repo"))
                .thenReturn(expected);

        List<Workspace> result = handler(fullConfig).listWorkspaces();

        assertEquals(2, result.size());
        assertEquals("dev", result.get(0).workspaceId());
        assertEquals("staging", result.get(1).workspaceId());
    }

    public void testListWorkspacesReturnsEmptyListWhenConfigMissing() {
        List<Workspace> result = handler(emptyConfig).listWorkspaces();

        assertTrue(result.isEmpty());
        verifyNoInteractions(repository);
    }

    public void testListWorkspacesPropagatesGcpApiException() {
        when(repository.findAll(any(), any(), any()))
                .thenThrow(new GcpApiException("failure", new RuntimeException()));

        assertThrows(GcpApiException.class, () -> handler(fullConfig).listWorkspaces());
    }

    /**
     * Creates a real file in the fixture and returns its path relative to the content root,
     * which is what {@code pushCode} resolves against before reading contents.
     */
    private String addProjectFile(String relativePath, String content) {
        PsiFile file = myFixture.addFileToProject(relativePath, content);
        VirtualFile contentRoot = ProjectRootManager.getInstance(getProject()).getContentRoots()[0];
        return VfsUtilCore.getRelativePath(file.getVirtualFile(), contentRoot);
    }

    public void testPushCodeCallsRepositoryWithCorrectWorkspaceId() {
        String path = addProjectFile("definitions/my_table.sqlx", "SELECT 1");

        handlerWithResolver(fullConfig, List.of(path)).pushCode("dev");

        verify(repository).push(eq("test-project"), eq("europe-west1"), eq("test-repo"),
                eq("dev"), eq(Map.of(path, "SELECT 1")), anySet());
    }

    public void testPushCodeDeletesRemotePathsNoLongerPresentLocally() {
        String path = addProjectFile("definitions/kept.sqlx", "SELECT 1");
        when(repository.listAllPaths("test-project", "europe-west1", "test-repo", "dev"))
                .thenReturn(List.of(path, "definitions/removed.sqlx"));

        handlerWithResolver(fullConfig, List.of(path)).pushCode("dev");

        verify(repository).push(eq("test-project"), eq("europe-west1"), eq("test-repo"),
                eq("dev"), eq(Map.of(path, "SELECT 1")), eq(Set.of("definitions/removed.sqlx")));
    }

    public void testPushCodeSkipsWhenResolvedFilesDoNotExistLocally() {
        handlerWithResolver(fullConfig, List.of("definitions/absent.sqlx")).pushCode("dev");

        verify(repository, never()).push(any(), any(), any(), any(), any(), any());
    }

    public void testPushCodeSkipsWhenNoDataformFilesPresent() {
        handlerWithResolver(fullConfig, List.of()).pushCode("dev");

        verifyNoInteractions(repository);
    }

    public void testPushCodeSkipsWhenConfigMissing() {
        handlerWithResolver(emptyConfig, List.of("definitions/my_table.sqlx"))
                .pushCode("dev");

        verifyNoInteractions(repository);
    }

    public void testPushCodePropagatesGcpApiException() {
        String path = addProjectFile("definitions/failing.sqlx", "SELECT 1");
        doThrow(new GcpApiException("failure", new RuntimeException()))
                .when(repository).push(any(), any(), any(), any(), any(), any());

        assertThrows(GcpApiException.class,
                () -> handlerWithResolver(fullConfig, List.of(path)).pushCode("dev"));
    }

    public void testDeleteWorkspaceCallsRepositoryWithActiveRepositoryCoordinates() {
        handler(fullConfig).deleteWorkspace("intellij-run-1");

        verify(repository).deleteWorkspace("test-project", "europe-west1", "test-repo", "intellij-run-1");
    }

    public void testDeleteWorkspaceFailsWhenConfigMissing() {
        assertThrows(GcpApiException.class, () -> handler(emptyConfig).deleteWorkspace("intellij-run-1"));
        verifyNoInteractions(repository);
    }

    public void testWorkspaceFromResourceNameExtractsId() {
        Workspace workspace = Workspace.fromResourceName(
                "projects/p/locations/l/repositories/r/workspaces/my-workspace"
        );

        assertEquals("my-workspace", workspace.workspaceId());
    }

    public void testWorkspaceFromResourceNameHandlesSingleSegment() {
        Workspace workspace = Workspace.fromResourceName("my-workspace");

        assertEquals("my-workspace", workspace.workspaceId());
    }

    public void testFetchCodeWithWorkspaceIdReadsAllWorkspaceFiles() {
        handler(fullConfig).fetchCode("dev");

        verify(repository).readAllFiles("test-project", "europe-west1", "test-repo", "dev");
    }

    public void testFetchCodeWithoutWorkspaceIdReadsTheRepositoryMainBranch() {
        repositoryIs(HOSTED);

        handler(fullConfig).fetchCode(null);

        verify(repository).readAllFiles("test-project", "europe-west1", "test-repo", null);
    }

    public void testFetchCodeReturnsTheFilesReadFromTheRepository() {
        repositoryIs(HOSTED);
        Map<String, String> expected = Map.of(
                "definitions/my_table.sqlx", "SELECT 1",
                "workflow_settings.yaml", "defaultProject: test"
        );
        when(repository.readAllFiles("test-project", "europe-west1", "test-repo", null))
                .thenReturn(expected);

        assertEquals(expected, handler(fullConfig).fetchCode(null));
    }

    public void testFetchCodeReturnsEmptyMapWhenTheRepositoryHasNoFile() {
        assertTrue(handler(fullConfig).fetchCode("dev").isEmpty());
    }

    public void testFetchCodeSkipsWhenConfigMissing() {
        assertTrue(handler(emptyConfig).fetchCode("dev").isEmpty());
        verifyNoInteractions(repository);
    }

    public void testFetchCodePropagatesGcpApiException() {
        doThrow(new GcpApiException("failure", new RuntimeException()))
                .when(repository).readAllFiles(any(), any(), any(), any());

        assertThrows(GcpApiException.class, () -> handler(fullConfig).fetchCode("dev"));
    }

    public void testPullCodeReadsAllFilesFromTheRepository() {
        repositoryIs(HOSTED);
        handler(fullConfig).pullCode(null);

        verify(repository).readAllFiles("test-project", "europe-west1", "test-repo", null);
    }

    public void testPullCodeWritesPulledFilesToTheProject() throws IOException {
        when(repository.readAllFiles("test-project", "europe-west1", "test-repo", "dev"))
                .thenReturn(Map.of("definitions/pulled.sqlx", "SELECT 42"));

        handler(fullConfig).pullCode("dev");

        VirtualFile contentRoot = ProjectRootManager.getInstance(getProject()).getContentRoots()[0];
        VirtualFile pulled = contentRoot.findFileByRelativePath("definitions/pulled.sqlx");
        assertNotNull("the pulled file must be written to the content root", pulled);
        assertEquals("SELECT 42", VfsUtilCore.loadText(pulled));
    }

    public void testPullCodeSkipsWhenConfigMissing() {
        handler(emptyConfig).pullCode(null);

        verifyNoInteractions(repository);
    }

    public void testPullCodePropagatesGcpApiException() {
        repositoryIs(HOSTED);
        doThrow(new GcpApiException("failure", new RuntimeException()))
                .when(repository).readAllFiles(any(), any(), any(), any());

        assertThrows(GcpApiException.class, () -> handler(fullConfig).pullCode(null));
    }

    public void testFetchGitStatusesHidesTheNodeModulesAndTheGitignoredPaths() {
        myFixture.addFileToProject(".gitignore", "*.log\n");
        UncommittedChange table = new UncommittedChange("definitions/a.sqlx", UncommittedChange.ChangeState.ADDED);
        when(repository.fetchFileGitStatuses("test-project", "europe-west1", "test-repo", "demo")).thenReturn(List.of(
                table,
                new UncommittedChange("node_modules/@dataform/core/package.json", UncommittedChange.ChangeState.ADDED),
                new UncommittedChange("logs/run.log", UncommittedChange.ChangeState.MODIFIED)));

        List<UncommittedChange> changes = handler(fullConfig).fetchGitStatuses("demo");

        assertEquals(List.of(table), changes);
    }

    private static final DataformRepository LINKED =
            new DataformRepository("test-repo", "https://example.com/r.git", "main", "");
    private static final DataformRepository HOSTED = new DataformRepository("test-repo", null, null, "");
    private static final FakeGitRemoteBranch BRANCH =
            new FakeGitRemoteBranch("main", Map.of("definitions/a.sqlx", "SELECT 'remote'"));

    private void repositoryIs(DataformRepository described) {
        when(repository.findRepository("test-project", "europe-west1", "test-repo")).thenReturn(described);
    }

    private void repositoryUnreadable() {
        when(repository.findRepository("test-project", "europe-west1", "test-repo"))
                .thenThrow(new GcpApiException("permission denied"));
    }

    private WorkspaceOperationsHandler cloneOf(GitRemoteBranch branch) {
        return new WorkspaceOperationsHandler(repository, fullConfig, getProject(), p -> List.of(), r -> branch);
    }

    private void verifyNoRepositoryLevelRead() {
        verify(repository, never()).listAllPaths(any(), any(), any(), any());
        verify(repository, never()).readAllFiles(any(), any(), any(), any());
        verify(repository, never()).getFileContent(any(), any(), any(), any(), any());
    }

    public void testAHostedRepositoryIsReadFromDataform() {
        repositoryIs(HOSTED);
        when(repository.listAllPaths("test-project", "europe-west1", "test-repo", null))
                .thenReturn(List.of("definitions/a.sqlx"));

        assertSame(MainBranchSource.DATAFORM, cloneOf(BRANCH).refreshMainBranchSource());
        assertEquals(List.of("definitions/a.sqlx"), cloneOf(BRANCH).listAllPaths(null));
    }

    public void testALinkedRepositoryWithACloneIsReadFromItsRemoteBranch() {
        repositoryIs(LINKED);

        assertEquals(new MainBranchSource.GitRemote(BRANCH), cloneOf(BRANCH).refreshMainBranchSource());
    }

    public void testALinkedRepositoryWithoutACloneHasNoSource() {
        repositoryIs(LINKED);

        assertSame(MainBranchSource.UNAVAILABLE, cloneOf(null).refreshMainBranchSource());
    }

    public void testWithoutTheGitPluginALinkedRepositoryHasNoSource() {
        repositoryIs(LINKED);
        WorkspaceOperationsHandler handler =
                new WorkspaceOperationsHandler(repository, fullConfig, getProject(), p -> List.of(), null);

        assertSame(MainBranchSource.UNAVAILABLE, handler.refreshMainBranchSource());
    }

    public void testAnUnreadableRepositoryIsReadFromDataformSoTheRealErrorSurfaces() {
        repositoryUnreadable();
        when(repository.listAllPaths("test-project", "europe-west1", "test-repo", null))
                .thenThrow(new GcpApiException("repository not found"));

        assertSame(MainBranchSource.DATAFORM, cloneOf(BRANCH).refreshMainBranchSource());
        assertThrows(GcpApiException.class, "repository not found", () -> cloneOf(BRANCH).listAllPaths(null));
    }

    public void testNoConfiguredRepositoryCallsNothing() {
        WorkspaceOperationsHandler handler = handler(emptyConfig);

        assertSame(MainBranchSource.DATAFORM, handler.refreshMainBranchSource());
        assertSame(MainBranchSource.DATAFORM, handler.mainBranchSource());
        assertEmpty(handler.listAllPaths(null));
        verifyNoInteractions(repository);
    }

    public void testTheRemoteBranchServesEveryReadWithoutAWorkspace() {
        repositoryIs(LINKED);
        WorkspaceOperationsHandler handler = cloneOf(BRANCH);

        assertEquals(List.of("definitions/a.sqlx"), handler.listAllPaths(null));
        assertEquals("SELECT 'remote'", handler.getFileContent(null, "definitions/a.sqlx"));
        assertEquals(Map.of("definitions/a.sqlx", "SELECT 'remote'"), handler.fetchCode(null));
        verifyNoRepositoryLevelRead();
    }

    public void testWithoutACloneNoReadReachesTheRepositoryApi() {
        repositoryIs(LINKED);
        WorkspaceOperationsHandler handler = cloneOf(null);

        assertEmpty(handler.listAllPaths(null));
        assertEquals("", handler.getFileContent(null, "definitions/a.sqlx"));
        assertTrue(handler.fetchCode(null).isEmpty());
        handler.pullCode(null);
        verifyNoRepositoryLevelRead();
    }

    public void testReadingAWorkspaceNeedsNoLookup() {
        when(repository.getFileContent(any(), any(), any(), any(), any())).thenReturn("");
        WorkspaceOperationsHandler handler = cloneOf(BRANCH);

        handler.listAllPaths("dev");
        handler.getFileContent("dev", "definitions/a.sqlx");
        handler.fetchCode("dev");

        verify(repository, never()).findRepository(any(), any(), any());
        verify(repository).listAllPaths("test-project", "europe-west1", "test-repo", "dev");
    }

    public void testReadsReuseTheKnownSource() {
        repositoryIs(LINKED);
        WorkspaceOperationsHandler handler = cloneOf(BRANCH);

        handler.refreshMainBranchSource();
        handler.listAllPaths(null);
        handler.getFileContent(null, "definitions/a.sqlx");

        verify(repository, times(1)).findRepository(any(), any(), any());
    }

    public void testRefreshingSeesTheRemoteBeingRemoved() {
        repositoryIs(LINKED);
        WorkspaceOperationsHandler handler = cloneOf(BRANCH);
        handler.refreshMainBranchSource();
        repositoryIs(HOSTED);

        assertSame(MainBranchSource.DATAFORM, handler.refreshMainBranchSource());
        assertSame(MainBranchSource.DATAFORM, handler.mainBranchSource());
    }

    public void testAFailedLookupIsNotRemembered() {
        when(repository.findRepository("test-project", "europe-west1", "test-repo"))
                .thenThrow(new GcpApiException("offline"))
                .thenReturn(LINKED);
        WorkspaceOperationsHandler handler = cloneOf(BRANCH);

        assertSame(MainBranchSource.DATAFORM, handler.mainBranchSource());
        assertEquals(new MainBranchSource.GitRemote(BRANCH), handler.mainBranchSource());
    }

    public void testAnotherRepositoryIsLookedUpAgain() {
        repositoryIs(LINKED);
        WorkspaceOperationsHandler handler = cloneOf(BRANCH);
        handler.mainBranchSource();
        when(fullConfig.getRepositoryId()).thenReturn("other-repo");
        when(repository.findRepository("test-project", "europe-west1", "other-repo")).thenReturn(HOSTED);

        assertSame(MainBranchSource.DATAFORM, handler.mainBranchSource());
    }

    public void testFetchingUpdatesTheRemoteBranch() {
        repositoryIs(LINKED);
        FakeGitRemoteBranch branch = new FakeGitRemoteBranch("main", Map.of());

        cloneOf(branch).fetchMainBranch();

        assertEquals(1, branch.fetches());
    }

    public void testFetchingAHostedRepositoryDoesNothing() {
        repositoryIs(HOSTED);
        FakeGitRemoteBranch branch = new FakeGitRemoteBranch("main", Map.of());

        cloneOf(branch).fetchMainBranch();

        assertEquals(0, branch.fetches());
    }

    public void testAFailedFetchIsReported() {
        repositoryIs(LINKED);
        FakeGitRemoteBranch branch = new FakeGitRemoteBranch("main", Map.of());
        branch.failFetchWith(new GitRemoteBranchException("offline", null));

        assertThrows(GitRemoteBranchException.class, "offline", () -> cloneOf(branch).fetchMainBranch());
    }

    public void testAFailedRefreshForgetsTheKnownSource() {
        when(repository.findRepository("test-project", "europe-west1", "test-repo"))
                .thenReturn(LINKED)
                .thenThrow(new GcpApiException("offline"))
                .thenReturn(HOSTED);
        WorkspaceOperationsHandler handler = cloneOf(BRANCH);
        handler.refreshMainBranchSource();

        assertSame(MainBranchSource.DATAFORM, handler.refreshMainBranchSource());
        assertSame(MainBranchSource.DATAFORM, handler.mainBranchSource());

        verify(repository, times(3)).findRepository(any(), any(), any());
    }

    public void testTheActiveRepositoryIsTheOneAlreadyRead() {
        repositoryIs(LINKED);
        WorkspaceOperationsHandler handler = cloneOf(null);
        handler.refreshMainBranchSource();

        assertEquals(LINKED, handler.commitRoute().repository());

        verify(repository, times(1)).findRepository(any(), any(), any());
    }

    public void testTheActiveRepositoryIsReadWhenUnknown() {
        repositoryIs(HOSTED);

        assertEquals(HOSTED, cloneOf(null).commitRoute().repository());
    }

    public void testAnUnreadableRepositoryIsNotActive() {
        repositoryUnreadable();

        assertNull(cloneOf(null).commitRoute());
    }

    public void testNoConfiguredRepositoryIsNotActive() {
        assertNull(handler(emptyConfig).commitRoute());
    }

    public void testPushingSendsTheBranchToTheApi() {
        handler(fullConfig).pushGitCommits("dev", "dev");

        verify(repository).pushGitCommits(eq("test-project"), eq("europe-west1"), eq("test-repo"), eq("dev"), eq("dev"), any());
    }

    public void testPushingWithoutABranchLeavesItToTheApi() {
        handler(fullConfig).pushGitCommits("dev", null);

        verify(repository).pushGitCommits(eq("test-project"), eq("europe-west1"), eq("test-repo"), eq("dev"), isNull(), any());
    }

    private WorkspaceOperationsHandler routing(GitRemoteBranches branches, LocalClones clones) {
        return new WorkspaceOperationsHandler(repository, fullConfig, getProject(), p -> List.of(), branches, clones);
    }

    public void testARepositoryWithoutRemoteHasNoClone() {
        repositoryIs(HOSTED);

        assertEquals(new CommitRoute(HOSTED, CommitRoute.LocalClone.NONE), routing(r -> null, r -> false).commitRoute());
    }

    public void testARemoteWithoutCloneHasNoClone() {
        repositoryIs(LINKED);

        assertEquals(new CommitRoute(LINKED, CommitRoute.LocalClone.NONE), routing(r -> null, r -> false).commitRoute());
    }

    public void testACloneWithTheGitPluginIsKnownAsSuch() {
        repositoryIs(LINKED);

        assertEquals(new CommitRoute(LINKED, CommitRoute.LocalClone.WITH_GIT_PLUGIN),
                routing(r -> null, r -> true).commitRoute());
    }

    public void testACloneWithoutTheGitPluginIsStillAClone() {
        repositoryIs(LINKED);

        assertEquals(new CommitRoute(LINKED, CommitRoute.LocalClone.WITHOUT_GIT_PLUGIN),
                routing(null, r -> true).commitRoute());
    }

    public void testACloneAddedLaterIsNoticedWithoutReadingTheRepositoryAgain() {
        repositoryIs(LINKED);
        AtomicBoolean clone = new AtomicBoolean();
        WorkspaceOperationsHandler handler = routing(r -> null, r -> clone.get());
        assertEquals(CommitRoute.LocalClone.NONE, handler.commitRoute().localClone());

        clone.set(true);

        assertEquals(CommitRoute.LocalClone.WITH_GIT_PLUGIN, handler.commitRoute().localClone());
        verify(repository, times(1)).findRepository(any(), any(), any());
    }
}
