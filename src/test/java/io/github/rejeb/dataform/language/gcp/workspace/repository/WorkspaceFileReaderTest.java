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
package io.github.rejeb.dataform.language.gcp.workspace.repository;

import com.google.api.gax.rpc.FailedPreconditionException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.dataform.v1.DataformClient;
import com.google.cloud.dataform.v1.DirectoryEntry;
import com.google.cloud.dataform.v1.QueryDirectoryContentsRequest;
import com.google.cloud.dataform.v1.QueryRepositoryDirectoryContentsRequest;
import com.google.cloud.dataform.v1.ReadFileRequest;
import com.google.cloud.dataform.v1.ReadFileResponse;
import com.google.cloud.dataform.v1.ReadRepositoryFileRequest;
import com.google.cloud.dataform.v1.ReadRepositoryFileResponse;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WorkspaceFileReaderTest {

    private static final String REPOSITORY = "projects/p/locations/eu/repositories/r";
    private static final String WORKSPACE = REPOSITORY + "/workspaces/w";

    private DataformClient client;

    @BeforeEach
    public void setUp() {
        client = mock(DataformClient.class);
    }

    @Test
    public void readsEveryRepositoryFileRecursivelySkippingNodeModules() {
        repositoryDirectory("", file("a.sqlx"), directory("definitions"), directory("node_modules"));
        repositoryDirectory("definitions", file("b.sqlx"), directory("sub"));
        repositoryDirectory("definitions/sub", file("c.js"));
        when(client.readRepositoryFile(any(ReadRepositoryFileRequest.class))).thenAnswer(invocation -> {
            ReadRepositoryFileRequest request = invocation.getArgument(0);
            assertEquals(REPOSITORY, request.getName());
            return ReadRepositoryFileResponse.newBuilder()
                    .setContents(ByteString.copyFromUtf8("content of " + request.getPath()))
                    .build();
        });

        Map<String, String> files = WorkspaceFileReader.readAllRepositoryFiles("p", "eu", "r", client);

        assertEquals(Map.of(
                "a.sqlx", "content of a.sqlx",
                "definitions/b.sqlx", "content of definitions/b.sqlx",
                "definitions/sub/c.js", "content of definitions/sub/c.js"), files);
    }

    @Test
    public void emptyRepositoryHasNoFiles() {
        when(client.queryRepositoryDirectoryContents(any(QueryRepositoryDirectoryContentsRequest.class)))
                .thenThrow(new FailedPreconditionException("Reading from empty repo", null,
                        mock(StatusCode.class), false));
        assertTrue(WorkspaceFileReader.readAllRepositoryFiles("p", "eu", "r", client).isEmpty());
    }

    @Test
    public void otherListingFailuresPropagate() {
        when(client.queryRepositoryDirectoryContents(any(QueryRepositoryDirectoryContentsRequest.class)))
                .thenThrow(new IllegalStateException("network"));
        assertThrows(IllegalStateException.class,
                () -> WorkspaceFileReader.readAllRepositoryFiles("p", "eu", "r", client));
    }

    @Test
    public void readsEveryWorkspaceFileRecursivelySkippingNodeModules() {
        workspaceDirectory("", file("workflow_settings.yaml"), directory("definitions"), directory("node_modules"));
        workspaceDirectory("definitions", file("definitions/a.sqlx"));
        when(client.readFile(any(ReadFileRequest.class))).thenAnswer(invocation -> {
            ReadFileRequest request = invocation.getArgument(0);
            assertEquals(WORKSPACE, request.getWorkspace());
            return ReadFileResponse.newBuilder()
                    .setFileContents(ByteString.copyFromUtf8("content of " + request.getPath()))
                    .build();
        });

        Map<String, String> files = WorkspaceFileReader.readAllWorkspaceFiles("p", "eu", "r", "w", client);

        assertEquals(Map.of(
                "workflow_settings.yaml", "content of workflow_settings.yaml",
                "definitions/a.sqlx", "content of definitions/a.sqlx"), files);
    }

    @Test
    public void readsASingleFileFromEitherSource() {
        when(client.readRepositoryFile(ReadRepositoryFileRequest.newBuilder()
                .setName(REPOSITORY).setPath("x.sqlx").build()))
                .thenReturn(ReadRepositoryFileResponse.newBuilder()
                        .setContents(ByteString.copyFromUtf8("repo")).build());
        when(client.readFile(ReadFileRequest.newBuilder()
                .setWorkspace(WORKSPACE).setPath("x.sqlx").build()))
                .thenReturn(ReadFileResponse.newBuilder()
                        .setFileContents(ByteString.copyFromUtf8("workspace")).build());

        assertEquals("repo", WorkspaceFileReader.readRepositoryFile("p", "eu", "r", "x.sqlx", client));
        assertEquals("workspace", WorkspaceFileReader.readWorkspaceFile("p", "eu", "r", "w", "x.sqlx", client));
    }

    private void repositoryDirectory(String path, DirectoryEntry... entries) {
        DataformClient.QueryRepositoryDirectoryContentsPagedResponse response =
                mock(DataformClient.QueryRepositoryDirectoryContentsPagedResponse.class);
        when(response.iterateAll()).thenReturn(List.of(entries));
        when(client.queryRepositoryDirectoryContents(QueryRepositoryDirectoryContentsRequest.newBuilder()
                .setName(REPOSITORY).setPath(path).build()))
                .thenReturn(response);
    }

    private void workspaceDirectory(String path, DirectoryEntry... entries) {
        DataformClient.QueryDirectoryContentsPagedResponse response =
                mock(DataformClient.QueryDirectoryContentsPagedResponse.class);
        when(response.iterateAll()).thenReturn(List.of(entries));
        when(client.queryDirectoryContents(QueryDirectoryContentsRequest.newBuilder()
                .setWorkspace(WORKSPACE).setPath(path).build()))
                .thenReturn(response);
    }

    private static DirectoryEntry file(String path) {
        return DirectoryEntry.newBuilder().setFile(path).build();
    }

    private static DirectoryEntry directory(String path) {
        return DirectoryEntry.newBuilder().setDirectory(path).build();
    }
}
