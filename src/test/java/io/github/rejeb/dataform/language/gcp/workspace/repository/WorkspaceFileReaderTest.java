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
import com.google.cloud.dataform.v1.QueryRepositoryDirectoryContentsRequest;
import com.google.cloud.dataform.v1.ReadFileRequest;
import com.google.cloud.dataform.v1.ReadFileResponse;
import com.google.cloud.dataform.v1.ReadRepositoryFileRequest;
import com.google.cloud.dataform.v1.ReadRepositoryFileResponse;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformClientStub.COORDINATES;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformClientStub.REPOSITORY;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformClientStub.WORKSPACE;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformClientStub.directory;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformClientStub.file;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformClientStub.repositoryDirectory;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformClientStub.workspaceDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WorkspaceFileReaderTest {

    private DataformClient client;

    @BeforeEach
    public void setUp() {
        client = mock(DataformClient.class);
    }

    @Test
    public void readsEveryRepositoryFileRecursivelySkippingNodeModules() {
        repositoryDirectory(client, "", file("a.sqlx"), directory("definitions"), directory("node_modules"));
        repositoryDirectory(client, "definitions", file("b.sqlx"), directory("sub"));
        repositoryDirectory(client, "definitions/sub", file("c.js"));
        when(client.readRepositoryFile(any(ReadRepositoryFileRequest.class))).thenAnswer(invocation -> {
            ReadRepositoryFileRequest request = invocation.getArgument(0);
            assertEquals(REPOSITORY, request.getName());
            return ReadRepositoryFileResponse.newBuilder()
                    .setContents(ByteString.copyFromUtf8("content of " + request.getPath()))
                    .build();
        });

        Map<String, String> files = WorkspaceFileReader.readAll(FileSource.of(COORDINATES, null, client));

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
        assertTrue(WorkspaceFileReader.readAll(FileSource.of(COORDINATES, null, client)).isEmpty());
    }

    @Test
    public void otherListingFailuresPropagate() {
        when(client.queryRepositoryDirectoryContents(any(QueryRepositoryDirectoryContentsRequest.class)))
                .thenThrow(new IllegalStateException("network"));
        assertThrows(IllegalStateException.class,
                () -> WorkspaceFileReader.readAll(FileSource.of(COORDINATES, null, client)));
    }

    @Test
    public void readsEveryWorkspaceFileRecursivelySkippingNodeModules() {
        workspaceDirectory(client, "", file("workflow_settings.yaml"), directory("definitions"), directory("node_modules"));
        workspaceDirectory(client, "definitions", file("definitions/a.sqlx"));
        when(client.readFile(any(ReadFileRequest.class))).thenAnswer(invocation -> {
            ReadFileRequest request = invocation.getArgument(0);
            assertEquals(WORKSPACE, request.getWorkspace());
            return ReadFileResponse.newBuilder()
                    .setFileContents(ByteString.copyFromUtf8("content of " + request.getPath()))
                    .build();
        });

        Map<String, String> files = WorkspaceFileReader.readAll(FileSource.of(COORDINATES, "w", client));

        assertEquals(Map.of(
                "workflow_settings.yaml", "content of workflow_settings.yaml",
                "definitions/a.sqlx", "content of definitions/a.sqlx"), files);
    }
}
