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

import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformClientStub.COORDINATES;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformClientStub.REPOSITORY;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformClientStub.WORKSPACE;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformClientStub.directory;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformClientStub.file;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformClientStub.repositoryDirectory;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformClientStub.workspaceDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileSourceTest {

    private DataformClient client;

    @BeforeEach
    void setUp() {
        client = mock(DataformClient.class);
        when(client.readRepositoryFile(ReadRepositoryFileRequest.newBuilder()
                .setName(REPOSITORY).setPath("x.sqlx").build()))
                .thenReturn(ReadRepositoryFileResponse.newBuilder()
                        .setContents(ByteString.copyFromUtf8("repo")).build());
        when(client.readFile(ReadFileRequest.newBuilder()
                .setWorkspace(WORKSPACE).setPath("x.sqlx").build()))
                .thenReturn(ReadFileResponse.newBuilder()
                        .setFileContents(ByteString.copyFromUtf8("workspace")).build());
    }

    @Test
    void anEmptyWorkspaceIdReadsTheRepositoryBranch() {
        assertEquals("repo", FileSource.of(COORDINATES, "", client).read("x.sqlx"));
        verify(client, never()).readFile(any(ReadFileRequest.class));
    }

    @Test
    void noWorkspaceIdReadsTheRepositoryBranch() {
        assertEquals("repo", FileSource.of(COORDINATES, null, client).read("x.sqlx"));
        verify(client, never()).readFile(any(ReadFileRequest.class));
    }

    @Test
    void aWorkspaceIdReadsTheWorkspace() {
        assertEquals("workspace", FileSource.of(COORDINATES, "w", client).read("x.sqlx"));
        verify(client, never()).readRepositoryFile(any(ReadRepositoryFileRequest.class));
    }

    @Test
    void anEmptyWorkspaceIdListsTheRepositoryBranch() {
        repositoryDirectory(client, "", file("a.sqlx"));

        assertEquals(List.of(new FileSource.Entry("a.sqlx", false)), FileSource.of(COORDINATES, "", client).list(""));
        verify(client, never()).queryDirectoryContents(any(QueryDirectoryContentsRequest.class));
    }

    @Test
    void theBranchListingTurnsNamesIntoPathsAndSkipsNodeModules() {
        repositoryDirectory(client, "defs", file("x.sqlx"), directory("sub"), directory("node_modules"));

        assertEquals(List.of(new FileSource.Entry("defs/x.sqlx", false), new FileSource.Entry("defs/sub", true)),
                FileSource.of(COORDINATES, null, client).list("defs"));
    }

    @Test
    void theWorkspaceListingKeepsFullPathsAndSkipsNodeModules() {
        workspaceDirectory(client, "", file("workflow_settings.yaml"), directory("defs"), directory("node_modules"));

        assertEquals(List.of(new FileSource.Entry("workflow_settings.yaml", false), new FileSource.Entry("defs", true)),
                FileSource.of(COORDINATES, "w", client).list(""));
    }

    @Test
    void anEmptyRepositoryListsNothing() {
        when(client.queryRepositoryDirectoryContents(any(QueryRepositoryDirectoryContentsRequest.class)))
                .thenThrow(new FailedPreconditionException("Reading from empty repo", null,
                        mock(StatusCode.class), false));

        assertTrue(FileSource.of(COORDINATES, null, client).list("").isEmpty());
    }
}
