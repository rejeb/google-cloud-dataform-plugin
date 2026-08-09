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
import io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        handler(fullConfig).fetchCode(null);

        verify(repository).readAllFiles("test-project", "europe-west1", "test-repo", null);
    }

    public void testFetchCodeReturnsTheFilesReadFromTheRepository() {
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
        doThrow(new GcpApiException("failure", new RuntimeException()))
                .when(repository).readAllFiles(any(), any(), any(), any());

        assertThrows(GcpApiException.class, () -> handler(fullConfig).pullCode(null));
    }


}
