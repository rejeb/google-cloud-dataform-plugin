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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.common.CommitAuthorConfig;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.common.GcpConfigProvider;
import io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceRepository;

import java.util.List;
import java.util.Map;

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

    public void testPushCodeCallsRepositoryWithCorrectWorkspaceId() {
        handlerWithResolver(fullConfig, List.of("definitions/my_table.sqlx"))
                .pushCode("dev");

        verify(repository).push("test-project", "europe-west1", "test-repo", "dev",any(),any());
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
        doThrow(new GcpApiException("failure", new RuntimeException()))
                .when(repository).push(any(), any(), any(), any(),any(),any());

        assertThrows(GcpApiException.class,
                () -> handlerWithResolver(fullConfig, List.of("definitions/my_table.sqlx"))
                        .pushCode("dev")
        );
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

    public void testFetchCodeWithWorkspaceIdCallsRepository() {
        handler(fullConfig).fetchCode("dev");

        verify(repository).pull(
                "test-project", "europe-west1", "test-repo", "dev",
                new CommitAuthorConfig("Test User", "test@example.com")
        );
    }

    public void testFetchCodeWithWorkspaceIdReturnsEmptyMap() {
        Map<String, String> result = handler(fullConfig).fetchCode("dev");

        assertTrue(result.isEmpty());
    }

    public void testFetchCodeWithWorkspaceIdSkipsWhenConfigMissing() {
        handler(emptyConfig).fetchCode("dev");

        verifyNoInteractions(repository);
    }

    public void testFetchCodeWithWorkspaceIdPropagatesGcpApiException() {
        doThrow(new GcpApiException("failure", new RuntimeException()))
                .when(repository).pull(any(), any(), any(), any(),any());

        assertThrows(GcpApiException.class, () -> handler(fullConfig).fetchCode("dev"));
    }

    public void testFetchCodeFromRepositoryReturnsFileContents() {
        List<String> paths = List.of("definitions/my_table.sqlx", "workflow_settings.yaml");
        Map<String, String> expected = Map.of(
                "definitions/my_table.sqlx", "SELECT 1",
                "workflow_settings.yaml", "defaultProject: test"
        );
        when(repository.readFilesFromRepository(
                "test-project", "europe-west1", "test-repo", paths))
                .thenReturn(expected);

        Map<String, String> result = handlerWithResolver(fullConfig, paths).fetchCode(null);

        assertEquals(expected, result);
    }

    public void testFetchCodeFromRepositorySkipsWhenNoFiles() {
        Map<String, String> result = handlerWithResolver(fullConfig, List.of()).fetchCode(null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(repository);
    }

    public void testFetchCodeFromRepositorySkipsWhenConfigMissing() {
        Map<String, String> result = handlerWithResolver(
                emptyConfig, List.of("definitions/my_table.sqlx")
        ).fetchCode(null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(repository);
    }

    public void testFetchCodeFromRepositoryPropagatesGcpApiException() {
        List<String> paths = List.of("definitions/my_table.sqlx");
        doThrow(new GcpApiException("failure", new RuntimeException()))
                .when(repository).readFilesFromRepository(any(), any(), any(), any());

        assertThrows(GcpApiException.class,
                () -> handlerWithResolver(fullConfig, paths).fetchCode(null));
    }

    public void testPullCodeCallsReadFilesFromRepository() {
        List<String> paths = List.of("definitions/my_table.sqlx");
        when(repository.readFilesFromRepository(
                "test-project", "europe-west1", "test-repo", paths))
                .thenReturn(Map.of("definitions/my_table.sqlx", "SELECT 1"));

        handlerWithResolver(fullConfig, paths).pullCode(null);

        verify(repository).readFilesFromRepository(
                "test-project", "europe-west1", "test-repo", paths);
    }

    public void testPullCodeSkipsWhenNoFiles() {
        handlerWithResolver(fullConfig, List.of()).pullCode(null);

        verifyNoInteractions(repository);
    }

    public void testPullCodeSkipsWhenConfigMissing() {
        handlerWithResolver(emptyConfig, List.of("definitions/my_table.sqlx")).pullCode(null);

        verifyNoInteractions(repository);
    }

    public void testPullCodePropagatesGcpApiException() {
        List<String> paths = List.of("definitions/my_table.sqlx");
        doThrow(new GcpApiException("failure", new RuntimeException()))
                .when(repository).readFilesFromRepository(any(), any(), any(), any());

        assertThrows(GcpApiException.class,
                () -> handlerWithResolver(fullConfig, paths).pullCode(null));
    }


}
